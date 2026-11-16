package com.noobfree.android.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.noobfree.android.system.AdminPolicyManager
import com.noobfree.android.system.AnimationManager
import com.noobfree.android.system.AnimationState
import com.noobfree.android.system.BackgroundProcessLimit
import com.noobfree.android.system.CapabilityManager
import com.noobfree.android.system.DebugManager
import com.noobfree.android.system.DebugState
import com.noobfree.android.system.DeveloperManager
import com.noobfree.android.system.DeveloperOptionsState
import com.noobfree.android.system.DeviceCapabilities
import com.noobfree.android.system.DeviceHardwareInfo
import com.noobfree.android.system.DeviceInfoManager
import com.noobfree.android.system.DeviceManagementReport
import com.noobfree.android.system.OperationOutcome
import com.noobfree.android.system.RefreshRateInfo
import com.noobfree.android.system.RefreshRateManager
import com.noobfree.android.system.WallpaperApplyOutcome
import com.noobfree.android.system.WallpaperDestination
import com.noobfree.android.system.WallpaperHelper
import com.noobfree.android.system.WallpaperPreflightState

class DeviceRepository(
    private val context: Context,
    val developerManager: DeveloperManager,
    val animationManager: AnimationManager,
    val debugManager: DebugManager,
    val refreshRateManager: RefreshRateManager,
    val deviceInfoManager: DeviceInfoManager,
    val adminPolicyManager: AdminPolicyManager,
    val wallpaperHelper: WallpaperHelper,
    val capabilityManager: CapabilityManager
) {
    suspend fun readDeveloperOptionsState(): DeveloperOptionsState =
        developerManager.getDeveloperOptionsState()

    suspend fun enableDeveloperOptions(): OperationOutcome<DeveloperOptionsState> =
        developerManager.enableDeveloperOptions()

    suspend fun disableDeveloperOptions(): OperationOutcome<DeveloperOptionsState> =
        developerManager.disableDeveloperOptions()

    suspend fun readAnimationState(): AnimationState =
        animationManager.getAnimationState()

    suspend fun applyAnimationScale(scale: Float): OperationOutcome<AnimationState> =
        animationManager.setAllAnimationScales(scale)

    suspend fun readDebugState(): DebugState =
        debugManager.getDebugState()

    suspend fun readBackgroundProcessLimit(): BackgroundProcessLimit =
        debugManager.getBackgroundProcessLimit()

    suspend fun setBackgroundProcessLimit(limit: BackgroundProcessLimit): OperationOutcome<BackgroundProcessLimit> =
        debugManager.setBackgroundProcessLimit(limit)

    fun readRefreshRateInfo(): RefreshRateInfo =
        refreshRateManager.getRefreshRateInfo()

    fun readDeviceHardwareInfo(): DeviceHardwareInfo =
        deviceInfoManager.getDeviceInfo()

    fun readDeviceManagementReport(): DeviceManagementReport =
        adminPolicyManager.inspectDeviceManagement()

    fun readCapabilities(shizukuUid: Int? = null): DeviceCapabilities =
        capabilityManager.inspect(shizukuUid)

    fun readWallpaperPreflight(): WallpaperPreflightState =
        wallpaperHelper.checkEligibility()

    suspend fun applyWallpaper(uri: Uri, destination: WallpaperDestination): WallpaperApplyOutcome =
        wallpaperHelper.applyWallpaperFromUri(uri, destination)

    suspend fun applyBuiltInGlassWallpaper(destination: WallpaperDestination): WallpaperApplyOutcome =
        wallpaperHelper.applyBuiltInGlassWallpaper(destination)

    fun launchSystemWallpaperIntent(uri: Uri): Boolean =
        wallpaperHelper.launchSystemWallpaperIntent(uri)

    /**
     * Tries multiple explicit Developer Options component & action intents so the user
     * can test opening Developer Options directly even when About Tablet -> Build Number is blocked.
     */
    fun openDeveloperOptionsSettings(): Boolean = launchSettingsIntent(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent("com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.DevelopmentSettings"
            )
        ),
        Intent("android.settings.ADB_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openDeviceInfoSettings(): Boolean = launchSettingsIntent(
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$MyDeviceInfoActivity"
            )
        ),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openDisplaySettings(): Boolean = launchSettingsIntent(
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openWirelessDebuggingOrDevSettings(): Boolean = launchSettingsIntent(
        Intent("android.settings.ADB_WIFI_SETTINGS"),
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$WirelessDebuggingActivity"
            )
        ),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openSystemSettings(): Boolean = launchSettingsIntent(
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openDeviceAdminSettings(): Boolean =
        adminPolicyManager.openDeviceAdminSettings()

    fun openAppDetailsSettings(): Boolean = launchSettingsIntent(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        },
        Intent(Settings.ACTION_SETTINGS)
    )

    private fun launchSettingsIntent(vararg intents: Intent): Boolean {
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
}
