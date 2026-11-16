package com.noobfree.android.system

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class DeviceInfoManager(private val context: Context) {

    private fun safeBuildString(value: () -> String): String =
        runCatching { value() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Unknown"

    fun getDeviceInfo(): DeviceHardwareInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRam = formatBytes(memInfo.totalMem)
        val availRam = formatBytes(memInfo.availMem)

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)

        val resolution = if (display != null) {
            "${display.mode.physicalWidth} × ${display.mode.physicalHeight} px"
        } else {
            "${metrics.widthPixels} × ${metrics.heightPixels} px"
        }

        val ratesSummary = display?.supportedModes
            ?.map { "${it.refreshRate.roundToInt()} Hz" }
            ?.distinct()
            ?.joinToString(" • ")
            ?: "60 Hz"

        val osSkin = detectOsSkin()
        val marketName = detectMarketName()

        return DeviceHardwareInfo(
            manufacturer = safeBuildString { Build.MANUFACTURER }.replaceFirstChar { it.titlecase(Locale.US) },
            brand = safeBuildString { Build.BRAND }.replaceFirstChar { it.titlecase(Locale.US) },
            model = safeBuildString { Build.MODEL },
            deviceCodename = safeBuildString { Build.DEVICE },
            marketName = marketName,
            osSkinName = osSkin,
            androidVersion = safeBuildString { Build.VERSION.RELEASE },
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = safeBuildString { Build.VERSION.SECURITY_PATCH },
            buildId = safeBuildString { Build.DISPLAY },
            totalRamFormatted = totalRam,
            availableRamFormatted = availRam,
            cpuHardware = readCpuHardware(),
            supportedAbis = runCatching { Build.SUPPORTED_ABIS.joinToString(", ") }.getOrDefault("arm64-v8a"),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            screenResolution = resolution,
            screenDensityDpi = metrics.densityDpi,
            supportedRefreshRatesSummary = ratesSummary,
            manufacturerCompatibilityNotes = buildCompatibilityNotes()
        )
    }

    private fun detectMarketName(): String {
        val model = safeBuildString { Build.MODEL }
        val device = safeBuildString { Build.DEVICE }.lowercase(Locale.US)
        val manufacturer = safeBuildString { Build.MANUFACTURER }
        val mfr = manufacturer.lowercase(Locale.US)
        return when {
            mfr.contains("acer") && model.contains("T4-82L", ignoreCase = true) ->
                "Acer One 8 ($model)"
            mfr.contains("acer") ->
                "Acer $model"
            mfr.contains("nothing") && (device.contains("asteroids") || model.contains("A059", ignoreCase = true)) ->
                "Nothing Phone (3a)"
            mfr.contains("nothing") ->
                "Nothing $model ($device)"
            else -> "$manufacturer $model"
        }
    }

    private fun detectOsSkin(): String {
        val manufacturer = safeBuildString { Build.MANUFACTURER }
        val mfr = manufacturer.lowercase(Locale.US)
        return when {
            mfr.contains("acer") -> "Acer Stock Android (AOSP)"
            mfr.contains("nothing") -> "Nothing OS"
            mfr.contains("samsung") -> "Samsung One UI"
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> "Xiaomi HyperOS / MIUI"
            mfr.contains("oneplus") -> "OxygenOS"
            mfr.contains("oppo") -> "ColorOS"
            mfr.contains("realme") -> "realme UI"
            mfr.contains("google") -> "Pixel / Stock AOSP"
            else -> "Android ($manufacturer)"
        }
    }

    private fun readCpuHardware(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = runCatching { Build.SOC_MODEL }.getOrNull()
            val socMfr = runCatching { Build.SOC_MANUFACTURER }.getOrNull()
            if (!socModel.isNullOrBlank() && socModel != Build.UNKNOWN) {
                return if (!socMfr.isNullOrBlank() && socMfr != Build.UNKNOWN) {
                    "$socMfr $socModel"
                } else {
                    socModel
                }
            }
        }
        return runCatching {
            File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim()
            }
        }.getOrNull() ?: safeBuildString { Build.HARDWARE }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "Unknown"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun buildCompatibilityNotes(): List<String> = listOf(
        "Acer One 8 T4-82L-G2: Runtime-verified Android version & API level. Supports guided 7-tap Developer Options, Wallpaper memory-safe downsampling, and Halo/Google Device Policy diagnostics.",
        "Nothing OS (Nothing Phone 3a): Full compatibility with Shizuku Wireless Debugging, Developer Options toggle, and 0x–2x animation scales.",
        "Samsung One UI: Developer Options & animation scales supported. Knox MDM profiles may restrict global settings writes.",
        "Xiaomi HyperOS / MIUI: Global flags supported; 'USB Debugging (Security settings)' requires manual Mi Account verification in Settings.",
        "Stock Android / Pixel: Reference AOSP behavior with full Settings.Global support."
    )
}
