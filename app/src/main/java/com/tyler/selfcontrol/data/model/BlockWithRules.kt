package com.tyler.selfcontrol.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class BlockWithRules(
    @Embedded
    val block: Block,

    @Relation(
        parentColumn = "id",
        entityColumn = "blockId"
    )
    val appRules: List<AppRule>,

    @Relation(
        parentColumn = "id",
        entityColumn = "blockId"
    )
    val websiteRules: List<WebsiteRule>,

    @Relation(
        parentColumn = "id",
        entityColumn = "blockId"
    )
    val lock: Lock?
)
