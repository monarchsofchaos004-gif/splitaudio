package app.splitaudio

import android.content.Context

/** 「開影片不打斷音樂」（Android 內建 multi audio focus）的用戶決定：第一次分流時先問，不自己改系統設定。
 *  prefs "mf" 的 "mf_decision"：ask（還沒決定，預設）／on（用戶同意）／off（用戶不要）。
 *  路由程式那邊對應 /data/local/tmp 的 approute.mf_ok（同意）／approute.nomf（不要）兩個旗標檔，由 approute.sh multifocus on|off 寫。 */
object Mf {
    const val ASK = "ask"
    const val ON = "on"
    const val OFF = "off"
    /** 通知最少隔多久才再發一次 */
    const val COOLDOWN_MS = 600_000L
    private fun sp(c: Context) = c.getSharedPreferences("mf", Context.MODE_PRIVATE)

    fun decision(c: Context): String = normalize(sp(c).getString("mf_decision", null))
    fun setDecision(c: Context, d: String) { sp(c).edit().putString("mf_decision", normalize(d)).apply() }
    fun lastAsk(c: Context): Long = sp(c).getLong("asked_at", 0L)
    fun markAsked(c: Context, now: Long) { sp(c).edit().putLong("asked_at", now).apply() }

    /** 純邏輯（可單元測試）：不認得的值一律當「還沒決定」 */
    fun normalize(d: String?): String = if (d == ON) ON else if (d == OFF) OFF else ASK

    fun decisionFor(on: Boolean): String = if (on) ON else OFF

    /** 純邏輯：把用戶決定跟系統現況對齊。system＝Route.multiFocusState()："on"／"off"／"off_user"（舊版的關閉鈕＝等於決定過）／null（讀不到）。
     *  已經決定過的不動；還沒決定但系統設定已經開著（舊版開的、或用戶自己開的）＝不用再問，當成同意。 */
    fun reconcile(decision: String, system: String?): String = when {
        system == null -> decision
        decision != ASK -> decision
        system == "on" -> ON
        system == "off_user" -> OFF
        else -> ASK
    }

    /** 純邏輯：要不要顯示決定卡／發通知＝用戶沒決定過，而且系統設定現在是關的 */
    fun needsAsk(decision: String, system: String?): Boolean = reconcile(decision, system) == ASK && system == "off"

    /** 純邏輯：要不要發通知＝需要問，且距離上次發超過 [COOLDOWN_MS]（手機時間被往回調也放行） */
    fun shouldNotify(decision: String, system: String?, lastAskMs: Long, nowMs: Long): Boolean =
        needsAsk(decision, system) && (lastAskMs <= 0L || nowMs < lastAskMs || nowMs - lastAskMs >= COOLDOWN_MS)
}
