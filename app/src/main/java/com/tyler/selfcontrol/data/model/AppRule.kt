package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_rules",
    foreignKeys = [
        ForeignKey(
            entity = Block::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("blockId")]
)
data class AppRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockId: Long,
    val packageName: String
)
