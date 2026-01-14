package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blacklisted_apps",
    indices = [Index("packageName", unique = true)]
)
data class BlacklistedApp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val reason: String? = null
)
