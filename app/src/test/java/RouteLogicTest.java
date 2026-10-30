import static org.junit.Assert.*;

import android.media.AudioDeviceInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import org.junit.Test;

/**
 * Pure helpers of the routing helper (route/AppRoute.java, default package; compiled into the unit tests through
 * sourceSets["test"].java.srcDir("../route")): volume key maths, who may use the control port (token line, uid lookup in
 * /proc/net/tcp), the file readers and the multi audio focus rule. Everything that needs the audio service or a device is not
 * covered here; the control port on real sockets is in ControlChannelTest.
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

    // ---- control port access: who ----
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

    @Test public void uidProblem_decidesWhoIsLetIn() {
        assertNull(AppRoute.uidProblem(10234, 10234, true));                         // the app that started the router
        assertNull(AppRoute.uidProblem(2000, 10234, true));                          // shell
        assertNull(AppRoute.uidProblem(0, -1, true));                                // root
        assertNull(AppRoute.uidProblem(2000, -1, false));
        assertNotNull(AppRoute.uidProblem(10235, 10234, true));                      // another app
        assertTrue(AppRoute.uidProblem(10235, 10234, true).contains("10235"));
        assertNotNull(AppRoute.uidProblem(10235, 10234, false));                     // also where the lookup is only "best effort"
        assertNotNull(AppRoute.uidProblem(10234, -1, true));                         // started without an owner: shell and root only
        assertNotNull(AppRoute.uidProblem(AppRoute.UID_AMBIGUOUS, 10234, true));
        assertNotNull(AppRoute.uidProblem(AppRoute.UID_AMBIGUOUS, 10234, false));    // disagreeing table entries are never waved through
        assertNotNull(AppRoute.uidProblem(AppRoute.UID_UNKNOWN, 10234, true));       // the lookup works here, but this client is not in the table
        assertNull(AppRoute.uidProblem(AppRoute.UID_UNKNOWN, 10234, false));         // the lookup does not work on this device at all: the token decided
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

    // ---- control port access: the token ----
    private static final String TOK = "0123456789abcdef0123456789abcdef";

    @Test public void validToken_acceptsTheAppsFormatAndNothingOdd() {
        assertEquals(TOK, AppRoute.validToken(TOK));
        assertEquals(TOK, AppRoute.validToken("  " + TOK + "\n"));                     // as read from a file
        assertNotNull(AppRoute.validToken("0123456789abcdef"));                       // 16 is the shortest
        assertEquals("Ab9Zz0Ab9Zz0Ab9Zz0", AppRoute.validToken("Ab9Zz0Ab9Zz0Ab9Zz0"));   // letters and digits are fine
        assertNull(AppRoute.validToken(null));
        assertNull(AppRoute.validToken(""));
        assertNull(AppRoute.validToken("abc"));
        assertNull(AppRoute.validToken("0123456789abcde"));                           // 15: too short
        assertNull(AppRoute.validToken(TOK + TOK + TOK + TOK + "0"));                 // 129: too long
        assertNull(AppRoute.validToken("0123456789abcdef0123456789abcde;"));          // not a letter or digit
        assertNull(AppRoute.validToken("0123456789abcdef 0123456789abcdef"));         // a blank inside
        assertNull(AppRoute.validToken("0123456789abcdef\n0123456789abcdef"));
    }

    @Test public void authToken_readsTheAuthLineStrictly() {
        assertEquals("abc123", AppRoute.authToken("auth abc123"));
        assertEquals("abc123", AppRoute.authToken("auth abc123\r"));          // a client that ends its lines with \r\n
        assertEquals(TOK, AppRoute.authToken("auth " + TOK));
        assertNull(AppRoute.authToken(null));                                 // end of stream / timeout / line too long
        assertNull(AppRoute.authToken(""));
        assertNull(AppRoute.authToken("auth"));
        assertNull(AppRoute.authToken("auth "));
        assertNull(AppRoute.authToken("authabc123"));
        assertNull(AppRoute.authToken("AUTH abc123"));
        assertNull(AppRoute.authToken("auth  abc123"));                       // two blanks
        assertNull(AppRoute.authToken("auth abc 123"));
        assertNull(AppRoute.authToken("auth\tabc123"));
        assertNull(AppRoute.authToken("state"));                              // a command where the auth line belongs
        assertNull(AppRoute.authToken("vol up com.a"));
    }

    @Test public void tokenMatches_exactCopyOnly() {
        assertTrue(AppRoute.tokenMatches(TOK, TOK));
        assertTrue(AppRoute.tokenMatches(new String(TOK), TOK));
        assertFalse(AppRoute.tokenMatches("0123456789abcdef0123456789abcdee", TOK));   // last character
        assertFalse(AppRoute.tokenMatches("1123456789abcdef0123456789abcdef", TOK));   // first character
        assertFalse(AppRoute.tokenMatches(TOK.substring(1), TOK));                       // shorter
        assertFalse(AppRoute.tokenMatches(TOK + "0", TOK));                              // longer
        assertFalse(AppRoute.tokenMatches(TOK.toUpperCase(), TOK));                      // case counts
        assertFalse(AppRoute.tokenMatches(null, TOK));
        assertFalse(AppRoute.tokenMatches(TOK, null));
        assertFalse(AppRoute.tokenMatches(null, null));
        assertFalse(AppRoute.tokenMatches("", ""));                                      // an empty token never matches
    }

    @Test public void readTokenFile_firstLineTrimmed() throws Exception {
        assertEquals(TOK, AppRoute.readTokenFile(temp(TOK + "\n")));
        assertEquals(TOK, AppRoute.readTokenFile(temp("  " + TOK + "  ")));
        assertEquals(TOK, AppRoute.readTokenFile(temp(TOK)));                 // approute.sh writes it without a newline
        assertNull(AppRoute.readTokenFile(temp("")));
        assertNull(AppRoute.readTokenFile(new File("/nonexistent/approute.token")));
        assertNull(AppRoute.validToken(AppRoute.readTokenFile(temp("\n"))));  // an empty line is not a token
    }

    // ---- control port access: the uid on the other end, from /proc/net/tcp and /proc/net/tcp6 ----
    // The router listens on 47821 = 0xBACD; the client's source port is 40000 = 0x9C40.
    private static final String HEAD4 = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode";
    private static final String HEAD6 = "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode";
    private static final String CLIENT4 = "   3: 0100007F:9C40 0100007F:BACD 01 00000000:00000000 00:00000000 00000000 10234        0 5551212 1 0000000000000000 20 4 30 10 -1";
    private static final String ROUTER4 = "   4: 0100007F:BACD 0100007F:9C40 01 00000000:00000000 00:00000000 00000000  2000        0 5551213 1 0000000000000000 20 4 30 10 -1";
    private static final String CLIENT6 = "   1: 0000000000000000FFFF00000100007F:9C40 0000000000000000FFFF00000100007F:BACD 01 00000000:00000000 00:00000000 00000000 10234        0 5551214 1 0000000000000000 20 4 30 10 -1";
    private static final String ROUTER6 = "   2: 0000000000000000FFFF00000100007F:BACD 0000000000000000FFFF00000100007F:9C40 01 00000000:00000000 00:00000000 00000000  2000        0 5551215 1 0000000000000000 20 4 30 10 -1";
    private static final String OTHER4 = "   9: 0100007F:9C41 0100007F:BACD 01 00000000:00000000 00:00000000 00000000 10300        0 5551299 1 0000000000000000 20 4 30 10 -1";

    private static java.util.List<String> lines(String... l) { return java.util.Arrays.asList(l); }

    @Test public void uidOfConnection_findsTheClientInTheIpv4Table() {
        assertEquals(10234, AppRoute.uidOfConnection(lines(HEAD4, CLIENT4, ROUTER4, OTHER4), 40000, 47821));
        assertEquals(10300, AppRoute.uidOfConnection(lines(HEAD4, CLIENT4, ROUTER4, OTHER4), 40001, 47821));
    }

    @Test public void uidOfConnection_findsTheClientInTheIpv6Table() {
        // Java sockets are IPv6 sockets: 127.0.0.1 shows up as ::ffff:127.0.0.1
        assertEquals(10234, AppRoute.uidOfConnection(lines(HEAD6, CLIENT6, ROUTER6), 40000, 47821));
    }

    @Test public void uidOfConnection_theRoutersOwnEndIsNotTheClient() {
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4, ROUTER4), 40000, 47821));   // would say uid 2000 = shell
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD6, ROUTER6), 40000, 47821));
    }

    @Test public void uidOfConnection_noMatch_isUnknown() {
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4, CLIENT4), 40002, 47821));    // other source port
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4, CLIENT4), 40000, 47822));    // other router port
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4), 40000, 47821));
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(), 40000, 47821));                  // unreadable /proc file
    }

    @Test public void uidOfConnection_timeWaitLinesAreIgnored() {
        // a closed connection lingering in TIME_WAIT (state 06) always reports uid 0 = root: it must never count as the client
        String tw = CLIENT4.replace(" 01 ", " 06 ").replace(" 10234 ", "     0 ");
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4, tw), 40000, 47821));
    }

    @Test public void uidOfConnection_onlyLoopbackConnectionsCount() {
        String lan = CLIENT4.replace("0100007F:9C40", "0A01A8C0:9C40");                                // 192.168.1.10 -> 127.0.0.1:47821 cannot happen
        String out = CLIENT4.replace("0100007F:BACD", "0A01A8C0:BACD");                               // our port number on another host
        String lan6 = CLIENT6.replace("0000000000000000FFFF00000100007F:9C40", "0000000000000000FFFF00000A01A8C0:9C40");
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4, lan), 40000, 47821));
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD4, out), 40000, 47821));
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines(HEAD6, lan6), 40000, 47821));
        String lo6 = CLIENT6.replace("0000000000000000FFFF00000100007F", "00000000000000000000000001000000");   // ::1
        assertEquals(10234, AppRoute.uidOfConnection(lines(HEAD6, lo6), 40000, 47821));
    }

    @Test public void uidOfConnection_entriesThatDisagreeAreAmbiguous() {
        String twin = CLIENT4.replace("0100007F:9C40", "0200007F:9C40").replace(" 10234 ", " 10300 ");    // same ports from 127.0.0.2, another uid
        assertEquals(AppRoute.UID_AMBIGUOUS, AppRoute.uidOfConnection(lines(HEAD4, CLIENT4, twin), 40000, 47821));
        assertEquals(10234, AppRoute.uidOfConnection(lines(HEAD4, CLIENT4, CLIENT4), 40000, 47821));      // the same uid twice is fine
        assertFalse(AppRoute.allowed(AppRoute.UID_AMBIGUOUS, 10234));
        assertFalse(AppRoute.allowed(AppRoute.UID_UNKNOWN, -1));
    }

    @Test public void uidOfConnection_junkLinesAreSkipped() {
        String badUid = CLIENT4.replace(" 10234 ", " abc ");
        String hugeUid = CLIENT4.replace(" 10234 ", " 4294967295 ");                                     // unmapped uid
        String shortLine = "   3: 0100007F:9C40 0100007F:BACD 01";
        assertEquals(AppRoute.UID_UNKNOWN, AppRoute.uidOfConnection(lines("", "garbage", shortLine, badUid, hugeUid), 40000, 47821));
        assertEquals(10234, AppRoute.uidOfConnection(lines("", "garbage", shortLine, badUid, CLIENT4), 40000, 47821));
    }

    @Test public void readLines_keepsOnlyTheLinesThatMentionThePort() throws Exception {
        File f = temp(HEAD4 + "\n" + CLIENT4 + "\n" + ROUTER4 + "\n" + OTHER4 + "\n" + CLIENT4.toLowerCase() + "\n");
        java.util.List<String> l = AppRoute.readLines(f.getPath(), 40000);       // 0x9C40: the client's end and the router's end of that connection
        assertEquals(3, l.size());                                                  // (the kernel writes capitals; lower case is accepted too)
        assertEquals(1, AppRoute.readLines(f.getPath(), 40001).size());
        assertEquals(0, AppRoute.readLines(f.getPath(), 1234).size());
        assertEquals(10234, AppRoute.uidOfConnection(l, 40000, 47821));
        assertEquals(0, AppRoute.readLines("/nonexistent/net/tcp", 40000).size());   // unreadable file: nothing found, no exception
    }

    @Test public void loopback_recognisesTheAddressFormsOfProcNetTcp() {
        assertTrue(AppRoute.loopback("0100007F:1234"));                                // 127.0.0.1
        assertTrue(AppRoute.loopback("0200007F:1234"));                                // 127.0.0.2
        assertTrue(AppRoute.loopback("0000000000000000FFFF00000100007F:1234"));        // ::ffff:127.0.0.1
        assertTrue(AppRoute.loopback("00000000000000000000000001000000:1234"));        // ::1
        assertTrue(AppRoute.loopback("0100007f:1234"));                                // lower case is fine
        assertFalse(AppRoute.loopback("0A01A8C0:1234"));                               // 192.168.1.10
        assertFalse(AppRoute.loopback("00000000:1234"));                               // 0.0.0.0
        assertFalse(AppRoute.loopback("0000000000000000FFFF00000A01A8C0:1234"));       // ::ffff:192.168.1.10
        assertFalse(AppRoute.loopback("00000000000000000000000000000000:1234"));       // ::
        assertFalse(AppRoute.loopback("0100007F"));                                    // no port
        assertFalse(AppRoute.loopback("abcd:1234"));
        assertFalse(AppRoute.loopback(""));
    }

    @Test public void hexPort_readsTheHexPortAfterTheColon() {
        assertEquals(47821, AppRoute.hexPort("0100007F:BACD"));
        assertEquals(40000, AppRoute.hexPort("0000000000000000FFFF00000100007F:9C40"));
        assertEquals(0, AppRoute.hexPort("00000000:0000"));
        assertEquals(-1, AppRoute.hexPort("0100007F"));
        assertEquals(-1, AppRoute.hexPort("0100007F:ZZZZ"));
    }

    @Test public void readLineLimited_stopsAtNewlineAndAtTheLimit() throws Exception {
        java.io.InputStream two = new java.io.ByteArrayInputStream("auth abc\nstate\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("auth abc", AppRoute.readLineLimited(two, 512));
        assertEquals("state", AppRoute.readLineLimited(two, 512));                     // nothing after the first line was swallowed
        assertNull(AppRoute.readLineLimited(two, 512));                                // end of stream
        assertEquals("abc\r", AppRoute.readLineLimited(new java.io.ByteArrayInputStream("abc\r\n".getBytes(StandardCharsets.UTF_8)), 512));   // trimmed by the caller
        assertEquals("12345", AppRoute.readLineLimited(new java.io.ByteArrayInputStream("12345\n".getBytes(StandardCharsets.UTF_8)), 5));      // exactly the limit
        assertNull(AppRoute.readLineLimited(new java.io.ByteArrayInputStream("123456\n".getBytes(StandardCharsets.UTF_8)), 5));                // one more
        assertNull(AppRoute.readLineLimited(new java.io.ByteArrayInputStream("no newline".getBytes(StandardCharsets.UTF_8)), 512));           // cut off
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

    // ---- voice typing / assistant: "mic doesn't pause the music" ----
    @Test public void micAction_truthTable() {
        // (newHolder, playedBefore, keepOn) -> 0 = nothing, 1 = only arm (press play once the holder is gone), 2 = press play at once and arm
        assertEquals(0, AppRoute.micAction(false, false, false));
        assertEquals(0, AppRoute.micAction(false, false, true));
        assertEquals(0, AppRoute.micAction(false, true, false));    // the holder was already there on the last tick: never twice for one holder
        assertEquals(0, AppRoute.micAction(false, true, true));
        assertEquals(0, AppRoute.micAction(true, false, false));    // the music was not playing before it showed up: nothing to bring back
        assertEquals(0, AppRoute.micAction(true, false, true));
        assertEquals(1, AppRoute.micAction(true, true, false));     // feature off (or multi audio focus off): the old behaviour
        assertEquals(2, AppRoute.micAction(true, true, true));
    }

    @Test public void micKeepOn_aFlagFileMeansOff() throws Exception {
        File f = File.createTempFile("approute", ".nomickeep");
        f.deleteOnExit();
        assertFalse(AppRoute.micKeepOn(f));                          // file there = the user turned it off
        assertTrue(f.delete());
        assertTrue(AppRoute.micKeepOn(f));                           // no file = on (the default)
        assertTrue(AppRoute.micKeepOn(new File("/nonexistent/approute.nomickeep")));
    }

    @Test public void micKeepFlag_isTheFileApprouteShWrites() throws Exception {
        assertEquals("/data/local/tmp/approute.nomickeep", AppRoute.NO_MICKEEP);
        File sh = null;
        for (String p : new String[] {"src/main/assets/approute.sh", "app/src/main/assets/approute.sh"}) if (new File(p).exists()) { sh = new File(p); break; }
        assertNotNull("approute.sh not found", sh);
        String text = new String(Files.readAllBytes(sh.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("D=/data/local/tmp;") && text.contains("$D/approute.nomickeep"));   // same directory, same file name as the router looks at
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
