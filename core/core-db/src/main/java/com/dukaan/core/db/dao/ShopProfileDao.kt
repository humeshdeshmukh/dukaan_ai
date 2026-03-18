package com.dukaan.core.db.dao

import androidx.room.*
import com.dukaan.core.db.entity.ShopProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ShopProfileEntity)

    @Query("SELECT * FROM shop_profile WHERE id = 1")
    fun getProfile(): Flow<ShopProfileEntity?>

    @Query("SELECT * FROM shop_profile WHERE id = 1")
    suspend fun getProfileOnce(): ShopProfileEntity?

    @Query("UPDATE shop_profile SET isDarkTheme = :isDark WHERE id = 1")
    suspend fun updateDarkThemeRaw(isDark: Boolean): Int

    @Query("UPDATE shop_profile SET languageCode = :code WHERE id = 1")
    suspend fun updateLanguageRaw(code: String): Int

    // Helper methods that ensure row exists before updating
    suspend fun updateDarkTheme(isDark: Boolean) {
        if (updateDarkThemeRaw(isDark) == 0) {
            // No row existed, create default profile with this setting
            upsertProfile(ShopProfileEntity(isDarkTheme = isDark))
        }
    }

    suspend fun updateLanguage(code: String) {
        if (updateLanguageRaw(code) == 0) {
            // No row existed, create default profile with this setting
            upsertProfile(ShopProfileEntity(languageCode = code))
        }
    }
}
