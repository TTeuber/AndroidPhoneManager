package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class AllowedAppSource {
    PRE_INSTALLED,      // Was on device before app setup
    USER_ADDED,         // User explicitly allowed
    COOLDOWN_APPROVED   // Went through cooldown process
}

@Entity(
    tableName = "allowed_apps",
    indices = [Index("packageName", unique = true)]
)
data class AllowedApp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val addedAt: Instant = Instant.now(),
    val source: AllowedAppSource = AllowedAppSource.USER_ADDED
)
