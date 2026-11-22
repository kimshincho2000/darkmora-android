package uz.darkmora.app

import android.app.Application
import uz.darkmora.app.crash.DarkmoraCrashReporter

class DarkmoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DarkmoraCrashReporter.install(this)
    }
}
