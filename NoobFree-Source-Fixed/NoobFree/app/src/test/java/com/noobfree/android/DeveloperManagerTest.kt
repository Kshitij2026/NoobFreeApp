package com.noobfree.android

import android.content.ContentResolver
import com.noobfree.android.data.CommandExecutor
import com.noobfree.android.service.ShizukuUserService
import com.noobfree.android.system.AdminPolicyManager
import com.noobfree.android.system.AnimationManager
import com.noobfree.android.system.BackgroundProcessLimit
import com.noobfree.android.system.DebloatSafetyTier
import com.noobfree.android.system.DebugManager
import com.noobfree.android.system.DeveloperManager
import com.noobfree.android.system.ManagementPrivilegeRole
import com.noobfree.android.system.PackageOperationManager
import com.noobfree.android.system.WallpaperHelper
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperManagerTest {

    private class FakeCommandExecutor(
        var ready: Boolean = true,
        private val initialSettings: MutableMap<String, String> = mutableMapOf(
            "development_settings_enabled" to "0",
            "window_animation_scale" to "1",
            "transition_animation_scale" to "1",
            "animator_duration_scale" to "1",
            "background_process_limit" to "-1",
            "adb_enabled" to "0",
            "adb_wifi_enabled" to "0"
        ),
        private val disabledPackages: MutableSet<String> = mutableSetOf(),
        var failWrites: Boolean = false
    ) : CommandExecutor {
        val executedCommands = mutableListOf<String>()

        override fun isReady(): Boolean = ready

        override suspend fun execute(command: String, timeoutMs: Long): ShellCommandResult {
            executedCommands.add(command)
            if (!ready) {
                return ShellCommandResult(-100, "", "Shizuku not connected")
            }
            val trimmed = command.trim()
            val parts = trimmed.split(" ")
            if (parts.size >= 4 && parts[0] == "settings" && parts[1] == "get" && parts[2] == "global") {
                val key = parts[3]
                return ShellCommandResult(0, initialSettings[key] ?: "null", "")
            }
            if (parts.size >= 5 && parts[0] == "settings" && parts[1] == "put" && parts[2] == "global") {
                if (failWrites) {
                    return ShellCommandResult(1, "", "SecurityException: Permission denial")
                }
                val key = parts[3]
                val value = parts[4]
                initialSettings[key] = value
                return ShellCommandResult(0, "", "")
            }
            if (trimmed.startsWith("pm disable-user --user 0 ")) {
                if (failWrites) return ShellCommandResult(1, "", "SecurityException")
                val pkg = parts.last()
                disabledPackages.add(pkg)
                return ShellCommandResult(0, "Package $pkg new state: disabled-user", "")
            }
            if (trimmed.startsWith("pm enable ")) {
                if (failWrites) return ShellCommandResult(1, "", "SecurityException")
                val pkg = parts.last()
                disabledPackages.remove(pkg)
                return ShellCommandResult(0, "Package $pkg new state: enabled", "")
            }
            if (trimmed.startsWith("pm list packages -d ")) {
                val pkg = parts.last()
                val out = if (pkg in disabledPackages) "package:$pkg" else ""
                return ShellCommandResult(0, out, "")
            }
            return ShellCommandResult(1, "", "Unsupported command")
        }
    }

    @Test
    fun `enableDeveloperOptions writes and verifies development_settings_enabled`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = true)
        val manager = DeveloperManager(resolver, executor, { "Acer" }, { false })

        val outcome = manager.enableDeveloperOptions()

        assertTrue(outcome.success)
        assertTrue(outcome.data.isEnabled)
        assertEquals(1, outcome.data.rawGlobalValue)
        assertTrue(executor.executedCommands.contains("settings put global development_settings_enabled 1"))
        assertTrue(executor.executedCommands.contains("settings get global development_settings_enabled"))
    }

    @Test
    fun `enableDeveloperOptions reports 7-tap guidance when Shizuku is disconnected`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = false)
        val manager = DeveloperManager(resolver, executor, { "Acer" }, { false })

        val outcome = manager.enableDeveloperOptions()

        assertFalse(outcome.success)
        assertFalse(outcome.data.isEnabled)
        assertTrue(outcome.message.contains("7-Tap"))
    }

    @Test
    fun `enableDeveloperOptions blocks when DISALLOW_DEBUGGING_FEATURES is active`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = true)
        val manager = DeveloperManager(resolver, executor, { "Acer" }, { true })

        val outcome = manager.enableDeveloperOptions()

        assertFalse(outcome.success)
        assertTrue(outcome.data.isBlockedByAdminRestriction)
        assertTrue(outcome.message.contains("DISALLOW_DEBUGGING_FEATURES"))
    }

    @Test
    fun `enableDeveloperOptions reports failure when OEM blocks write`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = true, failWrites = true)
        val manager = DeveloperManager(resolver, executor, { "Acer" }, { false })

        val outcome = manager.enableDeveloperOptions()

        assertFalse(outcome.success)
        assertFalse(outcome.data.isEnabled)
    }

    @Test
    fun `animationManager updates and verifies all three animation scales`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = true)
        val animManager = AnimationManager(resolver, executor)

        val outcome = animManager.setAllAnimationScales(0.5f)

        assertTrue(outcome.success)
        assertEquals(0.5f, outcome.data.windowScale, 0.001f)
        assertEquals(0.5f, outcome.data.transitionScale, 0.001f)
        assertEquals(0.5f, outcome.data.animatorScale, 0.001f)
    }

    @Test
    fun `debugManager sets and verifies background process limit`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = true)
        val debugManager = DebugManager(resolver, executor)

        val outcome = debugManager.setBackgroundProcessLimit(BackgroundProcessLimit.AT_MOST_2)

        assertTrue(outcome.success)
        assertEquals(BackgroundProcessLimit.AT_MOST_2, outcome.data)
    }

    @Test
    fun `manufacturer compatibility note handles Acer and unavailable manufacturer`() {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val executor = FakeCommandExecutor(ready = true)
        val unknownManager = DeveloperManager(resolver, executor, { "" }, { false })
        val acerManager = DeveloperManager(resolver, executor, { "Acer" }, { false })

        assertTrue(unknownManager.getManufacturerCompatibilityNote().contains("Unknown"))
        assertTrue(acerManager.getManufacturerCompatibilityNote().contains("Acer"))
        assertEquals(5, acerManager.getSevenTapGuideSteps().size)
    }

    @Test
    fun `userService command allowlist blocks arbitrary shell commands`() {
        assertTrue(ShizukuUserService.isCommandAllowed("settings put global development_settings_enabled 1"))
        assertTrue(ShizukuUserService.isCommandAllowed("settings put global window_animation_scale 0.5"))
        assertFalse(ShizukuUserService.isCommandAllowed("rm -rf /sdcard"))
        assertFalse(ShizukuUserService.isCommandAllowed("settings put global development_settings_enabled 1; reboot"))
        assertTrue(ShizukuUserService.isCommandAllowed("pm disable-user --user 0 com.example.app"))
        assertTrue(ShizukuUserService.isCommandAllowed("pm enable com.example.app"))
        assertTrue(ShizukuUserService.isCommandAllowed("pm list packages -d com.example.app"))
        assertFalse(ShizukuUserService.isCommandAllowed("pm disable-user --user 0 com.example.app; reboot"))
        assertFalse(ShizukuUserService.isCommandAllowed("pm disable-user --user 0 com.example.app && rm -rf /"))
    }

    @Test
    fun `packageOperationManager protects critical packages and allows safe debloat and restore`() = runTest {
        val executor = FakeCommandExecutor(ready = true)
        val pkgManager = PackageOperationManager(executor)

        assertTrue(pkgManager.isProtected("com.android.systemui"))
        assertTrue(pkgManager.isProtected("com.google.android.gms"))
        assertTrue(pkgManager.isProtected("com.google.android.apps.work.clouddpc"))
        assertTrue(pkgManager.isProtected("com.custom.home", setOf("com.custom.home")))
        assertFalse(pkgManager.isProtected("com.facebook.katana"))

        assertEquals(
            DebloatSafetyTier.SAFE_DEBLOAT_CANDIDATE,
            pkgManager.classifySafetyTier("com.facebook.katana", isSystemApp = true, isActiveAdminOrOwner = false)
        )

        val disableRes = pkgManager.setEnabled("com.facebook.katana", enabled = false)
        assertTrue(disableRes.success)

        val enableRes = pkgManager.setEnabled("com.facebook.katana", enabled = true)
        assertTrue(enableRes.success)
    }

    @Test
    fun `adminPolicyManager accurately classifies roles and uninstall eligibility`() {
        assertEquals(
            ManagementPrivilegeRole.DEVICE_OWNER,
            AdminPolicyManager.classifyManagementRole(
                isDeviceOwner = true,
                isProfileOwner = false,
                isActiveAdmin = true,
                hasAdminReceiver = true,
                isHaloOrMdmCompanion = true
            )
        )
        assertEquals(
            ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN,
            AdminPolicyManager.classifyManagementRole(
                isDeviceOwner = false,
                isProfileOwner = false,
                isActiveAdmin = true,
                hasAdminReceiver = true,
                isHaloOrMdmCompanion = false
            )
        )
        assertEquals(
            ManagementPrivilegeRole.MANAGED_COMPANION,
            AdminPolicyManager.classifyManagementRole(
                isDeviceOwner = false,
                isProfileOwner = false,
                isActiveAdmin = false,
                hasAdminReceiver = false,
                isHaloOrMdmCompanion = true
            )
        )

        // Active ordinary admin blocks direct uninstall until deactivated
        assertFalse(
            AdminPolicyManager.canLaunchDirectUninstall(
                role = ManagementPrivilegeRole.ACTIVE_ORDINARY_ADMIN,
                isSystemApp = false,
                isUpdatedSystemApp = false,
                isUninstallBlockedByPolicy = false,
                globalDisallowUninstall = false
            )
        )
        // Once deactivated (INACTIVE_ADMIN_CAPABLE), user app can be uninstalled directly
        assertTrue(
            AdminPolicyManager.canLaunchDirectUninstall(
                role = ManagementPrivilegeRole.INACTIVE_ADMIN_CAPABLE,
                isSystemApp = false,
                isUpdatedSystemApp = false,
                isUninstallBlockedByPolicy = false,
                globalDisallowUninstall = false
            )
        )
    }

    @Test
    fun `wallpaperHelper calculates power-of-two sample sizes safely`() {
        assertEquals(1, WallpaperHelper.calculateInSampleSize(1920, 1080, 2560))
        assertEquals(2, WallpaperHelper.calculateInSampleSize(4000, 3000, 2560))
        assertEquals(4, WallpaperHelper.calculateInSampleSize(8000, 6000, 2560))
    }
}
