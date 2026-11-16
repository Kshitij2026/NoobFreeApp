# NoobFree v1.2.0 — Android Device Policy, App Debloat & Developer Control Center

NoobFree is an offline-first Android utility engineered in **Kotlin** and **Jetpack Compose (Material 3)** for inspecting device administration policies, managing installed applications, safely debloating eligible preloads, applying memory-safe wallpapers, and unlocking Developer Options without requiring root or Shizuku.

## Key Capabilities in v1.2.0 (Build 3)

1. **Complete Device Management & Administrator Inspector (`AdminPolicyManager.kt`, `PrivacyInspectorScreen.kt`)**:
   - Inspects [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager) and [UserManager](https://developer.android.com/reference/android/app/admin/DevicePolicyIdentifiers) at runtime to distinguish between **Device Owner**, **Profile Owner**, **Active Ordinary Device Administrator**, **Inactive Admin-Capable Receiver**, and **Management Companion Apps**.
   - Dynamically discovers installed **Halo** packages (`Halo Agent`, `Halo ThreatX`, `Halo Remote`, `Halo Forge`, etc.) and **Google Android Device Policy** (`com.google.android.apps.work.clouddpc`) without hardcoded package guesses, inspecting each app's actual `BIND_DEVICE_ADMIN` receiver, `isDeviceOwnerApp`, `isProfileOwnerApp`, and `isUninstallBlocked` status.
   - Guides users through the **3-Step Administrator Removal Workflow**:
     1. Open Android's Device Administrator settings (`android.settings.DEVICE_ADMIN_SETTINGS`) to deactivate an ordinary active admin.
     2. Recheck live status in NoobFree (automatically refreshed on activity `ON_RESUME`).
     3. Launch Android's system uninstall dialog (`Intent.ACTION_DELETE`) once the admin role is inactive.
   - Clearly explains when an app is locked by a **Device Owner** or `setUninstallBlocked()` policy (`"App is required by the device administrator"`) and provides the legitimate unenrollment route.

2. **NoobFree Device Administrator & Two-Path Factory Reset (`NoobFreeAdminReceiver.kt`, `noobfree_device_admin.xml`)**:
   - Requests only `<force-lock />` and `<wipe-data />` policies with explicit user consent via `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`.
   - Supports immediate screen lock (`dpm.lockNow()`), self-deactivation (`dpm.removeActiveAdmin()`), and **In-App Factory Reset** (`dpm.wipeData(0)` / `dpm.wipeDevice(0)`) with preflight checks for `UserManager.DISALLOW_FACTORY_RESET` and Android 14+ (API 34+) platform restrictions.
   - Includes a dedicated **Recovery-Mode Factory Reset Guide** tailored for the **Acer One 8 T4-82L-G2** ([Acer T4-82L-G2 Manual](https://manuals.plus/acer/one-8-t4-82l-g2-4g-calling-tablet-manual)) with explicit warnings regarding Android Zero-Touch / cloud MDM automatic re-enrollment.

3. **Shizuku-Independent Developer Options Activation (`DeveloperManager.kt`, `DeveloperScreen.kt`)**:
   - Detects `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`, `Settings.Global.ADB_ENABLED`, `adb_wifi_enabled`, and `UserManager.DISALLOW_DEBUGGING_FEATURES` without requiring Shizuku.
   - Provides a **5-Step Guided 7-Tap Workflow** with a direct shortcut to **About tablet** (`Settings.ACTION_DEVICE_INFO_SETTINGS`) and **Developer options** (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`), automatically verifying activation when the user returns to NoobFree.
   - Retains an optional 1-tap Shizuku shell toggle (`settings put global development_settings_enabled 1`) when [Shizuku](https://github.com/RikkaApps/Shizuku-API) is connected.

4. **Reliable App Manager & Safe Debloat Catalogue (`AppManagementScreen.kt`, `PackageOperationManager.kt`)**:
   - Displays app icons, display names, package IDs, version names/codes, install classification, management role badges, and safety tiers.
   - Provides real-time search, category filter chips (*All Apps*, *User Installed*, *Admin / Halo / MDM*, *Safe Debloat*, *Updated System*, *All System*, *Disabled (Restore)*), and multi-mode sorting (*Admin/User First*, *Name A–Z*, *Package ID*).
   - Enforces a strict **Critical System Safety Denylist** protecting `android`, `com.android.systemui`, `com.android.settings`, `com.google.android.packageinstaller`, `com.google.android.gms`, active home launchers, telephony/emergency services, and `com.noobfree.android`.
   - Allows disabling non-critical preloaded bloatware for User 0 (`pm disable-user --user 0 <pkg>`) via Shizuku with a permanent **1-Tap Restore (`pm enable <pkg>`)** path under the *Disabled (Restore)* filter.

5. **Memory-Safe Wallpaper Studio (`WallpaperHelper.kt`)**:
   - Validates `SET_WALLPAPER` permission, `UserManager.DISALLOW_SET_WALLPAPER`, `WallpaperManager.isWallpaperSupported()`, and `WallpaperManager.isSetWallpaperAllowed()`.
   - Safely decodes high-resolution images using two-pass `BitmapFactory.Options` (`inJustDecodeBounds = true` followed by power-of-two `inSampleSize` downsampling and `ARGB_8888` -> `RGB_565` low-memory fallback) to prevent `OutOfMemoryError` on 2GB/3GB tablets ([Acer One 8 T4-82L Specs](https://www.bajajfinserv.in/acer-one-8-t4-82l-price-in-india)).
   - Supports applying wallpapers to **Home Screen** (`FLAG_SYSTEM`), **Lock Screen** (`FLAG_LOCK`), or **Both** (`FLAG_SYSTEM or FLAG_LOCK`) and verifies the returned wallpaper ID (`> 0`).

6. **Complete Glassmorphism UI Redesign (`Theme.kt`, `Components.kt`, `MainActivity.kt`)**:
   - Uses a full-bleed cosmic navy/indigo/purple canvas (`#060B19` -> `#0F172A` -> `#1A103C`) with ambient glowing cyan (`#00E5FF`) and violet (`#A855F7`) radial light orbs.
   - All screens use transparent `GlassScreenScaffold` containers, translucent frosted `GlassSurfaceCard` components with 1.dp specular gradient borders, and a frosted `GlassBottomNavigationBar`.

## Building & Signing the Project

### 1. Automatic Build via GitHub Actions (Recommended)
Push this project to a GitHub repository. The included workflow (`.github/workflows/android-ci.yml`) automatically runs:
- `gradle testDebugUnitTest --stacktrace` (Unit test suite)
- `gradle lintDebug --stacktrace` (Android Lint verification)
- `gradle assembleDebug --stacktrace` -> uploads `NoobFree-1.2.0-debug.apk`
- `gradle assembleRelease --stacktrace` -> uploads `NoobFree-1.2.0-release.apk`
- `apksigner verify --verbose` on both APK outputs

### 2. Preserving Signing Keys Across Builds (Preventing Package Conflicts)
Android rejects APK updates with `"App not installed as package conflicts with an existing package"` (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) if the new APK is signed with a different certificate than the currently installed version.
- NoobFree v1.2.0 uses a single unified `applicationId = "com.noobfree.android"` (`versionCode = 3`, `versionName = "1.2.0"`).
- To keep your own private signing certificate across all GitHub Actions builds, generate a keystore once:
  ```bash
  keytool -genkeypair -v -keystore noobfree-release.jks -storetype PKCS12 -alias noobfree_key -keyalg RSA -keysize 2048 -validity 10000
  base64 -w 0 noobfree-release.jks > noobfree-release.jks.base64
  ```
- Add these 4 Repository Secrets in GitHub (`Settings -> Secrets and variables -> Actions`):
  - `NOOBFREE_KEYSTORE_BASE64`
  - `NOOBFREE_KEYSTORE_PASSWORD`
  - `NOOBFREE_KEY_ALIAS`
  - `NOOBFREE_KEY_PASSWORD`
- For local builds in Android Studio, place `keystore.properties` in the project root (ignored by `.gitignore`):
  ```properties
  storeFile=noobfree-release.jks
  storePassword=YOUR_PASSWORD
  keyAlias=noobfree_key
  keyPassword=YOUR_PASSWORD
  ```
- **Note when upgrading from an older v1.0/v1.1 build signed with an ephemeral key**: Uninstall the old build once before installing v1.2.0; all subsequent v1.2.0+ builds signed with the same keystore will upgrade in-place without uninstalling.

## Project Documentation Index
- `TECHNICAL-AUDIT.md` — Stage 1 source code and architecture audit of v1.1.0 and root causes fixed in v1.2.0.
- `FEATURE-CAPABILITY-MATRIX.md` — Complete feature inventory labeled by privilege requirement and test status.
- `ANDROID-PRIVILEGE-LIMITATIONS.md` — Technical reference on Ordinary App vs. Device Admin vs. Shizuku vs. Device Owner and Zero-Touch enrollment.
- `TEST-REPORT.md` — Static, unit, build, and device testing report.
- `RELEASE-NOTES-1.2.0.md` — Summary of changes in NoobFree v1.2.0.
- `screenshots/` — High-resolution renders of the redesigned Glassmorphism screens.
