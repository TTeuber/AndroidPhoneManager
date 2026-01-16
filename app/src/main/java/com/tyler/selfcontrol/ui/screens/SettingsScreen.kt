package com.tyler.selfcontrol.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.domain.LockManager
import com.tyler.selfcontrol.receiver.SelfControlDeviceAdminReceiver
import com.tyler.selfcontrol.ui.components.LockDialog
import com.tyler.selfcontrol.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devModeEnabled by viewModel.devModeEnabled.collectAsState(initial = false)
    val lockState by viewModel.clearDeviceOwnerLockState.collectAsState(
        initial = SettingsDataStore.ClearDeviceOwnerLockState(LockMode.UNLOCKED, null)
    )

    var showClearDeviceOwnerDialog by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showForeverConfirmDialog by remember { mutableStateOf(false) }

    val isDeviceOwner = remember {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isDeviceOwnerApp(context.packageName)
    }

    // Check if the lock is currently active
    val isLocked = when (lockState.mode) {
        LockMode.UNLOCKED -> false
        LockMode.FOREVER -> true
        LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
            val unlockTime = lockState.unlockTime
            unlockTime != null && Instant.now().isBefore(unlockTime)
        }
    }

    // Get lock status description
    val lockStatusText = when (lockState.mode) {
        LockMode.UNLOCKED -> "Not locked"
        LockMode.FOREVER -> "Locked forever"
        LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
            val unlockTime = lockState.unlockTime
            if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
                "Lock expired"
            } else {
                val remaining = Duration.between(Instant.now(), unlockTime)
                "Unlocks in ${LockManager.formatDuration(remaining)}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clear Device Owner Section (always visible when device owner is set)
            if (isDeviceOwner) {
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
                                onClick = { showClearDeviceOwnerDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = !isLocked,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Clear")
                            }

                            OutlinedButton(
                                onClick = { showLockDialog = true },
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

            // Dev Mode Toggle
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dev Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Enable development features",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = devModeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                viewModel.setDevMode(enabled)
                            }
                        }
                    )
                }
            }

            // Dev Mode indicator
            if (devModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Dev Mode Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Lock expiry check: every 1 minute (instead of 15 minutes)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }

    // Lock Dialog
    if (showLockDialog) {
        LockDialog(
            onLockUntilDateTime = { instant ->
                viewModel.lockClearDeviceOwnerUntil(instant)
                showLockDialog = false
            },
            onLockForDuration = { duration ->
                viewModel.lockClearDeviceOwnerForDuration(duration)
                showLockDialog = false
            },
            onLockForever = {
                showLockDialog = false
                showForeverConfirmDialog = true
            },
            onDismiss = { showLockDialog = false }
        )
    }

    // Forever Lock Confirmation Dialog
    if (showForeverConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showForeverConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Lock Forever?") },
            text = {
                Text("This will permanently lock the Clear Device Owner button. You will never be able to clear device owner through the app. This cannot be undone!")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.lockClearDeviceOwnerForever()
                        showForeverConfirmDialog = false
                    }
                ) {
                    Text("Lock Forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForeverConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Device Owner Confirmation Dialog
    if (showClearDeviceOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showClearDeviceOwnerDialog = false },
            title = { Text("Clear Device Owner?") },
            text = {
                Text("This will remove all device owner restrictions. The app will need to be set as device owner again via ADB.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDeviceOwnerDialog = false
                        clearDeviceOwner(context)
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDeviceOwnerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun clearDeviceOwner(context: Context) {
    try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, SelfControlDeviceAdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.clearDeviceOwnerApp(context.packageName)
            Toast.makeText(context, "Device owner cleared", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to clear device owner: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
