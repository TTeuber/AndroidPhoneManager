package com.tyler.selfcontrol.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.BlockWithRules
import com.tyler.selfcontrol.data.repository.BlockRepository
import com.tyler.selfcontrol.domain.LockManager
import com.tyler.selfcontrol.domain.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val blockRepository: BlockRepository,
    private val lockManager: LockManager,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    val blocks: StateFlow<List<BlockWithRules>> = blockRepository.getAllBlocksWithRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createBlock(name: String) {
        viewModelScope.launch {
            blockRepository.createBlock(name)
        }
    }

    fun toggleBlockEnabled(block: Block) {
        viewModelScope.launch {
            // Check if block is locked before allowing disable (uses LockManager which checks expiry)
            if (block.isEnabled && lockManager.isBlockLocked(block.id)) {
                // Block is locked, cannot disable
                return@launch
            }

            if (block.isEnabled) {
                // Turning OFF - always allowed (user override)
                blockRepository.setBlockEnabled(block.id, false)
            } else {
                // Turning ON
                if (block.state == BlockState.SCHEDULED) {
                    // For scheduled blocks, run schedule check to determine actual state
                    scheduleManager.processSchedules()
                } else {
                    // For always-on blocks, just enable
                    blockRepository.setBlockEnabled(block.id, true)
                }
            }
        }
    }

    fun deleteBlock(blockId: Long) {
        viewModelScope.launch {
            // Check if block is locked (uses LockManager which checks expiry)
            if (lockManager.isBlockLocked(blockId)) {
                return@launch
            }
            blockRepository.deleteBlock(blockId)
        }
    }
}
