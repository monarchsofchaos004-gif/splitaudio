package app.splitaudio

import app.splitaudio.adb.AdbBackend
import org.junit.Assert.*
import org.junit.Test

/** play 版的接線：Shizuku 排前面（有就用）、其次內建 ADB；Pro 要買或試用；沒有無障礙音量鍵。 */
class DistTest {
    @Test fun backends_shizukuFirstThenAdb() = assertEquals(listOf<Backend>(ShizukuBackend, AdbBackend), Dist.backends())
    @Test fun proIsGated() = assertFalse(Dist.proAlways)
    @Test fun noVolumeKeys() = assertFalse(Dist.hasVolKey)
}
