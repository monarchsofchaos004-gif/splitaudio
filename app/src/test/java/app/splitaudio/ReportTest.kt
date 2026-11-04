package app.splitaudio

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class ReportTest {
    private fun params(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }

    @Test fun url_baseAndFieldIds() {
        val u = Report.issueUrl("T", "Pixel 8", "AP1A, Android 15 (API 35)", "0.5.0 (fdroid)", "works", "not tested", "not tested", "hi")
        assertTrue(u.startsWith("https://github.com/monarchsofchaos004-gif/splitaudio/issues/new?template=device_report.yml&"))
        val p = params(u)
        assertEquals(setOf("template", "title", "device", "rom", "version", "simple", "custom", "volkeys", "notes"), p.keys)
        assertEquals("device_report.yml", p["template"]); assertEquals("Pixel 8", p["device"]); assertEquals("works", p["simple"])
    }

    @Test fun url_fieldIdsMatchIssueTemplate() {
        val f = listOf("../.github/ISSUE_TEMPLATE/device_report.yml", ".github/ISSUE_TEMPLATE/device_report.yml").map { java.io.File(it) }.firstOrNull { it.exists() } ?: return
        val ids = Regex("id: (\\w+)").findAll(f.readText()).map { it.groupValues[1] }.toSet()
        listOf("device", "rom", "version", "simple", "custom", "volkeys", "notes").forEach { assertTrue(it, it in ids) }
    }

    @Test fun url_encodingRoundTripsAndUsesPercent20() {
        val notes = "line1\nline2 & more=stuff #1 100% \u4e2d\u6587 \uD83C\uDFA7"
        val u = Report.issueUrl("a b", "d", "r", "v", "works", "works", "works", notes)
        assertFalse(u.contains(' ')); assertFalse(u.contains('+')); assertFalse(u.contains('\n'))
        assertEquals(notes, params(u)["notes"]); assertEquals("a b", params(u)["title"])
    }

    @Test fun dropdown_onlyAllowedValues() {
        for (v in listOf("works", "partly works", "does not work", "not tested")) assertEquals(v, Report.dropdown(v))
        assertEquals("not tested", Report.dropdown("WORKS")); assertEquals("not tested", Report.dropdown("")); assertEquals("not tested", Report.dropdown("yes"))
        val p = params(Report.issueUrl("t", "d", "r", "v", "garbage", "x", "y", ""))
        assertEquals("not tested", p["simple"]); assertEquals("not tested", p["custom"]); assertEquals("not tested", p["volkeys"])
    }

    @Test fun url_longNotesAreCutTo8000() {
        val notes = "\u4e2d\u6587\uD83C\uDFA7abc ".repeat(3000)
        val u = Report.issueUrl("t", "d", "r", "v", "works", "works", "works", notes)
        assertTrue(u.length.toString(), u.length <= Report.MAX_URL)
        val n = params(u)["notes"]!!
        assertTrue(n.contains("truncated")); assertTrue(n.startsWith("\u4e2d\u6587"))
        assertFalse(n.any { Character.isSurrogate(it) && false })
        // no broken surrogate pair before the marker
        val cut = n.substringBefore("\n...(truncated")
        if (cut.isNotEmpty()) assertFalse(Character.isHighSurrogate(cut.last()))
    }

    @Test fun url_shortNotesUntouched() {
        val u = Report.issueUrl("t", "d", "r", "v", "works", "works", "works", "short")
        assertEquals("short", params(u)["notes"])
    }

    @Test fun url_hugeEverythingStillBounded() {
        val big = "x".repeat(20000)
        assertTrue(Report.issueUrl(big, big, big, big, "works", "works", "works", big).length <= Report.MAX_URL)
    }

    @Test fun fieldsFollowMode() {
        assertEquals(Triple("works", "not tested", "not tested"), Report.fields("simple", "works"))
        assertEquals(Triple("not tested", "does not work", "not tested"), Report.fields("custom", "does not work"))
        val p = params(Report.worksUrl("Pixel 8", "r", "v", "custom", "n"))
        assertEquals("works", p["custom"]); assertEquals("[device] Pixel 8: works", p["title"])
        assertEquals("[device] Pixel 8: not working", params(Report.problemUrl("Pixel 8", "r", "v", "simple", "n"))["title"])
    }

    @Test fun deviceName_noDoubleBrand() {
        assertEquals("Xiaomi 24129PN74C", Report.deviceName("Xiaomi", "24129PN74C"))
        assertEquals("Google Pixel 8", Report.deviceName("Google", "Pixel 8"))
        assertEquals("OnePlus 11 5G", Report.deviceName("OnePlus", "OnePlus 11 5G"))
    }

    private fun info(log: List<String>, types: List<String> = listOf("builtin_speaker", "bluetooth_a2dp")) = Report.DiagInfo(
        "Xiaomi", "24129PN74C", "OS3.0.1.0", "16", 36, "0.5.0-beta1", "fdroid", "simple", "running=true, version=13, permission=granted", types, log)

    @Test fun diagnostics_hasAllFieldsAndNoNames() {
        val d = Report.diagnostics(info(listOf("12:00:00 route: already")))
        for (s in listOf("Xiaomi", "24129PN74C", "OS3.0.1.0", "Android: 16 (API 36)", "0.5.0-beta1 (fdroid)", "Mode: simple", "Shizuku: running=true", "bluetooth_a2dp", "12:00:00 route: already"))
            assertTrue(s, d.contains(s))
    }

    @Test fun diagnostics_onlyLast20LogLines() {
        val d = Report.diagnostics(info((1..50).map { "line$it" }))
        assertFalse(d.contains("line30\n")); assertTrue(d.contains("line31\n")); assertTrue(d.contains("line50\n"))
        assertEquals(20, Regex("line\\d+").findAll(d).count())
    }

    @Test fun diagnostics_emptyLogAndNoDevices() {
        val d = Report.diagnostics(info(emptyList(), emptyList()))
        assertTrue(d.contains("(empty)")); assertTrue(d.contains("Audio output device types: none"))
    }

    @Test fun deviceTypeNames_areTypesNotNames() {
        assertEquals("bluetooth_a2dp", Report.deviceTypeName(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals("builtin_speaker", Report.deviceTypeName(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals("fm", Report.deviceTypeName(android.media.AudioDeviceInfo.TYPE_FM))                 // 14, seen on Xiaomi 15
        assertEquals("telephony", Report.deviceTypeName(android.media.AudioDeviceInfo.TYPE_TELEPHONY))   // 18
        assertEquals("type_9999", Report.deviceTypeName(9999))
    }

    @Test fun crashUrl_titleAndModesUntested() {
        val u = Report.crashUrl("Pixel 8", "AP2A, Android 14 (API 34)", "0.5.4 (fdroid)", "trace")
        assertTrue(u.contains("title=%5Bdevice%5D%20Pixel%208%3A%20crash"))
        assertTrue(u.contains("&simple=not%20tested&custom=not%20tested&volkeys=not%20tested&"))
    }

    @Test fun diagnostics_crashBlockOnlyWhenThereIsOne() {
        assertFalse(Report.diagnostics(info(emptyList())).contains("Last crash"))
        val d = Report.diagnostics(Report.DiagInfo("Xiaomi", "x", "d", "16", 36, "0.5.4", "fdroid", "simple", "s", emptyList(), emptyList(),
            "time=5\nversion=0.5.4\nthread=main\njava.lang.NullPointerException"))
        assertTrue(d.contains("Last crash:\n  time=5\n")); assertTrue(d.contains("  java.lang.NullPointerException\n"))
        assertTrue(d.indexOf("Last crash") < d.indexOf("Recent log"))
    }
}
