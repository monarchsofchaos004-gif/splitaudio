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
        assertTrue(Health.isFailure("start rc=1 boom", false)); assertTrue(Health.isFailure("start rc=0 started pid=5", false)); assertTrue(Health.isFailure("map_fail:1 x", false))
        assertTrue(Health.isFailure("deploy_fail:-2 no_backend", false))
        assertFalse(Health.isFailure("already", false)); assertFalse(Health.isFailure("no_apps", false)); assertFalse(Health.isFailure("", false))
        // 0.5.4 (#12): the router answers = it is only waiting for the music app -> never a failure, whatever the start text says
        assertFalse(Health.isFailure("start rc=0 11-03 10:31:01 control port 127.0.0.1:47821, owner uid 11302", true))
        assertFalse(Health.isFailure("start rc=1 boom", true))
    }

    @Test fun notifyNeedsThreeInARowAndRateLimit() {
        assertFalse(Health.shouldNotifyFailure(2, 0L, 1000L))
        assertTrue(Health.shouldNotifyFailure(3, 0L, 1000L))
        assertFalse(Health.shouldNotifyFailure(5, 1000L, 1000L + Health.NOTIFY_GAP_MS - 1))
        assertTrue(Health.shouldNotifyFailure(5, 1000L, 1000L + Health.NOTIFY_GAP_MS))
        assertTrue(Health.shouldNotifyFailure(3, 9000L, 1000L))   // clock went backwards
    }

    @Test fun failDue_needsCountAndSpan() {
        // 剛更新完兩次沒接上 → 不算；要三次以上且從第一次起至少 90 秒還在失敗
        assertFalse(Health.failDue(2, 1000L, 1000L + Health.FAIL_SPAN_MS))
        assertFalse(Health.failDue(3, 1000L, 1000L + Health.FAIL_SPAN_MS - 1))
        assertTrue(Health.failDue(3, 1000L, 1000L + Health.FAIL_SPAN_MS))
        assertFalse(Health.failDue(3, 0L, 1000L))
        assertFalse(Health.failDue(3, 5000L, 1000L))   // clock went backwards
    }

    @Test fun grace_afterServiceStart() {
        assertTrue(Health.inGrace(1000L + Health.GRACE_MS, 1000L + Health.GRACE_MS - 1))
        assertFalse(Health.inGrace(1000L + Health.GRACE_MS, 1000L + Health.GRACE_MS))
        assertFalse(Health.inGrace(0L, 1000L))
    }

    @Test fun feedbackOnTop_onlyWhileTheQuestionIsUnanswered() {
        org.junit.Assert.assertTrue(Health.feedbackOnTop(true, false))
        org.junit.Assert.assertFalse(Health.feedbackOnTop(false, false))
        org.junit.Assert.assertFalse(Health.feedbackOnTop(true, true))
    }

    @Test fun failCardWindow() {
        assertFalse(Health.failCardVisible(0L, 1000L)); assertTrue(Health.failCardVisible(1000L, 1000L + Health.FAIL_CARD_MS - 1))
        assertFalse(Health.failCardVisible(1000L, 1000L + Health.FAIL_CARD_MS))
    }
}
