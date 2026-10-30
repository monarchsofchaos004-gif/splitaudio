package app.splitaudio

import android.content.Context
import java.util.Locale

/** 每個 App 分開調音量（0.4）：設定存在本機（套件→0～99，100＝不管它，不存），寫成 /data/local/tmp/approute.volpkg
 *  交給「AppRoute mixer」（shell 身分對每個播放器呼叫 PlayerProxy.setVolume；0＝靜音）。
 *  有任何一個 App 不是 100 就一直跑；全部回到 100 ＝關掉（它會先把音量還原）。 */
object Mixer {
    private fun sp(c: Context) = c.getSharedPreferences("mixer", Context.MODE_PRIVATE)

    fun get(c: Context, pkg: String): Int = sp(c).getInt(pkg, 100)
    /** 靜音前的音量（解除靜音時回到這裡） */
    fun last(c: Context, pkg: String): Int = sp(c).getInt("$pkg#last", 100)

    fun set(c: Context, pkg: String, v: Int) {
        val e = sp(c).edit()
        if (v >= 100) e.remove(pkg) else e.putInt(pkg, v.coerceIn(0, 99))
        if (v > 0) e.putInt("$pkg#last", v.coerceIn(1, 100))
        e.apply()
    }

    fun all(c: Context): Map<String, Int> = valid(sp(c).all)

    /** 純邏輯（可單元測試）：prefs 全部內容裡的「套件名＝音量」（排除 "套件#last" 這種輔助鍵、非整數的值） */
    fun valid(raw: Map<String, *>): Map<String, Int> =
        raw.filter { !it.key.contains('#') && it.value is Int }.mapValues { it.value as Int }

    fun hasAny(c: Context) = all(c).isNotEmpty()

    /** approute.volpkg 內容：一個 App 一行「套件=0.35」（0＝靜音） */
    fun text(m: Map<String, Int>) =
        m.entries.joinToString("") { "${it.key}=${String.format(Locale.US, "%.2f", it.value / 100f)}\n" }

    /** 背景執行緒叫：部署＋寫設定＋起／更新 mixer。回最後一行結果。 */
    @Synchronized fun apply(c: Context): String {
        Route.deploy(c)?.let { return it }
        if (!hasAny(c)) return Backends.run("sh /data/local/tmp/approute.sh mixstop", 15).second.trim()
        val w = Backends.writeFile("/data/local/tmp/approute.volpkg", text(all(c)).toByteArray())
        if (w.first != 0) return "vol_fail:${w.first} ${w.second.take(60)}"
        return Backends.run("sh /data/local/tmp/approute.sh mixer", 30).second.trim().lines().lastOrNull().orEmpty()
    }

    /** 有設定但 mixer 沒在跑（例：手機重開過）→ 起來。背景執行緒叫。 */
    fun ensure(c: Context) {
        if (!hasAny(c) || Backends.current() == null) return
        // [A] 寫法:這條 sh 自己的命令列也含這串字,不加方括號 pgrep 會找到自己 → 永遠以為在跑
        if (Backends.run("pgrep -f '[A]ppRoute mixer' >/dev/null && echo y", 10).second.trim() != "y") apply(c)
    }

    /** 現在正在出聲的 App 的 uid；讀不到回空集合。背景執行緒叫。 */
    fun playingUids(): Set<Int> {
        val r = Backends.run("sh /data/local/tmp/approute.sh players", 15)
        if (r.first != 0) return emptySet()
        return r.second.lines().mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
}
