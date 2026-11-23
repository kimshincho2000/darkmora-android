package uz.darkmora.app.data

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadedEpisode(
    val qism: Int,
    val title: String,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long
)

data class DownloadedAnime(
    val id: Int,
    val slug: String,
    val title: String,
    val posterUrl: String,
    val localPosterPath: String? = null,
    val year: String = "",
    val rating: String = "",
    val genres: String = "",
    val description: String = "",
    val episodes: List<DownloadedEpisode> = emptyList()
) {
    fun displayPosterSource(): Any {
        val local = localPosterPath?.let { File(it) }
        return if (local != null && local.exists() && local.length() > 0) local else posterUrl
    }

    fun totalSizeBytes(): Long = episodes.sumOf { it.sizeBytes }
}

data class ActiveDownloadState(
    val slug: String,
    val qism: Int,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L
)

object OfflineDownloadManager {
    private const val PREFS_NAME = "darkmora_offline_downloads"
    private const val KEY_LIST = "downloaded_animes_v1"

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var initialized = false

    // Compose-observable states so UI updates live everywhere
    val downloadedAnimes = mutableStateListOf<DownloadedAnime>()
    val activeDownloads = mutableStateMapOf<String, ActiveDownloadState>()

    fun keyOf(slug: String, qism: Int): String = "${slug}_$qism"

    private fun safeDirName(slug: String): String =
        slug.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "anime" }

    private fun baseDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(root, "darkmora_offline").apply { mkdirs() }
    }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        reload(context.applicationContext)
    }

    fun reload(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, null)
        val loaded: List<DownloadedAnime> = if (!raw.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<DownloadedAnime>>() {}.type
                gson.fromJson(raw, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Keep only episodes whose files still exist on disk
        val cleaned = loaded.mapNotNull { anime ->
            val validEps = anime.episodes
                .filter { ep ->
                    val f = File(ep.filePath)
                    f.exists() && f.length() > 0L
                }
                .sortedBy { it.qism }
            if (validEps.isEmpty()) null else anime.copy(episodes = validEps)
        }

        if (cleaned.size != loaded.size || cleaned.sumOf { it.episodes.size } != loaded.sumOf { it.episodes.size }) {
            persist(context, cleaned)
        }

        scope.launch(Dispatchers.Main) {
            downloadedAnimes.clear()
            downloadedAnimes.addAll(cleaned)
        }
    }

    private fun persist(context: Context, list: List<DownloadedAnime>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }

    fun getDownloadedAnime(slug: String): DownloadedAnime? =
        downloadedAnimes.firstOrNull { it.slug == slug }

    fun getDownloadedEpisode(slug: String, qism: Int): DownloadedEpisode? =
        downloadedAnimes.firstOrNull { it.slug == slug }?.episodes?.firstOrNull { it.qism == qism }

    fun isDownloaded(slug: String, qism: Int): Boolean {
        val ep = getDownloadedEpisode(slug, qism) ?: return false
        val f = File(ep.filePath)
        return f.exists() && f.length() > 0L
    }

    fun startDownload(
        context: Context,
        anime: Anime,
        episode: Episode,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val appCtx = context.applicationContext
        init(appCtx)

        val slug = anime.getEffectiveSlug()
        val k = keyOf(slug, episode.qism)
        if (activeJobs.containsKey(k)) return

        val url = (episode.videoUrl ?: episode.embedUrl ?: "").trim()
        if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            scope.launch(Dispatchers.Main) {
                onResult(false, "Ushbu qism uchun yuklab olish manzili topilmadi")
            }
            return
        }

        scope.launch(Dispatchers.Main) {
            activeDownloads[k] = ActiveDownloadState(slug = slug, qism = episode.qism, progress = 0f)
        }

        val job = scope.launch {
            val animeDir = File(baseDir(appCtx), safeDirName(slug)).apply { mkdirs() }
            val targetFile = File(animeDir, "ep_${episode.qism}.mp4")
            val partFile = File(animeDir, "ep_${episode.qism}.mp4.part")
            val posterFile = File(animeDir, "poster.jpg")

            try {
                // Download poster locally once so offline downloads screen works 100% without internet
                val posterUrl = anime.displayPoster()
                if (posterUrl.startsWith("http") && (!posterFile.exists() || posterFile.length() == 0L)) {
                    try {
                        val pReq = Request.Builder()
                            .url(posterUrl)
                            .addHeader("Referer", "https://darkmora.uz/")
                            .build()
                        httpClient.newCall(pReq).execute().use { pRes ->
                            if (pRes.isSuccessful) {
                                pRes.body?.byteStream()?.use { inp ->
                                    FileOutputStream(posterFile).use { out ->
                                        inp.copyTo(out)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://darkmora.uz/")
                    .addHeader("Origin", "https://darkmora.uz")
                    .addHeader("User-Agent", "DarkMora-Android/1.0")
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Server xatosi (HTTP ${response.code})")
                    }
                    val body = response.body ?: throw Exception("Bo'sh video oqimi")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    var lastUiUpdate = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read

                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdate >= 220L) {
                                    lastUiUpdate = now
                                    val prog = if (totalBytes > 0L) {
                                        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    withContext(Dispatchers.Main) {
                                        activeDownloads[k] = ActiveDownloadState(
                                            slug = slug,
                                            qism = episode.qism,
                                            progress = prog,
                                            downloadedBytes = downloadedBytes,
                                            totalBytes = totalBytes
                                        )
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }

                    val finalSize = targetFile.length()
                    val newEp = DownloadedEpisode(
                        qism = episode.qism,
                        title = "${episode.qism}-qism",
                        filePath = targetFile.absolutePath,
                        sizeBytes = finalSize,
                        downloadedAt = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        val idx = downloadedAnimes.indexOfFirst { it.slug == slug }
                        if (idx >= 0) {
                            val existing = downloadedAnimes[idx]
                            val mergedEps = (existing.episodes.filter { it.qism != episode.qism } + newEp)
                                .sortedBy { it.qism }
                            downloadedAnimes[idx] = existing.copy(
                                title = anime.displayTitle(),
                                posterUrl = posterUrl,
                                localPosterPath = if (posterFile.exists() && posterFile.length() > 0L) posterFile.absolutePath else existing.localPosterPath,
                                year = anime.displayYear(),
                                rating = anime.displayRating(),
                                genres = anime.displayGenres(),
                                description = anime.displayDescription(),
                                episodes = mergedEps
                            )
                        } else {
                            downloadedAnimes.add(
                                0,
                                DownloadedAnime(
                                    id = anime.id,
                                    slug = slug,
                                    title = anime.displayTitle(),
                                    posterUrl = posterUrl,
                                    localPosterPath = if (posterFile.exists() && posterFile.length() > 0L) posterFile.absolutePath else null,
                                    year = anime.displayYear(),
                                    rating = anime.displayRating(),
                                    genres = anime.displayGenres(),
                                    description = anime.displayDescription(),
                                    episodes = listOf(newEp)
                                )
                            )
                        }
                        val snapshot = downloadedAnimes.toList()
                        activeDownloads.remove(k)
                        scope.launch(Dispatchers.IO) { persist(appCtx, snapshot) }
                        onResult(true, "${episode.qism}-qism yuklab olindi")
                    }
                }
            } catch (ce: CancellationException) {
                if (partFile.exists()) partFile.delete()
                withContext(Dispatchers.Main) {
                    activeDownloads.remove(k)
                    onResult(false, "Yuklab olish bekor qilindi")
                }
            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                withContext(Dispatchers.Main) {
                    activeDownloads.remove(k)
                    onResult(false, e.message ?: "Yuklab olishda xatolik")
                }
            } finally {
                activeJobs.remove(k)
            }
        }

        activeJobs[k] = job
    }

    fun cancelDownload(slug: String, qism: Int) {
        val k = keyOf(slug, qism)
        activeJobs.remove(k)?.cancel()
        scope.launch(Dispatchers.Main) {
            activeDownloads.remove(k)
        }
    }

    fun deleteEpisode(context: Context, slug: String, qism: Int) {
        val appCtx = context.applicationContext
        val idx = downloadedAnimes.indexOfFirst { it.slug == slug }
        if (idx < 0) return
        val existing = downloadedAnimes[idx]
        val target = existing.episodes.firstOrNull { it.qism == qism }
        if (target != null) {
            scope.launch(Dispatchers.IO) {
                try { File(target.filePath).delete() } catch (_: Exception) {}
            }
        }
        val remaining = existing.episodes.filter { it.qism != qism }
        if (remaining.isEmpty()) {
            downloadedAnimes.removeAt(idx)
            scope.launch(Dispatchers.IO) {
                try { File(baseDir(appCtx), safeDirName(slug)).deleteRecursively() } catch (_: Exception) {}
            }
        } else {
            downloadedAnimes[idx] = existing.copy(episodes = remaining)
        }
        val snapshot = downloadedAnimes.toList()
        scope.launch(Dispatchers.IO) { persist(appCtx, snapshot) }
    }

    fun deleteAnime(context: Context, slug: String) {
        val appCtx = context.applicationContext
        // Cancel any active downloads for this anime
        activeJobs.keys.filter { it.startsWith("${slug}_") }.forEach { k ->
            activeJobs.remove(k)?.cancel()
            activeDownloads.remove(k)
        }
        val idx = downloadedAnimes.indexOfFirst { it.slug == slug }
        if (idx >= 0) {
            downloadedAnimes.removeAt(idx)
            val snapshot = downloadedAnimes.toList()
            scope.launch(Dispatchers.IO) {
                try { File(baseDir(appCtx), safeDirName(slug)).deleteRecursively() } catch (_: Exception) {}
                persist(appCtx, snapshot)
            }
        }
    }

    fun totalStorageBytes(): Long = downloadedAnimes.sumOf { it.totalSizeBytes() }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
