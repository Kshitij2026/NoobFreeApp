package com.noobfree.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun DeviceInfoScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val hw = uiState.deviceHardwareInfo
    val rr = uiState.refreshRateInfo

    GlassScreenScaffold(
        title = "Device, Display & GPU Telemetry",
        subtitle = hw?.marketName ?: "Hardware & Runtime Inspector",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (hw != null) {
                item {
                    ControlCard(
                        title = hw.marketName,
                        subtitle = "${hw.osSkinName} • Runtime Android ${hw.androidVersion}",
                        icon = Icons.Outlined.PhoneAndroid,
                        badgeText = "API ${hw.sdkInt}",
                        badgeColor = GlassPalette.CyanGlow
                    ) {
                        KeyValueRow("Manufacturer / Brand", "${hw.manufacturer} / ${hw.brand}")
                        KeyValueRow("Model / Codename", "${hw.model} (${hw.deviceCodename})")
                        KeyValueRow("Runtime Android / SDK", "Android ${hw.androidVersion} (API ${hw.sdkInt})")
                        KeyValueRow("Security Patch", hw.securityPatch)
                        KeyValueRow("Build ID", hw.buildId)
                    }
                }

                item {
                    ControlCard(
                        title = "SoC, Memory & Display Panel",
                        subtitle = hw.cpuHardware,
                        icon = Icons.Outlined.DeveloperBoard,
                        badgeText = "${hw.cpuCoreCount} CORES",
                        badgeColor = GlassPalette.VioletGlow
                    ) {
                        KeyValueRow("SoC / CPU Hardware", hw.cpuHardware)
                        KeyValueRow("CPU Cores", "${hw.cpuCoreCount} logical cores")
                        KeyValueRow("Supported ABIs", hw.supportedAbis)
                        KeyValueRow("Total RAM", hw.totalRamFormatted)
                        KeyValueRow("Available RAM", hw.availableRamFormatted)
                        KeyValueRow("Physical Resolution", hw.screenResolution)
                        KeyValueRow("Screen Density", "${hw.screenDensityDpi} dpi")
                    }
                }
            }

            item {
                ControlCard(
                    title = "Display Refresh Modes",
                    subtitle = "Active: ${rr.activeRefreshRateHz.roundToInt()} Hz (Peak: ${rr.peakSupportedHz.roundToInt()} Hz)",
                    icon = Icons.Outlined.Speed,
                    badgeText = "${rr.activeRefreshRateHz.roundToInt()} HZ",
                    badgeColor = GlassPalette.EmeraldGlow
                ) {
                    rr.supportedModes.forEach { mode ->
                        KeyValueRow(
                            label = "Mode #${mode.modeId} (${mode.width}×${mode.height})",
                            value = "${mode.refreshRateHz.roundToInt()} Hz${if (mode.isActive) " [Active]" else ""}",
                            valueColor = if (mode.isActive) GlassPalette.CyanGlow else GlassPalette.TextPrimary
                        )
                    }
                    Text(
                        text = rr.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                }
            }

            item {
                ControlCard(
                    title = "GPU Rendering & HWUI Profiling",
                    subtitle = "Hardware-accelerated UI diagnostics",
                    icon = Icons.Outlined.GraphicEq,
                    badgeText = "HWUI",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    Text(
                        text = "Android exposes Profile HWUI Rendering, Show GPU View Updates, and Force 4x MSAA inside system Developer Options. NoobFree provides direct navigation rather than modifying restricted SurfaceFlinger debug flags.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    GlassPrimaryButton(
                        text = "Open Developer Options (HWUI Rendering)",
                        onClick = { viewModel.openDeveloperOptionsSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = GlassPalette.CyanGlow
                    )
                }
            }
        }
    }
}
