package com.tyler.selfcontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.AllowedAppSource
import com.tyler.selfcontrol.data.model.BlacklistedApp
import com.tyler.selfcontrol.ui.viewmodel.AllowlistManagementViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowlistManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: AllowlistManagementViewModel = hiltViewModel()
) {
    val allowedApps by viewModel.allowedApps.collectAsState()
    val blacklistedApps by viewModel.blacklistedApps.collectAsState()
    val addToBlacklistState by viewModel.addToBlacklistState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddBlacklistDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    // Close dialog on successful add
    LaunchedEffect(addToBlacklistState.success) {
        if (addToBlacklistState.success) {
            showAddBlacklistDialog = false
            urlInput = ""
            viewModel.resetAddToBlacklistState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Lists") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FAB on blacklist tab
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddBlacklistDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add to Blacklist")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Allowed (${allowedApps.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Blacklisted (${blacklistedApps.size})") }
                )
            }

            when (selectedTab) {
                0 -> AllowedAppsList(
                    apps = allowedApps,
                    onRemove = { viewModel.removeFromAllowlist(it) }
                )
                1 -> BlacklistedAppsList(apps = blacklistedApps)
            }
        }
    }

    // Add to Blacklist Dialog
    if (showAddBlacklistDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddBlacklistDialog = false
                urlInput = ""
                viewModel.resetAddToBlacklistState()
            },
            title = { Text("Add to Blacklist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Play Store URL") },
                        placeholder = { Text("https://play.google.com/store/apps/details?id=...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        addToBlacklistState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        addToBlacklistState.error != null -> {
                            Text(
                                text = addToBlacklistState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        addToBlacklistState.parsedApp != null -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = addToBlacklistState.parsedApp!!.appName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = addToBlacklistState.parsedApp!!.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Category: ${addToBlacklistState.parsedApp!!.category.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (addToBlacklistState.parsedApp != null) {
                    TextButton(
                        onClick = { viewModel.confirmAddToBlacklist() }
                    ) {
                        Text("Add to Blacklist", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.parsePlayStoreUrl(urlInput) },
                        enabled = urlInput.isNotBlank() && !addToBlacklistState.isLoading
                    ) {
                        Text("Check URL")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddBlacklistDialog = false
                        urlInput = ""
                        viewModel.resetAddToBlacklistState()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AllowedAppsList(
    apps: List<AllowedApp>,
    onRemove: (String) -> Unit
) {
    if (apps.isEmpty()) {
        EmptyListCard(text = "No apps on the allowlist")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            items(apps, key = { it.id }) { app ->
                AllowedAppCard(app = app, onRemove = { onRemove(app.packageName) })
            }

            item { Spacer(modifier = Modifier.height(80.dp)) } // Space for FAB
        }
    }
}

@Composable
private fun BlacklistedAppsList(apps: List<BlacklistedApp>) {
    if (apps.isEmpty()) {
        EmptyListCard(text = "No apps on the blacklist")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            items(apps, key = { it.id }) { app ->
                BlacklistedAppCard(app = app)
            }

            item { Spacer(modifier = Modifier.height(80.dp)) } // Space for FAB
        }
    }
}

@Composable
private fun EmptyListCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllowedAppCard(
    app: AllowedApp,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        .withZone(ZoneId.systemDefault())
    val addedDate = formatter.format(app.addedAt)

    val sourceText = when (app.source) {
        AllowedAppSource.PRE_INSTALLED -> "Pre-installed"
        AllowedAppSource.USER_ADDED -> "User added"
        AllowedAppSource.COOLDOWN_APPROVED -> "Cooldown approved"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$sourceText • Added $addedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove from Allowlist") },
            text = { Text("Remove \"${app.appName}\" from the allowlist? You'll need to go through the approval process again to reinstall it.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showRemoveDialog = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BlacklistedAppCard(app: BlacklistedApp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
            app.reason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
