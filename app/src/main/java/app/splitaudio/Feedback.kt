package app.splitaudio

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/**
 * 0.5.6: one-tap report that needs no account. "It works!" / "Report a problem" / the crash card POST the same
 * facts you can copy with "Copy diagnostics" (no accounts, contacts or Bluetooth names) to the project's own
 * endpoint; the server is published in the repo's server/ directory. Where to send is read from
 * server/endpoint.json on GitHub at most once a day (so the endpoint can move without an app update), with the
 * address built into this release as the fallback. When sending fails the caller falls back to the GitHub form.
 */
object Feedback {
    const val ENDPOINT_JSON = "https://raw.githubusercontent.com/monarchsofchaos004-gif/splitaudio/main/server/endpoint.json"
    const val DEFAULT_URL = "https://151-145-67-42.sslip.io/sa/report"
    const val REFRESH_MS = 24L * 3600 * 1000
    const val MAX_DIAG = 8000
    const val MAX_NOTE = 1000
    const val MAX_NICK = 40
    const val MAX_ENDPOINT_BYTES = 4096

    fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\t' -> append("\\t"); '\r' -> {}
            else -> if (ch < ' ') append(String.format("\\u%04x", ch.code)) else append(ch)
        }
    }

    /** Flat JSON object of strings (the server accepts nothing else). */
    fun json(fields: List<Pair<String, String>>): String =
        fields.joinToString(",", "{", "}") { "\"" + esc(it.first) + "\":\"" + esc(it.second) + "\"" }

    /** The report body: the diagnostics plus the verdict and an optional note / nickname. */
    fun payload(i: Report.DiagInfo, verdict: String, note: String = "", nick: String = ""): String = json(listOf(
        "app" to "splitaudio",
        "verdict" to verdict,
        "version" to (i.appVersion + " (" + i.flavor + ")").take(40),
        "flavor" to i.flavor.take(16),
        "device" to Report.deviceName(i.brand, i.model).take(80),
        "rom" to Report.romText(i.display, i.androidRelease, i.sdk).take(120),
        "mode" to i.mode.take(16),
        "diag" to Report.diagnostics(i).take(MAX_DIAG),
        "note" to note.trim().take(MAX_NOTE),
        "nick" to nick.trim().take(MAX_NICK)))

    /** The "report" address in endpoint.json; https only, otherwise null (then the cached / built-in address is used). */
    fun endpointFrom(json: String): String? =
        Regex("\"report\"\\s*:\\s*\"(https://[^\"\\s]+)\"").find(json)?.groupValues?.get(1)

    private fun sp(c: Context) = c.getSharedPreferences("feedback", Context.MODE_PRIVATE)

    /** Last report sent from this install: verdict, app version, time; null = none yet. */
    fun lastReport(c: Context): Triple<String, String, Long>? {
        val p = sp(c); val k = p.getString("last_kind", null) ?: return null
        return Triple(k, p.getString("last_ver", "") ?: "", p.getLong("last_at", 0L))
    }

    /** Blocking (network): the endpoint, refreshed from endpoint.json at most once a day. */
    fun endpoint(c: Context, now: Long = System.currentTimeMillis()): String {
        val p = sp(c); val cached = p.getString("url", null)
        if (cached != null && now - p.getLong("url_at", 0L) < REFRESH_MS) return cached
        val fresh = try { endpointFrom(get(ENDPOINT_JSON, ua(c))) } catch (_: Exception) { null }
        if (fresh != null) p.edit().putString("url", fresh).putLong("url_at", now).apply()
        return fresh ?: cached ?: DEFAULT_URL
    }

    /** Sends in the background; done(ok) runs on the UI thread. */
    fun send(c: Context, verdict: String, note: String = "", nick: String = "", done: (Boolean) -> Unit) {
        val app = c.applicationContext
        Thread {
            val ok = try { post(endpoint(app), payload(Diag.info(app), verdict, note, nick), ua(app)) in 200..299 } catch (_: Exception) { false }
            if (ok) sp(app).edit().putString("last_kind", verdict).putString("last_ver", Diag.versionName(app)).putLong("last_at", System.currentTimeMillis()).apply()
            AppLog.add("report " + verdict + (if (ok) " sent" else " failed"))
            Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    private fun ua(c: Context) = "SplitAudio/" + Diag.versionName(c) + " (+https://github.com/monarchsofchaos004-gif/splitaudio)"

    private fun get(url: String, ua: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 5000; con.readTimeout = 5000
            con.setRequestProperty("User-Agent", ua)
            if (con.responseCode != 200) throw java.io.IOException("http " + con.responseCode)
            val out = java.io.ByteArrayOutputStream()
            con.inputStream.use { s -> val b = ByteArray(1024); while (true) { val n = s.read(b); if (n < 0) break; out.write(b, 0, n); if (out.size() > MAX_ENDPOINT_BYTES) throw java.io.IOException("too big") } }
            return out.toString("UTF-8")
        } finally { con.disconnect() }
    }

    /** POST the JSON body; returns the HTTP status. */
    fun post(url: String, body: String, ua: String): Int {
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 8000; con.readTimeout = 8000
            con.requestMethod = "POST"; con.doOutput = true
            con.setRequestProperty("User-Agent", ua)
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = body.toByteArray(Charsets.UTF_8)
            con.setFixedLengthStreamingMode(bytes.size)
            con.outputStream.use { it.write(bytes) }
            return con.responseCode
        } finally { con.disconnect() }
    }
}
