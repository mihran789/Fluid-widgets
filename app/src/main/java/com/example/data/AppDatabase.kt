package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [WidgetConfig::class, FluidPreset::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun widgetDao(): WidgetDao
    abstract fun fluidPresetDao(): FluidPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fluid_widgets_db"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.widgetDao(), database.fluidPresetDao())
                }
            }
        }

        suspend fun populateDatabase(widgetDao: WidgetDao, presetDao: FluidPresetDao) {
            // Default widgets pre-loaded
            widgetDao.insertWidget(
                WidgetConfig(
                    type = "CLOCK",
                    posX = 150f,
                    posY = 200f,
                    size = 1.0f,
                    colorHex = "#00FFCC", // Mint
                    customLabel = "Dynamic Temporal Capsule",
                    isFloating = true
                )
            )
            widgetDao.insertWidget(
                WidgetConfig(
                    type = "MUSIC",
                    posX = 150f,
                    posY = 430f,
                    size = 1.0f,
                    colorHex = "#FF007F", // Neon Pink
                    customLabel = "Retrowave Antigravity Radio",
                    isFloating = true
                )
            )
            widgetDao.insertWidget(
                WidgetConfig(
                    type = "WEATHER",
                    posX = 150f,
                    posY = 680f,
                    size = 1.0f,
                    colorHex = "#39FF14", // Acid Green
                    customLabel = "Biosphere Atmospheric Probe",
                    isFloating = true
                )
            )

            // Default fluid presets preloaded
            presetDao.insertPreset(
                FluidPreset(
                    name = "Cosmic Neon Sparkle",
                    particleCount = 180,
                    viscosity = 0.04f,
                    gravityStrength = 0.6f,
                    trailStyle = "Electric Sparkle",
                    colorHex = "#FF007F" // Neon Pink
                )
            )
            presetDao.insertPreset(
                FluidPreset(
                    name = "Hydrologic Cyan Droplets",
                    particleCount = 250,
                    viscosity = 0.08f,
                    gravityStrength = 0.3f,
                    trailStyle = "Water Droplet",
                    colorHex = "#00FFCC" // Cyber Cyan
                )
            )
            presetDao.insertPreset(
                FluidPreset(
                    name = "Gravity Solar Flare",
                    particleCount = 150,
                    viscosity = 0.02f,
                    gravityStrength = 1.2f,
                    trailStyle = "Neon Glow",
                    colorHex = "#FF4500" // Solar Orange-Red
                )
            )
        }
    }
}
