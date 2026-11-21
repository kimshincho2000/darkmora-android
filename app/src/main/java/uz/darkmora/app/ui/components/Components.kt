@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package uz.darkmora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.ui.theme.*
import kotlin.math.abs

// ============================================================================
// 1. TENSEII-STYLE CINEMATIC CALLIGRAPHY INTRO OVERLAY (SPLASH ANIMATION)
// ============================================================================

@Composable
fun IntroOverlay(
    onFinished: () -> Unit
) {
    // 5 calligraphy strokes forming a stylized Torii / DarkMora emblem
    val strokeProgress = remember { List(5) { Animatable(0f) } }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(24f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    val glowPulse = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Staggered calligraphy brush stroke drawing (just like Tenseii IntroOverlayKt)
        strokeProgress.forEachIndexed { index, anim ->
            launch {
                delay(index * 110L)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )
            }
        }
        launch {
            delay(250L)
            glowPulse.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        delay(420L)
        launch {
            titleAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        }
        delay(180L)
        subtitleAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        delay(650L)
        overlayAlpha.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    val isDark = AppThemeState.isDark
    val bg = if (isDark) Color(0xFF07080C) else Color(0xFFF9FAFB)
    val primaryStroke = if (isDark) Color.White else Color(0xFF111827)
    val accentStroke = PrimaryBlue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial ambient glow behind emblem
        Box(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    scaleX = 0.8f + 0.3f * glowPulse.value
                    scaleY = 0.8f + 0.3f * glowPulse.value
                    alpha = 0.25f * glowPulse.value
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(PrimaryBlue, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Torii / Emblem Strokes Canvas
            Canvas(
                modifier = Modifier.size(116.dp)
            ) {
                val w = size.width
                val h = size.height

                // Stroke 1: Top curved Kasagi beam
                val p0 = strokeProgress[0].value
                if (p0 > 0f) {
                    val path1 = Path().apply {
                        moveTo(w * 0.12f, h * 0.24f)
                        quadraticBezierTo(
                            w * 0.50f,
                            h * 0.34f,
                            w * (0.12f + 0.76f * p0),
                            h * (0.24f + 0.0f * p0)
                        )
                    }
                    drawPath(
                        path = path1,
                        color = primaryStroke,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Stroke 2: Second horizontal貫 (Nuki) beam
                val p1 = strokeProgress[1].value
                if (p1 > 0f) {
                    drawLine(
                        color = accentStroke,
                        start = Offset(w * 0.20f, h * 0.42f),
                        end = Offset(w * (0.20f + 0.60f * p1), h * 0.42f),
                        strokeWidth = 4.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Stroke 3: Left pillar
                val p2 = strokeProgress[2].value
                if (p2 > 0f) {
                    drawLine(
                        color = primaryStroke,
                        start = Offset(w * 0.31f, h * 0.27f),
                        end = Offset(w * (0.31f - 0.04f * p2), h * (0.27f + 0.56f * p2)),
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Stroke 4: Right pillar
                val p3 = strokeProgress[3].value
                if (p3 > 0f) {
                    drawLine(
                        color = primaryStroke,
                        start = Offset(w * 0.69f, h * 0.27f),
                        end = Offset(w * (0.69f + 0.04f * p3), h * (0.27f + 0.56f * p3)),
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Stroke 5: Center vertical tablet (Gakuzuka) + glowing orb
                val p4 = strokeProgress[4].value
                if (p4 > 0f) {
                    drawLine(
                        color = accentStroke,
                        start = Offset(w * 0.50f, h * 0.27f),
                        end = Offset(w * 0.50f, h * (0.27f + 0.15f * p4)),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = accentStroke,
                        radius = 5.dp.toPx() * p4,
                        center = Offset(w * 0.50f, h * 0.62f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "DARKMORA",
                color = primaryStroke,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ANIME STREAMING",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha.value
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
