package com.tyler.selfcontrol.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tyler.selfcontrol.data.model.LockMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val devModeKey = booleanPreferencesKey("dev_mode")
    private val clearDeviceOwnerLockModeKey = stringPreferencesKey("clear_device_owner_lock_mode")
    private val clearDeviceOwnerUnlockTimeKey = longPreferencesKey("clear_device_owner_unlock_time")

    val devModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[devModeKey] ?: false
    }

    suspend fun setDevMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[devModeKey] = enabled
        }
    }

    // Clear Device Owner lock state
    data class ClearDeviceOwnerLockState(
        val mode: LockMode,
        val unlockTime: Instant?
    )

    val clearDeviceOwnerLockFlow: Flow<ClearDeviceOwnerLockState> = context.dataStore.data.map { preferences ->
        val modeString = preferences[clearDeviceOwnerLockModeKey] ?: LockMode.UNLOCKED.name
        val mode = try {
            LockMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            LockMode.UNLOCKED
        }
        val unlockTimeMillis = preferences[clearDeviceOwnerUnlockTimeKey]
        val unlockTime = unlockTimeMillis?.let { Instant.ofEpochMilli(it) }
        ClearDeviceOwnerLockState(mode, unlockTime)
    }

    suspend fun setClearDeviceOwnerLock(mode: LockMode, unlockTime: Instant? = null) {
        context.dataStore.edit { preferences ->
            preferences[clearDeviceOwnerLockModeKey] = mode.name
            if (unlockTime != null) {
                preferences[clearDeviceOwnerUnlockTimeKey] = unlockTime.toEpochMilli()
            } else {
                preferences.remove(clearDeviceOwnerUnlockTimeKey)
            }
        }
    }

    suspend fun lockClearDeviceOwnerForDuration(duration: Duration) {
        val unlockTime = Instant.now().plus(duration)
        setClearDeviceOwnerLock(LockMode.TIMER, unlockTime)
    }

    suspend fun lockClearDeviceOwnerUntil(unlockTime: Instant) {
        setClearDeviceOwnerLock(LockMode.UNTIL_DATETIME, unlockTime)
    }

    suspend fun lockClearDeviceOwnerForever() {
        setClearDeviceOwnerLock(LockMode.FOREVER, null)
    }

    suspend fun unlockClearDeviceOwner() {
        setClearDeviceOwnerLock(LockMode.UNLOCKED, null)
    }

    suspend fun isClearDeviceOwnerLocked(): Boolean {
        val state = clearDeviceOwnerLockFlow.first()
        return when (state.mode) {
            LockMode.UNLOCKED -> false
            LockMode.FOREVER -> true
            LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
                val unlockTime = state.unlockTime ?: return false
                Instant.now().isBefore(unlockTime)
            }
        }
    }

    suspend fun checkAndClearExpiredDeviceOwnerLock(): Boolean {
        val state = clearDeviceOwnerLockFlow.first()
        if (state.mode == LockMode.UNTIL_DATETIME || state.mode == LockMode.TIMER) {
            val unlockTime = state.unlockTime
            if (unlockTime != null && Instant.now().isAfter(unlockTime)) {
                unlockClearDeviceOwner()
                return true
            }
        }
        return false
    }
}
