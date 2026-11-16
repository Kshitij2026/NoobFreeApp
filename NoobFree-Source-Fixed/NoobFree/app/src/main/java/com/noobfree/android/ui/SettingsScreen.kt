package com.noobfree.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noobfree.android.BuildConfig
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    GlassScreenScaffold(
        title = "Settings, Privilege Guide & About",
        subtitle = "NoobFree v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ControlCard(
                    title = "Glassmorphism Theme Mode",
                    subtitle = "Cosmic Dark Glass vs. Frosted Light Glass",
                    icon = Icons.Outlined.Palette,
                    badgeText = if (darkTheme) "DARK GLASS" else "LIGHT GLASS",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cosmic Dark Glass Mode", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                "Deep navy/purple mesh gradient with translucent frosted surfaces and neon cyan/violet borders",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassPalette.TextSecondary
                            )
                        }
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GlassPalette.CyanGlow,
                                checkedTrackColor = GlassPalette.CyanGlow.copy(alpha = 0.28f)
                            )
                        )
                    }
                }
            }

            item {
                ControlCard(
                    title = "Android Privilege Tiers Explained",
                    subtitle = "Ordinary App vs. Device Admin vs. Shizuku vs. Device Owner",
                    icon = Icons.Outlined.Security,
                    badgeText = "PLATFORM GUIDE",
                    badgeColor = GlassPalette.VioletGlow
                ) {
                    Text(
                        text = "1. Standard App Permissions (No Shizuku needed): App scanning, Wallpaper Studio, 7-tap Developer Options guide, Admin/Halo inspection, and standard user-app uninstall confirmations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = "2. Ordinary Legacy Device Administrator (DeviceAdminReceiver): Grants user-consented screen lock (lockNow) and legacy wipeData requests. Cannot silently uninstall apps, cannot deactivate other admins, and does NOT equal Device Owner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = "3. Optional Shizuku ADB Shell Bridge (UID 2000): Enables `pm disable-user --user 0` for safe bloatware disabling, animation scale changes, and background process limits without root.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = "4. Device Owner / Profile Owner (Enterprise DPC): Provisioned during factory setup or Zero-Touch enrollment (e.g., Google Android Device Policy or Halo Agent). Only the controlling DPC or organization portal can release Device Owner locks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.AmberGlow
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassSecondaryButton(
                            text = "Shizuku Manager",
                            onClick = { viewModel.openShizukuApp() },
                            modifier = Modifier.weight(1f)
                        )
                        GlassSecondaryButton(
                            text = "NoobFree App Info",
                            onClick = { viewModel.openAppDetailsSettings() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                ControlCard(
                    title = "APK Signing & Seamless Upgrades",
                    subtitle = "Preventing INSTALL_FAILED_UPDATE_INCOMPATIBLE",
                    icon = Icons.Outlined.Key,
                    badgeText = "STABLE ID",
                    badgeColor = GlassPalette.EmeraldGlow
                ) {
                    Text(
                        text = "NoobFree v1.2.0 unifies applicationId (`com.noobfree.android`) across builds and uses persistent signing in GitHub Actions so updates install cleanly over previous versions without package conflicts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            item {
                ControlCard(
                    title = "Security & Privacy Guarantee",
                    subtitle = "Zero telemetry • Offline-first • Allowlisted Shell",
                    icon = Icons.Outlined.PrivacyTip,
                    badgeText = "OFFLINE SAFE",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    Text(
                        text = "• No INTERNET permission requested in AndroidManifest.xml.\n" +
                            "• Zero analytics, trackers, or hidden background services.\n" +
                            "• Shizuku UserService enforces a strict regex command allow-list and blocks arbitrary shell execution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            item {
                ControlCard(
                    title = "About NoobFree",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                    icon = Icons.Outlined.Info,
                    badgeText = "v${BuildConfig.VERSION_NAME}",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    KeyValueRow("Application", "NoobFree")
                    KeyValueRow("Package ID", BuildConfig.APPLICATION_ID)
                    KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
                    KeyValueRow("Target SDK", "Android 8.0–16+ (API 26–35)")
                    uiState.deviceHardwareInfo?.manufacturerCompatibilityNotes?.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
