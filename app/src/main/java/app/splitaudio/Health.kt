package app.splitaudio

import android.content.Context

/** "Tell us" prompts: (1) the first time the split ran cleanly for about 10 minutes -> one card "Working on your <phone>?";
 *  (2) the routing fails -> "Something went wrong, report it with diagnostics". State in prefs "report".
 *  The decisions are pure functions (HealthTest); the Context part only reads and writes prefs. */
object Health {
    const val STREAK_MS = 600_000L        // rule on for this long, without error
    const val GAP_MS = 180_000L           // rule not seen on for longer than this = the streak is broken
    const val FAIL_NEED = 2               // consecutive failures before we bother the user
    const val NOTIFY_GAP_MS = 6 * 3600_000L
    const val FAIL_CARD_MS = 24 * 3600_000L

    // ---- pure ----

    /** New streak start given the old one (0 = none), the last time the rule was seen on (0 = never) and now. */
    fun streakStart(start: Long, lastSeen: Long, now: Long): Long =
        if (start <= 0L || lastSeen <= 0L || now < lastSeen || now - lastSeen > GAP_MS) now else start

    fun successDue(start: Long, now: Long, shown: Boolean): Boolean = !shown && start > 0L && now >= start && now - start >= STREAK_MS

    /** A [Route.start] result that did not lead to a running rule: anything except "already" / "no_apps" (nothing to route). */
    fun isFailure(startResult: String): Boolean = startResult != "already" && startResult != "no_apps" && startResult.isNotBlank()

    fun shouldNotifyFailure(count: Int, lastNotify: Long, now: Long): Boolean =
        count >= FAIL_NEED && (lastNotify <= 0L || now < lastNotify || now - lastNotify >= NOTIFY_GAP_MS)

    fun failCardVisible(failAt: Long, now: Long): Boolean = failAt > 0L && now >= failAt && now - failAt < FAIL_CARD_MS

    // ---- prefs ----

    private fun sp(c: Context) = c.getSharedPreferences("report", Context.MODE_PRIVATE)

    /** The split is on right now (called from the monitor, background thread). */
    fun noteOn(c: Context, now: Long = System.currentTimeMillis()) {
        val p = sp(c)
        val start = streakStart(p.getLong("streak_start", 0L), p.getLong("last_on", 0L), now)
        val e = p.edit().putLong("streak_start", start).putLong("last_on", now).putInt("fail_count", 0).remove("fail_at")
        if (successDue(start, now, p.getBoolean("success_shown", false))) e.putBoolean("success_due", true)
        e.apply()
    }

    /** Something went wrong: breaks the streak, counts, and (when it keeps failing) tells the user once in a while. */
    fun noteFailure(c: Context, reason: String, now: Long = System.currentTimeMillis()) {
        val p = sp(c)
        val n = p.getInt("fail_count", 0) + 1
        AppLog.add("failure #$n: $reason", now)
        val e = p.edit().putInt("fail_count", n).remove("streak_start").remove("last_on")
        if (n >= FAIL_NEED) e.putLong("fail_at", now)
        e.apply()
        if (shouldNotifyFailure(n, p.getLong("fail_notified", 0L), now)) {
            p.edit().putLong("fail_notified", now).apply()
            Alerts.postLink(c, Alerts.ID_FAIL, c.getString(R.string.n_fail_title), c.getString(R.string.n_fail_text), Diag.problemUrl(c))
        }
    }

    fun successCardVisible(c: Context) = sp(c).getBoolean("success_due", false) && !sp(c).getBoolean("success_shown", false)
    fun dismissSuccess(c: Context) { sp(c).edit().putBoolean("success_shown", true).remove("success_due").apply() }
    fun failCardVisible(c: Context, now: Long = System.currentTimeMillis()) = failCardVisible(sp(c).getLong("fail_at", 0L), now)
    fun dismissFailure(c: Context) { sp(c).edit().remove("fail_at").putInt("fail_count", 0).apply() }
}
