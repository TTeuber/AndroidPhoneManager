package com.tyler.selfcontrol.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

enum class YouTubeRestrictLevel(val value: Int) {
    OFF(0), MODERATE(1), STRICT(2);

    companion object {
        fun fromValue(value: Int): YouTubeRestrictLevel = entries.find { it.value == value } ?: OFF
    }
}

data class LockableSettingState<T>(
    val value: T,
    val lockMode: LockMode,
    val unlockTime: Instant?
) {
    val isLocked: Boolean
        get() = when (lockMode) {
            LockMode.UNLOCKED -> false
            LockMode.FOREVER -> true
            LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
                val time = unlockTime ?: return false
                Instant.now().isBefore(time)
            }
        }
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val devModeKey = booleanPreferencesKey("dev_mode")
    private val clearDeviceOwnerLockModeKey = stringPreferencesKey("clear_device_owner_lock_mode")
    private val clearDeviceOwnerUnlockTimeKey = longPreferencesKey("clear_device_owner_unlock_time")

    // SafeSearch setting keys
    private val safeSearchEnabledKey = booleanPreferencesKey("safe_search_enabled")
    private val safeSearchLockModeKey = stringPreferencesKey("safe_search_lock_mode")
    private val safeSearchUnlockTimeKey = longPreferencesKey("safe_search_unlock_time")

    // YouTube Restrict setting keys
    private val youtubeRestrictLevelKey = intPreferencesKey("youtube_restrict_level")
    private val youtubeRestrictLockModeKey = stringPreferencesKey("youtube_restrict_lock_mode")
    private val youtubeRestrictUnlockTimeKey = longPreferencesKey("youtube_restrict_unlock_time")

    // Incognito Disabled setting keys
    private val incognitoDisabledKey = booleanPreferencesKey("incognito_disabled")
    private val incognitoDisabledLockModeKey = stringPreferencesKey("incognito_disabled_lock_mode")
    private val incognitoDisabledUnlockTimeKey = longPreferencesKey("incognito_disabled_unlock_time")

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

    suspend fun extendClearDeviceOwnerLockByDuration(additionalDuration: Duration): Boolean {
        val state = clearDeviceOwnerLockFlow.first()
        if (state.mode != LockMode.TIMER && state.mode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime ?: Instant.now()
        val baseTime = if (Instant.now().isAfter(currentUnlockTime)) Instant.now() else currentUnlockTime
        val newUnlockTime = baseTime.plus(additionalDuration)
        setClearDeviceOwnerLock(state.mode, newUnlockTime)
        return true
    }

    suspend fun extendClearDeviceOwnerLockUntil(newUnlockTime: Instant): Boolean {
        val state = clearDeviceOwnerLockFlow.first()
        if (state.mode != LockMode.TIMER && state.mode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime
        if (currentUnlockTime != null && newUnlockTime.isBefore(currentUnlockTime)) {
            return false
        }
        setClearDeviceOwnerLock(state.mode, newUnlockTime)
        return true
    }

    // ==================== SafeSearch Setting ====================

    val safeSearchStateFlow: Flow<LockableSettingState<Boolean>> = context.dataStore.data.map { preferences ->
        val enabled = preferences[safeSearchEnabledKey] ?: false
        val lockMode = preferences[safeSearchLockModeKey]?.let {
            try { LockMode.valueOf(it) } catch (e: IllegalArgumentException) { LockMode.UNLOCKED }
        } ?: LockMode.UNLOCKED
        val unlockTime = preferences[safeSearchUnlockTimeKey]?.let { Instant.ofEpochMilli(it) }
        LockableSettingState(enabled, lockMode, unlockTime)
    }

    suspend fun setSafeSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[safeSearchEnabledKey] = enabled
        }
    }

    suspend fun setSafeSearchLock(mode: LockMode, unlockTime: Instant? = null) {
        context.dataStore.edit { preferences ->
            preferences[safeSearchLockModeKey] = mode.name
            if (unlockTime != null) {
                preferences[safeSearchUnlockTimeKey] = unlockTime.toEpochMilli()
            } else {
                preferences.remove(safeSearchUnlockTimeKey)
            }
        }
    }

    suspend fun lockSafeSearchForDuration(duration: Duration) {
        val unlockTime = Instant.now().plus(duration)
        setSafeSearchLock(LockMode.TIMER, unlockTime)
    }

    suspend fun lockSafeSearchUntil(unlockTime: Instant) {
        setSafeSearchLock(LockMode.UNTIL_DATETIME, unlockTime)
    }

    suspend fun lockSafeSearchForever() {
        setSafeSearchLock(LockMode.FOREVER, null)
    }

    suspend fun unlockSafeSearch() {
        setSafeSearchLock(LockMode.UNLOCKED, null)
    }

    suspend fun checkAndClearExpiredSafeSearchLock(): Boolean {
        val state = safeSearchStateFlow.first()
        if (state.lockMode == LockMode.UNTIL_DATETIME || state.lockMode == LockMode.TIMER) {
            val unlockTime = state.unlockTime
            if (unlockTime != null && Instant.now().isAfter(unlockTime)) {
                unlockSafeSearch()
                return true
            }
        }
        return false
    }

    suspend fun extendSafeSearchLockByDuration(additionalDuration: Duration): Boolean {
        val state = safeSearchStateFlow.first()
        if (state.lockMode != LockMode.TIMER && state.lockMode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime ?: Instant.now()
        val baseTime = if (Instant.now().isAfter(currentUnlockTime)) Instant.now() else currentUnlockTime
        val newUnlockTime = baseTime.plus(additionalDuration)
        setSafeSearchLock(state.lockMode, newUnlockTime)
        return true
    }

    suspend fun extendSafeSearchLockUntil(newUnlockTime: Instant): Boolean {
        val state = safeSearchStateFlow.first()
        if (state.lockMode != LockMode.TIMER && state.lockMode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime
        if (currentUnlockTime != null && newUnlockTime.isBefore(currentUnlockTime)) {
            return false
        }
        setSafeSearchLock(state.lockMode, newUnlockTime)
        return true
    }

    // ==================== YouTube Restrict Setting ====================

    val youtubeRestrictStateFlow: Flow<LockableSettingState<YouTubeRestrictLevel>> = context.dataStore.data.map { preferences ->
        val level = YouTubeRestrictLevel.fromValue(preferences[youtubeRestrictLevelKey] ?: 0)
        val lockMode = preferences[youtubeRestrictLockModeKey]?.let {
            try { LockMode.valueOf(it) } catch (e: IllegalArgumentException) { LockMode.UNLOCKED }
        } ?: LockMode.UNLOCKED
        val unlockTime = preferences[youtubeRestrictUnlockTimeKey]?.let { Instant.ofEpochMilli(it) }
        LockableSettingState(level, lockMode, unlockTime)
    }

    suspend fun setYouTubeRestrictLevel(level: YouTubeRestrictLevel) {
        context.dataStore.edit { preferences ->
            preferences[youtubeRestrictLevelKey] = level.value
        }
    }

    suspend fun setYouTubeRestrictLock(mode: LockMode, unlockTime: Instant? = null) {
        context.dataStore.edit { preferences ->
            preferences[youtubeRestrictLockModeKey] = mode.name
            if (unlockTime != null) {
                preferences[youtubeRestrictUnlockTimeKey] = unlockTime.toEpochMilli()
            } else {
                preferences.remove(youtubeRestrictUnlockTimeKey)
            }
        }
    }

    suspend fun lockYouTubeRestrictForDuration(duration: Duration) {
        val unlockTime = Instant.now().plus(duration)
        setYouTubeRestrictLock(LockMode.TIMER, unlockTime)
    }

    suspend fun lockYouTubeRestrictUntil(unlockTime: Instant) {
        setYouTubeRestrictLock(LockMode.UNTIL_DATETIME, unlockTime)
    }

    suspend fun lockYouTubeRestrictForever() {
        setYouTubeRestrictLock(LockMode.FOREVER, null)
    }

    suspend fun unlockYouTubeRestrict() {
        setYouTubeRestrictLock(LockMode.UNLOCKED, null)
    }

    suspend fun checkAndClearExpiredYouTubeRestrictLock(): Boolean {
        val state = youtubeRestrictStateFlow.first()
        if (state.lockMode == LockMode.UNTIL_DATETIME || state.lockMode == LockMode.TIMER) {
            val unlockTime = state.unlockTime
            if (unlockTime != null && Instant.now().isAfter(unlockTime)) {
                unlockYouTubeRestrict()
                return true
            }
        }
        return false
    }

    suspend fun extendYouTubeRestrictLockByDuration(additionalDuration: Duration): Boolean {
        val state = youtubeRestrictStateFlow.first()
        if (state.lockMode != LockMode.TIMER && state.lockMode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime ?: Instant.now()
        val baseTime = if (Instant.now().isAfter(currentUnlockTime)) Instant.now() else currentUnlockTime
        val newUnlockTime = baseTime.plus(additionalDuration)
        setYouTubeRestrictLock(state.lockMode, newUnlockTime)
        return true
    }

    suspend fun extendYouTubeRestrictLockUntil(newUnlockTime: Instant): Boolean {
        val state = youtubeRestrictStateFlow.first()
        if (state.lockMode != LockMode.TIMER && state.lockMode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime
        if (currentUnlockTime != null && newUnlockTime.isBefore(currentUnlockTime)) {
            return false
        }
        setYouTubeRestrictLock(state.lockMode, newUnlockTime)
        return true
    }

    // ==================== Incognito Disabled Setting ====================

    val incognitoDisabledStateFlow: Flow<LockableSettingState<Boolean>> = context.dataStore.data.map { preferences ->
        val disabled = preferences[incognitoDisabledKey] ?: false
        val lockMode = preferences[incognitoDisabledLockModeKey]?.let {
            try { LockMode.valueOf(it) } catch (e: IllegalArgumentException) { LockMode.UNLOCKED }
        } ?: LockMode.UNLOCKED
        val unlockTime = preferences[incognitoDisabledUnlockTimeKey]?.let { Instant.ofEpochMilli(it) }
        LockableSettingState(disabled, lockMode, unlockTime)
    }

    suspend fun setIncognitoDisabled(disabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[incognitoDisabledKey] = disabled
        }
    }

    suspend fun setIncognitoDisabledLock(mode: LockMode, unlockTime: Instant? = null) {
        context.dataStore.edit { preferences ->
            preferences[incognitoDisabledLockModeKey] = mode.name
            if (unlockTime != null) {
                preferences[incognitoDisabledUnlockTimeKey] = unlockTime.toEpochMilli()
            } else {
                preferences.remove(incognitoDisabledUnlockTimeKey)
            }
        }
    }

    suspend fun lockIncognitoDisabledForDuration(duration: Duration) {
        val unlockTime = Instant.now().plus(duration)
        setIncognitoDisabledLock(LockMode.TIMER, unlockTime)
    }

    suspend fun lockIncognitoDisabledUntil(unlockTime: Instant) {
        setIncognitoDisabledLock(LockMode.UNTIL_DATETIME, unlockTime)
    }

    suspend fun lockIncognitoDisabledForever() {
        setIncognitoDisabledLock(LockMode.FOREVER, null)
    }

    suspend fun unlockIncognitoDisabled() {
        setIncognitoDisabledLock(LockMode.UNLOCKED, null)
    }

    suspend fun checkAndClearExpiredIncognitoDisabledLock(): Boolean {
        val state = incognitoDisabledStateFlow.first()
        if (state.lockMode == LockMode.UNTIL_DATETIME || state.lockMode == LockMode.TIMER) {
            val unlockTime = state.unlockTime
            if (unlockTime != null && Instant.now().isAfter(unlockTime)) {
                unlockIncognitoDisabled()
                return true
            }
        }
        return false
    }

    suspend fun extendIncognitoDisabledLockByDuration(additionalDuration: Duration): Boolean {
        val state = incognitoDisabledStateFlow.first()
        if (state.lockMode != LockMode.TIMER && state.lockMode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime ?: Instant.now()
        val baseTime = if (Instant.now().isAfter(currentUnlockTime)) Instant.now() else currentUnlockTime
        val newUnlockTime = baseTime.plus(additionalDuration)
        setIncognitoDisabledLock(state.lockMode, newUnlockTime)
        return true
    }

    suspend fun extendIncognitoDisabledLockUntil(newUnlockTime: Instant): Boolean {
        val state = incognitoDisabledStateFlow.first()
        if (state.lockMode != LockMode.TIMER && state.lockMode != LockMode.UNTIL_DATETIME) {
            return false
        }
        val currentUnlockTime = state.unlockTime
        if (currentUnlockTime != null && newUnlockTime.isBefore(currentUnlockTime)) {
            return false
        }
        setIncognitoDisabledLock(state.lockMode, newUnlockTime)
        return true
    }
}
