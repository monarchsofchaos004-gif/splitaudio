package app.splitaudio

import org.junit.Assert.*
import org.junit.Test
import app.splitaudio.Update.Release

class UpdateTest {
    private val base = "https://github.com/monarchsofchaos004-gif/splitaudio/releases/tag/"
    private fun r(tag: String, draft: Boolean = false, url: String = base + tag) = Release(tag, url, draft, tag.contains("beta"))

    @Test fun parseVersion() {
        assertEquals("0.5.0", Update.parseVersion("v0.5.0").toString())
        assertEquals("0.5.0-beta2", Update.parseVersion("v0.5.0-beta2").toString())
        assertEquals("1.2.3", Update.parseVersion("1.2.3").toString())
        for (bad in listOf("", "latest", "v1.2", "v1.2.3-rc1", "v1.2.3-beta0", "v1.2.3-beta", "v1.2.3.4", "v1.2.3-beta01", "x1.2.3")) assertNull(bad, Update.parseVersion(bad))
        assertNull(Update.parseVersion(null))
    }

    @Test fun ordering() {
        fun v(s: String) = Update.parseVersion(s)!!
        assertTrue(v("0.5.0-beta1") < v("0.5.0-beta2")); assertTrue(v("0.5.0-beta9") < v("0.5.0")); assertTrue(v("0.5.0") < v("0.5.1-beta1"))
        assertTrue(v("0.10.0") > v("0.9.9")); assertTrue(v("1.0.0") > v("0.99.99"))
    }

    @Test fun stableInstall_onlySeesStable() {
        val rel = listOf(r("v0.5.0-beta2"), r("v0.4.1"), r("v0.4.0"))
        assertEquals("v0.4.1", Update.pickNewer("0.4.0", rel, null)!!.tag)
        assertNull(Update.pickNewer("0.4.1", rel, null))
        assertNull(Update.pickNewer("0.5.0", rel, null))
    }

    @Test fun betaInstall_seesBetaAndStable() {
        val rel = listOf(r("v0.5.0-beta2"), r("v0.5.0-beta1"), r("v0.4.1"))
        assertEquals("v0.5.0-beta2", Update.pickNewer("0.5.0-beta1", rel, null)!!.tag)
        assertNull(Update.pickNewer("0.5.0-beta2", rel, null))
        assertEquals("v0.5.0", Update.pickNewer("0.5.0-beta2", rel + r("v0.5.0"), null)!!.tag)
    }

    @Test fun skippedDraftForeignAndGarbageAreIgnored() {
        assertNull(Update.pickNewer("0.4.0", listOf(r("v0.4.1")), "v0.4.1"))
        assertNull(Update.pickNewer("0.4.0", listOf(r("v0.4.1", draft = true)), null))
        assertNull(Update.pickNewer("0.4.0", listOf(r("v0.4.1", url = "https://evil.example/x")), null))
        assertNull(Update.pickNewer("0.4.0", listOf(r("nightly")), null))
        assertNull(Update.pickNewer("garbage", listOf(r("v9.9.9")), null))
        // skipping 0.4.1 does not hide the later 0.4.2
        assertEquals("v0.4.2", Update.pickNewer("0.4.0", listOf(r("v0.4.2"), r("v0.4.1")), "v0.4.1")!!.tag)
    }

    @Test fun dailyGate() {
        assertTrue(Update.due(0, 1000)); assertFalse(Update.due(1000, 1000 + Update.DAY_MS - 1)); assertTrue(Update.due(1000, 1000 + Update.DAY_MS))
        assertTrue(Update.due(5000, 1000))
    }

    @Test fun parseReleases_realisticJson() {
        val json = """[{"url":"x","html_url":"${base}v0.5.0-beta1","tag_name":"v0.5.0-beta1","draft":false,"prerelease":true,
            "author":{"login":"a","html_url":"https://github.com/a"},"body":"line\nbreak \u4e2d \"q\"","assets":[{"size":1.5e3,"n":null}]},
            {"tag_name":"v0.4.0","html_url":"${base}v0.4.0","draft":false,"prerelease":false},{"no":"tag"}]"""
        val l = Update.parseReleases(json)
        assertEquals(2, l.size); assertEquals("v0.5.0-beta1", l[0].tag); assertTrue(l[0].prerelease); assertEquals(base + "v0.4.0", l[1].htmlUrl)
    }

    @Test fun parseReleases_badInputIsEmpty() {
        for (s in listOf("", "{}", "[", "not json", "[1,2]", "{\"message\":\"rate limited\"}", "[{\"tag_name\":}]")) assertTrue(s, Update.parseReleases(s).isEmpty())
    }

    @Test fun miniJson_basics() {
        assertEquals(listOf(1.0, "a", true, null), MiniJson.parse("[1, \"a\", true, null]"))
        assertEquals("\u00e9\n", MiniJson.parse("\"\\u00e9\\n\""))
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse(" { } "))
    }
}
