package app.splitaudio

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.LinearLayout
import app.splitaudio.adb.AdbBackend
import app.splitaudio.adb.PlaySetup
import app.splitaudio.adb.Reconnect

/** fdroid 版與 play 版的差異都收在這裡；兩個版本各有一份同名同介面的 Dist（src/fdroid 那份只有 Shizuku）。
 *  play 版：Shizuku 有就用，沒有走內建 ADB（無線偵錯配對）；Pro 功能要買斷或試用；沒有無障礙音量鍵。 */
object Dist {
    /** 後端優先序（第一個 granted 的就用） */
    fun backends(): List<Backend> = listOf(ShizukuBackend, AdbBackend)

    /** Build flavor name (shown in diagnostics and issue reports) */
    const val flavor = "play"

    /** New-version hint (play: off, Google Play updates the app) */
    const val updateCheck = false

    /** 付費版：回報卡可以關、新版提示可以跳過（免費版那條「不給關閉」的規則不套用在這裡） */
    val feedbackDismissable = true

    /** Pro 功能（自訂模式、每個 App 的音量）是否一律開放 */
    val proAlways = false

    /** 有沒有無障礙音量鍵（VolKeyService）；Play 審核對無障礙很嚴，play 版不放 */
    val hasVolKey = false

    /** 前景服務類型（MonitorService、PairService）；要跟 manifest 宣告的一樣。play＝connectedDevice（跟藍牙／WiFi 裝置連線有關；manifest 有 CHANGE_WIFI_MULTICAST_STATE） */
    val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    /** 主畫面的「一次性設定」區塊（內建 ADB 4 步引導） */
    fun setupSection(a: MainActivity, parent: LinearLayout): SetupSection? = PlaySetup(a, parent)

    private var netCb: ConnectivityManager.NetworkCallback? = null

    /** 監看服務啟動：網路變成 WiFi（含開機後 WiFi 連上）→ 已配對就自動接回 ADB，必要時啟動路由；啟動時也接回一次 */
    fun onServiceCreate(s: MonitorService) {
        onServiceDestroy(s)   // 服務被重建時先清掉舊的回呼
        val app = s.applicationContext
        try {
            val cm = s.getSystemService(ConnectivityManager::class.java)
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { Thread { Thread.sleep(4000); Reconnect.run(app, "wifi") }.start() }
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            netCb = cb
        } catch (_: Throwable) {}
        Thread { Reconnect.run(app, "start") }.start()
    }

    fun onServiceDestroy(s: MonitorService) {
        val cb = netCb ?: return
        netCb = null
        try { s.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } catch (_: Throwable) {}
    }

    /** 沒有可用後端時，通知列要顯示什麼 */
    fun needSetupText(c: Context): String = c.getString(R.string.s_need_pair)

    /** 路由沒起來時，通知列要顯示什麼（r＝Route.start 的結果） */
    fun startResultText(c: Context, r: String): String =
        if (r.contains("no_adb")) c.getString(R.string.s_no_adb) else c.getString(R.string.s_result, r)
}
