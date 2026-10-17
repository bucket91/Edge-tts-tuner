package com.bucket91.ttstuner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "word_corrections")
data class WordCorrection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val base: String,
    val preferred: String,
    val note: String = "",
    val wholeWord: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
