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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.domain.LockManager
import java.time.Duration
import java.time.Instant

/**
 * Card exposing the "Clear Device Owner" escape hatch. The action can itself
 * be locked so the user cannot bypass their blocks by clearing device owner.
 */
@Composable
fun ClearDeviceOwnerCard(
    lockMode: LockMode,
    unlockTime: Instant?,
    onClear: () -> Unit,
    onLock: () -> Unit,
    onExtend: () -> Unit
) {
    val isLocked = when (lockMode) {
        LockMode.UNLOCKED -> false
        LockMode.FOREVER -> true
        LockMode.UNTIL_DATETIME, LockMode.TIMER ->
            unlockTime != null && Instant.now().isBefore(unlockTime)
    }

    val lockStatusText = when (lockMode) {
        LockMode.UNLOCKED -> "Not locked"
        LockMode.FOREVER -> "Locked forever"
        LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
            if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
                "Lock expired"
            } else {
                val remaining = Duration.between(Instant.now(), unlockTime)
                "Unlocks in ${LockManager.formatDuration(remaining)}"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
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
                Text(
                    text = "Clear Device Owner",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isLocked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = lockStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLocked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }

                // Show Extend button when lock can be extended
                if (isLocked && (lockMode == LockMode.TIMER || lockMode == LockMode.UNTIL_DATETIME)) {
                    OutlinedButton(
                        onClick = onExtend,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Extend")
                    }
                }

                OutlinedButton(
                    onClick = onLock,
                    modifier = Modifier.weight(1f),
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

/**
 * Confirmation dialog shown before device owner is actually cleared.
 */
@Composable
fun ClearDeviceOwnerConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Device Owner?") },
        text = {
            Text(
                "This will remove all device owner restrictions. " +
                    "The app will need to be set as device owner again via ADB."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
