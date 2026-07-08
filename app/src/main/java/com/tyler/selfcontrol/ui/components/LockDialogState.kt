package com.tyler.selfcontrol.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Visibility state for the dialog trio (lock, forever-confirm, extend)
 * that every lockable setting shares.
 */
@Stable
class LockDialogState {
    var showLockDialog by mutableStateOf(false)
    var showForeverConfirm by mutableStateOf(false)
    var showExtendDialog by mutableStateOf(false)
}

@Composable
fun rememberLockDialogState(): LockDialogState = remember { LockDialogState() }
