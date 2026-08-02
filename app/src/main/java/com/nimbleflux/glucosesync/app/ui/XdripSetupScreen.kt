package com.nimbleflux.glucosesync.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimbleflux.glucosesync.app.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XdripSetupScreen(
    checking: Boolean = false,
    checkResult: Boolean? = null,
    elapsedSec: Int = 0,
    broadcastsSeen: Int = 0,
    broadcastsAccepted: Int = 0,
    onCheckConnection: () -> Unit = {},
    onConnected: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("xDrip+ Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero icon
            Icon(
                Icons.Filled.Sensors,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "xDrip+ Direct Sensor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Read glucose directly from your sensor — no cloud dependency",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Advanced user warning
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "xDrip+ is designed for advanced users who are comfortable setting up their own sensor reading app. " +
                            "If you prefer a simpler setup, choose a cloud-based provider instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Why section
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Why use xDrip+?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BenefitItem("Works without internet — data stays on your phone")
                    BenefitItem("No account needed with any cloud service")
                    BenefitItem("Supports Libre 1/2/3, Dexcom G5/G6, and more")
                    BenefitItem("Faster updates — readings arrive instantly")
                    BenefitItem("Open source and trusted by the DIY community")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Steps
            Text(
                "Setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            SetupStep(
                number = 1,
                title = "Install xDrip+",
                description = "Download from the xDrip+ website (not on Play Store)"
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NightscoutFoundation/xDrip"))
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open website", style = MaterialTheme.typography.labelSmall)
                }
            }

            SetupStep(
                number = 2,
                title = "Configure your sensor",
                description = "Select your sensor type (Libre, Dexcom, etc.) in xDrip+ and follow its setup wizard"
            )

            SetupStep(
                number = 3,
                title = "Enable broadcasting",
                description = "In xDrip+ → ⚙ Settings → Inter-App Settings → turn ON \"Broadcast Locally\". " +
                    "Broadcasts are delivered automatically — no \"Identify receiver\" step needed."
            )

            SetupStep(
                number = 4,
                title = "Enable history backfill (optional)",
                description = "In the same Inter-App Settings screen, turn ON \"xDrip Web Service\". " +
                    "This lets GlucoseSync backfill up to 24h of chart history on connect. " +
                    "Without it, the chart fills point-by-point as new readings arrive (~5 min apart)."
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Check connection
            Button(
                onClick = onCheckConnection,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Listening… ${"%d:%02d".format(elapsedSec / 60, elapsedSec % 60)}")
                } else {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Connection", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Live diagnostics while listening. Counting broadcasts seen vs.
            // accepted lets the user tell the three failure modes apart:
            //   seen == 0                  -> nothing arriving from xDrip+
            //   seen > 0, accepted == 0    -> arriving but rejected (key/format)
            //   accepted > 0               -> working (success path handles it)
            if (checking) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (broadcastsSeen > 0) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Broadcasts seen: $broadcastsSeen · Accepted: $broadcastsAccepted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val remaining = 300 - elapsedSec
                        Text(
                            when {
                                broadcastsSeen == 0 && remaining > 0 ->
                                    "No broadcasts yet. Readings arrive ~every 5 min — keep xDrip+ running with \"Broadcast Locally\" on."
                                broadcastsSeen > 0 && broadcastsAccepted == 0 && remaining > 0 ->
                                    "Broadcasts are arriving but being rejected — likely an xDrip+ version/format mismatch."
                                remaining > 0 ->
                                    "Listening — next reading should arrive in ~${"%d".format(remaining / 60 + 1)} min."
                                else ->
                                    "No reading received in this window."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = checkResult != null, enter = fadeIn()) {
                when (checkResult) {
                    true -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connected! xDrip+ is sending readings.", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        LaunchedEffect(Unit) { delay(1500); onConnected() }
                    }
                    false -> {
                        // Contextual failure message based on what we observed
                        // during the listen window, instead of a generic hint.
                        val (title, body) = when {
                            broadcastsSeen == 0 -> "No broadcasts received" to
                                "xDrip+ didn't send anything to GlucoseSync. Make sure xDrip+ is running, has received at least one reading, and \"Broadcast Locally\" (Settings → Inter-App Settings) is enabled."
                            broadcastsAccepted == 0 -> "Broadcasts rejected" to
                                "xDrip+ is broadcasting, but GlucoseSync couldn't read the glucose value — likely an xDrip+ version mismatch. Make sure you're on a recent xDrip+ build."
                            else -> "No reading received" to
                                "Broadcasts were accepted but none arrived within the listen window. Try again — readings only arrive every ~5 minutes."
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(title, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    null -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "xDrip+ must stay running in the background. Both apps run simultaneously — xDrip+ reads the sensor, GlucoseSync displays the data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    description: String,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (trailingContent != null) {
                Spacer(modifier = Modifier.height(6.dp))
                trailingContent()
            }
        }
    }
}
