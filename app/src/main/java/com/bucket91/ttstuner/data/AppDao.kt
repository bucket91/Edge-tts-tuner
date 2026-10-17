package com.bucket91.ttstuner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordCorrectionDao {
    @Query("SELECT * FROM word_corrections ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<WordCorrection>>

    @Query("SELECT * FROM word_corrections ORDER BY createdAt DESC")
    suspend fun getAll(): List<WordCorrection>

    @Query("SELECT * FROM word_corrections WHERE enabled = 1")
    suspend fun getAllEnabled(): List<WordCorrection>

    @Query("SELECT * FROM word_corrections WHERE enabled = 1")
    fun getAllEnabledFlow(): Flow<List<WordCorrection>>

    @Query("SELECT * FROM word_corrections WHERE id = :id")
    suspend fun getById(id: Long): WordCorrection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wordCorrection: WordCorrection): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WordCorrection>)

    @Update
    suspend fun update(wordCorrection: WordCorrection)

    @Delete
    suspend fun delete(wordCorrection: WordCorrection)

    @Query("DELETE FROM word_corrections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM word_corrections")
    suspend fun clearAll()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Rule>>

    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    suspend fun getAll(): List<Rule>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun getAllEnabled(): List<Rule>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY createdAt ASC")
    fun getAllEnabledFlow(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): Rule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: Rule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Rule>)

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clearAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 50")
    fun getNewest50Flow(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 50")
    suspend fun getNewest50(): List<HistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem): Long

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY createdAt DESC LIMIT 50)")
    suspend fun trimTo50()

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
