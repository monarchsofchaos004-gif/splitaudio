package app.splitaudio

import android.content.Context

/** 簡單模式的「音樂 App」清單（在 MusicActivity 多選）：這些 App 的聲音留在藍牙，其他 App 走手機喇叭。
 *  存 SharedPreferences "music"；沒選過（或內容全無效）＝預設清單（YT Music＋Morphe 版＋常見音樂 App，沒裝的自然對不到 uid）。
 *  路由程式讀不到 App 的設定 → 啟動前經 [Backends] 寫成 /data/local/tmp/approute.music（一行一個套件名，見 [text]），approute.sh start 再換成 uid。
 *  套件名要合法（[Outputs.PKG]）才會被收下——它會被寫進給 shell 讀的檔。 */
object Music {
    /** 預設音樂 App（要跟 approute.sh 的 DEF_MUSIC 一致，有單元測試對） */
    val DEFAULT: Set<String> = linkedSetOf(
        "com.google.android.apps.youtube.music", "app.morphe.android.apps.youtube.music",
        "com.spotify.music", "com.apple.android.music", "deezer.android.app", "com.aspiro.tidal",
        "com.amazon.mp3", "com.soundcloud.android", "com.pandora.android", "com.maxmpz.audioplayer")
    private const val KEY = "pkgs"
    private fun sp(c: Context) = c.getSharedPreferences("music", Context.MODE_PRIVATE)

    /** 目前生效的音樂 App（套件名；永遠不是空的） */
    fun set(c: Context): Set<String> = effective(sp(c).getStringSet(KEY, null))

    fun save(c: Context, pkgs: Set<String>) {
        sp(c).edit().putStringSet(KEY, HashSet(valid(pkgs))).apply()
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

    /** 純邏輯：沒存過／存的全無效＝預設 */
    fun effective(raw: Collection<String?>?): Set<String> = valid(raw).ifEmpty { DEFAULT }

    /** 純邏輯：approute.music 的內容，一行一個套件名 */
    fun text(s: Collection<String>): String = s.joinToString("") { it + "\n" }

    /** 純邏輯：勾選／取消勾選一個套件。取消到一個都不剩時不動（至少留一個）。 */
    fun toggled(cur: Set<String>, pkg: String): Set<String> {
        val next = LinkedHashSet(cur)
        if (!next.remove(pkg)) next.add(pkg)
        return if (next.isEmpty()) cur else next
    }
}
