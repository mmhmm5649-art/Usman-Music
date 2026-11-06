package com.usman.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.sdk.MusicProvider
import dev.brahmkshatriya.echo.common.settings.*
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

@Composable
fun ExtensionSettings(
    provider: MusicProvider,
    modifier: Modifier = Modifier
) {
    var settings by remember { mutableStateOf<List<Setting>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(provider) {
        isLoading = true
        settings = provider.getSettingItems()
        isLoading = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
            }
        } else if (settings.isEmpty()) {
            // No settings
        } else {
            Text(
                text = "EXTENSION SETTINGS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            settings.forEach { item ->
                when (item) {
                    is SettingCategory -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = item.title,
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        item.description?.let {
                            Text(text = it, color = TextSecondary, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is SettingSwitch -> {
                        var checked by remember { mutableStateOf(item.defaultValue) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                item.description?.let {
                                    Text(text = it, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    provider.updateSetting(item.key, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonCyan
                                )
                            )
                        }
                    }
                    is SettingList -> {
                        var selectedIndex by remember { mutableIntStateOf(item.defaultValue) }
                        var showDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showDialog = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                item.description?.let {
                                    Text(text = it, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.entries.getOrNull(selectedIndex) ?: "Select",
                                    color = NeonCyan,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, null, tint = NeonCyan)
                            }
                        }

                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text(item.title, color = TextPrimary) },
                                text = {
                                    Column {
                                        item.entries.forEachIndexed { index, entry ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    selectedIndex = index
                                                    provider.updateSetting(item.key, index)
                                                    showDialog = false
                                                }.padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = selectedIndex == index,
                                                    onClick = {
                                                        selectedIndex = index
                                                        provider.updateSetting(item.key, index)
                                                        showDialog = false
                                                    },
                                                    colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = entry, color = TextPrimary)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                containerColor = Color(0xFF161B2E)
                            )
                        }
                    }
                    is SettingTextInput -> {
                        var value by remember { mutableStateOf(item.defaultValue) }
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            item.description?.let {
                                Text(text = it, color = TextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = {
                                    value = it
                                    provider.updateSetting(item.key, it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = NeonCyan
                                ),
                                singleLine = true
                            )
                        }
                    }
                    is SettingSlider -> {
                        var value by remember { mutableFloatStateOf(item.defaultValue.toFloat()) }
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Slider(
                                value = value,
                                onValueChange = {
                                    value = it
                                    provider.updateSetting(item.key, it.toInt())
                                },
                                valueRange = item.min.toFloat()..item.max.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                            )
                            Text(text = value.toInt().toString(), color = NeonCyan, fontSize = 12.sp)
                        }
                    }
                    is SettingMultipleChoice -> {
                        var selectedIndices by remember { mutableStateOf(item.defaultValues) }
                        var showDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showDialog = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                val summary = item.entries.filterIndexed { index, _ -> selectedIndices.contains(index) }.joinToString(", ")
                                if (summary.isNotBlank()) {
                                    Text(text = summary, color = NeonCyan, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Icon(Icons.Default.ArrowDropDown, null, tint = NeonCyan)
                        }

                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { 
                                    showDialog = false
                                    provider.updateSetting(item.key, selectedIndices.map { item.entries[it] }.toSet())
                                },
                                title = { Text(item.title, color = TextPrimary) },
                                text = {
                                    Column {
                                        item.entries.forEachIndexed { index, entry ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    selectedIndices = if (selectedIndices.contains(index)) {
                                                        selectedIndices - index
                                                    } else {
                                                        selectedIndices + index
                                                    }
                                                }.padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = selectedIndices.contains(index),
                                                    onCheckedChange = { checked ->
                                                        selectedIndices = if (checked) selectedIndices + index else selectedIndices - index
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = entry, color = TextPrimary)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { 
                                        showDialog = false
                                        provider.updateSetting(item.key, selectedIndices.map { item.entries[it] }.toSet())
                                    }) {
                                        Text("Done", color = NeonCyan)
                                    }
                                },
                                containerColor = Color(0xFF161B2E)
                            )
                        }
                    }
                    is SettingOnClick -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { provider.updateSetting(item.key, Unit) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
