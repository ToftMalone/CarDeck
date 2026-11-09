package com.cardeck.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Version publiée sur GitHub (release). */
data class Release(val version: String, val name: String, val notes: String, val date: Long, val apkUrl: String?, val apkSize: Long)

/** Mises à jour : releases du dépôt GitHub, téléchargement de l'APK et lancement de l'installation. */
object Updater {
    const val REPO = "ToftMalone/CarDeck"
    const val PAGE = "https://github.com/$REPO"
    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=30"

    /** Releases publiées (hors brouillons et préversions), de la plus récente à la plus ancienne. */
    fun releases(): List<Release> {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "CarDeck")
        try {
            if (c.responseCode != 200) throw java.io.IOException("GitHub a répondu ${c.responseCode}")
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                .map { o ->
                    val assets = o.optJSONArray("assets") ?: JSONArray()
                    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) }
                    Release(
                        version = o.optString("tag_name").trimStart('v', 'V'),
                        name = o.optString("name").ifBlank { o.optString("tag_name") },
                        notes = o.optString("body").trim(),
                        date = runCatching { iso.parse(o.optString("published_at"))!!.time }.getOrDefault(0L),
                        apkUrl = apk?.optString("browser_download_url"),
                        apkSize = apk?.optLong("size") ?: 0L,
                    )
                }
                .sortedWith { a, b -> compare(b.version, a.version) }
        } finally {
            c.disconnect()
        }
    }

    /** Compare deux numéros de version (« 1.10 » > « 1.9 »). */
    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** Télécharge l'APK de [r] dans le cache ; [progress] reçoit l'avancement de 0 à 1. */
    fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File {
        val url = r.apkUrl ?: throw java.io.IOException("Cette version ne contient pas d'APK")
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "CarDeck-${r.version}.apk")
        var c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "CarDeck")
        // GitHub redirige vers un autre domaine : redirection suivie à la main.
        var hops = 0
        while (c.responseCode in 300..399 && hops++ < 5) {
            val next = c.getHeaderField("Location")
            c.disconnect()
            c = URL(next).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "CarDeck")
        }
        try {
            if (c.responseCode != 200) throw java.io.IOException("Téléchargement impossible (${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.apkSize
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** Ouvre l'installeur d'Android sur l'APK téléchargé. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
