package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** Pro 試用天數計算（Pro.daysLeft，純函式）。 */
class ProTest {
    private val day = 86_400_000L
    private val t0 = 1_700_000_000_000L

    @Test fun neverStarted_fullTrial() = assertEquals(7, Pro.daysLeft(0L, t0))

    @Test fun justStarted_fullTrial() = assertEquals(7, Pro.daysLeft(t0, t0))

    @Test fun roundsUp() {
        assertEquals(7, Pro.daysLeft(t0, t0 + 1))               // 剛過 1 毫秒，還是算 7 天
        assertEquals(7, Pro.daysLeft(t0, t0 + day - 1))
        assertEquals(6, Pro.daysLeft(t0, t0 + day))             // 滿 1 天 → 剩 6
        assertEquals(1, Pro.daysLeft(t0, t0 + 6 * day + day / 2))
    }

    @Test fun endsExactlyAtTrialLength() {
        assertEquals(1, Pro.daysLeft(t0, t0 + 7 * day - 1))
        assertEquals(0, Pro.daysLeft(t0, t0 + 7 * day))
        assertEquals(0, Pro.daysLeft(t0, t0 + 400 * day))
    }

    @Test fun clockMovedBackwards_neverMoreThanTheTrial() = assertEquals(7, Pro.daysLeft(t0, t0 - 30 * day))

    @Test fun customTrialLength() {
        assertEquals(3, Pro.daysLeft(t0, t0, trialDays = 3))
        assertEquals(0, Pro.daysLeft(t0, t0 + 3 * day, trialDays = 3))
    }

    @Test fun trialLengthIsSevenDays() = assertEquals(7, Pro.TRIAL_DAYS)
}
