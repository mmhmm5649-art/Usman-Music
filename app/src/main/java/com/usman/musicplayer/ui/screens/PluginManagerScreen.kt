package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usman.musicplayer.sdk.ProviderRegistry

@Composable
fun PluginManagerScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Plugin Manager", modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn {
            items(ProviderRegistry.providers) { provider ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = provider.metadata.name, modifier = Modifier.weight(1f))
                    Switch(checked = true, onCheckedChange = { /* Toggle logic */ })
                }
            }
        }
    }
}
