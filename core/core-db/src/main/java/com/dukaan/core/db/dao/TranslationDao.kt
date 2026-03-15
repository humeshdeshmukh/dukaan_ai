package com.dukaan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dukaan.core.db.entity.TranslationCacheEntity

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_cache WHERE languageCode = :code")
    suspend fun getTranslation(code: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslation(entity: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache WHERE languageCode = :code")
    suspend fun deleteTranslation(code: String)
}
