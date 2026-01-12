package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "website_rules",
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
data class WebsiteRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockId: Long,
    val domain: String,
    val path: String? = null,
    val isAllowed: Boolean = false  // true = allowlist entry, false = blocklist entry
)
