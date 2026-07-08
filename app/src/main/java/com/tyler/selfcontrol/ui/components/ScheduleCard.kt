package com.tyler.selfcontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.Schedule

/**
 * Card for configuring a block's activation mode and, when scheduled,
 * its active days and time range.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleCard(
    blockState: BlockState,
    isBlockEnabled: Boolean,
    schedule: Schedule?,
    scheduleStatusText: String,
    isLocked: Boolean,
    onStateChange: (BlockState) -> Unit,
    onToggleDay: (Int) -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Block State Selector
            Text(
                text = "Activation Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = blockState == BlockState.ALWAYS_ON,
                    onClick = { if (!isLocked) onStateChange(BlockState.ALWAYS_ON) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !isLocked
                ) {
                    Text("Always", style = MaterialTheme.typography.labelSmall)
                }
                SegmentedButton(
                    selected = blockState == BlockState.SCHEDULED,
                    onClick = { if (!isLocked) onStateChange(BlockState.SCHEDULED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !isLocked
                ) {
                    Text("Schedule", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Schedule configuration (only shown when SCHEDULED)
            if (blockState == BlockState.SCHEDULED && schedule != null) {
                HorizontalDivider()

                // Days of week
                Text(
                    text = "Active Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayChip("Sun", Schedule.SUNDAY, schedule, isLocked, onToggleDay)
                    DayChip("Mon", Schedule.MONDAY, schedule, isLocked, onToggleDay)
                    DayChip("Tue", Schedule.TUESDAY, schedule, isLocked, onToggleDay)
                    DayChip("Wed", Schedule.WEDNESDAY, schedule, isLocked, onToggleDay)
                    DayChip("Thu", Schedule.THURSDAY, schedule, isLocked, onToggleDay)
                    DayChip("Fri", Schedule.FRIDAY, schedule, isLocked, onToggleDay)
                    DayChip("Sat", Schedule.SATURDAY, schedule, isLocked, onToggleDay)
                }

                // Time range
                Text(
                    text = "Active Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (!isLocked) onEditStartTime() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLocked
                    ) {
                        // LocalTime.toString() renders as HH:mm when seconds are zero
                        Text(schedule.getStartTime().toString())
                    }
                    Text("-")
                    OutlinedButton(
                        onClick = { if (!isLocked) onEditEndTime() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLocked
                    ) {
                        Text(schedule.getEndTime().toString())
                    }
                }

                if (schedule.isOvernightSchedule()) {
                    Text(
                        text = "Overnight schedule (spans midnight)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // Status
                HorizontalDivider()
                Text(
                    text = scheduleStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isBlockEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else if (blockState == BlockState.ALWAYS_ON) {
                Text(
                    text = if (isBlockEnabled) "Block is always active when enabled" else "Block is disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isBlockEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    dayBit: Int,
    schedule: Schedule,
    isLocked: Boolean,
    onToggleDay: (Int) -> Unit
) {
    FilterChip(
        selected = schedule.isDayEnabled(dayBit),
        onClick = { if (!isLocked) onToggleDay(dayBit) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        enabled = !isLocked,
        modifier = Modifier.size(width = 56.dp, height = 32.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
