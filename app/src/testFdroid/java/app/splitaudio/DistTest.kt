package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** fdroid 版的接線：只用 Shizuku、全部功能免費、有無障礙音量鍵、沒有一次性設定區塊的類別（setupSection 要 Activity，這裡只測常數）。 */
class DistTest {
    @Test fun backends_shizukuOnly() = assertEquals(listOf<Backend>(ShizukuBackend), Dist.backends())
    @Test fun everythingIsFree() = assertTrue(Dist.proAlways)
    @Test fun hasVolumeKeys() = assertTrue(Dist.hasVolKey)
}
