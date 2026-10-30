package app.splitaudio.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.splitaudio.MonitorService
import app.splitaudio.R
import app.splitaudio.Route

/** 開機／網路變成 WiFi 時自動接回：已配對的話，需要 ADB（路由程式沒在跑）就打開無線偵錯（有 WRITE_SECURE_SETTINGS 才寫得了）、
 *  等 mDNS 的 connect 服務出現後連線；成功就叫監看服務重新檢查（該起路由就起）。失敗發通知請使用者開無線偵錯。 */
object Reconnect {
    @Volatile private var busy = false

    fun wifiUp(c: Context): Boolean = try {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Throwable) { false }

    fun inputs(c: Context) = SetupLogic.Inputs(
        devOptions = Adb.devOptionsOn(), wifi = wifiUp(c), wirelessDebug = Adb.wirelessDebugOn(),
        paired = Adb.paired(), secureGranted = Adb.secureGranted(), connected = Adb.connected())

    /** 背景執行緒叫 */
    fun run(c: Context, why: String) {
        if (busy) return
        busy = true
        try {
            val act = SetupLogic.reconnect(inputs(c), Route.running())
            when (act) {
                SetupLogic.Action.NONE -> {}
                SetupLogic.Action.CONNECT, SetupLogic.Action.ENABLE_WD_THEN_CONNECT -> {
                    val ok = Adb.ensure(12_000)
                    if (ok) { Adb.grantSecure(); MonitorService.kick(c) }
                    else if (Adb.paired()) Pairing.alert(c, c.getString(R.string.n_wd_title), c.getString(R.string.n_wd_text))
                }
                SetupLogic.Action.NOTIFY_ENABLE_WD ->
                    Pairing.alert(c, c.getString(R.string.n_wd_title), c.getString(R.string.n_wd_text))
            }
        } finally { busy = false }
    }
}
