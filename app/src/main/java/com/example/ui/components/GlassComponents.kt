package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceHeavy
import com.example.ui.theme.LiquidGlassBorderBrush
import com.example.ui.theme.LiquidGlassButtonBorder
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SoftCyan
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.skydoves.cloudy.cloudy

/**
 * USMAN MUSIC PLAYER — REAL LIQUID GLASS DESIGN SYSTEM
 *
 * Implements open-source Liquid Glass rendering inspired by skydoves/Cloudy:
 * - Real backdrop blur with GPU acceleration via Modifier.cloudy(radius = ...)
 * - Specular rim highlights (glossy reflections along the top/left edge)
 * - Chromatic refraction (subtle cyan/purple dispersion)
 * - Tactile spring-like press interactions
 * - Pure AMOLED black contrast preservation
 */

/**
 * Core Liquid Glass Panel / Card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = GlassSurfaceHeavy,
    borderBrush: Brush = LiquidGlassBorderBrush,
    borderWidth: Dp = 1.dp,
    showSpecularHighlight: Boolean = true,
    blurRadius: Int = 20,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, borderBrush, shape)
    ) {
        // 1. Background Liquid Glass Layer with Cloudy blur & translucent material refraction
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .cloudy(radius = 25) // Increased blur for better effect
                .background(
                    backgroundColor.copy(alpha = 0.20f) // Simplified solid translucent color
                )
        )

        // 2. Specular reflection and chromatic rim refraction
        if (showSpecularHighlight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // High specular white gleam along the top rim
                        val gleamWidth = size.width * 0.75f
                        val startX = (size.width - gleamWidth) / 2f
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x40FFFFFF),
                                    Color(0x90FFFFFF),
                                    Color(0x40FFFFFF),
                                    Color.Transparent
                                ),
                                startX = startX,
                                endX = startX + gleamWidth
                            ),
                            start = Offset(startX, 1f),
                            end = Offset(startX + gleamWidth, 1f),
                            strokeWidth = 1.5f
                        )

                        // Subtle left specular light catch
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x60FFFFFF),
                                    Color(0x2038BDF8),
                                    Color.Transparent
                                ),
                                startY = 2f,
                                endY = size.height * 0.5f
                            ),
                            start = Offset(1f, 2f),
                            end = Offset(1f, size.height * 0.5f),
                            strokeWidth = 1f
                        )
                    }
            )
        }

        // 3. Razor-sharp foreground content
        content()
    }
}

/**
 * Backward compatibility alias for LiquidGlassPanel
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    backgroundColor: Color = GlassSurfaceHeavy,
    borderBrush: Brush = LiquidGlassBorderBrush,
    borderWidth: Dp = 1.dp,
    showTopSpecularGleam: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = shape,
        backgroundColor = backgroundColor,
        borderBrush = borderBrush,
        borderWidth = borderWidth,
        showSpecularHighlight = showTopSpecularGleam,
        content = content
    )
}

/**
 * Floating Glass Button Surface with tactile feedback and diagonal sheen
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0x221E2338),
    borderBrush: Brush = LiquidGlassButtonBorder,
    borderWidth: Dp = 1.dp,
    testTag: String = "glass_button",
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        scale.animateTo(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isPressed) backgroundColor.copy(alpha = 0.35f) else backgroundColor, shape)
            .border(borderWidth, borderBrush, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                // Diagonal sheen reflection inside the glass button
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x20FFFFFF), Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.25f),
                        radius = size.width * 0.65f
                    )
                )
            }
            .testTag(testTag),
        content = content
    )
}

/**
 * Backward compatibility alias for GlassButtonSurface
 */
@Composable
fun GlassButtonSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0x221E2338),
    borderBrush: Brush = LiquidGlassButtonBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(borderWidth, borderBrush, shape)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x18FFFFFF), Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.25f),
                        radius = size.width * 0.6f
                    )
                )
            },
        content = content
    )
}

/**
 * Floating Circular or Rounded Glass Icon Button
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0x221E2338),
    borderBrush: Brush = LiquidGlassButtonBorder,
    testTag: String = "glass_icon_btn"
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = shape,
        backgroundColor = backgroundColor,
        borderBrush = borderBrush,
        testTag = testTag
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .align(Alignment.Center)
        )
    }
}

/**
 * Liquid Glass Search Bar with integrated cloudy backdrop blur
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchAction: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search local songs, artists, and albums",
    leadingIconTint: Color = NeonCyan
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x241C2036),
        borderBrush = LiquidGlassBorderBrush,
        borderWidth = 1.dp
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("single_search_bar"),
            placeholder = {
                Text(
                    text = placeholder,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = leadingIconTint
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = NeonCyan
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchAction() })
        )
    }
}

/**
 * Liquid Glass Slider for Seek & Volume
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = SoftPurple,
    inactiveTrackColor: Color = Color(0x283A3F58),
    thumbColor: Color = Color.White,
    testTag: String = "glass_slider"
) {
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.testTag(testTag),
        colors = SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor
        )
    )
}

/**
 * Reusable Liquid Glass Bottom Sheet Modal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x55FFFFFF))
            )
        },
        modifier = modifier
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            backgroundColor = Color(0xDC0C0E1B),
            borderBrush = LiquidGlassBorderBrush,
            borderWidth = 1.dp,
            blurRadius = 25,
            content = content
        )
    }
}

/**
 * Reusable Liquid Glass Album Artwork Container with Specular Rim
 */
@Composable
fun GlassArtworkContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    showSpecularRim: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.5.dp, LiquidGlassBorderBrush, shape)
            .then(
                if (showSpecularRim) {
                    Modifier.drawBehind {
                        val gleamWidth = size.width * 0.6f
                        val startX = (size.width - gleamWidth) / 2f
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x40FFFFFF),
                                    Color(0x80FFFFFF),
                                    Color(0x40FFFFFF),
                                    Color.Transparent
                                ),
                                startX = startX,
                                endX = startX + gleamWidth
                            ),
                            start = Offset(startX, 1.2f),
                            end = Offset(startX + gleamWidth, 1.2f),
                            strokeWidth = 2f
                        )
                    }
                } else Modifier
            ),
        content = content
    )
}
