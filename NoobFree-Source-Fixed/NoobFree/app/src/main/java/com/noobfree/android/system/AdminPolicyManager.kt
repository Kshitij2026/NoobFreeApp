package com.noobfree.android.system

import android.app.AppOpsManager
import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.noobfree.android.admin.NoobFreeAdminReceiver
import java.io.IOException
import org.xmlpull.v1.XmlPullParserException

enum class ManagementPrivilegeRole(val badgeLabel: String) {
    DEVICE_OWNER("DEVICE OWNER"),
    PROFILE_OWNER("PROFILE OWNER"),
    ACTIVE_ORDINARY_ADMIN("ACTIVE ADMIN"),
    INACTIVE_ADMIN_CAPABLE("INACTIVE ADMIN"),
    MANAGED_COMPANION("MDM COMPANION"),
    STANDARD_APP("STANDARD APP")
}

data class DiscoveredAdminOrManagementApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName?,
    val role: ManagementPrivilegeRole,
    val isActiveAdmin: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val isUninstallBlockedByPolicy: Boolean,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isHaloRelated: Boolean,
    val isGoogleDevicePolicy: Boolean,
    val versionName: String,
    val declaredPolicies: List<String>,
    val statusSummary: String,
    val removalGuidance: String,
    val canDeactivateInSettings: Boolean,
    val canDirectUninstallNow: Boolean
)

data class UserRestrictionStatus(
    val key: String,
    val title: String,
    val isRestricted: Boolean,
    val explanation: String
)

data class DeviceManagementReport(
    val overallManagementMode: String,
    val overallSummary: String,
    val detectedDeviceOwnerPackage: String?,
    val detectedProfileOwnerPackage: String?,
    val isRunningInManagedProfile: Boolean,
    val isSystemUser: Boolean,
    val noobFreeIsActiveAdmin: Boolean,
    val noobFreeIsDeviceOwner: Boolean,
    val noobFreeIsProfileOwner: Boolean,
    val disallowUninstallApps: Boolean,
    val disallowFactoryReset: Boolean,
    val disallowDebuggingFeatures: Boolean,
    val disallowSetWallpaper: Boolean,
    val adminAndManagementApps: List<DiscoveredAdminOrManagementApp>,
    val haloApps: List<DiscoveredAdminOrManagementApp>,
    val userRestrictions: List<UserRestrictionStatus>,
    val allowedOverlayPackages: List<String>,
    val enabledAccessibilityServices: List<String>
)

data class AdminActionOutcome(
    val success: Boolean,
    val title: String,
    val message: String
)

/**
 * Comprehensive inspector and workflow coordinator for Android Device Policy,
 * ordinary Device Administrators, Device Owner / Profile Owner detection,
 * Halo management packages, and NoobFree's own optional Device Admin receiver.
 */
class AdminPolicyManager(private val context: Context) {

    companion object {
        const val GOOGLE_DEVICE_POLICY_PKG = "com.google.android.apps.work.clouddpc"
        private const val ACTION_USER_SETTINGS_FALLBACK = "android.settings.USER_SETTINGS"

        val TRACKED_USER_RESTRICTIONS: List<Pair<String, String>> = listOf(
            UserManager.DISALLOW_DEBUGGING_FEATURES to "Developer & ADB Debugging Features",
            UserManager.DISALLOW_UNINSTALL_APPS to "Uninstall Applications",
            UserManager.DISALLOW_SET_WALLPAPER to "Change Home/Lock Wallpaper",
            UserManager.DISALLOW_FACTORY_RESET to "Factory Data Reset",
            UserManager.DISALLOW_INSTALL_APPS to "Install Applications",
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES to "Sideload Unknown APKs",
            UserManager.DISALLOW_ADD_USER to "Add or Switch Users",
            UserManager.DISALLOW_CONFIG_WIFI to "Configure Wi-Fi Networks",
            UserManager.DISALLOW_USB_FILE_TRANSFER to "USB File Transfer (MTP/PTP)",
            UserManager.DISALLOW_SAFE_BOOT to "Reboot into Safe Mode"
        )

        /**
         * Pure classification helper testable on local JVM without Android framework mocks.
         */
        fun classifyManagementRole(
            isDeviceOwner: Boolean,
            isProfileOwner: Boolean,
            isActiveAdmin: Boolean,
            hasAdminReceiver: Boolean,
            isHaloOrMdmCompanion: Boolean
        ): ManagementPrivilegeRole = when {
            isDeviceOwner -> ManagementPrivilegeRole.DEVICE_OWNER
            isProfileOwner -> ManagementPrivilegeRole.PROFILE_OWNER
            isActiveAdmin -> ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN
            hasAdminReceiver -> ManagementPrivilegeRole.INACTIVE_ADMIN_CAPABLE
            isHaloOrMdmCompanion -> ManagementPrivilegeRole.MANAGED_COMPANION
            else -> ManagementPrivilegeRole.STANDARD_APP
        }

        /**
         * Determines whether Android permits launching an ordinary ACTION_DELETE uninstall flow right now.
         */
        fun canLaunchDirectUninstall(
            role: ManagementPrivilegeRole,
            isSystemApp: Boolean,
            isUpdatedSystemApp: Boolean,
            isUninstallBlockedByPolicy: Boolean,
            globalDisallowUninstall: Boolean
        ): Boolean {
            if (globalDisallowUninstall || isUninstallBlockedByPolicy) return false
            if (role == ManagementPrivilegeRole.DEVICE_OWNER ||
                role == ManagementPrivilegeRole.PROFILE_OWNER ||
                role == ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN
            ) {
                return false
            }
            return !isSystemApp || isUpdatedSystemApp
        }
    }

    fun inspectDeviceManagement(): DeviceManagementReport {
        val pm = context.packageManager
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val um = context.getSystemService(UserManager::class.java)
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

        val installedApps = getInstalledApplicationsCompat(pm)
        val activeAdmins: List<ComponentName> = runCatching {
            @Suppress("DEPRECATION")
            dpm?.activeAdmins.orEmpty()
        }.getOrDefault(emptyList())

        val adminReceivers = queryDeclaredAdminReceivers(pm)
        val receiversByPackage = adminReceivers.groupBy { it.activityInfo.packageName }
        val activeAdminByPackage = activeAdmins.associateBy { it.packageName }

        val globalDisallowUninstall = runCatching {
            um?.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS) == true
        }.getOrDefault(false)
        val disallowFactoryReset = runCatching {
            um?.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET) == true
        }.getOrDefault(false)
        val disallowDebugging = runCatching {
            um?.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES) == true
        }.getOrDefault(false)
        val disallowWallpaper = runCatching {
            um?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) == true
        }.getOrDefault(false)

        var detectedDeviceOwnerPkg: String? = null
        var detectedProfileOwnerPkg: String? = null

        val candidatePackages = mutableSetOf<String>()
        candidatePackages.addAll(activeAdmins.map { it.packageName })
        candidatePackages.addAll(receiversByPackage.keys)
        candidatePackages.add(GOOGLE_DEVICE_POLICY_PKG)

        installedApps.forEach { appInfo ->
            val pkg = appInfo.packageName
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            if (isHaloPackageOrLabel(pkg, label) || isKnownMdmPackage(pkg)) {
                candidatePackages.add(pkg)
            }
            if (runCatching { dpm?.isDeviceOwnerApp(pkg) == true }.getOrDefault(false)) {
                detectedDeviceOwnerPkg = pkg
                candidatePackages.add(pkg)
            }
            if (runCatching { dpm?.isProfileOwnerApp(pkg) == true }.getOrDefault(false)) {
                detectedProfileOwnerPkg = pkg
                candidatePackages.add(pkg)
            }
        }

        val appMap = installedApps.associateBy { it.packageName }

        val discoveredList = candidatePackages.mapNotNull { pkg ->
            val appInfo = appMap[pkg]
            val activeComp = activeAdminByPackage[pkg]
            val declaredResolveInfos = receiversByPackage[pkg].orEmpty()
            val primaryComponent = activeComp ?: declaredResolveInfos.firstOrNull()?.let {
                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
            }

            if (appInfo == null && activeComp == null && declaredResolveInfos.isEmpty()) {
                return@mapNotNull null
            }

            val label = if (appInfo != null) {
                runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            } else {
                pkg
            }

            val isDO = runCatching { dpm?.isDeviceOwnerApp(pkg) == true }.getOrDefault(false)
            val isPO = runCatching { dpm?.isProfileOwnerApp(pkg) == true }.getOrDefault(false)
            val isActive = activeComp != null ||
                (primaryComponent != null && runCatching { dpm?.isAdminActive(primaryComponent) == true }.getOrDefault(false))
            val isUninstallBlocked = runCatching { dpm?.isUninstallBlocked(null, pkg) == true }.getOrDefault(false)
            val isSystem = appInfo != null && (
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                )
            val isUpdatedSystem = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isHalo = isHaloPackageOrLabel(pkg, label)
            val isGoogleDpc = pkg == GOOGLE_DEVICE_POLICY_PKG
            val versionName = readVersionName(pm, pkg)
            val declaredPolicies = extractDeclaredPolicies(declaredResolveInfos.firstOrNull())

            val role = classifyManagementRole(
                isDeviceOwner = isDO,
                isProfileOwner = isPO,
                isActiveAdmin = isActive,
                hasAdminReceiver = primaryComponent != null,
                isHaloOrMdmCompanion = isHalo || isGoogleDpc || isKnownMdmPackage(pkg)
            )

            val canDeactivate = isActive && !isDO && !isPO
            val canUninstallNow = canLaunchDirectUninstall(
                role = role,
                isSystemApp = isSystem,
                isUpdatedSystemApp = isUpdatedSystem,
                isUninstallBlockedByPolicy = isUninstallBlocked,
                globalDisallowUninstall = globalDisallowUninstall
            )

            val statusSummary = buildStatusSummary(
                role = role,
                isSystem = isSystem,
                isUpdatedSystem = isUpdatedSystem,
                isUninstallBlocked = isUninstallBlocked,
                globalDisallowUninstall = globalDisallowUninstall
            )

            val removalGuidance = buildRemovalGuidance(
                label = label,
                pkg = pkg,
                role = role,
                isSystem = isSystem,
                isUpdatedSystem = isUpdatedSystem,
                isUninstallBlocked = isUninstallBlocked,
                globalDisallowUninstall = globalDisallowUninstall
            )

            DiscoveredAdminOrManagementApp(
                label = label,
                packageName = pkg,
                componentName = primaryComponent,
                role = role,
                isActiveAdmin = isActive,
                isDeviceOwner = isDO,
                isProfileOwner = isPO,
                isUninstallBlockedByPolicy = isUninstallBlocked,
                isSystemApp = isSystem,
                isUpdatedSystemApp = isUpdatedSystem,
                isHaloRelated = isHalo,
                isGoogleDevicePolicy = isGoogleDpc,
                versionName = versionName,
                declaredPolicies = declaredPolicies,
                statusSummary = statusSummary,
                removalGuidance = removalGuidance,
                canDeactivateInSettings = canDeactivate,
                canDirectUninstallNow = canUninstallNow
            )
        }.sortedWith(
            compareBy<DiscoveredAdminOrManagementApp> { it.role.ordinal }
                .thenByDescending { it.isHaloRelated || it.isGoogleDevicePolicy }
                .thenBy { it.label.lowercase() }
        )

        val noobFreeComp = NoobFreeAdminReceiver.getComponentName(context)
        val noobFreeActive = runCatching { dpm?.isAdminActive(noobFreeComp) == true }.getOrDefault(false)
        val noobFreeDO = runCatching { dpm?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false)
        val noobFreePO = runCatching { dpm?.isProfileOwnerApp(context.packageName) == true }.getOrDefault(false)
        val managedProfile = runCatching { um?.isManagedProfile == true }.getOrDefault(false)
        val systemUser = runCatching { um?.isSystemUser == true }.getOrDefault(true)

        val (overallMode, overallSummary) = determineOverallManagementMode(
            detectedDeviceOwnerPkg = detectedDeviceOwnerPkg,
            detectedProfileOwnerPkg = detectedProfileOwnerPkg,
            managedProfile = managedProfile,
            discoveredList = discoveredList,
            disallowUninstall = globalDisallowUninstall,
            disallowDebugging = disallowDebugging
        )

        val restrictionsList = TRACKED_USER_RESTRICTIONS.map { (key, title) ->
            val active = runCatching { um?.hasUserRestriction(key) == true }.getOrDefault(false)
            UserRestrictionStatus(
                key = key,
                title = title,
                isRestricted = active,
                explanation = if (active) {
                    "Enforced ($key = true). Set by Device Owner, Profile Owner, or OEM management policy."
                } else {
                    "Not reported as restricted ($key = false) for the current user profile."
                }
            )
        }

        val overlayApps = installedApps.mapNotNull { app ->
            val mode = runCatching {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, app.packageName)
            }.getOrNull()
            if (mode == AppOpsManager.MODE_ALLOWED) app.packageName else null
        }.sorted()

        val enabledA11y = accessibility?.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).orEmpty().map { svc ->
            "${svc.resolveInfo.serviceInfo.packageName}/${svc.resolveInfo.serviceInfo.name}"
        }.distinct().sorted()

        return DeviceManagementReport(
            overallManagementMode = overallMode,
            overallSummary = overallSummary,
            detectedDeviceOwnerPackage = detectedDeviceOwnerPkg,
            detectedProfileOwnerPackage = detectedProfileOwnerPkg,
            isRunningInManagedProfile = managedProfile,
            isSystemUser = systemUser,
            noobFreeIsActiveAdmin = noobFreeActive,
            noobFreeIsDeviceOwner = noobFreeDO,
            noobFreeIsProfileOwner = noobFreePO,
            disallowUninstallApps = globalDisallowUninstall,
            disallowFactoryReset = disallowFactoryReset,
            disallowDebuggingFeatures = disallowDebugging,
            disallowSetWallpaper = disallowWallpaper,
            adminAndManagementApps = discoveredList,
            haloApps = discoveredList.filter { it.isHaloRelated },
            userRestrictions = restrictionsList,
            allowedOverlayPackages = overlayApps,
            enabledAccessibilityServices = enabledA11y
        )
    }

    private fun determineOverallManagementMode(
        detectedDeviceOwnerPkg: String?,
        detectedProfileOwnerPkg: String?,
        managedProfile: Boolean,
        discoveredList: List<DiscoveredAdminOrManagementApp>,
        disallowUninstall: Boolean,
        disallowDebugging: Boolean
    ): Pair<String, String> {
        if (detectedDeviceOwnerPkg != null) {
            return "Fully Managed (Device Owner Active)" to
                "Package '$detectedDeviceOwnerPkg' is registered as Device Owner. Android prevents ordinary apps from deactivating or uninstalling a Device Owner or apps it locks via setUninstallBlocked()."
        }
        if (detectedProfileOwnerPkg != null || managedProfile) {
            val owner = detectedProfileOwnerPkg ?: "Organization DPC"
            return "Managed Work Profile (Profile Owner)" to
                "Profile Owner '$owner' controls this profile. Remove the work profile in Android Settings -> Passwords & accounts -> Work profile, or inside the management app."
        }
        val activeNonSelfAdmins = discoveredList.filter {
            it.isActiveAdmin && it.packageName != context.packageName
        }
        if (activeNonSelfAdmins.isNotEmpty()) {
            val names = activeNonSelfAdmins.joinToString(", ") { "${it.label} (${it.packageName})" }
            val policyHint = if (disallowUninstall || disallowDebugging) {
                " Note: Active UserManager restrictions are also present, which may indicate a platform/OEM or hidden profile owner policy."
            } else {
                ""
            }
            return "Active Device Administrator(s) Present" to
                "No Device Owner was reported to public APIs, but active Device Administrator(s) are enabled: $names. An active Device Administrator cannot be uninstalled until deactivated in Android Device Admin Settings.$policyHint"
        }
        return "Unmanaged / Standard Personal Device" to
            "No external Device Owner, Profile Owner, or active third-party Device Administrator was reported by Android's public DevicePolicyManager APIs."
    }

    private fun buildStatusSummary(
        role: ManagementPrivilegeRole,
        isSystem: Boolean,
        isUpdatedSystem: Boolean,
        isUninstallBlocked: Boolean,
        globalDisallowUninstall: Boolean
    ): String {
        val base = when (role) {
            ManagementPrivilegeRole.DEVICE_OWNER ->
                "Active Device Owner — Controls full device policy; deactivation is locked by Android."
            ManagementPrivilegeRole.PROFILE_OWNER ->
                "Active Profile Owner — Controls managed profile policy."
            ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN ->
                "Active Device Administrator — Blocks direct uninstall until deactivated."
            ManagementPrivilegeRole.INACTIVE_ADMIN_CAPABLE ->
                "Inactive Device Admin Receiver — Admin privileges are currently OFF."
            ManagementPrivilegeRole.MANAGED_COMPANION ->
                "Management Companion Package — No active DeviceAdminReceiver in this package."
            ManagementPrivilegeRole.STANDARD_APP ->
                "Standard Application"
        }
        val installType = when {
            isUpdatedSystem -> "Updated System App"
            isSystem -> "System Partition App"
            else -> "User-Installed App"
        }
        val lockNote = when {
            isUninstallBlocked -> " • Blocked by Administrator (setUninstallBlocked)"
            globalDisallowUninstall -> " • Blocked by DISALLOW_UNINSTALL_APPS"
            else -> ""
        }
        return "$base ($installType$lockNote)"
    }

    private fun buildRemovalGuidance(
        label: String,
        pkg: String,
        role: ManagementPrivilegeRole,
        isSystem: Boolean,
        isUpdatedSystem: Boolean,
        isUninstallBlocked: Boolean,
        globalDisallowUninstall: Boolean
    ): String {
        if (role == ManagementPrivilegeRole.DEVICE_OWNER) {
            return "$label ($pkg) is the Device Owner. Android greys out 'Deactivate' and rejects uninstall requests ('Can't uninstall active device admin app'). To remove it legitimately: open $label and check for an 'Unregister' / 'Unenroll' option, or have the enrolling organization release the tablet from Zero-Touch / MDM management."
        }
        if (role == ManagementPrivilegeRole.PROFILE_OWNER) {
            return "$label ($pkg) is the Profile Owner. Open Android Settings -> Accounts / Work Profile and select 'Remove work profile', or unenroll from inside $label."
        }
        if (isUninstallBlocked || globalDisallowUninstall) {
            return "Android reports that an administrator blocks uninstalling $label ('App is required by the device administrator'). Even if $label is not an admin itself, the controlling Device Owner or administrator has locked its removal via DevicePolicyManager. You must first deactivate or unenroll the primary administrator (such as Android Device Policy or Halo Agent) before Android will allow uninstalling $label."
        }
        if (role == ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN) {
            return "$label has an active DeviceAdminReceiver. Step 1: Tap 'Deactivate in Admin Settings' and uncheck/deactivate $label. (If the Deactivate button in Android Settings is greyed out, the app is protected by Device Owner / OEM MDM policy.) Step 2: Tap 'Recheck Status'. Step 3: Once inactive, tap 'Uninstall'."
        }
        if (isSystem && !isUpdatedSystem) {
            return "$label resides on the read-only system partition. Android does not allow standard APK uninstallation of factory system packages, though eligible non-critical packages can be disabled for User 0 via an authorized Shizuku shell."
        }
        if (isUpdatedSystem) {
            return "$label is a system app with Play Store/APK updates installed. Admin status is inactive—you can tap 'Uninstall Updates' to roll it back to its factory version."
        }
        return "$label is not an active administrator and has no visible uninstall lock. Tap 'Uninstall App' to open Android's system uninstall confirmation."
    }

    fun isNoobFreeAdminActive(): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching {
            dpm.isAdminActive(NoobFreeAdminReceiver.getComponentName(context))
        }.getOrDefault(false)
    }

    fun buildActivateNoobFreeAdminIntent(): Intent {
        val component = NoobFreeAdminReceiver.getComponentName(context)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Grants NoobFree optional local Device Administrator access (screen lock and authorized wipe-data request). This does NOT grant Device Owner privileges and cannot remove Google or Halo administrators."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun deactivateNoobFreeAdmin(): AdminActionOutcome {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
            ?: return AdminActionOutcome(false, "Service Unavailable", "DevicePolicyManager is unavailable on this device.")
        val component = NoobFreeAdminReceiver.getComponentName(context)
        if (!isNoobFreeAdminActive()) {
            return AdminActionOutcome(true, "Already Inactive", "NoobFree is not currently an active Device Administrator.")
        }
        return try {
            dpm.removeActiveAdmin(component)
            AdminActionOutcome(
                success = true,
                title = "Deactivation Requested",
                message = "Requested Android to remove NoobFree's Device Administrator role. Tap Recheck Status to confirm."
            )
        } catch (e: SecurityException) {
            AdminActionOutcome(
                success = false,
                title = "Deactivation Blocked",
                message = "Android blocked removing active admin: ${e.localizedMessage ?: "SecurityException"}"
            )
        }
    }

    fun lockScreenNow(): AdminActionOutcome {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
            ?: return AdminActionOutcome(false, "Service Unavailable", "DevicePolicyManager is unavailable.")
        if (!isNoobFreeAdminActive()) {
            return AdminActionOutcome(
                success = false,
                title = "NoobFree Admin Inactive",
                message = "Activate NoobFree as a Device Administrator first to use immediate screen lock."
            )
        }
        return try {
            dpm.lockNow()
            AdminActionOutcome(
                success = true,
                title = "Screen Locked",
                message = "DevicePolicyManager.lockNow() executed successfully."
            )
        } catch (e: SecurityException) {
            AdminActionOutcome(
                success = false,
                title = "Screen Lock Rejected",
                message = "Android rejected lockNow(): ${e.localizedMessage ?: "Missing force-lock policy permission."}"
            )
        }
    }

    fun performAuthorizedFactoryReset(): AdminActionOutcome {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
            ?: return AdminActionOutcome(false, "Service Unavailable", "DevicePolicyManager is unavailable on this device.")
        val um = context.getSystemService(UserManager::class.java)
        val component = NoobFreeAdminReceiver.getComponentName(context)

        if (!runCatching { dpm.isAdminActive(component) }.getOrDefault(false)) {
            return AdminActionOutcome(
                success = false,
                title = "NoobFree Admin Required",
                message = "NoobFree is not an active Device Administrator. Activate NoobFree Device Admin first, or use Android System Settings / Recovery Mode."
            )
        }

        if (runCatching { um?.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET) == true }.getOrDefault(false)) {
            return AdminActionOutcome(
                success = false,
                title = "Blocked by Organization Policy",
                message = "UserManager.DISALLOW_FACTORY_RESET is active on this tablet. Android's policy engine blocks in-app factory reset requests while managed."
            )
        }

        val isDO = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
        val isPO = runCatching { dpm.isProfileOwnerApp(context.packageName) }.getOrDefault(false)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isDO) {
                dpm.wipeDevice(0)
            } else {
                @Suppress("DEPRECATION")
                dpm.wipeData(0)
            }
            AdminActionOutcome(
                success = true,
                title = "Factory Reset Initiated",
                message = "Android accepted the wipeData(0) request and is initiating factory reset."
            )
        } catch (security: SecurityException) {
            val apiExplanation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !isDO && !isPO) {
                "On Android 14+ (API ${Build.VERSION.SDK_INT}), Google deprecated DevicePolicyManager.wipeData() for ordinary legacy Device Administrators—only a Device Owner or system Reset settings can trigger a full device wipe."
            } else {
                "Android's DevicePolicyManager rejected wipeData(0) due to active enterprise policies or insufficient privilege."
            }
            AdminActionOutcome(
                success = false,
                title = "Android Blocked In-App Wipe",
                message = "$apiExplanation\n\nSystem detail: ${security.localizedMessage ?: "SecurityException"}. Use the 'Open Android Reset Settings' button or the 'Manual Recovery-Mode Reset Guide' below."
            )
        } catch (e: Exception) {
            AdminActionOutcome(
                success = false,
                title = "Reset Request Failed",
                message = "Could not execute wipeData(0): ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    fun openDeviceAdminSettings(): Boolean {
        val intents = listOf(
            Intent("android.settings.DEVICE_ADMIN_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstResolvable(intents)
    }

    fun openSystemResetSettings(): Boolean {
        val intents = listOf(
            Intent("android.settings.BACKUP_AND_RESET_SETTINGS"),
            Intent(Settings.ACTION_PRIVACY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstResolvable(intents)
    }

    fun openAccountsAndWorkProfileSettings(): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_SYNC_SETTINGS),
            Intent(ACTION_USER_SETTINGS_FALLBACK),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstResolvable(intents)
    }

    fun openAppDetails(packageName: String): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        return launchFirstResolvable(listOf(intent, Intent(Settings.ACTION_APPLICATION_SETTINGS)))
    }

    fun launchManagementApp(packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launch != null) {
            launchFirstResolvable(listOf(launch))
        } else {
            openAppDetails(packageName)
        }
    }

    fun buildUninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun launchFirstResolvable(intents: List<Intent>): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return true
        }
        return false
    }

    private fun getInstalledApplicationsCompat(pm: PackageManager): List<ApplicationInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS)
            }
        }.getOrDefault(emptyList())
    }

    private fun queryDeclaredAdminReceivers(pm: PackageManager): List<ResolveInfo> {
        val queryIntent = Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryBroadcastReceivers(
                    queryIntent,
                    PackageManager.ResolveInfoFlags.of(
                        (PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryBroadcastReceivers(
                    queryIntent,
                    PackageManager.GET_META_DATA or PackageManager.GET_DISABLED_COMPONENTS
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun extractDeclaredPolicies(resolveInfo: ResolveInfo?): List<String> {
        if (resolveInfo == null) return emptyList()
        return try {
            val adminInfo = DeviceAdminInfo(context, resolveInfo)
            val policies = mutableListOf<String>()
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_POLICY_WIPE_DATA)) policies.add("wipe-data")
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_POLICY_FORCE_LOCK)) policies.add("force-lock")
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD)) policies.add("limit-password")
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_POLICY_WATCH_LOGIN)) policies.add("watch-login")
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_POLICY_RESET_PASSWORD)) policies.add("reset-password")
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_ENCRYPTED_STORAGE)) policies.add("encrypted-storage")
            if (adminInfo.usesPolicy(DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA)) policies.add("disable-camera")
            policies
        } catch (_: XmlPullParserException) {
            emptyList()
        } catch (_: IOException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readVersionName(pm: PackageManager, pkg: String): String {
        return runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            pkgInfo.versionName ?: "Unknown"
        }.getOrDefault("Not installed")
    }

    private fun isHaloPackageOrLabel(pkg: String, label: String): Boolean {
        val p = pkg.lowercase()
        val l = label.lowercase()
        return p.contains("halo") ||
            p.contains("threatx") ||
            l.contains("halo") ||
            l.contains("threatx")
    }

    private fun isKnownMdmPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == GOOGLE_DEVICE_POLICY_PKG ||
            p.contains("clouddpc") ||
            p.contains("mdm") ||
            p.contains("devicepolicy") ||
            p.contains("knoxmanage") ||
            p.contains("intune") ||
            p.contains("airwatch")
    }
}
