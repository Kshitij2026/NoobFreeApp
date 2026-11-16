package com.noobfree.android.system

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class WallpaperDestination(val label: String, val flags: Int) {
    HOME_SCREEN("Home Screen", WallpaperManager.FLAG_SYSTEM),
    LOCK_SCREEN("Lock Screen", WallpaperManager.FLAG_LOCK),
    BOTH("Home & Lock Screens", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
}

data class WallpaperPreflightState(
    val hasManifestPermission: Boolean,
    val isWallpaperSupported: Boolean,
    val isSetWallpaperAllowed: Boolean,
    val isBlockedByUserRestriction: Boolean,
    val statusHeadline: String,
    val statusDetail: String
) {
    // Never grey out the UI button: always allow the user to test applying a wallpaper
    val canApplyWallpaper: Boolean
        get() = true
}

data class WallpaperApplyOutcome(
    val success: Boolean,
    val destination: WallpaperDestination,
    val message: String,
    val sourceDimensions: String? = null,
    val decodedDimensions: String? = null,
    val sampleSizeUsed: Int? = null
)

/**
 * Multi-strategy Wallpaper engine for Android 8.0–16+.
 * Never pre-blocks the UI button: always attempts a 3-stage API fallback chain:
 *  1) WallpaperManager.setBitmap(bitmap, null, true, flags)
 *  2) WallpaperManager.setBitmap(bitmap) (universal 1-arg fallback)
 *  3) WallpaperManager.setStream(inputStream) (raw stream fallback)
 * Also provides a 1-tap built-in Glass Gradient wallpaper generator (no file URI required)
 * and a system ACTION_ATTACH_DATA wallpaper chooser intent launcher.
 */
class WallpaperHelper(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val DEFAULT_MAX_WALLPAPER_DIMENSION = 2560

        fun calculateInSampleSize(
            sourceWidth: Int,
            sourceHeight: Int,
            maxDimension: Int = DEFAULT_MAX_WALLPAPER_DIMENSION
        ): Int {
            if (sourceWidth <= 0 || sourceHeight <= 0) return 1
            var inSampleSize = 1
            while ((sourceWidth / inSampleSize > maxDimension ||
                    sourceHeight / inSampleSize > maxDimension) &&
                inSampleSize < 32
            ) {
                inSampleSize *= 2
            }
            return inSampleSize
        }
    }

    fun checkEligibility(): WallpaperPreflightState {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SET_WALLPAPER
        ) == PackageManager.PERMISSION_GRANTED

        val um = context.getSystemService(UserManager::class.java)
        val userRestricted = runCatching {
            um?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) == true
        }.getOrDefault(false)

        val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        val supported = runCatching { wm?.isWallpaperSupported == true }.getOrDefault(true)
        val allowed = runCatching { wm?.isSetWallpaperAllowed == true }.getOrDefault(true)

        val (headline, detail) = when {
            userRestricted ->
                "Notice: DISALLOW_SET_WALLPAPER Reported" to
                    "UserManager reports DISALLOW_SET_WALLPAPER = true, but Wallpaper Studio remains unlocked so you can test direct setBitmap/setStream and system intent methods."
            !allowed ->
                "Notice: isSetWallpaperAllowed = false" to
                    "WallpaperManager reports setWallpaperAllowed = false, but all Wallpaper Studio buttons remain active for direct testing."
            else ->
                "Ready to Apply Wallpaper" to
                    "Multi-stage Wallpaper engine active (flagged setBitmap -> legacy setBitmap -> setStream fallback)."
        }

        return WallpaperPreflightState(
            hasManifestPermission = hasPerm,
            isWallpaperSupported = supported,
            isSetWallpaperAllowed = allowed,
            isBlockedByUserRestriction = userRestricted,
            statusHeadline = headline,
            statusDetail = detail
        )
    }

    suspend fun applyWallpaperFromUri(
        uri: Uri,
        destination: WallpaperDestination = WallpaperDestination.HOME_SCREEN
    ): WallpaperApplyOutcome = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val boundsReadOk = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
                true
            } ?: false
        }.getOrElse { err ->
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "Could not read selected URI (${uri.scheme}): ${err.localizedMessage ?: "Permission denied by document provider."}"
            )
        }

        if (!boundsReadOk || boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "Selected file could not be decoded (${boundsOptions.outWidth}x${boundsOptions.outHeight}). Try a local JPG or PNG image."
            )
        }

        val wm = WallpaperManager.getInstance(context)
        val desiredMax = runCatching {
            max(max(wm.desiredMinimumWidth, wm.desiredMinimumHeight), DEFAULT_MAX_WALLPAPER_DIMENSION)
                .coerceIn(1280, 2560)
        }.getOrDefault(1920)

        val sampleSize = calculateInSampleSize(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            maxDimension = desiredMax
        )

        var decodedBitmap: Bitmap? = null
        try {
            decodedBitmap = decodeSampledBitmap(uri, sampleSize, Bitmap.Config.ARGB_8888)
                ?: decodeSampledBitmap(uri, max(sampleSize * 2, 2), Bitmap.Config.RGB_565)

            val bitmap = decodedBitmap ?: return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "BitmapFactory could not decode image stream. Try the 1-Tap Built-In Glass Wallpaper button to test WallpaperManager directly."
            )

            val srcDim = "${boundsOptions.outWidth}×${boundsOptions.outHeight}"
            val decDim = "${bitmap.width}×${bitmap.height}"
            val errors = mutableListOf<String>()

            // Stage 1: Flagged setBitmap (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val id1 = runCatching {
                    wm.setBitmap(bitmap, null, true, destination.flags)
                }.onFailure { errors.add("setBitmap(flags): ${it.javaClass.simpleName}: ${it.localizedMessage}") }
                    .getOrDefault(0)

                if (id1 > 0) {
                    return@withContext WallpaperApplyOutcome(
                        success = true,
                        destination = destination,
                        message = "${destination.label} wallpaper updated via setBitmap(flags) (ID #$id1, $decDim).",
                        sourceDimensions = srcDim,
                        decodedDimensions = decDim,
                        sampleSizeUsed = sampleSize
                    )
                }
            }

            // Stage 2: Legacy 1-argument setBitmap(bitmap)
            val stage2Ok = runCatching {
                wm.setBitmap(bitmap)
                true
            }.onFailure { errors.add("setBitmap(legacy): ${it.javaClass.simpleName}: ${it.localizedMessage}") }
                .getOrDefault(false)

            if (stage2Ok) {
                return@withContext WallpaperApplyOutcome(
                    success = true,
                    destination = destination,
                    message = "Wallpaper updated via universal setBitmap(bitmap) fallback ($decDim).",
                    sourceDimensions = srcDim,
                    decodedDimensions = decDim,
                    sampleSizeUsed = sampleSize
                )
            }

            // Stage 3: Direct InputStream setStream()
            val stage3Ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.setStream(stream, null, true, destination.flags) > 0
                    } else {
                        wm.setStream(stream)
                        true
                    }
                } ?: false
            }.onFailure { errors.add("setStream: ${it.javaClass.simpleName}: ${it.localizedMessage}") }
                .getOrDefault(false)

            if (stage3Ok) {
                return@withContext WallpaperApplyOutcome(
                    success = true,
                    destination = destination,
                    message = "Wallpaper updated via direct setStream() fallback.",
                    sourceDimensions = srcDim,
                    decodedDimensions = decDim,
                    sampleSizeUsed = sampleSize
                )
            }

            val errSummary = if (errors.isNotEmpty()) errors.joinToString(" | ") else "WallpaperManager returned 0 (blocked by DISALLOW_SET_WALLPAPER or custom MDM launcher)."
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "All 3 WallpaperManager methods were rejected by Android OS: $errSummary"
            )
        } finally {
            runCatching { decodedBitmap?.recycle() }
        }
    }

    /**
     * Generates a high-contrast NoobFree Cosmic Glass wallpaper bitmap directly in memory
     * and applies it through the multi-stage fallback chain (bypasses any URI/file picker issues).
     */
    suspend fun applyBuiltInGlassWallpaper(
        destination: WallpaperDestination = WallpaperDestination.BOTH
    ): WallpaperApplyOutcome = withContext(ioDispatcher) {
        val wm = WallpaperManager.getInstance(context)
        val width = 1200
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(0xFF060B19.toInt(), 0xFF0F172A.toInt(), 0xFF1E1040.toInt(), 0xFF060B19.toInt()),
                    floatArrayOf(0f, 0.35f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val cyanOrb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width * 0.8f, height * 0.22f, width * 0.65f,
                    intArrayOf(0x6600E5FF, 0x0000E5FF),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(width * 0.8f, height * 0.22f, width * 0.65f, cyanOrb)

            val violetOrb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width * 0.2f, height * 0.68f, width * 0.7f,
                    intArrayOf(0x66A855F7, 0x00A855F7),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(width * 0.2f, height * 0.68f, width * 0.7f, violetOrb)

            val errors = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val id = runCatching { wm.setBitmap(bitmap, null, true, destination.flags) }
                    .onFailure { errors.add(it.localizedMessage ?: it.javaClass.simpleName) }
                    .getOrDefault(0)
                if (id > 0) {
                    return@withContext WallpaperApplyOutcome(
                        success = true,
                        destination = destination,
                        message = "Built-in NoobFree Cosmic Glass wallpaper applied to ${destination.label} (ID #$id)."
                    )
                }
            }

            val fallbackOk = runCatching {
                wm.setBitmap(bitmap)
                true
            }.onFailure { errors.add(it.localizedMessage ?: it.javaClass.simpleName) }
                .getOrDefault(false)

            return@withContext if (fallbackOk) {
                WallpaperApplyOutcome(
                    success = true,
                    destination = destination,
                    message = "Built-in NoobFree Cosmic Glass wallpaper applied via legacy setBitmap()."
                )
            } else {
                WallpaperApplyOutcome(
                    success = false,
                    destination = destination,
                    message = "Android OS rejected built-in wallpaper: ${errors.joinToString("; ").ifBlank { "Blocked by DISALLOW_SET_WALLPAPER or MDM launcher overlay." }}"
                )
            }
        } finally {
            runCatching { bitmap.recycle() }
        }
    }

    fun launchSystemWallpaperIntent(uri: Uri): Boolean {
        val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        val cropIntent = runCatching { wm?.getCropAndSetWallpaperIntent(uri) }.getOrNull()
        val attachIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(uri, "image/*")
            putExtra("mimeType", "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(attachIntent, "Set wallpaper using").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        for (candidate in listOfNotNull(cropIntent, chooser)) {
            candidate.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(candidate); true }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    private fun decodeSampledBitmap(
        uri: Uri,
        sampleSize: Int,
        config: Bitmap.Config
    ): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = config
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
