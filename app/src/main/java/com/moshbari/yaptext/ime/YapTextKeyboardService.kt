package com.moshbari.yaptext.ime

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.moshbari.yaptext.MainActivity
import com.moshbari.yaptext.data.AppStorage

/**
 * YapText custom keyboard — Android port of KeyboardViewController.swift.
 *
 * A blue-themed QWERTY keyboard with an orange mic key. Tapping the mic opens
 * the main app to record (yaptext://dictate); when the user returns to the
 * keyboard, any newly dictated text saved in [AppStorage] is committed into
 * the focused text field.
 */
class YapTextKeyboardService : InputMethodService() {

    private var shifted = false
    private var numberMode = false
    private var lastTimestamp = 0L

    private lateinit var root: LinearLayout
    private var banner: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeat: Runnable? = null

    // Colors mirror the iOS keyboard.
    private val colBackground = Color.rgb(26, 38, 77)     // deep navy
    private val colLetter = Color.rgb(51, 77, 140)
    private val colSpecial = Color.rgb(38, 51, 102)
    private val colSpace = Color.rgb(64, 89, 153)
    private val colReturn = Color.rgb(77, 140, 255)
    private val colMic = Color.rgb(255, 140, 0)
    private val colShiftActive = Color.rgb(77, 115, 191)

    private val letterRows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"),
    )
    private val numberRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("-","/",":",";","(",")","$","&","@","\""),
        listOf(".",",","?","!","'"),
    )

    override fun onCreate() {
        super.onCreate()
        AppStorage.ensure(this)
        lastTimestamp = AppStorage.dictationTimestamp
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBackground)
            setPadding(dp(3), dp(6), dp(3), dp(4))
        }
        buildKeyboard()
        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        checkForNewText()
    }

    // MARK: - Dictation hand-off

    private fun checkForNewText() {
        val ts = AppStorage.dictationTimestamp
        if (ts > lastTimestamp) {
            val text = AppStorage.consumeDictationText()
            if (!text.isNullOrEmpty()) {
                lastTimestamp = ts
                currentInputConnection?.commitText(text, 1)
                showBanner("✅ Text inserted!")
            }
        }
    }

    private fun showBanner(message: String) {
        banner?.apply {
            text = message
            visibility = View.VISIBLE
        }
        handler.postDelayed({ banner?.visibility = View.GONE }, 2500)
    }

    // MARK: - Build

    private fun buildKeyboard() {
        root.removeAllViews()

        banner = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(102, 255, 102))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(banner, rowParams(dp(14)))

        val rows = if (numberMode) numberRows else letterRows
        root.addView(buildSimpleRow(rows[0]), rowParams(dp(46)))
        root.addView(buildSimpleRow(rows[1]), rowParams(dp(46)))
        root.addView(buildRow3(rows[2]), rowParams(dp(46)))
        root.addView(buildBottomRow(), rowParams(dp(46)))
    }

    private fun buildSimpleRow(keys: List<String>): LinearLayout {
        val row = horizontalRow()
        for (k in keys) row.addView(letterKey(k), keyParams(1f))
        return row
    }

    private fun buildRow3(keys: List<String>): LinearLayout {
        val row = horizontalRow()
        // Shift
        val shift = specialKey(if (shifted) "⇧" else "⇪") { toggleShift() }
        if (shifted) shift.setBackgroundColor(colShiftActive)
        row.addView(shift, keyParams(1.5f))
        // Letters
        for (k in keys) row.addView(letterKey(k), keyParams(1f))
        // Delete
        val del = specialKey("⌫") { deleteOne() }
        del.setOnLongClickListener {
            startDeleteRepeat(); true
        }
        del.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_UP ||
                e.action == android.view.MotionEvent.ACTION_CANCEL) stopDeleteRepeat()
            false
        }
        row.addView(del, keyParams(1.5f))
        return row
    }

    private fun buildBottomRow(): LinearLayout {
        val row = horizontalRow()
        row.addView(specialKey(if (numberMode) "ABC" else "123") { toggleNumberMode() }, keyParams(1.5f))
        row.addView(specialKey("🌐") { switchToNextIme() }, keyParams(1f))
        // Mic (orange)
        val mic = makeButton("🎤", colMic) { onMicTapped() }
        row.addView(mic, keyParams(1.5f))
        row.addView(makeButton("space", colSpace) { commit(" ") }, keyParams(4f))
        val ret = makeButton("return", colReturn) { commit("\n") }
        row.addView(ret, keyParams(2f))
        return row
    }

    // MARK: - Key factories

    private fun letterKey(key: String): Button {
        val display = if (shifted && !numberMode) key.uppercase() else key
        return makeButton(display, colLetter) {
            commit(display)
            if (shifted && !numberMode) { shifted = false; buildKeyboard() }
        }.apply { textSize = 20f }
    }

    private fun specialKey(label: String, onClick: () -> Unit): Button =
        makeButton(label, colSpecial, onClick).apply { textSize = 16f }

    private fun makeButton(label: String, bg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(bg)
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }

    // MARK: - Actions

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteOne() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        deleteRepeat = object : Runnable {
            override fun run() {
                deleteOne()
                handler.postDelayed(this, 80)
            }
        }
        handler.post(deleteRepeat!!)
    }

    private fun stopDeleteRepeat() {
        deleteRepeat?.let { handler.removeCallbacks(it) }
        deleteRepeat = null
    }

    private fun toggleShift() { shifted = !shifted; buildKeyboard() }
    private fun toggleNumberMode() { numberMode = !numberMode; shifted = false; buildKeyboard() }

    private fun switchToNextIme() {
        switchToNextInputMethod(false)
    }

    private fun onMicTapped() {
        // Remember the current hand-off timestamp so we only insert NEW text.
        lastTimestamp = AppStorage.dictationTimestamp
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_ACTION, "dictate")
        }
        startActivity(intent)
    }

    // MARK: - Layout helpers

    private fun horizontalRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(2), dp(3), dp(2), dp(3))
    }

    private fun rowParams(height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, height
    )

    private fun keyParams(weight: Float) = LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, weight
    ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopDeleteRepeat()
        super.onDestroy()
    }
}
