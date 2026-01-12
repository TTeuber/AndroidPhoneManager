package com.tyler.selfcontrol.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.model.WebsiteRule
import com.tyler.selfcontrol.ui.viewmodel.BlockEditViewModel
import com.tyler.selfcontrol.ui.viewmodel.InstalledApp

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
                    title = "Blocked Websites",
                    count = uiState.websiteRules.size,
                    onAddClick = { showAddWebsiteDialog = true }
                )
            }

            if (uiState.websiteRules.isEmpty()) {
                item {
                    EmptyStateCard(text = "No websites blocked. Tap + to add websites.")
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
            onAdd = { domain, path ->
                viewModel.addWebsite(domain, path)
                showAddWebsiteDialog = false
            },
            onDismiss = { showAddWebsiteDialog = false }
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
                    text = websiteRule.domain,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!websiteRule.path.isNullOrEmpty()) {
                    Text(
                        text = "Path: ${websiteRule.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AddWebsiteDialog(
    onAdd: (domain: String, path: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Website to Block") },
        text = {
            Column {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Path (optional)") },
                    placeholder = { Text("/shorts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Example: youtube.com with path /shorts to block youtube.com/shorts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (domain.isNotBlank()) {
                        onAdd(domain, path.takeIf { it.isNotBlank() })
                    }
                },
                enabled = domain.isNotBlank()
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
