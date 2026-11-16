package com.noobfree.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobfree.android.system.AdminPolicyManager
import com.noobfree.android.system.DiscoveredAdminOrManagementApp
import com.noobfree.android.system.ManagementPrivilegeRole
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel

@Composable
fun PrivacyInspectorScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    adminPolicyManager: AdminPolicyManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(uiState.managementReport ?: adminPolicyManager.inspectDeviceManagement()) }
    var actionFeedbackTitle by remember { mutableStateOf<String?>(null) }
    var actionFeedbackBody by remember { mutableStateOf<String?>(null) }
    var actionFeedbackIsError by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showRecoveryGuide by remember { mutableStateOf(false) }
    var pendingDeactivationPackage by remember { mutableStateOf<String?>(null) }

    fun rescan() {
        val fresh = adminPolicyManager.inspectDeviceManagement()
        report = fresh
        viewModel.refreshAllStates(showLoading = false)
        if (pendingDeactivationPackage != null) {
            val target = fresh.adminAndManagementApps.find { it.packageName == pendingDeactivationPackage }
            if (target != null && !target.isActiveAdmin) {
                actionFeedbackTitle = "Administrator Deactivated: ${target.label}"
                actionFeedbackBody = "${target.label} (${target.packageName}) is no longer an active Device Administrator. You can now tap 'Step 3: Uninstall App' on its card below."
                actionFeedbackIsError = false
                pendingDeactivationPackage = null
            }
        }
    }

    LaunchedEffect(uiState.managementReport) {
        uiState.managementReport?.let { report = it }
    }

    if (showResetConfirmation) {
        GlassConfirmDialog(
            title = "Confirm Authorized Factory Reset",
            confirmLabel = "Execute wipeData(0)",
            onConfirm = {
                showResetConfirmation = false
                val outcome = adminPolicyManager.performAuthorizedFactoryReset()
                actionFeedbackTitle = outcome.title
                actionFeedbackBody = outcome.message
                actionFeedbackIsError = !outcome.success
                rescan()
            },
            onDismiss = { showResetConfirmation = false },
            accentColor = GlassPalette.RoseGlow
        ) {
            Text(
                text = "1. Permanent Data Erasure: All local apps, photos, downloads, accounts, and user settings on this tablet will be permanently erased.",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "2. Enterprise / Zero-Touch Re-enrollment Warning: If this Acer tablet's serial number or IMEI is registered in Android Zero-Touch Enrollment or an organization's MDM portal (Google Android Device Policy / Halo), performing a factory reset will NOT unenroll it—the tablet will automatically re-download the organization profile when connected to Wi-Fi or cellular data during initial setup.",
                color = GlassPalette.AmberGlow
            )
            Text(
                text = "3. Android 14–16+ API Boundary: Ordinary legacy Device Administrators may be blocked by Android from calling wipeData(0) unless NoobFree is Device Owner. If Android rejects the call, NoobFree will report the exact SecurityException and offer the System Reset Settings shortcut.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showRecoveryGuide) {
        GlassConfirmDialog(
            title = "Recovery-Mode Factory Reset Guide (Acer One 8 T4-82L-G2)",
            confirmLabel = "I Understand",
            onConfirm = { showRecoveryGuide = false },
            onDismiss = { showRecoveryGuide = false },
            accentColor = GlassPalette.CyanGlow
        ) {
            Text(
                text = "Before proceeding, back up all personal photos and files. Only reset a tablet you personally own.",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text("Step 1: Power the tablet completely OFF and disconnect any USB cable.")
            Text("Step 2: Enter Android Recovery using your Acer tablet's documented hardware key combination (commonly Power + Volume Up held together until the boot menu or Acer logo appears; consult your Acer One 8 T4-82L-G2 user manual as hardware revisions can vary between Volume Up and Volume Down).")
            Text("Step 3: If a boot mode micro-menu appears ([Recovery Mode], [Fastboot Mode], [Normal]), use Volume Up to move the arrow to [Recovery Mode] and press Volume Down (or Power, per on-screen legend) to select.")
            Text("Step 4: If an Android 'No Command' screen appears, hold Power and briefly press Volume Up once to reveal the Android Recovery menu.")
            Text("Step 5: Use Volume keys to highlight 'Wipe data/factory reset' -> press Power -> select 'Factory data reset' -> 'Reboot system now'.")
            Text(
                text = "Important Zero-Touch Warning: Hardware recovery reset wipes local storage but does NOT remove cloud-bound Zero-Touch or FRP enrollment. If the tablet re-enrolls during setup, the issuing organization must release the serial/IMEI in their MDM console.",
                color = GlassPalette.AmberGlow,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    GlassScreenScaffold(
        title = "Device Policy & Admin Inspector",
        subtitle = report.overallManagementMode,
        onBack = onBack,
        actions = {
            IconButton(onClick = { rescan() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Rescan", tint = GlassPalette.CyanGlow)
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
            // Feedback banner if an action was just performed
            if (actionFeedbackTitle != null && actionFeedbackBody != null) {
                item {
                    val accent = if (actionFeedbackIsError) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                    ControlCard(
                        title = actionFeedbackTitle!!,
                        subtitle = "Operation Diagnostic Result",
                        icon = if (actionFeedbackIsError) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
                        badgeText = if (actionFeedbackIsError) "BLOCKED / ERROR" else "VERIFIED",
                        badgeColor = accent
                    ) {
                        Text(
                            text = actionFeedbackBody!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        GlassSecondaryButton(
                            text = "Dismiss Message",
                            onClick = {
                                actionFeedbackTitle = null
                                actionFeedbackBody = null
                            }
                        )
                    }
                }
            }

            // Section 1: Overall Device Management Mode
            item {
                val hasOwner = report.detectedDeviceOwnerPackage != null || report.detectedProfileOwnerPackage != null
                val badgeColor = when {
                    hasOwner -> GlassPalette.RoseGlow
                    report.adminAndManagementApps.any { it.isActiveAdmin } -> GlassPalette.AmberGlow
                    else -> GlassPalette.EmeraldGlow
                }
                ControlCard(
                    title = report.overallManagementMode,
                    subtitle = "Live DevicePolicyManager & UserManager Telemetry",
                    icon = Icons.Outlined.Policy,
                    badgeText = if (hasOwner) "MANAGED" else "INSPECTED",
                    badgeColor = badgeColor
                ) {
                    Text(
                        text = report.overallSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassPalette.TextSecondary
                    )
                    KeyValueRow(
                        label = "Device Owner Package",
                        value = report.detectedDeviceOwnerPackage ?: "None reported to public API",
                        valueColor = if (report.detectedDeviceOwnerPackage != null) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                    )
                    KeyValueRow(
                        label = "Profile Owner Package",
                        value = report.detectedProfileOwnerPackage ?: "None reported to public API",
                        valueColor = if (report.detectedProfileOwnerPackage != null) GlassPalette.AmberGlow else GlassPalette.TextPrimary
                    )
                    KeyValueRow(
                        label = "Current User Context",
                        value = buildString {
                            append(if (report.isSystemUser) "Primary/System User" else "Secondary User")
                            append(if (report.isRunningInManagedProfile) " (Managed Work Profile)" else " (Personal Profile)")
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Open Android Admin Settings",
                            onClick = { adminPolicyManager.openDeviceAdminSettings() },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.CyanGlow
                        )
                        GlassSecondaryButton(
                            text = "Recheck Status",
                            onClick = { rescan() },
                            icon = Icons.Outlined.Refresh
                        )
                    }
                }
            }

            // Section 2: Google Android Device Policy & Active Administrators Workflow
            item {
                ControlCard(
                    title = "Administrator & DPC Removal Workflow",
                    subtitle = "${report.adminAndManagementApps.size} management / admin package(s) inspected",
                    icon = Icons.Outlined.AdminPanelSettings,
                    badgeText = "${report.adminAndManagementApps.count { it.isActiveAdmin }} ACTIVE",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    Text(
                        text = "Android blocks uninstalling any app while its DeviceAdminReceiver is active ('Can't uninstall active device admin app'). Follow the 3-step workflow on each card below: (1) Deactivate in Android Admin Settings -> (2) Tap Recheck Status -> (3) Tap Uninstall once inactive. If Android greys out 'Deactivate', that app is locked by Device Owner or OEM MDM policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            items(report.adminAndManagementApps, key = { "admin_${it.packageName}" }) { app ->
                AdminPackageWorkflowCard(
                    app = app,
                    onOpenAdminSettings = {
                        pendingDeactivationPackage = app.packageName
                        adminPolicyManager.openDeviceAdminSettings()
                    },
                    onLaunchManagementUi = {
                        adminPolicyManager.launchManagementApp(app.packageName)
                    },
                    onOpenAppDetails = {
                        adminPolicyManager.openAppDetails(app.packageName)
                    },
                    onRecheck = { rescan() },
                    onRequestUninstall = {
                        runCatching {
                            context.startActivity(adminPolicyManager.buildUninstallIntent(app.packageName))
                        }.onSuccess {
                            actionFeedbackTitle = "Opened System Uninstall Confirmation"
                            actionFeedbackBody = "Requested Android PackageInstaller to uninstall ${app.label} (${app.packageName}). Confirm the system dialog, then tap Recheck Status."
                            actionFeedbackIsError = false
                        }.onFailure { err ->
                            actionFeedbackTitle = "Uninstall Request Rejected"
                            actionFeedbackBody = "Android blocked launching ACTION_DELETE for ${app.packageName}: ${err.localizedMessage ?: err.javaClass.simpleName}"
                            actionFeedbackIsError = true
                        }
                    }
                )
            }

            // Section 3: Discovered Halo Applications (Dynamic Discovery)
            item {
                ControlCard(
                    title = "Discovered Halo Suite Packages",
                    subtitle = if (report.haloApps.isEmpty()) {
                        "No Halo packages currently installed on this device"
                    } else {
                        "${report.haloApps.size} Halo package(s) discovered dynamically"
                    },
                    icon = Icons.Outlined.Shield,
                    badgeText = if (report.haloApps.isEmpty()) "0 FOUND" else "${report.haloApps.size} FOUND",
                    badgeColor = if (report.haloApps.isEmpty()) GlassPalette.EmeraldGlow else GlassPalette.VioletGlow
                ) {
                    Text(
                        text = "NoobFree inspects every installed package dynamically rather than guessing package names, and checks whether each Halo app (such as Halo Agent, Halo ThreatX, Halo Remote, or Halo Forge) actually registers a DeviceAdminReceiver or is protected by another administrator ('app is required by the device administrator').",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    if (report.haloApps.isEmpty()) {
                        Text(
                            text = "No packages matching 'Halo' or 'ThreatX' were returned by PackageManager on this runtime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    } else {
                        report.haloApps.forEach { halo ->
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            Text(
                                text = "${halo.label} (${halo.versionName})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Package: ${halo.packageName} • Role: ${halo.role.badgeLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassPalette.CyanGlow
                            )
                            Text(
                                text = halo.removalGuidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassPalette.TextSecondary
                            )
                        }
                    }
                }
            }

            // Section 4: NoobFree Optional Device Administrator & Factory Reset
            item {
                val active = report.noobFreeIsActiveAdmin
                ControlCard(
                    title = "NoobFree Device Administrator & Reset",
                    subtitle = "Requested XML policies: <force-lock /> and <wipe-data />",
                    icon = Icons.Outlined.Lock,
                    badgeText = if (active) "ADMIN ACTIVE" else "ADMIN INACTIVE",
                    badgeColor = if (active) GlassPalette.CyanGlow else GlassPalette.TextSecondary
                ) {
                    Text(
                        text = "Activating NoobFree as an ordinary Device Administrator allows immediate screen locking and authorized local wipeData(0) requests. It does NOT make NoobFree a Device Owner and cannot remove Google Android Device Policy or Halo Agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    KeyValueRow(
                        label = "NoobFree Admin Receiver",
                        value = if (active) "Active (Enabled by user)" else "Inactive"
                    )
                    KeyValueRow(
                        label = "NoobFree Owner Role",
                        value = when {
                            report.noobFreeIsDeviceOwner -> "Device Owner"
                            report.noobFreeIsProfileOwner -> "Profile Owner"
                            else -> "None (Ordinary App)"
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!active) {
                            GlassPrimaryButton(
                                text = "Activate NoobFree Admin",
                                onClick = {
                                    runCatching {
                                        context.startActivity(adminPolicyManager.buildActivateNoobFreeAdminIntent())
                                    }.onFailure { err ->
                                        actionFeedbackTitle = "Activation Dialog Failed"
                                        actionFeedbackBody = err.localizedMessage ?: "Could not open ACTION_ADD_DEVICE_ADMIN."
                                        actionFeedbackIsError = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = GlassPalette.CyanGlow
                            )
                        } else {
                            GlassSecondaryButton(
                                text = "Deactivate NoobFree Admin",
                                onClick = {
                                    val res = adminPolicyManager.deactivateNoobFreeAdmin()
                                    actionFeedbackTitle = res.title
                                    actionFeedbackBody = res.message
                                    actionFeedbackIsError = !res.success
                                    rescan()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            GlassPrimaryButton(
                                text = "Lock Screen Now",
                                onClick = {
                                    val res = adminPolicyManager.lockScreenNow()
                                    actionFeedbackTitle = res.title
                                    actionFeedbackBody = res.message
                                    actionFeedbackIsError = !res.success
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = GlassPalette.VioletGlow
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = "Factory Data Reset Options",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "In-App Wipe Request",
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier.weight(1f),
                            accentColor = GlassPalette.RoseGlow,
                            icon = Icons.Outlined.Restore
                        )
                        GlassSecondaryButton(
                            text = "Recovery Reset Guide",
                            onClick = { showRecoveryGuide = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Info
                        )
                    }
                    GlassSecondaryButton(
                        text = "Open Android System Reset Settings",
                        onClick = { adminPolicyManager.openSystemResetSettings() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Section 5: UserManager Restrictions Matrix
            item {
                val activeRestrictions = report.userRestrictions.count { it.isRestricted }
                ControlCard(
                    title = "UserManager Restriction Matrix",
                    subtitle = "$activeRestrictions of ${report.userRestrictions.size} inspected restrictions active",
                    icon = Icons.Outlined.Security,
                    badgeText = if (activeRestrictions > 0) "$activeRestrictions ENFORCED" else "ALL CLEAR",
                    badgeColor = if (activeRestrictions > 0) GlassPalette.AmberGlow else GlassPalette.EmeraldGlow
                ) {
                    report.userRestrictions.forEach { restriction ->
                        KeyValueRow(
                            label = restriction.title,
                            value = if (restriction.isRestricted) "RESTRICTED (${restriction.key})" else "Allowed",
                            valueColor = if (restriction.isRestricted) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                        )
                    }
                }
            }

            // Section 6: Overlay & Accessibility Audit
            item {
                ControlCard(
                    title = "Overlay & Accessibility Services",
                    subtitle = "${report.allowedOverlayPackages.size} overlay apps • ${report.enabledAccessibilityServices.size} accessibility services",
                    icon = Icons.Outlined.Visibility,
                    badgeText = "PRIVACY AUDIT",
                    badgeColor = GlassPalette.VioletGlow
                ) {
                    Text(
                        text = "Enabled Accessibility Services:",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = if (report.enabledAccessibilityServices.isEmpty()) {
                            "None enabled."
                        } else {
                            report.enabledAccessibilityServices.joinToString("\n")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Packages Allowed to Display Over Other Apps (SYSTEM_ALERT_WINDOW):",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = if (report.allowedOverlayPackages.isEmpty()) {
                            "No packages reported with MODE_ALLOWED."
                        } else {
                            report.allowedOverlayPackages.joinToString("\n")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPackageWorkflowCard(
    app: DiscoveredAdminOrManagementApp,
    onOpenAdminSettings: () -> Unit,
    onLaunchManagementUi: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onRecheck: () -> Unit,
    onRequestUninstall: () -> Unit
) {
    val badgeColor = when (app.role) {
        ManagementPrivilegeRole.DEVICE_OWNER -> GlassPalette.RoseGlow
        ManagementPrivilegeRole.PROFILE_OWNER -> GlassPalette.AmberGlow
        ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN -> GlassPalette.AmberGlow
        ManagementPrivilegeRole.INACTIVE_ADMIN_CAPABLE -> GlassPalette.EmeraldGlow
        ManagementPrivilegeRole.MANAGED_COMPANION -> GlassPalette.VioletGlow
        ManagementPrivilegeRole.STANDARD_APP -> GlassPalette.CyanGlow
    }

    ControlCard(
        title = app.label,
        subtitle = "${app.packageName} • v${app.versionName}",
        icon = Icons.Outlined.AdminPanelSettings,
        badgeText = app.role.badgeLabel,
        badgeColor = badgeColor
    ) {
        KeyValueRow("Management Status", app.statusSummary)
        KeyValueRow(
            "Admin Receiver Component",
            app.componentName?.flattenToShortString() ?: "None declared in AndroidManifest"
        )
        KeyValueRow(
            "Active Device Admin",
            if (app.isActiveAdmin) "YES (Blocks direct uninstall)" else "NO (Inactive)",
            valueColor = if (app.isActiveAdmin) GlassPalette.AmberGlow else GlassPalette.EmeraldGlow
        )
        KeyValueRow(
            "Device / Profile Owner",
            when {
                app.isDeviceOwner -> "YES — Device Owner"
                app.isProfileOwner -> "YES — Profile Owner"
                else -> "No"
            },
            valueColor = if (app.isDeviceOwner || app.isProfileOwner) GlassPalette.RoseGlow else GlassPalette.TextPrimary
        )
        if (app.declaredPolicies.isNotEmpty()) {
            KeyValueRow("Declared Admin Policies", app.declaredPolicies.joinToString(", "))
        }
        Text(
            text = app.removalGuidance,
            style = MaterialTheme.typography.bodySmall,
            color = GlassPalette.TextSecondary
        )

        // Step-by-step action buttons tailored to the exact role
        if (app.canDeactivateInSettings) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPrimaryButton(
                    text = "1. Deactivate in Admin Settings",
                    onClick = onOpenAdminSettings,
                    modifier = Modifier.weight(1f),
                    accentColor = GlassPalette.AmberGlow
                )
                GlassSecondaryButton(
                    text = "2. Recheck",
                    onClick = onRecheck
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassSecondaryButton(
                text = "Open App",
                onClick = onLaunchManagementUi,
                modifier = Modifier.weight(1f)
            )
            GlassSecondaryButton(
                text = "App Info",
                onClick = onOpenAppDetails,
                modifier = Modifier.weight(1f)
            )
            GlassPrimaryButton(
                text = if (app.isUpdatedSystemApp) "Uninstall Updates" else "Uninstall",
                onClick = onRequestUninstall,
                enabled = app.canDirectUninstallNow,
                modifier = Modifier.weight(1f),
                accentColor = if (app.canDirectUninstallNow) GlassPalette.RoseGlow else GlassPalette.TextMuted,
                icon = Icons.Outlined.DeleteOutline
            )
        }
    }
}
