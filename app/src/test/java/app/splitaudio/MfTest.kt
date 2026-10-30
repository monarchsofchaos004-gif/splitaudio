package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** 「開影片不打斷音樂」第一次先問：用戶決定（ask／on／off）跟系統現況的對齊、要不要問、通知的間隔。全是純函式。 */
class MfTest {
    private val t0 = 1_700_000_000_000L

    @Test fun normalize_unknownMeansNotDecided() {
        assertEquals(Mf.ASK, Mf.normalize(null))
        assertEquals(Mf.ASK, Mf.normalize(""))
        assertEquals(Mf.ASK, Mf.normalize("maybe"))
        assertEquals(Mf.ON, Mf.normalize("on"))
        assertEquals(Mf.OFF, Mf.normalize("off"))
    }

    @Test fun decisionFor() { assertEquals(Mf.ON, Mf.decisionFor(true)); assertEquals(Mf.OFF, Mf.decisionFor(false)) }

    @Test fun reconcile_systemUnknown_changesNothing() {
        for (d in listOf(Mf.ASK, Mf.ON, Mf.OFF)) assertEquals(d, Mf.reconcile(d, null))
    }

    @Test fun reconcile_aDecisionIsNeverOverwritten() {
        for (sys in listOf("on", "off", "off_user")) {
            assertEquals(Mf.ON, Mf.reconcile(Mf.ON, sys))
            assertEquals(Mf.OFF, Mf.reconcile(Mf.OFF, sys))
        }
    }

    @Test fun reconcile_undecidedButAlreadyOn_countsAsYes() = assertEquals(Mf.ON, Mf.reconcile(Mf.ASK, "on"))   // 舊版自動打開的

    @Test fun reconcile_oldOffButton_countsAsNo() = assertEquals(Mf.OFF, Mf.reconcile(Mf.ASK, "off_user"))

    @Test fun reconcile_undecidedAndOff_staysUndecided() = assertEquals(Mf.ASK, Mf.reconcile(Mf.ASK, "off"))

    @Test fun needsAsk_onlyWhenUndecidedAndTheSystemSettingIsOff() {
        assertTrue(Mf.needsAsk(Mf.ASK, "off"))
        assertFalse(Mf.needsAsk(Mf.ASK, "on"))
        assertFalse(Mf.needsAsk(Mf.ASK, "off_user"))
        assertFalse(Mf.needsAsk(Mf.ASK, null))        // 讀不到系統狀態：不顯示（按了也沒法確認）
        assertFalse(Mf.needsAsk(Mf.ON, "off"))
        assertFalse(Mf.needsAsk(Mf.OFF, "off"))
    }

    @Test fun shouldNotify_firstTime() = assertTrue(Mf.shouldNotify(Mf.ASK, "off", 0L, t0))

    @Test fun shouldNotify_notAgainWithinTenMinutes() {
        assertFalse(Mf.shouldNotify(Mf.ASK, "off", t0, t0 + 1))
        assertFalse(Mf.shouldNotify(Mf.ASK, "off", t0, t0 + 599_999))
        assertTrue(Mf.shouldNotify(Mf.ASK, "off", t0, t0 + 600_000))
        assertTrue(Mf.shouldNotify(Mf.ASK, "off", t0, t0 + 3_600_000))
    }

    @Test fun shouldNotify_clockMovedBackwards_stillAllowed() = assertTrue(Mf.shouldNotify(Mf.ASK, "off", t0, t0 - 86_400_000L))

    @Test fun shouldNotify_neverWhenDecidedOrAlreadyOn() {
        assertFalse(Mf.shouldNotify(Mf.ON, "off", 0L, t0))
        assertFalse(Mf.shouldNotify(Mf.OFF, "off", 0L, t0))
        assertFalse(Mf.shouldNotify(Mf.ASK, "on", 0L, t0))
        assertFalse(Mf.shouldNotify(Mf.ASK, "off_user", 0L, t0))
    }

    @Test fun cooldownIsTenMinutes() = assertEquals(600_000L, Mf.COOLDOWN_MS)
}
