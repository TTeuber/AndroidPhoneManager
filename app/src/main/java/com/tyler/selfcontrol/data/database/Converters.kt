package com.tyler.selfcontrol.data.database

import androidx.room.TypeConverter
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.LockMode
import java.time.Instant

class Converters {

    @TypeConverter
    fun fromBlockState(state: BlockState): String = state.name

    @TypeConverter
    fun toBlockState(value: String): BlockState = BlockState.valueOf(value)

    @TypeConverter
    fun fromLockMode(mode: LockMode): String = mode.name

    @TypeConverter
    fun toLockMode(value: String): LockMode = LockMode.valueOf(value)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}
