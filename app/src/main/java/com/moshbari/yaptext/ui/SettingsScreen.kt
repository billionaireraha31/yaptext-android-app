package com.moshbari.yaptext.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moshbari.yaptext.data.AppStorage
import com.moshbari.yaptext.data.Language
import com.moshbari.yaptext.data.SubscriptionManager
import com.moshbari.yaptext.ui.theme.YapGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit, onOpenPaywall: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppStorage.language) }
    var isPro by remember { mutableStateOf(SubscriptionManager.isPro.value) }
    var trialTick by remember { mutableIntStateOf(0) }

    var versionTaps by remember { mutableIntStateOf(0) }
    var debugUnlocked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Subscription
            SectionCard("Subscription") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isPro) Icons.Default.CheckCircle else Icons.Default.Lock, null,
                        tint = if (isPro) YapGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(if (isPro) "YapText Pro — Active" else "Free Trial", fontWeight = FontWeight.Medium)
                }
                if (!isPro) {
                    Spacer(Modifier.height(8.dp))
                    Text(SubscriptionManager.trialRemainingText,
                        color = if (SubscriptionManager.trialExhausted) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenPaywall) { Text("Upgrade to Pro") }
                }
            }

            // Voice language
            SectionCard("Voice Language") {
                Language.entries.forEach { lang ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            language = lang; AppStorage.language = lang
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == lang, onClick = {
                            language = lang; AppStorage.language = lang
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(lang.displayName)
                    }
                }
                Text("YapText automatically picks the best engine for each language.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Privacy
            SectionCard("Privacy") {
                LinkRow("Privacy Policy") { open(context, "https://yaptext.com/privacy") }
                LinkRow("Terms of Service") { open(context, "https://yaptext.com/terms") }
                LinkRow("Reset Data Consent") { AppStorage.resetDataConsent() }
            }

            // About — 7 taps on version unlocks the developer section (matches iOS)
            SectionCard("About") {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        versionTaps++
                        if (versionTaps >= 7) debugUnlocked = true
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Version")
                    Text("2.0 (1)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (debugUnlocked) {
                SectionCard("Developer") {
                    Text("Trial seconds used: ${AppStorage.trialSecondsUsed.toInt()}s",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        AppStorage.resetTrialSecondsUsed()
                        SubscriptionManager.noteTrialChanged()
                        trialTick++
                    }) { Text("Reset Free Trial") }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(text, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}

private fun open(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
