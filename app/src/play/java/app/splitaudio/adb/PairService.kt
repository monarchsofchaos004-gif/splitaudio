package app.splitaudio.adb

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.muntashirakon.adb.android.AdbMdns

/** 配對期間的前景服務：用 mDNS 找「使用配對碼配對裝置」視窗，找到後通知列出現輸入框。10 分鐘沒配好自動結束。 */
class PairService : Service() {
    private var mdns: AdbMdns? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Pairing.st = Pairing.St.WAITING; Pairing.host = null; Pairing.port = -1
        startForeground(Pairing.NOTIF_ID, Pairing.buildNotif(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Pairing.set(Pairing.St.WAITING)
        try { mdns?.stop() } catch (_: Throwable) {}
        mdns = Pairing.mdns(this).also { it.start() }
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ stopSelf() }, 600_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { mdns?.stop() } catch (_: Throwable) {}
        h.removeCallbacksAndMessages(null)
        if (Pairing.st == Pairing.St.WAITING || Pairing.st == Pairing.St.FOUND) Pairing.set(Pairing.St.IDLE)
        super.onDestroy()
    }
}
