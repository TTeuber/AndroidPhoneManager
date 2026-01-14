package com.tyler.selfcontrol.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.BlacklistedApp
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllowlistManagementViewModel @Inject constructor(
    private val repository: AppInstallationRepository
) : ViewModel() {

    val allowedApps: StateFlow<List<AllowedApp>> = repository.getAllowedApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val blacklistedApps: StateFlow<List<BlacklistedApp>> = repository.getBlacklistedApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeFromAllowlist(packageName: String) {
        viewModelScope.launch {
            repository.removeFromAllowlist(packageName)
        }
    }

    fun removeFromBlacklist(packageName: String) {
        viewModelScope.launch {
            repository.removeFromBlacklist(packageName)
        }
    }

    fun addToBlacklist(packageName: String, appName: String, reason: String? = null) {
        viewModelScope.launch {
            repository.addToBlacklist(packageName, appName, reason)
        }
    }
}
