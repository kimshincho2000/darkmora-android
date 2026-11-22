package uz.darkmora.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.admin.network.AdminApi
import uz.darkmora.admin.ui.theme.*
import kotlin.math.*

private data class AdminParticle(
    val nx: Float,
    val ny: Float,
    val rand: Float,
    val rand2: Float,
    val angleOffset: Float,
    val driftX: Float,
    val driftY: Float
)

@Composable
fun AdminLoginScreen(
    onAuthSuccess: (token: String, email: String, label: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) } // 1 = Login, 2 = Email 2FA
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    var tempToken by remember { mutableStateOf("") }
    var emailMasked by remember { mutableStateOf("") }
    var twoFaCode by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top centered white shield badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "DarkMora Admin",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 1:1 Generative White Particle Vortex Canvas
            AdminParticleField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
            )

            Text(
                text = "DarkMora Admin",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (step == 1) "Boshqaruv paneliga xavfsiz kirish" else "Email 2FA tasdiqlash kodi",
                color = TextSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "admin_login_step"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(BgCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (currentStep == 1) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = { Text("Admin login", color = TextMuted, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = TextSecondary, modifier = Modifier.size(19.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = adminTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("Admin parol", color = TextMuted, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(19.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(
                                        imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(16.dp),
                            colors = adminTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .clickable(enabled = !isLoading) {
                                    if (username.isBlank() || password.isBlank()) {
                                        Toast.makeText(context, "Login va parolni kiriting", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val res = AdminApi.login(username.trim(), password)
                                            if (res.ok && res.data?.requires2fa == true && !res.data.tempToken.isNullOrBlank()) {
                                                tempToken = res.data.tempToken
                                                emailMasked = res.data.emailMasked ?: "Admin email"
                                                resendCooldown = 45
                                                step = 2
                                                Toast.makeText(context, res.data.message ?: "2FA kod yuborildi", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, res.error ?: "Admin huquqi yoki parol noto'g'ri", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Aloqa xatosi: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Text("Davom etish", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text(
                            text = "6 xonali xavfsizlik kodi $emailMasked manziliga yuborildi.",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = twoFaCode,
                            onValueChange = { twoFaCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            placeholder = { Text("123456", color = TextMuted, fontSize = 16.sp) },
                            leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary, modifier = Modifier.size(19.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = adminTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .clickable(enabled = !isLoading && twoFaCode.length == 6) {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val res = AdminApi.verify2fa(tempToken, twoFaCode)
                                            val tok = res.data?.adminToken
                                            if (res.ok && !tok.isNullOrBlank()) {
                                                onAuthSuccess(
                                                    tok,
                                                    res.data.admin?.email ?: "",
                                                    res.data.admin?.label ?: "Super Admin"
                                                )
                                            } else {
                                                Toast.makeText(context, res.error ?: "Kod noto'g'ri", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Aloqa xatosi: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Text("Tasdiqlash va Kirish", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (resendCooldown > 0) "Qayta yuborish (${resendCooldown}s)" else "Kodni qayta yuborish",
                                color = if (resendCooldown > 0) TextMuted else TextPrimary,
                                fontSize = 12.5.sp,
                                modifier = Modifier.clickable(enabled = resendCooldown == 0 && !isLoading) {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val r = AdminApi.resend2fa(tempToken)
                                            if (r.ok) {
                                                resendCooldown = 45
                                                Toast.makeText(context, "Kod qayta yuborildi", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, r.error ?: "Xatolik", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Aloqa xatosi", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            )
                            Text(
                                text = "← Ortga",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                modifier = Modifier.clickable {
                                    step = 1
                                    twoFaCode = ""
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun adminTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0x88FFFFFF),
    unfocusedBorderColor = BorderSubtle,
    focusedContainerColor = BgCardElevated,
    unfocusedContainerColor = Color(0xFF141416),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Color.White
)

@Composable
private fun AdminParticleField(modifier: Modifier = Modifier) {
    val grid = 17
    val particles = remember {
        buildList(grid * grid) {
            for (gy in 0 until grid) {
                for (gx in 0 until grid) {
                    val nx = (gx.toFloat() / (grid - 1)) * 2f - 1f
                    val ny = (gy.toFloat() / (grid - 1)) * 2f - 1f
                    val s1 = sin(gx * 127.1 + gy * 311.7) * 43758.5453
                    val r1 = (s1 - floor(s1)).toFloat()
                    val s2 = sin(gx * 269.5 + gy * 183.3) * 43758.5453
                    val r2 = (s2 - floor(s2)).toFloat()
                    add(AdminParticle(nx, ny, r1, r2, (r1 - 0.5f) * (2.0 * PI).toFloat(), (r1 - 0.5f) * 1.4f, (r2 - 0.5f) * 1.4f))
                }
            }
        }
    }

    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val boxHalf = min(size.width, size.height) * 0.36f
        val t = elapsed
        val cycle = (t % 8f) / 8f
        val vortex = max(0f, sin(cycle * (2.0 * PI).toFloat() - 0.4f))
        val wavePos = sin(t * 0.95f) * 1.25f

        for (p in particles) {
            val dWave = p.nx * 0.85f + p.ny * 0.25f - wavePos
            val dissolve = ((1f - abs(dWave) * 1.15f) * (1f - vortex * 0.75f)).coerceIn(0f, 1f)
            val scatter = dissolve * dissolve
            val r = hypot(p.nx, p.ny) + 0.001f
            val baseA = atan2(p.ny, p.nx)
            val twist = vortex * (1.15f / (r + 0.45f)) * sin(t * 0.75f)
            val sx = p.nx * cos(twist * 0.35f) - p.ny * sin(twist * 0.35f)
            val sy = p.nx * sin(twist * 0.35f) + p.ny * cos(twist * 0.35f)
            val exp = 1f + vortex * 0.16f
            val px = cx + sx * boxHalf * exp + p.driftX * scatter * boxHalf * 0.8f - scatter * boxHalf * 0.2f
            val py = cy + sy * boxHalf * exp + p.driftY * scatter * boxHalf * 0.7f + sin(t * 2.1f + p.rand * 6.283f) * scatter * 14f
            val vAngle = baseA + (PI * 0.5).toFloat() + sin(r * 3.5f - t * 1.6f) * 0.45f
            val tAngle = p.angleOffset + t * (1.8f + p.rand * 2.2f)
            val angle = vAngle * (1f - scatter * 0.85f) + tAngle * (scatter * 0.85f)
            val dash = 13f * (0.55f + 0.45f * vortex + 0.35f * scatter)
            val alpha = ((0.9f - scatter * 0.35f) * (1f - max(0f, r - 0.92f) * 1.2f).coerceAtLeast(0.15f)).coerceIn(0.12f, 0.95f)
            val dx = cos(angle) * dash * 0.5f
            val dy = sin(angle) * dash * 0.5f
            drawLine(Color.White.copy(alpha = alpha), Offset(px - dx, py - dy), Offset(px + dx, py + dy), 2.4f, StrokeCap.Round)
        }
    }
}
