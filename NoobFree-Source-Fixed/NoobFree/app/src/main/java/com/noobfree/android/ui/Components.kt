package com.noobfree.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noobfree.android.ui.theme.GlassPalette

/**
 * Full-bleed Glassmorphism Cosmic Canvas with ambient cyan & violet glowing light orbs.
 */
@Composable
fun GlassBackgroundCanvas(
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bgBrush = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                GlassPalette.DeepNavyTop,
                GlassPalette.DeepIndigoMid,
                GlassPalette.CosmicPurpleBottom,
                GlassPalette.DeepNavyTop
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE0F2FE),
                Color(0xFFEDE9FE),
                Color(0xFFF1F5F9)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlassPalette.CyanGlow.copy(alpha = if (darkTheme) 0.18f else 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.12f),
                    radius = w * 0.65f
                ),
                center = Offset(w * 0.85f, h * 0.12f),
                radius = w * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlassPalette.VioletGlow.copy(alpha = if (darkTheme) 0.20f else 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.48f),
                    radius = w * 0.70f
                ),
                center = Offset(w * 0.15f, h * 0.48f),
                radius = w * 0.70f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlassPalette.CyanGlow.copy(alpha = if (darkTheme) 0.12f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.75f, h * 0.88f),
                    radius = w * 0.55f
                ),
                center = Offset(w * 0.75f, h * 0.88f),
                radius = w * 0.55f
            )
        }
        content()
    }
}

/**
 * Transparent Glass Scaffold so every screen lets the ambient cosmic gradient shine through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = GlassPalette.CyanGlow
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color(0xCC060B19)
                )
            )
        },
        bottomBar = bottomBar,
        content = content
    )
}

/**
 * Frosted Translucent Glass Card with a dual-stop specular border and inner highlight.
 */
@Composable
fun GlassSurfaceCard(
    modifier: Modifier = Modifier,
    accentGlow: Color = GlassPalette.CyanGlow,
    cornerRadius: Dp = 22.dp,
    contentPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val surfaceGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF16223B).copy(alpha = 0.76f),
            Color(0xFF0E1629).copy(alpha = 0.64f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.30f),
            accentGlow.copy(alpha = 0.38f),
            GlassPalette.VioletGlow.copy(alpha = 0.22f)
        )
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceGradient)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(clickMod)
            .padding(contentPadding)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun ControlCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = GlassPalette.CyanGlow,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurfaceCard(
        modifier = modifier,
        accentGlow = badgeColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    badgeColor.copy(alpha = 0.28f),
                                    GlassPalette.VioletGlow.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = badgeColor.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(text = badgeText, accentColor = badgeColor)
            }
        }
        content()
    }
}

@Composable
fun StatusPill(
    text: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = GlassPalette.CyanGlow,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.22f),
            contentColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) accentColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
        )
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.25f else 0.10f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color = GlassPalette.CyanGlow,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F172A),
        shape = RoundedCornerShape(24.dp),
        titleContentColor = Color.White,
        textContentColor = GlassPalette.TextSecondary,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        },
        confirmButton = {
            GlassPrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                accentColor = accentColor
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GlassPalette.TextSecondary)
            }
        }
    )
}
