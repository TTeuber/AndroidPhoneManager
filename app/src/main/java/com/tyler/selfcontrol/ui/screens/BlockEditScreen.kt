package com.tyler.selfcontrol.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.model.WebsiteRule
import com.tyler.selfcontrol.ui.components.ExtendLockDialog
import com.tyler.selfcontrol.ui.components.LockDialog
import com.tyler.selfcontrol.ui.viewmodel.BlockEditViewModel
import com.tyler.selfcontrol.ui.viewmodel.InstalledApp
import com.tyler.selfcontrol.util.parseUrl
import java.time.Duration
import java.time.Instant
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
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val block = uiState.block
    if (block == null) {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Block not found")
            }
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
                    block = block,
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
                    lock = uiState.lock,
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

    // Schedule Start Time Picker
    if (showScheduleStartTimePicker) {
        val schedule = uiState.schedule
        val initialTime = schedule?.getStartTime() ?: LocalTime.of(9, 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showScheduleStartTimePicker = false },
            title = { Text("Schedule Start Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setScheduleStartTime(
                            LocalTime.of(timePickerState.hour, timePickerState.minute)
                        )
                        showScheduleStartTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleStartTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Schedule End Time Picker
    if (showScheduleEndTimePicker) {
        val schedule = uiState.schedule
        val initialTime = schedule?.getEndTime() ?: LocalTime.of(17, 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showScheduleEndTimePicker = false },
            title = { Text("Schedule End Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setScheduleEndTime(
                            LocalTime.of(timePickerState.hour, timePickerState.minute)
                        )
                        showScheduleEndTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleEndTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun AppRuleCard(
    appName: String,
    packageName: String,
    isLocked: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isLocked) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsiteRuleCard(
    websiteRule: WebsiteRule,
    isLocked: Boolean,
    onRemove: () -> Unit
) {
    val isAllowed = websiteRule.isAllowed
    val fullUrl = buildString {
        append(websiteRule.domain)
        if (!websiteRule.path.isNullOrEmpty()) {
            append(websiteRule.path)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAllowed) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAllowed) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (isAllowed) "Allowed" else "Blocked",
                    tint = if (isAllowed) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = fullUrl,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isAllowed) "Allowed" else "Blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAllowed) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
            if (!isLocked) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    installedApps: List<InstalledApp>,
    blockedPackages: Set<String>,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Apps to Block") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isBlocked = app.packageName in blockedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBlocked) {
                                    onAppSelected(app.packageName)
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isBlocked) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isBlocked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Already blocked",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWebsiteDialog(
    onAdd: (domain: String, path: String?, isAllowed: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var isAllowed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Website Rule") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("youtube.com/shorts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Supported formats:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "  \u2022 example.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "  \u2022 example.com/path",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "  \u2022 https://example.com/path",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // TODO: Block/Allow toggle disabled for now - uncomment to re-enable allowlist feature
                // Spacer(modifier = Modifier.height(16.dp))
                // SingleChoiceSegmentedButtonRow(
                //     modifier = Modifier.fillMaxWidth()
                // ) {
                //     SegmentedButton(
                //         selected = !isAllowed,
                //         onClick = { isAllowed = false },
                //         shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                //     ) {
                //         Text("Block")
                //     }
                //     SegmentedButton(
                //         selected = isAllowed,
                //         onClick = { isAllowed = true },
                //         shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                //     ) {
                //         Text("Allow")
                //     }
                // }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotBlank()) {
                        val parsed = parseUrl(url)
                        onAdd(parsed.domain, parsed.path, isAllowed)
                    }
                },
                enabled = url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LockStatusCard(
    lock: com.tyler.selfcontrol.data.model.Lock?,
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
                    text = when (lock?.mode) {
                        LockMode.FOREVER -> "This block is permanently locked and cannot be modified."
                        LockMode.UNTIL_DATETIME, LockMode.TIMER -> "You cannot remove items or disable this block until it unlocks."
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(
    block: com.tyler.selfcontrol.data.model.Block,
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
                    selected = block.state == BlockState.ALWAYS_ON,
                    onClick = { if (!isLocked) onStateChange(BlockState.ALWAYS_ON) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !isLocked
                ) {
                    Text("Always", style = MaterialTheme.typography.labelSmall)
                }
                SegmentedButton(
                    selected = block.state == BlockState.SCHEDULED,
                    onClick = { if (!isLocked) onStateChange(BlockState.SCHEDULED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !isLocked
                ) {
                    Text("Schedule", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Schedule configuration (only shown when SCHEDULED)
            if (block.state == BlockState.SCHEDULED && schedule != null) {
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
                        val startTime = schedule.getStartTime()
                        Text(String.format("%02d:%02d", startTime.hour, startTime.minute))
                    }
                    Text("-")
                    OutlinedButton(
                        onClick = { if (!isLocked) onEditEndTime() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLocked
                    ) {
                        val endTime = schedule.getEndTime()
                        Text(String.format("%02d:%02d", endTime.hour, endTime.minute))
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
                    color = if (block.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else if (block.state == BlockState.ALWAYS_ON) {
                Text(
                    text = if (block.isEnabled) "Block is always active when enabled" else "Block is disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (block.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun ForeverLockConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val requiredText = "LOCK FOREVER"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Forever Lock")
            }
        },
        text = {
            Column {
                Text(
                    text = "This action cannot be undone!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Once locked forever, you will never be able to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("- Remove apps or websites from this block")
                Text("- Disable this block")
                Text("- Delete this block")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Type \"$requiredText\" to confirm:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmText == requiredText,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Lock Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
