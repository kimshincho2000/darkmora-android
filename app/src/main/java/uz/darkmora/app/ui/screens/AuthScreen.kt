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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.data.UserProfile
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.tr
import kotlin.math.*

// ============================================================================
// MINIMALIST MONOCHROME WELCOME & AUTH EXPERIENCE (1:1 PARTICLE VORTEX DESIGN)
// ============================================================================

private data class WelcomeParticle(
    val nx: Float,
    val ny: Float,
    val rand: Float,
    val rand2: Float,
    val angleOffset: Float,
    val driftX: Float,
    val driftY: Float
)

@Composable
fun AuthDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val scope = rememberCoroutineScope()

    // "welcome" | "login" | "register" | "telegram"
    var activeScreen by remember { mutableStateOf("welcome") }

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

    fun startTelegramAuth() {
        activeScreen = "telegram"
        scope.launch {
            isWaitingTelegram = true
            try {
                val initRes = DarkmoraApi.initTelegramAuth()
                if (initRes.ok && !initRes.token.isNullOrBlank() && !initRes.botUrl.isNullOrBlank()) {
                    tgPollToken = initRes.token
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(initRes.botUrl))
                    context.startActivity(intent)
                } else {
                    isWaitingTelegram = false
                    activeScreen = "welcome"
                    Toast.makeText(
                        context,
                        initRes.error ?: tr("Telegram ulanishda xato", "Telegram auth error", "Ошибка Telegram"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                isWaitingTelegram = false
                activeScreen = "welcome"
                Toast.makeText(
                    context,
                    tr("Server bilan aloqa yo'q", "Server unreachable", "Нет связи с сервером"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            isWaitingTelegram = false
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. TOP BAR: Centered white avatar badge + Back / Close buttons
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    if (activeScreen != "welcome") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(38.dp)
                                .bounceClick {
                                    isWaitingTelegram = false
                                    activeScreen = "welcome"
                                }
                                .clip(CircleShape)
                                .background(Color(0xFF18181A))
                                .border(1.dp, Color(0x1FFFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = tr("Orqaga", "Back", "Назад"),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Centered crisp white user badge (1:1 from concept video)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "DarkMora",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(38.dp)
                            .bounceClick {
                                isWaitingTelegram = false
                                onDismiss()
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF18181A))
                            .border(1.dp, Color(0x1FFFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Yopish", "Close", "Закрыть"),
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. CENTERPIECE: 1:1 Generative White Micro-Dash Vortex / Dissolving Square Canvas
                val canvasHeight by animateDpAsState(
                    targetValue = if (activeScreen == "welcome") 290.dp else 128.dp,
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                    label = "particle_canvas_height"
                )
                WelcomeParticleField(
                    compact = activeScreen != "welcome",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                )

                // 3. CONTENT SWITCHER
                AnimatedContent(
                    targetState = activeScreen,
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 8 })
                            .togetherWith(fadeOut(tween(180)))
                    },
                    label = "auth_screen_content",
                    modifier = Modifier.fillMaxWidth()
                ) { screen ->
                    when (screen) {
                        // ====================================================
                        // WELCOME SCREEN (1:1 MATCH TO CONCEPT VIDEO)
                        // ====================================================
                        "welcome" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = tr("Xush kelibsiz", "Welcome", "Добро пожаловать"),
                                    color = Color.White,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.6).sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = tr(
                                        "Anime sayohatingiz shu yerdan boshlanadi",
                                        "Your journey starts from here",
                                        "Ваше аниме-путешествие начинается здесь"
                                    ),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(34.dp))

                                // Primary Pure White Pill Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .bounceClick { startTelegramAuth() }
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = tr("Telegram orqali kirish", "Continue with Telegram", "Войти через Telegram"),
                                            color = Color.Black,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Secondary Dark Charcoal Pill Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .bounceClick { activeScreen = "login" }
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF1C1C1E))
                                        .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = tr("Login va Parol bilan kirish", "Continue with Login", "Войти по логину и паролю"),
                                            color = Color.White,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = tr("Akkauntingiz yo'qmi? Ro'yxatdan o'tish", "No account? Create one", "Нет аккаунта? Регистрация"),
                                    color = Color(0xFFB8B8BE),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            regStep = 1
                                            activeScreen = "register"
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = tr(
                                        "Davom etish orqali DarkMora Foydalanish shartlari va\nMaxfiylik siyosatiga rozilik bildirasiz.",
                                        "By pressing \"Continue\", you agree\nto our Terms of Service and Privacy Policy.",
                                        "Нажимая «Продолжить», вы соглашаетесь\nс Условиями и Политикой конфиденциальности."
                                    ),
                                    color = Color(0xFF636366),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // ====================================================
                        // TELEGRAM WAITING VIEW
                        // ====================================================
                        "telegram" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0xFF111113))
                                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(26.dp))
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = tr("Telegram tasdiqlash kutilmoqda...", "Waiting for Telegram confirmation...", "Ожидание подтверждения в Telegram..."),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = tr(
                                        "Telegram botda «Start» tugmasini bosing — ilova avtomatik kiradi.",
                                        "Press «Start» in the Telegram bot — the app will sign you in automatically.",
                                        "Нажмите «Start» в боте Telegram — вход выполнится автоматически."
                                    ),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .bounceClick {
                                            isWaitingTelegram = false
                                            activeScreen = "welcome"
                                        }
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF1C1C1E))
                                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tr("Bekor qilish", "Cancel", "Отмена"),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // ====================================================
                        // MONOCHROME LOGIN FORM
                        // ====================================================
                        "login" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0xFF111113))
                                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(26.dp))
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = tr("Kirish", "Sign In", "Вход"),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                MonochromeAuthInput(
                                    value = loginUsername,
                                    onValueChange = { loginUsername = it },
                                    placeholder = tr("Login yoki Email", "Username or Email", "Логин или Email"),
                                    icon = Icons.Default.Person
                                )

                                MonochromeAuthInput(
                                    value = loginPassword,
                                    onValueChange = { loginPassword = it },
                                    placeholder = tr("Parol", "Password", "Пароль"),
                                    icon = Icons.Default.Lock,
                                    isPassword = true,
                                    passwordVisible = loginPasswordVisible,
                                    onTogglePassword = { loginPasswordVisible = !loginPasswordVisible }
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                MonochromeWhiteButton(
                                    text = tr("Kirish", "Sign In", "Войти"),
                                    isLoading = isLoggingIn,
                                    onClick = {
                                        if (loginUsername.isBlank() || loginPassword.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                tr("Login va parolni kiriting", "Enter username & password", "Введите логин и пароль"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@MonochromeWhiteButton
                                        }
                                        scope.launch {
                                            isLoggingIn = true
                                            try {
                                                val res = DarkmoraApi.login(loginUsername.trim(), loginPassword)
                                                if (res.ok && res.data?.user != null) {
                                                    val u = res.data.user!!
                                                    val jwt = res.data.token ?: ""
                                                    sessionManager.saveSession(jwt, u)
                                                    onLoginSuccess(u)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        res.error ?: tr("Login yoki parol xato", "Invalid credentials", "Неверный логин или пароль"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Server bilan aloqa yo'q", "Server unreachable", "Нет связи с сервером"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                isLoggingIn = false
                                            }
                                        }
                                    }
                                )

                                Text(
                                    text = tr("Akkauntingiz yo'qmi? Ro'yxatdan o'tish", "No account? Register", "Нет аккаунта? Регистрация"),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            regStep = 1
                                            activeScreen = "register"
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }

                        // ====================================================
                        // MONOCHROME REGISTER FORM (EMAIL OTP)
                        // ====================================================
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0xFF111113))
                                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(26.dp))
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                Text(
                                    text = tr("Ro'yxatdan o'tish", "Create Account", "Регистрация"),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (regStep == 1) {
                                    MonochromeAuthInput(
                                        value = regUsername,
                                        onValueChange = { regUsername = it },
                                        placeholder = tr("Nik name (login)", "Username", "Имя пользователя"),
                                        icon = Icons.Default.Person
                                    )

                                    MonochromeAuthInput(
                                        value = regEmail,
                                        onValueChange = { regEmail = it },
                                        placeholder = "Email (example@gmail.com)",
                                        icon = Icons.Default.Email,
                                        keyboardType = KeyboardType.Email
                                    )

                                    MonochromeAuthInput(
                                        value = regPassword,
                                        onValueChange = { regPassword = it },
                                        placeholder = tr("Parol (kamida 6 belgi)", "Password (min 6 chars)", "Пароль (мин. 6 символов)"),
                                        icon = Icons.Default.Lock,
                                        isPassword = true,
                                        passwordVisible = regPasswordVisible,
                                        onTogglePassword = { regPasswordVisible = !regPasswordVisible }
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    MonochromeWhiteButton(
                                        text = tr("Kod yuborish", "Send Code", "Отправить код"),
                                        isLoading = isSendingCode,
                                        onClick = {
                                            if (regUsername.isBlank() || regEmail.isBlank() || regPassword.length < 6) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Barcha maydonlarni to'g'ri to'ldiring (parol 6+)", "Fill all fields (password 6+)", "Заполните все поля (пароль 6+)"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@MonochromeWhiteButton
                                            }
                                            scope.launch {
                                                isSendingCode = true
                                                try {
                                                    val res = DarkmoraApi.sendEmailCode(regEmail.trim())
                                                    if (res.ok) {
                                                        regStep = 2
                                                        Toast.makeText(
                                                            context,
                                                            tr("Tasdiqlash kodi emailga yuborildi!", "Code sent to your email!", "Код отправлен на почту!"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            res.error ?: tr("Kod yuborishda xato", "Failed to send code", "Ошибка отправки кода"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (_: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        tr("Server bilan aloqa yo'q", "Server unreachable", "Нет связи с сервером"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    isSendingCode = false
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "${tr("Tasdiqlash kodi yuborildi:", "Verification code sent to:", "Код отправлен на:")} $regEmail",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 12.5.sp
                                    )

                                    MonochromeAuthInput(
                                        value = regCode,
                                        onValueChange = { regCode = it.take(6) },
                                        placeholder = tr("6 xonali kod", "6-digit code", "6-значный код"),
                                        icon = Icons.Default.Pin,
                                        keyboardType = KeyboardType.Number
                                    )

                                    MonochromeWhiteButton(
                                        text = tr("Tasdiqlash va Kirish", "Verify & Sign Up", "Подтвердить и войти"),
                                        isLoading = isVerifyingCode,
                                        onClick = {
                                            if (regCode.length < 4) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Kodni kiriting", "Enter the code", "Введите код"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@MonochromeWhiteButton
                                            }
                                            scope.launch {
                                                isVerifyingCode = true
                                                try {
                                                    val res = DarkmoraApi.register(
                                                        regUsername.trim(),
                                                        regPassword,
                                                        regEmail.trim(),
                                                        regCode.trim()
                                                    )
                                                    if (res.ok && res.data?.user != null) {
                                                        val u = res.data.user!!
                                                        val jwt = res.data.token ?: ""
                                                        sessionManager.saveSession(jwt, u)
                                                        onLoginSuccess(u)
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            res.error ?: tr("Kod noto'g'ri", "Invalid code", "Неверный код"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (_: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        tr("Server bilan aloqa yo'q", "Server unreachable", "Нет связи с сервером"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    isVerifyingCode = false
                                                }
                                            }
                                        }
                                    )

                                    Text(
                                        text = tr("← Ortga qaytish", "← Back", "← Назад"),
                                        color = Color(0xFF8E8E93),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { regStep = 1 }
                                            .padding(vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = tr("Akkauntingiz bormi? Kirish", "Already have an account? Sign In", "Уже есть аккаунт? Войти"),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { activeScreen = "login" }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WelcomeParticleField(
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val grid = if (compact) 14 else 21
    val particles = remember(grid) {
        buildList(grid * grid) {
            for (gy in 0 until grid) {
                for (gx in 0 until grid) {
                    val nx = (gx.toFloat() / (grid - 1)) * 2f - 1f
                    val ny = (gy.toFloat() / (grid - 1)) * 2f - 1f
                    val s1 = sin(gx * 127.1 + gy * 311.7) * 43758.5453
                    val r1 = (s1 - floor(s1)).toFloat()
                    val s2 = sin(gx * 269.5 + gy * 183.3) * 43758.5453
                    val r2 = (s2 - floor(s2)).toFloat()
                    add(
                        WelcomeParticle(
                            nx = nx,
                            ny = ny,
                            rand = r1,
                            rand2 = r2,
                            angleOffset = (r1 - 0.5f) * (2.0 * PI).toFloat(),
                            driftX = (r1 - 0.5f) * 1.45f,
                            driftY = (r2 - 0.5f) * 1.45f
                        )
                    )
                }
            }
        }
    }

    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                elapsedSec = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val boxHalf = min(w, h) * (if (compact) 0.36f else 0.34f)

        val t = elapsedSec
        val cycle = (t % 8f) / 8f
        val vortexStrength = max(0f, sin(cycle * (2.0 * PI).toFloat() - 0.4f))
        val wavePos = sin(t * 0.95f) * 1.25f

        val baseDash = if (compact) 11f else 17f
        val strokeW = if (compact) 2.2f else 2.8f

        for (i in particles.indices) {
            val p = particles[i]

            val distFromWave = p.nx * 0.85f + p.ny * 0.25f - wavePos
            val dissolve = ((1f - abs(distFromWave) * 1.15f) * (1f - vortexStrength * 0.75f)).coerceIn(0f, 1f)
            val scatterAmt = dissolve * dissolve

            val r = hypot(p.nx, p.ny) + 0.001f
            val baseAngle = atan2(p.ny, p.nx)
            val swirlTwist = vortexStrength * (1.15f / (r + 0.45f)) * sin(t * 0.75f)

            val cosSw = cos(swirlTwist * 0.35f)
            val sinSw = sin(swirlTwist * 0.35f)
            val sx = p.nx * cosSw - p.ny * sinSw
            val sy = p.nx * sinSw + p.ny * cosSw

            val expand = 1f + vortexStrength * 0.16f
            val px = cx + sx * boxHalf * expand +
                p.driftX * scatterAmt * boxHalf * 0.85f -
                scatterAmt * boxHalf * 0.22f
            val py = cy + sy * boxHalf * expand +
                p.driftY * scatterAmt * boxHalf * 0.75f +
                sin(t * 2.1f + p.rand * 6.283f) * scatterAmt * 18f

            val vortexAngle = baseAngle + (PI * 0.5).toFloat() + sin(r * 3.5f - t * 1.6f) * 0.45f
            val tumbleAngle = p.angleOffset + t * (1.8f + p.rand * 2.2f)
            val angle = vortexAngle * (1f - scatterAmt * 0.85f) + tumbleAngle * (scatterAmt * 0.85f)

            val dashLen = baseDash * (0.55f + 0.45f * vortexStrength + 0.35f * scatterAmt)
            val edgeFade = (1f - max(0f, r - 0.92f) * 1.2f).coerceAtLeast(0.15f)
            val alpha = ((0.90f - scatterAmt * 0.35f) * edgeFade).coerceIn(0.12f, 0.96f)

            val dx = cos(angle) * dashLen * 0.5f
            val dy = sin(angle) * dashLen * 0.5f

            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(px - dx, py - dy),
                end = Offset(px + dx, py + dy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun MonochromeAuthInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = Color(0xFF636366),
                fontSize = 14.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        } else null,
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0x88FFFFFF),
            unfocusedBorderColor = Color(0x1EFFFFFF),
            focusedContainerColor = Color(0xFF18181A),
            unfocusedContainerColor = Color(0xFF161618),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MonochromeWhiteButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .bounceClick { if (!isLoading) onClick() }
            .clip(RoundedCornerShape(50))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 2.2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
