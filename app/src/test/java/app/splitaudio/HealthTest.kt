package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

class HealthTest {
    private val m = 60_000L

    @Test fun streak_continuesWithinGap_restartsAfter() {
        assertEquals(1000L, Health.streakStart(1000L, 5000L, 5000L + Health.GAP_MS))
        assertEquals(9_999_999L, Health.streakStart(1000L, 5000L, 9_999_999L))
        assertEquals(500L, Health.streakStart(0L, 0L, 500L))
        assertEquals(500L, Health.streakStart(1000L, 0L, 500L))
        assertEquals(500L, Health.streakStart(1000L, 2000L, 500L))   // clock went backwards
    }

    @Test fun success_dueAfterTenMinutesOnlyOnce() {
        assertFalse(Health.successDue(1000L, 1000L + 9 * m, false))
        assertTrue(Health.successDue(1000L, 1000L + 10 * m, false))
        assertFalse(Health.successDue(1000L, 1000L + 20 * m, true))
        assertFalse(Health.successDue(0L, 20 * m, false))
    }

    @Test fun failureClassification() {
        assertTrue(Health.isFailure("start rc=1 boom")); assertTrue(Health.isFailure("start rc=0 started pid=5")); assertTrue(Health.isFailure("map_fail:1 x"))
        assertTrue(Health.isFailure("deploy_fail:-2 no_backend"))
        assertFalse(Health.isFailure("already")); assertFalse(Health.isFailure("no_apps")); assertFalse(Health.isFailure(""))
    }

    @Test fun notifyNeedsTwoInARowAndRateLimit() {
        assertFalse(Health.shouldNotifyFailure(1, 0L, 1000L))
        assertTrue(Health.shouldNotifyFailure(2, 0L, 1000L))
        assertFalse(Health.shouldNotifyFailure(5, 1000L, 1000L + Health.NOTIFY_GAP_MS - 1))
        assertTrue(Health.shouldNotifyFailure(5, 1000L, 1000L + Health.NOTIFY_GAP_MS))
        assertTrue(Health.shouldNotifyFailure(2, 9000L, 1000L))   // clock went backwards
    }

    @Test fun failCardWindow() {
        assertFalse(Health.failCardVisible(0L, 1000L)); assertTrue(Health.failCardVisible(1000L, 1000L + Health.FAIL_CARD_MS - 1))
        assertFalse(Health.failCardVisible(1000L, 1000L + Health.FAIL_CARD_MS))
    }
}
