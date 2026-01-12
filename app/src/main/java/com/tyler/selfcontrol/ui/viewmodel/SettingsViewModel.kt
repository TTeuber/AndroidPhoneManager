package com.tyler.selfcontrol.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.worker.UnlockWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val devModeEnabled: Flow<Boolean> = settingsDataStore.devModeFlow

    fun setDevMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDevMode(enabled)
            // Reschedule the unlock worker with appropriate interval
            UnlockWorker.schedule(context, devMode = enabled)
        }
    }
}
