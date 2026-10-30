package app.splitaudio

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

/** 路由本體＝AppRoute（app_process 小程式，shell 身分註冊系統路由規則：音樂 App 以外的 App 走手機喇叭）。
 *  本 App 只負責：把 assets 裡的 approute.dex／approute.sh 經 [Backends]（Shizuku 或 Play 版內建 ADB）寫到 /data/local/tmp 並啟動，之後用本機指令口（abstract local socket "app.splitaudio.route"）溝通。
 *  指令口不走網路（App 不需要 INTERNET 權限）；路由程式會檢查連進來的 uid，只有本 App（啟動指令帶 SPLITAUDIO_UID）、shell、root 問得到答案。
 *  AppRoute 用 setsid 脫離，本 App 被系統殺掉路由照跑；藍牙斷 2 分／音樂 10 分沒播它自己結束。
 *  簡單模式：音樂 App 清單先寫成 approute.music（[Music]），再 approute.sh start。
 *  自訂模式（0.3）：先把每個 App 的輸出寫成 approute.pkgmap，再用 approute.sh startmap 起「AppRoute map」。 */
object Route {
    const val SOCK = "app.splitaudio.route"
    /** 路由程式對不是本 App 的 uid 的回答（例：重裝 App 後 uid 變了舊路由還在跑，或同時裝了另一個 SplitAudio 版本） */
    const val DENIED = "denied"
    private const val D = "/data/local/tmp"
    @Volatile var lastStart = "none"
    /** 最近一次 [ask] 失敗的原因（診斷用：例如指令口被系統安全策略擋下會是 Permission denied） */
    @Volatile var lastAskError = ""

    /** 純字串（可單元測試）：經 shell 跑 approute.sh。前面帶本 App 的 uid，路由程式只回答這個 uid（和 shell、root）。 */
    fun cmd(uid: Int, sub: String) = "SPLITAUDIO_UID=$uid sh $D/approute.sh $sub"

    /** 跑 approute.sh 的指令字串（Shizuku／ADB 兩個後端都是 sh -c，前綴的環境變數都有效） */
    fun sh(sub: String) = cmd(android.os.Process.myUid(), sub)

    /** 問路由程式一句（state／mode／vol up|down 套件）。連不上回 null（原因存在 [lastAskError]）。 */
    fun ask(cmd: String, timeoutMs: Int = 400): String? = try {
        val s = LocalSocket()
        try {
            s.connect(LocalSocketAddress(SOCK, LocalSocketAddress.Namespace.ABSTRACT))
            s.soTimeout = timeoutMs * 3
            s.outputStream.write((cmd + "\n").toByteArray()); s.outputStream.flush()
            BufferedReader(InputStreamReader(s.inputStream)).readLine()?.trim()
        } finally { try { s.close() } catch (_: Exception) {} }
    } catch (e: Exception) { lastAskError = (e.message ?: e.javaClass.simpleName).take(60); null }

    /** 純邏輯（可單元測試）：路由程式有回答、而且不是「denied」＝這個路由是我們的、在跑 */
    fun isAlive(reply: String?) = reply != null && reply != DENIED

    fun running(): Boolean = isAlive(ask("state"))
    fun ruleOn(): Boolean = ask("state") == "on"
    /** 正在跑的路由是哪個模式："map"（自訂）／"legacy"（簡單；0.2 的舊路由不認得 mode 也當 legacy） */
    fun mode(): String? = ask("mode")?.takeIf { it != DENIED }?.let { if (it == "map") "map" else "legacy" }

    /** 純邏輯（可單元測試）：部署內容的指紋（sha256 十六進位）。檔名也算進去，兩個檔內容對調會不一樣。 */
    fun fingerprint(files: List<Pair<String, ByteArray>>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for ((name, data) in files) {
            md.update(name.toByteArray()); md.update(0.toByte())
            md.update(data.size.toString().toByteArray()); md.update(0.toByte())
            md.update(data)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 純邏輯：手機上記的指紋跟這版 App 的一樣＝不用重新部署 */
    fun sameVersion(remote: String?, local: String): Boolean = remote != null && remote.trim() == local

    /** 純邏輯：approute.sh start／startmap 的輸出有帶 pid（路由程式起來了）嗎 */
    fun hasPid(out: String): Boolean = Regex("(started|already) pid=\\d+").containsMatchIn(out)

    /** 把 assets 寫到 /data/local/tmp（先寫 .new 再改名：正在跑的 AppRoute 還拿著舊檔，不能原地覆蓋）。成功回 null。
     *  先比對手機上 approute.ver 記的指紋（dex＋sh 的 sha256），一樣就略過；兩個檔任一個不見了也重寫。
     *  內容有變＝App 更新了：寫完新檔把還在跑的舊路由停掉（舊版的指令口／規則跟新版不同，新版問不到它），之後由監看服務照新檔重起。 */
    @Synchronized fun deploy(ctx: Context): String? {
        val files = listOf("approute.dex", "approute.sh").map { n -> n to ctx.assets.open(n).use { it.readBytes() } }
        val ver = fingerprint(files)
        val have = Backends.run("[ -s $D/approute.dex ] && [ -s $D/approute.sh ] && cat $D/approute.ver", 10)
        if (have.first == 0 && sameVersion(have.second, ver)) return null
        for ((n, data) in files) {
            val a = Backends.writeFile("$D/$n", data)
            if (a.first != 0) return "deploy_fail:${a.first} ${a.second.take(60)}"
        }
        val v = Backends.writeFile("$D/approute.ver", ver.toByteArray())
        if (v.first != 0) return "deploy_fail:${v.first} ${v.second.take(60)}"
        Backends.run(sh("stop"), 15)
        return null
    }

    /** 部署＋啟動（要在背景執行緒叫）。已在跑就不動。 */
    @Synchronized fun start(ctx: Context): String {
        val st = ask("state")
        if (isAlive(st)) return "already".also { lastStart = it }
        deploy(ctx)?.let { return it.also { lastStart = it } }
        // st == DENIED：有路由在跑但它是別的 uid 起的（重裝前的舊路由，或同時裝了另一個 SplitAudio 版本）。不去殺它——兩個版本互殺會一直重啟、聲音一直閃；
        // 舊路由藍牙斷 2 分／音樂停 10 分會自己結束，或按「停止分流」（stop 對任何 uid 起的都有效）。下面 approute.sh start 會回 already，結果文字會註明 denied。
        val custom = Outputs.custom(ctx)
        if (custom) {
            if (!Outputs.hasAny(ctx)) return "no_apps".also { lastStart = it }
            val w = Backends.writeFile("$D/approute.pkgmap", Outputs.mapText(ctx).toByteArray())
            if (w.first != 0) return "map_fail:${w.first} ${w.second.take(60)}".also { lastStart = it }
        } else {
            val w = Backends.writeFile("$D/approute.music", Music.text(Music.set(ctx)).toByteArray())
            if (w.first != 0) return "music_fail:${w.first} ${w.second.take(60)}".also { lastStart = it }
        }
        val r = Backends.run(sh(if (custom) "startmap" else "start"), 45)
        var res = "start rc=${r.first} " + r.second.trim().lines().lastOrNull().orEmpty().take(80)
        if (hasPid(r.second)) {   // 路由程式起來了卻問不到它＝指令口有問題，把原因帶進結果（主畫面「上次啟動」會顯示）
            val a = ask("state")
            if (a == null) res += " | no answer on the control socket: $lastAskError"
            else if (a == DENIED) res += " | control socket: denied (the running router was started for another uid; tap Stop split to end it)"
        }
        return res.also { lastStart = it }
    }

    /** 換模式／改了 App 輸出／改了音樂 App（背景執行緒叫）：停掉正在跑的路由，監看服務照新設定、有聲音時再起。 */
    fun apply(ctx: Context) { stop(); MonitorService.ensure(ctx) }

    fun stop(): String = Backends.run(sh("stop"), 15).second.trim()

    /** 「開影片不打斷音樂」（Android 內建 multi audio focus）：這個系統設定 App 讀不到，經 [Backends] 讀。
     *  回 "on"／"off"／"off_user"（用戶按過關＝分流不會自動打開）／null（讀不到） */
    fun multiFocusState(): String? {
        val r = Backends.run("settings get system multi_audio_focus_enabled; [ -e $D/approute.nomf ] && echo nomf", 10)
        if (r.first < 0 || r.second.isBlank()) return null
        return if (r.second.trim().startsWith("1")) "on" else if (r.second.contains("nomf")) "off_user" else "off"
    }

    /** 手動開／關（要在背景執行緒叫）。開＝用戶同意（之後分流會自動打開）；關＝用戶不要（分流不會再自動打開）。 */
    @Synchronized fun setMultiFocus(ctx: Context, on: Boolean): String {
        deploy(ctx)?.let { return it }
        return Backends.run(sh("multifocus " + (if (on) "on" else "off")), 30).second.trim().lines().lastOrNull().orEmpty()
    }
}
