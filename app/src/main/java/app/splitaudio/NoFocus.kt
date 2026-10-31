package app.splitaudio

import android.content.Context

/** 自訂模式的「不要暫停別的 App」（實驗功能）：被勾的 App 不能搶音訊焦點（appops TAKE_AUDIO_FOCUS ignore），所以它一出聲，音樂 App 不會被暫停。
 *  存 SharedPreferences "nofocus"；路由程式讀不到 App 的設定 → 啟動前經 [Backends] 寫成 /data/local/tmp/approute.nofocus（一行一個套件名，見 [text]），
 *  approute.sh startmap 套用、stop／簡單模式還原。套件名要合法（[Outputs.PKG]）才會被收下——它會被寫進給 shell 讀的檔。 */
object NoFocus {
    private const val KEY = "pkgs"
    private fun sp(c: Context) = c.getSharedPreferences("nofocus", Context.MODE_PRIVATE)

    fun set(c: Context): Set<String> = valid(sp(c).getStringSet(KEY, null))

    fun has(c: Context, pkg: String) = pkg in set(c)

    /** 開／關一個 App，回傳新的清單 */
    fun toggle(c: Context, pkg: String): Set<String> {
        val next = toggled(set(c), pkg)
        sp(c).edit().putStringSet(KEY, HashSet(next)).apply()
        return next
    }

    /** 純邏輯（可單元測試）：去頭尾空白、套件名要合法、去重（保留順序） */
    fun valid(raw: Collection<String?>?): Set<String> {
        val out = LinkedHashSet<String>()
        for (r in raw.orEmpty()) {
            val p = r?.trim() ?: continue
            if (Outputs.PKG.matches(p)) out.add(p)
        }
        return out
    }

    /** 純邏輯：approute.nofocus 的內容，一行一個套件名（沒有＝空檔，路由那邊會把之前套用的全還原） */
    fun text(s: Collection<String>): String = s.joinToString("") { it + "\n" }

    /** 純邏輯：勾選／取消勾選一個套件（可以全取消）；不合法的套件名不收 */
    fun toggled(cur: Set<String>, pkg: String): Set<String> {
        val next = LinkedHashSet(cur)
        if (!next.remove(pkg) && Outputs.PKG.matches(pkg)) next.add(pkg)
        return next
    }
}
