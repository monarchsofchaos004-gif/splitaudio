package app.splitaudio

/** App 怎麼拿到「shell 身分」（路由小程式要 shell 才有的系統權限）。兩種實作：
 *  [ShizukuBackend]（兩個版本都有）、AdbBackend（Play 版內建 ADB 無線偵錯，見 src/play）。
 *  run() 回 (rc, 輸出)；rc：-2 這個後端還不能用（沒授權／沒配對）、-1 逾時、-3 例外、-4 暫時連不上（ADB 斷線）。 */
interface Backend {
    /** 顯示用名稱（"Shizuku"／"ADB"） */
    val name: String
    /** 後端本身有沒有在跑／連著（Shizuku＝服務在；ADB＝連線中） */
    fun alive(): Boolean
    /** 這個後端現在能不能用（已授權／已配對） */
    fun granted(): Boolean
    fun run(cmd: String, timeoutSec: Int = 30, stdin: ByteArray? = null): Pair<Int, String>

    /** 把 data 寫到手機的 path（先寫 path.new 再改名：正在跑的程式還拿著舊檔，不能原地覆蓋）。回 (rc, 輸出)，rc=0 成功。
     *  預設經 stdin 寫；沒有 stdin 管道的後端（ADB）要覆寫。 */
    fun writeFile(path: String, data: ByteArray, mode: String = "644", timeoutSec: Int = 15): Pair<Int, String> =
        run("cat > $path.new && chmod $mode $path.new && mv -f $path.new $path", timeoutSec, data)
}

/** 後端選擇：[Dist.backends] 的順序＝優先序，第一個 granted 的就用（Play 版＝Shizuku 有就用，沒有走內建 ADB）。 */
object Backends {
    /** 純邏輯（可單元測試）：第一個 granted 的；granted() 丟例外當作不能用。 */
    fun pick(list: List<Backend>): Backend? = list.firstOrNull { try { it.granted() } catch (_: Throwable) { false } }

    fun current(): Backend? = pick(Dist.backends())

    /** 用目前的後端跑一條 shell；沒有可用的後端回 (-2, "no_backend") */
    fun run(cmd: String, timeoutSec: Int = 30, stdin: ByteArray? = null): Pair<Int, String> =
        current()?.run(cmd, timeoutSec, stdin) ?: Pair(-2, "no_backend")

    fun writeFile(path: String, data: ByteArray, mode: String = "644", timeoutSec: Int = 15): Pair<Int, String> =
        current()?.writeFile(path, data, mode, timeoutSec) ?: Pair(-2, "no_backend")
}

/** Play 版的一次性設定區塊（內建 ADB 的 4 步引導）。fdroid 版沒有（[Dist.setupSection] 回 null，主畫面用 Shizuku 那列）。 */
interface SetupSection {
    /** 背景執行緒叫：讀系統狀態，回「要在 UI 執行緒執行的畫面更新」 */
    fun poll(): () -> Unit
    /** 畫面回到前景；狀態有變時呼叫 [refresh] */
    fun onResume(refresh: () -> Unit)
    fun onPause()
    /** 主畫面最底下要多加的東西（例如授權頁按鈕） */
    fun addFooter(parent: android.widget.LinearLayout)
}
