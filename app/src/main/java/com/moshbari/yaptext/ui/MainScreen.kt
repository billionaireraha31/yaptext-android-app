package com.moshbari.yaptext.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moshbari.yaptext.data.Language
import com.moshbari.yaptext.data.SubscriptionManager
import com.moshbari.yaptext.data.Tone
import com.moshbari.yaptext.ui.theme.YapGreen
import com.moshbari.yaptext.ui.theme.YapOrange

enum class Screen { MAIN, PAYWALL, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YapTextRoot(
    vm: MainViewModel,
    micGranted: Boolean,
    autoRecordTrigger: Int,
    returnToKeyboard: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val state by vm.transcriptionState.collectAsState()
    val polished by vm.polishedText.collectAsState()
    val isPolishing by vm.isPolishing.collectAsState()
    val polishError by vm.polishError.collectAsState()
    val editableText by vm.editableText.collectAsState()
    val language by vm.language.collectAsState()
    val isPro by vm.isPro.collectAsState()
    val trialTick by vm.trialTick.collectAsState()

    var showTonePicker by remember { mutableStateOf(false) }

    // canRecord recomputed whenever pro status or trial usage changes.
    val canRecord = remember(isPro, trialTick) { SubscriptionManager.canRecord }
    val trialText = remember(trialTick) { SubscriptionManager.trialRemainingText }
    val trialExhausted = remember(trialTick) { SubscriptionManager.trialExhausted }

    // Auto-record when launched from the keyboard or widget.
    LaunchedEffect(autoRecordTrigger) {
        if (autoRecordTrigger > 0 && micGranted && canRecord &&
            !state.isRecording && !state.isTranscribing
        ) {
            vm.resetPolish()
            showTonePicker = false
            vm.transcription.startRecording()
        }
    }

    fun onMicTap() {
        if (state.isRecording) {
            vm.transcription.toggle()
            return
        }
        if (!micGranted) { onRequestPermission(); return }
        if (!canRecord) { onOpenPaywall(); return }
        vm.resetPolish()
        showTonePicker = false
        vm.transcription.startRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (returnToKeyboard) "Dictate" else "YapText", fontWeight = FontWeight.Bold) },
                actions = {
                    if (!returnToKeyboard) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            if (returnToKeyboard) {
                KeyboardDictationBadge()
                Spacer(Modifier.height(16.dp))
            }

            LanguagePicker(selected = language, onSelect = vm::setLanguage)

            if (!isPro) {
                Spacer(Modifier.height(12.dp))
                TrialBanner(text = trialText, exhausted = trialExhausted, onUpgrade = onOpenPaywall)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(24.dp))

            MicButton(
                isRecording = state.isRecording,
                isTranscribing = state.isTranscribing,
                enabled = !state.isTranscribing && !isPolishing,
                tint = micColor(state.isRecording, state.isTranscribing, canRecord),
                onTap = ::onMicTap,
            )

            if (state.isRecording) {
                Spacer(Modifier.height(16.dp))
                RecordingIndicator()
            }

            if (state.transcribedText.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                ResultsSection(
                    original = editableText,
                    onOriginalChange = vm::onEditText,
                    polished = polished,
                    onPolishedChange = vm::onEditPolished,
                    isPolishing = isPolishing,
                    polishError = polishError,
                    showTonePicker = showTonePicker,
                    onPolishCta = { showTonePicker = true },
                    onPickTone = { tone -> showTonePicker = false; vm.polish(tone) },
                    onCancelTones = { showTonePicker = false },
                )
            }

            state.errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp))
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun micColor(isRecording: Boolean, isTranscribing: Boolean, canRecord: Boolean): Color = when {
    isTranscribing -> YapOrange
    isRecording -> Color(0xFFE63946)
    !canRecord -> Color.Gray
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun KeyboardDictationBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Keyboard Dictation", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(selected: Language, onSelect: (Language) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Language", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 24.dp)) {
            Language.entries.forEachIndexed { index, lang ->
                SegmentedButton(
                    selected = selected == lang,
                    onClick = { onSelect(lang) },
                    shape = SegmentedButtonDefaults.itemShape(index, Language.entries.size),
                ) { Text(lang.displayName) }
            }
        }
    }
}

@Composable
private fun TrialBanner(text: String, exhausted: Boolean, onUpgrade: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (exhausted) Icons.Default.Lock else Icons.Default.Mic, null,
                tint = if (exhausted) MaterialTheme.colorScheme.error else YapOrange)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall,
                color = if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Button(onClick = onUpgrade, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                Text("Upgrade", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MicButton(
    isRecording: Boolean,
    isTranscribing: Boolean,
    enabled: Boolean,
    tint: Color,
    onTap: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart), label = "scale",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        if (isRecording) {
            Box(
                Modifier.size(160.dp).scale(scale)
                    .background(Color(0xFFE63946).copy(alpha = 0.25f), CircleShape)
            )
        }
        Surface(
            shape = CircleShape,
            color = tint,
            shadowElevation = 12.dp,
            modifier = Modifier.size(140.dp),
            onClick = onTap,
            enabled = enabled,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isTranscribing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                } else {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        tint = Color.White, modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Color(0xFFE63946), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("Recording — speak now", color = Color(0xFFE63946),
            fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsSection(
    original: String,
    onOriginalChange: (String) -> Unit,
    polished: String,
    onPolishedChange: (String) -> Unit,
    isPolishing: Boolean,
    polishError: String?,
    showTonePicker: Boolean,
    onPolishCta: () -> Unit,
    onPickTone: (Tone) -> Unit,
    onCancelTones: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (polished.isEmpty()) {
            EditableTextCard("Original (tap to edit)", original, onOriginalChange)
        } else {
            EditableTextCard("Polished (tap to edit)", polished, onPolishedChange, accent = YapGreen)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = YapGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copied to clipboard", color = YapGreen, style = MaterialTheme.typography.bodyMedium)
        }

        if (!isPolishing && !showTonePicker) {
            Button(onClick = onPolishCta, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (polished.isEmpty()) "Polish with AI" else "Polish Again", fontWeight = FontWeight.SemiBold)
            }
        }

        if (isPolishing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Polishing...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(visible = showTonePicker && !isPolishing) {
            TonePicker(onPick = onPickTone, onCancel = onCancelTones)
        }

        polishError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TextCard(label: String, text: String, accent: Color) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        ) {
            Text(text, modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp))
        }
    }
}

/** Editable text card — used for both the raw transcription and the polished result. */
@Composable
private fun EditableTextCard(
    label: String,
    text: String,
    onChange: (String) -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 240.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TonePicker(onPick: (Tone) -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Choose a tone:", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                Tone.entries.forEach { tone ->
                    ElevatedButton(
                        onClick = { onPick(tone) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                    ) {
                        Text(tone.displayName, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
