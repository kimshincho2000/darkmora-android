@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package uz.darkmora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.R
import uz.darkmora.app.data.Anime
import uz.darkmora.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================
// 1. DUAL WHITE & BLACK FLUID SPIRAL -> DARKMORA LOGO INTRO (MATCHES d.darkmora.uz)
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
fun IntroOverlay(
    onFinished: () -> Unit
) {
    val timelineMs = remember { Animatable(0f) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        timelineMs.animateTo(
            targetValue = 2850f,
            animationSpec = tween(durationMillis = 2850, easing = LinearEasing)
        )
        if (!dismissed) {
            dismissed = true
            onFinished()
        }
    }

    val t = timelineMs.value
    val isDark = AppThemeState.isDark
    val bg = if (isDark) Color(0xFF05060A) else Color(0xFFF6F7FB)
    val fgColor = if (isDark) Color.White else Color(0xFF0E1118)
    val subColor = if (isDark) Color.White.copy(alpha = 0.52f) else Color(0xFF4B5563)

    // Exit fade & subtle lift (2420..2850ms)
    val exitProgress = ((t - 2420f) / 430f).coerceIn(0f, 1f)
    val overlayAlpha = 1f - easeOutCubic(exitProgress)
    val overlayScale = 1f + 0.04f * easeOutCubic(exitProgress)

    // Logo reveal (1180..1620ms)
    val logoAlpha = easeOutCubic(((t - 1180f) / 280f).coerceIn(0f, 1f))
    val logoSettle = easeOutBack(((t - 1180f) / 440f).coerceIn(0f, 1f))
    val logoScale = lerp(0.80f, 1.0f, logoSettle)
    val logoRotation = lerp(-18f, 0f, easeOutCubic(((t - 1180f) / 400f).coerceIn(0f, 1f)))

    // Glossy diagonal sheen across logo squircle (1440..1980ms)
    val sheenProgress = ((t - 1440f) / 540f).coerceIn(0f, 1f)

    // Title & subtitle glide up (1480..1940ms)
    val titleP = easeOutCubic(((t - 1480f) / 420f).coerceIn(0f, 1f))
    val subP = easeOutCubic(((t - 1640f) / 360f).coerceIn(0f, 1f))

    // Ambient fusion glow
    val glowRise = easeOutCubic(((t - 1050f) / 420f).coerceIn(0f, 1f))
    val glowFade = 1f - ((t - 1650f) / 700f).coerceIn(0f, 1f)
    val glowAlpha = (0.28f * glowRise * (0.45f + 0.55f * glowFade)).coerceIn(0f, 0.32f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                scaleX = overlayScale
                scaleY = overlayScale
            }
            .background(bg)
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
        // Subtle monochrome ambient glow at center
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
                    Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color.White else Color(0xFF1F2937),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Stage box: Spiral ribbons + shockwaves + sparks + real DarkMora logo
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension * 0.42f

                    // --- PHASE 1: DUAL WHITE & BLACK FLUID SPIRAL (0..1340ms) ---
                    val spiralAlpha = 1f - ((t - 1080f) / 260f).coerceIn(0f, 1f)
                    if (spiralAlpha > 0.005f) {
                        val u = (t / 1300f).coerceIn(0f, 1f)
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

                        // Draw Black ink ribbon first, then White silk ribbon on top
                        val droplets = listOf(
                            Pair(PI.toFloat(), false), // Black ink droplet
                            Pair(0f, true)             // White silk droplet
                        )

                        for ((phaseOffset, isWhite) in droplets) {
                            val headPos = spiralPos(u, phaseOffset)
                            val headR = lerp(12.dp.toPx(), 7.5.dp.toPx(), easeInOutQuart(u))

                            // Tapered fluid tail
                            var prev = headPos
                            for (i in 1..steps) {
                                val uBack = (u - i * stepDt).coerceAtLeast(0f)
                                if (uBack <= 0f && u < i * stepDt) break
                                val curr = spiralPos(uBack, phaseOffset)
                                val frac = 1f - (i.toFloat() / steps)
                                val segWidth = (headR * 1.45f * frac * frac).coerceAtLeast(1.2.dp.toPx())
                                val segAlpha = (frac * frac * 0.92f * spiralAlpha).coerceIn(0f, 1f)

                                if (!isWhite) {
                                    // Crisp rim behind black ink trail so it pops on dark bg
                                    drawLine(
                                        color = if (isDark) Color.White.copy(alpha = segAlpha * 0.42f)
                                        else Color(0xFF94A3B8).copy(alpha = segAlpha * 0.35f),
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
                                    // Soft outer halo for white silk trail
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

                            // Droplet head (teardrop / tomoe orb)
                            val prevHead = spiralPos((u - 0.01f).coerceAtLeast(0f), phaseOffset)
                            val tangentRad = kotlin.math.atan2(
                                (headPos.y - prevHead.y).toDouble(),
                                (headPos.x - prevHead.x).toDouble()
                            ).toFloat()
                            val tangentDeg = tangentRad * (180f / PI.toFloat())

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
                                        color = (if (isDark) Color.White else Color(0xFF64748B))
                                            .copy(alpha = 0.58f * spiralAlpha),
                                        radius = headR * 1.18f,
                                        center = Offset.Zero
                                    )
                                    drawOval(
                                        color = Color(0xFF0A0C12).copy(alpha = spiralAlpha),
                                        topLeft = Offset(-headR * 1.15f, -headR * 0.92f),
                                        size = Size(headR * 2.3f, headR * 1.84f)
                                    )
                                    // Subtle glossy highlight on dark ink droplet
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.55f * spiralAlpha),
                                        radius = headR * 0.24f,
                                        center = Offset(headR * 0.32f, -headR * 0.28f)
                                    )
                                }
                            }
                        }
                    }

                    // --- PHASE 2: DOUBLE FUSION SHOCKWAVE + 8 SPARKS (1150..1880ms) ---
                    val ring1P = ((t - 1150f) / 620f).coerceIn(0f, 1f)
                    if (ring1P > 0f && ring1P < 1f) {
                        val e = easeOutCubic(ring1P)
                        drawCircle(
                            color = fgColor.copy(alpha = (1f - e) * 0.65f),
                            radius = lerp(18.dp.toPx(), 102.dp.toPx(), e),
                            center = Offset(cx, cy),
                            style = Stroke(width = lerp(3.5.dp.toPx(), 0.8.dp.toPx(), e))
                        )
                    }
                    val ring2P = ((t - 1260f) / 600f).coerceIn(0f, 1f)
                    if (ring2P > 0f && ring2P < 1f) {
                        val e = easeOutCubic(ring2P)
                        drawCircle(
                            color = fgColor.copy(alpha = (1f - e) * 0.36f),
                            radius = lerp(12.dp.toPx(), 84.dp.toPx(), e),
                            center = Offset(cx, cy),
                            style = Stroke(width = lerp(2.dp.toPx(), 0.6.dp.toPx(), e))
                        )
                    }

                    val sparkP = ((t - 1170f) / 640f).coerceIn(0f, 1f)
                    if (sparkP > 0f && sparkP < 1f) {
                        val e = easeOutCubic(sparkP)
                        for (i in 0 until 8) {
                            val angle = ((i * 45f + 18f) * PI / 180.0).toFloat()
                            val dist = lerp(22.dp.toPx(), (76f + (i % 2) * 18f).dp.toPx(), e)
                            val sx = cx + cos(angle) * dist
                            val sy = cy + sin(angle) * dist
                            val sr = lerp(3.2.dp.toPx(), 0.6.dp.toPx(), e)
                            val sparkColor = if (i % 2 == 0) fgColor else Color(0xFF94A3B8)
                            drawCircle(
                                color = sparkColor.copy(alpha = (1f - e) * 0.85f),
                                radius = sr,
                                center = Offset(sx, sy)
                            )
                        }
                    }
                }

                // Real DarkMora Logo inside crisp rounded white squircle + diagonal glass sheen
                if (logoAlpha > 0.005f) {
                    val squircleShape = RoundedCornerShape(28.dp)
                    Box(
                        modifier = Modifier
                            .size(102.dp)
                            .graphicsLayer {
                                alpha = logoAlpha
                                scaleX = logoScale
                                scaleY = logoScale
                                rotationZ = logoRotation
                            }
                            .shadow(
                                elevation = if (isDark) 24.dp else 14.dp,
                                shape = squircleShape,
                                ambientColor = Color.White.copy(alpha = 0.25f),
                                spotColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)
                            )
                            .clip(squircleShape)
                            .background(Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color.White.copy(alpha = 0.35f) else Color(0xFFE5E7EB),
                                shape = squircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.darkmora_logo),
                            contentDescription = "DarkMora",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Glossy diagonal sheen sweep across logo
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
                color = fgColor,
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
                text = "ANIME STREAMING",
                color = subColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.2.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = subP
                    translationY = lerp(12f, 0f, subP)
                }
            )
        }
    }
}

// ============================================================================
// 2. TENSEII STAGGERED ENTRANCE ANIMATION MODIFIER (`Modifier.appear(index)`)
// ============================================================================

fun Modifier.appear(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 12) * 45L))
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
        )
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 32f
        val s = 0.94f + 0.06f * progress.value
        scaleX = s
        scaleY = s
    }
}

// ============================================================================
// 3. SOFT TACTILE SPRING BOUNCE CLICK MODIFIER (`pressable` / `bounceClick`)
// ============================================================================

fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
        .pointerInput(isPressed) {
            awaitPointerEventScope {
                isPressed = if (isPressed) {
                    waitForUpOrCancellation()
                    false
                } else {
                    awaitFirstDown(false)
                    true
                }
            }
        }
}

// ============================================================================
// 4. SHIMMER SKELETON EFFECT (ADAPTS TO LIGHT & DARK MODE)
// ============================================================================

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val isDark = AppThemeState.isDark
    val colors = if (isDark) {
        listOf(Color(0xFF131620), Color(0xFF1D2231), Color(0xFF131620))
    } else {
        listOf(Color(0xFFE5E7EB), Color(0xFFF3F4F6), Color(0xFFE5E7EB))
    }

    val brush = Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

// ============================================================================
// 5. CLEAN MINIMALIST ANIME CARD WITH STAGGERED APPEAR & LIGHT/DARK ADAPTATION
// ============================================================================

@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 152,
    height: Int = 218,
    index: Int = 0
) {
    Column(
        modifier = modifier
            .width(width.dp)
            .appear(index)
            .bounceClick(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BgCard)
                .border(
                    width = 0.8.dp,
                    color = CardBorder,
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            AsyncImage(
                model = anime.displayPoster(),
                contentDescription = anime.displayTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Soft bottom gradient for clean badge contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x220A0B10),
                                Color(0xCC0A0B10)
                            ),
                            startY = 140f
                        )
                    )
            )

            // Minimalist Top Rating Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xBF0A0B10))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = RatingGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = anime.displayRating(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val year = anime.displayYear()
                if (year.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x990A0B10))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = year,
                            color = Color(0xFFE2E8F0),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Clean Bottom Episode Badge
            val eps = anime.displayEpisodes()
            if (eps.isNotEmpty() && eps != "0" && eps != "null") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xD9161926))
                        .border(0.6.dp, Color(0x28FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 3.5.dp)
                ) {
                    Text(
                        text = "$eps qism",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = anime.displayTitle(),
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val genres = anime.displayGenres()
        if (genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = genres.replaceFirstChar { it.uppercase() },
                color = TextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// 6. PARALLAX 3D FLOATING HERO CAROUSEL BANNER (NEW MAIN MENU DESIGN)
// ============================================================================

@Composable
fun HeroCarousel(
    featuredList: List<Anime>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(featuredList) { featuredList.take(6) }
    if (items.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    val shineTransition = rememberInfiniteTransition(label = "heroShine")
    val shineOffset by shineTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffset"
    )

    // Auto-advance every 5 seconds
    LaunchedEffect(currentIndex, items.size) {
        if (items.size > 1) {
            delay(5000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Single Full-Width Card with Smooth Crossfade & Swipe (Never splits or clashes with outer tab pager!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(315.dp)
                .padding(horizontal = 18.dp)
                .pointerInput(items.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccum += dragAmount
                        },
                        onDragEnd = {
                            if (items.size > 1) {
                                if (dragAccum < -65f) {
                                    currentIndex = (currentIndex + 1) % items.size
                                } else if (dragAccum > 65f) {
                                    currentIndex = (currentIndex - 1 + items.size) % items.size
                                }
                            }
                            dragAccum = 0f
                        }
                    )
                }
        ) {
            androidx.compose.animation.Crossfade(
                targetState = currentIndex.coerceIn(0, items.size - 1),
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                label = "heroCrossfade"
            ) { page ->
                val anime = items[page]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                        .background(BgCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(26.dp))
                        .clickable { onAnimeClick(anime.getEffectiveSlug()) }
                ) {
                    AsyncImage(
                        model = anime.banner ?: anime.displayPoster(),
                        contentDescription = anime.displayTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Smooth dark cinematic gradient inside the floating card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x28000000),
                                        Color(0x4D000000),
                                        Color(0xD908090F),
                                        Color(0xF508090F)
                                    )
                                )
                            )
                    )

                    // Top Badges inside the floating card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .padding(horizontal = 11.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tr("TOP PREMYERA", "TOP PREMIERE", "ТОП ПРЕМЬЕРА"),
                                color = Color(0xFF0A0B10),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xBF12141E))
                                .border(0.8.dp, Color(0x28FFFFFF), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(12.dp))
                            Text(anime.displayRating(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("•", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(anime.displayYear(), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                        }
                    }

                    // Bottom Content Info Overlay inside card
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val genres = anime.displayGenres()
                        if (genres.isNotEmpty()) {
                            Text(
                                text = genres.replaceFirstChar { it.uppercase() },
                                color = PrimaryGlow,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = anime.displayTitle(),
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Buttons Row (100% inside the single card!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(42.dp)
                                    .bounceClick { onAnimeClick(anime.getEffectiveSlug()) }
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0x443B82F6),
                                                Color.Transparent
                                            ),
                                            start = Offset(shineOffset, 0f),
                                            end = Offset(shineOffset + 120f, 120f)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0A0B10), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = tr("Tomosha qilish", "Watch Now", "Смотреть"),
                                        color = Color(0xFF0A0B10),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(42.dp)
                                    .bounceClick { onAnimeClick(anime.getEffectiveSlug()) }
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xBF1A1D2B))
                                    .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = tr("Batafsil", "Details", "Подробнее"),
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Animated Pill Indicator Dots below the floating card (Tap to jump to any slide)
        if (items.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.indices.forEach { idx ->
                    val isSelected = currentIndex == idx
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 6.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dotWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) TextPrimary else TextDim.copy(alpha = 0.35f),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { currentIndex = idx }
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. TENSEII PILL TABS SWITCHER (ANIMATED SLIDING PILL TABS FOR MAIN MENU)
// ============================================================================

@Composable
fun PillTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(BgCard)
            .border(0.8.dp, CardBorder, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        titles.forEachIndexed { idx, title ->
            val isSelected = selectedIndex == idx
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) TextPrimary else Color.Transparent,
                animationSpec = tween(240),
                label = "pillTabBg"
            )
            val txtColor by animateColorAsState(
                targetValue = if (isSelected) BgSurface else TextMuted,
                animationSpec = tween(240),
                label = "pillTabTxt"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bgColor)
                    .bounceClick { onSelect(idx) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = txtColor,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (onSeeAllClick != null) {
            Row(
                modifier = Modifier
                    .bounceClick(onClick = onSeeAllClick)
                    .clip(RoundedCornerShape(50))
                    .background(BgCard)
                    .border(0.6.dp, CardBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tr("Barchasi", "See All", "Все"),
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
