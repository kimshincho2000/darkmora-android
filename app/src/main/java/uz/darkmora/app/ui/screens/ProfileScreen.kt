package uz.darkmora.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.darkmora.app.BuildConfig
import uz.darkmora.app.data.*
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.appear
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun ProfileScreen(
    onNavigateToFavorites: () -> Unit,
    onAnimeClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var user by remember { mutableStateOf(sessionManager.getUser()) }
    var currentLang by remember { mutableStateOf(sessionManager.getLanguage()) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var showManageProfileDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    // Fetch latest animes for Notifications sheet
    var notifList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var readNotifIds by remember { mutableStateOf(sessionManager.getReadNotificationIds()) }

    LaunchedEffect(Unit) {
        try {
            val res = DarkmoraApi.getHome()
            if (res.ok && res.data != null) {
                notifList = (res.data.latest ?: res.data.latestEpisodes ?: res.data.trending ?: emptyList()).take(12)
            }
        } catch (_: Exception) {}
    }

    fun refreshUserData() {
        isLoggedIn = sessionManager.isLoggedIn()
        user = sessionManager.getUser()
    }

    // Native Android Photo Picker (Gallery Image Selector -> Compress -> Upload Base64 or Save Locally)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch {
                try {
                    val resultPair = withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (originalBitmap != null) {
                            // Scale to max 512x512 square-ish avatar
                            val maxDim = 512
                            val ratio = minOf(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height, 1f)
                            val w = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
                            val h = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
                            val scaled = Bitmap.createScaledBitmap(originalBitmap, w, h, true)

                            // Save local file copy for instant offline/local display
                            val localFile = File(context.filesDir, "user_avatar_${System.currentTimeMillis()}.jpg")
                            localFile.outputStream().use { fos ->
                                scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                            }

                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            val b64 = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            Pair(localFile.absolutePath, b64)
                        } else null
                    }

                    if (resultPair != null) {
                        val (localPath, base64Data) = resultPair
                        val token = sessionManager.getToken()
                        var finalPhotoUrl = "file://$localPath"

                        if (!token.isNullOrBlank()) {
                            try {
                                val apiRes = DarkmoraApi.updateProfile(token = token, photoBase64 = base64Data)
                                if (apiRes.ok && !apiRes.photoUrl.isNullOrBlank()) {
                                    finalPhotoUrl = apiRes.photoUrl
                                }
                            } catch (_: Exception) {}
                        }

                        val currentU = user ?: UserProfile(username = "user", displayName = "Foydalanuvchi")
                        val updatedU = currentU.copy(photoUrl = finalPhotoUrl)
                        sessionManager.updateProfile(updatedU)
                        refreshUserData()
                        Toast.makeText(context, "Profil rasmi galereyadan o'rnatildi!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rasmni o'qib bo'lmadi", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Xatolik: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    fun pickAvatarFromGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 120.dp)
    ) {
        // ====================================================================
        // CENTERED TOP TITLE ("Profile" - 1:1 AS IN PHOTO, LOCALIZED)
        // ====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tr("Profil", "Profile", "Профиль"),
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ====================================================================
        // TOP PROFILE CARD (Avatar Circle on Left + Name & Email/Subtext on Right)
        // ====================================================================
        val u = user
        val displayName = if (isLoggedIn && u != null) u.getEffectiveName() else tr("Mehmon Foydalanuvchi", "Guest User", "Гость")
        val subTitle = if (isLoggedIn && u != null) {
            u.email?.takeIf { it.isNotBlank() }
                ?: u.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                ?: "ID: #${u.userCode ?: u.dbId ?: "100"}"
        } else {
            tr("Tizimga kirish uchun bosing", "Tap to sign in", "Нажмите, чтобы войти")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .appear(0)
                .clip(RoundedCornerShape(22.dp))
                .background(BgCard)
                .border(0.8.dp, CardBorder, RoundedCornerShape(22.dp))
                .clickable {
                    if (isLoggedIn) {
                        showManageProfileDialog = true
                    } else {
                        showAuthDialog = true
                    }
                }
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circular Avatar (Tap to pick directly from Gallery!)
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(BgSurface)
                        .border(1.dp, CardBorder, CircleShape)
                        .clickable {
                            if (isLoggedIn) {
                                pickAvatarFromGallery()
                            } else {
                                showAuthDialog = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(
                            color = PrimaryBlue,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else if (!u?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = u!!.photoUrl,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = displayName.take(1).uppercase(),
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Small Camera Badge at Bottom Right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(TextPrimary)
                            .border(1.5.dp, BgCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Galereyadan rasm tanlash",
                            tint = BgSurface,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = displayName,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subTitle,
                        color = TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TextPrimary)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = tr("Kirish", "Sign In", "Войти"),
                            color = BgSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ====================================================================
        // SECTION 1: ACCOUNT (1:1 AS IN PHOTO, REACTIVE TO LANGUAGE)
        // ====================================================================
        Text(
            text = tr("Akkaunt", "Account", "Аккаунт"),
            color = TextMuted,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        val unreadNotifs = notifList.count { !readNotifIds.contains(it.id) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appear(1)
                .clip(RoundedCornerShape(22.dp))
                .background(BgCard)
                .border(0.8.dp, CardBorder, RoundedCornerShape(22.dp))
        ) {
            CleanProfileRow(
                icon = Icons.Outlined.AccountCircle,
                title = tr("Profilni boshqarish", "Manage Profile", "Управление профилем"),
                trailingText = if (isLoggedIn) tr("Tahrirlash", "Edit", "Изменить") else tr("Kirish", "Sign In", "Войти"),
                onClick = {
                    if (isLoggedIn) showManageProfileDialog = true else showAuthDialog = true
                }
            )
            ProfileRowDivider()

            CleanProfileRow(
                icon = Icons.Outlined.Lock,
                title = tr("Parol va Xavfsizlik", "Password & Security", "Пароль и безопасность"),
                onClick = {
                    if (isLoggedIn) showPasswordDialog = true else showAuthDialog = true
                }
            )
            ProfileRowDivider()

            CleanProfileRow(
                icon = Icons.Outlined.Notifications,
                title = tr("Bildirishnomalar", "Notifications", "Уведомления"),
                trailingText = if (unreadNotifs > 0) "$unreadNotifs ${tr("yangi", "new", "новых")}" else null,
                onClick = { showNotificationsDialog = true }
            )
            ProfileRowDivider()

            CleanProfileRow(
                icon = Icons.Outlined.Translate,
                title = tr("Ilova tili", "Language", "Язык"),
                trailingText = AppThemeState.language,
                onClick = { showLanguageDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ====================================================================
        // SECTION 2: PREFERENCES (1:1 AS IN PHOTO, REACTIVE TO LANGUAGE)
        // ====================================================================
        Text(
            text = tr("Sozlamalar", "Preferences", "Настройки"),
            color = TextMuted,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appear(2)
                .clip(RoundedCornerShape(22.dp))
                .background(BgCard)
                .border(0.8.dp, CardBorder, RoundedCornerShape(22.dp))
        ) {
            CleanProfileRow(
                icon = Icons.Outlined.ListAlt,
                title = tr("Ilova haqida", "About Us", "О приложении"),
                trailingText = "v${BuildConfig.VERSION_NAME}",
                onClick = { showAboutDialog = true }
            )
            ProfileRowDivider()

            CleanProfileRow(
                icon = Icons.Outlined.Contrast,
                title = tr("Mavzu", "Theme", "Тема"),
                trailingText = if (AppThemeState.isDark) tr("Tungi", "Dark", "Тёмная") else tr("Tonggi", "Light", "Светлая"),
                onClick = { showThemeDialog = true }
            )
            ProfileRowDivider()

            CleanProfileRow(
                icon = Icons.Outlined.CalendarMonth,
                title = tr("Sevimlilar ro'yxati", "Appointments & Favorites", "Избранное"),
                trailingText = "${sessionManager.getFavorites().size} ${tr("ta", "items", "шт.")}",
                onClick = onNavigateToFavorites
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ====================================================================
        // SECTION 3: SUPPORT (1:1 AS IN PHOTO, REACTIVE TO LANGUAGE)
        // ====================================================================
        Text(
            text = tr("Qo'llab-quvvatlash", "Support", "Поддержка"),
            color = TextMuted,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appear(3)
                .clip(RoundedCornerShape(22.dp))
                .background(BgCard)
                .border(0.8.dp, CardBorder, RoundedCornerShape(22.dp))
        ) {
            CleanProfileRow(
                icon = Icons.Outlined.HelpOutline,
                title = tr("Yordam markazi", "Help Center", "Центр помощи"),
                onClick = { showHelpDialog = true }
            )
            ProfileRowDivider()

            CleanProfileRow(
                icon = Icons.Outlined.SystemUpdate,
                title = tr("Yangilanishni tekshirish", "Check for Updates", "Проверить обновления"),
                trailingText = if (isCheckingUpdate) "..." else "v${BuildConfig.VERSION_NAME}",
                onClick = {
                    if (!isCheckingUpdate) {
                        isCheckingUpdate = true
                        scope.launch {
                            try {
                                val res = DarkmoraApi.checkUpdate()
                                if (res.ok && res.data != null) {
                                    if (res.data.versionCode > BuildConfig.VERSION_CODE) {
                                        updateInfo = res.data
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            tr(
                                                "Sizda eng so'nggi versiya o'rnatilgan (v${BuildConfig.VERSION_NAME})",
                                                "You have the latest version installed (v${BuildConfig.VERSION_NAME})",
                                                "У вас установлена последняя версия (v${BuildConfig.VERSION_NAME})"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Versiya tekshirishda xatolik", Toast.LENGTH_SHORT).show()
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

            if (isLoggedIn) {
                ProfileRowDivider()
                CleanProfileRow(
                    icon = Icons.Outlined.Logout,
                    title = tr("Akkauntdan chiqish", "Log Out", "Выйти"),
                    titleColor = RedBadge,
                    onClick = {
                        sessionManager.clearSession()
                        refreshUserData()
                        Toast.makeText(
                            context,
                            tr("Akkauntdan chiqildi", "Logged out", "Вы вышли из аккаунта"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    // ========================================================================
    // DIALOGS & SHEETS
    // ========================================================================

    // 1. Manage Profile Dialog (Gallery Photo Picker + Display Name Edit)
    if (showManageProfileDialog) {
        var newName by remember { mutableStateOf(user?.displayName ?: user?.username ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showManageProfileDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Manage Profile",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Gallery Avatar Picker Box
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.5.dp, PrimaryBlue, CircleShape)
                            .clickable { pickAvatarFromGallery() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!user?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = user!!.photoUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    TextButton(onClick = { pickAvatarFromGallery() }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galereyadan rasm tanlash", color = PrimaryBlue, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Ism / Taxallus") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
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
                        TextButton(onClick = { showManageProfileDialog = false }) {
                            Text("Bekor qilish", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            val token = sessionManager.getToken() ?: ""
                                            if (token.isNotBlank()) {
                                                DarkmoraApi.updateProfile(token, displayName = newName.trim())
                                            }
                                            val cur = user ?: UserProfile(username = newName.trim())
                                            sessionManager.updateProfile(cur.copy(displayName = newName.trim()))
                                            refreshUserData()
                                            showManageProfileDialog = false
                                            Toast.makeText(context, "Profil saqlandi!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(if (isSaving) "Saqlanmoqda..." else "Saqlash", color = BgSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 2. Password & Security Dialog
    if (showPasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Password & Security", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Yangi parol (kamida 6 ta belgi)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Yangi parolni tasdiqlang") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text("Bekor qilish", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPass.length < 6) {
                                    Toast.makeText(context, "Parol kamida 6 belgidan iborat bo'lsin", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (newPass != confirmPass) {
                                    Toast.makeText(context, "Parollar mos kelmadi", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSaving = true
                                scope.launch {
                                    try {
                                        val token = sessionManager.getToken() ?: ""
                                        val res = DarkmoraApi.updateProfile(token, newPass = newPass)
                                        if (res.ok) {
                                            showPasswordDialog = false
                                            Toast.makeText(context, "Parol muvaffaqiyatli yangilandi!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, res.error ?: "Xatolik yuz berdi", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(if (isSaving) "..." else "Yangilash", color = BgSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 3. Notifications Center Dialog
    if (showNotificationsDialog) {
        NotificationsDialog(
            notifications = notifList,
            readIds = readNotifIds,
            onMarkAllRead = {
                val allIds = notifList.map { it.id }
                sessionManager.markNotificationsRead(allIds)
                readNotifIds = sessionManager.getReadNotificationIds()
            },
            onAnimeClick = { anime ->
                sessionManager.markNotificationsRead(listOf(anime.id))
                readNotifIds = sessionManager.getReadNotificationIds()
                showNotificationsDialog = false
                onAnimeClick(anime.getEffectiveSlug())
            },
            onDismiss = { showNotificationsDialog = false }
        )
    }

    // 4. Theme Switcher Dialog (Dark / Light)
    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ilova Mavzusi (Theme)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    listOf(
                        Pair(true, "Tungi rejim (Dark Mode)"),
                        Pair(false, "Tonggi rejim (Light Mode)")
                    ).forEach { (isDarkOpt, label) ->
                        val selected = AppThemeState.isDark == isDarkOpt
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) PrimaryBlue.copy(alpha = 0.12f) else BgCard)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) PrimaryBlue else CardBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    AppThemeState.isDark = isDarkOpt
                                    sessionManager.setDarkMode(isDarkOpt)
                                    showThemeDialog = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDarkOpt) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                    contentDescription = null,
                                    tint = if (selected) PrimaryBlue else TextPrimary
                                )
                                Text(
                                    text = label,
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (selected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryBlue)
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Language Selector Dialog (Instantly updates AppThemeState.language across the entire app!)
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr("Tilni tanlang", "Select Language", "Выберите язык"),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    listOf("O'zbekcha", "English", "Русский").forEach { lang ->
                        val selected = AppThemeState.language == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) PrimaryBlue.copy(alpha = 0.12f) else BgCard)
                                .border(1.dp, if (selected) PrimaryBlue else CardBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    currentLang = lang
                                    AppThemeState.language = lang
                                    sessionManager.setLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lang, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue)
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. About Us Dialog
    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = tr("DarkMora haqida", "About DarkMora", "О DarkMora"),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            "DarkMora (v2.5.3) — O'zbek tilidagi eng sara animelar, kinolar va seriallarni yuqori Full HD sifatda reklamalarsiz tomosha qilish uchun yaratilgan rasmiy mobil ilova.",
                            "DarkMora (v2.5.3) — Official mobile streaming platform for watching top anime, movies, and series in Full HD quality without ads.",
                            "DarkMora (v2.5.3) — Официальное мобильное приложение для просмотра лучших аниме, фильмов и сериалов в Full HD качестве без рекламы."
                        ),
                        color = TextMuted,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://darkmora.uz")))
                            showAboutDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(tr("Rasmiy sayt: darkmora.uz", "Official Website: darkmora.uz", "Официальный сайт: darkmora.uz"), color = BgSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 7. Interactive Help Center & Support Request Submission Dialog
    if (showHelpDialog) {
        var reqTitle by remember { mutableStateOf("") }
        var reqDetails by remember { mutableStateOf("") }
        var isSubmittingReq by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = tr("Yordam markazi & Murojaat", "Help Center & Support", "Центр помощи и обращение"),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            "Yangi anime buyurtma qilish yoki muammo haqida xabar yuborish:",
                            "Request a new anime or report a technical issue directly to admins:",
                            "Заказать новое аниме или сообщить о проблеме:"
                        ),
                        color = TextMuted,
                        fontSize = 12.5.sp
                    )

                    OutlinedTextField(
                        value = reqTitle,
                        onValueChange = { reqTitle = it },
                        label = { Text(tr("Anime nomi yoki mavzu", "Anime title or subject", "Название аниме или тема")) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reqDetails,
                        onValueChange = { reqDetails = it },
                        label = { Text(tr("Xabar matni / Izoh", "Message details", "Текст сообщения")) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (reqTitle.isBlank()) {
                                Toast.makeText(context, tr("Sarlavhani kiriting", "Enter subject", "Введите тему"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmittingReq = true
                            scope.launch {
                                try {
                                    val tok = sessionManager.getToken() ?: ""
                                    val res = DarkmoraApi.submitSupportRequest(tok, reqTitle.trim(), reqDetails.trim())
                                    if (res.ok) {
                                        Toast.makeText(
                                            context,
                                            tr("So'rovingiz adminlarga yuborildi!", "Your request has been sent!", "Ваш запрос отправлен!"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showHelpDialog = false
                                    } else {
                                        Toast.makeText(context, res.error ?: "Xatolik", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSubmittingReq = false
                                }
                            }
                        },
                        enabled = !isSubmittingReq,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isSubmittingReq) "..." else tr("Adminlarga yuborish", "Send Request", "Отправить"),
                            color = BgSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                context.cacheDir.deleteRecursively()
                                Toast.makeText(
                                    context,
                                    tr("Kesh tozalandi!", "Cache cleared!", "Кэш очищен!"),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showHelpDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(tr("Keshni tozalash", "Clear Cache", "Очистить кэш"), color = TextPrimary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/darkmoraloginbot")))
                                showHelpDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Telegram Bot", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

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

    if (showUpdateDialog && updateInfo != null) {
        UpdateAppDialog(
            info = updateInfo!!,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

// ============================================================================
// 1:1 CLEAN PROFILE ROW COMPONENT (MATCHING photo_1795265499.jpg)
// ============================================================================

@Composable
private fun CleanProfileRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (titleColor != TextPrimary) titleColor else TextPrimary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    color = TextMuted,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ProfileRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(0.7.dp)
            .background(DividerTone)
    )
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
                .background(BgSurface)
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
                            .background(PrimaryBlue.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Yangi Versiya Mavjud!", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Versiya: ${info.versionName} (Build ${info.versionCode})", color = PrimaryBlue, fontSize = 12.sp)
                    }
                }

                info.changelog?.let { log ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        LinearProgressIndicator(color = PrimaryBlue, modifier = Modifier.fillMaxWidth())
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
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    }
}
