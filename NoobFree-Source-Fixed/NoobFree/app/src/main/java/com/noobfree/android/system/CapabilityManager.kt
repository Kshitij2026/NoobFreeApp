package com.noobfree.android.system

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import com.noobfree.android.data.CommandExecutor

data class DeviceCapabilities(
    val shizukuConnected: Boolean,
    val shizukuShellUid: Int?,
    val noobFreeIsDeviceOwner: Boolean,
    val noobFreeIsProfileOwner: Boolean,
    val wallpaperRestricted: Boolean,
    val oemPrivilegedBridgeAvailable: Boolean = false
)

/** Reports capabilities actually available to this installation; it never assumes OEM privilege. */
class CapabilityManager(private val context: Context, private val commandExecutor: CommandExecutor) {
    fun inspect(): DeviceCapabilities {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val um = context.getSystemService(UserManager::class.java)
        return DeviceCapabilities(
            shizukuConnected = commandExecutor.isReady(),
            shizukuShellUid = null, // UID is reported by the Shizuku connection status screen.
            noobFreeIsDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true,
            noobFreeIsProfileOwner = dpm?.isProfileOwnerApp(context.packageName) == true,
            wallpaperRestricted = um?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) == true,
            oemPrivilegedBridgeAvailable = false // Requires an OEM-signed/approved integration.
        )
    }
}
