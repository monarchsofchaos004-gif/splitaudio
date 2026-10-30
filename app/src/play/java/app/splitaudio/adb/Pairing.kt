package app.splitaudio.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import app.splitaudio.MainActivity
import app.splitaudio.MonitorService
import app.splitaudio.R
import io.github.muntashirakon.adb.android.AdbMdns

/** 配對流程的共用狀態＋動作。PairService 找到「配對視窗」(_adb-tls-pairing._tcp) 的位址後，
 *  使用者可以在通知列（RemoteInput）或 App 內輸入 6 位數配對碼；兩條路都走 submit()。 */
object Pairing {
    const val KEY_CODE = "pair_code"
    const val NOTIF_ID = 2
    const val CH_PAIR = "pair"
    const val CH_ALERT = "alert"

    enum class St { IDLE, WAITING, FOUND, BUSY, OK, FAIL }
    @Volatile var st = St.IDLE
    @Volatile var host: String? = null
    @Volatile var port = -1
    @Volatile var failReason = ""
    private val main = Handler(Looper.getMainLooper())
    @Volatile var onChange: (() -> Unit)? = null

    private fun changed() { main.post { onChange?.invoke() } }

    fun set(s: St) { st = s; changed() }

    fun onFound(h: String?, p: Int) {
        if (st == St.BUSY || st == St.OK) return
        if (h != null && p > 0) { host = h; port = p; set(St.FOUND); notifyState(Adb.app) }
        else { host = null; port = -1; if (st == St.FOUND) { set(St.WAITING); notifyState(Adb.app) } }
    }

    /** 送出配對碼（任何執行緒叫；內部開背景執行緒）。done 在背景執行緒回呼：null＝成功，否則原因。 */
    fun submit(ctx: Context, code: String, done: ((String?) -> Unit)? = null) {
        val c = ctx.applicationContext
        if (!SetupLogic.validCode(code)) { done?.invoke("bad_code"); return }
        val h = host; val p = port
        if (h == null || p <= 0) { done?.invoke("no_dialog"); return }
        set(St.BUSY); notifyState(c)
        Thread {
            val err = Adb.pair(h, p, code)
            if (err == null) {
                set(St.OK); notifyState(c)
                // 配對成功 → 連線、授權自己、啟動監看
                val ok = Adb.ensure(15_000) && Adb.grantSecure()
                if (ok) MonitorService.ensure(c)
                c.stopService(Intent(c, PairService::class.java))
                notifyDone(c, ok)
            } else {
                failReason = err; set(St.FAIL); notifyState(c)
            }
            done?.invoke(err)
        }.start()
    }

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PAIR, c.getString(R.string.ch_pair), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, c.getString(R.string.ch_alert), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** 依目前狀態組配對通知（FOUND 時帶輸入框） */
    fun buildNotif(c: Context): Notification {
        channels(c)
        val b = Notification.Builder(c, CH_PAIR).setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(c.getString(R.string.n_pair_title)).setOngoing(true).setOnlyAlertOnce(true)
        when (st) {
            St.FOUND -> {
                b.setContentText(c.getString(R.string.n_pair_enter))
                val ri = RemoteInput.Builder(KEY_CODE).setLabel(c.getString(R.string.n_pair_label)).build()
                // RemoteInput 的 PendingIntent 一定要可變（FLAG_MUTABLE），Android 14 起還要明確指定元件
                val pi = PendingIntent.getBroadcast(c, 0, Intent(c, PairReceiver::class.java).setAction(PairReceiver.ACT_CODE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                b.addAction(Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_menu_edit), c.getString(R.string.n_pair_action), pi).addRemoteInput(ri).build())
            }
            St.BUSY -> b.setContentText(c.getString(R.string.n_pair_busy))
            St.OK -> b.setContentText(c.getString(R.string.n_pair_ok))
            St.FAIL -> b.setContentText(c.getString(R.string.n_pair_fail, failReason))
            else -> b.setContentText(c.getString(R.string.n_pair_wait))
        }
        b.setContentIntent(PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        return b.build()
    }

    fun notifyState(c: Context) {
        try { c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(c)) } catch (_: Exception) {}
    }

    private fun notifyDone(c: Context, ok: Boolean) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIF_ID)
            if (!ok) alert(c, c.getString(R.string.n_wd_title), c.getString(R.string.n_wd_text))
        } catch (_: Exception) {}
    }

    private var lastAlert = 0L
    /** 需要使用者處理的提醒（10 分鐘內不重複） */
    fun alert(c: Context, title: String, text: String) {
        val now = System.currentTimeMillis()
        if (now - lastAlert < 600_000) return
        lastAlert = now
        channels(c)
        val n = Notification.Builder(c, CH_ALERT).setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()
        try { c.getSystemService(NotificationManager::class.java).notify(3, n) } catch (_: Exception) {}
    }

    fun mdns(c: Context): AdbMdns = AdbMdns(c, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { h, p -> onFound(h?.hostAddress, p) }
}
