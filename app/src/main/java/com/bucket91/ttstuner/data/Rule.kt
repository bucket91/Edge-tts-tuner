package com.bucket91.ttstuner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
