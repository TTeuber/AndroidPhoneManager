package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class LockMode {
    UNLOCKED,
    UNTIL_DATETIME,
    TIMER,
    FOREVER
}

@Entity(
    tableName = "locks",
    foreignKeys = [
        ForeignKey(
            entity = Block::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("blockId", unique = true)]
)
data class Lock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockId: Long,
    val mode: LockMode = LockMode.UNLOCKED,
    val unlockTime: Instant? = null  // Used for UNTIL_DATETIME and TIMER modes
)
