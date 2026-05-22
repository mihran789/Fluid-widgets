package com.example.ui.fluid

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FluidViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FluidWidgetsRepository(database.widgetDao(), database.fluidPresetDao())

    // 1. Reactive Data Streams from SQLite Room DB
    val widgets: StateFlow<List<WidgetConfig>> = repository.allWidgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val presets: StateFlow<List<FluidPreset>> = repository.allPresets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. Active Physics & Particle Simulation Configuration Settings (Transient State)
    private val _viscosity = MutableStateFlow(0.04f)
    val viscosity: StateFlow<Float> = _viscosity.asStateFlow()

    private val _gravityX = MutableStateFlow(0f)
    val gravityX: StateFlow<Float> = _gravityX.asStateFlow()

    private val _gravityY = MutableStateFlow(0.8f)
    val gravityY: StateFlow<Float> = _gravityY.asStateFlow()

    private val _trailStyle = MutableStateFlow("Neon Glow")
    val trailStyle: StateFlow<String> = _trailStyle.asStateFlow()

    private val _neonColorHex = MutableStateFlow("#00FFCC")
    val neonColorHex: StateFlow<String> = _neonColorHex.asStateFlow()

    private val _particleCount = MutableStateFlow(180)
    val particleCount: StateFlow<Int> = _particleCount.asStateFlow()

    // Screen-space bounding boxes reported from Composable nodes
    private val _widgetBounds = MutableStateFlow<Map<Int, Rect>>(emptyMap())
    val widgetBounds: StateFlow<Map<Int, Rect>> = _widgetBounds.asStateFlow()

    fun updateViscosity(value: Float) {
        _viscosity.value = value.coerceIn(0.01f, 0.2f)
    }

    fun updateGravity(x: Float, y: Float) {
        _gravityX.value = x.coerceIn(-5f, 5f)
        _gravityY.value = y.coerceIn(-5f, 5f)
    }

    fun updateTrailStyle(style: String) {
        _trailStyle.value = style
    }

    fun updateNeonColor(hex: String) {
        _neonColorHex.value = hex
    }

    fun updateParticleCount(count: Int) {
        _particleCount.value = count.coerceIn(50, 450)
    }

    /**
     * Map screen-space bounding boxes of widgets to resolve collision physics.
     */
    fun setWidgetBounds(widgetId: Int, rect: Rect) {
        val current = _widgetBounds.value.toMutableMap()
        current[widgetId] = rect
        _widgetBounds.value = current
    }

    /**
     * Clear bounds for removed widgets.
     */
    fun removeWidgetBounds(widgetId: Int) {
        val current = _widgetBounds.value.toMutableMap()
        current.remove(widgetId)
        _widgetBounds.value = current
    }

    // 3. Database mutation operations
    fun moveWidget(widgetId: Int, x: Float, y: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val matching = widgets.value.find { it.id == widgetId }
            if (matching != null) {
                repository.updateWidget(matching.copy(posX = x, posY = y))
            }
        }
    }

    fun addNewWidget(type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = widgets.value.size
            val config = WidgetConfig(
                type = type,
                posX = 80f + (count * 40f) % 300f,
                posY = 150f + (count * 60f) % 500f,
                size = 1.0f,
                colorHex = _neonColorHex.value,
                customLabel = when (type) {
                    "CLOCK" -> "Synchronized Chronology Matrix"
                    "MUSIC" -> "Dynamic Acoustic Fuel Chamber"
                    "WEATHER" -> "Integrated Weather Analyzer"
                    else -> "Battery Power Inductor"
                },
                isFloating = true
            )
            repository.insertWidget(config)
        }
    }

    fun deleteWidget(config: WidgetConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWidget(config)
            removeWidgetBounds(config.id)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllWidgets()
            _widgetBounds.value = emptyMap()
            
            // Re-inflate default configurations
            repository.insertWidget(WidgetConfig(type = "CLOCK", posX = 150f, posY = 200f, colorHex = _neonColorHex.value, customLabel = "Synchronized Chronology Matrix"))
            repository.insertWidget(WidgetConfig(type = "MUSIC", posX = 150f, posY = 450f, colorHex = _neonColorHex.value, customLabel = "Dynamic Acoustic Fuel Chamber"))
        }
    }

    /**
     * Apply preset properties into our simulation engine parameters dynamically.
     */
    fun applyPreset(preset: FluidPreset) {
        _viscosity.value = preset.viscosity
        _gravityY.value = preset.gravityStrength
        _gravityX.value = 0f
        _trailStyle.value = preset.trailStyle
        _neonColorHex.value = preset.colorHex
        _particleCount.value = preset.particleCount

        // Propagate current color to widgets as well to coordinate the appearance
        viewModelScope.launch(Dispatchers.IO) {
            widgets.value.forEach { w ->
                repository.updateWidget(w.copy(colorHex = preset.colorHex))
            }
        }
    }

    /**
     * Store customized simulation combinations as a user preset.
     */
    fun saveAsNewPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val preset = FluidPreset(
                name = name,
                particleCount = _particleCount.value,
                viscosity = _viscosity.value,
                gravityStrength = _gravityY.value,
                trailStyle = _trailStyle.value,
                colorHex = _neonColorHex.value
            )
            repository.insertPreset(preset)
        }
    }

    fun deletePreset(preset: FluidPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePreset(preset)
        }
    }
}
