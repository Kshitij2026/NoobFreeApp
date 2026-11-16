package com.noobfree.android.system

import android.content.ContentResolver
import android.os.Build
import android.provider.Settings
import com.noobfree.android.data.CommandExecutor

class DeveloperManager(
    private val contentResolver: ContentResolver,
    private val commandExecutor: CommandExecutor,
    private val manufacturerProvider: () -> String = { Build.MANUFACTURER },
    private val disallowDebuggingProvider: () -> Boolean = { false }
) {
    private fun manufacturerName(): String =
        runCatching { manufacturerProvider() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"

    companion object {
        const val CMD_GET_DEV_OPTIONS = "settings get global development_settings_enabled"
        const val CMD_ENABLE_DEV_OPTIONS = "settings put global development_settings_enabled 1"
        const val CMD_DISABLE_DEV_OPTIONS = "settings put global development_settings_enabled 0"
    }

    suspend fun getDeveloperOptionsState(): DeveloperOptionsState {
        val publicValue = runCatching {
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        }.getOrDefault(0)

        val adbValue = runCatching {
            Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0)
        }.getOrDefault(0)

        val blockedByAdmin = runCatching { disallowDebuggingProvider() }.getOrDefault(false)

        if (commandExecutor.isReady()) {
            val shellRes = commandExecutor.execute(CMD_GET_DEV_OPTIONS)
            val parsedShell = shellRes.stdout.trim().toIntOrNull()
            if (shellRes.isSuccess && parsedShell != null) {
                return DeveloperOptionsState(
                    isEnabled = parsedShell == 1,
                    rawGlobalValue = parsedShell,
                    verificationSource = "Verified via Shizuku Shell (settings get global)",
                    manufacturerNote = getManufacturerCompatibilityNote(),
                    isBlockedByAdminRestriction = blockedByAdmin,
                    usbDebuggingEnabled = adbValue == 1,
                    guidedSteps = getSevenTapGuideSteps()
                )
            }
        }

        return DeveloperOptionsState(
            isEnabled = publicValue == 1,
            rawGlobalValue = publicValue,
            verificationSource = "Verified via Android Settings.Global (No Shizuku required)",
            manufacturerNote = getManufacturerCompatibilityNote(),
            isBlockedByAdminRestriction = blockedByAdmin,
            usbDebuggingEnabled = adbValue == 1,
            guidedSteps = getSevenTapGuideSteps()
        )
    }

    suspend fun enableDeveloperOptions(): OperationOutcome<DeveloperOptionsState> {
        val blockedByAdmin = runCatching { disallowDebuggingProvider() }.getOrDefault(false)
        if (blockedByAdmin) {
            val current = getDeveloperOptionsState()
            return OperationOutcome(
                success = false,
                data = current,
                message = "Blocked by administrator policy (DISALLOW_DEBUGGING_FEATURES). Use 'Direct Open Dev Options Activity' to test whether the Settings sub-activity is accessible directly.",
                executedCommand = null
            )
        }

        // Strategy 1: Attempt direct ContentResolver write in case WRITE_SECURE_SETTINGS was granted
        val directWriteOk = runCatching {
            Settings.Global.putInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        }.getOrDefault(false)

        if (directWriteOk) {
            val checkInt = runCatching {
                Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
            }.getOrDefault(0)
            if (checkInt == 1) {
                val updated = getDeveloperOptionsState()
                return OperationOutcome(
                    success = true,
                    data = updated,
                    message = "Developer Options enabled directly via Settings.Global.putInt (WRITE_SECURE_SETTINGS).",
                    executedCommand = "Settings.Global.putInt(DEVELOPMENT_SETTINGS_ENABLED, 1)",
                    rawOutput = "1"
                )
            }
        }

        // Strategy 2: Shizuku shell write if connected
        if (!commandExecutor.isReady()) {
            val current = getDeveloperOptionsState()
            return OperationOutcome(
                success = false,
                data = current,
                message = "Direct Settings.Global write requires WRITE_SECURE_SETTINGS or Shizuku. Without Shizuku, use 'Direct Open Dev Options' or 'Open About Tablet (7-Tap Guide)'.",
                executedCommand = CMD_ENABLE_DEV_OPTIONS
            )
        }

        val writeResult = commandExecutor.execute(CMD_ENABLE_DEV_OPTIONS)
        if (!writeResult.isSuccess) {
            val current = getDeveloperOptionsState()
            val reason = writeResult.stderr.ifBlank { "Exit code ${writeResult.exitCode}" }
            return OperationOutcome(
                success = false,
                data = current,
                message = "Command failed: $reason. ${getManufacturerRestrictionHint()}",
                executedCommand = CMD_ENABLE_DEV_OPTIONS,
                rawOutput = reason
            )
        }

        val verifyResult = commandExecutor.execute(CMD_GET_DEV_OPTIONS)
        val verifiedInt = verifyResult.stdout.trim().toIntOrNull()
            ?: runCatching {
                Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
            }.getOrDefault(0)

        val updatedState = DeveloperOptionsState(
            isEnabled = verifiedInt == 1,
            rawGlobalValue = verifiedInt,
            verificationSource = "Verified via `settings get global development_settings_enabled` -> $verifiedInt",
            manufacturerNote = getManufacturerCompatibilityNote(),
            isBlockedByAdminRestriction = blockedByAdmin,
            usbDebuggingEnabled = false,
            guidedSteps = getSevenTapGuideSteps()
        )

        return if (verifiedInt == 1) {
            OperationOutcome(
                success = true,
                data = updatedState,
                message = "Developer Options enabled and verified (development_settings_enabled = 1).",
                executedCommand = "$CMD_ENABLE_DEV_OPTIONS && $CMD_GET_DEV_OPTIONS",
                rawOutput = "1"
            )
        } else {
            OperationOutcome(
                success = false,
                data = updatedState,
                message = "Verification failed: value remained '$verifiedInt'. ${getManufacturerRestrictionHint()}",
                executedCommand = "$CMD_ENABLE_DEV_OPTIONS && $CMD_GET_DEV_OPTIONS",
                rawOutput = verifyResult.stdout.ifBlank { verifyResult.stderr }
            )
        }
    }

    suspend fun disableDeveloperOptions(): OperationOutcome<DeveloperOptionsState> {
        val directWriteOk = runCatching {
            Settings.Global.putInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        }.getOrDefault(false)
        if (directWriteOk) {
            val state = getDeveloperOptionsState()
            if (!state.isEnabled) {
                return OperationOutcome(
                    success = true,
                    data = state,
                    message = "Developer Options disabled via Settings.Global.putInt.",
                    executedCommand = "Settings.Global.putInt(DEVELOPMENT_SETTINGS_ENABLED, 0)",
                    rawOutput = "0"
                )
            }
        }

        if (!commandExecutor.isReady()) {
            val current = getDeveloperOptionsState()
            return OperationOutcome(
                success = false,
                data = current,
                message = "Shizuku is not connected. Open Android Developer Options Settings to toggle the top switch off.",
                executedCommand = CMD_DISABLE_DEV_OPTIONS
            )
        }

        val writeResult = commandExecutor.execute(CMD_DISABLE_DEV_OPTIONS)
        val verifyResult = commandExecutor.execute(CMD_GET_DEV_OPTIONS)
        val verifiedInt = verifyResult.stdout.trim().toIntOrNull() ?: -1
        val state = getDeveloperOptionsState()

        return if (writeResult.isSuccess && verifiedInt == 0) {
            OperationOutcome(
                success = true,
                data = state,
                message = "Developer Options disabled and verified (development_settings_enabled = 0).",
                executedCommand = "$CMD_DISABLE_DEV_OPTIONS && $CMD_GET_DEV_OPTIONS",
                rawOutput = "0"
            )
        } else {
            OperationOutcome(
                success = false,
                data = state,
                message = "Could not disable Developer Options (verified value: $verifiedInt).",
                executedCommand = CMD_DISABLE_DEV_OPTIONS,
                rawOutput = writeResult.stderr
            )
        }
    }

    fun getSevenTapGuideSteps(): List<String> {
        val mfr = manufacturerName().lowercase()
        val pathNote = when {
            mfr.contains("acer") ->
                "On Acer tablets (e.g., Acer One 8 T4-82L-G2): Settings -> About tablet -> scroll to the bottom for 'Build number'."
            mfr.contains("samsung") ->
                "On Samsung One UI: Settings -> About phone/tablet -> Software information -> 'Build number'."
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") ->
                "On Xiaomi HyperOS / MIUI: Settings -> About tablet/phone -> tap 'OS version' or 'MIUI version' 7 times."
            else ->
                "Open Settings -> About tablet / About phone -> locate 'Build number'."
        }
        return listOf(
            "Method A (Direct Activity Launch): Tap 'Direct Open Dev Options' to launch Android's DevelopmentSettingsDashboardActivity directly without going through About Tablet.",
            "Method B (Direct Global Write): Tap 'Try Direct Enable Flag' to test writing DEVELOPMENT_SETTINGS_ENABLED = 1 via ContentResolver or Shizuku.",
            "Method C (7-Tap Build Number): $pathNote Tap 'Build number' 7 times if not restricted by DISALLOW_DEBUGGING_FEATURES."
        )
    }

    fun getManufacturerCompatibilityNote(): String {
        val manufacturer = manufacturerName()
        val mfr = manufacturer.lowercase()
        return when {
            mfr.contains("acer") ->
                "Acer Tablet ($manufacturer): Uses standard AOSP Settings.Global.DEVELOPMENT_SETTINGS_ENABLED. If Build number in About Tablet is locked by DISALLOW_DEBUGGING_FEATURES, use 'Direct Open Dev Options' to test whether the DevelopmentSettings activity responds directly."
            mfr.contains("nothing") ->
                "Nothing OS (e.g., Nothing Phone 3a): Standard AOSP Settings.Global flag supported cleanly via 7-tap gesture or Shizuku."
            mfr.contains("samsung") ->
                "Samsung One UI: Supported via 7-tap Build number or Shizuku. Knox enterprise policies on managed devices may lock Developer Options."
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") ->
                "Xiaomi HyperOS / MIUI: Global flag writes cleanly; sub-toggles like 'USB Debugging (Security settings)' require Mi Account verification."
            mfr.contains("oneplus") || mfr.contains("oppo") || mfr.contains("realme") ->
                "OxygenOS / ColorOS: Supported via 7-tap or Shizuku. Some builds auto-hide Developer Options after reboot unless re-toggled."
            mfr.contains("google") ->
                "Pixel / Stock Android: Full native compatibility with Settings.Global and Shizuku UserService."
            else ->
                "$manufacturer: Uses standard AOSP development_settings_enabled flag; OEM MDM policies may apply."
        }
    }

    private fun getManufacturerRestrictionHint(): String {
        val mfr = manufacturerName().lowercase()
        return when {
            mfr.contains("acer") ->
                "On Acer tablets, check the Privacy & Admin Inspector to see if Halo Agent or Google Device Policy enforces DISALLOW_DEBUGGING_FEATURES."
            mfr.contains("xiaomi") || mfr.contains("redmi") ->
                "On HyperOS/MIUI, ensure 'Disable permission monitoring' is enabled or no MDM lock is active."
            mfr.contains("samsung") ->
                "On Samsung One UI, check if Knox Guard or work profile MDM restricts Developer Options."
            else ->
                "Your device OEM or work profile policy may restrict global settings writes."
        }
    }
}
