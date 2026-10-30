package app.splitaudio

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

/** 路由本體＝AppRoute（app_process 小程式，shell 身分註冊系統路由規則：音樂 App 以外的 App 走手機喇叭）。
 *  本 App 只負責：把 assets 裡的 approute.dex／approute.sh 經 [Backends]（Shizuku 或 Play 版內建 ADB）寫到 /data/local/tmp 並啟動，之後用本機指令口 127.0.0.1:47821 溝通。
 *  指令口只綁本機，但任何有 INTERNET 權限的 App 都連得上本機埠，所以每個連線要過兩關：第一行送 "auth <token>"（token＝本 App 自己產生的 32 字元隨機碼，只存在本 App 的私有儲存空間，
 *  啟動路由時經環境變數交給它），而且路由程式會查 /proc/net/tcp 確認對方的 uid 是本 App（SPLITAUDIO_UID）、shell 或 root；不符一律回 "denied"。資料不出手機。
 *  AppRoute 用 setsid 脫離，本 App 被系統殺掉路由照跑；藍牙斷 2 分／音樂 10 分沒播它自己結束。
 *  簡單模式：音樂 App 清單先寫成 approute.music（[Music]），再 approute.sh start。
 *  自訂模式（0.3）：先把每個 App 的輸出寫成 approute.pkgmap，再用 approute.sh startmap 起「AppRoute map」。 */
object Route {
    /** 路由程式的本機指令口（只綁 127.0.0.1）；要跟 route/AppRoute.java 的 VOL_PORT 一樣（有單元測試對） */
    const val PORT = 47821
    private const val HOST = "127.0.0.1"
    /** 路由程式對沒過關的連線（token 不對或 uid 不是本 App）的回答（例：重裝 App 後 token／uid 都變了舊路由還在跑，或同時裝了另一個 SplitAudio 版本） */
    const val DENIED = "denied"
    private const val D = "/data/local/tmp"
    @Volatile var lastStart = "none"
    /** 最近一次 [query] 沒拿到回答的原因（診斷用：[DENIED]＝路由在跑但不認我們；其他＝連不上／逾時…）；拿到回答就清空 */
    @Volatile var lastAskError = ""
    @Volatile private var tokenCache: String? = null
    private val tokenLock = Any()   // not Route's own monitor: start() holds that for up to a minute, and every ask() needs the token

    // ---- token ----

    /** 純邏輯（可單元測試）：由 [rnd] 產生 token＝32 個小寫十六進位字元（128 位元；預設用 SecureRandom） */
    fun newToken(rnd: java.util.Random = SecureRandom()): String {
        val b = ByteArray(16); rnd.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** 純邏輯：token 長得對不對（會被放進給 shell 的指令列，所以只收我們自己產生的格式：32 個小寫十六進位字元） */
    fun isValidToken(t: String?): Boolean = t != null && t.length == 32 && t.all { it in '0'..'9' || it in 'a'..'f' }

    /** 本 App 的指令口 token：第一次需要時產生，存在私有 prefs "route"／"token"（allowBackup=false，不會被備份帶走）。清除 App 資料＝換新 token。 */
    fun token(ctx: Context): String {
        tokenCache?.let { return it }
        synchronized(tokenLock) {
            tokenCache?.let { return it }
            val sp = ctx.applicationContext.getSharedPreferences("route", Context.MODE_PRIVATE)
            var t = sp.getString("token", null)
            if (!isValidToken(t)) { t = newToken(); sp.edit().putString("token", t).commit() }
            tokenCache = t
            return t!!
        }
    }

    /** 純字串（可單元測試）：經 shell 跑 approute.sh。前面帶本 App 的 uid 和 token：路由程式只服務帶這個 token、而且來自這個 uid（和 shell、root）的連線。 */
    fun cmd(uid: Int, token: String, sub: String): String {
        require(isValidToken(token)) { "bad token" }   // 會進 shell 指令列：只放自己產生的固定格式
        return "SPLITAUDIO_UID=$uid SPLITAUDIO_TOKEN=$token sh $D/approute.sh $sub"
    }

    /** 跑 approute.sh 的指令字串（Shizuku／ADB 兩個後端都是 sh -c，前綴的環境變數都有效） */
    fun sh(ctx: Context, sub: String) = cmd(android.os.Process.myUid(), token(ctx), sub)

    // ---- 問路由程式 ----

    /** 問一句的結果：[reply] 有值＝路由程式回答了；null＝沒拿到，[error] 是原因（[DENIED] 或連線錯誤的文字） */
    class Answer(val reply: String?, val error: String = "")

    /** 純邏輯（可單元測試）：路由程式回的那一行 → [Answer]。沒有回覆（對方直接關線）和 "denied" 都算沒拿到回答。 */
    fun answerOf(raw: String?): Answer = when (raw) {
        null -> Answer(null, "no reply")
        DENIED -> Answer(null, DENIED)
        else -> Answer(raw)
    }

    /** 連到指令口問一句（純 IO、不碰 Context，所以單元測試可以對真的 AppRoute 跑）：先送 "auth <token>" 再送指令，讀一行回覆。連不上／逾時會丟例外。 */
    fun exchange(token: String, cmd: String, timeoutMs: Int = 400, port: Int = PORT): String? {
        require(!cmd.contains('\n') && !cmd.contains('\r')) { "one line only" }
        return Socket().use { s ->
            s.connect(InetSocketAddress(HOST, port), timeoutMs); s.soTimeout = timeoutMs * 3
            s.getOutputStream().write(("auth $token\n$cmd\n").toByteArray()); s.getOutputStream().flush()
            BufferedReader(InputStreamReader(s.getInputStream())).readLine()?.trim()
        }
    }

    /** 問路由程式一句（state／mode／vol up|down 套件），結果帶原因。要在背景執行緒叫（網路 IO）。 */
    fun query(ctx: Context, cmd: String, timeoutMs: Int = 400): Answer {
        val a = try { answerOf(exchange(token(ctx), cmd, timeoutMs)) }
                catch (e: Exception) { Answer(null, (e.message ?: e.javaClass.simpleName).take(60)) }
        lastAskError = a.error
        return a
    }

    /** 問路由程式一句。沒拿到回答（連不上、逾時、denied）回 null，原因存在 [lastAskError]。 */
    fun ask(ctx: Context, cmd: String, timeoutMs: Int = 400): String? = query(ctx, cmd, timeoutMs).reply

    /** 純邏輯（可單元測試）：對 "state" 的回答是 on／off＝這個路由是我們的、在跑。沒回答、denied（別的 token／uid 起的）、
     *  或別的字（例如 0.4 以前沒有 token 的舊路由把 auth 那行當指令回 "err"）都不算——後兩種要重新部署／起新的。 */
    fun isAlive(reply: String?) = reply == "on" || reply == "off"

    /** 純邏輯：對 "state" 的回答不是 on／off 時，要寫進「上次啟動」的原因；正常（路由在跑）回空字串 */
    fun controlProblem(a: Answer): String = when {
        isAlive(a.reply) -> ""
        a.reply != null -> " | unexpected answer on the control port: ${a.reply.take(30)}"
        a.error == DENIED -> " | control port: denied (the running router was started with another token or for another uid; tap Stop split to end it)"
        else -> " | no answer on the control port: ${a.error}"
    }

    fun running(ctx: Context): Boolean = isAlive(ask(ctx, "state"))
    fun ruleOn(ctx: Context): Boolean = ask(ctx, "state") == "on"
    /** 正在跑的路由是哪個模式："map"（自訂）／"legacy"（簡單）；沒拿到回答是 null */
    fun mode(ctx: Context): String? = ask(ctx, "mode")?.let { if (it == "map") "map" else "legacy" }

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
        Backends.run(sh(ctx, "stop"), 15)
        return null
    }

    /** 部署＋啟動（要在背景執行緒叫）。已在跑就不動。 */
    @Synchronized fun start(ctx: Context): String {
        val st = query(ctx, "state")
        if (isAlive(st.reply)) return "already".also { lastStart = it }
        deploy(ctx)?.let { return it.also { lastStart = it } }
        // st.error == DENIED：有路由在跑但它是別的 token／uid 起的（重裝前的舊路由，或同時裝了另一個 SplitAudio 版本）。不去殺它——兩個版本互殺會一直重啟、聲音一直閃；
        // 舊路由藍牙斷 2 分／音樂停 10 分會自己結束，或按「停止分流」（stop 對任何 uid 起的都有效）。下面 approute.sh start 會回 already，結果文字會註明 denied。
        // st.reply 是別的字（0.4 以前沒有 token 的舊路由會把 auth 那行當指令回 err）：上面的 deploy 內容有變會把它停掉，再起新的。
        val custom = Outputs.custom(ctx)
        if (custom) {
            if (!Outputs.hasAny(ctx)) return "no_apps".also { lastStart = it }
            val w = Backends.writeFile("$D/approute.pkgmap", Outputs.mapText(ctx).toByteArray())
            if (w.first != 0) return "map_fail:${w.first} ${w.second.take(60)}".also { lastStart = it }
        } else {
            val w = Backends.writeFile("$D/approute.music", Music.text(Music.set(ctx)).toByteArray())
            if (w.first != 0) return "music_fail:${w.first} ${w.second.take(60)}".also { lastStart = it }
        }
        val r = Backends.run(sh(ctx, if (custom) "startmap" else "start"), 45)
        var res = "start rc=${r.first} " + r.second.trim().lines().lastOrNull().orEmpty().take(80)
        if (hasPid(r.second)) res += controlProblem(query(ctx, "state"))   // 路由程式起來了卻問不到它＝指令口有問題，把原因帶進結果（主畫面「上次啟動」會顯示）
        return res.also { lastStart = it }
    }

    /** 換模式／改了 App 輸出／改了音樂 App（背景執行緒叫）：停掉正在跑的路由，監看服務照新設定、有聲音時再起。 */
    fun apply(ctx: Context) { stop(ctx); MonitorService.ensure(ctx) }

    fun stop(ctx: Context): String = Backends.run(sh(ctx, "stop"), 15).second.trim()

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
        return Backends.run(sh(ctx, "multifocus " + (if (on) "on" else "off")), 30).second.trim().lines().lastOrNull().orEmpty()
    }

    /** 「按麥克風時不暫停音樂」（語音輸入／助理搶走聲音時，路由程式立刻替音樂按播放）：旗標檔 approute.nomickeep 在＝關，不在＝開（預設開）。
     *  旗標檔 App 讀不到，經 [Backends] 讀；路由程式每次要用都重查這個檔，所以切換不用重啟路由。回 "on"／"off"／null（讀不到） */
    fun micKeepState(): String? {
        val r = Backends.run("[ -e $D/approute.nomickeep ] && echo off || echo on", 10)
        return micKeepOf(r.first, r.second)
    }

    /** 純邏輯（可單元測試）：[micKeepState] 那條指令的結果（rc＝後端回的代碼，負的＝後端不能用／逾時／例外）→ "on"／"off"／null。看輸出的最後一行。 */
    fun micKeepOf(rc: Int, out: String): String? {
        if (rc < 0) return null
        val last = out.trim().lines().lastOrNull()?.trim()
        return if (last == "on" || last == "off") last else null
    }

    /** 開／關「按麥克風時不暫停音樂」（要在背景執行緒叫）。回 approute.sh 印的最後一行（"mickeep=on"／"mickeep=off"），部署失敗回失敗原因。 */
    @Synchronized fun setMicKeep(ctx: Context, on: Boolean): String {
        deploy(ctx)?.let { return it }
        return Backends.run(sh(ctx, "mickeep " + (if (on) "on" else "off")), 30).second.trim().lines().lastOrNull().orEmpty()
    }
}
