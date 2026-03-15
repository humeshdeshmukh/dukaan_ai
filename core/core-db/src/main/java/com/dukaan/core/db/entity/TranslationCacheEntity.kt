package com.dukaan.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_cache")
data class TranslationCacheEntity(
    @PrimaryKey val languageCode: String,
    val translationsJson: String,
    val timestamp: Long
)
