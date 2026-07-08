package com.tyler.selfcontrol.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tyler.selfcontrol.data.datastore.YouTubeRestrictLevel

/**
 * Dropdown selector for the YouTube restricted mode level.
 */
@Composable
fun YouTubeRestrictDropdown(
    level: YouTubeRestrictLevel,
    enabled: Boolean,
    onLevelChange: (YouTubeRestrictLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled
        ) {
            Text(
                text = when (level) {
                    YouTubeRestrictLevel.OFF -> "Off"
                    YouTubeRestrictLevel.MODERATE -> "Moderate"
                    YouTubeRestrictLevel.STRICT -> "Strict"
                }
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Off") },
                onClick = {
                    onLevelChange(YouTubeRestrictLevel.OFF)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Moderate") },
                onClick = {
                    onLevelChange(YouTubeRestrictLevel.MODERATE)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Strict") },
                onClick = {
                    onLevelChange(YouTubeRestrictLevel.STRICT)
                    expanded = false
                }
            )
        }
    }
}
