package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** 自訂模式設定：prefs → 有效的「套件→輸出」（Outputs.valid）、approute.pkgmap 文字（Outputs.mapText）、套件名驗證。 */
class OutputsTest {
    @Test fun pkgRegex_acceptsPackageNames() {
        for (p in listOf("com.google.android.apps.youtube.music", "app.morphe.android.apps.youtube.music", "a", "com.example.app_1", "A.B2"))
            assertTrue(p, Outputs.PKG.matches(p))
    }

    @Test fun pkgRegex_rejectsAnythingThatCouldReachTheShell() {
        for (p in listOf("", "a b", "a;rm -rf /", "a\nb", "abc\n", "com.a/b", "a|b", "a`b`", "a\$(b)", "a&b", "a'b", "a\"b", "中文.app", "a-b"))
            assertFalse(p, Outputs.PKG.matches(p))
    }

    @Test fun valid_keepsOnlyKnownOutputsOfValidPackages() {
        val raw = mapOf<String, Any?>(
            "custom" to true,                       // 不是 o: 開頭（模式開關）
            "o:com.a.speaker" to "speaker",
            "o:com.b.bt" to "bt",
            "o:com.c.wired" to "wired",
            "o:com.d.default" to "default",         // default＝沒設定
            "o:com.e.unknown" to "loudspeaker",     // 不認識的輸出
            "o:bad pkg" to "speaker",               // 套件名不合法
            "o:" to "speaker",
            "x:com.f" to "speaker")
        assertEquals(mapOf("com.a.speaker" to "speaker", "com.b.bt" to "bt", "com.c.wired" to "wired"), Outputs.valid(raw))
    }

    @Test fun mapText_oneLinePerOutput() {
        val m = linkedMapOf("com.a" to "speaker", "com.b" to "bt", "com.c" to "speaker")
        assertEquals("speaker:com.a,com.c\nbt:com.b\n", Outputs.mapText(m))
    }

    @Test fun mapText_empty() = assertEquals("\n", Outputs.mapText(emptyMap()))

    @Test fun keys_defaultFirst() {
        assertEquals("default", Outputs.KEYS[0])
        assertEquals(listOf("default", "speaker", "bt", "wired"), Outputs.KEYS.toList())
    }
}
