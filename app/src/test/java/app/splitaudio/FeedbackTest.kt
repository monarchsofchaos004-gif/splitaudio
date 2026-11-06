package app.splitaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    private val info = Report.DiagInfo("Xiaomi", "24129PN74G", "OS3.0.1", "16", 36, "0.5.6", "fdroid", "simple", "ok",
        listOf("bluetooth a2dp", "speaker"), listOf("12:00:01 start", "12:00:02 on"), null)

    @Test fun json_escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("{\"a\":\"x\\\"y\\\\z\\nw\\tq\\u0001\"}", Feedback.json(listOf("a" to "x\"y\\z\nw\tq\u0001\r")))
        assertEquals("{}", Feedback.json(emptyList()))
    }

    @Test fun payload_carriesTheDiagnosticsAndStaysWithinTheServerLimits() {
        val p = Feedback.payload(info, "problem", "no sound " + "x".repeat(2000), "  ann  ")
        assertTrue(p.startsWith("{\"app\":\"splitaudio\",\"verdict\":\"problem\",\"version\":\"0.5.6 (fdroid)\",\"flavor\":\"fdroid\",\"device\":\"Xiaomi 24129PN74G\""))
        assertTrue(p.contains("\"mode\":\"simple\""))
        assertTrue(p.contains("\"nick\":\"ann\""))
        assertTrue(p.contains("bluetooth a2dp"))
        val note = Regex("\"note\":\"([^\"]*)\"").find(p)!!.groupValues[1]
        assertEquals(Feedback.MAX_NOTE, note.length)
        // only the keys the server knows, in this order
        assertEquals(listOf("app", "verdict", "version", "flavor", "device", "rom", "mode", "diag", "note", "nick"),
            Regex("\"([a-z]+)\":\"").findAll(p).map { it.groupValues[1] }.filter { it in setOf("app", "verdict", "version", "flavor", "device", "rom", "mode", "diag", "note", "nick") }.distinct().toList())
    }

    @Test fun payload_truncatesHugeDiagnostics() {
        val big = info.let { Report.DiagInfo(it.brand, it.model, it.display, it.androidRelease, it.sdk, it.appVersion, it.flavor, it.mode, it.shizuku, it.audioTypes, List(20) { "line $it " + "y".repeat(600) }, "crash ".repeat(800)) }
        val diag = Regex("\"diag\":\"(.*?)\",\"note\"").find(Feedback.payload(big, "crash"))!!.groupValues[1]
        assertTrue(diag.length <= Feedback.MAX_DIAG * 2)   // escaped newlines may double a little, raw text is capped at MAX_DIAG
    }

    @Test fun endpointFrom_acceptsHttpsOnly() {
        assertEquals("https://example.org/sa/report", Feedback.endpointFrom("{\"report\": \"https://example.org/sa/report\"}"))
        assertEquals("https://h/sa/report", Feedback.endpointFrom("{ \"other\": 1, \"report\":\"https://h/sa/report\" }"))
        assertNull(Feedback.endpointFrom("{\"report\": \"http://example.org/sa/report\"}"))
        assertNull(Feedback.endpointFrom("not json at all"))
        assertNull(Feedback.endpointFrom("{\"report\": \"javascript:alert(1)\"}"))
        assertTrue(Feedback.DEFAULT_URL.startsWith("https://") && Feedback.DEFAULT_URL.endsWith("/sa/report") && !Feedback.DEFAULT_URL.contains("__"))
    }

    @Test fun isRepeat_onlyAWorksRightAfterAWorks() {
        val t = 1_000_000L
        assertTrue(Feedback.isRepeat("works", "works", t, t + 1))
        assertTrue(Feedback.isRepeat("works", "works", t, t + Feedback.REPEAT_MS - 1))
        assertFalse(Feedback.isRepeat("works", "works", t, t + Feedback.REPEAT_MS))
        assertFalse(Feedback.isRepeat("works", null, 0L, t))
        assertFalse(Feedback.isRepeat("works", "problem", t, t + 1))
        assertFalse(Feedback.isRepeat("problem", "works", t, t + 1))
        assertFalse(Feedback.isRepeat("crash", "crash", t, t + 1))
    }
}
