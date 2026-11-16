package com.noobfree.android.system

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    val canApplyWallpaper: Boolean
        get() = hasManifestPermission &&
            isWallpaperSupported &&
            isSetWallpaperAllowed &&
            !isBlockedByUserRestriction
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
 * Reliable, memory-safe Wallpaper engine for Android 8.0–16+.
 * Validates permissions, UserManager restrictions (DISALLOW_SET_WALLPAPER),
 * WallpaperManager eligibility, URI stream readability, and safe bitmap downsampling
 * (including RGB_565 fallback for 2GB/3GB RAM tablets like Acer One 8 T4-82L-G2).
 */
class WallpaperHelper(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val DEFAULT_MAX_WALLPAPER_DIMENSION = 2560

        /**
         * Pure helper for calculating power-of-two BitmapFactory.inSampleSize.
         * Testable in local JVM unit tests.
         */
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
        val supported = runCatching { wm?.isWallpaperSupported == true }.getOrDefault(false)
        val allowed = runCatching { wm?.isSetWallpaperAllowed == true }.getOrDefault(false)

        val (headline, detail) = when {
            userRestricted ->
                "Blocked by DISALLOW_SET_WALLPAPER" to
                    "UserManager reports that an active Device Owner or administrator has restricted changing the wallpaper on this user profile."
            !supported ->
                "Wallpaper Not Supported" to
                    "WallpaperManager.isWallpaperSupported() returned false on this device/profile."
            !allowed ->
                "Wallpaper Modification Disallowed" to
                    "WallpaperManager.isSetWallpaperAllowed() returned false. An OEM launcher or enterprise policy locks the current wallpaper."
            !hasPerm ->
                "Missing SET_WALLPAPER Permission" to
                    "Android did not grant android.permission.SET_WALLPAPER to this package."
            else ->
                "Ready to Apply Wallpaper" to
                    "SET_WALLPAPER permission granted, WallpaperManager is active, and no visible DISALLOW_SET_WALLPAPER policy is enforced."
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
        val preflight = checkEligibility()
        if (!preflight.canApplyWallpaper) {
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "${preflight.statusHeadline}: ${preflight.statusDetail}"
            )
        }

        // Persist or verify URI read grant if the picker supplied one
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        // Step 1: Decode bounds only
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
                message = "Selected file is not a valid or readable image (decoded bounds: ${boundsOptions.outWidth}x${boundsOptions.outHeight}). Please select a local JPEG, PNG, or WebP image."
            )
        }

        val wm = WallpaperManager.getInstance(context)
        val desiredMax = runCatching {
            max(max(wm.desiredMinimumWidth, wm.desiredMinimumHeight), DEFAULT_MAX_WALLPAPER_DIMENSION)
                .coerceIn(1280, 3072)
        }.getOrDefault(DEFAULT_MAX_WALLPAPER_DIMENSION)

        val sampleSize = calculateInSampleSize(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            maxDimension = desiredMax
        )

        // Step 2: Decode downsampled bitmap with ARGB_8888, falling back to RGB_565 on low memory
        var decodedBitmap: Bitmap? = null
        try {
            decodedBitmap = decodeSampledBitmap(uri, sampleSize, Bitmap.Config.ARGB_8888)
                ?: decodeSampledBitmap(uri, max(sampleSize * 2, 2), Bitmap.Config.RGB_565)

            val bitmap = decodedBitmap ?: return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "Android BitmapFactory could not decode the image stream. Try choosing a standard JPG or PNG image stored locally on the tablet."
            )

            val srcDim = "${boundsOptions.outWidth}×${boundsOptions.outHeight}"
            val decDim = "${bitmap.width}×${bitmap.height}"

            val wallpaperId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, destination.flags)
            } else {
                wm.setBitmap(bitmap)
                1
            }

            return@withContext if (wallpaperId > 0) {
                WallpaperApplyOutcome(
                    success = true,
                    destination = destination,
                    message = "${destination.label} wallpaper applied and verified (Wallpaper ID #$wallpaperId, decoded $decDim from $srcDim).",
                    sourceDimensions = srcDim,
                    decodedDimensions = decDim,
                    sampleSizeUsed = sampleSize
                )
            } else {
                WallpaperApplyOutcome(
                    success = false,
                    destination = destination,
                    message = "WallpaperManager.setBitmap() returned 0 (operation suppressed by OEM launcher or active administrator policy).",
                    sourceDimensions = srcDim,
                    decodedDimensions = decDim,
                    sampleSizeUsed = sampleSize
                )
            }
        } catch (oom: OutOfMemoryError) {
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "Out of memory while decoding large image. Please choose a smaller image file."
            )
        } catch (sec: SecurityException) {
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "SecurityException while setting wallpaper: ${sec.localizedMessage ?: "Blocked by device administrator or URI permission."}"
            )
        } catch (e: Exception) {
            return@withContext WallpaperApplyOutcome(
                success = false,
                destination = destination,
                message = "Failed to set wallpaper: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            runCatching { decodedBitmap?.recycle() }
        }
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
