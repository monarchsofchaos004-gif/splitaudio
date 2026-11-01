package app.splitaudio

/** The app's own tiny in-memory log (last [MAX] lines, lost when the process dies). It only ever gets short status texts
 *  (route start results, state changes); the diagnostics text includes the last 20 lines. */
object AppLog {
    const val MAX = 100
    private val lines = ArrayDeque<String>()

    /** Pure: one log line (HH:MM:SS prefix from [nowMs], local time zone) */
    fun format(nowMs: Long, msg: String): String {
        val s = (nowMs / 1000 + java.util.TimeZone.getDefault().getOffset(nowMs) / 1000).mod(86400L)
        return "%02d:%02d:%02d %s".format(s / 3600, s / 60 % 60, s % 60, msg.replace('\n', ' ').take(200))
    }

    @Synchronized fun add(msg: String, nowMs: Long = System.currentTimeMillis()) {
        lines.addLast(format(nowMs, msg))
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized fun tail(n: Int = Report.LOG_LINES): List<String> = lines.toList().takeLast(n)
    @Synchronized fun clear() = lines.clear()
}
