package com.bucket91.ttstuner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val input: String,
    val stage1: String,
    val stage2: String,
    val `final`: String,
    val createdAt: Long = System.currentTimeMillis()
)
