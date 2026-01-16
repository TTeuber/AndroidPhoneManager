package com.tyler.selfcontrol.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.worker.CooldownExpirationWorker
import com.tyler.selfcontrol.worker.CooldownNotificationWorker
import com.tyler.selfcontrol.worker.ScheduleWorker
import com.tyler.selfcontrol.worker.UnlockWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val devModeEnabled: Flow<Boolean> = settingsDataStore.devModeFlow
    val clearDeviceOwnerLockState: Flow<SettingsDataStore.ClearDeviceOwnerLockState> =
        settingsDataStore.clearDeviceOwnerLockFlow

    fun setDevMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDevMode(enabled)
            // Reschedule workers with appropriate interval
            UnlockWorker.schedule(context, devMode = enabled)
            ScheduleWorker.schedule(context, devMode = enabled)
            CooldownNotificationWorker.schedule(context, devMode = enabled)
            CooldownExpirationWorker.schedule(context, devMode = enabled)
        }
    }

    fun lockClearDeviceOwnerForDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.lockClearDeviceOwnerForDuration(duration)
        }
    }

    fun lockClearDeviceOwnerUntil(unlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.lockClearDeviceOwnerUntil(unlockTime)
        }
    }

    fun lockClearDeviceOwnerForever() {
        viewModelScope.launch {
            settingsDataStore.lockClearDeviceOwnerForever()
        }
    }
}
