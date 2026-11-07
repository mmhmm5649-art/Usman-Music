package com.usman.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.ui.theme.ActivePillBrush
import com.usman.musicplayer.ui.theme.LiquidGlassBorderBrush
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import kotlin.math.roundToInt

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "home"),
    NEW("New", Icons.Filled.NewReleases, Icons.Outlined.NewReleases, "new"),
    LIBRARY("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "library"),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search, "search")
}

@Composable
fun UsmanGlassBottomNav(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val items = AppDestination.entries
    val selectedIndex = items.indexOf(currentDestination).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        LiquidGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(28.dp), spotColor = Color(0x99000000))
                .testTag("glass_bottom_nav"),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = Color(0x280D111E),
            borderBrush = LiquidGlassBorderBrush,
            borderWidth = 1.dp,
            hazeState = hazeState,
            hazeBlurRadius = 30.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val tabCount = items.size
                val tabWidth = maxWidth / tabCount

                // Animated sliding active pill indicator across the dock
                val targetOffset = tabWidth * selectedIndex
                val animatedOffsetFraction by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tabIndicatorAnimation"
                )

                // Sliding Liquid Glass Active Pill with translucent chromatic tint
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = (animatedOffsetFraction * tabWidth.toPx()).roundToInt(), y = 0) }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0x900284C7),
                                    Color(0x907C3AED)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0x99FFFFFF), Color(0x30FFFFFF))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                )

                // Interactive Navigation Tab Items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { destination ->
                        val isSelected = destination == currentDestination
                        NavTabItem(
                            destination = destination,
                            isSelected = isSelected,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onDestinationSelected(destination) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    destination: AppDestination,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) TextPrimary else TextSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "iconColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nav_item_${destination.route}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = destination.title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = destination.title,
                color = iconColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
