package com.example.andresfinanzas.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.andresfinanzas.ui.theme.PrimaryBrand

data class CurvedNavItem(
    val route: String,
    val title: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Animated Raised Wave Floating Navigation Bar.
 * Directly based on the reference design:
 * - Floating pill container with smooth rounded corners.
 * - Smooth animated wave/hill that rises around the active tab.
 * - Elevated circular floating bubble popping above the bar.
 * - Bold active label underneath the elevated circle.
 * - Minimalist outline icons for unselected items.
 */
@Composable
fun FloatingDockNavigationBar(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = remember {
        listOf(
            CurvedNavItem(
                route = "dashboard",
                title = "Inicio",
                unselectedIcon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home
            ),
            CurvedNavItem(
                route = "accounts",
                title = "Cuentas",
                unselectedIcon = Icons.Outlined.AccountBalanceWallet,
                selectedIcon = Icons.Filled.AccountBalanceWallet
            ),
            CurvedNavItem(
                route = "metrics",
                title = "Métricas",
                unselectedIcon = Icons.AutoMirrored.Outlined.TrendingUp,
                selectedIcon = Icons.AutoMirrored.Filled.TrendingUp
            ),
            CurvedNavItem(
                route = "goals_debts",
                title = "Metas",
                unselectedIcon = Icons.Outlined.Flag,
                selectedIcon = Icons.Filled.Flag
            ),
            CurvedNavItem(
                route = "motorcycle",
                title = "Mi Moto",
                unselectedIcon = Icons.Outlined.TwoWheeler,
                selectedIcon = Icons.Filled.TwoWheeler
            )
        )
    }

    val selectedIndex = remember(currentScreen) {
        val idx = navItems.indexOfFirst { it.route == currentScreen }
        if (idx >= 0) idx else 0
    }

    // Spring animation for smooth sweeping transition of wave and elevated bubble
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "waveSweepAnim"
    )

    val haptic = LocalHapticFeedback.current
    val barSurfaceColor = MaterialTheme.colorScheme.surface
    val barBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val shadowAmbientColor = Color.Black.copy(alpha = 0.14f)
    val shadowSpotColor = PrimaryBrand.copy(alpha = 0.22f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(86.dp) // Total container height accommodating the raised wave & circle
    ) {
        // 1. Dynamic Curved Wave Surface (Drawn on Canvas)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    spotColor = shadowSpotColor,
                    ambientColor = shadowAmbientColor
                )
        ) {
            val barWidth = size.width
            val barHeight = size.height
            val barTop = 20.dp.toPx()
            val cornerRadius = 26.dp.toPx()

            val tabCount = navItems.size
            val tabWidth = barWidth / tabCount
            val waveCenterX = (animatedIndex + 0.5f) * tabWidth
            val waveHalfWidth = 44.dp.toPx()
            val wavePeakY = 2.dp.toPx()

            val waveLeft = (waveCenterX - waveHalfWidth).coerceAtLeast(0f)
            val waveRight = (waveCenterX + waveHalfWidth).coerceAtMost(barWidth)

            val path = Path().apply {
                // Start below top-left corner
                moveTo(0f, barTop + cornerRadius)
                // Top-left rounded corner
                quadraticBezierTo(0f, barTop, cornerRadius, barTop)

                // Line to start of wave
                if (waveLeft > cornerRadius) {
                    lineTo(waveLeft, barTop)
                }

                // Smooth cubic bezier wave rising to peak
                val c1x = waveLeft + waveHalfWidth * 0.45f
                val c1y = barTop
                val c2x = waveCenterX - waveHalfWidth * 0.35f
                val c2y = wavePeakY
                cubicTo(c1x, c1y, c2x, c2y, waveCenterX, wavePeakY)

                // Smooth cubic bezier wave descending back to bar top
                val c3x = waveCenterX + waveHalfWidth * 0.35f
                val c3y = wavePeakY
                val c4x = waveRight - waveHalfWidth * 0.45f
                val c4y = barTop
                cubicTo(c3x, c3y, c4x, c4y, waveRight, barTop)

                // Line to top-right corner
                lineTo(barWidth - cornerRadius, barTop)
                // Top-right rounded corner
                quadraticBezierTo(barWidth, barTop, barWidth, barTop + cornerRadius)

                // Right edge to bottom-right corner
                lineTo(barWidth, barHeight - cornerRadius)
                // Bottom-right rounded corner
                quadraticBezierTo(barWidth, barHeight, barWidth - cornerRadius, barHeight)

                // Bottom line to bottom-left corner
                lineTo(cornerRadius, barHeight)
                // Bottom-left rounded corner
                quadraticBezierTo(0f, barHeight, 0f, barHeight - cornerRadius)

                close()
            }

            // Draw filled body
            drawPath(path = path, color = barSurfaceColor)

            // Draw subtle outline border
            drawPath(path = path, color = barBorderColor, style = Stroke(width = 1.2.dp.toPx()))
        }

        // 2. Elevated Floating Circular Bubble (Glides smoothly above active tab)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidthDp = maxWidth / navItems.size
            val bubbleCenterDp = tabWidthDp * (animatedIndex + 0.5f)
            val bubbleLeftDp = bubbleCenterDp - 27.dp

            val activeItem = navItems[selectedIndex]

            Box(
                modifier = Modifier
                    .offset(x = bubbleLeftDp, y = (-2).dp)
                    .size(54.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        spotColor = PrimaryBrand.copy(alpha = 0.40f)
                    )
                    .clip(CircleShape)
                    .background(barSurfaceColor) // Outer matching ring
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(PrimaryBrand.copy(alpha = 0.12f)) // Inner tinted bubble
                    .border(
                        width = 2.2.dp,
                        color = PrimaryBrand,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = activeItem.selectedIcon,
                    contentDescription = activeItem.title,
                    tint = PrimaryBrand,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 3. Interactive Touch Slots & Labels for all 5 tabs
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp) // Starts at barTop
        ) {
            navItems.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(item.route)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        if (isSelected) {
                            // When selected, icon is in the elevated bubble above, show spacing
                            Spacer(modifier = Modifier.height(28.dp))

                            Text(
                                text = item.title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        } else {
                            // When unselected, show normal outline icon & muted label
                            Icon(
                                imageVector = item.unselectedIcon,
                                contentDescription = item.title,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = item.title,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
