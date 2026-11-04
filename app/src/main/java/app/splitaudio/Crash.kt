package app.splitaudio

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** 0.5.4: the last uncaught exception is kept in the app's private files so the report button can attach it (#11 came in as
 *  "the app crashed" with nothing to go on). Pure parts (the saved text, the diagnostics block, the card decision) are unit-tested
 *  in CrashTest; install / saved / dismiss need a Context. Nothing is sent anywhere by itself: the text only goes into a report
 *  the user submits (or copies). */
object Crash {
    const val FILE = "last_crash.txt"
    const val MAX_LINES = 30     // the top of a stack trace is the useful part
    const val MAX_CHARS = 4000
    private const val PREFS = "report"
    private const val KEY_DISMISSED = "crash_dismissed"

    /** Pure: what gets saved. The first line "time=<ms>" is what [timeOf] reads back. */
    fun text(whenMs: Long, version: String, thread: String, trace: String): String {
        val body = trace.replace("\r", "").lines().filter { it.isNotBlank() }.take(MAX_LINES).joinToString("\n")
        return "time=$whenMs\nversion=$version\nthread=$thread\n$body".take(MAX_CHARS)
    }

    fun traceOf(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()

    /** Pure: the block for the diagnostics text; nothing saved = no block (reports without a crash look exactly as before). */
    fun section(saved: String?): String =
        if (saved.isNullOrBlank()) "" else "Last crash:\n" + saved.lines().joinToString("\n") { "  $it" } + "\n"

    /** Pure: the "time=" line of a saved text -> ms (0 when there is none) */
    fun timeOf(saved: String?): Long = saved?.lineSequence()?.firstOrNull { it.startsWith("time=") }?.substring(5)?.toLongOrNull() ?: 0L

    /** Pure: show the "it crashed last time" card? A crash the user already reported or closed ([dismissedAt] = its time) is not asked about again. */
    fun cardDue(savedAt: Long, dismissedAt: Long): Boolean = savedAt > 0L && savedAt != dismissedAt

    private fun file(c: Context) = File(c.filesDir, FILE)
    private fun sp(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Once per process ([App.onCreate]): save the trace, then hand over to Android's own handler (its dialog, the service restart) as before. */
    fun install(c: Context) {
        val app = c.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { file(app).writeText(text(System.currentTimeMillis(), Diag.versionName(app), t.name, traceOf(e))) } catch (_: Throwable) {}
            if (prev != null) prev.uncaughtException(t, e) else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun saved(c: Context): String? = try { file(c).takeIf { it.isFile }?.readText() } catch (_: Throwable) { null }
    fun savedAt(c: Context): Long = timeOf(saved(c))
    fun cardVisible(c: Context): Boolean = cardDue(savedAt(c), sp(c).getLong(KEY_DISMISSED, 0L))
    /** The user reported or closed the card: remember which crash, so the same one is not asked about again */
    fun dismiss(c: Context) { sp(c).edit().putLong(KEY_DISMISSED, savedAt(c)).apply() }
}
