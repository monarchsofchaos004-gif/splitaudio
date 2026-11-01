package app.splitaudio

import android.content.Context
import android.content.pm.ServiceInfo
import android.widget.LinearLayout

/** fdroid 版（F-Droid／GitHub 發行）與 play 版的差異都收在這裡；兩個版本各有一份同名同介面的 Dist（src/play 那份多了內建 ADB）。
 *  fdroid 版：只用 Shizuku；全部功能免費；有無障礙音量鍵。 */
object Dist {
    /** 後端優先序（第一個 granted 的就用） */
    fun backends(): List<Backend> = listOf(ShizukuBackend)

    /** Build flavor name (shown in diagnostics and issue reports) */
    const val flavor = "fdroid"

    /** New-version hint (fdroid: only links to the GitHub release page, never downloads or installs) */
    const val updateCheck = true

    /** Pro 功能（自訂模式、每個 App 的音量）是否一律開放 */
    val proAlways = true

    /** 有沒有無障礙音量鍵（VolKeyService） */
    val hasVolKey = true

    /** 常駐監看服務（MonitorService）的前景服務類型；要跟這個版本的 manifest 宣告的一樣。fdroid＝specialUse（沒有更貼切的類型） */
    val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    /** 主畫面的「一次性設定」區塊；fdroid 版沒有，用主畫面內建的 Shizuku 那一列 */
    @Suppress("UNUSED_PARAMETER")
    fun setupSection(a: MainActivity, parent: LinearLayout): SetupSection? = null

    @Suppress("UNUSED_PARAMETER")
    fun onServiceCreate(s: MonitorService) {}

    @Suppress("UNUSED_PARAMETER")
    fun onServiceDestroy(s: MonitorService) {}

    /** 沒有可用後端時，通知列要顯示什麼 */
    fun needSetupText(c: Context): String = c.getString(if (ShizukuBackend.alive()) R.string.s_need_grant else R.string.s_shizuku_down)

    /** 路由沒起來時，通知列要顯示什麼（r＝Route.start 的結果） */
    fun startResultText(c: Context, r: String): String = c.getString(R.string.s_result, r)
}
