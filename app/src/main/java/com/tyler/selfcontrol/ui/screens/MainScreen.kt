package com.tyler.selfcontrol.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.model.BlockWithRules
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.domain.LockManager
import com.tyler.selfcontrol.ui.viewmodel.MainViewModel
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToBlockEdit: (Long) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isDeviceOwner = remember {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isDeviceOwnerApp(context.packageName)
    }

    val blocks by viewModel.blocks.collectAsState()
    var showAddBlockDialog by remember { mutableStateOf(false) }
    var newBlockName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Self Control") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBlockDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Block")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Device Owner Status Card
            item {
                DeviceOwnerStatusCard(isDeviceOwner = isDeviceOwner)
            }

            // Blocks section header
            if (blocks.isNotEmpty()) {
                item {
                    Text(
                        text = "Blocks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Block list
            items(blocks, key = { it.block.id }) { blockWithRules ->
                BlockCard(
                    blockWithRules = blockWithRules,
                    onToggle = { viewModel.toggleBlockEnabled(blockWithRules.block) },
                    onClick = { onNavigateToBlockEdit(blockWithRules.block.id) },
                    onDelete = { viewModel.deleteBlock(blockWithRules.block.id) }
                )
            }

            // Empty state
            if (blocks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Blocks Yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap + to create your first block",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
            }
        }
    }

    // Add Block Dialog
    if (showAddBlockDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddBlockDialog = false
                newBlockName = ""
            },
            title = { Text("New Block") },
            text = {
                OutlinedTextField(
                    value = newBlockName,
                    onValueChange = { newBlockName = it },
                    label = { Text("Block name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newBlockName.isNotBlank()) {
                            viewModel.createBlock(newBlockName.trim())
                            showAddBlockDialog = false
                            newBlockName = ""
                        }
                    },
                    enabled = newBlockName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddBlockDialog = false
                        newBlockName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceOwnerStatusCard(isDeviceOwner: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDeviceOwner) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isDeviceOwner) "Device Owner Active" else "Device Owner Not Set",
                style = MaterialTheme.typography.titleMedium,
                color = if (isDeviceOwner) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            if (!isDeviceOwner) {
                Text(
                    text = "Run: adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockCard(
    blockWithRules: BlockWithRules,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val block = blockWithRules.block
    val lock = blockWithRules.lock
    val isLocked = isLockActive(lock)
    val appCount = blockWithRules.appRules.size
    val websiteCount = blockWithRules.websiteRules.size
    val lockStatusText = getLockStatusText(lock)

    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (block.isEnabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = block.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.padding(start = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = buildString {
                        append("$appCount app${if (appCount != 1) "s" else ""}")
                        append(" | ")
                        append("$websiteCount website${if (websiteCount != 1) "s" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (isLocked && lockStatusText.isNotEmpty()) {
                    Text(
                        text = lockStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isLocked) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = block.isEnabled,
                    onCheckedChange = { onToggle() },
                    enabled = !isLocked || !block.isEnabled // Can enable, but can't disable if locked
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Block") },
            text = { Text("Are you sure you want to delete \"${block.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getLockStatusText(lock: Lock?): String {
    if (lock == null) return ""

    return when (lock.mode) {
        LockMode.UNLOCKED -> ""
        LockMode.FOREVER -> "Locked forever"
        LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
            val unlockTime = lock.unlockTime
            if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
                ""
            } else {
                val remaining = Duration.between(Instant.now(), unlockTime)
                "Unlocks in ${LockManager.formatDuration(remaining)}"
            }
        }
    }
}

/**
 * Check if a lock is actually active (not just non-UNLOCKED mode, but also not expired).
 */
private fun isLockActive(lock: Lock?): Boolean {
    if (lock == null) return false

    return when (lock.mode) {
        LockMode.UNLOCKED -> false
        LockMode.FOREVER -> true
        LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
            val unlockTime = lock.unlockTime
            unlockTime != null && Instant.now().isBefore(unlockTime)
        }
    }
}
