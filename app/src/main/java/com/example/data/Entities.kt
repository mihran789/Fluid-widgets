package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_configs")
data class WidgetConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "CLOCK", "MUSIC", "WEATHER", "BATTERY"
    val posX: Float,
    val posY: Float,
    val size: Float = 1.0f,
    val colorHex: String = "#00FFCC", // Cyber Cyan default
    val customLabel: String = "",
    val isFloating: Boolean = true
)

@Entity(tableName = "fluid_presets")
data class FluidPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val particleCount: Int = 200,
    val viscosity: Float = 0.05f,
    val gravityStrength: Float = 0.5f,
    val trailStyle: String = "Neon Glow", // "Neon Glow", "Water Droplet", "Electric Sparkle"
    val colorHex: String = "#FF007F" // Neon Pink default
)
