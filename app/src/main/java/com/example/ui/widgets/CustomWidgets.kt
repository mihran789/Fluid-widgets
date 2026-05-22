package com.example.ui.widgets

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WidgetConfig
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun InteractiveWidgetCard(
    config: WidgetConfig,
    activeNeonColor: Color,
    onPositionChanged: (Float, Float) -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    onWidgetBoundsMeasured: (Rect) -> Unit,
    onSplashAt: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(config.posX) }
    var offsetY by remember { mutableStateOf(config.posY) }

    // Synchronize offset state if configuration changes from external sources (such as presets)
    LaunchedEffect(config.posX, config.posY) {
        offsetX = config.posX
        offsetY = config.posY
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .onGloballyPositioned { layoutCoordinates ->
                val parentPos = layoutCoordinates.positionInParent()
                val width = layoutCoordinates.size.width.toFloat()
                val height = layoutCoordinates.size.height.toFloat()
                onWidgetBoundsMeasured(
                    Rect(
                        left = parentPos.x,
                        top = parentPos.y,
                        right = parentPos.x + width,
                        bottom = parentPos.y + height
                    )
                )
                onSizeChanged(layoutCoordinates.size.width, layoutCoordinates.size.height)
            }
            .width(195.dp) // --- Highly compact 195.dp size for optimal home screen layout
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.45f),
                        Color.Black.copy(alpha = 0.25f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.03f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 0.5.dp,
                color = activeNeonColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("widget_card_${config.type.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Grip row at the top (acts as the only drag handle to let regular buttons work below)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .pointerInput(config.id) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                onSplashAt(offsetX + offset.x, offsetY + offset.y)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                onPositionChanged(offsetX, offsetY)
                                // Create minor ripples / splash while dragging to make moving feel watery
                                if (Math.abs(dragAmount.x) + Math.abs(dragAmount.y) > 8f) {
                                    onSplashAt(offsetX + change.position.x, offsetY + change.position.y)
                                }
                            },
                            onDragEnd = {
                                onPositionChanged(offsetX, offsetY)
                            }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(activeNeonColor.copy(alpha = 0.5f))
                    )
                    Text(
                        text = when (config.type) {
                            "CLOCK" -> "TIME DEVIATION"
                            "MUSIC" -> "GRAVITY SPECTRAL"
                            "WEATHER" -> "BAROMETER"
                            "BATTERY" -> "ION RESONATOR"
                            else -> "QUANTUM DATA"
                        },
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = activeNeonColor.copy(alpha = 0.82f),
                        letterSpacing = 0.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(activeNeonColor)
                )
            }

            // Widget Content with regular interactions (no drag gestures interception)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Body rendering based on type
                when (config.type) {
                    "CLOCK" -> CyberClockWidget(themeColor = activeNeonColor)
                    "MUSIC" -> CyberMusicWidget(themeColor = activeNeonColor)
                    "WEATHER" -> CyberWeatherWidget(themeColor = activeNeonColor)
                    "BATTERY" -> CyberBatteryWidget(themeColor = activeNeonColor)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Draggability indicator footer
                Text(
                    text = if (config.customLabel.isNotEmpty()) config.customLabel.uppercase() else "✦ HOLD TOP BAR TO DRAG ✦",
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CyberClockWidget(themeColor: Color) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            currentDate = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date()).uppercase()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = currentTime,
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = themeColor,
                shadow = Shadow(
                    color = themeColor,
                    blurRadius = 12f
                )
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = currentDate,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CyberMusicWidget(themeColor: Color) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentSongIndex by remember { mutableStateOf(0) }
    
    val songs = listOf(
        "NEON OVERDRIVE" to "SYNTHWAVE CHRON",
        "GRAVITY WELLS" to "ANTIGRAVITY EXP",
        "STARDUST SHOWER" to "FLUID ECLIPSE"
    )

    // Animated rotating vinyl disc effect
    val infiniteTransition = rememberInfiniteTransition(label = "VinylDisc")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rotating record disc visualizer
        Box(
            modifier = Modifier
                .size(46.dp)
                .rotate(if (isPlaying) rotationAngle else 0f)
                .clip(CircleShape)
                .background(Color.Black)
                .border(1.2.dp, themeColor.copy(alpha = 0.8f), CircleShape)
                .drawBehind {
                    // Draw micro groove cylinders
                    drawCircle(
                        color = themeColor.copy(alpha = 0.2f),
                        radius = size.minDimension / 3f,
                        style = Stroke(1f)
                    )
                    drawCircle(
                        color = themeColor.copy(alpha = 0.1f),
                        radius = size.minDimension / 4.5f,
                        style = Stroke(0.8f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(themeColor)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Tracks Controller UI
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = songs[currentSongIndex].first,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = songs[currentSongIndex].second,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = themeColor.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp)
            )

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Previous Track",
                    tint = Color.LightGray,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            currentSongIndex = if (currentSongIndex > 0) currentSongIndex - 1 else songs.size - 1
                        }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause Controls",
                    tint = themeColor,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { isPlaying = !isPlaying }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next Track",
                    tint = Color.LightGray,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            currentSongIndex = (currentSongIndex + 1) % songs.size
                        }
                )
            }
        }
    }
}

data class ClimateCondition(val first: String, val second: String, val third: ImageVector)

@Composable
fun CyberWeatherWidget(themeColor: Color) {
    var climateTheme by remember { mutableStateOf(0) } // 0: Storm, 1: Rainforest Rain, 2: Cyber Aurora
    val conditions = listOf(
        ClimateCondition("BLIZZARD", "-04°C", Icons.Default.Warning),
        ClimateCondition("PRECIPIT.", "21°C", Icons.Default.Info),
        ClimateCondition("AURORA", "99K", Icons.Default.Refresh)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { climateTheme = (climateTheme + 1) % conditions.size }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "NEO-GRID 07",
                fontSize = 8.sp,
                color = Color.LightGray.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = conditions[climateTheme].first,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "HUMIDITY 89%",
                fontSize = 8.sp,
                color = themeColor.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Icon(
                imageVector = conditions[climateTheme].third,
                contentDescription = "Weather Icon State",
                tint = themeColor,
                modifier = Modifier
                    .size(26.dp)
                    .drawBehind {
                        drawCircle(
                            color = themeColor.copy(alpha = 0.25f),
                            radius = size.maxDimension * 0.7f,
                            style = Stroke(1.5f)
                        )
                    }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conditions[climateTheme].second,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun CyberBatteryWidget(themeColor: Color) {
    // Dynamic charging wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidCharging")
    val batteryPower by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BatteryLevel"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1.3f)
        ) {
            Text(
                text = "FUSION FIELD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "POWER ENERGY",
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(batteryPower * 100).toInt()}% READY",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = themeColor,
                style = TextStyle(
                    shadow = Shadow(color = themeColor, blurRadius = 6f)
                )
            )
        }

        // Custom drawn interactive fluid battery chamber
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(48.dp)
                .border(1.2.dp, themeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Charging liquid wave drawing
            Canvas(modifier = Modifier.fillMaxSize()) {
                val waveHeight = size.height * batteryPower
                val fillY = size.height - waveHeight
                
                // Draw liquid background block
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(themeColor.copy(alpha = 0.8f), themeColor.copy(alpha = 0.3f))
                    ),
                    topLeft = Offset(0f, fillY),
                    size = androidx.compose.ui.geometry.Size(size.width, waveHeight)
                )

                // Render dynamic overlay line
                drawLine(
                    color = Color.White,
                    start = Offset(0f, fillY),
                    end = Offset(size.width, fillY),
                    strokeWidth = 2f
                )
            }
        }
    }
}
