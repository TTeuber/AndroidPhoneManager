package com.tyler.selfcontrol.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import com.tyler.selfcontrol.domain.AppInstallationManager
import com.tyler.selfcontrol.domain.PlayStoreParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppInstallationUiState(
    val playStoreUrl: String = "",
    val isLoading: Boolean = false,
    val parsedInfo: PlayStoreParser.ParsedAppInfo? = null,
    val decision: AppInstallationManager.InstallationDecision? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AppInstallationViewModel @Inject constructor(
    private val appInstallationManager: AppInstallationManager,
    private val repository: AppInstallationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppInstallationUiState())
    val uiState: StateFlow<AppInstallationUiState> = _uiState.asStateFlow()

    val pendingRequests: StateFlow<List<CooldownRequest>> = repository.getActiveRequests()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            playStoreUrl = url,
            error = null,
            successMessage = null,
            decision = null,
            parsedInfo = null
        )
    }

    fun evaluateUrl() {
        val url = _uiState.value.playStoreUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a Play Store URL")
            return
        }

        if (!url.contains("play.google.com") && !url.contains("market://")) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid Play Store URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val decision = appInstallationManager.evaluateInstallation(url)

            val parsedInfo = when (decision) {
                is AppInstallationManager.InstallationDecision.Allowed -> decision.appInfo
                is AppInstallationManager.InstallationDecision.RequiresCooldown -> decision.appInfo
                is AppInstallationManager.InstallationDecision.AlreadyAllowed -> decision.appInfo
                else -> null
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                decision = decision,
                parsedInfo = parsedInfo
            )
        }
    }

    fun requestCooldown() {
        val decision = _uiState.value.decision
        if (decision !is AppInstallationManager.InstallationDecision.RequiresCooldown) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                appInstallationManager.createCooldownRequest(
                    decision.appInfo,
                    _uiState.value.playStoreUrl
                )
                _uiState.value = AppInstallationUiState(
                    successMessage = "Cooldown request created. Check back tomorrow between 3-6 PM."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to create cooldown request"
                )
            }
        }
    }

    fun addToAllowlistAndInstall() {
        val info = _uiState.value.parsedInfo ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = appInstallationManager.addToAllowlistImmediate(
                info.packageName,
                info.appName
            )

            result.onSuccess {
                appInstallationManager.openPlayStoreForInstall(info.packageName)
                _uiState.value = AppInstallationUiState(
                    successMessage = "Opening Play Store..."
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to add to allowlist"
                )
            }
        }
    }

    fun approveRequest(requestId: Long) {
        viewModelScope.launch {
            val result = appInstallationManager.approveRequest(requestId)
            result.onSuccess { request ->
                appInstallationManager.openPlayStoreForInstall(request.packageName)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Opening Play Store for ${request.appName}..."
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to approve request"
                )
            }
        }
    }

    fun cancelRequest(requestId: Long) {
        viewModelScope.launch {
            appInstallationManager.cancelRequest(requestId)
        }
    }

    fun clearState() {
        _uiState.value = AppInstallationUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
