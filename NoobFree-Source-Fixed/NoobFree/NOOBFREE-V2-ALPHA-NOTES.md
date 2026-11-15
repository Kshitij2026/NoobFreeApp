# NoobFree V2 Alpha – implementation notes

This update preserves the existing NoobFree screens and Shizuku integration, and adds an initial App & Restriction Manager screen.

## Added in this alpha
- Installed-app inventory using PackageManager.
- App-name and package-name search.
- System-app visibility filter.
- Basic classification (Android system, Google, manufacturer/system, user-installed).
- Per-app shortcut to Android App Info settings.
- Wallpaper restriction inspection using the visible `DISALLOW_SET_WALLPAPER` user restriction.
- Device Owner / Profile Owner status check for NoobFree itself.
- Wallpaper picker shortcut when no wallpaper restriction is visible.

## Safety and platform limits
- This alpha is read-only apart from opening Android settings screens. It does not silently disable, suspend, or uninstall apps.
- App inventory uses `QUERY_ALL_PACKAGES`. This is suitable for a sideloaded/private test build, but public Google Play distribution requires a valid policy justification and review.
- A normal app cannot see every enterprise policy or override an organization's restrictions.
- The Device Owner / Profile Owner checks report NoobFree's own role; they do not detect whether another DPC owns the device.
- System-app disable/uninstall support is not assumed. The next phase will add explicit Shizuku-backed actions only after eligibility checks, confirmation, and restore guidance.
- The existing Developer Options, Shizuku, animation, background-process, device-info and settings workflows remain in the project.

## Build
The project includes `.github/workflows/android-ci.yml`. Push this project to GitHub and run the workflow to compile/test it and download the debug APK artifact. A local build was not run in this environment because the Android Gradle toolchain is not installed.
