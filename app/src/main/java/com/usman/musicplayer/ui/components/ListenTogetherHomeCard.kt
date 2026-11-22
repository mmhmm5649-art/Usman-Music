package com.usman.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.R
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

/**
 * Premium Liquid Glass horizontal entry card placed on the Home screen
 * directly below the language filter chips and directly above the category cards.
 */
@Composable
fun ListenTogetherHomeCard(
    onClick: () -> Unit,
    activeRoomCode: String? = null,
    activeParticipantCount: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val isLive = !activeRoomCode.isNullOrBlank()
    val cardShape = RoundedCornerShape(20.dp)
    val glassBackground = remember(isLive) {
        Brush.horizontalGradient(
            colors = if (isLive) {
                listOf(
                    Color(0xCC0A2338),
                    Color(0xBF0B1930),
                    Color(0xCC0A2A3B)
                )
            } else {
                listOf(
                    Color(0xC8091728),
                    Color(0xBA0C1626),
                    Color(0xC80A2033)
                )
            }
        )
    }
    val glassBorder = remember(isLive) {
        Brush.horizontalGradient(
            colors = listOf(
                SoftCyan.copy(alpha = if (isLive) 0.72f else 0.45f),
                ElectricBlue.copy(alpha = if (isLive) 0.55f else 0.30f),
                Color.White.copy(alpha = 0.14f)
            )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(cardShape)
                .background(glassBackground)
                .border(width = 1.dp, brush = glassBorder, shape = cardShape)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("home_listen_together_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Two-person Liquid Glass icon emblem
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    SoftCyan.copy(alpha = 0.28f),
                                    ElectricBlue.copy(alpha = 0.32f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = SoftCyan.copy(alpha = 0.42f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = stringResource(R.string.listen_together_title),
                        tint = SoftCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.listen_together_title),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Small NEW or LIVE indicator pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isLive) Color(0xFF00E676).copy(alpha = 0.20f)
                                    else SoftCyan.copy(alpha = 0.20f)
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = if (isLive) Color(0xFF00E676).copy(alpha = 0.65f)
                                    else SoftCyan.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("home_listen_together_badge")
                        ) {
                            Text(
                                text = if (isLive) "LIVE" else stringResource(R.string.listen_together_new_badge),
                                color = if (isLive) Color(0xFF4ADE80) else SoftCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Text(
                        text = if (isLive) {
                            val listeners = if (activeParticipantCount == 1) "1 listener" else "$activeParticipantCount listeners"
                            "Room $activeRoomCode • $listeners in sync"
                        } else {
                            stringResource(R.string.listen_together_subtitle)
                        },
                        color = if (isLive) SoftCyan.copy(alpha = 0.92f) else TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Open Listen Together",
                        tint = SoftCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
