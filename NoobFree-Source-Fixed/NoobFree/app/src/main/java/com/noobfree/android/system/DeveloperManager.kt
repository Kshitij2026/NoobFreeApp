package com.noobfree.android.system

import android.content.ContentResolver
import android.os.Build
import android.provider.Settings
import com.noobfree.android.data.CommandExecutor

class DeveloperManager(
    private val contentResolver: ContentResolver,
    private val commandExecutor: CommandExecutor,
    // Injectable so local JVM tests do not depend on Android framework Build fields.
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
                message = "Blocked by administrator policy (DISALLOW_DEBUGGING_FEATURES). An active Device Owner or organization administrator prevents enabling Developer Options until unenrolled.",
                executedCommand = null
            )
        }

        if (!commandExecutor.isReady()) {
            val current = getDeveloperOptionsState()
            return OperationOutcome(
                success = false,
                data = current,
                message = "Direct global flag write requires Shizuku. Without Shizuku, use the 'Open About Tablet (7-Tap Guide)' button to enable Developer Options natively in Settings.",
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
        if (!commandExecutor.isReady()) {
            val current = getDeveloperOptionsState()
            return OperationOutcome(
                success = false,
                data = current,
                message = "Shizuku is not connected. Without Shizuku, open Android Developer Options Settings and toggle the top 'Use developer options' switch off.",
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
            "Step 1: Tap 'Open About Tablet' below to jump directly to Android's Device Info screen.",
            "Step 2: $pathNote",
            "Step 3: Tap 'Build number' rapidly 7 times until Android displays 'You are now a developer!'.",
            "Step 4: If prompted, confirm your tablet's lock-screen PIN, pattern, or password.",
            "Step 5: Return to NoobFree—status is re-verified automatically on resume—or tap 'Open Developer Options' to configure USB / Wireless Debugging."
        )
    }

    fun getManufacturerCompatibilityNote(): String {
        val manufacturer = manufacturerName()
        val mfr = manufacturer.lowercase()
        return when {
            mfr.contains("acer") ->
                "Acer Tablet ($manufacturer): Uses standard AOSP Settings.Global.DEVELOPMENT_SETTINGS_ENABLED. If Halo Agent or Google Android Device Policy enforces DISALLOW_DEBUGGING_FEATURES, Android Settings blocks the 7-tap Build number gesture until the administrator restriction is lifted."
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
