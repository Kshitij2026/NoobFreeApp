# NoobFree v1.2.0 — Feature Capability Matrix

Every capability in NoobFree is classified below using the required platform status labels:
- **Implemented and tested** (Verified via static analysis, unit test suite, and behavioral harness)
- **Implemented but not device-tested** (Implemented against public Android SDK APIs; requires physical Acer One 8 T4-82L-G2 / Android runtime execution)
- **Requires Device Owner**
- **Requires Shizuku**
- **Requires OEM integration**
- **Blocked by Android policy**
- **Not implemented**

| Feature / Capability | Status Label | Privilege / API Used | Honest Platform Behavior & Notes |
| :--- | :--- | :--- | :--- |
| **Device Hardware, SoC, RAM & Display Refresh Inspection** | Implemented but not device-tested | Standard App (`ActivityManager`, `DisplayManager`, `Build`) | Reads real-time RAM, CPU cores/ABIs, display modes, and runtime Android version/API level ([DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)). |
| **Device Management & Owner Role Detection** | Implemented and tested | Standard App (`DevicePolicyManager.isDeviceOwnerApp`, `isProfileOwnerApp`, `getActiveAdmins`) | Distinguishes Ordinary Device Admin, Device Owner, Profile Owner, Managed Profile, and Companion Apps without guessing. |
| **Dynamic Halo & Google Device Policy Discovery** | Implemented and tested | Standard App (`PackageManager.getInstalledApplications`, `queryBroadcastReceivers`) | Discovers `com.google.android.apps.work.clouddpc`, `Halo Agent`, `Halo ThreatX`, `Halo Remote`, `Halo Forge`, and checks actual admin/owner locks. |
| **Ordinary Device Admin Deactivation & Uninstall Workflow** | Implemented but not device-tested | Standard App (`android.settings.DEVICE_ADMIN_SETTINGS` + `Intent.ACTION_DELETE`) | Guides user to deactivate ordinary admin in Android Settings, rechecks `isAdminActive` on resume, then unlocks `ACTION_DELETE` uninstall. |
| **Forcible Removal of Active Device Owner / Locked MDM App** | Blocked by Android policy | Controlled by `DevicePolicyManager` / Zero-Touch | An ordinary APK cannot strip another app's Device Owner status or override `setUninstallBlocked()`. Must use DPC unenrollment or organization release. |
| **NoobFree Optional Device Administrator (`force-lock`, `wipe-data`)** | Implemented but not device-tested | Legacy `DeviceAdminReceiver` (`ACTION_ADD_DEVICE_ADMIN`) | User-consented activation for immediate screen lock (`lockNow()`) and self-deactivation (`removeActiveAdmin()`). |
| **In-App Factory Reset (`wipeData(0)` / `wipeDevice(0)`)** | Requires Device Owner (on Android 14+) / Implemented | `DevicePolicyManager.wipeData(0)` | Checks `isAdminActive` and `DISALLOW_FACTORY_RESET`. On Android 14+ (API 34+), ordinary legacy admins throw `SecurityException`; falls back to System Reset Settings. |
| **Manual Recovery-Mode Factory Reset Guide** | Implemented and tested | User-guided Hardware Key Instructions | Provides step-by-step recovery reset instructions ([Acer One 8 T4-82L-G2 Manual](https://manuals.plus/acer/one-8-t4-82l-g2-4g-calling-tablet-manual)) and warns about Zero-Touch re-enrollment. |
| **Developer Options Activation (Guided 7-Tap Flow)** | Implemented and tested | Standard App (`Settings.ACTION_DEVICE_INFO_SETTINGS` + `Settings.Global`) | Works without Shizuku. Launches About Tablet, guides 7 taps on Build number, checks `DISALLOW_DEBUGGING_FEATURES`, and auto-verifies on resume. |
| **Silent 0-Tap Developer Options Simulation in Settings** | Blocked by Android policy | Restricted by `WRITE_SECURE_SETTINGS` / Input Sandboxing | Ordinary apps cannot inject taps into Settings or bypass `DISALLOW_DEBUGGING_FEATURES` ([UserManager](https://developer.android.com/reference/android/app/admin/DevicePolicyIdentifiers)). |
| **Developer Options 1-Tap Flag Write (`development_settings_enabled`)** | Requires Shizuku | [Shizuku](https://github.com/RikkaApps/Shizuku-API) (`UID 2000` / `0`) | Writes `settings put global development_settings_enabled 1` and verifies via `settings get global`. |
| **Installed App Inventory, Search, Sort & Safety Classification** | Implemented and tested | Standard App (`QUERY_ALL_PACKAGES`) | Lists user, updated-system, system, admin, and Halo apps with icons, versions, 7 category filters, and 3 sort orders. |
| **User App Uninstall & Updated-System App Rollback** | Implemented but not device-tested | Standard App (`REQUEST_DELETE_PACKAGES`, `Intent.ACTION_DELETE`) | Launches Android's system uninstall confirmation dialog for eligible apps after verifying no active admin lock exists. |
| **System Bloatware Disable (`pm disable-user --user 0`) & Restore (`pm enable`)** | Requires Shizuku | Shizuku UserService + Safety Denylist | Blocks critical OS/Launcher/Play Services packages; allows disabling non-critical bloatware for User 0 with 1-tap restore. |
| **Silent Background Package Uninstallation Without User Prompt** | Requires Device Owner | `PackageInstaller.uninstall()` (Privileged) | Requires Device Owner, Profile Owner, or OEM-signed privileged integration. |
| **OEM Privileged Bridge Integration** | Requires OEM integration | Vendor-signed Priv-App API | Reported as `oemPrivilegedBridgeAvailable = false` unless signed with the OEM platform certificate. |
| **Wallpaper Studio (Home Screen, Lock Screen, Both)** | Implemented and tested | Standard App (`WallpaperManager`, `SET_WALLPAPER`) | Validates `DISALLOW_SET_WALLPAPER`, downsamples large images via `inSampleSize` with `RGB_565` low-RAM fallback, and verifies wallpaper ID `> 0`. |
| **System Animation Scales (`0x`, `0.5x`, `1x`, `1.5x`, `2x`)** | Requires Shizuku | Shizuku UserService (`settings put global`) | Reads scales without Shizuku; writing all 3 global scale keys requires Shizuku authorization. |
| **Background Process Limit Control** | Requires Shizuku | Shizuku UserService (`settings put global`) | Reads and writes `background_process_limit` via allowlisted Shizuku shell command. |
| **Full Glassmorphism UI & Responsive Navigation** | Implemented and tested | Jetpack Compose Material 3 | Cosmic navy/purple mesh gradient, translucent frosted cards, 1.dp specular borders, and high-res rendered screenshots. |
