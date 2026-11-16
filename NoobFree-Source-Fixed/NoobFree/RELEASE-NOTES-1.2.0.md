# NoobFree v1.2.0 (Build 3) — Release Notes

## Highlights of v1.2.0 — Complete Glassmorphism & Device Policy Overhaul

1. **Full Glassmorphism UI Redesign Across All 7 Screens**:
   - Replaced opaque Material `Scaffold` and `TopAppBar` backgrounds with `GlassBackgroundCanvas` (deep cosmic navy/indigo/purple gradient with radial cyan `#00E5FF` and violet `#A855F7` light orbs), `GlassScreenScaffold`, `GlassSurfaceCard` (frosted translucent surfaces with 1.dp specular gradient borders), and `GlassBottomNavigationBar`.

2. **Complete Device Policy, Active Admin & Dynamic Halo Suite Inspector (`AdminPolicyManager.kt`)**:
   - Inspects all installed applications and declared `BIND_DEVICE_ADMIN` broadcast receivers using `DevicePolicyManager`, `UserManager`, and `PackageManager`.
   - Accurately distinguishes **Device Owner**, **Profile Owner**, **Active Ordinary Device Administrator**, **Inactive Admin-Capable App**, and **Management Companion App**.
   - Dynamically discovers **Google Android Device Policy** (`com.google.android.apps.work.clouddpc`) and **Halo** applications (`Halo Agent`, `Halo ThreatX`, `Halo Remote`, `Halo Forge`, etc.), showing their real package IDs, admin receiver components, and whether uninstallation is blocked by an active admin role or `setUninstallBlocked()`.
   - Implements the **3-Step Administrator Removal Workflow**: *(1) Deactivate in Admin Settings -> (2) Recheck Status -> (3) Uninstall once inactive*.

3. **NoobFree Optional Device Administrator & Two-Path Factory Reset**:
   - Upgraded `NoobFreeAdminReceiver` and `noobfree_device_admin.xml` (`<force-lock />` and `<wipe-data />`) with in-app activation, immediate screen lock (`lockNow()`), self-deactivation (`removeActiveAdmin()`), and preflight-checked `wipeData(0)` / `wipeDevice(0)` handling `SecurityException` and `DISALLOW_FACTORY_RESET`.
   - Added a comprehensive **Recovery-Mode Factory Reset Guide** for the **Acer One 8 T4-82L-G2** with explicit warnings on Android Zero-Touch / enterprise re-enrollment.

4. **Shizuku-Independent 7-Tap Developer Options Workflow (`DeveloperManager.kt`, `DeveloperScreen.kt`)**:
   - Made the **5-Step Guided 7-Tap Workflow** (`Settings.ACTION_DEVICE_INFO_SETTINGS` + automatic `ON_RESUME` verification) the primary path on both the Home Dashboard and Developer Options screen—no Shizuku required.
   - Added upfront detection for `UserManager.DISALLOW_DEBUGGING_FEATURES` and clear distinction between unlocking the Developer Options menu and enabling USB/Wireless Debugging.

5. **Reliable App Manager & Safe System Debloat Engine (`AppManagementScreen.kt`, `PackageOperationManager.kt`)**:
   - Added app icons, version names/codes, real-time search, 7 category filters (*All*, *User*, *Admin/Halo*, *Safe Debloat*, *Updated System*, *System*, *Disabled (Restore)*), and 3 sort modes.
   - Intercepts uninstall attempts on active administrators to guide deactivation first rather than failing with a generic error.
   - Enforces a strict **Critical System Safety Denylist** while allowing safe bloatware disabling (`pm disable-user --user 0`) and 1-tap restoration (`pm enable`) via Shizuku.

6. **Memory-Safe Wallpaper Studio (`WallpaperHelper.kt`)**:
   - Rebuilt wallpaper application with `SET_WALLPAPER` and `DISALLOW_SET_WALLPAPER` preflight diagnostics, persistent URI read grants, two-pass `inSampleSize` bitmap downsampling with `RGB_565` low-RAM fallback, Home/Lock/Both target selection, and wallpaper ID (`> 0`) verification.

7. **Resolved APK Upgrade Package Conflicts (`app/build.gradle.kts`, `.github/workflows/android-ci.yml`)**:
   - Unified `applicationId = "com.noobfree.android"`, bumped `versionCode = 3` (`1.2.0`), added persistent keystore support via `keystore.properties` or GitHub Actions secrets, and configured CI to output signed, `apksigner`-verified Debug and Release APKs.
