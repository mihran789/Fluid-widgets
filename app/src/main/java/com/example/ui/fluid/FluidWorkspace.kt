package com.example.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FluidPreset
import com.example.data.WidgetConfig
import com.example.ui.widgets.InteractiveWidgetCard
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random

// Additional core launcher listing imports
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

@Composable
fun rememberDrawablePainter(drawable: Drawable): androidx.compose.ui.graphics.painter.Painter {
    val bitmap = remember(drawable) {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }
    return remember(bitmap) {
        BitmapPainter(bitmap.asImageBitmap())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluidWorkspace(
    viewModel: FluidViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Core Reactive States from DB
    val widgets by viewModel.widgets.collectAsState()
    val presets by viewModel.presets.collectAsState()

    // Interactive Config States
    val viscosity by viewModel.viscosity.collectAsState()
    val gravityX by viewModel.gravityX.collectAsState()
    val gravityY by viewModel.gravityY.collectAsState()
    val trailStyle by viewModel.trailStyle.collectAsState()
    val neonColorHex by viewModel.neonColorHex.collectAsState()
    val particleCount by viewModel.particleCount.collectAsState()
    val widgetBounds by viewModel.widgetBounds.collectAsState()

    val activeColor = remember(neonColorHex) {
        try {
            Color(android.graphics.Color.parseColor(neonColorHex))
        } catch (e: Exception) {
            Color(0xFF00FFCC)
        }
    }

    // Viewport layout tracking
    var size by remember { mutableStateOf(IntSize(0, 0)) }
    
    // Physics engine state
    val engine = remember { FluidSimulationEngine() }
    var tick by remember { mutableStateOf(0L) }

    // Navigation and UI state panels
    var showHomescreenOverlay by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf(0) } // 0: Presets, 1: Physics parameters, 2: Spawn Widgets
    var showSettingsSliderPanel by remember { mutableStateOf(true) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var userPresetName by remember { mutableStateOf("") }
    var activeAppOverlay by remember { mutableStateOf<String?>(null) }
    
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isAppDrawerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfoList = pm.queryIntentActivities(intent, 0)
                val apps = ArrayList<AppInfo>()
                for (resolveInfo in resolveInfoList) {
                    val pName = resolveInfo.activityInfo.packageName
                    if (pName == context.packageName) continue
                    
                    val appLabel = resolveInfo.loadLabel(pm).toString()
                    val appIcon = resolveInfo.loadIcon(pm)
                    apps.add(AppInfo(label = appLabel, packageName = pName, icon = appIcon))
                }
                apps.sortBy { it.label.lowercase() }
                appList = apps
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Reinitialize engine whenever count or color transitions
    LaunchedEffect(size, particleCount, neonColorHex) {
        if (size.width > 0 && size.height > 0) {
            val config = PhysicsConfig(
                viscosity = viscosity,
                gravityX = gravityX,
                gravityY = gravityY,
                trailStyle = trailStyle,
                colorHex = neonColorHex,
                particleCount = particleCount
            )
            engine.initialize(size.width.toFloat(), size.height.toFloat(), config)
        }
    }

    // Frame simulation loop ticker (Runs at 60fps)
    LaunchedEffect(size) {
        while (true) {
            if (size.width > 0 && size.height > 0) {
                val currentConfig = PhysicsConfig(
                    viscosity = viscosity,
                    gravityX = gravityX,
                    gravityY = gravityY,
                    trailStyle = trailStyle,
                    colorHex = neonColorHex,
                    particleCount = particleCount
                )
                engine.update(
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    config = currentConfig,
                    widgets = widgets,
                    widgetBounds = widgetBounds
                )
                tick++
            }
            kotlinx.coroutines.delay(16) // tick lock standard speed
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .drawBehind {
                // Base background color
                drawRect(Color(0xFF05060F))

                val width = size.width
                val height = size.height
                if (width > 0 && height > 0) {
                    // Top-Left Blue Ambient Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2563EB).copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(-width * 0.1f, -height * 0.1f),
                            radius = width * 0.9f
                        ),
                        center = Offset(-width * 0.1f, -height * 0.1f),
                        radius = width * 0.9f
                    )

                    // Bottom-Right Indigo Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF7E22CE).copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(width * 1.05f, height * 1.05f),
                            radius = width * 0.9f
                        ),
                        center = Offset(width * 1.05f, height * 1.05f),
                        radius = width * 0.9f
                    )

                    // Center-Right Active Dynamic Accent Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(activeColor.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.35f),
                            radius = width * 0.65f
                        ),
                        center = Offset(width * 0.85f, height * 0.35f),
                        radius = width * 0.65f
                    )
                }
            }
            .testTag("fluid_workspace_container")
    ) {
        // 1. Interactive Fluid Dynamics Particles Canvas drawing layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viscosity, trailStyle, neonColorHex) {
                    // Capture drags/swipes to inject massive fluid momentum splashes
                    detectTapGestures(
                        onPress = { offset ->
                            val currentConfig = PhysicsConfig(
                                viscosity = viscosity,
                                gravityX = gravityX,
                                gravityY = gravityY,
                                trailStyle = trailStyle,
                                colorHex = neonColorHex,
                                particleCount = particleCount
                            )
                            engine.createSplash(offset.x, offset.y, 25, currentConfig)
                        }
                    )
                }
                .pointerInput(viscosity, trailStyle, neonColorHex) {
                    // Capturing drag motions
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val currentConfig = PhysicsConfig(
                            viscosity = viscosity,
                            gravityX = gravityX,
                            gravityY = gravityY,
                            trailStyle = trailStyle,
                            colorHex = neonColorHex,
                            particleCount = particleCount
                        )
                        engine.createSplash(change.position.x, change.position.y, 8, currentConfig)
                    }
                }
        ) {
            // Unused trigger to force Canvas recomposition on each physics tick
            val trigger = tick

            // Draw glowing trails based on style
            for (p in engine.particles) {
                val radius = p.size
                
                when (trailStyle) {
                    "Water Droplet" -> {
                        // Drawing translucent liquid drop bubbles
                        drawCircle(
                            color = p.color.copy(alpha = 0.25f),
                            radius = radius * 1.5f,
                            center = Offset(p.x, p.y)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.7f),
                            radius = radius * 0.4f,
                            center = Offset(p.x - radius * 0.3f, p.y - radius * 0.3f) // drop shine reflections
                        )
                    }
                    "Electric Sparkle" -> {
                        // Sharp glowing cross trails
                        drawCircle(
                            color = p.color.copy(alpha = 0.8f),
                            radius = radius * 0.8f,
                            center = Offset(p.x, p.y)
                        )
                        // Spark core
                        drawLine(
                            color = Color.White,
                            start = Offset(p.x - radius, p.y),
                            end = Offset(p.x + radius, p.y),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(p.x, p.y - radius),
                            end = Offset(p.x, p.y + radius),
                            strokeWidth = 2f
                        )
                    }
                    else -> {
                        // "Neon Glow": Beautiful glowing gaussian spheres
                        drawCircle(
                            color = p.color.copy(alpha = 0.15f),
                            radius = radius * 2.5f,
                            center = Offset(p.x, p.y)
                        )
                        drawCircle(
                            color = p.color.copy(alpha = 0.6f),
                            radius = radius,
                            center = Offset(p.x, p.y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = radius * 0.35f,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }

            // Draw exploding ephemeral splash sparks
            for (ep in engine.persistentExplosions) {
                drawCircle(
                    color = ep.color.copy(alpha = ep.life.coerceIn(0f, 1f)),
                    radius = ep.size * ep.life,
                    center = Offset(ep.x, ep.y)
                )
            }
        }

        // 2. Mock Android Home Screen UI context (Shows status bar, search bars, shortcuts)
        if (showHomescreenOverlay) {
            MockHomescreenOverlay(
                activeColor = activeColor,
                onShortcutClick = { type ->
                    when (type) {
                        "SETTINGS" -> {
                            showSettingsSliderPanel = !showSettingsSliderPanel
                            if (size.width > 0 && size.height > 0) {
                                val currentConfig = PhysicsConfig(
                                    viscosity = viscosity,
                                    gravityX = gravityX,
                                    gravityY = gravityY,
                                    trailStyle = trailStyle,
                                    colorHex = neonColorHex,
                                    particleCount = particleCount
                                )
                                engine.createSplash(size.width * 0.5f, size.height * 0.8f, 25, currentConfig)
                            }
                        }
                        "HOME" -> {
                            viewModel.resetToDefaults()
                            if (size.width > 0 && size.height > 0) {
                                val currentConfig = PhysicsConfig(
                                    viscosity = viscosity,
                                    gravityX = gravityX,
                                    gravityY = gravityY,
                                    trailStyle = trailStyle,
                                    colorHex = neonColorHex,
                                    particleCount = particleCount
                                )
                                engine.createSplash(size.width * 0.5f, size.height * 0.5f, 50, currentConfig)
                            }
                        }
                        "PHONE", "EMAIL" -> {
                            activeAppOverlay = type
                            if (size.width > 0 && size.height > 0) {
                                val currentConfig = PhysicsConfig(
                                    viscosity = viscosity,
                                    gravityX = gravityX,
                                    gravityY = gravityY,
                                    trailStyle = trailStyle,
                                    colorHex = neonColorHex,
                                    particleCount = particleCount
                                )
                                engine.createSplash(size.width * 0.5f, size.height * 0.5f, 20, currentConfig)
                            }
                        }
                        "APPS" -> {
                            isAppDrawerOpen = true
                            if (size.width > 0 && size.height > 0) {
                                val currentConfig = PhysicsConfig(
                                    viscosity = viscosity,
                                    gravityX = gravityX,
                                    gravityY = gravityY,
                                    trailStyle = trailStyle,
                                    colorHex = neonColorHex,
                                    particleCount = particleCount
                                )
                                engine.createSplash(size.width * 0.5f, size.height * 0.5f, 35, currentConfig)
                            }
                        }
                    }
                }
            )
        }

        // 3. Custom Drag-and-Drop Active Widgets loaded from SQlite Database
        widgets.forEach { config ->
            InteractiveWidgetCard(
                config = config,
                activeNeonColor = activeColor,
                onPositionChanged = { rx, ry ->
                    viewModel.moveWidget(config.id, rx, ry)
                },
                onSizeChanged = { w, h ->
                    // No mandatory size action needed now
                },
                onWidgetBoundsMeasured = { rect ->
                    viewModel.setWidgetBounds(config.id, rect)
                },
                onSplashAt = { sx, sy ->
                    val currentConfig = PhysicsConfig(
                        viscosity = viscosity,
                        gravityX = gravityX,
                        gravityY = gravityY,
                        trailStyle = trailStyle,
                        colorHex = neonColorHex,
                        particleCount = particleCount
                    )
                    engine.createSplash(sx, sy, 30, currentConfig)
                },
                modifier = Modifier.wrapContentSize()
            )
        }

        // 3.5. Default Android Launcher set-up banner check
        var isDefaultLauncher by remember { mutableStateOf(false) }
        LaunchedEffect(tick) {
            if (tick % 120 == 0L) { // Check every ~2 seconds
                try {
                    val pm = context.packageManager
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                    }
                    val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    isDefaultLauncher = resolveInfo?.activityInfo?.packageName == context.packageName
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isDefaultLauncher) Color.White.copy(alpha = 0.05f)
                        else activeColor.copy(alpha = 0.15f)
                    )
                    .clickable {
                        if (!isDefaultLauncher) {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Откройте Системные Настройки -> Приложения -> Приложения по умолчанию -> Главный экран и выберите это приложение.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = if (isDefaultLauncher) {
                                listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            } else {
                                listOf(
                                    activeColor.copy(alpha = 0.8f),
                                    activeColor.copy(alpha = 0.2f)
                                )
                            }
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = activeColor.copy(alpha = if (isDefaultLauncher) 0.15f else 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isDefaultLauncher) activeColor else Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDefaultLauncher) "АКТИВНЫЙ РАБОЧИЙ СТОЛ (ACTIVE HOME)" else "СДЕЛАТЬ РАБОЧИМ СТОЛОМ (SET DESKTOP)",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (!isDefaultLauncher) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Set home launcher directive",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // 4. Custom sliding widget settings controller panel at the bottom half
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Settings Slide trigger button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showHomescreenOverlay = !showHomescreenOverlay },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.2f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .border(
                                width = 0.5.dp,
                                color = activeColor.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (showHomescreenOverlay) Icons.Default.Phone else Icons.Default.Info,
                            contentDescription = "Toggle Homescreen Mockup HUD",
                            tint = activeColor
                        )
                    }

                    Button(
                        onClick = { showSettingsSliderPanel = !showSettingsSliderPanel },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("toggle_settings_button")
                    ) {
                        Icon(
                            imageVector = if (showSettingsSliderPanel) Icons.Default.KeyboardArrowDown else Icons.Default.Settings,
                            contentDescription = "Settings Panel",
                            tint = activeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showSettingsSliderPanel) "HIDE DESIGNER" else "FLUID DESIGN ZONE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Smooth unfolding settings panel
                AnimatedVisibility(
                    visible = showSettingsSliderPanel,
                    enter = fadeIn(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(220))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.18f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                ),
                                shape = RoundedCornerShape(32.dp)
                            )
                            .border(
                                width = 0.5.dp,
                                color = activeColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(32.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            // Sub tabs control in elegant layout
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(4.dp)
                            ) {
                                listOf("PRESETS", "PHYSICS", "SPAWN").forEachIndexed { index, title ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (activeTab == index) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                                            .border(
                                                width = 0.8.dp,
                                                color = if (activeTab == index) activeColor.copy(alpha = 0.35f) else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { activeTab = index }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (activeTab == index) activeColor else Color.LightGray.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Tab Body content
                            when (activeTab) {
                                0 -> { // PRESETS CONTROLLERS
                                    PresetsTab(
                                        presets = presets,
                                        activeColor = activeColor,
                                        onPresetSelected = { viewModel.applyPreset(it) },
                                        onPresetDeleted = { viewModel.deletePreset(it) },
                                        onSaveCurrentRequest = { showSavePresetDialog = true }
                                    )
                                }
                                1 -> { // DETAILED PHYSICS ENGINE SLIDERS
                                    PhysicsConfigTab(
                                        viscosity = viscosity,
                                        gravityX = gravityX,
                                        gravityY = gravityY,
                                        trailStyle = trailStyle,
                                        neonColorHex = neonColorHex,
                                        particleLimit = particleCount,
                                        activeColor = activeColor,
                                        onViscosityChanged = { viewModel.updateViscosity(it) },
                                        onGravityChanged = { x, y -> viewModel.updateGravity(x, y) },
                                        onTrailStyleChanged = { viewModel.updateTrailStyle(it) },
                                        onColorChanged = { viewModel.updateNeonColor(it) },
                                        onLimitChanged = { viewModel.updateParticleCount(it) }
                                    )
                                }
                                2 -> { // COMPONENT SPAWNING TOOLBOX
                                    SpawnWidgetsTab(
                                        widgetsInUse = widgets,
                                        activeColor = activeColor,
                                        onSpawnWidget = { viewModel.addNewWidget(it) },
                                        onDeleteConfig = { viewModel.deleteWidget(it) },
                                        onResetDefaults = { viewModel.resetToDefaults() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Save Preset Dialog Modal
        if (showSavePresetDialog) {
            AlertDialog(
                onDismissRequest = { showSavePresetDialog = false },
                containerColor = Color(0xFF0F111A),
                title = {
                    Text(
                        "SAVE DESIGN CUSTOMIZATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                },
                text = {
                    Column {
                        Text(
                            "Enter a designation label for this custom Physics & Color trail layout.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = userPresetName,
                            onValueChange = { userPresetName = it },
                            placeholder = { Text("e.g. Acid Neon Vortex", color = Color.Gray.copy(alpha = 0.5f), fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeColor,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("preset_name_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (userPresetName.isNotBlank()) {
                                viewModel.saveAsNewPreset(userPresetName.trim())
                                userPresetName = ""
                                showSavePresetDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                    ) {
                        Text("PERSIST PRESET", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSavePresetDialog = false }) {
                        Text("CANCEL", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            )
        }

        // 6. Immersive Simulated Applications Overlays
        AnimatedVisibility(
            visible = activeAppOverlay != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { activeAppOverlay = null },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF070810).copy(alpha = 0.95f))
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = activeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .clickable(enabled = false) {} // prevent tap-through
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activeAppOverlay == "PHONE") "QUANTUM DIALER CELL" else "GLYPH EMAIL DECK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeColor,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        if (activeAppOverlay == "PHONE") {
                            var inputNumber by remember { mutableStateOf("") }
                            var isCallingState by remember { mutableStateOf(false) }

                            Text(
                                text = if (inputNumber.isEmpty()) "ENTER TELEMETRY DIRECTORY" else inputNumber,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isCallingState) activeColor else Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!isCallingState) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val rows = listOf(
                                        listOf("1", "2", "3"),
                                        listOf("4", "5", "6"),
                                        listOf("7", "8", "9"),
                                        listOf("*", "0", "#")
                                    )
                                    rows.forEach { rowKeys ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            rowKeys.forEach { key ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(54.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.05f))
                                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                                        .clickable {
                                                            if (inputNumber.length < 15) {
                                                                inputNumber += key
                                                            }
                                                            if (size.width > 0) {
                                                                val currentConfig = PhysicsConfig(
                                                                    viscosity = viscosity,
                                                                    gravityX = gravityX,
                                                                    gravityY = gravityY,
                                                                    trailStyle = trailStyle,
                                                                    colorHex = neonColorHex,
                                                                    particleCount = particleCount
                                                                )
                                                                engine.createSplash(size.width * 0.5f, size.height * 0.4f, 5, currentConfig)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = key,
                                                        color = Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            if (inputNumber.isNotEmpty()) {
                                                isCallingState = true
                                                if (size.width > 0) {
                                                    val currentConfig = PhysicsConfig(
                                                        viscosity = viscosity,
                                                        gravityX = gravityX,
                                                        gravityY = gravityY,
                                                        trailStyle = trailStyle,
                                                        colorHex = neonColorHex,
                                                        particleCount = particleCount
                                                    )
                                                    engine.createSplash(size.width * 0.5f, size.height * 0.5f, 40, currentConfig)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Icon(Icons.Default.Phone, "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("DIAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }

                                    Button(
                                        onClick = {
                                            if (inputNumber.isNotEmpty()) {
                                                inputNumber = inputNumber.dropLast(1)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier.weight(0.8f)
                                    ) {
                                        Text("BACK", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(activeColor.copy(alpha = 0.1f))
                                        .border(2.dp, activeColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Calling icon",
                                        tint = activeColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "ESTABLISHING CELL CONNECTION...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = activeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "SUBNET TELEMETRY INJECTED OK",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { isCallingState = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("DISCONNECT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val mails = listOf(
                                    Triple("Google AI Studio", "Workspace Active", "The liquid particle sandbox has compiled in debug emulator view. Feel free to drag widgets."),
                                    Triple("Antigravity Labs", "Gavitational Slip Detected", "Tilt orientation sensors are listening. Viscosity levels stored cleanly in SQLite database."),
                                    Triple("Android Core OS", "Launcher Mode Complete", "This application is now behaving like your default custom home screen UI workspace.")
                                )
                                mails.forEach { (sender, subject, body) ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(sender, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = activeColor, fontWeight = FontWeight.Bold)
                                            Text("JUST NOW", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(subject, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(body, fontSize = 9.sp, color = Color.LightGray.copy(alpha = 0.8f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (size.width > 0) {
                                        val currentConfig = PhysicsConfig(
                                            viscosity = viscosity,
                                            gravityX = gravityX,
                                            gravityY = gravityY,
                                            trailStyle = trailStyle,
                                            colorHex = neonColorHex,
                                            particleCount = particleCount
                                        )
                                        engine.createSplash(size.width * 0.2f, size.height * 0.2f, 40, currentConfig)
                                        engine.createSplash(size.width * 0.8f, size.height * 0.2f, 40, currentConfig)
                                        engine.createSplash(size.width * 0.5f, size.height * 0.7f, 40, currentConfig)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = activeColor.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, activeColor),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("COMPOSE SIMULATION MAIL", color = activeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TextButton(
                            onClick = { activeAppOverlay = null }
                        ) {
                            Text("RETURN TO HOMESCREEN", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // 7. Immersive Real Applications Drawer
        val filteredApps = remember(appList, searchQuery) {
            if (searchQuery.isBlank()) {
                appList
            } else {
                appList.filter { it.label.contains(searchQuery, ignoreCase = true) }
            }
        }

        AnimatedVisibility(
            visible = isAppDrawerOpen,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { isAppDrawerOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF070810).copy(alpha = 0.96f))
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = activeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .clickable(enabled = false) {} // prevent tap-through
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CORE APPLICATIONS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeColor,
                                letterSpacing = 1.5.sp
                            )
                            IconButton(
                                onClick = { isAppDrawerOpen = false }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close apps list",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Search core
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "SEARCH CORES...",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search icon",
                                    tint = activeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.04f)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = activeColor,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = activeColor
                            ),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Apps Grid list
                        if (filteredApps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (appList.isEmpty()) "SCANNING APPLICATIONS..." else "NO MATCHED CORES FOUND",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 64.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(filteredApps) { app ->
                                    val painter = rememberDrawablePainter(app.icon)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                try {
                                                    val pm = context.packageManager
                                                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                                    if (launchIntent != null) {
                                                        context.startActivity(launchIntent)
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Launch failed: ${app.label}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Image(
                                            painter = painter,
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.03f))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                .padding(5.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = app.label,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.White.copy(alpha = 0.9f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockHomescreenOverlay(
    activeColor: Color,
    onShortcutClick: (String) -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Very fine real-phone status bar indicator at top of HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "09:41",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = FontFamily.SansSerif
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("5G", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.85f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Telemetry Active",
                        tint = activeColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("100%", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = activeColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Premium "Frosted Glass" Desktop Pro M3 Status Header (Fluid Engine)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLUID ENGINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Desktop Pro",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glassy Search Action Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.2f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable {
                                onShortcutClick("APPS")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Widgets",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Glassy Profile Active User indicator
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        activeColor.copy(alpha = 0.18f),
                                        activeColor.copy(alpha = 0.04f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        activeColor.copy(alpha = 0.35f),
                                        activeColor.copy(alpha = 0.10f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable {
                                android.widget.Toast.makeText(context, "OPERATOR IDENTITY MODULE ACTIVE", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Identity",
                            tint = activeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Mock Bottom Desktop Launcher Shortcuts (Docked crystal icons that liquids can flow around!)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 200.dp) // Height above controls panel
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shortcutList = listOf(
                "PHONE" to (Icons.Default.Phone to Color(0xFF4CAF50)),
                "EMAIL" to (Icons.Default.Email to Color(0xFF2196F3)),
                "APPS" to (Icons.Default.Menu to activeColor),
                "HOME" to (Icons.Default.Home to Color(0xFFFF9800)),
                "SETTINGS" to (Icons.Default.Settings to activeColor)
            )

            shortcutList.forEach { (type, pair) ->
                val (vector, bg) = pair
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.06f),
                                    Color.White.copy(alpha = 0.01f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 0.5.dp,
                            color = bg.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickable { onShortcutClick(type) }
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = vector,
                        contentDescription = "Desktop Launcher Shortcut $type",
                        tint = bg.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsTab(
    presets: List<FluidPreset>,
    activeColor: Color,
    onPresetSelected: (FluidPreset) -> Unit,
    onPresetDeleted: (FluidPreset) -> Unit,
    onSaveCurrentRequest: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PREFABRICATED PHYSICAL SCHEMAS",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Button(
                onClick = onSaveCurrentRequest,
                colors = ButtonDefaults.buttonColors(containerColor = activeColor.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, activeColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("SAVE TEMP PRES", color = activeColor, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (presets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "NO USER PRESETS RECORDED YAET.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 130.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val borderPresetColor = remember(preset.colorHex) {
                        try {
                            Color(android.graphics.Color.parseColor(preset.colorHex))
                        } catch (e: Exception) {
                            Color.Cyan
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(0.5.dp, borderPresetColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { onPresetSelected(preset) }
                            .padding(start = 10.dp, end = 4.dp, top = 4.3.dp, bottom = 4.3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(borderPresetColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = preset.name.uppercase(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Preset",
                            tint = Color.LightGray.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onPresetDeleted(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhysicsConfigTab(
    viscosity: Float,
    gravityX: Float,
    gravityY: Float,
    trailStyle: String,
    neonColorHex: String,
    particleLimit: Int,
    activeColor: Color,
    onViscosityChanged: (Float) -> Unit,
    onGravityChanged: (Float, Float) -> Unit,
    onTrailStyleChanged: (String) -> Unit,
    onColorChanged: (String) -> Unit,
    onLimitChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 190.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Viscosity fluid thickness
        Text(
            text = "FLUID VISCOSITY DENSITY: ${"%.3f".format(viscosity)} (SLOWER FLOW vs FAST WATER)",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray.copy(alpha = 0.8f)
        )
        Slider(
            value = viscosity,
            onValueChange = onViscosityChanged,
            valueRange = 0.01f..0.15f,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Particle volume count
        Text(
            text = "LIQUID PARTICLES POOL QUANTITY: $particleLimit UNITS",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray.copy(alpha = 0.8f)
        )
        Slider(
            value = particleLimit.toFloat(),
            onValueChange = { onLimitChanged(it.toInt()) },
            valueRange = 60f..380f,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Gravity Tilt simulator controllers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VIRTUAL TILT GRAVITY VECTOR",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray.copy(alpha = 0.8f)
                )
                Text(
                    text = "GRAV-X: ${"%.2f".format(gravityX)}  GRAV-Y: ${"%.2f".format(gravityY)}",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = activeColor
                )
            }
            // Joypad buttons to simulate device tilting directions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onGravityChanged(gravityX - 0.5f, gravityY) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("LEFT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White) }
                Button(
                    onClick = { onGravityChanged(gravityX + 0.5f, gravityY) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("RIGHT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White) }
                Button(
                    onClick = { onGravityChanged(gravityX, gravityY + 0.5f) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("DOWN", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White) }
                Button(
                    onClick = { onGravityChanged(0f, 0f) },
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("ZERO", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = activeColor) }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Trail styling choice
        Text(
            text = "TRAIL DISPLAY DESIGN STYLE:",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray.copy(alpha = 0.8f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Neon Glow", "Water Droplet", "Electric Sparkle").forEach { style ->
                val selected = style == trailStyle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) activeColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                        .border(
                            width = 1.dp,
                            color = if (selected) activeColor else Color.DarkGray.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTrailStyleChanged(style) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = style.uppercase(),
                        fontSize = 8.8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) activeColor else Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Theme Color Choices
        Text(
            text = "GLOWING NEON PALETTE CONTEXT:",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray.copy(alpha = 0.8f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val palettes = listOf(
                "#00FFCC" to "MINT CYAN",
                "#FF007F" to "NEON PINK",
                "#39FF14" to "ACID GREEN",
                "#BD00FF" to "ELEC PURPLE",
                "#FF5E00" to "SOLAR ORANGE"
            )

            palettes.forEach { (hexStr, label) ->
                val matching = hexStr.equals(neonColorHex, ignoreCase = true)
                val colorHexParsed = try { Color(android.graphics.Color.parseColor(hexStr)) } catch (e: Exception) { Color.Cyan }
                
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colorHexParsed)
                        .border(
                            width = 2.5.dp,
                            color = if (matching) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onColorChanged(hexStr) }
                )
            }
        }
    }
}

@Composable
fun SpawnWidgetsTab(
    widgetsInUse: List<WidgetConfig>,
    activeColor: Color,
    onSpawnWidget: (String) -> Unit,
    onDeleteConfig: (WidgetConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column {
        Text(
            "ACTIVE COMPOSABLE DESKTOP COMPONENT DECK",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Row of action spawner buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val potential = listOf(
                "CLOCK" to Icons.Default.Refresh,
                "MUSIC" to Icons.Default.PlayArrow,
                "WEATHER" to Icons.Default.Warning,
                "BATTERY" to Icons.Default.Settings
            )

            potential.forEach { (type, vector) ->
                val alreadySpawns = widgetsInUse.any { it.type == type }
                Button(
                    onClick = { if (!alreadySpawns) onSpawnWidget(type) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alreadySpawns) Color.DarkGray.copy(alpha = 0.4f) else activeColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = if (alreadySpawns) Color.DarkGray else activeColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("spawn_button_${type.lowercase()}"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = vector,
                        contentDescription = "Spawn $type",
                        tint = if (alreadySpawns) Color.Gray else activeColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active widget lists panel where they can delete individual spawned widgets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DESKTOP INSTANCES DETECTED: ${widgetsInUse.size}/4",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray.copy(alpha = 0.6f)
            )
            Text(
                text = "FACTORY RESET WORKSPACE",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = activeColor,
                modifier = Modifier
                    .clickable { onResetDefaults() }
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scrolling horizontal items inside spawning controller representing actual delete actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            widgetsInUse.forEach { config ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = config.type,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Trash delete",
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onDeleteConfig(config) }
                    )
                }
            }
        }
    }
}
