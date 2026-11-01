package app.splitaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** 常駐（前景通知）：聽「播放變化」「輸出裝置變化」→ 藍牙連著＋有音樂在播＋路由沒在跑 → 經 [Backends]（Shizuku 或 Play 版內建 ADB）啟動 AppRoute。
 *  沒有定時輪詢；只有藍牙連著時每 60 秒補查一次（防回呼漏掉）。啟動失敗 30 秒內不重試。
 *  自訂模式：有指定輸出的 App＋有任何聲音在播（本 App 看不到是誰在播，路由程式看得到）就起，不管藍牙；每 60 秒補查。
 *  回呼都存成欄位，服務結束（onDestroy）時全部解除；連發的事件共用一個固定的 Runnable，重新計時、只跑最後一次。 */
class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var lastTry = 0L
    private var lastMix = 0L
    @Volatile private var state = ""
    private lateinit var am: AudioManager
    private var binderHandle: Any? = null

    private val playRun = Runnable { check("play"); mixer() }
    private val btRun = Runnable { check("bt") }
    private val startRun = Runnable { check("start") }
    private val shizukuRun = Runnable { check("shizuku") }
    private val tickRun = object : Runnable {
        override fun run() { if (bt() || Outputs.custom(this@MonitorService)) check("tick"); h.postDelayed(this, 60_000) }
    }
    private val playbackCb = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(c: MutableList<AudioPlaybackConfiguration>?) { rearm(playRun, 1200) }
    }
    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(a: Array<out AudioDeviceInfo>?) { rearm(btRun, 2500) }
        override fun onAudioDevicesRemoved(r: Array<out AudioDeviceInfo>?) { rearm(btRun, 2500) }
    }

    /** 重新計時：同一個 Runnable 在等的話先取消，所以事件連發只會跑最後一次 */
    private fun rearm(r: Runnable, delayMs: Long) { h.removeCallbacks(r); h.postDelayed(r, delayMs) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        state = getString(R.string.s_idle)
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startForeground(1, notif(), Dist.fgsType)
        am.registerAudioPlaybackCallback(playbackCb, h)
        am.registerAudioDeviceCallback(deviceCb, h)
        binderHandle = ShizukuBackend.addBinderListener { rearm(shizukuRun, 0) }
        Dist.onServiceCreate(this)
        rearm(tickRun, 60_000)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        if (::am.isInitialized) {
            try { am.unregisterAudioPlaybackCallback(playbackCb) } catch (_: Throwable) {}
            try { am.unregisterAudioDeviceCallback(deviceCb) } catch (_: Throwable) {}
        }
        ShizukuBackend.removeBinderListener(binderHandle); binderHandle = null
        Dist.onServiceDestroy(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { rearm(startRun, 0); return START_STICKY }

    private fun bt(): Boolean = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }

    /** 路由規則現在是開的嗎？（背景執行緒叫）順便：第一次看到分流掛上時，若用戶還沒決定「開影片會不會暫停音樂」就發通知請他決定 */
    private fun ruleOnNoted(): Boolean {
        val on = Route.ruleOn(this)
        if (on) { Health.noteOn(applicationContext); noteSplit() }
        return on
    }

    /** 用戶還沒決定（Mf.ASK）而且系統的 multi audio focus 是關的 → 發一則通知（10 分鐘內不重發，一共最多 [Mf.MAX_NOTIFY] 則，之後只有主畫面的決定卡）。背景執行緒叫。 */
    private fun noteSplit() {
        val c = applicationContext
        val d = Mf.decision(c)
        if (d != Mf.ASK) return
        val sent = Mf.notifiedCount(c)
        if (sent >= Mf.MAX_NOTIFY) return   // 發滿了：不再打擾（也不用再去問 shell）
        val now = System.currentTimeMillis()
        val last = Mf.lastAsk(c)
        if (last > 0L && now >= last && now - last < Mf.COOLDOWN_MS) return   // 先用便宜的條件擋掉，不用每次都去問 shell
        val sys = Route.multiFocusState()
        val d2 = Mf.reconcile(d, sys)
        if (d2 != d) { Mf.setDecision(c, d2); return }   // 系統設定早就是開的（舊版開的）→ 不用問
        if (!Mf.shouldNotify(d2, sys, last, now, sent)) return
        Mf.markAsked(c, now)
        Alerts.post(c, Alerts.ID_MF, getString(R.string.n_mf_title), getString(R.string.n_mf_text))
    }

    private fun check(why: String) {
        if (Outputs.custom(this)) { checkCustom(); return }
        val b = bt(); val music = am.isMusicActive
        if (!(b && music)) { Thread { setState(getString(if (ruleOnNoted()) R.string.s_split else if (b) R.string.s_bt_idle else R.string.s_idle)) }.start(); return }
        Thread {
            if (Route.mode(this) == "map") Route.stop(this)   // 剛從自訂模式換回來還在跑的 → 停掉照簡單模式重起
            if (ruleOnNoted()) { setState(getString(R.string.s_split)); return@Thread }
            if (Route.running(this)) { setState(getString(R.string.s_router_wait)); return@Thread }
            val now = System.currentTimeMillis()
            if (now - lastTry < 30_000) return@Thread
            lastTry = now
            if (Backends.current() == null) { noBackend(); return@Thread }
            val r = Route.start(this)
            Thread.sleep(2500)
            afterStart(r)
        }.start()
    }

    /** Route.start was just run ([r] = its result): split is on -> fine; otherwise a failure (route command error / rule did not appear) goes to [Health] */
    private fun afterStart(r: String) {
        if (ruleOnNoted()) { setState(getString(R.string.s_split)); return }
        if (Health.isFailure(r)) Health.noteFailure(applicationContext, r)
        setState(Dist.startResultText(this, r))
    }

    /** No usable backend: tell the notification; if Shizuku is running but its permission was refused, that is a failure to report */
    private fun noBackend() {
        setState(Dist.needSetupText(this))
        if (ShizukuBackend.alive() && !ShizukuBackend.granted()) Health.noteFailure(applicationContext, "shizuku permission not granted")
    }

    private fun checkCustom() {
        val any = Outputs.hasAny(this)
        if (!(any && am.isMusicActive)) { Thread { setState(getString(if (ruleOnNoted()) R.string.s_split else if (!any) R.string.s_no_apps else R.string.s_idle)) }.start(); return }
        Thread {
            if (Route.running(this) && Route.mode(this) != "map") Route.stop(this)   // 簡單模式的路由還在跑 → 換成自訂
            if (ruleOnNoted()) { setState(getString(R.string.s_split)); return@Thread }
            if (Route.running(this)) { setState(getString(R.string.s_custom_wait)); return@Thread }
            val now = System.currentTimeMillis()
            if (now - lastTry < 30_000) return@Thread
            lastTry = now
            if (Backends.current() == null) { noBackend(); return@Thread }
            val r = Route.start(this)
            Thread.sleep(2500)
            afterStart(r)
        }.start()
    }

    /** 每個 App 音量（0.4）：有設定但 mixer 沒在跑（例：手機重開過）→ 起來；30 秒內不重查 */
    private fun mixer() {
        val now = System.currentTimeMillis()
        if (now - lastMix < 30_000 || !Mixer.hasAny(this)) return
        lastMix = now
        val c = applicationContext
        Thread { Mixer.ensure(c) }.start()
    }

    private fun setState(s: String) {
        if (s == state) return
        state = s
        AppLog.add("state: $s")
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notif()) } catch (_: Exception) {}
    }

    private fun notif(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("route", getString(R.string.ch_route), NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "route").setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(getString(R.string.notif_title, state)).setContentIntent(pi).setOngoing(true).build()
    }

    companion object {
        fun ensure(c: Context) { try { c.startForegroundService(Intent(c, MonitorService::class.java)) } catch (_: Exception) {} }
        /** 後端剛接回／配對完成：叫監看服務立刻重新檢查（有聲音該分流就起路由） */
        fun kick(c: Context) { ensure(c) }
    }
}
