package app.splitaudio

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** "New version" hint: at most one GitHub releases query per day, silent on failure, 5 s timeouts, background thread.
 *  fdroid build: only links to the release page (never downloads or installs an APK; F-Droid does not allow self-updates).
 *  play build: switched off entirely ([Dist.updateCheck] = false; Google Play updates the app).
 *  The comparison and filtering are pure functions (UpdateTest). */
object Update {
    const val API = "https://api.github.com/repos/monarchsofchaos004-gif/splitaudio/releases?per_page=20"
    private const val RELEASE_PREFIX = "https://github.com/monarchsofchaos004-gif/splitaudio/"
    const val DAY_MS = 24 * 3600_000L
    private const val MAX_BYTES = 1_000_000

    /** x.y.z, optionally -betaN. [beta] = 0 means a stable release. */
    data class Ver(val major: Int, val minor: Int, val patch: Int, val beta: Int) : Comparable<Ver> {
        val isBeta get() = beta > 0
        private fun rank() = if (beta > 0) beta else Int.MAX_VALUE   // x.y.z-beta1 < x.y.z-beta2 < x.y.z
        override fun compareTo(other: Ver): Int =
            compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.rank() })
        override fun toString() = "$major.$minor.$patch" + if (beta > 0) "-beta$beta" else ""
    }

    class Release(val tag: String, val htmlUrl: String, val draft: Boolean, val prerelease: Boolean)

    private val VER = Regex("^v?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:-beta(\\d{1,3}))?$")

    /** Pure: "v0.5.0", "0.5.0-beta1" -> [Ver]; anything else (including "-rc1", "-beta0") -> null */
    fun parseVersion(s: String?): Ver? {
        val m = VER.matchEntire(s?.trim() ?: return null) ?: return null
        val beta = m.groupValues[4]
        if (beta == "0" || (beta.isNotEmpty() && beta.startsWith("0") && beta.length > 1)) return null
        return Ver(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), if (beta.isEmpty()) 0 else beta.toInt())
    }

    /** Pure: the newest release the installed version should be told about, or null.
     *  Beta installs (versionName contains "beta") also see betas; stable installs only stable releases.
     *  Drafts, unparseable tags, links that are not this repo's, the skipped tag and anything not newer than [current] are ignored. */
    fun pickNewer(current: String, releases: List<Release>, skippedTag: String?): Release? {
        val cur = parseVersion(current) ?: return null
        val wantBeta = current.contains("beta", ignoreCase = true)
        return releases.asSequence()
            .filter { !it.draft && it.htmlUrl.startsWith(RELEASE_PREFIX) && it.tag != skippedTag }
            .mapNotNull { r -> parseVersion(r.tag)?.let { r to it } }
            .filter { (_, v) -> (wantBeta || !v.isBeta) && v > cur }
            .maxByOrNull { it.second }?.first
    }

    /** Pure: is a check due? (never checked, a day passed, or the clock went backwards) */
    fun due(lastCheck: Long, now: Long): Boolean = lastCheck <= 0L || now < lastCheck || now - lastCheck >= DAY_MS

    /** Pure: the releases API answer (a JSON array) -> releases. Anything unexpected -> empty list. */
    fun parseReleases(json: String): List<Release> {
        val root = try { MiniJson.parse(json) } catch (_: Exception) { return emptyList() }
        if (root !is List<*>) return emptyList()
        return root.mapNotNull { o ->
            val m = o as? Map<*, *> ?: return@mapNotNull null
            val tag = m["tag_name"] as? String ?: return@mapNotNull null
            val url = m["html_url"] as? String ?: return@mapNotNull null
            Release(tag, url, m["draft"] == true, m["prerelease"] == true)
        }
    }

    // ---- prefs + network ----

    private fun sp(c: Context) = c.getSharedPreferences("update", Context.MODE_PRIVATE)

    fun enabled(c: Context) = Dist.updateCheck && sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, on: Boolean) { sp(c).edit().putBoolean("enabled", on).apply() }

    /** The release to show in the card (re-checked against the installed version), or null */
    fun pending(c: Context): Release? {
        if (!enabled(c)) return null
        val p = sp(c)
        val tag = p.getString("found_tag", null) ?: return null
        val url = p.getString("found_url", null) ?: return null
        return pickNewer(Diag.versionName(c), listOf(Release(tag, url, false, false)), p.getString("skip_tag", null))
    }

    fun skip(c: Context, tag: String) { sp(c).edit().putString("skip_tag", tag).remove("found_tag").remove("found_url").apply() }

    /** Called when the app opens: at most one query per day, background thread, silent on any failure. [done] runs on that thread afterwards. */
    fun checkAsync(c: Context, done: () -> Unit) {
        if (!enabled(c)) return
        val app = c.applicationContext
        val p = sp(app)
        val now = System.currentTimeMillis()
        if (!due(p.getLong("last_check", 0L), now)) return
        p.edit().putLong("last_check", now).apply()   // counts as today's check even when it fails
        Thread {
            try {
                val rel = pickNewer(Diag.versionName(app), parseReleases(fetch(app)), p.getString("skip_tag", null))
                val e = p.edit()
                if (rel == null) e.remove("found_tag").remove("found_url") else e.putString("found_tag", rel.tag).putString("found_url", rel.htmlUrl)
                e.apply()
                done()
            } catch (_: Throwable) { /* silent */ }
        }.start()
    }

    private fun fetch(c: Context): String {
        val con = URL(API).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 5000; con.readTimeout = 5000
            con.setRequestProperty("User-Agent", "SplitAudio/" + Diag.versionName(c) + " (+https://github.com/monarchsofchaos004-gif/splitaudio)")
            con.setRequestProperty("Accept", "application/vnd.github+json")
            if (con.responseCode != 200) throw java.io.IOException("http " + con.responseCode)
            val out = java.io.ByteArrayOutputStream()
            con.inputStream.use { s -> val b = ByteArray(8192); while (true) { val n = s.read(b); if (n < 0) break; out.write(b, 0, n); if (out.size() > MAX_BYTES) throw java.io.IOException("too big") } }
            return out.toString("UTF-8")
        } finally { con.disconnect() }
    }
}

/** Tiny JSON reader (objects -> Map, arrays -> List, strings, numbers -> Double, booleans, null): android.jar's org.json is only a stub in unit tests. */
object MiniJson {
    fun parse(s: String): Any? { val p = P(s); val v = p.value(); p.ws(); if (p.i != s.length) throw IllegalArgumentException("trailing"); return v }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws(); if (i >= s.length) throw IllegalArgumentException("eof")
            return when (val c = s[i]) {
                '{' -> obj(); '[' -> arr(); '"' -> str()
                't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("bad char")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) throw IllegalArgumentException("bad literal"); i += w.length; return v }
        fun num(): Double { val st = i; while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++; return s.substring(st, i).toDouble() }
        fun str(): String {
            i++; val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("eof in string")
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c != '\\') { sb.append(c); continue }
                if (i >= s.length) throw IllegalArgumentException("eof in escape")
                when (val e = s[i++]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                    'u' -> { if (i + 4 > s.length) throw IllegalArgumentException("bad \\u"); sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> sb.append(e)
                }
            }
        }
        fun arr(): List<Any?> {
            i++; val l = ArrayList<Any?>(); ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) { l.add(value()); ws(); if (i >= s.length) throw IllegalArgumentException("eof"); if (s[i] == ',') { i++; continue }; if (s[i] == ']') { i++; return l }; throw IllegalArgumentException("bad array") }
        }
        fun obj(): Map<String, Any?> {
            i++; val m = LinkedHashMap<String, Any?>(); ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws(); if (i >= s.length || s[i] != '"') throw IllegalArgumentException("bad key")
                val k = str(); ws(); if (i >= s.length || s[i] != ':') throw IllegalArgumentException("no colon"); i++
                m[k] = value(); ws(); if (i >= s.length) throw IllegalArgumentException("eof")
                if (s[i] == ',') { i++; continue }; if (s[i] == '}') { i++; return m }; throw IllegalArgumentException("bad object")
            }
        }
    }
}
