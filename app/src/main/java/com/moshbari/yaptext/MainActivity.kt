package com.moshbari.yaptext

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moshbari.yaptext.data.AppStorage
import com.moshbari.yaptext.data.SubscriptionManager
import com.moshbari.yaptext.ui.MainViewModel
import com.moshbari.yaptext.ui.Screen
import com.moshbari.yaptext.ui.YapTextRoot
import com.moshbari.yaptext.ui.theme.YapTextTheme

class MainActivity : ComponentActivity() {

    // Re-keyed each time a record-intent arrives so Compose re-triggers auto-record.
    private var autoRecordTrigger by mutableIntStateOf(0)
    private var returnToKeyboard by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStorage.ensure(this)

        setContent {
            YapTextTheme {
                val vm: MainViewModel = viewModel()
                var screen by remember { mutableStateOf(Screen.MAIN) }

                val transcription by vm.transcriptionState.collectAsState()
                val polished by vm.polishedText.collectAsState()

                // Mic permission
                var micGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { micGranted = it }
                LaunchedEffect(Unit) {
                    if (!micGranted) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                // Auto-copy original transcription to clipboard + hand off to keyboard.
                LaunchedEffect(transcription.transcribedText) {
                    val t = transcription.transcribedText
                    if (t.isNotEmpty()) {
                        copyToClipboard(t)
                        AppStorage.saveDictationText(t)
                    }
                }
                // Auto-copy polished text (overrides the original hand-off).
                LaunchedEffect(polished) {
                    if (polished.isNotEmpty()) {
                        copyToClipboard(polished)
                        AppStorage.saveDictationText(polished)
                    }
                }

                // Refresh subscription state when the app comes to the foreground.
                LaunchedEffect(Unit) { vm.refreshSubscription() }

                when (screen) {
                    Screen.MAIN -> YapTextRoot(
                        vm = vm,
                        micGranted = micGranted,
                        autoRecordTrigger = autoRecordTrigger,
                        returnToKeyboard = returnToKeyboard,
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenPaywall = { screen = Screen.PAYWALL },
                        onRequestPermission = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                    Screen.PAYWALL -> com.moshbari.yaptext.ui.PaywallScreen(
                        onClose = { screen = Screen.MAIN }
                    )
                    Screen.SETTINGS -> com.moshbari.yaptext.ui.SettingsScreen(
                        onClose = { screen = Screen.MAIN },
                        onOpenPaywall = { screen = Screen.PAYWALL },
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        SubscriptionManager.refresh()
    }

    /** Parse yaptext://record | yaptext://dictate, or the widget/keyboard extra. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val host = intent.data?.host
        val extra = intent.getStringExtra(EXTRA_ACTION)
        val action = host ?: extra ?: return
        when (action) {
            "record" -> { returnToKeyboard = false; autoRecordTrigger++ }
            "dictate" -> { returnToKeyboard = true; autoRecordTrigger++ }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("YapText", text))
    }

    companion object {
        const val EXTRA_ACTION = "yaptext_action"
    }
}
