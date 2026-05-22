package com.example.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.example.data.WidgetConfig
import kotlin.random.Random

data class FluidParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var alpha: Float = 1.0f,
    var color: Color = Color.Cyan,
    var life: Float = 1.0f, // 1.0 down to 0.0 for ephemeral trails/sparkles
    val isStatic: Boolean = false
)

data class PhysicsConfig(
    val viscosity: Float = 0.05f,   // Drag coefficient (0 = no resistance, 0.2 = thick liquid)
    val gravityX: Float = 0f,      // Horizontal gravity force
    val gravityY: Float = 0.8f,    // Vertical gravity force
    val trailStyle: String = "Neon Glow", // "Neon Glow", "Water Droplet", "Electric Sparkle"
    val colorHex: String = "#00FFCC",     // Theme neon color
    val particleCount: Int = 180,
    val attractionStrength: Float = 0.02f // Keep particles within fluid cluster
)

class FluidSimulationEngine {
    private val random = Random(System.currentTimeMillis())
    val particles = mutableListOf<FluidParticle>()
    val persistentExplosions = mutableListOf<FluidParticle>() // Ephemeral splash particles

    // Color conversion utility
    fun hexToColor(hex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            Color(0xFF00FFCC)
        }
    }

    /**
     * Reinitialize particle system based on engine configuration and viewport bounds.
     */
    fun initialize(width: Float, height: Float, config: PhysicsConfig) {
        particles.clear()
        persistentExplosions.clear()
        val baseColor = hexToColor(config.colorHex)

        val count = config.particleCount.coerceIn(50, 400)
        for (i in 0 until count) {
            // Distribute particles in a central cloud
            val px = width * 0.5f + (random.nextFloat() - 0.5f) * width * 0.4f
            val py = height * 0.5f + (random.nextFloat() - 0.5f) * height * 0.4f
            
            // Add subtle random color variation for visual depth
            val colorVar = Color(
                red = (baseColor.red + (random.nextFloat() - 0.5f) * 0.2f).coerceIn(0f, 1f),
                green = (baseColor.green + (random.nextFloat() - 0.5f) * 0.1f).coerceIn(0f, 1f),
                blue = (baseColor.blue + (random.nextFloat() - 0.5f) * 0.2f).coerceIn(0f, 1f),
                alpha = 0.8f
            )

            particles.add(
                FluidParticle(
                    x = px.coerceIn(0f, width),
                    y = py.coerceIn(0f, height),
                    vx = (random.nextFloat() - 0.5f) * 4f,
                    vy = (random.nextFloat() - 0.5f) * 4f,
                    size = random.nextFloat() * 8f + 4f, // 4dp to 12dp
                    color = colorVar,
                    life = 1.0f
                )
            )
        }
    }

    /**
     * Create floating glowing particles splash upon tapping or dragging near widgets.
     */
    fun createSplash(x: Float, y: Float, count: Int, config: PhysicsConfig) {
        val baseColor = hexToColor(config.colorHex)
        for (i in 0 until count) {
            val angle = random.nextFloat() * 2.0f * Math.PI.toFloat()
            val speed = random.nextFloat() * 12f + 4f
            val px = x + (random.nextFloat() - 0.5f) * 20f
            val py = y + (random.nextFloat() - 0.5f) * 20f

            val splashColor = when (config.trailStyle) {
                "Electric Sparkle" -> Color.White // Intense core
                "Water Droplet" -> baseColor.copy(alpha = 0.6f)
                else -> Color(
                    red = (baseColor.red + 0.3f).coerceIn(0f, 1f),
                    green = (baseColor.green + 0.3f).coerceIn(0f, 1f),
                    blue = (baseColor.blue + 0.3f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }

            persistentExplosions.add(
                FluidParticle(
                    x = px,
                    y = py,
                    vx = Math.cos(angle.toDouble()).toFloat() * speed,
                    vy = Math.sin(angle.toDouble()).toFloat() * speed,
                    size = random.nextFloat() * 6f + 3f,
                    color = splashColor,
                    life = 1.0f + random.nextFloat() * 0.5f // Fade lifespan
                )
            )
        }

        // Apply direct outward physics impulse force to regular fluid particles
        for (p in particles) {
            val dx = p.x - x
            val dy = p.y - y
            val distSq = dx * dx + dy * dy
            if (distSq < 150000f) { // Within radius
                val dist = Math.sqrt(distSq.toDouble()).toFloat().coerceAtLeast(10f)
                val force = (150000f - distSq) / 150000f * 15f
                p.vx += (dx / dist) * force
                p.vy += (dy / dist) * force
            }
        }
    }

    /**
     * Run simulation tick: apply gravity, friction/viscosity, widget boundary collision, and boundary rebounds.
     */
    fun update(
        width: Float,
        height: Float,
        config: PhysicsConfig,
        widgets: List<WidgetConfig>,
        widgetBounds: Map<Int, Rect>, // Mapped screen rectangles of actual widgets
        deltaTime: Float = 0.016f
    ) {
        if (width <= 0 || height <= 0) return

        // Fill particles if empty
        if (particles.isEmpty()) {
            initialize(width, height, config)
        }

        // Viscosity drag coefficient factor
        val drag = (1.0f - config.viscosity).coerceIn(0.7f, 1.0f)
        val baseColor = hexToColor(config.colorHex)

        // 1. Update persistent, short-lived explosion splash sparks
        val explosionIterator = persistentExplosions.iterator()
        while (explosionIterator.hasNext()) {
            val p = explosionIterator.next()
            p.x += p.vx
            p.y += p.vy
            
            // Fading out
            p.life -= 0.04f
            
            // Slight gravity on sparks
            p.vx *= 0.95f
            p.vy *= 0.95f
            p.vy += config.gravityY * 0.15f
            p.vx += config.gravityX * 0.15f

            // Bounce on wall bounds
            if (p.x < 0) { p.x = 0f; p.vx = -p.vx * 0.6f }
            if (p.x > width) { p.x = width; p.vx = -p.vx * 0.6f }
            if (p.y < 0) { p.y = 0f; p.vy = -p.vy * 0.6f }
            if (p.y > height) { p.y = height; p.vy = -p.vy * 0.6f }

            if (p.life <= 0f) {
                explosionIterator.remove()
            }
        }

        // 2. Core liquid particle updates
        for (p in particles) {
            // Apply Gravity (virtual tilt / sensor)
            p.vx += config.gravityX * 0.6f
            p.vy += config.gravityY * 0.6f

            // Viscosity friction
            p.vx *= drag
            p.vy *= drag

            // Inter-particle attraction to center / cohesive flow (makes it feel liquid vs gaseous)
            val dxCenter = width * 0.5f - p.x
            val dyCenter = height * 0.5f - p.y
            val dCenter = Math.sqrt((dxCenter * dxCenter + dyCenter * dyCenter).toDouble()).toFloat().coerceAtLeast(1f)
            p.vx += (dxCenter / dCenter) * config.attractionStrength
            p.vy += (dyCenter / dCenter) * config.attractionStrength

            // Add subtle noise (Brownian vibration) to keep particles lively
            p.vx += (random.nextFloat() - 0.5f) * 0.25f
            p.vy += (random.nextFloat() - 0.5f) * 0.25f

            // Move particle
            p.x += p.vx
            p.y += p.vy

            // Collision resolution with widget bounding boxes! (Extremely crisp tactile feedback)
            for ((widgetId, bounds) in widgetBounds) {
                // Pad bounds slightly to account for particle diameter
                val margin = p.size * 0.5f
                val left = bounds.left - margin
                val right = bounds.right + margin
                val top = bounds.top - margin
                val bottom = bounds.bottom + margin

                if (p.x > left && p.x < right && p.y > top && p.y < bottom) {
                    // Collision detected! Determine closest edge to push particle out
                    val dLeft = p.x - left
                    val dRight = right - p.x
                    val dTop = p.y - top
                    val dBottom = bottom - p.y

                    val minD = minOf(dLeft, dRight, dTop, dBottom)
                    val elasticity = 0.6f

                    when (minD) {
                        dLeft -> {
                            p.x = left
                            p.vx = -p.vx * elasticity // bounce left
                            // add flow slide velocity along surface
                            p.vy += (random.nextFloat() - 0.5f) * 1.5f
                        }
                        dRight -> {
                            p.x = right
                            p.vx = -p.vx * elasticity // bounce right
                            p.vy += (random.nextFloat() - 0.5f) * 1.5f
                        }
                        dTop -> {
                            p.y = top
                            p.vy = -p.vy * elasticity // bounce up
                            p.vx += (random.nextFloat() - 0.5f) * 1.5f
                        }
                        dBottom -> {
                            p.y = bottom
                            p.vy = -p.vy * elasticity // bounce down
                            p.vx += (random.nextFloat() - 0.5f) * 1.5f
                        }
                    }
                }
            }

            // Boundary collision (screen borders)
            val padding = p.size
            if (p.x < padding) {
                p.x = padding
                p.vx = -p.vx * 0.5f
            }
            if (p.x > width - padding) {
                p.x = width - padding
                p.vx = -p.vx * 0.5f
            }
            if (p.y < padding) {
                p.y = padding
                p.vy = -p.vy * 0.5f
            }
            if (p.y > height - padding) {
                p.y = height - padding
                p.vy = -p.vy * 0.5f
            }
        }
    }
}
