package uz.darkmora.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.data.*
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.theme.*
import java.io.File

@Composable
fun ProfileScreen(
    onNavigateToFavorites: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var user by remember { mutableStateOf(sessionManager.getUser()) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditPhotoDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    fun refreshUserData() {
        isLoggedIn = sessionManager.isLoggedIn()
        user = sessionManager.getUser()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = 90.dp)
    ) {
        Text(
            text = "Mening Profilim",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (!isLoggedIn) {
            // Guest Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF141926), Color(0xFF0F121C))
                        )
                    )
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color(0x332563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = PrimaryGlow,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "DarkMora ga xush kelibsiz!",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Sevimlilarni saqlash, profil sozlamalari va yangi imkoniyatlar uchun tizimga kiring.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = { showAuthDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kirish / Ro'yxatdan o'tish",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Logged in User Card
            val u = user
            val displayName = u?.getEffectiveName() ?: "Foydalanuvchi"
            val username = u?.username ?: ""
            val emailOrId = u?.email?.takeIf { it.isNotBlank() } ?: "ID: #${u?.userCode ?: u?.dbId ?: "100"}"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF141A29), Color(0xFF0D101A))
                        )
                    )
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar with edit button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2538))
                            .border(2.dp, PrimaryBlue, CircleShape)
                            .clickable { showEditPhotoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!u?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = u!!.photoUrl,
                                contentDescription = displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = displayName.take(1).uppercase(),
                                color = PrimaryGlow,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Camera icon overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x33000000)),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Rasm", tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Tahrirlash",
                                tint = TextDim,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showEditNameDialog = true }
                            )
                        }

                        if (username.isNotBlank()) {
                            Text(
                                text = "@$username",
                                color = PrimaryGlow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = emailOrId,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickStatCard(
                    title = "Sevimlilar",
                    value = "${sessionManager.getFavorites().size} ta",
                    icon = Icons.Default.Bookmark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToFavorites
                )
                QuickStatCard(
                    title = "Holat",
                    value = if (u?.isAdmin == true) "Admin" else "A'zo",
                    icon = Icons.Default.Shield,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                QuickStatCard(
                    title = "Versiya",
                    value = "v2.4.1",
                    icon = Icons.Default.Info,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Options List Section
        Text(
            text = "Ilova va Sozlamalar",
            color = TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        ProfileMenuOption(
            icon = Icons.Default.SystemUpdate,
            title = "Yangilanishlarni tekshirish",
            subtitle = if (isCheckingUpdate) "Tekshirilmoqda..." else "Ilovaning so'nggi versiyasi",
            onClick = {
                if (!isCheckingUpdate) {
                    isCheckingUpdate = true
                    scope.launch {
                        try {
                            val res = DarkmoraApi.checkUpdate()
                            if (res.ok && res.data != null) {
                                if (res.data.versionCode > 16) {
                                    updateInfo = res.data
                                    showUpdateDialog = true
                                } else {
                                    Toast.makeText(context, "Sizda eng so'nggi versiya o'rnatilgan (v2.4.1)", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Versiya tekshirishda xatolik yuz berdi", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Aloqa xatosi: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isCheckingUpdate = false
                        }
                    }
                }
            }
        )

        ProfileMenuOption(
            icon = Icons.Default.Bookmark,
            title = "Mening Sevimlilarim",
            subtitle = "Saqlangan barcha animelar",
            onClick = onNavigateToFavorites
        )

        ProfileMenuOption(
            icon = Icons.Default.DeleteOutline,
            title = "Keshni tozalash",
            subtitle = "Rasmlar va vaqtinchalik fayllarni tozalash",
            onClick = {
                scope.launch {
                    try {
                        context.cacheDir.deleteRecursively()
                        Toast.makeText(context, "Kesh muvaffaqiyatli tozalandi!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Xatolik: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        if (isLoggedIn) {
            ProfileMenuOption(
                icon = Icons.Default.Logout,
                title = "Akkauntdan chiqish",
                subtitle = "Tizimdan xavfsiz chiqish",
                titleColor = Color(0xFFEF4444),
                onClick = {
                    sessionManager.clearSession()
                    refreshUserData()
                    Toast.makeText(context, "Akkauntdan chiqildi", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // --- DIALOGS ---

    if (showAuthDialog) {
        AuthDialog(
            onDismiss = { showAuthDialog = false },
            onLoginSuccess = { loggedUser ->
                refreshUserData()
                showAuthDialog = false
                Toast.makeText(context, "Xush kelibsiz, ${loggedUser.getEffectiveName()}!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showEditNameDialog && user != null) {
        var newName by remember { mutableStateOf(user?.displayName ?: user?.username ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showEditNameDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Ismni tahrirlash", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Yangi ism / Taxallus") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showEditNameDialog = false }) {
                            Text("Bekor qilish", color = TextDim)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            val token = sessionManager.getToken() ?: ""
                                            val res = DarkmoraApi.updateProfile(token, displayName = newName)
                                            if (res.ok) {
                                                val updated = user!!.copy(displayName = newName)
                                                sessionManager.updateProfile(updated)
                                                refreshUserData()
                                                showEditNameDialog = false
                                                Toast.makeText(context, "Ism muvaffaqiyatli yangilandi", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, res.error ?: "Xatolik yuz berdi", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isSaving) "Saqlanmoqda..." else "Saqlash", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showEditPhotoDialog && user != null) {
        var photoUrlInput by remember { mutableStateOf(user?.photoUrl ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showEditPhotoDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Avatar rasmini o'zgartirish", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    Text(
                        text = "To'g'ridan-to'g'ri rasm havolasini (URL) kiriting:",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = photoUrlInput,
                        onValueChange = { photoUrlInput = it },
                        label = { Text("Rasm URL manzili") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showEditPhotoDialog = false }) {
                            Text("Bekor qilish", color = TextDim)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (photoUrlInput.isNotBlank()) {
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            val updated = user!!.copy(photoUrl = photoUrlInput)
                                            sessionManager.updateProfile(updated)
                                            refreshUserData()
                                            showEditPhotoDialog = false
                                            Toast.makeText(context, "Avatar saqlandi", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isSaving) "Saqlanmoqda..." else "Saqlash", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        UpdateAppDialog(
            info = updateInfo!!,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

@Composable
fun QuickStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = PrimaryGlow, modifier = Modifier.size(20.dp))
            Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = title, color = TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
fun ProfileMenuOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x222563EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = if (titleColor != Color.White) titleColor else PrimaryGlow, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = TextDim, fontSize = 11.sp)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

// ============================================================================
// AUTH DIALOG: Telegram One-Tap + Gmail 3-Step Register + Login
// ============================================================================

@Composable
fun AuthDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Telegram, 1 = Gmail Register, 2 = Oddiy Kirish

    // Gmail Register state
    var regUsername by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regCode by remember { mutableStateOf("") }
    var regStep by remember { mutableIntStateOf(1) } // 1 = Input data, 2 = Verify 6-digit code
    var isSendingCode by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }

    // Standard Login state
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Telegram Auth state
    var isWaitingTelegram by remember { mutableStateOf(false) }
    var tgPollToken by remember { mutableStateOf<String?>(null) }

    // Auto-poll telegram
    LaunchedEffect(tgPollToken) {
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
            } catch (e: Exception) {
                // ignore and continue polling
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F121C))
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DarkMora Akkaunt",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Yopish", tint = TextDim)
                    }
                }

                // Liquid Glass Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161B2B))
                        .padding(4.dp)
                ) {
                    val tabs = listOf("Telegram", "Gmail", "Kirish")
                    tabs.forEachIndexed { index, label ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) PrimaryBlue else Color.Transparent)
                                .clickable {
                                    selectedTab = index
                                    isWaitingTelegram = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // TAB 0: TELEGRAM AUTH
                if (selectedTab == 0) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF229ED9).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF229ED9), modifier = Modifier.size(32.dp))
                        }

                        Text(
                            text = "Telegram orqali bir bosishda kirish",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Maxsus start havolasi orqali Telegram botga o'tasiz va ilova avtomatik tarzda profilingizni ochadi.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        if (isWaitingTelegram) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(color = PrimaryGlow, modifier = Modifier.size(32.dp))
                                Text(
                                    text = "Telegram botda tasdiqlash kutilmoqda...",
                                    color = PrimaryGlow,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val initRes = DarkmoraApi.initTelegramAuth()
                                            if (initRes.ok && initRes.data != null) {
                                                val token = initRes.data.token
                                                val botUrl = initRes.data.botUrl
                                                tgPollToken = token
                                                isWaitingTelegram = true

                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(botUrl))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, initRes.error ?: "Telegram havolasini olib bo'lmadi", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Telegram bilan davom etish",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // TAB 1: GMAIL REGISTER
                if (selectedTab == 1) {
                    if (regStep == 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = regUsername,
                                onValueChange = { regUsername = it },
                                label = { Text("Foydalanuvchi nomi") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = CardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = { regPassword = it },
                                label = { Text("Parol (kamida 6 ta belgi)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = CardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                label = { Text("Gmail pochta manzili") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = CardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (regUsername.length < 3) {
                                        Toast.makeText(context, "Login kamida 3 belgidan iborat bo'lsin", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (regPassword.length < 6) {
                                        Toast.makeText(context, "Parol kamida 6 belgidan iborat bo'lsin", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (!regEmail.contains("@")) {
                                        Toast.makeText(context, "To'g'ri email kiriting", Toast.LENGTH_SHORT).show()
                                        return@Button
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
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSendingCode = false
                                        }
                                    }
                                },
                                enabled = !isSendingCode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isSendingCode) "Kod yuborilmoqda..." else "Ro'yxatdan o'tish (Kod olish)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Step 2: Enter 6-digit OTP code
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Tasdiqlash kodi $regEmail manziliga yuborildi.",
                                color = PrimaryGlow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedTextField(
                                value = regCode,
                                onValueChange = { if (it.length <= 6) regCode = it },
                                label = { Text("6 xonali tasdiqlash kodi") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = CardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (regCode.length < 4) {
                                        Toast.makeText(context, "Kodni to'liq kiriting", Toast.LENGTH_SHORT).show()
                                        return@Button
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
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isVerifyingCode = false
                                        }
                                    }
                                },
                                enabled = !isVerifyingCode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isVerifyingCode) "Tekshirilmoqda..." else "Tasdiqlash va Ro'yxatdan o'tish", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = { regStep = 1 },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Orqaga qaytish", color = TextDim, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // TAB 2: STANDARD LOGIN
                if (selectedTab == 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = loginUsername,
                            onValueChange = { loginUsername = it },
                            label = { Text("Login yoki Email") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            label = { Text("Parol") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (loginUsername.isBlank() || loginPassword.isBlank()) {
                                    Toast.makeText(context, "Login va parolni kiriting", Toast.LENGTH_SHORT).show()
                                    return@Button
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
                                            Toast.makeText(context, res.error ?: "Login yoki parol noto'g'ri", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoggingIn = false
                                    }
                                }
                            },
                            enabled = !isLoggingIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isLoggingIn) "Kirilmoqda..." else "Kirish", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// IN-APP AUTO-UPDATE DIALOG
// ============================================================================

@Composable
fun UpdateAppDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D101A))
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x332563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = PrimaryGlow, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Yangi Versiya Mavjud!", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Versiya: ${info.versionName} (Build ${info.versionCode})", color = PrimaryGlow, fontSize = 12.sp)
                    }
                }

                info.changelog?.let { log ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141824))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = log,
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                if (isDownloading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) {
                        LinearProgressIndicator(color = PrimaryGlow, modifier = Modifier.fillMaxWidth())
                        Text("APK yuklanmoqda... Iltimos kuting.", color = TextDim, fontSize = 12.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!info.isForce) {
                            TextButton(onClick = onDismiss) {
                                Text("Keyinroq", color = TextDim)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = {
                                isDownloading = true
                                downloadAndInstallApk(context, info.apkUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yangilash", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun downloadAndInstallApk(context: Context, apkUrl: String) {
    try {
        val parsedUri = Uri.parse(apkUrl)
        val host = parsedUri.host?.lowercase() ?: ""
        if (parsedUri.scheme?.lowercase() != "https" || (!host.endsWith("darkmora.uz") && !host.endsWith("wwwz.uz"))) {
            Toast.makeText(context, "Xavfsizlik: Noma'lum manbadan APK yuklash bloklandi!", Toast.LENGTH_SHORT).show()
            return
        }

        val targetApkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DarkMora-update.apk")
        if (targetApkFile.exists()) {
            targetApkFile.delete()
        }

        val request = DownloadManager.Request(parsedUri)
            .setTitle("DarkMora Yangilanish")
            .setDescription("Yangi versiya yuklanmoqda...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "DarkMora-update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DarkMora-update.apk")
                    if (apkFile.exists()) {
                        val apkUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        Toast.makeText(context, "Yuklab olish boshlandi...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Yuklashda xatolik: ${e.message}", Toast.LENGTH_SHORT).show()
        // Fallback: open in browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    }
}
