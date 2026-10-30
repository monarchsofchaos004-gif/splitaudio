package app.splitaudio.adb

import java.util.Base64

/** 純邏輯（不碰 Android）：產生「經 ADB shell 寫檔／取回傳碼」的指令字串。有 JVM 單元測試。 */
object ShellCmd {
    /** 單條 shell 指令裡 base64 片段的長度（ADB 單包上限至少 4096，留餘裕） */
    const val CHUNK = 3000
    private val SAFE_PATH = Regex("^/[A-Za-z0-9_./-]+$")

    /** 單引號包起來（內含單引號要跳脫） */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 指令結尾補上回傳碼標記，讓呼叫端能取回 rc（legacy shell 服務沒有 exit code） */
    fun withRc(cmd: String): String = "$cmd; echo \"__rc=\$?\""

    /** 從輸出取出 rc 與去掉標記後的內文；找不到標記＝-5（指令被中斷或輸出被截） */
    fun parseRc(out: String): Pair<Int, String> {
        val i = out.lastIndexOf("__rc=")
        if (i < 0) return Pair(-5, out)
        val rc = out.substring(i + 5).trim().takeWhile { it.isDigit() }.toIntOrNull() ?: return Pair(-5, out)
        return Pair(rc, out.substring(0, i))
    }

    /** 把 data 寫到 path：先分段 base64 追加到 path.b64，最後解碼成 path.new、chmod、改名（正在跑的程式還拿著舊檔，不能原地覆蓋）。
     *  回傳要依序執行的 shell 指令；每條都獨立可跑，任何一條 rc≠0 就整體失敗。 */
    fun writeFileCmds(path: String, data: ByteArray, mode: String = "644", chunk: Int = CHUNK): List<String> {
        require(SAFE_PATH.matches(path)) { "unsafe path" }
        require(mode.matches(Regex("^[0-7]{3}$")))
        val b64 = Base64.getEncoder().encodeToString(data)
        val cmds = ArrayList<String>()
        cmds += ": > $path.b64"
        var i = 0
        while (i < b64.length) {
            val part = b64.substring(i, minOf(b64.length, i + chunk))
            cmds += "printf %s '$part' >> $path.b64"     // base64 字元（含 + / =）都不含單引號，可直接放進單引號
            i += chunk
        }
        cmds += "base64 -d $path.b64 > $path.new && chmod $mode $path.new && mv -f $path.new $path; r=\$?; rm -f $path.b64 $path.new; [ \$r = 0 ]"
        return cmds
    }
}
