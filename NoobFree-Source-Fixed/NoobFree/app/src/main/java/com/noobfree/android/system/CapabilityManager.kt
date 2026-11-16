package com.noobfree.android.system

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import com.noobfree.android.admin.NoobFreeAdminReceiver
import com.noobfree.android.data.CommandExecutor

data class DeviceCapabilities(
    val shizukuConnected: Boolean,
    val shizukuShellUid: Int?,
    val noobFreeIsDeviceAdmin: Boolean,
    val noobFreeIsDeviceOwner: Boolean,
    val noobFreeIsProfileOwner: Boolean,
    val wallpaperRestricted: Boolean,
    val debuggingRestricted: Boolean,
    val uninstallRestricted: Boolean,
    val factoryResetRestricted: Boolean,
    val oemPrivilegedBridgeAvailable: Boolean = false
)

/** Reports capabilities and restrictions actually visible to this installation; never fakes OEM or Device Owner privilege. */
class CapabilityManager(private val context: Context, private val commandExecutor: CommandExecutor) {
    fun inspect(shizukuUid: Int? = null): DeviceCapabilities {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val um = context.getSystemService(UserManager::class.java)
        val adminComp = NoobFreeAdminReceiver.getComponentName(context)
        return DeviceCapabilities(
            shizukuConnected = commandExecutor.isReady(),
            shizukuShellUid = shizukuUid,
            noobFreeIsDeviceAdmin = runCatching { dpm?.isAdminActive(adminComp) == true }.getOrDefault(false),
            noobFreeIsDeviceOwner = runCatching { dpm?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false),
            noobFreeIsProfileOwner = runCatching { dpm?.isProfileOwnerApp(context.packageName) == true }.getOrDefault(false),
            wallpaperRestricted = runCatching { um?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) == true }.getOrDefault(false),
            debuggingRestricted = runCatching { um?.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES) == true }.getOrDefault(false),
            uninstallRestricted = runCatching { um?.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS) == true }.getOrDefault(false),
            factoryResetRestricted = runCatching { um?.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET) == true }.getOrDefault(false),
            oemPrivilegedBridgeAvailable = false // Requires an OEM-signed system integration.
        )
    }
}
