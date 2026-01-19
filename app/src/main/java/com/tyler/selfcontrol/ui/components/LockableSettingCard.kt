package com.tyler.selfcontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyler.selfcontrol.data.datastore.LockableSettingState
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.domain.LockManager
import java.time.Duration
import java.time.Instant

/**
 * A reusable card composable for lockable settings.
 * Displays the setting with its current value, lock status, and a lock button.
 */
@Composable
fun <T> LockableSettingCard(
    title: String,
    description: String,
    state: LockableSettingState<T>,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier,
    onExtendClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isLocked = state.isLocked

    // Check if lock can be extended (TIMER or UNTIL_DATETIME with future unlock time)
    val canExtend = isLocked && onExtendClick != null &&
            (state.lockMode == LockMode.TIMER || state.lockMode == LockMode.UNTIL_DATETIME) &&
            state.unlockTime?.let { Instant.now().isBefore(it) } == true

    // Get lock status description
    val lockStatusText = when (state.lockMode) {
        LockMode.UNLOCKED -> "Not locked"
        LockMode.FOREVER -> "Locked forever"
        LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
            val unlockTime = state.unlockTime
            if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
                "Lock expired"
            } else {
                val remaining = Duration.between(Instant.now(), unlockTime)
                "Unlocks in ${LockManager.formatDuration(remaining)}"
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isLocked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = lockStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLocked) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Setting control (switch, dropdown, etc.)
                content()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Extend button (only when locked and extendable)
                    if (canExtend) {
                        OutlinedButton(
                            onClick = { onExtendClick?.invoke() }
                        ) {
                            Text("Extend")
                        }
                    }

                    // Lock button
                    OutlinedButton(
                        onClick = onLockClick,
                        enabled = !isLocked
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Lock")
                    }
                }
            }
        }
    }
}
