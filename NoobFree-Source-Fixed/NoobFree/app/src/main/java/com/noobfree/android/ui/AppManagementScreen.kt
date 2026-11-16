package com.noobfree.android.ui

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobfree.android.data.CommandExecutor
import com.noobfree.android.system.PackageOperationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ScannedApp(
    val label: String, val packageName: String, val isSystem: Boolean,
    val isEnabled: Boolean, val category: String
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(onBack: () -> Unit, commandExecutor: CommandExecutor) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()
    val operationManager = remember(commandExecutor) { PackageOperationManager(commandExecutor) }
    val apps = remember { mutableStateListOf<ScannedApp>() }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ScannedApp?>(null) }
    var wallpaperRestricted by remember { mutableStateOf<Boolean?>(null) }
    var isDeviceOwner by remember { mutableStateOf(false) }
    var isProfileOwner by remember { mutableStateOf(false) }
    var wallpaperBusy by remember { mutableStateOf(false) }
    fun scan() {
        try {
            val installed = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") packageManager.getInstalledApplications(0)
            }
            apps.clear()
            apps.addAll(installed.map { info ->
                val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val category = when {
                    info.packageName.startsWith("com.android.") || info.packageName == "android" -> "Android system"
                    info.packageName.startsWith("com.google.") -> "Google"
                    system -> "Manufacturer / system"
                    else -> "User-installed"
                }
                ScannedApp(packageManager.getApplicationLabel(info).toString(), info.packageName, system, info.enabled, category)
            }.sortedWith(compareBy<ScannedApp> { !it.isSystem }.thenBy { it.label.lowercase() }))
            scanError = null
        } catch (e: Exception) { scanError = "Couldn't scan apps: ${e.localizedMessage ?: "unknown error"}" }
        val um = context.getSystemService(UserManager::class.java)
        wallpaperRestricted = um?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) == true
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true
        isProfileOwner = dpm?.isProfileOwnerApp(context.packageName) == true
    }


    var uninstallingPackage by remember { mutableStateOf<String?>(null) }
    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // The package installer usually returns after the user confirms or cancels.
        // Verify the actual package state instead of trusting its result code.
        val lastPackage = uninstallingPackage
        if (lastPackage != null) {
            val stillInstalled = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getApplicationInfo(lastPackage, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") packageManager.getApplicationInfo(lastPackage, 0)
                }
                true
            }.getOrDefault(false)
            resultMessage = if (stillInstalled) {
                "Returned from Android's installer. $lastPackage is still installed; the uninstall may have been cancelled or not permitted."
            } else {
                "Successfully removed $lastPackage."
            }
            uninstallingPackage = null
            scan()
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            resultMessage = "No image was selected. Wallpaper was not changed."
        } else {
            wallpaperBusy = true
            resultMessage = "Image selected. Applying home-screen wallpaper…"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    try {
                        val resolver = context.contentResolver
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                            ?: error("Android could not open the selected image.")
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                            error("The selected file is not a supported image.")
                        }
                        val display = context.resources.displayMetrics
                        val targetWidth = display.widthPixels.coerceAtLeast(1)
                        val targetHeight = display.heightPixels.coerceAtLeast(1)
                        var sample = 1
                        val sourceMax = maxOf(bounds.outWidth, bounds.outHeight)
                        val targetMax = maxOf(targetWidth, targetHeight)
                        while (sourceMax / (sample * 2) >= targetMax) {
                            sample *= 2
                        }
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bitmap = resolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        } ?: error("The selected image could not be decoded.")
                        try {
                            WallpaperManager.getInstance(context).setBitmap(
                                bitmap, null, true, WallpaperManager.FLAG_SYSTEM
                            )
                            "success" to "Home-screen wallpaper applied. Check the home screen."
                        } finally {
                            bitmap.recycle()
                        }
                    } catch (security: SecurityException) {
                        "error" to "Android blocked the wallpaper change (permission or administrator policy): ${security.message ?: security.javaClass.simpleName}"
                    } catch (error: Exception) {
                        "error" to "Wallpaper could not be applied: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
                resultMessage = outcome.second
                wallpaperBusy = false
            }
        }
    }


    LaunchedEffect(Unit) { scan() }
    val filtered = apps.filter { (showSystem || !it.isSystem) &&
        (it.label.contains(query, true) || it.packageName.contains(query, true)) }

    if (pending != null) {
        val app = pending!!
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (app.isEnabled) "Disable application?" else "Enable application?") },
            text = { Text("${app.label}\n${app.packageName}\n\nDisabling can affect device features. Essential Android packages are blocked by NoobFree's safety guard. This action requires an active, authorized Shizuku service.") },
            confirmButton = { Button(onClick = {
                pending = null
                scope.launch {
                    val outcome = operationManager.setEnabled(app.packageName, !app.isEnabled)
                    resultMessage = outcome.detail
                    scan()
                }
            }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("App & Restriction Manager") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        }, actions = { IconButton(onClick = { scan() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Rescan") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row { Icon(Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text("  Installed applications", fontWeight = FontWeight.SemiBold) }
                        Text("${apps.size} visible apps · ${apps.count { it.isSystem }} system or updated-system apps")
                        Text(if (commandExecutor.isReady()) "Shizuku shell: connected" else "Shizuku shell: not connected — package changes are unavailable")
                        Text("NoobFree is not Device Owner: ${!isDeviceOwner}; Profile Owner: $isProfileOwner", style = MaterialTheme.typography.bodySmall)
                        scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Wallpaper & policy", fontWeight = FontWeight.SemiBold)
                        Text(if (wallpaperRestricted == true) "Wallpaper changes are restricted by a visible user restriction." else "No DISALLOW_SET_WALLPAPER restriction is visible to this app.")
                        Button(
                            onClick = {
                                runCatching { wallpaperPicker.launch(arrayOf("image/*")) }
                                    .onFailure { resultMessage = "Could not open image picker: ${it.message ?: it.javaClass.simpleName}" }
                            },
                            enabled = wallpaperRestricted != true && !wallpaperBusy
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Text(if (wallpaperBusy) "  Applying wallpaper…" else "  Choose and apply wallpaper")
                        }
                        Text("NoobFree decodes the selected image and asks Android to set it as the home-screen wallpaper. Any failure should be shown below.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            resultMessage?.let { msg -> item { Card { Column(Modifier.padding(12.dp)) { Text(msg); TextButton(onClick = { resultMessage = null }) { Text("Dismiss") } } } } }
            item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Search app name or package") }) }
            item { FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text(if (showSystem) "Showing system apps" else "Hide system apps") }) }
            items(filtered, key = { it.packageName }) { app ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${app.category} · ${if (app.isEnabled) "Enabled" else "Disabled"}", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val protected = operationManager.isProtected(app.packageName)
                            Button(
                                onClick = { pending = app },
                                enabled = !protected && commandExecutor.isReady()
                            ) {
                                Text(if (app.isEnabled) "Disable" else "Enable")
                            }
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                            }) { Text("App info") }
                            if (app.packageName != context.packageName) TextButton(onClick = {
                                val warning = if (app.isSystem) {
                                    "${app.label} is a preinstalled/system app. Android may only offer to remove its updates, or may refuse removal."
                                } else {
                                    "Android will ask you to confirm removal of ${app.label}."
                                }
                                resultMessage = warning
                                val uninstallIntent = Intent(
                                    Intent.ACTION_DELETE,
                                    Uri.parse("package:${app.packageName}")
                                )
                                uninstallingPackage = app.packageName
                                runCatching { uninstallLauncher.launch(uninstallIntent) }
                                    .onFailure { error ->
                                        uninstallingPackage = null
                                        resultMessage = when (error) {
                                            is android.content.ActivityNotFoundException ->
                                                "No Android package-uninstall screen is available. Open App info and try uninstalling there."
                                            is SecurityException ->
                                                "Android denied the uninstall request: ${error.message ?: "permission or administrator policy"}"
                                            else -> "Couldn't open Android's uninstall screen: ${error.message ?: error.javaClass.simpleName}"
                                        }
                                    }
                            }) { Text(if (app.isSystem) "Remove updates" else "Uninstall") }
                        }
                        if (operationManager.isProtected(app.packageName)) {
                            Text("Disable is blocked by NoobFree's safety protection.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (!commandExecutor.isReady()) {
                            Text("Disable/Enable requires an authorized Shizuku shell in this build.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
