package com.tyler.selfcontrol.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.model.AppRule
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.WebsiteRule
import com.tyler.selfcontrol.data.repository.BlockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val isLocked: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class BlockEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val blockRepository: BlockRepository,
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
    }

    private fun loadBlock() {
        viewModelScope.launch {
            val blockWithRules = blockRepository.getBlockWithRulesById(blockId)
            if (blockWithRules != null) {
                val isLocked = blockRepository.isBlockLocked(blockId)
                _uiState.value = BlockEditUiState(
                    block = blockWithRules.block,
                    appRules = blockWithRules.appRules,
                    websiteRules = blockWithRules.websiteRules,
                    isLocked = isLocked,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
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
}
