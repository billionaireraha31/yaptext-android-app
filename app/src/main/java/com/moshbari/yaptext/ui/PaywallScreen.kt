package com.moshbari.yaptext.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moshbari.yaptext.Config
import com.moshbari.yaptext.data.LicenseService
import com.moshbari.yaptext.data.SubscriptionManager
import com.moshbari.yaptext.ui.theme.YapGreen
import kotlinx.coroutines.launch

@Composable
fun PaywallScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var license by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }

        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("YapText Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Unlimited voice-to-text in English, Bengali & Banglish",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(20.dp))

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Feature(Icons.Default.CheckCircle, "Unlimited dictation")
                Feature(Icons.Default.Language, "English, Bengali & Banglish")
                Feature(Icons.Default.AutoAwesome, "8 AI-polished tones")
                Feature(Icons.Default.Lock, "One-time purchase via JVZoo")
            }
        }

        Spacer(Modifier.height(24.dp))

        if (unlocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = YapGreen)
                Spacer(Modifier.width(8.dp))
                Text("Pro unlocked — thank you!", color = YapGreen, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Start using Pro") }
        } else {
            // Step 1 — buy on JVZoo
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Config.JVZOO_CHECKOUT_URL))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Get YapText Pro", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Opens secure JVZoo checkout in your browser.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Step 2 — already purchased: enter license key
            Text("Already purchased?", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = license,
                onValueChange = { license = it; message = null },
                label = { Text("License key") },
                singleLine = true,
                enabled = !verifying,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    verifying = true
                    message = null
                    scope.launch {
                        when (val r = SubscriptionManager.redeemLicense(license)) {
                            is LicenseService.Result.Valid -> unlocked = true
                            is LicenseService.Result.Invalid -> message = r.message
                            is LicenseService.Result.Unreachable -> message = r.message
                        }
                        verifying = false
                    }
                },
                enabled = !verifying && license.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (verifying) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Unlock Pro")
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Payment is processed by JVZoo. A one-time purchase unlocks YapText Pro " +
                "on this device. For help, contact support@yaptext.com.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = YapGreen, modifier = Modifier.size(24.dp).width(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(text)
    }
}
