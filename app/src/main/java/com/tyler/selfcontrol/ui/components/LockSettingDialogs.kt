package com.tyler.selfcontrol.ui.components

import androidx.compose.runtime.Composable
import java.time.Duration
import java.time.Instant

/**
 * The lock / forever-confirm / extend dialogs for one lockable setting,
 * driven by [state]. Pair with a [LockableSettingCard] (or similar) whose
 * lock/extend buttons set [LockDialogState.showLockDialog] and
 * [LockDialogState.showExtendDialog].
 *
 * @param unlockTime current unlock time; required for the extend dialog to show
 * @param foreverWarning message shown before a forever lock is confirmed
 */
@Composable
fun LockSettingDialogs(
    state: LockDialogState,
    unlockTime: Instant?,
    foreverWarning: String,
    onLockUntil: (Instant) -> Unit,
    onLockForDuration: (Duration) -> Unit,
    onLockForever: () -> Unit,
    onExtendByDuration: (Duration) -> Unit,
    onExtendUntil: (Instant) -> Unit
) {
    if (state.showLockDialog) {
        LockDialog(
            onLockUntilDateTime = { instant ->
                onLockUntil(instant)
                state.showLockDialog = false
            },
            onLockForDuration = { duration ->
                onLockForDuration(duration)
                state.showLockDialog = false
            },
            onLockForever = {
                state.showLockDialog = false
                state.showForeverConfirm = true
            },
            onDismiss = { state.showLockDialog = false }
        )
    }

    if (state.showForeverConfirm) {
        ForeverLockWarningDialog(
            message = foreverWarning,
            onConfirm = {
                onLockForever()
                state.showForeverConfirm = false
            },
            onDismiss = { state.showForeverConfirm = false }
        )
    }

    if (state.showExtendDialog && unlockTime != null) {
        ExtendLockDialog(
            currentUnlockTime = unlockTime,
            onExtendByDuration = { duration ->
                onExtendByDuration(duration)
                state.showExtendDialog = false
            },
            onExtendUntil = { instant ->
                onExtendUntil(instant)
                state.showExtendDialog = false
            },
            onDismiss = { state.showExtendDialog = false }
        )
    }
}
