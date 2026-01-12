package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BlockState {
    ALWAYS_ON,
    SCHEDULED
}

@Entity(tableName = "blocks")
data class Block(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = false,
    val state: BlockState = BlockState.ALWAYS_ON,
    val isScheduleActive: Boolean = true // For SCHEDULED blocks, tracks if schedule is currently active
)
