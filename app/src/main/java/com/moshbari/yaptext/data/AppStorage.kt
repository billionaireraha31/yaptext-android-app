package com.moshbari.yaptext.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared key/value storage — the Android replacement for the iOS App Group
 * UserDefaults (`group.com.moshbari.yaptextios`).
 *
 * On iOS the main app, keyboard extension and widget are separate processes
 * that need an App Group to share data. On Android they all live in the SAME
 * package, so a single SharedPreferences file is shared automatically — no
 * special entitlement required.
 *
 * Call [init] once from the Application's onCreate.
 */
object AppStorage {

    private const val PREFS = "yaptext_shared"

    // Keys (kept identical in spirit to the iOS UserDefaults keys)
    private const val KEY_LANGUAGE = "yaptext-selected-language"
    private const val KEY_TRIAL_USED = "yaptext-trial-seconds-used"
    private const val KEY_IS_PRO = "yaptext-is-pro"
    private const val KEY_LICENSE = "yaptext-license-key"
    private const val KEY_DICTATION_TEXT = "keyboard-dictation-text"
    private const val KEY_DICTATION_TS = "keyboard-dictation-timestamp"
    private const val KEY_CONSENT = "userConsentedToDataSharing"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** For components (IME / widget) that may run before the Application init ran. */
    fun ensure(context: Context) {
        if (!::prefs.isInitialized) init(context)
    }

    // MARK: - Language

    var language: Language
        get() = Language.fromRaw(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.raw).apply()

    // MARK: - Trial usage

    val trialSecondsUsed: Double
        get() = prefs.getFloat(KEY_TRIAL_USED, 0f).toDouble()

    fun addTrialSecondsUsed(seconds: Double) {
        val current = prefs.getFloat(KEY_TRIAL_USED, 0f)
        prefs.edit().putFloat(KEY_TRIAL_USED, current + seconds.toFloat()).apply()
    }

    fun resetTrialSecondsUsed() {
        prefs.edit().putFloat(KEY_TRIAL_USED, 0f).apply()
    }

    // MARK: - Pro / license

    var isPro: Boolean
        get() = prefs.getBoolean(KEY_IS_PRO, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PRO, value).apply()

    var licenseKey: String?
        get() = prefs.getString(KEY_LICENSE, null)
        set(value) = prefs.edit().putString(KEY_LICENSE, value).apply()

    // MARK: - Keyboard dictation hand-off (app -> IME)

    fun saveDictationText(text: String) {
        prefs.edit()
            .putString(KEY_DICTATION_TEXT, text)
            .putLong(KEY_DICTATION_TS, System.currentTimeMillis())
            .apply()
    }

    val dictationTimestamp: Long
        get() = prefs.getLong(KEY_DICTATION_TS, 0L)

    fun consumeDictationText(): String? {
        val text = prefs.getString(KEY_DICTATION_TEXT, null)
        if (text != null) prefs.edit().remove(KEY_DICTATION_TEXT).apply()
        return text
    }

    // MARK: - Consent

    fun resetDataConsent() {
        prefs.edit().remove(KEY_CONSENT).apply()
    }
}
