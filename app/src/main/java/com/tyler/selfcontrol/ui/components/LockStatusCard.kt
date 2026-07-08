package com.tyler.selfcontrol.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
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
import com.tyler.selfcontrol.data.model.LockMode

/**
 * Card showing a block's lock status with actions to set or extend the lock.
 */
@Composable
fun LockStatusCard(
    lockMode: LockMode?,
    lockStatusText: String,
    isLocked: Boolean,
    canExtendLock: Boolean,
    onSetLock: () -> Unit,
    onExtendLock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = lockStatusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isLocked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (!isLocked) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSetLock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Lock")
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (lockMode) {
                        LockMode.FOREVER -> "This block is permanently locked and cannot be modified."
                        LockMode.UNTIL_DATETIME, LockMode.TIMER ->
                            "You cannot remove items or disable this block until it unlocks."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                if (canExtendLock) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onExtendLock,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Extend Lock")
                    }
                }
            }
        }
    }
}
