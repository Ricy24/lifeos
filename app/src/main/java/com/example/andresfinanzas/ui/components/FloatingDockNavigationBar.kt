package com.example.andresfinanzas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Minimalist Navigation Item representation (Icon-Only).
 */
data class GlassDockItem(
    val route: String,
    val description: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Ultra-Sleek iOS Frosted Glassmorphism Floating Dock.
 * - Icon-Only (No text clutter, minimalist aesthetic).
 * - Multi-layered frosted glass with specular highlight border.
 * - Glowing active capsule with spring micro-interaction.
 * - Floating detached capsule layout.
 */
@Composable
fun FloatingDockNavigationBar(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val navItems = remember {
        listOf(
            GlassDockItem(
                route = "dashboard",
                description = "Inicio",
                unselectedIcon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home
            ),
            GlassDockItem(
                route = "accounts",
                description = "Cuentas",
                unselectedIcon = Icons.Outlined.AccountBalanceWallet,
                selectedIcon = Icons.Filled.AccountBalanceWallet
            ),
            GlassDockItem(
                route = "metrics",
                description = "Métricas",
                unselectedIcon = Icons.AutoMirrored.Outlined.TrendingUp,
                selectedIcon = Icons.AutoMirrored.Filled.TrendingUp
            ),
            GlassDockItem(
                route = "goals_debts",
                description = "Metas y Deudas",
                unselectedIcon = Icons.Outlined.Flag,
                selectedIcon = Icons.Filled.Flag
            ),
            GlassDockItem(
                route = "motorcycle",
                description = "Mi Moto",
                unselectedIcon = Icons.Outlined.TwoWheeler,
                selectedIcon = Icons.Filled.TwoWheeler
            )
        )
    }

    // Frosted Glass Gradients for Dark and Light Themes
    val glassBgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xCC1A1F30), // Frosted translucent deep slate
                Color(0xD90D111A)  // Dark glassy bottom
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xE6FFFFFF), // Frosted crisp white glass
                Color(0xCCEEF2F6)  // Soft glassy silver
            )
        )
    }

    val glassBorderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x59FFFFFF), // Top specular highlight (35% white)
                Color(0x1AFFFFFF), // Mid fade
                Color(0x08FFFFFF)  // Bottom edge
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xB3FFFFFF), // Crisp upper reflection
                Color(0x33000000)  // Soft bottom shadow border
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Glass Dock Pod
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.20f),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.15f)
                )
                .clip(CircleShape)
                .background(brush = glassBgBrush)
                .border(
                    width = 1.2.dp,
                    brush = glassBorderBrush,
                    shape = CircleShape
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val isSelected = currentScreen == item.route

                    GlassDockIconItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassDockIconItem(
    item: GlassDockItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    // Spring bouncy scale effect
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glassIconScale"
    )

    // Animated glow container alpha
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glassGlowAlpha"
    )

    // Icon tint transition
    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isDark -> Color(0xFF94A3B8).copy(alpha = 0.70f)
            else -> Color(0xFF475569).copy(alpha = 0.75f)
        },
        label = "glassIconColor"
    )

    // Active Glowing Pill Brush
    val activePillBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 24.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Glass Active Indicator Pill
        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                    )
                    .clip(CircleShape)
                    .background(brush = activePillBrush)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.50f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Crisp Icon
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.description,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
