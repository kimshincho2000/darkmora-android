package uz.darkmora.admin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import uz.darkmora.admin.R
import uz.darkmora.admin.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================
// 1. LIVING LIQUID AURORA BACKGROUND (DEPTH LAYER FOR LIQUID GLASS REFRACTION)
// ============================================================================

@Composable
fun LiquidAuroraBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "liquid_aurora")
    val phase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb1"
    )
    val phase2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb2"
    )
    val phase3 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 27000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb3"
    )

    Canvas(modifier = modifier.fillMaxSize().background(BgBlack)) {
        val w = size.width
        val h = size.height

        // Orb 1: Deep Electric Blue / Cyan top-left drift
        val c1 = Offset(
            x = w * (0.22f + 0.16f * cos(phase1)),
            y = h * (0.16f + 0.08f * sin(phase1 * 1.2f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentBlue.copy(alpha = 0.24f), AccentCyan.copy(alpha = 0.08f), Color.Transparent),
                center = c1,
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = c1
        )

        // Orb 2: Liquid Violet right-mid drift
        val c2 = Offset(
            x = w * (0.82f + 0.14f * sin(phase2)),
            y = h * (0.46f + 0.12f * cos(phase2))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentViolet.copy(alpha = 0.22f), AccentRose.copy(alpha = 0.06f), Color.Transparent),
                center = c2,
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = c2
        )

        // Orb 3: Soft Emerald bottom-left glow
        val c3 = Offset(
            x = w * (0.28f + 0.18f * cos(phase3 + 1.4f)),
            y = h * (0.84f + 0.09f * sin(phase3))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentEmerald.copy(alpha = 0.16f), AccentCyan.copy(alpha = 0.05f), Color.Transparent),
                center = c3,
                radius = w * 0.64f
            ),
            radius = w * 0.64f,
            center = c3
        )
    }
}

// ============================================================================
// 2. CRYSTAL LIQUID GLASS MODIFIER (SPECULAR RIM + CAUSTIC GRADIENT + SHEEN)
// ============================================================================

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.White,
    glassAlpha: Float = 0.10f,
    borderAlpha: Float = 0.34f,
    elevation: Dp = 10.dp
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.55f),
        spotColor = Color.Black.copy(alpha = 0.65f)
    )
    .clip(shape)
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                tint.copy(alpha = glassAlpha + 0.07f),
                Color(0x9A0C101B),
                Color(0xA6090C14),
                tint.copy(alpha = glassAlpha * 0.65f)
            )
        )
    )
    .drawWithContent {
        drawContent()
        // Top-edge liquid glass specular lens highlight
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.02f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.38f
            )
        )
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha + 0.18f),
                tint.copy(alpha = borderAlpha * 0.55f),
                Color.White.copy(alpha = borderAlpha * 0.22f),
                Color.White.copy(alpha = borderAlpha * 0.72f)
            )
        ),
        shape = shape
    )

fun Modifier.liquidGlassPill(
    tint: Color = Color.White,
    glassAlpha: Float = 0.12f,
    borderAlpha: Float = 0.36f
): Modifier = this.liquidGlass(
    shape = RoundedCornerShape(50),
    tint = tint,
    glassAlpha = glassAlpha,
    borderAlpha = borderAlpha,
    elevation = 8.dp
)

// ============================================================================
// 3. TACTILE SPRING BOUNCE & STAGGERED ENTRANCE ANIMATIONS
// ============================================================================

fun Modifier.pressBounce(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.52f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}

fun Modifier.staggeredAppear(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 14) * 38L))
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.80f,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - progress.value) * 34f
        val s = 0.95f + 0.05f * p
        scaleX = s
        scaleY = s
    }
}

// ============================================================================
// 4. PULSING LIVE STATUS DOT
// ============================================================================

@Composable
fun PulsingOnlineDot(
    color: Color = AccentEmerald,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "online_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )

    Box(modifier = modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    val s = 0.5f + 0.85f * pulse
                    scaleX = s
                    scaleY = s
                    alpha = (1f - pulse) * 0.65f
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ============================================================================
// 5. DUAL WHITE & BLACK FLUID SPIRAL -> DARKMORA ADMIN LOGO INTRO
// ============================================================================

private fun easeInOutQuart(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) 8f * x * x * x * x else 1f - (-2f * x + 2f).pow(4) / 2f
}

private fun easeOutCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x).pow(3)
}

private fun easeOutBack(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    val c1 = 1.45f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

@Composable
fun AdminIntroOverlay(
    onFinished: () -> Unit
) {
    val timelineMs = remember { Animatable(0f) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        timelineMs.animateTo(
            targetValue = 2650f,
            animationSpec = tween(durationMillis = 2650, easing = LinearEasing)
        )
        if (!dismissed) {
            dismissed = true
            onFinished()
        }
    }

    val t = timelineMs.value
    val exitProgress = ((t - 2220f) / 430f).coerceIn(0f, 1f)
    val overlayAlpha = 1f - easeOutCubic(exitProgress)
    val overlayScale = 1f + 0.04f * easeOutCubic(exitProgress)

    val logoAlpha = easeOutCubic(((t - 1100f) / 280f).coerceIn(0f, 1f))
    val logoSettle = easeOutBack(((t - 1100f) / 420f).coerceIn(0f, 1f))
    val logoScale = lerp(0.80f, 1.0f, logoSettle)
    val logoRotation = lerp(-18f, 0f, easeOutCubic(((t - 1100f) / 380f).coerceIn(0f, 1f)))
    val sheenProgress = ((t - 1360f) / 520f).coerceIn(0f, 1f)

    val titleP = easeOutCubic(((t - 1380f) / 400f).coerceIn(0f, 1f))
    val subP = easeOutCubic(((t - 1540f) / 340f).coerceIn(0f, 1f))

    val glowRise = easeOutCubic(((t - 980f) / 400f).coerceIn(0f, 1f))
    val glowFade = 1f - ((t - 1550f) / 680f).coerceIn(0f, 1f)
    val glowAlpha = (0.28f * glowRise * (0.45f + 0.55f * glowFade)).coerceIn(0f, 0.32f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                scaleX = overlayScale
                scaleY = overlayScale
            }
            .background(Color(0xFF04060B))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!dismissed) {
                    dismissed = true
                    onFinished()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    val s = 0.75f + 0.35f * glowRise
                    scaleX = s
                    scaleY = s
                    alpha = glowAlpha
                }
                .background(
                    Brush.radialGradient(colors = listOf(Color.White, Color.Transparent)),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension * 0.42f

                    val spiralAlpha = 1f - ((t - 1000f) / 250f).coerceIn(0f, 1f)
                    if (spiralAlpha > 0.005f) {
                        val u = (t / 1220f).coerceIn(0f, 1f)
                        val startAngle = (-PI * 0.65).toFloat()
                        val totalSpin = (PI * 3.15).toFloat()
                        val steps = 22
                        val stepDt = 0.0155f

                        fun spiralPos(uSample: Float, phaseOffset: Float): Offset {
                            val e = easeInOutQuart(uSample)
                            val r = maxR * (1f - e)
                            val a = startAngle + e * totalSpin + phaseOffset
                            return Offset(cx + cos(a) * r, cy + sin(a) * r)
                        }

                        val droplets = listOf(Pair(PI.toFloat(), false), Pair(0f, true))
                        for ((phaseOffset, isWhite) in droplets) {
                            val headPos = spiralPos(u, phaseOffset)
                            val headR = lerp(12.dp.toPx(), 7.5.dp.toPx(), easeInOutQuart(u))
                            var prev = headPos
                            for (i in 1..steps) {
                                val uBack = (u - i * stepDt).coerceAtLeast(0f)
                                if (uBack <= 0f && u < i * stepDt) break
                                val curr = spiralPos(uBack, phaseOffset)
                                val frac = 1f - (i.toFloat() / steps)
                                val segWidth = (headR * 1.45f * frac * frac).coerceAtLeast(1.2.dp.toPx())
                                val segAlpha = (frac * frac * 0.92f * spiralAlpha).coerceIn(0f, 1f)
                                if (!isWhite) {
                                    drawLine(
                                        color = Color.White.copy(alpha = segAlpha * 0.42f),
                                        start = prev,
                                        end = curr,
                                        strokeWidth = segWidth + 2.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = Color(0xFF0A0C12).copy(alpha = segAlpha),
                                        start = prev,
                                        end = curr,
                                        strokeWidth = segWidth,
                                        cap = StrokeCap.Round
                                    )
                                } else {
                                    drawLine(
                                        color = Color.White.copy(alpha = segAlpha * 0.24f),
                                        start = prev,
                                        end = curr,
                                        strokeWidth = segWidth + 5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = Color.White.copy(alpha = segAlpha),
                                        start = prev,
                                        end = curr,
                                        strokeWidth = segWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                                prev = curr
                            }

                            val prevHead = spiralPos((u - 0.01f).coerceAtLeast(0f), phaseOffset)
                            val tangentDeg = atan2(
                                (headPos.y - prevHead.y).toDouble(),
                                (headPos.x - prevHead.x).toDouble()
                            ).toFloat() * (180f / PI.toFloat())

                            withTransform({
                                translate(headPos.x, headPos.y)
                                rotate(tangentDeg)
                            }) {
                                if (isWhite) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.30f * spiralAlpha),
                                        radius = headR * 1.75f,
                                        center = Offset.Zero
                                    )
                                    drawOval(
                                        color = Color.White.copy(alpha = spiralAlpha),
                                        topLeft = Offset(-headR * 1.15f, -headR * 0.92f),
                                        size = Size(headR * 2.3f, headR * 1.84f)
                                    )
                                } else {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.58f * spiralAlpha),
                                        radius = headR * 1.18f,
                                        center = Offset.Zero
                                    )
                                    drawOval(
                                        color = Color(0xFF0A0C12).copy(alpha = spiralAlpha),
                                        topLeft = Offset(-headR * 1.15f, -headR * 0.92f),
                                        size = Size(headR * 2.3f, headR * 1.84f)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.55f * spiralAlpha),
                                        radius = headR * 0.24f,
                                        center = Offset(headR * 0.32f, -headR * 0.28f)
                                    )
                                }
                            }
                        }
                    }

                    // Double monochrome shockwave + 8 sparks
                    val ring1P = ((t - 1080f) / 600f).coerceIn(0f, 1f)
                    if (ring1P > 0f && ring1P < 1f) {
                        val e = easeOutCubic(ring1P)
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - e) * 0.65f),
                            radius = lerp(18.dp.toPx(), 100.dp.toPx(), e),
                            center = Offset(cx, cy),
                            style = Stroke(width = lerp(3.5.dp.toPx(), 0.8.dp.toPx(), e))
                        )
                    }
                    val ring2P = ((t - 1190f) / 580f).coerceIn(0f, 1f)
                    if (ring2P > 0f && ring2P < 1f) {
                        val e = easeOutCubic(ring2P)
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - e) * 0.36f),
                            radius = lerp(12.dp.toPx(), 82.dp.toPx(), e),
                            center = Offset(cx, cy),
                            style = Stroke(width = lerp(2.dp.toPx(), 0.6.dp.toPx(), e))
                        )
                    }
                    val sparkP = ((t - 1100f) / 620f).coerceIn(0f, 1f)
                    if (sparkP > 0f && sparkP < 1f) {
                        val e = easeOutCubic(sparkP)
                        for (i in 0 until 8) {
                            val angle = ((i * 45f + 18f) * PI / 180.0).toFloat()
                            val dist = lerp(22.dp.toPx(), (74f + (i % 2) * 18f).dp.toPx(), e)
                            val sx = cx + cos(angle) * dist
                            val sy = cy + sin(angle) * dist
                            val sr = lerp(3.2.dp.toPx(), 0.6.dp.toPx(), e)
                            val sparkColor = if (i % 2 == 0) Color.White else AccentCyan
                            drawCircle(
                                color = sparkColor.copy(alpha = (1f - e) * 0.85f),
                                radius = sr,
                                center = Offset(sx, sy)
                            )
                        }
                    }
                }

                if (logoAlpha > 0.005f) {
                    val squircleShape = RoundedCornerShape(28.dp)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                alpha = logoAlpha
                                scaleX = logoScale
                                scaleY = logoScale
                                rotationZ = logoRotation
                            }
                            .shadow(
                                elevation = 24.dp,
                                shape = squircleShape,
                                ambientColor = Color.White.copy(alpha = 0.25f),
                                spotColor = Color.White.copy(alpha = 0.22f)
                            )
                            .clip(squircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.White.copy(alpha = 0.40f), squircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.darkmora_logo),
                            contentDescription = "DarkMora Admin",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (sheenProgress > 0f && sheenProgress < 1f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sweepX = lerp(-size.width * 0.9f, size.width * 1.9f, sheenProgress)
                                val bandW = size.width * 0.45f
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.55f),
                                            Color.Transparent
                                        ),
                                        start = Offset(sweepX - bandW, 0f),
                                        end = Offset(sweepX + bandW, size.height)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "DARKMORA",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = lerp(8.5f, 5.2f, titleP).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleP
                    translationY = lerp(24f, 0f, titleP)
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ADMIN CONTROL",
                color = AccentCyan.copy(alpha = 0.78f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.6.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = subP
                    translationY = lerp(12f, 0f, subP)
                }
            )
        }
    }
}
