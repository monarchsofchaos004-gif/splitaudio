import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * The control port on real loopback sockets: AppRoute.serve (the router's side) against the app's own client code
 * (app.splitaudio.Route.exchange) and the helper's own "ask" client (AppRoute.askRouter). The stand-in router listens on a free port and
 * hands every connection to AppRoute.serve with the owner uid and token given by the test. The uid check reads this machine's
 * /proc/net/tcp*; where that does not work (not Linux, a sandbox) the tests that need it skip themselves.
 */
public class ControlChannelTest {
    private static final String TOK = "0123456789abcdef0123456789abcdef";
    private static final String OTHER = "fedcba9876543210fedcba9876543210";
    private final List<ServerSocket> open = new ArrayList<>();

    @After public void closeServers() throws Exception { for (ServerSocket s : open) s.close(); }

    /** A router stand-in; returns its port. A null token means "no usable token": every client is refused. As in the real router, a client
     *  that cannot be found in /proc/net/tcp* is only refused where the lookup works at all. */
    private int server(int owner, String token) throws Exception { return server(owner, token, uidLookupWorks(), -1); }

    /** fakePort >= 0: the router looks that source port up instead of the client's real one (a client it cannot find) */
    private int server(final int owner, final String token, final boolean enforced, final int fakePort) throws Exception {
        final ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        open.add(ss);
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    final Socket s = ss.accept();
                    final Socket seen = fakePort >= 0 ? new OtherPort(s, fakePort) : s;
                    Thread h = new Thread(() -> AppRoute.serve(seen, owner, token, enforced));
                    h.setDaemon(true); h.start();
                }
            } catch (Exception e) { /* closed by the test */ }
        });
        t.setDaemon(true); t.start();
        return ss.getLocalPort();
    }

    /** An accepted socket that reports another source port, so the uid lookup cannot find it (everything else is the real connection) */
    private static final class OtherPort extends Socket {
        private final Socket real; private final int port;
        OtherPort(Socket real, int port) { this.real = real; this.port = port; }
        @Override public int getPort() { return port; }
        @Override public int getLocalPort() { return real.getLocalPort(); }
        @Override public java.io.InputStream getInputStream() throws java.io.IOException { return real.getInputStream(); }
        @Override public java.io.OutputStream getOutputStream() throws java.io.IOException { return real.getOutputStream(); }
        @Override public void setSoTimeout(int t) throws java.net.SocketException { real.setSoTimeout(t); }
        @Override public void shutdownOutput() throws java.io.IOException { real.shutdownOutput(); }
        @Override public synchronized void close() throws java.io.IOException { real.close(); }
    }

    /** The uid this test process runs as (the filesystem uid, which is what its sockets are stamped with); -1 when unreadable */
    private static int myUid() {
        try {
            for (String l : Files.readAllLines(new File("/proc/self/status").toPath())) {
                if (!l.startsWith("Uid:")) continue;
                String[] f = l.substring(4).trim().split("\\s+");
                return Integer.parseInt(f[f.length > 3 ? 3 : 1]);
            }
        } catch (Throwable e) { /* not Linux */ }
        return -1;
    }

    /** Can the router find out who is on the other end here? (real loopback connection, looked up in /proc/net/tcp*) */
    private static boolean uidLookupWorks() throws Exception {
        try (ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
             Socket c = new Socket("127.0.0.1", ss.getLocalPort());
             Socket s = ss.accept()) {
            return AppRoute.peerUid(s) == myUid() && myUid() >= 0;
        }
    }

    /** Sends the lines, closes the sending side, returns the first line of the answer (null when the router just hangs up) */
    private static String talk(int port, String... lines) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(3000);
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            s.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            s.shutdownOutput();
            return new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
        }
    }

    @Test public void clientAndHelperUseTheSamePort() { assertEquals(AppRoute.VOL_PORT, app.splitaudio.Route.PORT); }

    @Test public void goodToken_fromTheOwner_getsTheCommandsAnswered() throws Exception {
        int port = server(myUid(), TOK);
        assertEquals("off", talk(port, "auth " + TOK, "state"));                  // no audio rule in a test
        assertEquals("legacy", talk(port, "auth " + TOK, "mode"));
        assertEquals("pass", talk(port, "auth " + TOK, "vol up com.example.video"));
        assertEquals("err", talk(port, "auth " + TOK, "reboot"));                // unknown command
        assertEquals("err", talk(port, "auth " + TOK));                          // good token, then nothing
    }

    @Test public void wrongToken_isDenied() throws Exception {
        int port = server(myUid(), TOK);
        assertEquals("denied", talk(port, "auth " + OTHER, "state"));
        assertEquals("denied", talk(port, "auth " + TOK.substring(1), "state"));   // one character short
        assertEquals("denied", talk(port, "auth " + TOK + "0", "state"));          // one character too many
        assertEquals("denied", talk(port, "auth " + TOK.toUpperCase(), "state"));
        assertEquals("denied", talk(port, "auth ", "state"));
    }

    @Test public void noAuthLine_isDenied_andTheCommandNeverRuns() throws Exception {
        int port = server(myUid(), TOK);
        assertEquals("denied", talk(port, "state"));
        assertEquals("denied", talk(port, "vol up com.example.video"));
        assertEquals("denied", talk(port, "state", "auth " + TOK));                // wrong order
        assertEquals("denied", talk(port));                                        // connects, says nothing
    }

    @Test public void aRouterWithoutAToken_refusesEveryone() throws Exception {
        int port = server(myUid(), null);
        assertEquals("denied", talk(port, "auth " + TOK, "state"));
        assertEquals("denied", talk(port, "auth ", "state"));
    }

    @Test public void aTooLongFirstLine_isDenied() throws Exception {
        int port = server(myUid(), TOK);
        StringBuilder sb = new StringBuilder("auth ");
        for (int i = 0; i < 5000; i++) sb.append('a');
        String r;
        try { r = talk(port, sb.toString(), "state"); } catch (java.io.IOException e) { r = "denied"; }   // a reset instead of the answer is a refusal too
        assertEquals("denied", r);
    }

    @Test public void peerUid_isTheUidOfTheConnectingProcess() throws Exception {
        assumeTrue("/proc/net/tcp* cannot be used to find the uid here", uidLookupWorks());
        try (ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
             Socket c = new Socket("127.0.0.1", ss.getLocalPort());
             Socket s = ss.accept()) {
            assertEquals(myUid(), AppRoute.peerUid(s));
        }
    }

    @Test public void rightToken_butAnotherUid_isDenied() throws Exception {
        int uid = myUid();
        assumeTrue("root and the shell user are always let in", uid > 0 && uid != 2000);
        assumeTrue("/proc/net/tcp* cannot be used to find the uid here", uidLookupWorks());
        assertEquals("denied", talk(server(uid + 1, TOK), "auth " + TOK, "state"));   // router started for some other app
        assertEquals("denied", talk(server(-1, TOK), "auth " + TOK, "state"));        // router started without an owner: shell and root only
        assertEquals("off", talk(server(uid, TOK), "auth " + TOK, "state"));          // the owner itself is let in
    }

    @Test public void aClientThatCannotBeFound_isRefusedWhereTheLookupWorks_andLetInWhereItDoesNot() throws Exception {
        // source port 1 is in nobody's table, like a client that hung up before the router looked it up
        assertEquals("denied", talk(server(myUid(), TOK, true, 1), "auth " + TOK, "state"));
        assertEquals("off", talk(server(myUid(), TOK, false, 1), "auth " + TOK, "state"));         // lookup unusable on this device: the token decides
        assertEquals("denied", talk(server(myUid(), TOK, false, 1), "auth " + OTHER, "state"));    // ...but the token is always needed
    }

    @Test public void selfTest_findsTheRoutersOwnConnection() throws Exception {
        assumeTrue("/proc/net/tcp* cannot be used to find the uid here", new File("/proc/net/tcp").canRead() && myUid() >= 0);
        try (ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            assertTrue(AppRoute.selfTestUidLookup(ss));
            assertEquals("the self test leaves the server socket usable", 0, ss.getSoTimeout());
        }
    }

    @Test public void aSilentConnection_doesNotBlockTheOthers() throws Exception {
        int port = server(myUid(), TOK);
        try (Socket idle = new Socket("127.0.0.1", port)) {
            long t0 = System.currentTimeMillis();
            assertEquals("off", talk(port, "auth " + TOK, "state"));
            assertTrue("answered while another client keeps silent", System.currentTimeMillis() - t0 < 1200);
        }
    }

    // ---- the app's client code (Route.exchange / Route.answerOf) against the real router code ----
    @Test public void theAppsClient_getsAnswersAndDenials() throws Exception {
        app.splitaudio.Route route = app.splitaudio.Route.INSTANCE;
        int port = server(myUid(), TOK);
        assertEquals("off", route.exchange(TOK, "state", 2000, port));
        assertEquals("denied", route.exchange(OTHER, "state", 2000, port));
        app.splitaudio.Route.Answer ok = route.answerOf(route.exchange(TOK, "mode", 2000, port));
        assertEquals("legacy", ok.getReply());
        app.splitaudio.Route.Answer no = route.answerOf(route.exchange(OTHER, "mode", 2000, port));
        assertNull(no.getReply());
        assertEquals(app.splitaudio.Route.DENIED, no.getError());
        assertEquals("", route.controlProblem(route.answerOf(route.exchange(TOK, "state", 2000, port))));
        assertTrue(route.controlProblem(route.answerOf(route.exchange(OTHER, "state", 2000, port))).contains("denied"));
    }

    @Test public void theAppsClient_refusesToSendMoreThanOneLine() throws Exception {
        int port = server(myUid(), TOK);
        try { app.splitaudio.Route.INSTANCE.exchange(TOK, "state\nvol up x", 2000, port); fail("a second line must not be smuggled in"); }
        catch (IllegalArgumentException expected) { /* fine */ }
    }

    @Test public void theAppsClient_failsLoudlyWhenNothingListens() throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }   // a free port, closed again
        try { app.splitaudio.Route.INSTANCE.exchange(TOK, "state", 500, port); fail("connection refused expected"); }
        catch (Exception expected) { /* java.net.ConnectException */ }
    }

    // ---- the helper's own client ("approute.sh ask state") ----
    @Test public void theHelpersAskClient_usesTheSameProtocol() throws Exception {
        int port = server(myUid(), TOK);
        assertEquals("off", AppRoute.askRouter(port, TOK, "state"));
        assertEquals("denied", AppRoute.askRouter(port, OTHER, "state"));
    }
}
