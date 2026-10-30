import static org.junit.Assert.*;

import android.media.AudioDeviceInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import org.junit.Test;

/**
 * Pure helpers of the routing helper (route/AppRoute.java, default package; compiled into the unit tests through
 * sourceSets["test"].java.srcDir("../route")): volume key maths, who may use the control socket, the file readers
 * and the multi audio focus rule. Everything that needs the audio service or a device is not covered here.
 */
public class RouteLogicTest {
    // ---- volume keys ----
    @Test public void volStep_aboutFifteenStepsWhateverTheRange() {
        assertEquals(1, AppRoute.volStep(0, 15));      // AOSP: 15 index steps
        assertEquals(10, AppRoute.volStep(0, 150));    // Xiaomi 15: the old fixed step
        assertEquals(7, AppRoute.volStep(0, 100));
        assertEquals(2, AppRoute.volStep(0, 25));
        assertEquals(2, AppRoute.volStep(0, 30));
        assertEquals(1, AppRoute.volStep(1, 15));      // a stream whose minimum is 1
    }

    @Test public void volStep_neverBelowOne() {
        assertEquals(1, AppRoute.volStep(0, 7));
        assertEquals(1, AppRoute.volStep(0, 1));
        assertEquals(1, AppRoute.volStep(0, 0));
    }

    @Test public void clampIdx_movesByOneStepAndStaysInRange() {
        assertEquals(85, AppRoute.clampIdx(75, 10, true, 0, 150));
        assertEquals(65, AppRoute.clampIdx(75, 10, false, 0, 150));
        assertEquals(150, AppRoute.clampIdx(145, 10, true, 0, 150));
        assertEquals(150, AppRoute.clampIdx(150, 10, true, 0, 150));
        assertEquals(0, AppRoute.clampIdx(5, 10, false, 0, 150));
        assertEquals(0, AppRoute.clampIdx(0, 1, false, 0, 15));
        assertEquals(1, AppRoute.clampIdx(1, 1, false, 1, 15));   // minimum 1 is respected
    }

    @Test public void percent_isRelativeToTheMaximum() {
        assertEquals(50, AppRoute.percent(75, 150));
        assertEquals(100, AppRoute.percent(150, 150));
        assertEquals(0, AppRoute.percent(0, 150));
        assertEquals(6, AppRoute.percent(1, 15));
        assertEquals(0, AppRoute.percent(5, 0));       // unknown maximum: never divide by zero
    }

    // ---- control socket access ----
    @Test public void allowed_rootShellAndTheOwnerAppOnly() {
        assertTrue(AppRoute.allowed(0, -1));
        assertTrue(AppRoute.allowed(2000, -1));
        assertTrue(AppRoute.allowed(10123, 10123));
        assertFalse(AppRoute.allowed(10124, 10123));   // another app
        assertFalse(AppRoute.allowed(10123, -1));      // started without SPLITAUDIO_UID: shell and root only
        assertFalse(AppRoute.allowed(1000, -1));       // system uid is not special
        assertFalse(AppRoute.allowed(1000, 10123));
        assertFalse(AppRoute.allowed(-1, -1));         // a failed lookup never matches an unset owner
    }

    @Test public void parseUid_readsTheEnvironmentValue() {
        assertEquals(10123, AppRoute.parseUid("10123"));
        assertEquals(1010123, AppRoute.parseUid(" 1010123 "));
        assertEquals(-1, AppRoute.parseUid(null));
        assertEquals(-1, AppRoute.parseUid(""));
        assertEquals(-1, AppRoute.parseUid("abc"));
        assertEquals(-1, AppRoute.parseUid("10123; id"));
    }

    @Test public void controlCommands_unknownOrMalformedGetErr() {
        assertEquals("err", AppRoute.volCmd(""));
        assertEquals("err", AppRoute.volCmd("vol"));
        assertEquals("err", AppRoute.volCmd("vol up"));
        assertEquals("err", AppRoute.volCmd("vol sideways com.a"));   // only up / down move the volume
        assertEquals("err", AppRoute.volCmd("reboot"));
    }

    @Test public void controlCommands_stateAndModeWithoutARule() {
        assertEquals("off", AppRoute.volCmd("state"));    // no audio policy registered in a test
        assertEquals("legacy", AppRoute.volCmd("mode"));
        assertEquals("pass", AppRoute.volCmd("vol up com.a"));   // rule off: the keys are left alone
    }

    // ---- multi audio focus: only after the user agreed ----
    @Test public void mfShouldEnable_onlyAfterAgreementAndNotDeclinedAndNotAlreadyOn() {
        assertTrue(AppRoute.mfShouldEnable(true, false, false));
        assertFalse(AppRoute.mfShouldEnable(false, false, false));   // never asked / not agreed
        assertFalse(AppRoute.mfShouldEnable(false, true, false));    // declined
        assertFalse(AppRoute.mfShouldEnable(true, true, false));     // both files: declined wins
        assertFalse(AppRoute.mfShouldEnable(true, false, true));     // already on
        assertFalse(AppRoute.mfShouldEnable(false, false, true));
        assertFalse(AppRoute.mfShouldEnable(false, true, true));
        assertFalse(AppRoute.mfShouldEnable(true, true, true));
    }

    // ---- files written by the app (through approute.sh) ----
    private static File temp(String text) throws Exception {
        File f = File.createTempFile("approute", ".txt");
        f.deleteOnExit();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test public void readVol_parsesVolumesAndSkipsJunk() throws Exception {
        HashMap<Integer, Float> m = AppRoute.readVol(temp(
            "10001=0.35\n10002=0\n10003=1.0\n10004=abc\n=0.5\n10005=1.5\n10006 = 0.5\nnonsense\n10007=-3\n"));
        assertEquals(0.35f, m.get(10001).floatValue(), 1e-6f);
        assertEquals(0f, m.get(10002).floatValue(), 0f);          // 0 = mute
        assertEquals(0.5f, m.get(10006).floatValue(), 1e-6f);     // spaces around the number are fine
        assertEquals(0f, m.get(10007).floatValue(), 0f);          // below 0 is clamped
        assertFalse(m.containsKey(10003));                        // 100 % = left alone
        assertFalse(m.containsKey(10005));                        // above 1 is clamped to 100 % = left alone
        assertFalse(m.containsKey(10004));
        assertEquals(4, m.size());
    }

    @Test public void readVol_missingFile_isEmpty() {
        assertTrue(AppRoute.readVol(new File("/nonexistent/approute.vol")).isEmpty());
    }

    @Test public void readMap_parsesTargetsAndSpeakerUids() throws Exception {
        AppRoute.readMap(temp("speaker:10001,10002\nbt:10003\nwired:abc,10005,\nbogus:10006\n:7\nnocolon\nspeaker:\n"));
        assertArrayEquals(new int[] {10001, 10002}, AppRoute.map.get("speaker"));
        assertArrayEquals(new int[] {10003}, AppRoute.map.get("bt"));
        assertArrayEquals(new int[] {10005}, AppRoute.map.get("wired"));
        assertFalse(AppRoute.map.containsKey("bogus"));
        assertEquals(3, AppRoute.map.size());
        // volume keys: only the speaker-routed apps are "routed"
        assertTrue(AppRoute.routed.contains(10001));
        assertTrue(AppRoute.routed.contains(10002));
        assertFalse(AppRoute.routed.contains(10003));
        assertEquals(2, AppRoute.routed.size());
    }

    @Test public void readMap_isRebuiltFromScratchEachTime() throws Exception {
        AppRoute.readMap(temp("speaker:10001\nbt:10002\n"));
        AppRoute.readMap(temp("bt:10009\n"));
        assertEquals(1, AppRoute.map.size());
        assertArrayEquals(new int[] {10009}, AppRoute.map.get("bt"));
        assertTrue(AppRoute.routed.isEmpty());
    }

    @Test public void target_mapsDeviceTypes() {
        assertEquals("speaker", AppRoute.target(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals("bt", AppRoute.target(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertEquals("bt", AppRoute.target(26));   // BLE headset
        assertEquals("bt", AppRoute.target(27));   // BLE speaker
        assertEquals("wired", AppRoute.target(AudioDeviceInfo.TYPE_WIRED_HEADSET));
        assertEquals("wired", AppRoute.target(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertEquals("wired", AppRoute.target(AudioDeviceInfo.TYPE_USB_HEADSET));
        assertEquals("wired", AppRoute.target(AudioDeviceInfo.TYPE_USB_DEVICE));
        assertNull(AppRoute.target(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE));
    }
}
