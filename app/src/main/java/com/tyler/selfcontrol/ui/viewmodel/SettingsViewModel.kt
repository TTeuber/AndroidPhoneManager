package com.tyler.selfcontrol.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val devModeEnabled: Flow<Boolean> = settingsDataStore.devModeFlow

    fun setDevMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDevMode(enabled)
        }
    }
}
