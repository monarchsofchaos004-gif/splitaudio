package app.splitaudio

import android.content.Context

/** Pro 功能閘門（自訂模式、每個 App 的音量）。
 *  fdroid 版全部免費（[Dist.proAlways]）；play 版＝已購買（prefs "pro_licensed"，Play Billing 之後接）或 7 天試用內。
 *  試用從第一次需要判斷時起算，起算時間存 prefs "first_run"。 */
object Pro {
    const val TRIAL_DAYS = 7
    private const val DAY_MS = 86_400_000L
    private fun sp(c: Context) = c.getSharedPreferences("pro", Context.MODE_PRIVATE)

    /** 純邏輯（可單元測試）：試用還剩幾天（無條件進位；0＝已到期）。firstRunMs<=0＝還沒起算＝整個試用期都在。 */
    fun daysLeft(firstRunMs: Long, nowMs: Long, trialDays: Int = TRIAL_DAYS): Int {
        if (firstRunMs <= 0L) return trialDays
        val used = (nowMs - firstRunMs).coerceAtLeast(0L)   // 手機時間被往回調時不給超過整個試用期
        val left = trialDays * DAY_MS - used
        return if (left <= 0L) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    fun licensed(c: Context): Boolean = sp(c).getBoolean("pro_licensed", false)

    private fun firstRun(c: Context): Long {
        val sp = sp(c)
        val t = sp.getLong("first_run", 0L)
        if (t > 0L) return t
        val now = System.currentTimeMillis()
        sp.edit().putLong("first_run", now).apply()
        return now
    }

    fun trialDaysLeft(c: Context): Int = daysLeft(firstRun(c), System.currentTimeMillis())

    fun unlocked(c: Context): Boolean = Dist.proAlways || licensed(c) || trialDaysLeft(c) > 0
}
