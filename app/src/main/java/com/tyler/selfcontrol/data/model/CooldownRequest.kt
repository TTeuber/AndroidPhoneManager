package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class CooldownStatus {
    PENDING,        // Waiting for window to open
    WINDOW_OPEN,    // Window is active, user can approve
    APPROVED,       // User approved, installation allowed
    EXPIRED,        // Window passed without approval
    CANCELLED       // User cancelled the request
}

enum class AppCategory {
    UNRESTRICTED,   // Can install immediately
    SOCIAL,         // Requires cooldown
    ENTERTAINMENT,  // Requires cooldown
    VIDEO_PLAYERS,  // Requires cooldown
    GAMES,          // Requires cooldown
    BROWSERS        // Requires cooldown (blocked entirely)
}

@Entity(
    tableName = "cooldown_requests",
    indices = [Index("packageName")]
)
data class CooldownRequest(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val playStoreUrl: String,
    val category: AppCategory,
    val requestedAt: Instant = Instant.now(),
    val windowStart: Instant,   // 3pm next day
    val windowEnd: Instant,     // 6pm next day
    val status: CooldownStatus = CooldownStatus.PENDING
)
