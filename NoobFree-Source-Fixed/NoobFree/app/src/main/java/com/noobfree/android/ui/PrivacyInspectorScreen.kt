package com.noobfree.android.ui

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
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
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
