package uz.darkmora.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.data.UserProfile
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// ANIME-THEMED FULL-SCREEN ANIMATED LOGIN / AUTH EXPERIENCE
// ============================================================================

private data class SakuraPetal(
    val xSeed: Float,
    val ySeed: Float,
    val speed: Float,
    val size: Float,
    val swayAmp: Float,
    val rotSpeed: Float,
    val isSpark: Boolean,
    val tint: Color
)

@Composable
fun AuthDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val scope = rememberCoroutineScope()
    val isDark = AppThemeState.isDark

    // 0 = Kirish (Login), 1 = Telegram, 2 = Ro'yxatdan o'tish (Register)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Login state
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Telegram state
    var isWaitingTelegram by remember { mutableStateOf(false) }
    var tgPollToken by remember { mutableStateOf<String?>(null) }

    // Register state
    var regUsername by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var regEmail by remember { mutableStateOf("") }
    var regCode by remember { mutableStateOf("") }
    var regStep by remember { mutableIntStateOf(1) }
    var isSendingCode by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }

    // Live anime poster slideshow for cinematic backdrop
    var posterUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var activePosterIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val home = DarkmoraApi.getHome()
            if (home.ok && home.data != null) {
                val list = ((home.data.featured ?: emptyList()) + (home.data.trending ?: emptyList()))
                    .mapNotNull { it.banner?.takeIf { u -> u.startsWith("http") } ?: it.displayPoster().takeIf { u -> u.startsWith("http") } }
                    .distinct()
                    .take(6)
                if (list.isNotEmpty()) posterUrls = list
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(posterUrls) {
        if (posterUrls.size > 1) {
            while (true) {
                delay(5200)
                activePosterIndex = (activePosterIndex + 1) % posterUrls.size
            }
        }
    }

    // Telegram auth polling
    LaunchedEffect(tgPollToken, isWaitingTelegram) {
        val tok = tgPollToken ?: return@LaunchedEffect
        while (isWaitingTelegram) {
            delay(2000)
            try {
                val pollRes = DarkmoraApi.pollTelegramAuth(tok)
                if (pollRes.ok && pollRes.status == "confirmed" && pollRes.data?.user != null) {
                    val authData = pollRes.data
                    val u = authData.user!!
                    val jwt = authData.token ?: ""
                    sessionManager.saveSession(jwt, u)
                    isWaitingTelegram = false
                    onLoginSuccess(u)
                    break
                }
            } catch (_: Exception) {}
        }
    }

    // Entrance spring animation
    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        cardVisible = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (cardVisible) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "authCardAlpha"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (cardVisible) 0f else 64f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "authCardOffset"
    )

    // Infinite ambient animations (Ken Burns + glowing aura + sakura particles)
    val infiniteTransition = rememberInfiniteTransition(label = "animeAuthAmbient")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.02f,
        targetValue = 1.11f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kenBurns"
    )
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulse"
    )
    val particleClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sakuraClock"
    )

    val petals = remember {
        listOf(
            SakuraPetal(0.08f, 0.12f, 1.0f, 11f, 26f, 180f, false, Color(0xFFF472B6)),
            SakuraPetal(0.22f, 0.45f, 1.4f, 8f, 20f, -220f, false, Color(0xFFF9A8D4)),
            SakuraPetal(0.36f, 0.05f, 0.8f, 13f, 32f, 140f, false, Color(0xFFF472B6)),
            SakuraPetal(0.54f, 0.68f, 1.2f, 9f, 22f, -160f, false, Color(0xFFC084FC)),
            SakuraPetal(0.71f, 0.25f, 1.1f, 12f, 28f, 200f, false, Color(0xFFF9A8D4)),
            SakuraPetal(0.86f, 0.58f, 1.5f, 10f, 24f, -190f, false, Color(0xFFF472B6)),
            SakuraPetal(0.93f, 0.14f, 0.9f, 8f, 18f, 150f, false, Color(0xFF60A5FA)),
            // Glowing spirit sparks
            SakuraPetal(0.15f, 0.78f, 1.6f, 5f, 14f, 0f, true, Color(0xFF60A5FA)),
            SakuraPetal(0.45f, 0.32f, 1.3f, 6f, 18f, 0f, true, Color(0xFFA78BFA)),
            SakuraPetal(0.64f, 0.85f, 1.8f, 4.5f, 12f, 0f, true, Color(0xFF38BDF8)),
            SakuraPetal(0.80f, 0.40f, 1.2f, 5.5f, 16f, 0f, true, Color(0xFFF472B6)),
            SakuraPetal(0.29f, 0.60f, 1.5f, 4f, 15f, 0f, true, Color(0xFF60A5FA))
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xFF070912) else Color(0xFFF1F5F9))
        ) {
            // 1. CINEMATIC ANIME POSTER BACKDROP (Ken Burns + Crossfade)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.58f)
                    .align(Alignment.TopCenter)
            ) {
                if (posterUrls.isNotEmpty()) {
                    Crossfade(
                        targetState = posterUrls[activePosterIndex % posterUrls.size],
                        animationSpec = tween(1200),
                        label = "authPosterCrossfade"
                    ) { posterUrl ->
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = kenBurnsScale
                                    scaleY = kenBurnsScale
                                }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF1E1B4B),
                                        Color(0xFF0F172A),
                                        Color(0xFF172554)
                                    )
                                )
                            )
                    )
                }

                // Atmospheric scrim blending smoothly into app background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (isDark) {
                                    listOf(
                                        Color(0x66070912),
                                        Color(0xA6070912),
                                        Color(0xEB070912),
                                        Color(0xFF070912)
                                    )
                                } else {
                                    listOf(
                                        Color(0x550F172A),
                                        Color(0x991E293B),
                                        Color(0xE8F1F5F9),
                                        Color(0xFFF1F5F9)
                                    )
                                }
                            )
                        )
                )
            }

            // 2. FLOATING ANIME SAKURA PETALS & SPIRIT PARTICLES CANVAS
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .align(Alignment.TopCenter)
            ) {
                val w = size.width
                val h = size.height
                petals.forEach { p ->
                    val progress = (p.ySeed + particleClock * p.speed) % 1f
                    val y = progress * h
                    val sway = sin((progress * 2.0 * PI + p.xSeed * 10.0)).toFloat() * p.swayAmp
                    val x = ((p.xSeed * w) + sway + w) % w
                    val edgeFade = (sin(progress * PI)).toFloat().coerceIn(0f, 1f)

                    if (p.isSpark) {
                        val glowAlpha = (0.28f + 0.45f * edgeFade).coerceIn(0f, 0.85f)
                        drawCircle(
                            color = p.tint.copy(alpha = glowAlpha * 0.35f),
                            radius = p.size * 2.4f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = p.tint.copy(alpha = glowAlpha),
                            radius = p.size,
                            center = Offset(x, y)
                        )
                    } else {
                        val angle = progress * p.rotSpeed + p.xSeed * 360f
                        val alpha = (0.18f + 0.55f * edgeFade).coerceIn(0f, 0.78f)
                        rotate(degrees = angle, pivot = Offset(x, y)) {
                            val petalPath = Path().apply {
                                moveTo(x, y - p.size)
                                cubicTo(
                                    x + p.size * 0.9f, y - p.size * 0.4f,
                                    x + p.size * 0.7f, y + p.size * 0.8f,
                                    x, y + p.size
                                )
                                cubicTo(
                                    x - p.size * 0.7f, y + p.size * 0.8f,
                                    x - p.size * 0.9f, y - p.size * 0.4f,
                                    x, y - p.size
                                )
                                close()
                            }
                            drawPath(
                                path = petalPath,
                                color = p.tint.copy(alpha = alpha)
                            )
                        }
                    }
                }
            }

            // 3. TOP-RIGHT GLASS CLOSE BUTTON
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 14.dp, end = 18.dp)
                    .size(40.dp)
                    .bounceClick(onClick = onDismiss)
                    .clip(CircleShape)
                    .background(Color(0x550F172A))
                    .border(1.dp, Color(0x38FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Yopish",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. MAIN SCROLLABLE CONTENT (HERO CREST + LIQUID GLASS AUTH CARD)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // --- ANIME CREST & BRAND HERO ---
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    // Pulsing glowing aura behind crest
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .scale(auraPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        PrimaryBlue.copy(alpha = 0.55f),
                                        AccentViolet.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .blur(14.dp)
                    )

                    // Outer anime crest ring
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PrimaryBlue, AccentViolet)
                                )
                            )
                            .border(1.5.dp, Color(0x88FFFFFF), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "DarkMora",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Katakana anime accent badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryBlue.copy(alpha = 0.16f))
                        .border(0.8.dp, PrimaryGlow.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ダークモラ  •  ANIME UNIVERSE",
                        color = if (isDark) PrimaryGlow else PrimaryBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "DARKMORA",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Text(
                    text = tr(
                        "Sevimli animelaringiz bir akkauntda",
                        "All your favorite anime in one account",
                        "Все любимые аниме в одном аккаунте"
                    ),
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
                )

                // --- LIQUID GLASS AUTH CARD ---
                val cardBg = if (isDark) Color(0xD9111524) else Color(0xF2FFFFFF)
                val cardBorderBrush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0x40FFFFFF), Color(0x14FFFFFF), Color(0x2860A5FA))
                    } else {
                        listOf(Color(0x22000000), Color(0x0D000000), Color(0x283B82F6))
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = cardAlpha
                            translationY = cardOffsetY
                        }
                        .clip(RoundedCornerShape(28.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorderBrush, RoundedCornerShape(28.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Segmented Pill Tabs
                    val tabTitles = listOf(
                        tr("Kirish", "Sign In", "Вход"),
                        "Telegram",
                        tr("Ro'yxat", "Sign Up", "Регистр.")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF0A0D18) else Color(0xFFEEF2F6))
                            .padding(4.dp)
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            val pillBg by animateColorAsState(
                                targetValue = if (isSelected) PrimaryBlue else Color.Transparent,
                                animationSpec = tween(260),
                                label = "tabBg"
                            )
                            val pillText by animateColorAsState(
                                targetValue = if (isSelected) Color.White else TextMuted,
                                animationSpec = tween(260),
                                label = "tabText"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(pillBg)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedTab = index
                                        if (index != 1) isWaitingTelegram = false
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = pillText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Animated Tab Content
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(animationSpec = tween(300)) { it / 3 * dir } + fadeIn(tween(260)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { -it / 3 * dir } + fadeOut(tween(200)))
                        },
                        label = "authTabContent"
                    ) { tab ->
                        when (tab) {
                            // ==================================================
                            // TAB 0: LOGIN (USERNAME + PASSWORD)
                            // ==================================================
                            0 -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AnimeAuthField(
                                        value = loginUsername,
                                        onValueChange = { loginUsername = it },
                                        label = tr("Login yoki Email", "Username or Email", "Логин или Email"),
                                        leadingIcon = Icons.Default.Person,
                                        isDark = isDark
                                    )

                                    AnimeAuthField(
                                        value = loginPassword,
                                        onValueChange = { loginPassword = it },
                                        label = tr("Parol", "Password", "Пароль"),
                                        leadingIcon = Icons.Default.Lock,
                                        isPassword = true,
                                        passwordVisible = loginPasswordVisible,
                                        onTogglePassword = { loginPasswordVisible = !loginPasswordVisible },
                                        isDark = isDark
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    AnimeGradientButton(
                                        text = if (isLoggingIn) {
                                            tr("Kirilmoqda...", "Signing in...", "Вход...")
                                        } else {
                                            tr("Akkauntga kirish", "Sign In", "Войти")
                                        },
                                        loading = isLoggingIn,
                                        onClick = {
                                            if (loginUsername.isBlank() || loginPassword.isBlank()) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Login va parolni kiriting", "Enter username and password", "Введите логин и пароль"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@AnimeGradientButton
                                            }
                                            isLoggingIn = true
                                            scope.launch {
                                                try {
                                                    val res = DarkmoraApi.login(loginUsername.trim(), loginPassword.trim())
                                                    if (res.ok && res.data?.user != null) {
                                                        val u = res.data.user
                                                        val jwt = res.data.token ?: ""
                                                        sessionManager.saveSession(jwt, u)
                                                        onLoginSuccess(u)
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            res.error ?: tr("Login yoki parol noto'g'ri", "Invalid credentials", "Неверный логин или пароль"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, e.message ?: "Aloqa xatosi", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isLoggingIn = false
                                                }
                                            }
                                        }
                                    )

                                    // Quick switch to Telegram
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tr("Tezroq kirish kerakmi? ", "Want faster login? ", "Хотите быстрее? "),
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Telegram →",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { selectedTab = 1 }
                                        )
                                    }
                                }
                            }

                            // ==================================================
                            // TAB 1: TELEGRAM ONE-TAP WITH PULSE RADAR
                            // ==================================================
                            1 -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(92.dp)
                                    ) {
                                        if (isWaitingTelegram) {
                                            Box(
                                                modifier = Modifier
                                                    .size(90.dp)
                                                    .scale(auraPulse)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF229ED9).copy(alpha = 0.22f))
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFF2AABEE), Color(0xFF229ED9))
                                                    )
                                                )
                                                .border(1.5.dp, Color(0x66FFFFFF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "Telegram",
                                                tint = Color.White,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = tr(
                                            "Telegram orqali bir bosishda kirish",
                                            "One-Tap Sign In with Telegram",
                                            "Вход в один клик через Telegram"
                                        ),
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = tr(
                                            "Botga o'tib Start tugmasini bosing — ilova akkauntingizni avtomatik ochadi.",
                                            "Press Start in our Telegram bot and the app will sign you in automatically.",
                                            "Нажмите Start в Telegram-боте, и приложение автоматически откроет ваш профиль."
                                        ),
                                        color = TextMuted,
                                        fontSize = 12.5.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )

                                    if (isWaitingTelegram) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color(0xFF2AABEE),
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Text(
                                                text = tr(
                                                    "Telegram botda tasdiqlash kutilmoqda...",
                                                    "Waiting for Telegram confirmation...",
                                                    "Ожидание подтверждения в Telegram..."
                                                ),
                                                color = Color(0xFF38BDF8),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            TextButton(onClick = { isWaitingTelegram = false }) {
                                                Text(
                                                    text = tr("Bekor qilish", "Cancel", "Отмена"),
                                                    color = TextMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    } else {
                                        AnimeGradientButton(
                                            text = tr("Telegram bilan davom etish", "Continue with Telegram", "Продолжить с Telegram"),
                                            loading = false,
                                            colors = listOf(Color(0xFF2AABEE), Color(0xFF1D89C8)),
                                            icon = Icons.Default.Send,
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val initRes = DarkmoraApi.initTelegramAuth()
                                                        if (initRes.ok && initRes.data != null) {
                                                            tgPollToken = initRes.data.token
                                                            isWaitingTelegram = true
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(initRes.data.botUrl))
                                                            context.startActivity(intent)
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                initRes.error ?: "Telegram havolasini olib bo'lmadi",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, e.message ?: "Aloqa xatosi", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // ==================================================
                            // TAB 2: REGISTER (2-STEP GMAIL VERIFICATION)
                            // ==================================================
                            else -> {
                                AnimatedContent(
                                    targetState = regStep,
                                    transitionSpec = {
                                        (fadeIn(tween(240)) + slideInHorizontally { it / 4 })
                                            .togetherWith(fadeOut(tween(180)) + slideOutHorizontally { -it / 4 })
                                    },
                                    label = "regStepAnim"
                                ) { step ->
                                    if (step == 1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                            AnimeAuthField(
                                                value = regUsername,
                                                onValueChange = { regUsername = it },
                                                label = tr("Foydalanuvchi nomi", "Username", "Имя пользователя"),
                                                leadingIcon = Icons.Default.Person,
                                                isDark = isDark
                                            )

                                            AnimeAuthField(
                                                value = regPassword,
                                                onValueChange = { regPassword = it },
                                                label = tr("Parol (kamida 6 ta belgi)", "Password (min 6 chars)", "Пароль (мин. 6 симв.)"),
                                                leadingIcon = Icons.Default.Lock,
                                                isPassword = true,
                                                passwordVisible = regPasswordVisible,
                                                onTogglePassword = { regPasswordVisible = !regPasswordVisible },
                                                isDark = isDark
                                            )

                                            AnimeAuthField(
                                                value = regEmail,
                                                onValueChange = { regEmail = it },
                                                label = tr("Gmail pochta manzili", "Gmail address", "Адрес Gmail"),
                                                leadingIcon = Icons.Default.Email,
                                                keyboardType = KeyboardType.Email,
                                                isDark = isDark
                                            )

                                            Spacer(modifier = Modifier.height(2.dp))

                                            AnimeGradientButton(
                                                text = if (isSendingCode) {
                                                    tr("Kod yuborilmoqda...", "Sending code...", "Отправка кода...")
                                                } else {
                                                    tr("Tasdiqlash kodini olish", "Get Verification Code", "Получить код")
                                                },
                                                loading = isSendingCode,
                                                onClick = {
                                                    if (regUsername.trim().length < 3) {
                                                        Toast.makeText(context, "Login kamida 3 belgidan iborat bo'lsin", Toast.LENGTH_SHORT).show()
                                                        return@AnimeGradientButton
                                                    }
                                                    if (regPassword.length < 6) {
                                                        Toast.makeText(context, "Parol kamida 6 belgidan iborat bo'lsin", Toast.LENGTH_SHORT).show()
                                                        return@AnimeGradientButton
                                                    }
                                                    if (!regEmail.contains("@")) {
                                                        Toast.makeText(context, "To'g'ri email kiriting", Toast.LENGTH_SHORT).show()
                                                        return@AnimeGradientButton
                                                    }

                                                    isSendingCode = true
                                                    scope.launch {
                                                        try {
                                                            val res = DarkmoraApi.sendEmailCode(regEmail.trim())
                                                            if (res.ok) {
                                                                regStep = 2
                                                                Toast.makeText(context, "Tasdiqlash kodi emailingizga yuborildi!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, res.error ?: "Kodni yuborib bo'lmadi", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, e.message ?: "Aloqa xatosi", Toast.LENGTH_SHORT).show()
                                                        } finally {
                                                            isSendingCode = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(PrimaryBlue.copy(alpha = 0.12f))
                                                    .border(0.8.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = tr(
                                                        "Tasdiqlash kodi $regEmail manziliga yuborildi.",
                                                        "Verification code sent to $regEmail.",
                                                        "Код подтверждения отправлен на $regEmail."
                                                    ),
                                                    color = if (isDark) PrimaryGlow else PrimaryBlue,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            AnimeAuthField(
                                                value = regCode,
                                                onValueChange = { if (it.length <= 6) regCode = it },
                                                label = tr("6 xonali tasdiqlash kodi", "6-digit code", "6-значный код"),
                                                leadingIcon = Icons.Default.Key,
                                                keyboardType = KeyboardType.Number,
                                                isDark = isDark
                                            )

                                            AnimeGradientButton(
                                                text = if (isVerifyingCode) {
                                                    tr("Tekshirilmoqda...", "Verifying...", "Проверка...")
                                                } else {
                                                    tr("Tasdiqlash va kirish", "Verify & Create Account", "Подтвердить и войти")
                                                },
                                                loading = isVerifyingCode,
                                                onClick = {
                                                    if (regCode.trim().length < 4) {
                                                        Toast.makeText(context, "Kodni to'liq kiriting", Toast.LENGTH_SHORT).show()
                                                        return@AnimeGradientButton
                                                    }

                                                    isVerifyingCode = true
                                                    scope.launch {
                                                        try {
                                                            val res = DarkmoraApi.register(
                                                                username = regUsername.trim(),
                                                                pass = regPassword.trim(),
                                                                email = regEmail.trim(),
                                                                code = regCode.trim()
                                                            )
                                                            if (res.ok && res.data?.user != null) {
                                                                val u = res.data.user
                                                                val jwt = res.data.token ?: ""
                                                                sessionManager.saveSession(jwt, u)
                                                                onLoginSuccess(u)
                                                            } else {
                                                                Toast.makeText(context, res.error ?: "Kod noto'g'ri", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, e.message ?: "Aloqa xatosi", Toast.LENGTH_SHORT).show()
                                                        } finally {
                                                            isVerifyingCode = false
                                                        }
                                                    }
                                                }
                                            )

                                            TextButton(
                                                onClick = { regStep = 1 },
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowBack,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = tr("Orqaga qaytish", "Go Back", "Назад"),
                                                    color = TextMuted,
                                                    fontSize = 12.5.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AnimeAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    isDark: Boolean,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val containerColor = if (isDark) Color(0xFF0B0F1C) else Color(0xFFF8FAFC)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = PrimaryGlow,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = if (isDark) Color(0x24FFFFFF) else Color(0x20000000),
            focusedLabelColor = PrimaryGlow,
            unfocusedLabelColor = TextMuted,
            cursorColor = PrimaryBlue
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AnimeGradientButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    colors: List<Color> = listOf(PrimaryBlue, AccentViolet),
    icon: ImageVector? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .bounceClick {
                if (!loading) onClick()
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (loading) colors.map { it.copy(alpha = 0.65f) } else colors
                )
            )
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.4.dp,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
