package com.noobfree.android.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.noobfree.android.data.CommandExecutor
import com.noobfree.android.system.AdminPolicyManager
import com.noobfree.android.system.DebloatSafetyTier
import com.noobfree.android.system.ManagementPrivilegeRole
import com.noobfree.android.system.PackageOperationManager
import com.noobfree.android.system.WallpaperDestination
import com.noobfree.android.system.WallpaperHelper
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppCategoryFilter(val label: String) {
    ALL("All Apps"),
    USER("User Installed"),
    ADMIN_AND_HALO("Admin / Halo / MDM"),
    SAFE_DEBLOAT("Safe Debloat"),
    UPDATED_SYSTEM("Updated System"),
    SYSTEM("All System"),
    DISABLED("Disabled (Restore)")
}

private enum class AppSortOrder(val label: String) {
    NAME("Name (A–Z)"),
    PACKAGE("Package ID"),
    ROLE_FIRST("Admin / User First")
}

private data class ScannedAppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val isEnabled: Boolean,
    val categoryLabel: String,
    val safetyTier: DebloatSafetyTier,
    val managementRole: ManagementPrivilegeRole,
    val isActiveAdmin: Boolean,
    val isDeviceOrProfileOwner: Boolean,
    val isUninstallBlockedByPolicy: Boolean,
    val isHaloRelated: Boolean,
    val canDirectUninstall: Boolean,
    val iconBitmap: ImageBitmap?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppManagementScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    commandExecutor: CommandExecutor,
    adminPolicyManager: AdminPolicyManager,
    wallpaperHelper: WallpaperHelper,
    onNavigateToPrivacy: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    val operationManager = remember(commandExecutor) { PackageOperationManager(commandExecutor) }

    val apps = remember { mutableStateListOf<ScannedAppItem>() }
    var activeLauncherPkgs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isScanning by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategoryFilter.ALL) }
    var sortOrder by remember { mutableStateOf(AppSortOrder.ROLE_FIRST) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    var pendingToggleApp by remember { mutableStateOf<ScannedAppItem?>(null) }
    var selectedDetailApp by remember { mutableStateOf<ScannedAppItem?>(null) }
    var blockedAdminAppDialog by remember { mutableStateOf<ScannedAppItem?>(null) }

    var wallpaperDest by remember { mutableStateOf(WallpaperDestination.HOME_SCREEN) }
    var wallpaperBusy by remember { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            wallpaperBusy = true
            scope.launch {
                val outcome = wallpaperHelper.applyWallpaperFromUri(uri, wallpaperDest)
                statusBanner = outcome.message
                statusIsError = !outcome.success
                wallpaperBusy = false
                viewModel.refreshAllStates(showLoading = false)
            }
        }
    }

    fun scanApps() {
        scope.launch {
            isScanning = true
            val result = withContext(Dispatchers.IO) {
                val mgmtReport = adminPolicyManager.inspectDeviceManagement()
                val adminMap = mgmtReport.adminAndManagementApps.associateBy { it.packageName }

                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val launchers = runCatching {
                    pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        .mapNotNull { it.activityInfo?.packageName }
                        .toSet()
                }.getOrDefault(emptySet())

                val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS)
                }

                val scanned = installed.map { info ->
                    val pkg = info.packageName
                    val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
                    val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val isUpdatedSys = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    val pkgInfo = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(pkg, 0)
                        }
                    }.getOrNull()

                    val verName = pkgInfo?.versionName ?: "1.0"
                    val verCode = if (pkgInfo != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pkgInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pkgInfo.versionCode.toLong()
                        }
                    } else 0L

                    val adminEntry = adminMap[pkg]
                    val role = adminEntry?.role ?: ManagementPrivilegeRole.STANDARD_APP
                    val isActiveAdmin = adminEntry?.isActiveAdmin == true
                    val isOwner = adminEntry?.isDeviceOwner == true || adminEntry?.isProfileOwner == true
                    val isBlockedByPolicy = adminEntry?.isUninstallBlockedByPolicy == true || mgmtReport.disallowUninstallApps
                    val isHalo = adminEntry?.isHaloRelated == true ||
                        label.contains("halo", ignoreCase = true) ||
                        pkg.contains("halo", ignoreCase = true)

                    val safetyTier = operationManager.classifySafetyTier(
                        packageName = pkg,
                        isSystemApp = isSys,
                        isActiveAdminOrOwner = isActiveAdmin || isOwner,
                        activeLauncherPackages = launchers
                    )

                    val catLabel = when {
                        isOwner -> "Device / Profile Owner"
                        isActiveAdmin -> "Active Device Admin"
                        isHalo -> "Halo Suite"
                        !isSys -> "User Installed"
                        isUpdatedSys -> "Updated System"
                        pkg.startsWith("com.google.") -> "Google System"
                        pkg.startsWith("com.android.") || pkg == "android" -> "Android Core"
                        else -> "OEM / System"
                    }

                    val canDirectUninstall = AdminPolicyManager.canLaunchDirectUninstall(
                        role = role,
                        isSystemApp = isSys,
                        isUpdatedSystemApp = isUpdatedSys,
                        isUninstallBlockedByPolicy = isBlockedByPolicy,
                        globalDisallowUninstall = mgmtReport.disallowUninstallApps
                    )

                    val iconBmp = runCatching {
                        drawableToImageBitmap(pm.getApplicationIcon(info), maxPx = 96)
                    }.getOrNull()

                    ScannedAppItem(
                        label = label,
                        packageName = pkg,
                        versionName = verName,
                        versionCode = verCode,
                        isSystem = isSys,
                        isUpdatedSystem = isUpdatedSys,
                        isEnabled = info.enabled,
                        categoryLabel = catLabel,
                        safetyTier = safetyTier,
                        managementRole = role,
                        isActiveAdmin = isActiveAdmin,
                        isDeviceOrProfileOwner = isOwner,
                        isUninstallBlockedByPolicy = isBlockedByPolicy,
                        isHaloRelated = isHalo,
                        canDirectUninstall = canDirectUninstall,
                        iconBitmap = iconBmp
                    )
                }
                launchers to scanned
            }
            activeLauncherPkgs = result.first
            apps.clear()
            apps.addAll(result.second)
            isScanning = false
        }
    }

    LaunchedEffect(Unit) { scanApps() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scanApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredApps = apps
        .filter { app ->
            val matchesCategory = when (selectedCategory) {
                AppCategoryFilter.ALL -> true
                AppCategoryFilter.USER -> !app.isSystem
                AppCategoryFilter.ADMIN_AND_HALO ->
                    app.isActiveAdmin || app.isDeviceOrProfileOwner || app.isHaloRelated ||
                        app.managementRole != ManagementPrivilegeRole.STANDARD_APP
                AppCategoryFilter.SAFE_DEBLOAT ->
                    app.safetyTier == DebloatSafetyTier.SAFE_DEBLOAT_CANDIDATE || !app.isSystem
                AppCategoryFilter.UPDATED_SYSTEM -> app.isUpdatedSystem
                AppCategoryFilter.SYSTEM -> app.isSystem
                AppCategoryFilter.DISABLED -> !app.isEnabled
            }
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
        .sortedWith(
            when (sortOrder) {
                AppSortOrder.NAME -> compareBy { it.label.lowercase() }
                AppSortOrder.PACKAGE -> compareBy { it.packageName.lowercase() }
                AppSortOrder.ROLE_FIRST ->
                    compareBy<ScannedAppItem> { it.managementRole.ordinal }
                        .thenBy { it.isSystem }
                        .thenBy { it.label.lowercase() }
            }
        )

    // Dialog: Disable / Enable Confirmation via Shizuku
    if (pendingToggleApp != null) {
        val target = pendingToggleApp!!
        GlassConfirmDialog(
            title = if (target.isEnabled) "Disable ${target.label}?" else "Re-enable ${target.label}?",
            confirmLabel = if (target.isEnabled) "Disable for User 0" else "Re-enable Package",
            onConfirm = {
                pendingToggleApp = null
                scope.launch {
                    val outcome = operationManager.setEnabled(
                        packageName = target.packageName,
                        enabled = !target.isEnabled,
                        activeLauncherPackages = activeLauncherPkgs
                    )
                    statusBanner = outcome.detail
                    statusIsError = !outcome.success
                    viewModel.recordExternalOperation(outcome)
                    scanApps()
                }
            },
            onDismiss = { pendingToggleApp = null },
            accentColor = if (target.isEnabled) GlassPalette.AmberGlow else GlassPalette.EmeraldGlow
        ) {
            Text(
                text = "${target.label} (${target.packageName})",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = target.safetyTier.description)
            Text(
                text = if (target.isEnabled) {
                    "Executes `pm disable-user --user 0 ${target.packageName}` via Shizuku and verifies the disabled state. You can restore this package anytime using the 'Disabled (Restore)' filter."
                } else {
                    "Executes `pm enable ${target.packageName}` via Shizuku to restore full app functionality."
                },
                style = MaterialTheme.typography.bodySmall,
                color = GlassPalette.CyanGlow
            )
        }
    }

    // Dialog: Active Admin / Protected App Uninstall Interception
    if (blockedAdminAppDialog != null) {
        val target = blockedAdminAppDialog!!
        GlassConfirmDialog(
            title = "Protected by Device Policy: ${target.label}",
            confirmLabel = "Open Admin & Halo Inspector",
            onConfirm = {
                blockedAdminAppDialog = null
                onNavigateToPrivacy()
            },
            onDismiss = { blockedAdminAppDialog = null },
            accentColor = GlassPalette.AmberGlow
        ) {
            Text(
                text = when {
                    target.isDeviceOrProfileOwner ->
                        "${target.label} (${target.packageName}) is registered as Device Owner or Profile Owner. Android blocks direct uninstallation ('Can't uninstall active device admin app')."
                    target.isActiveAdmin ->
                        "${target.label} is an Active Device Administrator. Android requires deactivating its administrator receiver in Settings before it can be uninstalled."
                    target.isUninstallBlockedByPolicy ->
                        "Android reports that an administrator blocks uninstalling ${target.label} ('App is required by the device administrator'). You must first deactivate or unenroll the controlling administrator."
                    else ->
                        "${target.label} is a read-only system package."
                },
                color = Color.White
            )
            GlassSecondaryButton(
                text = "Open Android Device Admin Settings",
                onClick = {
                    blockedAdminAppDialog = null
                    adminPolicyManager.openDeviceAdminSettings()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Dialog: App Details Inspector
    if (selectedDetailApp != null) {
        val app = selectedDetailApp!!
        GlassConfirmDialog(
            title = app.label,
            confirmLabel = "Open System App Info",
            onConfirm = {
                val pkg = app.packageName
                selectedDetailApp = null
                adminPolicyManager.openAppDetails(pkg)
            },
            onDismiss = { selectedDetailApp = null },
            accentColor = GlassPalette.CyanGlow
        ) {
            KeyValueRow("Package Name", app.packageName)
            KeyValueRow("Version", "${app.versionName} (Code ${app.versionCode})")
            KeyValueRow("Install Type", app.categoryLabel)
            KeyValueRow("Current State", if (app.isEnabled) "Enabled" else "Disabled for User 0")
            KeyValueRow("Management Role", app.managementRole.badgeLabel)
            KeyValueRow("Debloat Safety Tier", app.safetyTier.badgeLabel)
            Text(
                text = app.safetyTier.description,
                style = MaterialTheme.typography.bodySmall,
                color = GlassPalette.TextSecondary
            )
        }
    }

    GlassScreenScaffold(
        title = "App Manager, Debloater & Wallpaper",
        subtitle = "${apps.size} apps scanned • ${apps.count { !it.isEnabled }} disabled",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    sortOrder = when (sortOrder) {
                        AppSortOrder.ROLE_FIRST -> AppSortOrder.NAME
                        AppSortOrder.NAME -> AppSortOrder.PACKAGE
                        AppSortOrder.PACKAGE -> AppSortOrder.ROLE_FIRST
                    }
                }
            ) {
                Icon(Icons.Outlined.SortByAlpha, contentDescription = "Cycle Sort Order", tint = GlassPalette.VioletGlow)
            }
            IconButton(onClick = { scanApps() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Rescan Apps", tint = GlassPalette.CyanGlow)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Banner
            statusBanner?.let { msg ->
                item {
                    val accent = if (statusIsError) GlassPalette.RoseGlow else GlassPalette.EmeraldGlow
                    GlassSurfaceCard(accentGlow = accent, contentPadding = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { statusBanner = null }) {
                                Text("OK", color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Wallpaper Studio Card
            item {
                val wp = uiState.wallpaperPreflight ?: wallpaperHelper.checkEligibility()
                ControlCard(
                    title = "Wallpaper Studio (Memory-Safe Decoder)",
                    subtitle = wp.statusHeadline,
                    icon = Icons.Outlined.Image,
                    badgeText = if (wp.canApplyWallpaper) "READY" else "LOCKED",
                    badgeColor = if (wp.canApplyWallpaper) GlassPalette.CyanGlow else GlassPalette.RoseGlow
                ) {
                    Text(
                        text = wp.statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WallpaperDestination.entries.forEach { dest ->
                            val selected = wallpaperDest == dest
                            FilterChip(
                                selected = selected,
                                onClick = { wallpaperDest = dest },
                                label = { Text(dest.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GlassPalette.CyanGlow.copy(alpha = 0.22f),
                                    selectedLabelColor = GlassPalette.CyanGlow
                                )
                            )
                        }
                    }
                    GlassPrimaryButton(
                        text = if (wallpaperBusy) "Decoding & Applying..." else "Choose Image & Apply to ${wallpaperDest.label}",
                        onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                        enabled = wp.canApplyWallpaper && !wallpaperBusy,
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = GlassPalette.CyanGlow,
                        icon = Icons.Outlined.Image
                    )
                }
            }

            // Search & Filter Card
            item {
                GlassSurfaceCard(accentGlow = GlassPalette.VioletGlow, contentPadding = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Apps, contentDescription = null, tint = GlassPalette.CyanGlow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Showing ${filteredApps.size} of ${apps.size} packages",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        StatusPill(text = "Sort: ${sortOrder.label}", accentColor = GlassPalette.VioletGlow)
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GlassPalette.CyanGlow) },
                        label = { Text("Search by app name or package ID") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlassPalette.CyanGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppCategoryFilter.entries.forEach { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GlassPalette.VioletGlow.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) GlassPalette.VioletGlow else Color.White.copy(alpha = 0.16f)
                                )
                            )
                        }
                    }
                }
            }

            // App Inventory Items
            items(filteredApps, key = { it.packageName }) { app ->
                val tierColor = when (app.safetyTier) {
                    DebloatSafetyTier.CRITICAL_PROTECTED -> GlassPalette.RoseGlow
                    DebloatSafetyTier.ADMIN_OR_MDM_PROTECTED -> GlassPalette.AmberGlow
                    DebloatSafetyTier.SAFE_DEBLOAT_CANDIDATE -> GlassPalette.EmeraldGlow
                    DebloatSafetyTier.CAUTION_SYSTEM -> GlassPalette.VioletGlow
                    DebloatSafetyTier.USER_REMOVABLE -> GlassPalette.CyanGlow
                }

                GlassSurfaceCard(
                    accentGlow = tierColor,
                    cornerRadius = 18.dp,
                    contentPadding = 14.dp,
                    onClick = { selectedDetailApp = app }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App Icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        ) {
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(38.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Android,
                                    contentDescription = null,
                                    tint = tierColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPill(text = app.safetyTier.badgeLabel, accentColor = tierColor)
                            }
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassPalette.CyanGlow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${app.categoryLabel} • v${app.versionName} • ${if (app.isEnabled) "Enabled" else "DISABLED"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (app.isEnabled) GlassPalette.TextSecondary else GlassPalette.AmberGlow
                            )
                        }
                    }

                    // Action Buttons Row
                    val isCriticallyProtected = app.safetyTier == DebloatSafetyTier.CRITICAL_PROTECTED
                    val canToggleEnable = commandExecutor.isReady() &&
                        (!isCriticallyProtected || !app.isEnabled) &&
                        !app.isDeviceOrProfileOwner

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassSecondaryButton(
                            text = "Details",
                            onClick = { selectedDetailApp = app },
                            icon = Icons.Outlined.Info
                        )

                        // Disable / Re-enable button (always available to re-enable if disabled; requires Shizuku)
                        GlassSecondaryButton(
                            text = if (app.isEnabled) "Disable" else "Restore (Enable)",
                            onClick = { pendingToggleApp = app },
                            enabled = canToggleEnable,
                            modifier = Modifier.weight(1f)
                        )

                        // Uninstall OR Admin Deactivation workflow button
                        if (app.isActiveAdmin || app.isDeviceOrProfileOwner || (app.isUninstallBlockedByPolicy && !app.isSystem)) {
                            GlassPrimaryButton(
                                text = if (app.isActiveAdmin && !app.isDeviceOrProfileOwner) "Deactivate Admin" else "Policy Locked",
                                onClick = { blockedAdminAppDialog = app },
                                modifier = Modifier.weight(1f),
                                accentColor = GlassPalette.AmberGlow,
                                icon = Icons.Outlined.Security
                            )
                        } else if (!app.isSystem || app.isUpdatedSystem) {
                            GlassPrimaryButton(
                                text = if (app.isUpdatedSystem) "Remove Updates" else "Uninstall",
                                onClick = {
                                    val uninstallIntent = Intent(
                                        Intent.ACTION_DELETE,
                                        Uri.fromParts("package", app.packageName, null)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(uninstallIntent) }
                                        .onSuccess {
                                            statusBanner = if (app.isUpdatedSystem) {
                                                "Opened Android's 'Uninstall updates' confirmation for ${app.label}."
                                            } else {
                                                "Opened Android's uninstall confirmation for ${app.label}."
                                            }
                                            statusIsError = false
                                        }
                                        .onFailure { err ->
                                            statusBanner = "Couldn't launch uninstall for ${app.label}: ${err.localizedMessage ?: err.javaClass.simpleName}"
                                            statusIsError = true
                                        }
                                },
                                enabled = app.canDirectUninstall,
                                modifier = Modifier.weight(1f),
                                accentColor = GlassPalette.RoseGlow,
                                icon = Icons.Outlined.DeleteOutline
                            )
                        }
                    }

                    // Transparent status footnote
                    val footnote = when {
                        app.isDeviceOrProfileOwner ->
                            "Protected as Device/Profile Owner. Tap 'Policy Locked' for official unenrollment steps."
                        app.isActiveAdmin ->
                            "Active Device Administrator — tap 'Deactivate Admin' first to unlock uninstallation."
                        app.isUninstallBlockedByPolicy && !app.isSystem ->
                            "Administrator enforces setUninstallBlocked / DISALLOW_UNINSTALL_APPS on this app."
                        isCriticallyProtected ->
                            "Protected by NoobFree's critical system safety catalogue (never disabled)."
                        !commandExecutor.isReady() && app.isSystem ->
                            "Connect Shizuku to disable/enable eligible system bloatware for User 0."
                        else -> null
                    }
                    if (footnote != null) {
                        Text(
                            text = footnote,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun drawableToImageBitmap(drawable: Drawable, maxPx: Int = 96): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val w = drawable.intrinsicWidth.coerceIn(1, maxPx)
    val h = drawable.intrinsicHeight.coerceIn(1, maxPx)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
