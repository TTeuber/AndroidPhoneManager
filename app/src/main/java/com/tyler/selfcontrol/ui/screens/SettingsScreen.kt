package com.tyler.selfcontrol.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tyler.selfcontrol.data.datastore.LockableSettingState
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.data.datastore.YouTubeRestrictLevel
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.ui.components.ClearDeviceOwnerCard
import com.tyler.selfcontrol.ui.components.ClearDeviceOwnerConfirmDialog
import com.tyler.selfcontrol.ui.components.DevModeSection
import com.tyler.selfcontrol.ui.components.LockSettingDialogs
import com.tyler.selfcontrol.ui.components.LockableSettingCard
import com.tyler.selfcontrol.ui.components.YouTubeRestrictDropdown
import com.tyler.selfcontrol.ui.components.rememberLockDialogState
import com.tyler.selfcontrol.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

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

    // Content restriction settings
    val safeSearchState by viewModel.safeSearchState.collectAsState(
        initial = LockableSettingState(false, LockMode.UNLOCKED, null)
    )
    val youtubeRestrictState by viewModel.youtubeRestrictState.collectAsState(
        initial = LockableSettingState(YouTubeRestrictLevel.OFF, LockMode.UNLOCKED, null)
    )
    val incognitoDisabledState by viewModel.incognitoDisabledState.collectAsState(
        initial = LockableSettingState(false, LockMode.UNLOCKED, null)
    )

    var showClearDeviceOwnerDialog by remember { mutableStateOf(false) }
    val clearDeviceOwnerDialogs = rememberLockDialogState()
    val safeSearchDialogs = rememberLockDialogState()
    val youtubeDialogs = rememberLockDialogState()
    val incognitoDialogs = rememberLockDialogState()

    val isDeviceOwner = remember {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isDeviceOwnerApp(context.packageName)
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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clear Device Owner Section (always visible when device owner is set)
            if (isDeviceOwner) {
                ClearDeviceOwnerCard(
                    lockMode = lockState.mode,
                    unlockTime = lockState.unlockTime,
                    onClear = { showClearDeviceOwnerDialog = true },
                    onLock = { clearDeviceOwnerDialogs.showLockDialog = true },
                    onExtend = { clearDeviceOwnerDialogs.showExtendDialog = true }
                )
            }

            DevModeSection(
                devModeEnabled = devModeEnabled,
                onDevModeChange = { enabled ->
                    scope.launch {
                        viewModel.setDevMode(enabled)
                    }
                }
            )

            // Content Restriction Settings Section
            if (isDeviceOwner) {
                Text(
                    text = "Content Restrictions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )

                LockableSettingCard(
                    title = "Force SafeSearch",
                    description = "Force Google SafeSearch and block other search engines",
                    state = safeSearchState,
                    onLockClick = { safeSearchDialogs.showLockDialog = true },
                    onExtendClick = { safeSearchDialogs.showExtendDialog = true }
                ) {
                    Switch(
                        checked = safeSearchState.value,
                        onCheckedChange = { enabled ->
                            if (!safeSearchState.isLocked) {
                                viewModel.setSafeSearchEnabled(enabled)
                            }
                        },
                        enabled = !safeSearchState.isLocked
                    )
                }

                LockableSettingCard(
                    title = "YouTube Restricted Mode",
                    description = "Enforce restricted mode on YouTube web. YouTube app will be blocked.",
                    state = youtubeRestrictState,
                    onLockClick = { youtubeDialogs.showLockDialog = true },
                    onExtendClick = { youtubeDialogs.showExtendDialog = true }
                ) {
                    YouTubeRestrictDropdown(
                        level = youtubeRestrictState.value,
                        enabled = !youtubeRestrictState.isLocked,
                        onLevelChange = { viewModel.setYouTubeRestrictLevel(it) }
                    )
                }

                LockableSettingCard(
                    title = "Disable Incognito Mode",
                    description = "Disable incognito in Chrome. YouTube app will be blocked.",
                    state = incognitoDisabledState,
                    onLockClick = { incognitoDialogs.showLockDialog = true },
                    onExtendClick = { incognitoDialogs.showExtendDialog = true }
                ) {
                    Switch(
                        checked = incognitoDisabledState.value,
                        onCheckedChange = { disabled ->
                            if (!incognitoDisabledState.isLocked) {
                                viewModel.setIncognitoDisabled(disabled)
                            }
                        },
                        enabled = !incognitoDisabledState.isLocked
                    )
                }
            }
        }
    }

    if (showClearDeviceOwnerDialog) {
        ClearDeviceOwnerConfirmDialog(
            onConfirm = {
                showClearDeviceOwnerDialog = false
                clearDeviceOwner(context)
            },
            onDismiss = { showClearDeviceOwnerDialog = false }
        )
    }

    LockSettingDialogs(
        state = clearDeviceOwnerDialogs,
        unlockTime = lockState.unlockTime,
        foreverWarning = "This will permanently lock the Clear Device Owner button. " +
            "You will never be able to clear device owner through the app. This cannot be undone!",
        onLockUntil = viewModel::lockClearDeviceOwnerUntil,
        onLockForDuration = viewModel::lockClearDeviceOwnerForDuration,
        onLockForever = viewModel::lockClearDeviceOwnerForever,
        onExtendByDuration = viewModel::extendClearDeviceOwnerLockByDuration,
        onExtendUntil = viewModel::extendClearDeviceOwnerLockUntil
    )

    LockSettingDialogs(
        state = safeSearchDialogs,
        unlockTime = safeSearchState.unlockTime,
        foreverWarning = "This will permanently lock the SafeSearch setting. " +
            "You will not be able to disable SafeSearch. This cannot be undone!",
        onLockUntil = viewModel::lockSafeSearchUntil,
        onLockForDuration = viewModel::lockSafeSearchForDuration,
        onLockForever = viewModel::lockSafeSearchForever,
        onExtendByDuration = viewModel::extendSafeSearchLockByDuration,
        onExtendUntil = viewModel::extendSafeSearchLockUntil
    )

    LockSettingDialogs(
        state = youtubeDialogs,
        unlockTime = youtubeRestrictState.unlockTime,
        foreverWarning = "This will permanently lock the YouTube Restricted Mode setting. " +
            "You will not be able to reduce the restriction level. This cannot be undone!",
        onLockUntil = viewModel::lockYouTubeRestrictUntil,
        onLockForDuration = viewModel::lockYouTubeRestrictForDuration,
        onLockForever = viewModel::lockYouTubeRestrictForever,
        onExtendByDuration = viewModel::extendYouTubeRestrictLockByDuration,
        onExtendUntil = viewModel::extendYouTubeRestrictLockUntil
    )

    LockSettingDialogs(
        state = incognitoDialogs,
        unlockTime = incognitoDisabledState.unlockTime,
        foreverWarning = "This will permanently lock the Incognito Mode setting. " +
            "You will not be able to re-enable incognito mode. This cannot be undone!",
        onLockUntil = viewModel::lockIncognitoDisabledUntil,
        onLockForDuration = viewModel::lockIncognitoDisabledForDuration,
        onLockForever = viewModel::lockIncognitoDisabledForever,
        onExtendByDuration = viewModel::extendIncognitoDisabledLockByDuration,
        onExtendUntil = viewModel::extendIncognitoDisabledLockUntil
    )
}

private fun clearDeviceOwner(context: Context) {
    try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            // clearDeviceOwnerApp is deprecated in favor of wipeData() (factory reset),
            // but relinquishing device owner without wiping the device is exactly what
            // this setting is for, and no non-destructive replacement exists.
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(context.packageName)
            Toast.makeText(context, "Device owner cleared", Toast.LENGTH_SHORT).show()
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Failed to clear device owner: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
