package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widget_configs ORDER BY id ASC")
    fun getAllWidgets(): Flow<List<WidgetConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidget(config: WidgetConfig): Long

    @Update
    suspend fun updateWidget(config: WidgetConfig)

    @Delete
    suspend fun deleteWidget(config: WidgetConfig)

    @Query("DELETE FROM widget_configs")
    suspend fun deleteAllWidgets()
}

@Dao
interface FluidPresetDao {
    @Query("SELECT * FROM fluid_presets ORDER BY id ASC")
    fun getAllPresets(): Flow<List<FluidPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FluidPreset): Long

    @Delete
    suspend fun deletePreset(preset: FluidPreset)

    @Query("DELETE FROM fluid_presets")
    suspend fun deleteAllPresets()
}
