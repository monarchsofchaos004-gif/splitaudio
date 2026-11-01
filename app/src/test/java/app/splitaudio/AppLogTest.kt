package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

class AppLogTest {
    @Test fun keepsTailOnly_oneLineEach() {
        AppLog.clear()
        for (i in 1..150) AppLog.add("msg$i\nsecond", 1_000_000L)
        assertEquals(20, AppLog.tail().size); assertTrue(AppLog.tail().last().endsWith("msg150 second"))
        assertEquals(AppLog.MAX, AppLog.tail(1000).size)
        assertTrue(AppLog.format(0L, "x").matches(Regex("\\d\\d:\\d\\d:\\d\\d x")))
        AppLog.clear()
    }
}
