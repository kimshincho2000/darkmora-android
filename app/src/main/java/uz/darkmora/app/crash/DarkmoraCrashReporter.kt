package uz.darkmora.app.crash

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uz.darkmora.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

object DarkmoraCrashReporter {
    private const val CRASH_FILE_NAME = "pending_crash_report.json"
    private const val ENDPOINT = "https://darkmora.uz/api?action=report_crash"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                savePendingCrash(appContext, thread.name, throwable)
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Upload any crash saved from a previous run
        flushPendingCrash(appContext)
    }

    fun reportNonFatal(tag: String, throwable: Throwable) {
        scope.launch {
            try {
                val payload = buildPayload(tag, throwable)
                uploadJson(gson.toJson(payload))
            } catch (_: Exception) {}
        }
    }

    private fun savePendingCrash(context: Context, threadName: String, throwable: Throwable) {
        val payload = buildPayload(threadName, throwable)
        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(gson.toJson(payload), Charsets.UTF_8)
    }

    private fun flushPendingCrash(context: Context) {
        scope.launch {
            try {
                val file = File(context.filesDir, CRASH_FILE_NAME)
                if (!file.exists()) return@launch
                val json = file.readText(Charsets.UTF_8)
                if (json.isBlank()) {
                    file.delete()
                    return@launch
                }
                if (uploadJson(json)) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun buildPayload(threadName: String, throwable: Throwable): Map<String, Any> {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString().take(5500)
        val title = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Unknown"}".take(220)
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        return mapOf(
            "app_version" to BuildConfig.VERSION_NAME,
            "version_code" to BuildConfig.VERSION_CODE,
            "device_model" to device,
            "android_version" to androidVer,
            "thread_name" to threadName,
            "error_title" to title,
            "stack_trace" to stack
        )
    }

    private fun uploadJson(json: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(ENDPOINT)
                .post(json.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}
