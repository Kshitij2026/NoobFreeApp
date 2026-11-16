package com.noobfree.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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

    GlassScreenScaffold(
        title = "Developer Options & Debugging",
        subtitle = "Direct Activity Launchers & Flag Controls",
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
            // Card 1: Direct Activity & Flag Launchers (Does NOT redirect only to About Tablet!)
            item {
                val badgeColor = when {
                    dev.isEnabled -> GlassPalette.EmeraldGlow
                    dev.isBlockedByAdminRestriction -> GlassPalette.AmberGlow
                    else -> GlassPalette.CyanGlow
                }
                val badgeText = when {
                    dev.isEnabled -> "ENABLED (1)"
                    dev.isBlockedByAdminRestriction -> "ADMIN LOCKED"
                    else -> "DISABLED (0)"
                }

                ControlCard(
                    title = "Direct Developer Options Launchers",
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
                        label = "Admin Restriction (DISALLOW_DEBUGGING_FEATURES)",
                        value = if (dev.isBlockedByAdminRestriction) "ENFORCED" else "Not Enforced",
                        valueColor = if (dev.isBlockedByAdminRestriction) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Direct Open Dev Options",
                            onClick = { viewModel.openDeveloperOptionsSettings() },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.CyanGlow
                        )
                        GlassSecondaryButton(
                            text = "Wireless / USB Debug",
                            onClick = { viewModel.openWirelessDebuggingSettings() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Try Direct Enable (1)",
                            onClick = { viewModel.enableDeveloperOptions() },
                            enabled = !uiState.isExecutingAction,
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.EmeraldGlow
                        )
                        GlassSecondaryButton(
                            text = "Try Disable (0)",
                            onClick = { viewModel.disableDeveloperOptions() },
                            enabled = !uiState.isExecutingAction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Card 2: Multi-Method Guide & About Tablet Shortcut
            item {
                ControlCard(
                    title = "Alternative Activation Methods",
                    subtitle = "Direct Activity vs. Flag Write vs. About Tablet",
                    icon = Icons.Outlined.TouchApp,
                    badgeText = "3 METHODS",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    val steps = dev.guidedSteps.ifEmpty {
                        listOf(
                            "Method A: Tap 'Direct Open Dev Options' above to launch DevelopmentSettingsDashboardActivity directly.",
                            "Method B: Tap 'Try Direct Enable (1)' above to test writing DEVELOPMENT_SETTINGS_ENABLED = 1.",
                            "Method C: Open About Tablet and tap Build number 7 times."
                        )
                    }
                    steps.forEach { step ->
                        Text(
                            text = "• $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassSecondaryButton(
                            text = "Open About Tablet",
                            onClick = { viewModel.openDeviceInfoSettings() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Info
                        )
                        GlassSecondaryButton(
                            text = "Admin Settings",
                            onClick = { viewModel.openDeviceAdminSettings() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Security
                        )
                    }
                }
            }

            // Card 3: OEM Compatibility Notes
            item {
                ControlCard(
                    title = "OEM & Tablet Compatibility",
                    subtitle = "Runtime Manufacturer Behavior",
                    icon = Icons.Outlined.Code,
                    badgeText = "OEM INFO",
                    badgeColor = GlassPalette.VioletGlow
                ) {
                    Text(
                        text = dev.manufacturerNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            // Card 4: Session Operation Audit Log
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
