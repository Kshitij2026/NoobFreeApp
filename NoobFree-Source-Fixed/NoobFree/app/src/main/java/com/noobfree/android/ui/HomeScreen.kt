package com.noobfree.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobfree.android.data.ShizukuConnectionState
import com.noobfree.android.system.AnimationManager
import com.noobfree.android.system.BackgroundProcessLimit
import com.noobfree.android.system.WallpaperDestination
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateToDeveloper: () -> Unit,
    onNavigateToAnimation: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    var selectedWallpaperDest by remember { mutableStateOf(WallpaperDestination.HOME_SCREEN) }
    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.applyWallpaper(uri, selectedWallpaperDest)
        }
    }

    val pendingBg = uiState.pendingBackgroundLimitConfirmation
    if (pendingBg != null) {
        GlassConfirmDialog(
            title = "Confirm Background Process Limit",
            confirmLabel = "Apply Limit",
            onConfirm = { viewModel.confirmBackgroundProcessLimitChange() },
            onDismiss = { viewModel.dismissBackgroundProcessLimitDialog() },
            accentColor = GlassPalette.VioletGlow
        ) {
            Text(
                text = "Change background process limit to: ${pendingBg.label}?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = pendingBg.impactDescription)
            Text(
                text = "Note: Requires an active Shizuku shell. Android resets this value back to Standard upon device reboot.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassPalette.AmberGlow
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Glass Hero Header
        item {
            GlassSurfaceCard(
                accentGlow = GlassPalette.CyanGlow,
                cornerRadius = 24.dp,
                contentPadding = 18.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatusPill(text = "GLASS EDITION v1.2.0", accentColor = GlassPalette.CyanGlow)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NOOBFREE",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Text(
                            text = uiState.deviceHardwareInfo?.let {
                                "${it.marketName} • Android ${it.androidVersion} (API ${it.sdkInt})"
                            } ?: "Device Management, App Debloat & Developer Control Center",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassPalette.TextSecondary
                        )
                    }
                    Row {
                        IconButton(onClick = { viewModel.refreshAllStates(showLoading = true) }) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh status",
                                tint = GlassPalette.CyanGlow
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings and About",
                                tint = GlassPalette.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isLoading || uiState.isExecutingAction) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = GlassPalette.CyanGlow,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }

        // Banner notification
        item {
            AnimatedVisibility(visible = uiState.bannerMessage != null) {
                val msg = uiState.bannerMessage.orEmpty()
                val isErr = uiState.bannerIsError
                val accent = if (isErr) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                GlassSurfaceCard(accentGlow = accent, contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        TextButton(onClick = { viewModel.dismissBanner() }) {
                            Text("Dismiss", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Card 1: Device Management & Halo / Admin Inspector
        item {
            val mgmt = uiState.managementReport
            val hasOwner = mgmt?.detectedDeviceOwnerPackage != null || mgmt?.detectedProfileOwnerPackage != null
            val activeAdminCount = mgmt?.adminAndManagementApps?.count { it.isActiveAdmin } ?: 0
            val haloCount = mgmt?.haloApps?.size ?: 0
            val badgeText = when {
                hasOwner -> "OWNER ACTIVE"
                activeAdminCount > 0 -> "$activeAdminCount ADMIN(S)"
                else -> "INSPECT READY"
            }
            val badgeColor = when {
                hasOwner -> GlassPalette.RoseGlow
                activeAdminCount > 0 -> GlassPalette.AmberGlow
                else -> GlassPalette.EmeraldGlow
            }

            ControlCard(
                title = "Admin, Google Policy & Halo Inspector",
                subtitle = mgmt?.overallManagementMode ?: "Inspecting DevicePolicyManager & UserManager...",
                icon = Icons.Outlined.Shield,
                badgeText = badgeText,
                badgeColor = badgeColor
            ) {
                Text(
                    text = mgmt?.overallSummary
                        ?: "Distinguish ordinary Device Administrators from Device Owner, inspect Google Android Device Policy & Halo apps, and run guided admin deactivation or factory reset workflows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassPalette.TextSecondary
                )
                if (mgmt != null) {
                    KeyValueRow(
                        label = "Active Device Admins",
                        value = "$activeAdminCount active component(s)"
                    )
                    KeyValueRow(
                        label = "Discovered Halo Packages",
                        value = if (haloCount > 0) "$haloCount Halo app(s) detected" else "None detected on this device"
                    )
                    KeyValueRow(
                        label = "NoobFree Admin Status",
                        value = when {
                            mgmt.noobFreeIsDeviceOwner -> "Device Owner"
                            mgmt.noobFreeIsProfileOwner -> "Profile Owner"
                            mgmt.noobFreeIsActiveAdmin -> "Active Legacy Device Admin"
                            else -> "Standard App (Admin Inactive)"
                        },
                        valueColor = if (mgmt.noobFreeIsActiveAdmin) GlassPalette.CyanGlow else GlassPalette.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassPrimaryButton(
                        text = "Open Admin & Halo Inspector",
                        onClick = onNavigateToPrivacy,
                        modifier = Modifier.weight(1f),
                        accentColor = GlassPalette.CyanGlow
                    )
                    GlassSecondaryButton(
                        text = "Admin Settings",
                        onClick = { viewModel.openDeviceAdminSettings() }
                    )
                }
            }
        }

        // Card 2: Developer Options (Shizuku-Independent Guided Workflow + Optional Shizuku)
        item {
            val dev = uiState.developerOptions
            val shizukuReady = uiState.shizukuStatus.isReady
            val badgeColor = when {
                dev.isBlockedByAdminRestriction -> GlassPalette.RoseGlow
                dev.isEnabled -> GlassPalette.EmeraldGlow
                else -> GlassPalette.AmberGlow
            }
            val badgeText = when {
                dev.isBlockedByAdminRestriction -> "POLICY BLOCKED"
                dev.isEnabled -> "ENABLED"
                else -> "7-TAP READY"
            }

            ControlCard(
                title = "Developer Options (No Shizuku Needed)",
                subtitle = dev.verificationSource,
                icon = Icons.Outlined.DeveloperMode,
                badgeText = badgeText,
                badgeColor = badgeColor
            ) {
                Text(
                    text = when {
                        dev.isBlockedByAdminRestriction ->
                            "UserManager reports DISALLOW_DEBUGGING_FEATURES = true. An active administrator blocks enabling Developer Options until deactivated or unenrolled."
                        dev.isEnabled ->
                            "Developer Options are active on this tablet! Tap 'Open Developer Options' to access USB Debugging, Wireless Debugging, or hardware overlays."
                        else ->
                            "Enable Developer Options without Shizuku: tap 'Open About Tablet (7-Tap)' below and tap Build number 7 times. NoobFree automatically verifies activation when you return."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassPalette.TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dev.isEnabled) {
                        GlassPrimaryButton(
                            text = "Open Developer Options",
                            onClick = { viewModel.openDeveloperOptionsSettings() },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.EmeraldGlow
                        )
                    } else {
                        GlassPrimaryButton(
                            text = "Open About Tablet (7-Tap)",
                            onClick = { viewModel.openDeviceInfoSettings() },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.CyanGlow
                        )
                    }
                    GlassSecondaryButton(
                        text = "Interactive Guide",
                        onClick = onNavigateToDeveloper
                    )
                }
                if (shizukuReady && !dev.isEnabled && !dev.isBlockedByAdminRestriction) {
                    GlassSecondaryButton(
                        text = "1-Tap Enable via Connected Shizuku Shell",
                        onClick = { viewModel.enableDeveloperOptions() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Card 3: App Manager, Uninstall & Safe System Debloater
        item {
            ControlCard(
                title = "App Manager & Safe Debloat Engine",
                subtitle = "Uninstall user apps • Deactivate admins first • Safe system debloat",
                icon = Icons.Outlined.Apps,
                badgeText = "READY",
                badgeColor = GlassPalette.VioletGlow
            ) {
                Text(
                    text = "Browse all installed user, system, Halo, and administrator apps with icons, versions, category filters, and safety badges. Uninstall eligible apps without Shizuku, or disable non-critical bloatware via Shizuku with 1-tap restore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassPalette.TextSecondary
                )
                GlassPrimaryButton(
                    text = "Launch App Manager & Debloater",
                    onClick = onNavigateToApps,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = GlassPalette.VioletGlow
                )
            }
        }

        // Card 4: Wallpaper Studio (Works Without Shizuku)
        item {
            val wp = uiState.wallpaperPreflight
            val canSet = wp?.canApplyWallpaper != false
            ControlCard(
                title = "Wallpaper Studio",
                subtitle = wp?.statusHeadline ?: "Checking WallpaperManager & DISALLOW_SET_WALLPAPER...",
                icon = Icons.Outlined.Image,
                badgeText = if (canSet) "AVAILABLE" else "RESTRICTED",
                badgeColor = if (canSet) GlassPalette.CyanGlow else GlassPalette.RoseGlow
            ) {
                Text(
                    text = wp?.statusDetail
                        ?: "Select any local JPEG, PNG, or WebP image. NoobFree safely downsamples high-resolution images to prevent memory errors on 2GB/3GB tablets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassPalette.TextSecondary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperDestination.entries.forEach { dest ->
                        val selected = selectedWallpaperDest == dest
                        FilterChip(
                            selected = selected,
                            onClick = { selectedWallpaperDest = dest },
                            label = { Text(dest.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GlassPalette.CyanGlow.copy(alpha = 0.22f),
                                selectedLabelColor = GlassPalette.CyanGlow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selected) GlassPalette.CyanGlow else Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
                GlassPrimaryButton(
                    text = if (uiState.isExecutingAction) "Applying Wallpaper..." else "Pick Image & Apply to ${selectedWallpaperDest.label}",
                    onClick = { wallpaperLauncher.launch(arrayOf("image/*")) },
                    enabled = canSet && !uiState.isExecutingAction,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = GlassPalette.CyanGlow,
                    icon = Icons.Outlined.Image
                )
            }
        }

        // Card 5: Optional Shizuku Privileged Bridge
        item {
            val status = uiState.shizukuStatus
            val badgeColor = when (status.state) {
                ShizukuConnectionState.CONNECTED -> GlassPalette.EmeraldGlow
                ShizukuConnectionState.PERMISSION_REQUIRED,
                ShizukuConnectionState.BINDING_SERVICE -> GlassPalette.AmberGlow
                else -> GlassPalette.TextSecondary
            }
            val connectLabel = when (status.state) {
                ShizukuConnectionState.NOT_INSTALLED -> "Get Shizuku (Optional)"
                ShizukuConnectionState.SERVICE_STOPPED -> "Open Shizuku"
                ShizukuConnectionState.PERMISSION_REQUIRED -> "Authorize Shizuku"
                ShizukuConnectionState.PERMISSION_DENIED_PERMANENTLY -> "Open Shizuku"
                ShizukuConnectionState.BINDING_SERVICE -> "Connecting..."
                ShizukuConnectionState.CONNECTED -> "Re-verify Bridge"
            }

            ControlCard(
                title = "Optional Shizuku Shell Bridge",
                subtitle = "Privilege: ${status.privilegeLabel} (Optional for ADB-level features)",
                icon = Icons.Outlined.Security,
                badgeText = status.statusTitle.uppercase(),
                badgeColor = badgeColor
            ) {
                Text(
                    text = "${status.statusDescription} Shizuku is strictly optional and only needed for system-package disable (`pm disable-user`), animation scales, and background process limits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassPalette.TextSecondary
                )
                if (status.errorDetail != null) {
                    Text(
                        text = status.errorDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.RoseGlow
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassPrimaryButton(
                        text = connectLabel,
                        onClick = { viewModel.onConnectOrAuthorizeShizuku() },
                        modifier = Modifier.weight(1f),
                        accentColor = GlassPalette.VioletGlow
                    )
                    GlassSecondaryButton(
                        text = "Refresh",
                        onClick = { viewModel.refreshAllStates(showLoading = true) },
                        icon = Icons.Outlined.Refresh
                    )
                }
            }
        }

        // Card 6: Animation Scale & Performance
        item {
            val anim = uiState.animationState
            val currentLabel = anim.uniformScale?.let { "${AnimationManager.formatScaleForShell(it)}x" }
                ?: "Custom (${anim.windowScale}x / ${anim.transitionScale}x / ${anim.animatorScale}x)"
            ControlCard(
                title = "System Animation Scales",
                subtitle = "Current: $currentLabel • ${anim.source}",
                icon = Icons.Outlined.Animation,
                badgeText = currentLabel,
                badgeColor = GlassPalette.CyanGlow
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimationManager.PRESET_SCALES.forEach { preset ->
                        val uniformScale = anim.uniformScale
                        val selected = uniformScale != null && abs(uniformScale - preset) < 0.01f
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setAnimationScale(preset) },
                            label = {
                                Text("${AnimationManager.formatScaleForShell(preset)}x")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GlassPalette.CyanGlow.copy(alpha = 0.22f),
                                selectedLabelColor = GlassPalette.CyanGlow
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.setAnimationScale(1f) }) {
                        Text("Restore 1x Default", color = GlassPalette.CyanGlow)
                    }
                    TextButton(onClick = onNavigateToAnimation) {
                        Text("Detailed Controls ->", color = GlassPalette.TextSecondary)
                    }
                }
            }
        }

        // Card 7: USB & Wireless Debugging + Refresh Rate + Background Processes
        item {
            val dbg = uiState.debugState
            val rr = uiState.refreshRateInfo
            ControlCard(
                title = "Debugging, Display & Memory Limits",
                subtitle = "Active Panel: ${rr.activeRefreshRateHz.roundToInt()} Hz • Process Limit: ${uiState.backgroundProcessLimit.label}",
                icon = Icons.Outlined.Usb,
                badgeText = if (dbg.adbEnabled) "ADB ON" else "ADB OFF",
                badgeColor = if (dbg.adbEnabled) GlassPalette.EmeraldGlow else GlassPalette.TextSecondary
            ) {
                KeyValueRow("USB Debugging (ADB)", if (dbg.adbEnabled) "Enabled" else "Disabled")
                KeyValueRow(
                    "Wireless Debugging",
                    if (!dbg.wirelessDebuggingSupported) "Requires Android 11+"
                    else if (dbg.wirelessAdbEnabled) "Enabled" else "Disabled"
                )
                KeyValueRow("Display Refresh Rate", "${rr.activeRefreshRateHz.roundToInt()} Hz (Peak ${rr.peakSupportedHz.roundToInt()} Hz)")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackgroundProcessLimit.entries.forEach { option ->
                        val selected = option == uiState.backgroundProcessLimit
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.requestBackgroundProcessLimitChange(option) },
                            label = {
                                Text(
                                    when (option) {
                                        BackgroundProcessLimit.STANDARD -> "Std RAM"
                                        BackgroundProcessLimit.NO_BACKGROUND -> "0 Bg"
                                        else -> "Max ${option.value}"
                                    }
                                )
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassSecondaryButton(
                        text = "Debugging Settings",
                        onClick = { viewModel.openWirelessDebuggingSettings() },
                        modifier = Modifier.weight(1f)
                    )
                    GlassSecondaryButton(
                        text = "Display Settings",
                        onClick = { viewModel.openDisplaySettings() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Card 8: Hardware & Runtime Telemetry
        item {
            val hw = uiState.deviceHardwareInfo
            ControlCard(
                title = "Device Hardware & Runtime Verification",
                subtitle = hw?.marketName ?: "Reading hardware...",
                icon = Icons.Outlined.PhoneAndroid,
                badgeText = hw?.let { "API ${it.sdkInt}" } ?: "INFO",
                badgeColor = GlassPalette.VioletGlow
            ) {
                if (hw != null) {
                    KeyValueRow("Manufacturer / Model", "${hw.manufacturer} ${hw.model} (${hw.deviceCodename})")
                    KeyValueRow("Runtime Android Version", "Android ${hw.androidVersion} (SDK ${hw.sdkInt})")
                    KeyValueRow("Security Patch / Build", "${hw.securityPatch} • ${hw.buildId}")
                    KeyValueRow("Memory (RAM)", "${hw.availableRamFormatted} free / ${hw.totalRamFormatted}")
                    KeyValueRow("Display Geometry", "${hw.screenResolution} @ ${hw.screenDensityDpi} dpi")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassPrimaryButton(
                        text = "Full Hardware & GPU Specs",
                        onClick = onNavigateToDeviceInfo,
                        modifier = Modifier.weight(1f),
                        accentColor = GlassPalette.VioletGlow
                    )
                    GlassSecondaryButton(
                        text = "About Tablet",
                        onClick = { viewModel.openDeviceInfoSettings() },
                        icon = Icons.Outlined.Info
                    )
                }
            }
        }
    }
}
