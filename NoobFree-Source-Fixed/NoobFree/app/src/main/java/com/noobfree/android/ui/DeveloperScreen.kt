package com.noobfree.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobfree.android.system.DeveloperManager
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel

@Composable
fun DeveloperScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val dev = uiState.developerOptions
    val dbg = uiState.debugState
    val shizukuReady = uiState.shizukuStatus.isReady

    GlassScreenScaffold(
        title = "Developer Options & Debugging",
        subtitle = "Shizuku-Independent 7-Tap Workflow & ADB Diagnostics",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.refreshAllStates(showLoading = true) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = GlassPalette.CyanGlow)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card 1: Live Developer Options & Debugging Status
            item {
                val badgeColor = when {
                    dev.isBlockedByAdminRestriction -> GlassPalette.RoseGlow
                    dev.isEnabled -> GlassPalette.EmeraldGlow
                    else -> GlassPalette.AmberGlow
                }
                val badgeText = when {
                    dev.isBlockedByAdminRestriction -> "POLICY LOCKED"
                    dev.isEnabled -> "ENABLED (1)"
                    else -> "DISABLED (0)"
                }

                ControlCard(
                    title = "Live Developer Options Status",
                    subtitle = dev.verificationSource,
                    icon = Icons.Outlined.DeveloperMode,
                    badgeText = badgeText,
                    badgeColor = badgeColor
                ) {
                    KeyValueRow(
                        label = "Developer Options Menu",
                        value = if (dev.isEnabled) "Active (DEVELOPMENT_SETTINGS_ENABLED = 1)" else "Hidden (DEVELOPMENT_SETTINGS_ENABLED = 0)",
                        valueColor = if (dev.isEnabled) GlassPalette.EmeraldGlow else GlassPalette.AmberGlow
                    )
                    KeyValueRow(
                        label = "USB Debugging (ADB)",
                        value = if (dbg.adbEnabled) "Enabled (adb_enabled = 1)" else "Disabled (adb_enabled = 0)",
                        valueColor = if (dbg.adbEnabled) GlassPalette.EmeraldGlow else GlassPalette.TextPrimary
                    )
                    KeyValueRow(
                        label = "Wireless Debugging",
                        value = if (!dbg.wirelessDebuggingSupported) "Requires Android 11+" else if (dbg.wirelessAdbEnabled) "Enabled" else "Disabled"
                    )
                    KeyValueRow(
                        label = "Admin Restriction (DISALLOW_DEBUGGING_FEATURES)",
                        value = if (dev.isBlockedByAdminRestriction) "ENFORCED (Blocked by Admin)" else "Not Enforced (Allowed)",
                        valueColor = if (dev.isBlockedByAdminRestriction) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Open Developer Options",
                            onClick = { viewModel.openDeveloperOptionsSettings() },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.CyanGlow
                        )
                        GlassSecondaryButton(
                            text = "Verify Status",
                            onClick = { viewModel.refreshAllStates(showLoading = true) },
                            icon = Icons.Outlined.Refresh
                        )
                    }
                }
            }

            // Card 2: Shizuku-Independent 7-Tap Guided Activation
            item {
                ControlCard(
                    title = "Enable Without Shizuku (7-Tap Guide)",
                    subtitle = "Official Android activation flow — no root, PC, or Shizuku needed",
                    icon = Icons.Outlined.TouchApp,
                    badgeText = if (dev.isEnabled) "COMPLETED" else "STEP-BY-STEP",
                    badgeColor = if (dev.isEnabled) GlassPalette.EmeraldGlow else GlassPalette.CyanGlow
                ) {
                    Text(
                        text = "Android's security sandbox prevents any ordinary app from silently simulating taps inside Settings or writing Settings.Global without shell/system permissions. Use the direct shortcut below to unlock Developer Options in under 10 seconds:",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    val steps = dev.guidedSteps.ifEmpty {
                        listOf(
                            "Step 1: Tap 'Open About Tablet' below.",
                            "Step 2: Scroll to 'Build number' (or Software Information -> Build number).",
                            "Step 3: Tap 'Build number' 7 times rapidly until 'You are now a developer!' appears.",
                            "Step 4: Enter your lock-screen PIN/pattern if prompted.",
                            "Step 5: Return to NoobFree—status updates automatically on resume!"
                        )
                    }
                    steps.forEach { step ->
                        Text(
                            text = "• $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                    if (dev.isBlockedByAdminRestriction) {
                        Text(
                            text = "Diagnostic Warning: UserManager reports DISALLOW_DEBUGGING_FEATURES is active on this tablet. Android Settings will block tapping Build number until the controlling Device Owner or administrator (Google Android Device Policy / Halo Agent) lifts the restriction or unenrolls the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.RoseGlow
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "1. Open About Tablet",
                            onClick = { viewModel.openDeviceInfoSettings() },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.CyanGlow,
                            icon = Icons.Outlined.Info
                        )
                        GlassSecondaryButton(
                            text = "2. Open Dev Options",
                            onClick = { viewModel.openDeveloperOptionsSettings() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Card 3: Distinction Between Developer Options & USB Debugging
            item {
                ControlCard(
                    title = "Developer Options vs. USB Debugging",
                    subtitle = "Understanding Android's Two-Tier Debugging Model",
                    icon = Icons.Outlined.Usb,
                    badgeText = "ARCHITECTURE",
                    badgeColor = GlassPalette.VioletGlow
                ) {
                    Text(
                        text = "1. Developer Options Menu (DEVELOPMENT_SETTINGS_ENABLED): Unlocks the hidden system settings menu for animation scales, GPU profiling, display cutouts, and background limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = "2. USB & Wireless Debugging (ADB_ENABLED / adb_wifi_enabled): Starts the privileged adbd daemon (UID 2000). Even after Developer Options are unlocked, Android requires you to manually toggle USB Debugging or Wireless Debugging inside Developer Options and approve the RSA/pairing dialog.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            // Card 4: Optional Shizuku Shell Direct Flag Control
            item {
                ControlCard(
                    title = "Optional Shizuku Shell Toggle",
                    subtitle = if (shizukuReady) "Shizuku Connected — Direct Flag Write Available" else "Requires Shizuku Connection (Optional)",
                    icon = Icons.Outlined.Security,
                    badgeText = if (shizukuReady) "READY" else "OPTIONAL",
                    badgeColor = if (shizukuReady) GlassPalette.EmeraldGlow else GlassPalette.TextSecondary
                ) {
                    Text(
                        text = "When Shizuku is running and authorized, NoobFree can write `settings put global development_settings_enabled 1` or `0` directly and verify the result.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    Text(
                        text = "Allowlisted Command: ${DeveloperManager.CMD_ENABLE_DEV_OPTIONS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassPalette.CyanGlow
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Enable via Shizuku (1)",
                            onClick = { viewModel.enableDeveloperOptions() },
                            enabled = shizukuReady && !uiState.isExecutingAction && !dev.isBlockedByAdminRestriction,
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.EmeraldGlow
                        )
                        GlassSecondaryButton(
                            text = "Disable via Shizuku (0)",
                            onClick = { viewModel.disableDeveloperOptions() },
                            enabled = shizukuReady && !uiState.isExecutingAction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Card 5: OEM Compatibility Notes
            item {
                ControlCard(
                    title = "OEM & Tablet Compatibility",
                    subtitle = "Runtime Manufacturer Behavior",
                    icon = Icons.Outlined.Code,
                    badgeText = "OEM INFO",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    Text(
                        text = dev.manufacturerNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            // Card 6: Session Operation Audit Log
            item {
                ControlCard(
                    title = "Operation Verification Log",
                    subtitle = "${uiState.operationHistory.size} recorded event(s) in this session",
                    icon = Icons.Outlined.History,
                    badgeText = "AUDIT LOG",
                    badgeColor = GlassPalette.VioletGlow
                ) {
                    if (uiState.operationHistory.isEmpty()) {
                        Text(
                            text = "No operations recorded in this session yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.TextSecondary
                        )
                    }
                }
            }

            items(uiState.operationHistory, key = { it.id }) { entry ->
                GlassSurfaceCard(
                    accentGlow = if (entry.success) GlassPalette.EmeraldGlow else GlassPalette.RoseGlow,
                    cornerRadius = 16.dp,
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[${entry.timestamp}] ${entry.category} • ${entry.action}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        StatusPill(
                            text = if (entry.success) "OK" else "FAIL",
                            accentColor = if (entry.success) GlassPalette.EmeraldGlow else GlassPalette.RoseGlow
                        )
                    }
                    if (entry.command != null) {
                        Text(
                            text = entry.command,
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassPalette.CyanGlow
                        )
                    }
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }
        }
    }
}
