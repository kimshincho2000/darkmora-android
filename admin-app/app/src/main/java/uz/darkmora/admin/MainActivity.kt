package uz.darkmora.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import uz.darkmora.admin.data.AdminSessionManager
import uz.darkmora.admin.network.AdminApi
import uz.darkmora.admin.ui.screens.AdminLoginScreen
import uz.darkmora.admin.ui.screens.AdminMainScreen
import uz.darkmora.admin.ui.theme.BgBlack
import uz.darkmora.admin.ui.theme.DarkMoraAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val session = AdminSessionManager(this)

        setContent {
            DarkMoraAdminTheme {
                var token by remember { mutableStateOf(session.getToken()) }
                var label by remember { mutableStateOf(session.getLabel()) }
                var checking by remember { mutableStateOf(token != null) }

                LaunchedEffect(Unit) {
                    val saved = session.getToken()
                    if (saved != null) {
                        try {
                            val res = AdminApi.checkAuth(saved)
                            if (res.ok && res.data?.authenticated == true) {
                                label = res.data.admin?.label ?: session.getLabel()
                            } else {
                                session.clear()
                                token = null
                            }
                        } catch (_: Exception) {
                            // Keep offline session token if network hiccups
                        } finally {
                            checking = false
                        }
                    }
                }

                when {
                    checking -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(BgBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    token == null -> {
                        AdminLoginScreen(
                            onAuthSuccess = { newToken, email, newLabel ->
                                session.saveToken(newToken, email, newLabel)
                                label = newLabel
                                token = newToken
                            }
                        )
                    }
                    else -> {
                        AdminMainScreen(
                            adminToken = token!!,
                            adminLabel = label,
                            onLogout = {
                                session.clear()
                                token = null
                            }
                        )
                    }
                }
            }
        }
    }
}
