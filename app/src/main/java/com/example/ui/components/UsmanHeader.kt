package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SoftCyan
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.TextPrimary

@Composable
fun UsmanHeader(
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App official logo inside elegant glass frame
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0x301E2338)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "USMAN Logo",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle with Apple-inspired typography
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "USMAN",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .size(14.dp)
                    .background(Color(0x35FFFFFF))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MUSIC PLAYER",
                color = SoftCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Floating Glass Search Icon Button
        GlassButtonSurface(
            modifier = Modifier
                .size(40.dp)
                .testTag("header_search_btn"),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = Color(0x221E2338)
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Floating Glass More Button
        GlassButtonSurface(
            modifier = Modifier
                .size(40.dp)
                .testTag("header_more_btn"),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = Color(0x221E2338)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = TextPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
