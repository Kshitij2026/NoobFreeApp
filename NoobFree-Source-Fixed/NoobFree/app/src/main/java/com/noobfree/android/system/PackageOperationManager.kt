package com.noobfree.android.system

import com.noobfree.android.data.CommandExecutor
import com.noobfree.android.data.OperationLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DebloatSafetyTier(val badgeLabel: String, val description: String) {
    CRITICAL_PROTECTED(
        "CRITICAL SYSTEM",
        "Essential Android OS, Launcher, SystemUI, Package Installer, Play Services, or Policy component. Disabling is strictly blocked to prevent bootloops or bricking."
    ),
    ADMIN_OR_MDM_PROTECTED(
        "ADMIN / MDM",
        "Device Administrator, Device Owner, or MDM agent. Must be deactivated or unenrolled via Android Device Policy settings before removal."
    ),
    SAFE_DEBLOAT_CANDIDATE(
        "SAFE DEBLOAT",
        "Non-essential preloaded app or promotional bloatware. Can be safely disabled for User 0 via Shizuku and re-enabled at any time."
    ),
    CAUTION_SYSTEM(
        "SYSTEM (CAUTION)",
        "System or OEM component. Disabling via Shizuku is permitted with confirmation, and can be restored from the Disabled filter."
    ),
    USER_REMOVABLE(
        "USER APP",
        "Standard user-installed application. Can be uninstalled directly without Shizuku or disabled via Shizuku."
    )
}

/**
 * Executes narrowly-scoped package commands through an already-authorized Shizuku shell,
 * backed by a comprehensive Android safety catalogue so critical OS packages can never be disabled.
 */
class PackageOperationManager(private val commandExecutor: CommandExecutor) {
    companion object {
        // Hard denylist: Core Android OS, SystemUI, Settings, Package Installer, Play Services, DPC, Telephony/Emergency, NoobFree
        val CRITICAL_PROTECTED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.media.module",
            "com.android.providers.telephony",
            "com.android.providers.downloads",
            "com.android.providers.contacts",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.webview",
            "com.android.webview",
            "com.google.android.apps.work.clouddpc",
            "moe.shizuku.privileged.api",
            "com.noobfree.android",
            "com.noobfree.android.debug"
        )

        private val CRITICAL_PREFIXES = listOf(
            "com.android.systemui.",
            "com.android.internal.",
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.tblenovo.launcher",
            "com.acer.launcher"
        )

        // Curated list of common preinstalled bloatware / non-essential preload packages safe to disable
        val KNOWN_SAFE_DEBLOAT_PACKAGES = setOf(
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.magazines",
            "com.google.android.apps.books",
            "com.google.android.apps.podcasts",
            "com.google.android.apps.youtube.music",
            "com.google.android.feedback",
            "com.google.android.printservice.recommendation",
            "com.android.bips",
            "com.android.bookmarkprovider",
            "com.android.dreams.basic",
            "com.android.dreams.phototable",
            "com.android.egg",
            "com.android.printspooler",
            "com.facebook.katana",
            "com.facebook.system",
            "com.facebook.appmanager",
            "com.facebook.services"
        )

        private val packagePattern = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    }

    fun isProtected(packageName: String, activeLauncherPackages: Set<String> = emptySet()): Boolean {
        val normalized = packageName.trim()
        if (normalized in CRITICAL_PROTECTED_PACKAGES) return true
        if (normalized in activeLauncherPackages) return true
        return CRITICAL_PREFIXES.any { normalized.startsWith(it) }
    }

    fun classifySafetyTier(
        packageName: String,
        isSystemApp: Boolean,
        isActiveAdminOrOwner: Boolean,
        activeLauncherPackages: Set<String> = emptySet()
    ): DebloatSafetyTier {
        if (isProtected(packageName, activeLauncherPackages)) {
            return DebloatSafetyTier.CRITICAL_PROTECTED
        }
        if (isActiveAdminOrOwner) {
            return DebloatSafetyTier.ADMIN_OR_MDM_PROTECTED
        }
        if (!isSystemApp) {
            return DebloatSafetyTier.USER_REMOVABLE
        }
        if (packageName in KNOWN_SAFE_DEBLOAT_PACKAGES) {
            return DebloatSafetyTier.SAFE_DEBLOAT_CANDIDATE
        }
        return DebloatSafetyTier.CAUTION_SYSTEM
    }

    suspend fun setEnabled(
        packageName: String,
        enabled: Boolean,
        activeLauncherPackages: Set<String> = emptySet()
    ): OperationLogEntry {
        require(packagePattern.matches(packageName)) { "Invalid package name format: $packageName" }
        // Always allow re-enabling a previously disabled package; block disabling protected packages
        if (!enabled && isProtected(packageName, activeLauncherPackages)) {
            return log(
                success = false,
                pkg = packageName,
                detail = "NoobFree safety guard blocked disabling '$packageName' because it is a critical Android system, launcher, or security component."
            )
        }
        if (!commandExecutor.isReady()) {
            return log(
                success = false,
                pkg = packageName,
                detail = "Shizuku is not connected and authorized. Start Shizuku to run package enable/disable operations."
            )
        }

        val action = if (enabled) "enable" else "disable-user --user 0"
        val command = "pm $action $packageName"
        val result = commandExecutor.execute(command, 10000L)
        val verify = commandExecutor.execute("pm list packages -d $packageName", 5000L)
        val disabled = verify.stdout.lineSequence().any { it.trim() == "package:$packageName" }
        val success = result.isSuccess && (if (enabled) !disabled else disabled)

        return log(
            success = success,
            pkg = packageName,
            detail = if (success) {
                if (enabled) {
                    "Package '$packageName' re-enabled and verified active."
                } else {
                    "Package '$packageName' disabled for User 0 and verified. You can restore it anytime from the Disabled filter."
                }
            } else {
                val errText = result.stderr.ifBlank { verify.stderr }
                    .ifBlank { "Rejected by Android PackageManager or active Device Owner policy." }
                "Operation could not be verified for '$packageName': $errText"
            },
            command = command
        )
    }

    private fun log(
        success: Boolean,
        pkg: String,
        detail: String,
        command: String? = null
    ) = OperationLogEntry(
        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
        category = "Package Management",
        action = pkg,
        command = command,
        success = success,
        detail = detail
    )
}
