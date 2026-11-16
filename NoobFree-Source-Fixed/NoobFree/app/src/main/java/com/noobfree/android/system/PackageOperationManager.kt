package com.noobfree.android.system

import com.noobfree.android.data.CommandExecutor
import com.noobfree.android.data.OperationLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DebloatSafetyTier(val badgeLabel: String, val description: String) {
    CRITICAL_PROTECTED(
        "CRITICAL SYSTEM",
        "Essential Android OS, Launcher, SystemUI, Package Installer, or Play Services component. Protected from disabling to prevent bootloops."
    ),
    ADMIN_OR_MDM_PROTECTED(
        "ADMIN / MDM",
        "Device Administrator, Device Owner, or MDM agent. Can be tested with system ACTION_DELETE, Admin Settings deactivation, or Shizuku shell commands."
    ),
    SAFE_DEBLOAT_CANDIDATE(
        "SAFE DEBLOAT",
        "Non-essential preloaded app or promotional bloatware. Can be safely disabled or uninstalled for User 0 via Shizuku and restored anytime."
    ),
    CAUTION_SYSTEM(
        "SYSTEM (CAUTION)",
        "System or OEM component. Disabling or uninstalling for User 0 via Shizuku is permitted with confirmation."
    ),
    USER_REMOVABLE(
        "USER APP",
        "Standard user-installed application. Can be uninstalled directly without Shizuku or disabled via Shizuku."
    )
}

/**
 * Executes narrowly-scoped package commands through an already-authorized Shizuku shell,
 * while protecting critical boot/UI components from accidental disabling.
 */
class PackageOperationManager(private val commandExecutor: CommandExecutor) {
    companion object {
        // Hard denylist for OS boot/UI stability (does NOT block testing on 3rd-party/MDM packages if user explicitly tests them)
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
        if (isActiveAdminOrOwner) {
            return DebloatSafetyTier.ADMIN_OR_MDM_PROTECTED
        }
        if (isProtected(packageName, activeLauncherPackages)) {
            return DebloatSafetyTier.CRITICAL_PROTECTED
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
        if (!enabled && isProtected(packageName, activeLauncherPackages) && packageName != "com.google.android.apps.work.clouddpc") {
            return log(
                success = false,
                pkg = packageName,
                detail = "NoobFree safety guard blocked disabling '$packageName' because it is a core Android boot/UI component."
            )
        }
        if (!commandExecutor.isReady()) {
            return log(
                success = false,
                pkg = packageName,
                detail = "Shizuku is not connected. Start Shizuku to run shell package enable/disable operations, or use the Uninstall / App Info buttons."
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
                    "Package '$packageName' disabled for User 0 and verified."
                }
            } else {
                val errText = result.stderr.ifBlank { result.stdout }.ifBlank { verify.stderr }
                    .ifBlank { "Rejected by Android PackageManager or active Device Owner policy." }
                "Shell result for '$packageName': $errText"
            },
            command = command
        )
    }

    suspend fun uninstallForUserZeroViaShizuku(packageName: String): OperationLogEntry {
        require(packagePattern.matches(packageName)) { "Invalid package name format: $packageName" }
        if (!commandExecutor.isReady()) {
            return log(
                success = false,
                pkg = packageName,
                detail = "Shizuku is not connected. Use the standard 'Uninstall (System UI)' button or connect Shizuku to test `pm uninstall --user 0`."
            )
        }
        val command = "pm uninstall --user 0 $packageName"
        val result = commandExecutor.execute(command, 10000L)
        val out = (result.stdout + "\n" + result.stderr).trim()
        val success = result.isSuccess && out.contains("Success", ignoreCase = true)
        return log(
            success = success,
            pkg = packageName,
            detail = if (success) {
                "Uninstalled '$packageName' for User 0 via Shizuku (`$out`)."
            } else {
                "Android PackageManager response for `$command`: ${out.ifBlank { "Exit code ${result.exitCode}" }}"
            },
            command = command
        )
    }

    suspend fun removeActiveAdminViaShizuku(componentFlatten: String): OperationLogEntry {
        if (!commandExecutor.isReady()) {
            return log(
                success = false,
                pkg = componentFlatten,
                detail = "Shizuku is not connected. Open Android Admin Settings to deactivate the administrator manually."
            )
        }
        val command = "dpm remove-active-admin --user 0 $componentFlatten"
        val result = commandExecutor.execute(command, 8000L)
        val out = (result.stdout + "\n" + result.stderr).trim()
        return log(
            success = result.isSuccess,
            pkg = componentFlatten,
            detail = if (result.isSuccess) {
                "Executed `$command`: ${out.ifBlank { "Success" }}"
            } else {
                "DevicePolicyManager response for `$command`: ${out.ifBlank { "Exit code ${result.exitCode}" }}"
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
