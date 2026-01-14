package com.tyler.selfcontrol.data.database

import androidx.room.TypeConverter
import com.tyler.selfcontrol.data.model.AllowedAppSource
import com.tyler.selfcontrol.data.model.AppCategory
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.CooldownStatus
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

    @TypeConverter
    fun fromAllowedAppSource(source: AllowedAppSource): String = source.name

    @TypeConverter
    fun toAllowedAppSource(value: String): AllowedAppSource = AllowedAppSource.valueOf(value)

    @TypeConverter
    fun fromCooldownStatus(status: CooldownStatus): String = status.name

    @TypeConverter
    fun toCooldownStatus(value: String): CooldownStatus = CooldownStatus.valueOf(value)

    @TypeConverter
    fun fromAppCategory(category: AppCategory): String = category.name

    @TypeConverter
    fun toAppCategory(value: String): AppCategory = AppCategory.valueOf(value)
}
