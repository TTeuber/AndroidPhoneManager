package com.tyler.selfcontrol.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.ui.components.AddWebsiteDialog
import com.tyler.selfcontrol.ui.components.AppPickerDialog
import com.tyler.selfcontrol.ui.components.AppRuleCard
import com.tyler.selfcontrol.ui.components.EmptyStateCard
import com.tyler.selfcontrol.ui.components.ExtendLockDialog
import com.tyler.selfcontrol.ui.components.ForeverLockConfirmDialog
import com.tyler.selfcontrol.ui.components.LockDialog
import com.tyler.selfcontrol.ui.components.LockStatusCard
import com.tyler.selfcontrol.ui.components.ScheduleCard
import com.tyler.selfcontrol.ui.components.SectionHeader
import com.tyler.selfcontrol.ui.components.TimePickerDialog
import com.tyler.selfcontrol.ui.components.WebsiteRuleCard
import com.tyler.selfcontrol.ui.viewmodel.BlockEditViewModel
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: BlockEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showAppPicker by remember { mutableStateOf(false) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var showLockDialog by remember { mutableStateOf(false) }
    var showExtendLockDialog by remember { mutableStateOf(false) }
    var showForeverConfirmDialog by remember { mutableStateOf(false) }
    var showScheduleStartTimePicker by remember { mutableStateOf(false) }
    var showScheduleEndTimePicker by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        FullScreenMessage {
            CircularProgressIndicator()
        }
        return
    }

    val block = uiState.block
    if (block == null) {
        FullScreenMessage {
            Text("Block not found")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                editedName = block.name
                                isEditingName = true
                            }
                        ) {
                            Text(block.name)
                            if (uiState.isLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    modifier = Modifier.padding(start = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditingName) {
                        IconButton(
                            onClick = {
                                if (editedName.isNotBlank()) {
                                    viewModel.updateBlockName(editedName.trim())
                                }
                                isEditingName = false
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                        IconButton(onClick = { isEditingName = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Apps Section
            item {
                SectionHeader(
                    title = "Blocked Apps",
                    count = uiState.appRules.size,
                    onAddClick = { showAppPicker = true }
                )
            }

            if (uiState.appRules.isEmpty()) {
                item {
                    EmptyStateCard(text = "No apps blocked. Tap + to add apps.")
                }
            } else {
                items(uiState.appRules, key = { it.id }) { appRule ->
                    AppRuleCard(
                        appName = viewModel.getAppNameForPackage(appRule.packageName),
                        packageName = appRule.packageName,
                        isLocked = uiState.isLocked,
                        onRemove = { viewModel.removeApp(appRule.packageName) }
                    )
                }
            }

            // Websites Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    title = "Website Rules",
                    count = uiState.websiteRules.size,
                    onAddClick = { showAddWebsiteDialog = true }
                )
            }

            if (uiState.websiteRules.isEmpty()) {
                item {
                    EmptyStateCard(text = "No website rules. Tap + to add websites.")
                }
            } else {
                items(uiState.websiteRules, key = { it.id }) { websiteRule ->
                    WebsiteRuleCard(
                        websiteRule = websiteRule,
                        isLocked = uiState.isLocked,
                        onRemove = { viewModel.removeWebsite(websiteRule.id) }
                    )
                }
            }

            // Schedule Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Schedule",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                ScheduleCard(
                    blockState = block.state,
                    isBlockEnabled = block.isEnabled,
                    schedule = uiState.schedule,
                    scheduleStatusText = uiState.scheduleStatusText,
                    isLocked = uiState.isLocked,
                    onStateChange = { viewModel.setBlockState(it) },
                    onToggleDay = { viewModel.toggleDay(it) },
                    onEditStartTime = { showScheduleStartTimePicker = true },
                    onEditEndTime = { showScheduleEndTimePicker = true }
                )
            }

            // Lock Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lock Settings",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                LockStatusCard(
                    lockMode = uiState.lock?.mode,
                    lockStatusText = uiState.lockStatusText,
                    isLocked = uiState.isLocked,
                    canExtendLock = uiState.canExtendLock,
                    onSetLock = { showLockDialog = true },
                    onExtendLock = { showExtendLockDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // App Picker Dialog
    if (showAppPicker) {
        AppPickerDialog(
            installedApps = installedApps,
            blockedPackages = uiState.appRules.map { it.packageName }.toSet(),
            onAppSelected = { packageName ->
                viewModel.addApp(packageName)
            },
            onDismiss = { showAppPicker = false }
        )
    }

    // Add Website Dialog
    if (showAddWebsiteDialog) {
        AddWebsiteDialog(
            onAdd = { domain, path, isAllowed ->
                viewModel.addWebsite(domain, path, isAllowed)
                showAddWebsiteDialog = false
            },
            onDismiss = { showAddWebsiteDialog = false }
        )
    }

    // Lock Dialog
    if (showLockDialog) {
        LockDialog(
            onLockUntilDateTime = { instant ->
                viewModel.lockUntilDateTime(instant)
                showLockDialog = false
            },
            onLockForDuration = { duration ->
                viewModel.lockForDuration(duration)
                showLockDialog = false
            },
            onLockForever = {
                showLockDialog = false
                showForeverConfirmDialog = true
            },
            onDismiss = { showLockDialog = false }
        )
    }

    // Extend Lock Dialog
    val currentUnlockTime = uiState.currentUnlockTime
    if (showExtendLockDialog && currentUnlockTime != null) {
        ExtendLockDialog(
            currentUnlockTime = currentUnlockTime,
            onExtendByDuration = { duration ->
                viewModel.extendLockByDuration(duration)
                showExtendLockDialog = false
            },
            onExtendUntil = { instant ->
                viewModel.extendLockUntil(instant)
                showExtendLockDialog = false
            },
            onDismiss = { showExtendLockDialog = false }
        )
    }

    // Forever Lock Confirmation Dialog
    if (showForeverConfirmDialog) {
        ForeverLockConfirmDialog(
            onConfirm = {
                viewModel.lockForever()
                showForeverConfirmDialog = false
            },
            onDismiss = { showForeverConfirmDialog = false }
        )
    }

    // Lock Error Dialog
    uiState.lockError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearLockError() },
            title = { Text("Lock Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLockError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Schedule Time Pickers
    if (showScheduleStartTimePicker) {
        TimePickerDialog(
            title = "Schedule Start Time",
            initialTime = uiState.schedule?.getStartTime() ?: LocalTime.of(9, 0),
            onConfirm = { time ->
                viewModel.setScheduleStartTime(time)
                showScheduleStartTimePicker = false
            },
            onDismiss = { showScheduleStartTimePicker = false }
        )
    }

    if (showScheduleEndTimePicker) {
        TimePickerDialog(
            title = "Schedule End Time",
            initialTime = uiState.schedule?.getEndTime() ?: LocalTime.of(17, 0),
            onConfirm = { time ->
                viewModel.setScheduleEndTime(time)
                showScheduleEndTimePicker = false
            },
            onDismiss = { showScheduleEndTimePicker = false }
        )
    }
}

@Composable
private fun FullScreenMessage(content: @Composable () -> Unit) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}
