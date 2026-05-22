package com.example.data

import kotlinx.coroutines.flow.Flow

class FluidWidgetsRepository(
    private val widgetDao: WidgetDao,
    private val fluidPresetDao: FluidPresetDao
) {
    val allWidgets: Flow<List<WidgetConfig>> = widgetDao.getAllWidgets()
    val allPresets: Flow<List<FluidPreset>> = fluidPresetDao.getAllPresets()

    suspend fun insertWidget(config: WidgetConfig): Long {
        return widgetDao.insertWidget(config)
    }

    suspend fun updateWidget(config: WidgetConfig) {
        widgetDao.updateWidget(config)
    }

    suspend fun deleteWidget(config: WidgetConfig) {
        widgetDao.deleteWidget(config)
    }

    suspend fun deleteAllWidgets() {
        widgetDao.deleteAllWidgets()
    }

    suspend fun insertPreset(preset: FluidPreset): Long {
        return fluidPresetDao.insertPreset(preset)
    }

    suspend fun deletePreset(preset: FluidPreset) {
        fluidPresetDao.deletePreset(preset)
    }

    suspend fun deleteAllPresets() {
        fluidPresetDao.deleteAllPresets()
    }
}
