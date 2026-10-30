package app.splitaudio.adb

import app.splitaudio.Backend

/** Play 版的內建 ADB 後端（取代 Shizuku）。granted＝已配對（一次性設定做完）；alive＝目前連著。
 *  斷線時 [Adb.run] 會自己重連一次（必要時先打開無線偵錯）。 */
object AdbBackend : Backend {
    override val name = "ADB"
    override fun alive(): Boolean = Adb.connected()
    override fun granted(): Boolean = Adb.paired()

    private const val STDIN_TMP = "/data/local/tmp/.splitaudio_stdin"
    private val stdinLock = Any()

    /** 純字串（可單元測試）：讓 cmd 從 tmp 檔讀 stdin，跑完刪掉 tmp，回傳碼維持 cmd 的（用子 shell 結束，才不會把外層 shell 結束掉而丟了 __rc 標記）。 */
    fun wrapStdin(cmd: String, tmp: String): String = "( $cmd ) < $tmp; r=\$?; rm -f $tmp; (exit \$r)"

    override fun run(cmd: String, timeoutSec: Int, stdin: ByteArray?): Pair<Int, String> {
        if (stdin == null) return Adb.run(cmd, timeoutSec)
        // ADB 的 shell 串流不能當 stdin 管道用 → 先把資料寫進手機上的暫存檔，再讓指令從檔案讀
        synchronized(stdinLock) {
            val w = Adb.writeFile(STDIN_TMP, stdin)
            if (w.first != 0) return w
            return Adb.run(wrapStdin(cmd, STDIN_TMP), timeoutSec)
        }
    }

    /** 寫檔直接走 base64 分段（不必先落暫存檔再 cat，少一倍指令數） */
    override fun writeFile(path: String, data: ByteArray, mode: String, timeoutSec: Int): Pair<Int, String> = Adb.writeFile(path, data, mode)
}
