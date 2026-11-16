package com.noobfree.android.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.noobfree.android.data.DeviceRepository
import com.noobfree.android.data.OperationLogEntry
import com.noobfree.android.data.ShizukuRepository
import com.noobfree.android.system.BackgroundProcessLimit
import com.noobfree.android.system.WallpaperDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val shizukuRepository: ShizukuRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        viewModelScope.launch {
            shizukuRepository.status.collect { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
                refreshAllStates(showLoading = false)
            }
        }
        refreshAllStates(showLoading = true)
    }

    fun refreshAllStates(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true) }
            }
            shizukuRepository.refreshStatus()
            val devState = deviceRepository.readDeveloperOptionsState()
            val animState = deviceRepository.readAnimationState()
            val debugState = deviceRepository.readDebugState()
            val bgLimit = deviceRepository.readBackgroundProcessLimit()
            val refreshInfo = deviceRepository.readRefreshRateInfo()
            val hwInfo = deviceRepository.readDeviceHardwareInfo()
            val mgmtReport = withContext(Dispatchers.IO) {
                deviceRepository.readDeviceManagementReport()
            }
            val uid = _uiState.value.shizukuStatus.serviceUid.takeIf { it >= 0 }
            val caps = deviceRepository.readCapabilities(uid)
            val wpPreflight = deviceRepository.readWallpaperPreflight()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    developerOptions = devState,
                    animationState = animState,
                    debugState = debugState,
                    backgroundProcessLimit = bgLimit,
                    refreshRateInfo = refreshInfo,
                    deviceHardwareInfo = hwInfo,
                    managementReport = mgmtReport,
                    capabilities = caps,
                    wallpaperPreflight = wpPreflight
                )
            }
        }
    }

    fun onConnectOrAuthorizeShizuku() {
        shizukuRepository.requestPermissionOrConnect()
        appendLog(
            category = "Shizuku",
            action = "Connect / Authorize",
            command = null,
            success = true,
            detail = "Triggered Shizuku permission or service flow (${_uiState.value.shizukuStatus.statusTitle})"
        )
    }

    fun enableDeveloperOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, bannerMessage = null) }
            val outcome = deviceRepository.enableDeveloperOptions()
            _uiState.update {
                it.copy(
                    isExecutingAction = false,
                    developerOptions = outcome.data,
                    bannerMessage = outcome.message,
                    bannerIsError = !outcome.success
                )
            }
            appendLog(
                category = "Developer Options",
                action = "Enable Developer Options",
                command = outcome.executedCommand,
                success = outcome.success,
                detail = outcome.message
            )
        }
    }

    fun disableDeveloperOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, bannerMessage = null) }
            val outcome = deviceRepository.disableDeveloperOptions()
            _uiState.update {
                it.copy(
                    isExecutingAction = false,
                    developerOptions = outcome.data,
                    bannerMessage = outcome.message,
                    bannerIsError = !outcome.success
                )
            }
            appendLog(
                category = "Developer Options",
                action = "Disable Developer Options",
                command = outcome.executedCommand,
                success = outcome.success,
                detail = outcome.message
            )
        }
    }

    fun setAnimationScale(scale: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, bannerMessage = null) }
            val outcome = deviceRepository.applyAnimationScale(scale)
            _uiState.update {
                it.copy(
                    isExecutingAction = false,
                    animationState = outcome.data,
                    bannerMessage = outcome.message,
                    bannerIsError = !outcome.success
                )
            }
            appendLog(
                category = "Animations",
                action = "Set all scales to ${scale}x",
                command = outcome.executedCommand,
                success = outcome.success,
                detail = outcome.message
            )
        }
    }

    fun applyWallpaper(uri: Uri, destination: WallpaperDestination) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, bannerMessage = null) }
            val outcome = deviceRepository.applyWallpaper(uri, destination)
            val wpPreflight = deviceRepository.readWallpaperPreflight()
            _uiState.update {
                it.copy(
                    isExecutingAction = false,
                    wallpaperPreflight = wpPreflight,
                    bannerMessage = outcome.message,
                    bannerIsError = !outcome.success
                )
            }
            appendLog(
                category = "Wallpaper",
                action = "Apply ${destination.label}",
                command = null,
                success = outcome.success,
                detail = outcome.message
            )
        }
    }

    fun requestBackgroundProcessLimitChange(limit: BackgroundProcessLimit) {
        _uiState.update { it.copy(pendingBackgroundLimitConfirmation = limit) }
    }

    fun dismissBackgroundProcessLimitDialog() {
        _uiState.update { it.copy(pendingBackgroundLimitConfirmation = null) }
    }

    fun confirmBackgroundProcessLimitChange() {
        val target = _uiState.value.pendingBackgroundLimitConfirmation ?: return
        _uiState.update { it.copy(pendingBackgroundLimitConfirmation = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, bannerMessage = null) }
            val outcome = deviceRepository.setBackgroundProcessLimit(target)
            _uiState.update {
                it.copy(
                    isExecutingAction = false,
                    backgroundProcessLimit = outcome.data,
                    bannerMessage = outcome.message,
                    bannerIsError = !outcome.success
                )
            }
            appendLog(
                category = "Background Processes",
                action = "Set limit: ${target.label}",
                command = outcome.executedCommand,
                success = outcome.success,
                detail = outcome.message
            )
        }
    }

    fun showBanner(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(bannerMessage = message, bannerIsError = isError) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun openDeveloperOptionsSettings() {
        val opened = deviceRepository.openDeveloperOptionsSettings()
        if (!opened) {
            _uiState.update {
                it.copy(
                    bannerMessage = "Unable to open Developer Options directly. Use the 7-Tap About Tablet guide first.",
                    bannerIsError = true
                )
            }
        }
    }

    fun openWirelessDebuggingSettings() {
        deviceRepository.openWirelessDebuggingOrDevSettings()
    }

    fun openDisplaySettings() {
        deviceRepository.openDisplaySettings()
    }

    fun openDeviceInfoSettings() {
        deviceRepository.openDeviceInfoSettings()
        appendLog(
            category = "Developer Options",
            action = "Open About Tablet (7-Tap Flow)",
            command = "android.settings.DEVICE_INFO_SETTINGS",
            success = true,
            detail = "Opened Android Device Info settings for manual 7-tap Build Number activation."
        )
    }

    fun openDeviceAdminSettings() {
        deviceRepository.openDeviceAdminSettings()
        appendLog(
            category = "Device Policy",
            action = "Open Device Admin Settings",
            command = "android.settings.DEVICE_ADMIN_SETTINGS",
            success = true,
            detail = "Opened Android Device Administrators screen."
        )
    }

    fun openSystemSettings() {
        deviceRepository.openSystemSettings()
    }

    fun openAppDetailsSettings() {
        deviceRepository.openAppDetailsSettings()
    }

    fun openShizukuApp() {
        shizukuRepository.openShizukuApp()
    }

    fun recordExternalOperation(entry: OperationLogEntry) {
        _uiState.update { state ->
            state.copy(operationHistory = (listOf(entry) + state.operationHistory).take(50))
        }
    }

    private fun appendLog(
        category: String,
        action: String,
        command: String?,
        success: Boolean,
        detail: String
    ) {
        val entry = OperationLogEntry(
            timestamp = timeFormatter.format(Date()),
            category = category,
            action = action,
            command = command,
            success = success,
            detail = detail
        )
        _uiState.update { state ->
            state.copy(operationHistory = (listOf(entry) + state.operationHistory).take(50))
        }
    }

    class Factory(
        private val shizukuRepository: ShizukuRepository,
        private val deviceRepository: DeviceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(shizukuRepository, deviceRepository) as T
        }
    }
}
