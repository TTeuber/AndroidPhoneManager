package com.tyler.selfcontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tyler.selfcontrol.domain.LockManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Dialog for extending an existing lock.
 * Supports two modes: "Add Time" (duration) and "Until" (datetime).
 * Does not include "Forever" option since you can't extend to forever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendLockDialog(
    currentUnlockTime: Instant,
    onExtendByDuration: (Duration) -> Unit,
    onExtendUntil: (Instant) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Duration state
    var durationHours by remember { mutableStateOf("1") }
    var durationMinutes by remember { mutableStateOf("0") }

    // Date/time state - initialize to day after current unlock time
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val initialDate = currentUnlockTime.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
    val initialTime = currentUnlockTime.atZone(ZoneId.systemDefault()).toLocalTime()
    var selectedDate by remember { mutableStateOf<LocalDate?>(initialDate) }
    var selectedTime by remember { mutableStateOf(initialTime) }

    // Error state
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Calculate remaining time for display
    val remainingDuration = Duration.between(Instant.now(), currentUnlockTime)
    val remainingText = if (remainingDuration.isNegative) {
        "Lock expired"
    } else {
        LockManager.formatDuration(remainingDuration)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extend Lock") },
        text = {
            Column {
                // Current status
                Text(
                    text = "Current unlock: $remainingText remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tab row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedTab = 0
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (selectedTab == 0) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("Add Time", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            selectedTab = 1
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (selectedTab == 1) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("Until", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // Add Time mode
                        Text(
                            text = "Add more time to the lock:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = durationHours,
                                onValueChange = { durationHours = it.filter { c -> c.isDigit() } },
                                label = { Text("Hours") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                                label = { Text("Minutes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                    1 -> {
                        // Until date/time mode
                        Text(
                            text = "Extend lock until:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedDate?.toString() ?: "Select Date")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(String.format(Locale.US, "%02d:%02d", selectedTime.hour, selectedTime.minute))
                        }

                        // Show error message if present
                        errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            val hours = durationHours.toLongOrNull() ?: 0
                            val minutes = durationMinutes.toLongOrNull() ?: 0
                            if (hours > 0 || minutes > 0) {
                                val duration = Duration.ofHours(hours).plusMinutes(minutes)
                                onExtendByDuration(duration)
                            }
                        }
                        1 -> {
                            selectedDate?.let { date ->
                                val dateTime = LocalDateTime.of(date, selectedTime)
                                val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()
                                if (instant.isAfter(currentUnlockTime)) {
                                    errorMessage = null
                                    onExtendUntil(instant)
                                } else {
                                    errorMessage = "New time must be later than current unlock time"
                                }
                            }
                        }
                    }
                },
                enabled = when (selectedTab) {
                    0 -> (durationHours.toLongOrNull() ?: 0) > 0 || (durationMinutes.toLongOrNull() ?: 0) > 0
                    1 -> selectedDate != null
                    else -> false
                }
            ) {
                Text("Extend")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (selectedDate ?: initialDate)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
