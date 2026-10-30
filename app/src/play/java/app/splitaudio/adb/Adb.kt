package app.splitaudio.adb

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

/** libadb-android 的連線管理：金鑰＋憑證存在 App 私有目錄（filesDir，別的 App 讀不到；allowBackup=false 不會被備份帶走）。 */
class AdbManager(ctx: Context) : AbsAdbConnectionManager() {
    private val priv: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        val kf = File(ctx.filesDir, "adbkey.pk8"); val cf = File(ctx.filesDir, "adbkey.crt")
        var p: PrivateKey? = null; var c: Certificate? = null
        try {
            if (kf.exists() && cf.exists()) {
                p = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                c = CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream())
            }
        } catch (_: Exception) { p = null; c = null }
        if (p == null || c == null) {
            val kp: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            p = kp.private; c = CertGen.selfSigned(kp, "SplitAudio")
            kf.writeBytes(p.encoded); cf.writeBytes(c.encoded)
            // 換了新金鑰＝舊配對作廢
            Adb.prefs(ctx).edit().putBoolean("paired", false).apply()
        }
        priv = p!!; cert = c!!
    }

    override fun getPrivateKey(): PrivateKey = priv
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "SplitAudio"
}

/** 本 App 對「自己這支手機的 adbd」的 ADB 連線（Play 版取代 Shizuku；[AdbBackend] 包這個）。run() 回 (rc, 輸出)。
 *  rc：-2 還沒配對、-4 連不上 ADB、-1 逾時、-5 輸出沒帶回傳碼、-3 例外。 */
object Adb {
    lateinit var app: Context
    @Volatile private var mgr: AdbManager? = null
    private val connLock = Any()
    @Volatile var lastError = ""

    fun init(c: Context) { app = c.applicationContext }
    fun prefs(c: Context) = c.getSharedPreferences("adb", Context.MODE_PRIVATE)

    fun paired(): Boolean = prefs(app).getBoolean("paired", false)
    fun setPaired(v: Boolean) { prefs(app).edit().putBoolean("paired", v).apply() }
    fun manager(): AdbManager = mgr ?: synchronized(connLock) { mgr ?: AdbManager(app).also { mgr = it } }
    fun connected(): Boolean = try { mgr?.isConnected == true } catch (_: Throwable) { false }
    fun secureGranted(): Boolean = app.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    fun wirelessDebugOn(): Boolean = try { Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", 0) == 1 } catch (_: Throwable) { false }
    fun devOptionsOn(): Boolean = try { Settings.Global.getInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 } catch (_: Throwable) { false }

    /** 有 WRITE_SECURE_SETTINGS 時直接把無線偵錯打開（重開機後系統會自己關掉它） */
    fun enableWirelessDebug(): Boolean = try {
        if (!secureGranted()) false else Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)
    } catch (_: Throwable) { false }

    /** 確保連著（必要時先打開無線偵錯、用 mDNS 找 connect 服務）。背景執行緒叫。 */
    fun ensure(timeoutMs: Long = 10_000): Boolean = synchronized(connLock) {
        val m = manager()
        if (m.isConnected) return true
        try { m.disconnect() } catch (_: Throwable) {}
        var t = timeoutMs
        if (!wirelessDebugOn() && enableWirelessDebug()) { Thread.sleep(1500); t += 5000 }
        try {
            m.connectTls(app, t)
            if (m.isConnected) { lastError = ""; true } else { lastError = "not_connected"; false }
        } catch (e: AdbPairingRequiredException) {
            lastError = "pairing_required"; setPaired(false); false
        } catch (e: Throwable) {
            lastError = e.javaClass.simpleName + ":" + (e.message ?: "").take(80); false
        }
    }

    fun disconnect() { try { mgr?.disconnect() } catch (_: Throwable) {} }

    /** 配對（背景執行緒叫）。成功回 null，失敗回原因。 */
    fun pair(host: String, port: Int, code: String): String? = synchronized(connLock) {
        try {
            val m = manager()
            if (m.pair(host, port, code)) { setPaired(true); null } else "rejected"
        } catch (e: Throwable) { e.javaClass.simpleName + ":" + (e.message ?: "").take(80) }
    }

    /** 連上後給自己 WRITE_SECURE_SETTINGS。成功（或本來就有）回 true */
    fun grantSecure(): Boolean {
        if (secureGranted()) return true
        val r = run("pm grant ${app.packageName} android.permission.WRITE_SECURE_SETTINGS", 20)
        return r.first == 0 && secureGranted()
    }

    /** 跑一條 shell，回 (rc, 輸出)。連線斷了會重連一次。 */
    fun run(cmd: String, timeoutSec: Int = 30): Pair<Int, String> {
        if (!paired()) return Pair(-2, "not_paired")
        var last = Pair(-4, "no_adb")
        for (attempt in 0..1) {
            if (!connected() && !ensure()) return Pair(-4, "no_adb:$lastError")
            try { return exec(cmd, timeoutSec) }
            catch (e: IOException) { last = Pair(-3, "io:" + (e.message ?: "").take(60)); disconnect() }
            catch (e: Throwable) { return Pair(-3, e.javaClass.simpleName + ":" + (e.message ?: "").take(100)) }
        }
        return last
    }

    private fun exec(cmd: String, timeoutSec: Int): Pair<Int, String> {
        val st = manager().openStream("shell:" + ShellCmd.withRc(cmd))
        val bo = ByteArrayOutputStream()
        val t = Thread {
            try { val b = ByteArray(4096); while (bo.size() < 16384) { val n = st.read(b, 0, b.size); if (n < 0) break; synchronized(bo) { bo.write(b, 0, n) } } }
            catch (_: IOException) {}
        }
        t.isDaemon = true; t.start(); t.join(timeoutSec * 1000L)
        val done = !t.isAlive
        try { st.close() } catch (_: Throwable) {}
        if (!done) return Pair(-1, synchronized(bo) { bo.toString("UTF-8") }.take(200))
        t.join(500)
        return ShellCmd.parseRc(synchronized(bo) { bo.toString("UTF-8") })
    }

    /** 把 bytes 寫到手機的 path（base64 分段，先 .new 再改名）。成功回 (0, "")，失敗回第一條失敗指令的 (rc, 原因)。 */
    fun writeFile(path: String, data: ByteArray, mode: String = "644"): Pair<Int, String> {
        for (c in ShellCmd.writeFileCmds(path, data, mode)) {
            val r = run(c, 20)
            if (r.first != 0) return Pair(r.first, "write_fail ${r.second.trim().take(60)}")
        }
        return Pair(0, "")
    }
}
