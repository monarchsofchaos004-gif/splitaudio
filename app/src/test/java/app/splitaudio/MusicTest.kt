package app.splitaudio

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 簡單模式的音樂 App 清單：預設、套件名驗證、approute.music 的檔案文字、勾選規則（至少留一個）、跟 approute.sh 內建預設一致。 */
class MusicTest {
    @Test fun defaults_areYtMusicFirstThenCommonPlayers() {
        assertEquals(listOf("com.google.android.apps.youtube.music", "app.morphe.android.apps.youtube.music"), Music.DEFAULT.toList().take(2))
        assertTrue(Music.DEFAULT.containsAll(listOf("com.spotify.music", "com.apple.android.music", "deezer.android.app", "com.aspiro.tidal",
            "com.amazon.mp3", "com.soundcloud.android", "com.pandora.android", "com.maxmpz.audioplayer")))
        assertEquals(10, Music.DEFAULT.size)
        assertEquals(Music.DEFAULT, Music.valid(Music.DEFAULT))   // every default passes the package-name check
    }

    @Test fun effective_nothingSavedOrNothingValid_isTheDefault() {
        assertEquals(Music.DEFAULT, Music.effective(null))
        assertEquals(Music.DEFAULT, Music.effective(emptyList()))
        assertEquals(Music.DEFAULT, Music.effective(listOf("bad pkg", "a;rm -rf /", "", "  ", null)))
    }

    @Test fun effective_keepsAValidChoice() =
        assertEquals(setOf("com.spotify.music"), Music.effective(listOf("com.spotify.music")))

    @Test fun valid_trimsDropsAnythingThatCouldReachTheShellAndDedups() {
        val raw = listOf(" com.a.one ", "com.a.one", "com.b.two", "bad name", "x;y", "a\nb", "\$(id)", "`id`", "a|b", "中文.app", null, "")
        assertEquals(listOf("com.a.one", "com.b.two"), Music.valid(raw).toList())   // 順序保留
    }

    @Test fun text_onePackagePerLine() {
        assertEquals("com.a\ncom.b\n", Music.text(listOf("com.a", "com.b")))
        assertEquals("", Music.text(emptyList()))
    }

    @Test fun toggled_addsAndRemoves() {
        val cur = linkedSetOf("com.a", "com.b")
        assertEquals(setOf("com.a", "com.b", "com.c"), Music.toggled(cur, "com.c"))
        assertEquals(setOf("com.b"), Music.toggled(cur, "com.a"))
        assertEquals(setOf("com.a", "com.b"), cur)   // 原本的集合不被改動
    }

    @Test fun toggled_lastOneStays() {
        val one = setOf("com.a")
        assertEquals(one, Music.toggled(one, "com.a"))   // 取消最後一個＝不動
    }

    /** Music.DEFAULT 跟 approute.sh 的 DEF_MUSIC（沒有 approute.music 檔時用）要一致，不然 App 顯示的預設跟路由程式實際用的不同 */
    @Test fun scriptDefaults_matchTheKotlinDefaults() {
        val f = listOf("src/main/assets/approute.sh", "app/src/main/assets/approute.sh").map(::File).first { it.exists() }
        val line = Regex("DEF_MUSIC=\"([^\"]*)\"").find(f.readText())
        assertNotNull("approute.sh has no DEF_MUSIC", line)
        assertEquals(Music.DEFAULT.toList(), line!!.groupValues[1].trim().split(Regex("\\s+")))
    }

    @Test fun script_readsTheUsersMusicFileAndHandsOverTheControlTokenAndUid() {
        val sh = listOf("src/main/assets/approute.sh", "app/src/main/assets/approute.sh").map(::File).first { it.exists() }.readText()
        assertTrue(sh.contains("approute.music"))
        assertTrue(sh.contains("SPLITAUDIO_UID"))
        assertTrue(sh.contains("SPLITAUDIO_TOKEN"))
        assertTrue(sh.contains("approute.token"))
        assertTrue("the token file must be private to the shell user", sh.contains("umask 077") && sh.contains("chmod 600 \$D/approute.token"))
        assertEquals("settoken runs right before each router launch (start and startmap), after the already-running check",
            2, Regex("(?m)^    settoken\\n    CLASSPATH=").findAll(sh).count())
        assertTrue(sh.contains("ask) shift;"))
    }
}
