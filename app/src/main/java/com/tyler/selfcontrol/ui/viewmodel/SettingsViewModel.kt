package com.tyler.selfcontrol.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.datastore.LockableSettingState
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.data.datastore.YouTubeRestrictLevel
import com.tyler.selfcontrol.domain.ContentRestrictionManager
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
    private val contentRestrictionManager: ContentRestrictionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val devModeEnabled: Flow<Boolean> = settingsDataStore.devModeFlow
    val clearDeviceOwnerLockState: Flow<SettingsDataStore.ClearDeviceOwnerLockState> =
        settingsDataStore.clearDeviceOwnerLockFlow

    // Content restriction settings
    val safeSearchState: Flow<LockableSettingState<Boolean>> = settingsDataStore.safeSearchStateFlow
    val youtubeRestrictState: Flow<LockableSettingState<YouTubeRestrictLevel>> = settingsDataStore.youtubeRestrictStateFlow
    val incognitoDisabledState: Flow<LockableSettingState<Boolean>> = settingsDataStore.incognitoDisabledStateFlow

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

    fun extendClearDeviceOwnerLockByDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.extendClearDeviceOwnerLockByDuration(duration)
        }
    }

    fun extendClearDeviceOwnerLockUntil(newUnlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.extendClearDeviceOwnerLockUntil(newUnlockTime)
        }
    }

    // ==================== SafeSearch Setting ====================

    fun setSafeSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSafeSearchEnabled(enabled)
            contentRestrictionManager.updateAllRestrictions()
        }
    }

    fun lockSafeSearchForDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.lockSafeSearchForDuration(duration)
        }
    }

    fun lockSafeSearchUntil(unlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.lockSafeSearchUntil(unlockTime)
        }
    }

    fun lockSafeSearchForever() {
        viewModelScope.launch {
            settingsDataStore.lockSafeSearchForever()
        }
    }

    fun extendSafeSearchLockByDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.extendSafeSearchLockByDuration(duration)
        }
    }

    fun extendSafeSearchLockUntil(newUnlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.extendSafeSearchLockUntil(newUnlockTime)
        }
    }

    // ==================== YouTube Restrict Setting ====================

    fun setYouTubeRestrictLevel(level: YouTubeRestrictLevel) {
        viewModelScope.launch {
            settingsDataStore.setYouTubeRestrictLevel(level)
            contentRestrictionManager.updateAllRestrictions()
        }
    }

    fun lockYouTubeRestrictForDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.lockYouTubeRestrictForDuration(duration)
        }
    }

    fun lockYouTubeRestrictUntil(unlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.lockYouTubeRestrictUntil(unlockTime)
        }
    }

    fun lockYouTubeRestrictForever() {
        viewModelScope.launch {
            settingsDataStore.lockYouTubeRestrictForever()
        }
    }

    fun extendYouTubeRestrictLockByDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.extendYouTubeRestrictLockByDuration(duration)
        }
    }

    fun extendYouTubeRestrictLockUntil(newUnlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.extendYouTubeRestrictLockUntil(newUnlockTime)
        }
    }

    // ==================== Incognito Disabled Setting ====================

    fun setIncognitoDisabled(disabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setIncognitoDisabled(disabled)
            contentRestrictionManager.updateAllRestrictions()
        }
    }

    fun lockIncognitoDisabledForDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.lockIncognitoDisabledForDuration(duration)
        }
    }

    fun lockIncognitoDisabledUntil(unlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.lockIncognitoDisabledUntil(unlockTime)
        }
    }

    fun lockIncognitoDisabledForever() {
        viewModelScope.launch {
            settingsDataStore.lockIncognitoDisabledForever()
        }
    }

    fun extendIncognitoDisabledLockByDuration(duration: Duration) {
        viewModelScope.launch {
            settingsDataStore.extendIncognitoDisabledLockByDuration(duration)
        }
    }

    fun extendIncognitoDisabledLockUntil(newUnlockTime: Instant) {
        viewModelScope.launch {
            settingsDataStore.extendIncognitoDisabledLockUntil(newUnlockTime)
        }
    }
}
