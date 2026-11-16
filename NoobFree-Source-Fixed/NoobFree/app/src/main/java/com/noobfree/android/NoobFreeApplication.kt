package com.noobfree.android

import android.app.Application
import android.os.UserManager
import com.noobfree.android.data.DeviceRepository
import com.noobfree.android.data.ShizukuRepository
import com.noobfree.android.system.AdminPolicyManager
import com.noobfree.android.system.AnimationManager
import com.noobfree.android.system.CapabilityManager
import com.noobfree.android.system.DebugManager
import com.noobfree.android.system.DeveloperManager
import com.noobfree.android.system.DeviceInfoManager
import com.noobfree.android.system.RefreshRateManager
import com.noobfree.android.system.WallpaperHelper

class NoobFreeApplication : Application() {

    lateinit var shizukuRepository: ShizukuRepository
        private set

    lateinit var deviceRepository: DeviceRepository
        private set

    lateinit var adminPolicyManager: AdminPolicyManager
        private set

    lateinit var wallpaperHelper: WallpaperHelper
        private set

    lateinit var capabilityManager: CapabilityManager
        private set

    override fun onCreate() {
        super.onCreate()
        shizukuRepository = ShizukuRepository(applicationContext)
        shizukuRepository.registerListeners()

        val userManager = getSystemService(UserManager::class.java)
        val developerManager = DeveloperManager(
            contentResolver = contentResolver,
            commandExecutor = shizukuRepository,
            disallowDebuggingProvider = {
                runCatching {
                    userManager?.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES) == true
                }.getOrDefault(false)
            }
        )
        val animationManager = AnimationManager(contentResolver, shizukuRepository)
        val debugManager = DebugManager(contentResolver, shizukuRepository)
        val refreshRateManager = RefreshRateManager(applicationContext)
        val deviceInfoManager = DeviceInfoManager(applicationContext)
        adminPolicyManager = AdminPolicyManager(applicationContext)
        wallpaperHelper = WallpaperHelper(applicationContext)
        capabilityManager = CapabilityManager(applicationContext, shizukuRepository)

        deviceRepository = DeviceRepository(
            context = applicationContext,
            developerManager = developerManager,
            animationManager = animationManager,
            debugManager = debugManager,
            refreshRateManager = refreshRateManager,
            deviceInfoManager = deviceInfoManager,
            adminPolicyManager = adminPolicyManager,
            wallpaperHelper = wallpaperHelper,
            capabilityManager = capabilityManager
        )
    }

    override fun onTerminate() {
        shizukuRepository.unregisterListeners()
        super.onTerminate()
    }
}
