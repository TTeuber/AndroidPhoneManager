package com.tyler.selfcontrol.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.BlacklistedApp
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import com.tyler.selfcontrol.domain.PlayStoreParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for adding an app to the blacklist via Play Store URL.
 */
data class AddToBlacklistState(
    val isLoading: Boolean = false,
    val parsedApp: PlayStoreParser.ParsedAppInfo? = null,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class AllowlistManagementViewModel @Inject constructor(
    private val repository: AppInstallationRepository,
    private val playStoreParser: PlayStoreParser
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

    private val _addToBlacklistState = MutableStateFlow(AddToBlacklistState())
    val addToBlacklistState: StateFlow<AddToBlacklistState> = _addToBlacklistState.asStateFlow()

    fun removeFromAllowlist(packageName: String) {
        viewModelScope.launch {
            repository.removeFromAllowlist(packageName)
        }
    }

    /**
     * Parse a Play Store URL to get app info for blacklisting.
     */
    fun parsePlayStoreUrl(url: String) {
        viewModelScope.launch {
            _addToBlacklistState.value = AddToBlacklistState(isLoading = true)

            val result = playStoreParser.parsePlayStoreUrl(url)
            result.fold(
                onSuccess = { appInfo ->
                    // Check if already blacklisted
                    if (repository.isBlacklisted(appInfo.packageName)) {
                        _addToBlacklistState.value = AddToBlacklistState(
                            error = "${appInfo.appName} is already on the blacklist"
                        )
                    } else {
                        _addToBlacklistState.value = AddToBlacklistState(parsedApp = appInfo)
                    }
                },
                onFailure = { error ->
                    _addToBlacklistState.value = AddToBlacklistState(
                        error = error.message ?: "Failed to parse Play Store URL"
                    )
                }
            )
        }
    }

    /**
     * Confirm adding the parsed app to the blacklist.
     */
    fun confirmAddToBlacklist(reason: String? = null) {
        val parsedApp = _addToBlacklistState.value.parsedApp ?: return

        viewModelScope.launch {
            repository.addToBlacklist(
                packageName = parsedApp.packageName,
                appName = parsedApp.appName,
                reason = reason ?: "User added"
            )
            _addToBlacklistState.value = AddToBlacklistState(success = true)
        }
    }

    /**
     * Reset the add to blacklist state.
     */
    fun resetAddToBlacklistState() {
        _addToBlacklistState.value = AddToBlacklistState()
    }
}
