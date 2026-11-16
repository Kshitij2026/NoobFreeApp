# NoobFree V2.1 – Privilege Bridge + Safe Debloat Engine

## Included in this patch
- CapabilityManager reports Shizuku connectivity, NoobFree Device Owner/Profile Owner status, visible wallpaper restriction, and OEM bridge availability (false until a real OEM integration is supplied).
- PackageOperationManager validates package identifiers, blocks a small critical-package denylist, and performs `pm disable-user --user 0` / `pm enable` only through an already-authorized Shizuku shell. It verifies the resulting disabled-package state.
- App Manager adds per-app enable/disable confirmation, user-app uninstall through Android's system uninstall confirmation UI, wallpaper picker launch, and Shizuku status messaging.
- Existing application screens and GitHub workflow are retained.

## Important platform boundaries
- This patch does not bootstrap Shizuku, enable Developer Options, or start Wireless Debugging. Android requires the user/OEM to provide an authorized privilege path first.
- OEM privileged bridge is intentionally reported unavailable. It cannot be implemented generically without an OEM-signed service/API and its authorization contract.
- The Android system uninstall UI is used for user-installed apps. System packages are not silently uninstalled. `pm disable-user` is reversible but may break device features; use only after review.
- Wallpaper picker opens only when the visible restriction check permits it. No administrator policy is bypassed.
- A regular app cannot become Device Owner or Profile Owner by calling these APIs; enrollment/provisioning is required.

## Apply
Copy the patch files into the matching paths under `NoobFree-Source-Fixed/NoobFree/` in the repository. Keep the existing workflow unchanged.
