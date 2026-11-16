package com.noobfree.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noobfree.android.system.AnimationManager
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.viewmodel.MainUiState
import com.noobfree.android.viewmodel.MainViewModel
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimationScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val anim = uiState.animationState

    GlassScreenScaffold(
        title = "Animation Control Center",
        subtitle = anim.source,
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ControlCard(
                    title = "Active System Animation Scales",
                    subtitle = anim.source,
                    icon = Icons.Outlined.Tune,
                    badgeText = anim.uniformScale?.let { "${AnimationManager.formatScaleForShell(it)}x" } ?: "CUSTOM",
                    badgeColor = GlassPalette.CyanGlow
                ) {
                    KeyValueRow("window_animation_scale", "${anim.windowScale}x")
                    KeyValueRow("transition_animation_scale", "${anim.transitionScale}x")
                    KeyValueRow("animator_duration_scale", "${anim.animatorScale}x")
                }
            }

            item {
                ControlCard(
                    title = "Unified Scale Presets (Shizuku)",
                    subtitle = "Applies and verifies all 3 Settings.Global animation keys",
                    icon = Icons.Outlined.Animation,
                    badgeText = if (uiState.shizukuStatus.isReady) "SHIZUKU READY" else "REQUIRES SHIZUKU",
                    badgeColor = if (uiState.shizukuStatus.isReady) GlassPalette.EmeraldGlow else GlassPalette.AmberGlow
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimationManager.PRESET_SCALES.forEach { preset ->
                            val uniformScale = anim.uniformScale
                            val selected = uniformScale != null && abs(uniformScale - preset) < 0.01f
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setAnimationScale(preset) },
                                label = {
                                    Text("${AnimationManager.formatScaleForShell(preset)}x")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GlassPalette.CyanGlow.copy(alpha = 0.24f),
                                    selectedLabelColor = GlassPalette.CyanGlow
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) GlassPalette.CyanGlow else Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• 0x: Instant transitions (disables window/animator transitions completely).",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.TextSecondary
                        )
                        Text(
                            text = "• 0.5x: Snappy UI response on 2GB/3GB tablets while keeping smooth visual cues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.TextSecondary
                        )
                        Text(
                            text = "• 1x: Standard AOSP / OEM default speed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassPalette.TextSecondary
                        )
                    }
                    GlassPrimaryButton(
                        text = "Restore Default (1x)",
                        onClick = { viewModel.setAnimationScale(1f) },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = GlassPalette.CyanGlow
                    )
                }
            }
        }
    }
}
