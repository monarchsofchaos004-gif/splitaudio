package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** 每個 App 音量：prefs → 有效設定（Mixer.valid）、approute.volpkg 文字（Mixer.text）。 */
class MixerTest {
    @Test fun text_percentToFraction() {
        val m = linkedMapOf("com.a" to 35, "com.b" to 0, "com.c" to 99, "com.d" to 7, "com.e" to 50)
        assertEquals("com.a=0.35\ncom.b=0.00\ncom.c=0.99\ncom.d=0.07\ncom.e=0.50\n", Mixer.text(m))
    }

    @Test fun text_empty() = assertEquals("", Mixer.text(emptyMap()))

    @Test fun text_alwaysUsesDotDecimalSeparator() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)   // 逗號小數點的語系：不能寫出「0,35」，路由程式讀不懂
            assertEquals("com.a=0.35\n", Mixer.text(mapOf("com.a" to 35)))
        } finally { java.util.Locale.setDefault(old) }
    }

    @Test fun valid_dropsHelperKeysAndNonIntegers() {
        val raw = mapOf<String, Any?>(
            "com.a" to 35,
            "com.a#last" to 35,       // 靜音前的音量（輔助鍵）
            "com.b" to 0,             // 靜音
            "com.c" to "35",          // 字串不收
            "com.d" to 5L,            // Long 不收（getInt 會拋例外）
            "com.e" to true)
        assertEquals(mapOf("com.a" to 35, "com.b" to 0), Mixer.valid(raw))
    }
}
