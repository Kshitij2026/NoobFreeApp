package com.noobfree.android.ui

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class InspectorFinding(val title: String, val status: String, val explanation: String)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyInspectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var findings by remember { mutableStateOf<List<InspectorFinding>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val policyPackage = "com.google.android.apps.work.clouddpc"
    var policyRole by remember { mutableStateOf("Checking management status…") }
    var adminPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var haloPackages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showResetWarning by remember { mutableStateOf(false) }

    fun openAdminSettings() {
        val intent = Intent("android.settings.DEVICE_ADMIN_SETTINGS")
        runCatching { context.startActivity(intent) }
            .onFailure { error = "Android admin settings could not be opened: ${it.localizedMessage}" }
    }

    fun openAppDetails(packageName: String) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.onFailure { error = "Could not open app details for $packageName: ${it.localizedMessage}" }
    }

    fun requestUninstall(packageName: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }.onFailure { error = "Android did not allow an uninstall request for $packageName: ${it.localizedMessage}" }
    }

    fun openPolicyApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(policyPackage)
        if (launch != null) {
            runCatching { context.startActivity(launch) }.onFailure { error = "Could not open Android Device Policy: ${it.localizedMessage}" }
        } else {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$policyPackage")))
            }.onFailure { error = "Android Device Policy app details could not be opened." }
        }
    }

    fun requestNoobFreeAdmin() {
        val component = ComponentName(context, com.noobfree.android.admin.NoobFreeAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Optional NoobFree administrator access. This does not make NoobFree Device Owner and cannot remove other administrators.")
        }
        runCatching { context.startActivity(intent) }
            .onFailure { error = "Could not open Android administrator activation: ${it.localizedMessage}" }
    }

    fun openFactoryResetSettings() {
        val primary = Intent("android.settings.FACTORY_RESET")
        val fallback = Intent(Settings.ACTION_SETTINGS)
        runCatching {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(primary)
        }.onFailure {
            runCatching {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            }.onFailure { error = "Could not open reset settings: ${it.localizedMessage}" }
        }
    }

    fun refresh() {
        val output = mutableListOf<InspectorFinding>()
        try {
            val pm = context.packageManager
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val installed = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getInstalledApplications(0)
            }
            haloPackages = installed.mapNotNull { app ->
                val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
                if (label.contains("halo", ignoreCase = true) || app.packageName.contains("halo", ignoreCase = true)) {
                    label to app.packageName
                } else null
            }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
            val overlayApps = installed.mapNotNull { app ->
                val mode = runCatching {
                    @Suppress("DEPRECATION")
                    appOps?.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, app.packageName)
                }.getOrNull()
                if (mode == AppOpsManager.MODE_ALLOWED) app.packageName else null
            }.sorted()
            output += InspectorFinding(
                "Display over other apps",
                "${overlayApps.size} apps reported as allowed",
                if (overlayApps.isEmpty()) "No allowed overlay apps were returned by AppOps." else overlayApps.joinToString("\n")
            )

            val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = accessibility?.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).orEmpty().map { service ->
                "${service.resolveInfo.serviceInfo.packageName}/${service.resolveInfo.serviceInfo.name}"
            }.distinct().sorted()
            output += InspectorFinding(
                "Enabled accessibility services",
                "${enabledServices.size} enabled services visible",
                if (enabledServices.isEmpty()) "No enabled accessibility services were returned." else enabledServices.joinToString("\n")
            )

            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val activeAdmins = runCatching { @Suppress("DEPRECATION") dpm?.activeAdmins.orEmpty() }.getOrDefault(emptyList())
            val policyIsDeviceOwner = runCatching { dpm?.isDeviceOwnerApp(policyPackage) == true }.getOrDefault(false)
            val policyIsProfileOwner = runCatching { dpm?.isProfileOwnerApp(policyPackage) == true }.getOrDefault(false)
            val policyIsActiveAdmin = activeAdmins.any { it.packageName == policyPackage }
            adminPackages = activeAdmins.map { it.packageName }.distinct().sorted()
            policyRole = when {
                policyIsDeviceOwner -> "Android Device Policy is Device Owner (fully managed device)"
                policyIsProfileOwner -> "Android Device Policy is Profile Owner (managed work profile)"
                policyIsActiveAdmin -> "Android Device Policy is an active legacy device administrator"
                else -> "Android Device Policy role could not be confirmed from this app"
            }
            output += InspectorFinding(
                "Google Android Device Policy role",
                policyRole,
                "This is a read-only check. Android does not let an ordinary app remove another app's administrator or clear Device Owner status."
            )
            output += InspectorFinding(
                "Active device administrators",
                "${activeAdmins.size} active admin component(s) visible",
                if (activeAdmins.isEmpty()) "No active admin components were returned to this app." else activeAdmins.joinToString("\n") { it.flattenToShortString() }
            )
            output += InspectorFinding(
                "NoobFree management role",
                when {
                    dpm?.isDeviceOwnerApp(context.packageName) == true -> "NoobFree is Device Owner"
                    dpm?.isProfileOwnerApp(context.packageName) == true -> "NoobFree is Profile Owner"
                    else -> "NoobFree is not Device Owner or Profile Owner"
                },
                "This check describes NoobFree only. It does not identify every administrator or prove whether another work profile exists."
            )

            val um = context.getSystemService(UserManager::class.java)
            val restrictions = listOf(
                UserManager.DISALLOW_DEBUGGING_FEATURES to "Developer/debugging features",
                UserManager.DISALLOW_UNINSTALL_APPS to "Uninstall applications",
                UserManager.DISALLOW_SET_WALLPAPER to "Set wallpaper",
                UserManager.DISALLOW_INSTALL_APPS to "Install applications",
                UserManager.DISALLOW_CONFIG_WIFI to "Configure Wi-Fi"
            )
            restrictions.forEach { (key, label) ->
                val restricted = runCatching { um?.hasUserRestriction(key) == true }.getOrDefault(false)
                output += InspectorFinding(
                    "Policy: $label",
                    if (restricted) "Restriction visible" else "No restriction visible",
                    if (restricted) "Android reports $key for the current user/profile." else "This restriction was not reported for the current user/profile. Other policies may still affect this action."
                )
            }
            val managedProfile = runCatching { um?.isManagedProfile == true }.getOrDefault(false)
            output += InspectorFinding(
                "Current profile context",
                if (managedProfile) "This app is running in a managed profile" else "This app is not running in a managed profile",
                "A separate work profile elsewhere on the device cannot be conclusively ruled out by this check."
            )
            findings = output
            error = null
        } catch (e: Exception) {
            error = "Some device information could not be read: ${e.localizedMessage ?: e.javaClass.simpleName}"
            findings = output
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (showResetWarning) {
        AlertDialog(
            onDismissRequest = { showResetWarning = false },
            title = { Text("Factory reset") },
            text = { Text("A factory reset erases local apps, photos, downloads, accounts and settings. It may NOT remove Android Device Policy if the tablet is registered for automatic enterprise enrollment. Back up your data and confirm with the device owner or seller before proceeding.") },
            confirmButton = {
                Button(onClick = {
                    showResetWarning = false
                    openFactoryResetSettings()
                }) { Text("Open reset settings") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetWarning = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Privacy & Admin Inspector") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            actions = { IconButton(onClick = { refresh() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh") } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Device recovery & NoobFree admin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("NoobFree can request ordinary Device Administrator access with your consent. This does not make it Device Owner, does not grant permission to remove Google/Halo administrators, and does not bypass managed-device restrictions.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { requestNoobFreeAdmin() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Make NoobFree a device admin")
                        }
                        OutlinedButton(onClick = { showResetWarning = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open factory reset options")
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Android Device Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(policyRole, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        Text("If this is a personal device enrolled in legacy Device Admin mode, open Device Policy and look for Unregister. If it is Device Owner, Google documents that switching out of that mode requires an administrator-authorized reset; NoobFree cannot bypass that protection.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { openPolicyApp() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Android Device Policy")
                        }
                        OutlinedButton(onClick = { openAppDetails(policyPackage) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Device Policy app details")
                        }
                        OutlinedButton(onClick = { requestUninstall(policyPackage) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Request uninstall of Device Policy")
                        }
                        OutlinedButton(onClick = { openAdminSettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Manage device administrators")
                        }
                        OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Recheck management status")
                        }
                        Text("Android decides whether these requests are allowed. If Device Policy is Device Owner, Android will block ordinary uninstall; use the official organization unenrollment route. NoobFree cannot override that protection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Administrator app actions", fontWeight = FontWeight.SemiBold)
                        Text("These are direct Android system requests. Deactivate an ordinary administrator in Android settings before uninstalling it. Device Owner and organization-protected apps cannot be removed by an ordinary app.", style = MaterialTheme.typography.bodySmall)
                        if (adminPackages.isEmpty()) {
                            Text("No active administrator packages were returned. Refresh the scan to check again.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            adminPackages.forEach { pkg ->
                                HorizontalDivider()
                                Text(pkg, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { openAppDetails(pkg) }, modifier = Modifier.weight(1f)) { Text("App details") }
                                    Button(onClick = { requestUninstall(pkg) }, modifier = Modifier.weight(1f)) { Text("Request uninstall") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Halo apps found on this tablet", fontWeight = FontWeight.SemiBold)
                        Text("NoobFree finds installed apps whose visible name or package contains Halo. Use Android's own uninstall confirmation. If policy blocks removal, the system will explain why.", style = MaterialTheme.typography.bodySmall)
                        if (haloPackages.isEmpty()) {
                            Text("No Halo-labelled apps were found in the installed-app list.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            haloPackages.forEach { (label, pkg) ->
                                HorizontalDivider()
                                Text(label, fontWeight = FontWeight.Medium)
                                Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { openAppDetails(pkg) }, modifier = Modifier.weight(1f)) { Text("App details") }
                                    Button(onClick = { requestUninstall(pkg) }, modifier = Modifier.weight(1f)) { Text("Uninstall") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What this scan can and cannot tell you", fontWeight = FontWeight.SemiBold)
                        Text("NoobFree reads information Android exposes to an ordinary app. A permission being granted does not prove it is actively being used. A policy not visible here may still be enforced through another mechanism.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            items(findings) { finding ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(finding.title, fontWeight = FontWeight.SemiBold)
                        Text(finding.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(finding.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
