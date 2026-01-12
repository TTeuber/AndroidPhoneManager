package com.tyler.selfcontrol.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.model.AppRule
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.model.WebsiteRule
import com.tyler.selfcontrol.data.repository.BlockRepository
import com.tyler.selfcontrol.domain.LockManager
import com.tyler.selfcontrol.domain.ScheduleManager
import java.time.LocalTime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

data class BlockEditUiState(
    val block: Block? = null,
    val appRules: List<AppRule> = emptyList(),
    val websiteRules: List<WebsiteRule> = emptyList(),
    val lock: Lock? = null,
    val isLocked: Boolean = false,
    val lockStatusText: String = "Not locked",
    val schedule: Schedule? = null,
    val scheduleStatusText: String = "No schedule",
    val isLoading: Boolean = true,
    val lockError: String? = null
)

@HiltViewModel
class BlockEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val blockRepository: BlockRepository,
    private val lockManager: LockManager,
    private val scheduleManager: ScheduleManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val blockId: Long = savedStateHandle.get<Long>("blockId") ?: 0L

    private val _uiState = MutableStateFlow(BlockEditUiState())
    val uiState: StateFlow<BlockEditUiState> = _uiState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    init {
        loadBlock()
        loadInstalledApps()
        startLockStatusUpdater()
    }

    private fun loadBlock() {
        viewModelScope.launch {
            val blockWithRules = blockRepository.getBlockWithRulesById(blockId)
            if (blockWithRules != null) {
                val lock = blockRepository.getLockForBlock(blockId)
                val isLocked = lockManager.isBlockLocked(blockId)
                val schedule = blockRepository.getScheduleForBlock(blockId)
                val scheduleStatusText = scheduleManager.getScheduleStatusDescription(blockId)
                _uiState.value = BlockEditUiState(
                    block = blockWithRules.block,
                    appRules = blockWithRules.appRules,
                    websiteRules = blockWithRules.websiteRules,
                    lock = lock,
                    isLocked = isLocked,
                    lockStatusText = lockManager.getLockStatusDescription(lock),
                    schedule = schedule,
                    scheduleStatusText = scheduleStatusText,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun startLockStatusUpdater() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000) // Update every minute
                refreshLockStatus()
            }
        }
    }

    private suspend fun refreshLockStatus() {
        val lock = blockRepository.getLockForBlock(blockId)
        val isLocked = lockManager.isBlockLocked(blockId)
        val schedule = blockRepository.getScheduleForBlock(blockId)
        val scheduleStatusText = scheduleManager.getScheduleStatusDescription(blockId)
        _uiState.value = _uiState.value.copy(
            lock = lock,
            isLocked = isLocked,
            lockStatusText = lockManager.getLockStatusDescription(lock),
            schedule = schedule,
            scheduleStatusText = scheduleStatusText
        )
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    // Filter out system apps that don't have a launcher icon
                    pm.getLaunchIntentForPackage(app.packageName) != null
                }
                .map { app ->
                    InstalledApp(
                        packageName = app.packageName,
                        appName = app.loadLabel(pm).toString(),
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.appName.lowercase() }

            _installedApps.value = apps
        }
    }

    fun updateBlockName(name: String) {
        val block = _uiState.value.block ?: return
        viewModelScope.launch {
            val updatedBlock = block.copy(name = name)
            blockRepository.updateBlock(updatedBlock)
            _uiState.value = _uiState.value.copy(block = updatedBlock)
        }
    }

    fun addApp(packageName: String) {
        viewModelScope.launch {
            blockRepository.addAppRule(blockId, packageName)
            refreshAppRules()
        }
    }

    fun removeApp(packageName: String) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            blockRepository.removeAppRule(blockId, packageName)
            refreshAppRules()
        }
    }

    fun addWebsite(domain: String, path: String? = null) {
        viewModelScope.launch {
            blockRepository.addWebsiteRule(
                blockId = blockId,
                domain = domain.lowercase().trim(),
                path = path?.trim()?.takeIf { it.isNotEmpty() },
                isAllowed = false
            )
            refreshWebsiteRules()
        }
    }

    fun removeWebsite(ruleId: Long) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            blockRepository.removeWebsiteRule(ruleId)
            refreshWebsiteRules()
        }
    }

    private suspend fun refreshAppRules() {
        val rules = blockRepository.getAppRulesForBlockOnce(blockId)
        _uiState.value = _uiState.value.copy(appRules = rules)
    }

    private suspend fun refreshWebsiteRules() {
        val rules = blockRepository.getWebsiteRulesForBlockOnce(blockId)
        _uiState.value = _uiState.value.copy(websiteRules = rules)
    }

    fun getAppNameForPackage(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    // Lock operations

    fun lockUntilDateTime(unlockTime: Instant) {
        viewModelScope.launch {
            val result = lockManager.lockUntilDateTime(blockId, unlockTime)
            result.onSuccess {
                refreshLockStatus()
                _uiState.value = _uiState.value.copy(lockError = null)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(lockError = e.message)
            }
        }
    }

    fun lockForDuration(duration: Duration) {
        viewModelScope.launch {
            val result = lockManager.lockForDuration(blockId, duration)
            result.onSuccess {
                refreshLockStatus()
                _uiState.value = _uiState.value.copy(lockError = null)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(lockError = e.message)
            }
        }
    }

    fun lockForever() {
        viewModelScope.launch {
            val result = lockManager.lockForever(blockId)
            result.onSuccess {
                refreshLockStatus()
                _uiState.value = _uiState.value.copy(lockError = null)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(lockError = e.message)
            }
        }
    }

    fun clearLockError() {
        _uiState.value = _uiState.value.copy(lockError = null)
    }

    // Schedule operations

    fun setBlockState(state: BlockState) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            blockRepository.setBlockState(blockId, state)

            when (state) {
                BlockState.ALWAYS_ON -> {
                    // Keep current enabled state (user controls via toggle)
                }
                BlockState.SCHEDULED -> {
                    // Create default schedule if none exists
                    val existingSchedule = blockRepository.getScheduleForBlock(blockId)
                    if (existingSchedule == null) {
                        // Default: weekdays 9:00 - 17:00
                        blockRepository.setSchedule(
                            blockId,
                            daysOfWeek = Schedule.WEEKDAYS,
                            startTimeMinutes = 9 * 60,
                            endTimeMinutes = 17 * 60
                        )
                    }
                    // Process schedule immediately to set correct enabled state
                    scheduleManager.processSchedules()
                }
            }
            refreshBlock()
        }
    }

    fun setSchedule(daysOfWeek: Int, startTime: LocalTime, endTime: LocalTime) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            val startMinutes = Schedule.localTimeToMinutes(startTime)
            val endMinutes = Schedule.localTimeToMinutes(endTime)
            blockRepository.setSchedule(blockId, daysOfWeek, startMinutes, endMinutes)
            // Process schedule immediately to update enabled state
            scheduleManager.processSchedules()
            refreshBlock()
        }
    }

    fun toggleDay(dayBit: Int) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            val currentSchedule = _uiState.value.schedule ?: return@launch
            val newDays = currentSchedule.daysOfWeek xor dayBit
            blockRepository.setSchedule(
                blockId,
                newDays,
                currentSchedule.startTimeMinutes,
                currentSchedule.endTimeMinutes
            )
            scheduleManager.processSchedules()
            refreshBlock()
        }
    }

    fun setScheduleStartTime(time: LocalTime) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            val currentSchedule = _uiState.value.schedule ?: return@launch
            val startMinutes = Schedule.localTimeToMinutes(time)
            blockRepository.setSchedule(
                blockId,
                currentSchedule.daysOfWeek,
                startMinutes,
                currentSchedule.endTimeMinutes
            )
            scheduleManager.processSchedules()
            refreshBlock()
        }
    }

    fun setScheduleEndTime(time: LocalTime) {
        if (_uiState.value.isLocked) return
        viewModelScope.launch {
            val currentSchedule = _uiState.value.schedule ?: return@launch
            val endMinutes = Schedule.localTimeToMinutes(time)
            blockRepository.setSchedule(
                blockId,
                currentSchedule.daysOfWeek,
                currentSchedule.startTimeMinutes,
                endMinutes
            )
            scheduleManager.processSchedules()
            refreshBlock()
        }
    }

    private suspend fun refreshBlock() {
        val block = blockRepository.getBlockById(blockId)
        val schedule = blockRepository.getScheduleForBlock(blockId)
        val scheduleStatusText = scheduleManager.getScheduleStatusDescription(blockId)
        _uiState.value = _uiState.value.copy(
            block = block,
            schedule = schedule,
            scheduleStatusText = scheduleStatusText
        )
    }
}
