package com.noobfree.android.system

import com.noobfree.android.data.CommandExecutor
import com.noobfree.android.data.OperationLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Executes narrowly-scoped package commands through an already-authorized Shizuku shell. */
class PackageOperationManager(private val commandExecutor: CommandExecutor) {
    companion object {
        // Never allow core Android, package installer, launcher, or NoobFree itself to be disabled here.
        private val protectedPackages = setOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.android.providers.settings",
            "com.android.providers.media", "com.android.providers.telephony",
            "com.noobfree.android", "com.noobfree.android.debug"
        )
        private val packagePattern = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    }

    fun isProtected(packageName: String): Boolean =
        packageName in protectedPackages || packageName.startsWith("com.android.systemui.")

    suspend fun setEnabled(packageName: String, enabled: Boolean): OperationLogEntry {
        require(packagePattern.matches(packageName)) { "Invalid package name." }
        require(!isProtected(packageName)) { "NoobFree safety guard: this package is protected." }
        if (!commandExecutor.isReady()) {
            return log(false, packageName, "Shizuku is not connected and authorized. No changes were made.")
        }
        val action = if (enabled) "enable" else "disable-user --user 0"
        val command = "pm $action $packageName"
        val result = commandExecutor.execute(command, 10000L)
        val verify = commandExecutor.execute("pm list packages -d $packageName", 5000L)
        val disabled = verify.stdout.lineSequence().any { it.trim() == "package:$packageName" }
        val success = result.isSuccess && (if (enabled) !disabled else disabled)
        log(success, packageName, if (success) {
            if (enabled) "Package enabled and verified." else "Package disabled for user 0 and verified."
        } else {
            "Operation could not be verified. ${result.stderr.ifBlank { verify.stderr }.ifBlank { "Check device policy and package eligibility." }}"
        }, command)
    }

    private fun log(success: Boolean, pkg: String, detail: String, command: String? = null) = OperationLogEntry(
        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
        category = "Package management", action = pkg, command = command,
        success = success, detail = detail
    )
}
