package app.splitaudio

import android.content.Context

/** "Tell us" prompts: (1) the first time the split ran cleanly for about 10 minutes -> one card "Working on your <phone>?";
 *  (2) the routing fails -> "Something went wrong, report it with diagnostics". State in prefs "report".
 *  The decisions are pure functions (HealthTest); the Context part only reads and writes prefs. */
object Health {
    const val STREAK_MS = 600_000L        // rule on for this long, without error
    const val GAP_MS = 180_000L           // rule not seen on for longer than this = the streak is broken
    const val FAIL_NEED = 3               // consecutive failures before we bother the user
    const val FAIL_SPAN_MS = 90_000L      // ...and they must keep happening for at least this long (a hiccup right after start is not a failure)
    const val GRACE_MS = 90_000L          // failures within this long after the service (re)starts are ignored: Shizuku is often not reconnected yet after an update or reboot
    const val NOTIFY_GAP_MS = 6 * 3600_000L
    const val FAIL_CARD_MS = 24 * 3600_000L

    // ---- pure ----

    /** New streak start given the old one (0 = none), the last time the rule was seen on (0 = never) and now. */
    fun streakStart(start: Long, lastSeen: Long, now: Long): Long =
        if (start <= 0L || lastSeen <= 0L || now < lastSeen || now - lastSeen > GAP_MS) now else start

    fun successDue(start: Long, now: Long, shown: Boolean): Boolean = !shown && start > 0L && now >= start && now - start >= STREAK_MS

    /** A [Route.start] that left no router to talk to: anything except "already" / "no_apps" (nothing to route) / blank (nothing ran).
     *  0.5.4: when the router answers ([routerAlive]) it is only waiting for the music app, whatever the start text says: not a failure (#12). */
    fun isFailure(startResult: String, routerAlive: Boolean): Boolean =
        !routerAlive && startResult != "already" && startResult != "no_apps" && startResult.isNotBlank()

    fun shouldNotifyFailure(count: Int, lastNotify: Long, now: Long): Boolean =
        count >= FAIL_NEED && (lastNotify <= 0L || now < lastNotify || now - lastNotify >= NOTIFY_GAP_MS)

    fun failCardVisible(failAt: Long, now: Long): Boolean = failAt > 0L && now >= failAt && now - failAt < FAIL_CARD_MS

    /** Enough consecutive failures AND they span at least [FAIL_SPAN_MS] since the first one (so two quick misses while starting do not count). */
    fun failDue(count: Int, firstAt: Long, now: Long): Boolean = count >= FAIL_NEED && firstAt > 0L && now >= firstAt && now - firstAt >= FAIL_SPAN_MS

    /** Inside the start-up grace window: failures are logged but not counted. */
    fun inGrace(graceUntil: Long, now: Long): Boolean = graceUntil > 0L && now < graceUntil

    // ---- prefs ----

    private fun sp(c: Context) = c.getSharedPreferences("report", Context.MODE_PRIVATE)

    /** The split is on right now (called from the monitor, background thread). */
    fun noteOn(c: Context, now: Long = System.currentTimeMillis()) {
        val p = sp(c)
        val start = streakStart(p.getLong("streak_start", 0L), p.getLong("last_on", 0L), now)
        val e = p.edit().putLong("streak_start", start).putLong("last_on", now).putInt("fail_count", 0).remove("fail_at").remove("fail_first").putBoolean("ever_on", true)
        if (successDue(start, now, p.getBoolean("success_shown", false))) e.putBoolean("success_due", true)
        e.apply()
    }

    const val CONFIRM_MS = 24 * 3600_000L   // 0.5.7: an "It works!" report counts as confirmed when the split was on within this long

    /** Pure: the split was seen on recently enough for an "It works!" report to count as confirmed */
    fun isRecent(lastOn: Long, now: Long): Boolean = lastOn > 0L && now >= lastOn && now - lastOn < CONFIRM_MS

    /** When the split was last seen on (0 = never on this install) */
    fun lastOn(c: Context): Long = sp(c).getLong("last_on", 0L)

    fun recentlyOn(c: Context, now: Long = System.currentTimeMillis()): Boolean = isRecent(lastOn(c), now)

    /** Something went wrong: breaks the streak, counts, and (when it keeps failing) tells the user once in a while. */
    fun noteFailure(c: Context, reason: String, now: Long = System.currentTimeMillis()) {
        val p = sp(c)
        if (inGrace(p.getLong("grace_until", 0L), now)) { AppLog.add("failure ignored (start-up grace): $reason", now); return }
        val n = p.getInt("fail_count", 0) + 1
        val first = if (n == 1) now else p.getLong("fail_first", now)
        AppLog.add("failure #$n: $reason", now)
        val e = p.edit().putInt("fail_count", n).putLong("fail_first", first).remove("streak_start").remove("last_on")
        if (failDue(n, first, now)) e.putLong("fail_at", now)
        e.apply()
        if (failDue(n, first, now) && shouldNotifyFailure(n, p.getLong("fail_notified", 0L), now)) {
            p.edit().putLong("fail_notified", now).apply()
            Alerts.postLink(c, Alerts.ID_FAIL, c.getString(R.string.n_fail_title), c.getString(R.string.n_fail_text), Diag.problemUrl(c))
        }
    }

    /** Routing has worked at least once on this install (so a missing Shizuku grant later is a real failure, not first-run setup). */
    fun everOn(c: Context) = sp(c).getBoolean("ever_on", false)

    fun successCardVisible(c: Context) = sp(c).getBoolean("success_due", false) && !sp(c).getBoolean("success_shown", false)
    fun dismissSuccess(c: Context) { sp(c).edit().putBoolean("success_shown", true).remove("success_due").apply() }
    /** 0.5.6（用戶 2026-11-04：不要回報過就永遠收起）：「能用嗎？」的提問回答過（能用！／回報問題／Play 版關閉）記下版本與時間；
     *  換了版本或滿 30 天就再問一次（之後又不能用也問得到），按鈕本身一直都在。 */
    const val REASK_MS = 30L * 24 * 3600 * 1000
    fun reaskDue(answeredVer: Int, curVer: Int, answeredAt: Long, now: Long): Boolean = answeredVer != curVer || now - answeredAt >= REASK_MS
    fun answered(c: Context, verCode: Int, now: Long = System.currentTimeMillis()) {
        sp(c).edit().putBoolean("success_shown", true).remove("success_due").putInt("ans_ver", verCode).putLong("ans_at", now).apply()
    }
    /** Call on resume: re-arm the question (fresh 10-minute streak) when the answer is from another version or older than 30 days. */
    fun reaskIfDue(c: Context, verCode: Int, now: Long = System.currentTimeMillis()) {
        val p = sp(c)
        if (!p.getBoolean("success_shown", false)) return
        if (reaskDue(p.getInt("ans_ver", -1), verCode, p.getLong("ans_at", 0L), now))
            p.edit().putBoolean("success_shown", false).remove("success_due").putLong("streak_start", 0L).apply()
    }
    fun failCardVisible(c: Context, now: Long = System.currentTimeMillis()) = failCardVisible(sp(c).getLong("fail_at", 0L), now)
    fun dismissFailure(c: Context) { sp(c).edit().remove("fail_at").remove("fail_first").putInt("fail_count", 0).apply() }

    /** The monitor just (re)started: give Shizuku and the router [GRACE_MS] to come back before any miss counts as a failure. */
    fun startGrace(c: Context, now: Long = System.currentTimeMillis()) { sp(c).edit().putLong("grace_until", now + GRACE_MS).apply() }
}
