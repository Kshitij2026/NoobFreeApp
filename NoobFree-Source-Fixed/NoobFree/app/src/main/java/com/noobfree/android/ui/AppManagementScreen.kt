package com.noobfree.android.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ScannedApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val apps = remember { mutableStateListOf<ScannedApp>() }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var wallpaperRestricted by remember { mutableStateOf<Boolean?>(null) }
    var isDeviceOwner by remember { mutableStateOf(false) }
    var isProfileOwner by remember { mutableStateOf(false) }

    fun scan() {
        try {
            val installed = packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            val result = installed.map { info ->
                val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val label = packageManager.getApplicationLabel(info).toString()
                val category = when {
                    info.packageName.startsWith("com.android.") || info.packageName.startsWith("android.") -> "Android system"
                    info.packageName.startsWith("com.google.") -> "Google"
                    system -> "Manufacturer / system"
                    else -> "User-installed"
                }
                ScannedApp(label, info.packageName, system, info.enabled, category)
            }.sortedWith(compareBy<ScannedApp> { !it.isSystem }.thenBy { it.label.lowercase() })
            apps.clear()
            apps.addAll(result)
            scanError = null
        } catch (e: Exception) {
            scanError = "Couldn't scan apps: ${e.localizedMessage ?: "unknown error"}"
        }
        val userManager = context.getSystemService(UserManager::class.java)
        wallpaperRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            userManager?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) == true
        } else null
        val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true
        isProfileOwner = dpm?.isProfileOwnerApp(context.packageName) == true
    }

    LaunchedEffect(Unit) { scan() }
    val filtered = apps.filter {
        (showSystem || !it.isSystem) &&
            (it.label.contains(query, true) || it.packageName.contains(query, true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & Restriction Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scan() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Rescan")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Icon(Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(1.dp))
                            Text("Installed application inventory", fontWeight = FontWeight.SemiBold)
                        }
                        Text("${apps.size} apps visible to NoobFree · ${apps.count { it.isSystem }} system or updated-system apps")
                        Text("This first build is read-only: it will not disable or uninstall anything automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (scanError != null) Text(scanError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Restriction Inspector", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when (wallpaperRestricted) {
                                true -> "Wallpaper changes: restricted by a user restriction visible to this app."
                                false -> "Wallpaper changes: no DISALLOW_SET_WALLPAPER restriction is visible to this app."
                                null -> "Wallpaper restriction status unavailable."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("NoobFree role: " + when {
                            isDeviceOwner -> "Device Owner"
                            isProfileOwner -> "Profile Owner"
                            else -> "Ordinary app (not Device Owner/Profile Owner)"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                            enabled = wallpaperRestricted == false
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Text("  Open wallpaper picker")
                        }
                        Text("NoobFree reports restrictions; it cannot override policies set by an organization without administrator authorization.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text("Search app name or package") }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = showSystem,
                        onClick = { showSystem = !showSystem },
                        label = { Text(if (showSystem) "Showing system apps" else "Hide system apps") }
                    )
                }
            }
            items(filtered, key = { it.packageName }) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(app.category, style = MaterialTheme.typography.labelSmall)
                            Text(if (app.isEnabled) "Enabled" else "Disabled", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                            context.startActivity(intent)
                        }) { Text("Open app settings") }
                    }
                }
            }
        }
    }
}
