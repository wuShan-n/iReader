package com.ireader.core.database.annotation

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["updatedAtEpochMs"]),
        Index(value = ["type"])
    ]
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val type: String,
    val anchorType: String,
    val rangeStartLocatorJson: String? = null,
    val rangeEndLocatorJson: String? = null,
    val pageLocatorJson: String? = null,
    val rectsJson: String? = null,
    val content: String? = null,
    val styleJson: String,
    val extraJson: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

object AnnotationAnchorType {
    const val REFLOW_RANGE: String = "REFLOW_RANGE"
    const val FIXED_RECTS: String = "FIXED_RECTS"
}
