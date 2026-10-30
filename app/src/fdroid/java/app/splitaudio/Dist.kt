package app.splitaudio

import android.content.Context
import android.widget.LinearLayout

/** fdroid 版（F-Droid／GitHub 發行）與 play 版的差異都收在這裡；兩個版本各有一份同名同介面的 Dist（src/play 那份多了內建 ADB）。
 *  fdroid 版：只用 Shizuku；全部功能免費；有無障礙音量鍵。 */
object Dist {
    /** 後端優先序（第一個 granted 的就用） */
    fun backends(): List<Backend> = listOf(ShizukuBackend)

    /** Pro 功能（自訂模式、每個 App 的音量）是否一律開放 */
    val proAlways = true

    /** 有沒有無障礙音量鍵（VolKeyService） */
    val hasVolKey = true

    /** 主畫面的「一次性設定」區塊；fdroid 版沒有，用主畫面內建的 Shizuku 那一列 */
    @Suppress("UNUSED_PARAMETER")
    fun setupSection(a: MainActivity, parent: LinearLayout): SetupSection? = null

    fun onServiceCreate(s: MonitorService) {}
    fun onServiceDestroy(s: MonitorService) {}

    /** 沒有可用後端時，通知列要顯示什麼 */
    fun needSetupText(c: Context): String = c.getString(if (ShizukuBackend.alive()) R.string.s_need_grant else R.string.s_shizuku_down)

    /** 路由沒起來時，通知列要顯示什麼（r＝Route.start 的結果） */
    fun startResultText(c: Context, r: String): String = c.getString(R.string.s_result, r)
}
