package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** 「不要暫停別的 App」清單：套件名驗證、approute.nofocus 的檔案文字、勾選規則。 */
class NoFocusTest {
    @Test fun valid_trimsDropsAnythingThatCouldReachTheShellAndDedups() {
        val raw = listOf(" com.a.one ", "com.a.one", "com.b.two", "bad name", "x;y", "a\nb", "\$(id)", "`id`", "a|b", "中文.app", null, "")
        assertEquals(listOf("com.a.one", "com.b.two"), NoFocus.valid(raw).toList())
        assertTrue(NoFocus.valid(null).isEmpty())
    }

    @Test fun text_onePackagePerLine() {
        assertEquals("com.a\ncom.b\n", NoFocus.text(listOf("com.a", "com.b")))
        assertEquals("", NoFocus.text(emptyList()))
    }

    @Test fun toggled_addsRemovesAndMayEmpty() {
        val cur = linkedSetOf("com.a")
        assertEquals(setOf("com.a", "com.b"), NoFocus.toggled(cur, "com.b"))
        assertEquals(emptySet<String>(), NoFocus.toggled(cur, "com.a"))
        assertEquals(setOf("com.a"), cur)   // 原本的集合不被改動
    }

    @Test fun toggled_ignoresInvalidPackageNames() {
        assertEquals(setOf("com.a"), NoFocus.toggled(setOf("com.a"), "bad;name"))
    }
}
