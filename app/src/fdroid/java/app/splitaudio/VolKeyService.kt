package app.splitaudio

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** 音量鍵：分流規則掛著＋前景是走喇叭的 App → 調「手機喇叭」（問 AppRoute 本機指令口）；
 *  音樂 App（[Music] 清單，用戶可選）／桌面（[Launcher] 動態查）／系統介面／鎖屏照常調藍牙；本 App 自己的畫面在前面＝調手機喇叭（0.5.9）。規則沒掛＝完全不攔。
 *  Android 13（API 33）起才有系統的 VolumeInfo（路由程式靠它調喇叭音量）→ API 33 以前不輪詢、不攔任何鍵。 */
class VolKeyService : AccessibilityService() {
    @Volatile private var routeOn = false
    @Volatile private var routeAsked = 0L          // 上次問到 state 的時間；按鍵時太舊就當場再問一次（0.5.2）
    @Volatile private var lastPkg: String? = null    // 最近一次視窗切換的前景套件（rootInActiveWindow 讀不到時用，0.5.2）
    @Volatile private var alive = true
    private val consumed = HashSet<Int>()
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        MonitorService.ensure(this)
        if (Build.VERSION.SDK_INT < MIN_SDK) return
        try {   // 0.5.3: some ROMs ignore accessibilityFlags from XML → request key filtering again at runtime and log what we actually got
            val si = serviceInfo
            if (si != null) {
                si.flags = si.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                serviceInfo = si
                AppLog.add("volkey: connected flags=0x%x caps=0x%x".format(serviceInfo?.flags ?: -1, serviceInfo?.capabilities ?: -1))
            } else AppLog.add("volkey: connected, serviceInfo null")
        } catch (e: Exception) { AppLog.add("volkey: flags err ${e.javaClass.simpleName}") }
        Thread {
            while (alive) {
                routeOn = Route.ask(this@VolKeyService, "state") == "on"; routeAsked = System.currentTimeMillis()
                try { Thread.sleep(if (routeOn) 2000 else 4000) } catch (_: InterruptedException) { return@Thread }
            }
        }.apply { isDaemon = true }.start()
    }
    override fun onDestroy() { alive = false; super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) e.packageName?.toString()?.let { if (it != "com.android.systemui") lastPkg = it }
    }
    override fun onInterrupt() {}

    override fun onKeyEvent(e: KeyEvent?): Boolean {
        if (e == null || Build.VERSION.SDK_INT < MIN_SDK) return false
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (e.action == KeyEvent.ACTION_DOWN) AppLog.add("volkey: key ${if (k == KeyEvent.KEYCODE_VOLUME_UP) "up" else "down"} received")   // 0.5.3
        if (e.action == KeyEvent.ACTION_UP) return consumed.remove(k)
        if (e.action != KeyEvent.ACTION_DOWN) return false
        if (!routeOn && System.currentTimeMillis() - routeAsked > 1500) {   // 0.5.2：輪詢結果太舊就當場再問（本機指令口，約 10 ms），免得剛開規則那幾秒按鍵被放過
            routeOn = Route.ask(this, "state", 300) == "on"; routeAsked = System.currentTimeMillis()
        }
        if (!routeOn) { AppLog.add("volkey: rule off, pass"); return false }
        // 0.5.9: lock screen = the keys change the music on Bluetooth (the app that was open before locking does not count)
        if ((getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked) { AppLog.add("volkey: locked, pass"); return false }
        val fg = (try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }) ?: lastPkg
        if (fg == null) { AppLog.add("volkey: no foreground pkg, pass"); return false }   // 0.5.2：讀不到就用最近視窗切換的套件（全螢幕影片常讀不到）
        // 自訂模式：音樂 App 也可能被指定走喇叭 → 只略過桌面／系統介面，其餘交給路由程式判斷（不是走喇叭的回 pass，照原本調）
        val home = Launcher.packages(this)
        if (fg != packageName && (if (Outputs.custom(this)) fg in home else fg in home || fg in Music.set(this))) { AppLog.add("volkey: fg=$fg is home/music, pass"); return false }
        consumed.add(k)
        val dir = if (k == KeyEvent.KEYCODE_VOLUME_UP) "up" else "down"
        val target = if (fg == packageName) "@speaker" else fg   // 0.5.9: this app's own screen in front = change the phone speaker volume
        Thread {
            val r = Route.ask(this, "vol $dir $target")
            AppLog.add("volkey: $dir fg=$fg -> ${r ?: "no reply"}")
            main.post {
                if (r != null && r.startsWith("ok ")) Toast.makeText(this, getString(R.string.toast_vol, r.substring(3)), Toast.LENGTH_SHORT).show()
                else try {   // 不是走喇叭的 App／規則剛好拿掉 → 這次按鍵已被攔，自己照原本調藍牙
                    (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if (dir == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
            }
        }.start()
        return true
    }

    companion object {
        /** 音量鍵功能需要的最低 API（VolumeInfo） */
        const val MIN_SDK = 33
    }
}
