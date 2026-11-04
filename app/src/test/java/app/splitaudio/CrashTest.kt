package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** Crash 的純邏輯：存檔文字（表頭＋堆疊前段、有上限）、診斷區塊、「上次當掉」卡片要不要出現 */
class CrashTest {
    @Test fun text_headerThenTopOfTrace_bounded() {
        val trace = (1..100).joinToString("\n") { "\tat frame$it(File.kt:$it)" }
        val t = Crash.text(1234L, "0.5.4 (fdroid)", "main", "java.lang.IllegalStateException: boom\n$trace")
        assertTrue(t.startsWith("time=1234\nversion=0.5.4 (fdroid)\nthread=main\njava.lang.IllegalStateException: boom\n"))
        assertTrue(t.contains("frame${Crash.MAX_LINES - 1}(")); assertFalse(t.contains("frame${Crash.MAX_LINES}("))   // 只留堆疊前段
        assertTrue(t.length <= Crash.MAX_CHARS)
        assertTrue(Crash.text(1L, "v", "t", "x".repeat(10_000)).length <= Crash.MAX_CHARS)
    }

    @Test fun timeOf_readsTheFirstLine() {
        assertEquals(1234L, Crash.timeOf("time=1234\nversion=v\n"))
        assertEquals(0L, Crash.timeOf("version=v\n")); assertEquals(0L, Crash.timeOf(null)); assertEquals(0L, Crash.timeOf("time=abc"))
    }

    @Test fun section_emptyWhenNothingSaved_indentedOtherwise() {
        assertEquals("", Crash.section(null)); assertEquals("", Crash.section(" \n"))
        assertEquals("Last crash:\n  time=5\n  java.lang.NullPointerException\n", Crash.section("time=5\njava.lang.NullPointerException"))
    }

    @Test fun cardDue_onlyForACrashNotYetDismissed() {
        assertFalse(Crash.cardDue(0L, 0L)); assertTrue(Crash.cardDue(5L, 0L)); assertFalse(Crash.cardDue(5L, 5L)); assertTrue(Crash.cardDue(6L, 5L))
    }

    @Test fun traceOf_hasTheExceptionAndAFrame() {
        val s = Crash.traceOf(IllegalStateException("boom"))
        assertTrue(s.startsWith("java.lang.IllegalStateException: boom")); assertTrue(s.contains("\tat "))
    }
}
