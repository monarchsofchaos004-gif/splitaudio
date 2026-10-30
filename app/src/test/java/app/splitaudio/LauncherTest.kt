package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** 桌面套件名：查到的結果 ∪ 系統介面、60 秒快取的新鮮度判斷（純邏輯；真正的 PackageManager 查詢要裝置）。 */
class LauncherTest {
    @Test fun merge_addsSystemUi() =
        assertEquals(setOf("com.miui.home", "com.android.systemui"), Launcher.merge(listOf("com.miui.home")))

    @Test fun merge_nothingFound_stillHasSystemUi() = assertEquals(Launcher.ALWAYS, Launcher.merge(emptyList()))

    @Test fun merge_dropsNullsAndEmptiesAndDuplicates() =
        assertEquals(setOf("com.android.launcher3", "com.android.systemui"), Launcher.merge(listOf(null, "", "com.android.launcher3", "com.android.launcher3", "com.android.systemui")))

    @Test fun merge_keepsEveryLauncherThatIsInstalled() =
        assertEquals(setOf("com.miui.home", "com.mi.android.globallauncher", "com.android.systemui"),
            Launcher.merge(listOf("com.miui.home", "com.mi.android.globallauncher")))

    @Test fun fresh_neverFilled_isStale() = assertFalse(Launcher.fresh(0L, 1_000_000L))

    @Test fun fresh_within60Seconds() {
        val t0 = 1_700_000_000_000L
        assertTrue(Launcher.fresh(t0, t0))
        assertTrue(Launcher.fresh(t0, t0 + 59_999))
        assertFalse(Launcher.fresh(t0, t0 + 60_000))
        assertFalse(Launcher.fresh(t0, t0 + 3_600_000))
    }

    @Test fun fresh_clockMovedBackwards_isStale() = assertFalse(Launcher.fresh(1_700_000_000_000L, 1_699_999_000_000L))

    @Test fun ttlIsSixtySeconds() = assertEquals(60_000L, Launcher.TTL_MS)
}
