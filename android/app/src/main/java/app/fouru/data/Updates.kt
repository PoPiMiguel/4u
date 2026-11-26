package app.fouru.data

import app.fouru.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Comprueba en GitHub Releases si hay un APK más nuevo (DECISIONS §14). */
object Updates {
    private const val LATEST = "https://api.github.com/repos/PoPiMiguel/4u/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val page: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
    )

    data class Available(val version: String, val url: String)

    suspend fun check(): Available? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val release = json.decodeFromString<Release>(conn.inputStream.bufferedReader().readText())
            val version = release.tag.removePrefix("v")
            if (!newer(version, BuildConfig.VERSION_NAME)) return@withContext null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            Available(version, apk?.url ?: release.page)
        } finally {
            conn.disconnect()
        }
    }

    /** ¿Es `a` más nueva que `b`? (x.y.z) */
    fun newer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
