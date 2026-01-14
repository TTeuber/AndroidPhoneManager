package com.tyler.selfcontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.model.AppCategory
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import com.tyler.selfcontrol.domain.AppInstallationManager
import com.tyler.selfcontrol.ui.viewmodel.AppInstallationViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInstallationScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppInstallationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add App") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // URL Input Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Enter Play Store URL",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.playStoreUrl,
                            onValueChange = { viewModel.setUrl(it) },
                            label = { Text("Play Store URL") },
                            placeholder = { Text("https://play.google.com/store/apps/details?id=...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.evaluateUrl() },
                            enabled = uiState.playStoreUrl.isNotBlank() && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).width(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Check App")
                            }
                        }
                    }
                }
            }

            // Result Section
            uiState.decision?.let { decision ->
                item {
                    DecisionCard(
                        decision = decision,
                        parsedInfo = uiState.parsedInfo,
                        onAddToAllowlist = { viewModel.addToAllowlistAndInstall() },
                        onRequestCooldown = { viewModel.requestCooldown() },
                        isLoading = uiState.isLoading
                    )
                }
            }

            // Pending Requests Section
            if (pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Approvals",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(pendingRequests, key = { it.id }) { request ->
                    PendingRequestCard(
                        request = request,
                        onApprove = { viewModel.approveRequest(request.id) },
                        onCancel = { viewModel.cancelRequest(request.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Error Dialog
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Success Dialog
    uiState.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSuccess() },
            title = { Text("Success") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSuccess()
                    onNavigateBack()
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun DecisionCard(
    decision: AppInstallationManager.InstallationDecision,
    parsedInfo: com.tyler.selfcontrol.domain.PlayStoreParser.ParsedAppInfo?,
    onAddToAllowlist: () -> Unit,
    onRequestCooldown: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (decision) {
                is AppInstallationManager.InstallationDecision.Allowed,
                is AppInstallationManager.InstallationDecision.AlreadyAllowed ->
                    MaterialTheme.colorScheme.primaryContainer
                is AppInstallationManager.InstallationDecision.RequiresCooldown ->
                    MaterialTheme.colorScheme.tertiaryContainer
                is AppInstallationManager.InstallationDecision.Blacklisted ->
                    MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
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
                Icon(
                    imageVector = when (decision) {
                        is AppInstallationManager.InstallationDecision.Allowed,
                        is AppInstallationManager.InstallationDecision.AlreadyAllowed ->
                            Icons.Default.Check
                        is AppInstallationManager.InstallationDecision.Blacklisted ->
                            Icons.Default.Close
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (decision) {
                        is AppInstallationManager.InstallationDecision.Allowed -> "Allowed"
                        is AppInstallationManager.InstallationDecision.AlreadyAllowed -> "Already Allowed"
                        is AppInstallationManager.InstallationDecision.RequiresCooldown -> "Requires Cooldown"
                        is AppInstallationManager.InstallationDecision.Blacklisted -> "Blacklisted"
                        is AppInstallationManager.InstallationDecision.PendingCooldown -> "Pending Approval"
                        is AppInstallationManager.InstallationDecision.Error -> "Error"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            parsedInfo?.let { info ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = info.appName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Category: ${formatCategory(info.category)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.isGame) {
                    Text(
                        text = "Detected as game",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (info.isBrowser) {
                    Text(
                        text = "Detected as browser",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (decision) {
                is AppInstallationManager.InstallationDecision.Allowed -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddToAllowlist,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add to Allowlist & Install")
                    }
                }
                is AppInstallationManager.InstallationDecision.AlreadyAllowed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app is already on your allowlist. You can install it from the Play Store.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is AppInstallationManager.InstallationDecision.RequiresCooldown -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app requires a cooldown period. You can approve it tomorrow between 3-6 PM.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestCooldown,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Cooldown")
                    }
                }
                is AppInstallationManager.InstallationDecision.Blacklisted -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = decision.reason ?: "This app is blacklisted and cannot be installed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is AppInstallationManager.InstallationDecision.PendingCooldown -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app already has a pending cooldown request.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is AppInstallationManager.InstallationDecision.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = decision.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingRequestCard(
    request: CooldownRequest,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    val isWindowOpen = request.status == CooldownStatus.WINDOW_OPEN
    val timeText = if (isWindowOpen) {
        val remaining = Duration.between(Instant.now(), request.windowEnd)
        "Expires in ${formatDuration(remaining)}"
    } else {
        val formatter = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a")
            .withZone(ZoneId.systemDefault())
        "Opens ${formatter.format(request.windowStart)}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWindowOpen) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.appName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatCategory(request.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWindowOpen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (isWindowOpen) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve & Install")
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel Request")
                }
            }
        }
    }
}

private fun formatCategory(category: AppCategory): String {
    return when (category) {
        AppCategory.UNRESTRICTED -> "Unrestricted"
        AppCategory.SOCIAL -> "Social"
        AppCategory.ENTERTAINMENT -> "Entertainment"
        AppCategory.VIDEO_PLAYERS -> "Video Players & Editors"
        AppCategory.GAMES -> "Games"
        AppCategory.BROWSERS -> "Browser"
    }
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
