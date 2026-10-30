// AppRoute: keep the music app on Bluetooth, send every other app's audio to the phone speaker.
// How: a process running as the shell user holds MODIFY_AUDIO_ROUTING, so it can register a system
//   AudioPolicy with RENDER mixes whose rule is RULE_MATCH_UID (the listed app uids) -> built-in speaker.
//   Some OEM ROMs ignore an app's own setPreferredDevice(), but a system routing rule is honoured.
// The rule is only installed while Bluetooth is connected AND the music app is playing (kept 15 s after
//   the music stops so track changes don't flicker). Otherwise it is removed and audio behaves normally.
// While another app is in a call / voice chat (audio mode not NORMAL, e.g. a camera app's talk button), Android sends
//   media to the earpiece. Multi audio focus keeps the music playing there, so the music is paused for the duration
//   and resumed when the mode is back to NORMAL (only if this process paused it).
// Voice typing / assistant mic (transient focus): the music app pauses itself; with multi audio focus Android never hands
//   focus back, so the helper presses play once the transient holder is gone (focusWait, 0.3.1).
// Exits by itself after Bluetooth has been gone 2 min, or the music app has been silent 10 min.
//   If this process dies, the rule dies with it -> everything falls back to Bluetooth (never silence).
// Custom mode (0.3): "AppRoute map <file>" reads per-app outputs instead (see mapMain).
// Per-app volume (0.4): "AppRoute mixer <file>" keeps each listed app at its own volume, 0 = mute (see mixerMain);
//   "AppRoute players" prints the uids that are playing right now (for the app's list).
// Usage: CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute <musicUid,musicUid> <uid> [uid...]
//        CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute map /data/local/tmp/approute.map
//        CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute multifocus on|off|state
//        CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute ask "<command>"   (asks a running router as the shell user)
// Control channel (0.5): a running router listens on the abstract local socket "app.splitaudio.route" (nothing on the network).
//   Every connection is checked with the uid of the process on the other end: only the app that started the router
//   (env SPLITAUDIO_UID), the shell user (2000) and root answer, everybody else gets "denied".
import android.media.*;
import android.os.Looper;
import java.lang.reflect.*;

public class AppRoute {
    static Object policy = null;
    static boolean pausedForCall = false;   // music paused by us because another app entered a call / voice chat
    static int[] uids;
    static final java.util.HashSet<Integer> routed = new java.util.HashSet<>();

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("multifocus")) { multiFocusCmd(a.length > 1 ? a[1] : "state"); return; }
        if (a.length > 1 && a[0].equals("map")) { mapMain(a[1]); return; }
        if (a.length > 1 && a[0].equals("mixer")) { mixerMain(a[1]); return; }
        if (a.length > 0 && a[0].equals("players")) { for (int u : playingUids()) System.out.println(u); return; }
        if (a.length > 1 && a[0].equals("ask")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < a.length; i++) sb.append(i > 1 ? " " : "").append(a[i]);
            askMain(sb.toString());
            return;
        }
        Looper.prepareMainLooper();
        java.util.HashSet<Integer> music = new java.util.HashSet<>();
        for (String m : a[0].split(",")) if (!m.isEmpty()) music.add(Integer.parseInt(m));
        uids = new int[a.length - 1];
        for (int i = 1; i < a.length; i++) uids[i - 1] = Integer.parseInt(a[i]);
        for (int u : uids) routed.add(u);
        log("start music=" + music + " video=" + java.util.Arrays.toString(uids));
        startServers();
        long now = System.currentTimeMillis(), lastMusic = 0, lastBt = now, startAt = now;
        while (true) {
            now = System.currentTimeMillis();
            AudioDeviceInfo spk = null; boolean bt = false;
            for (AudioDeviceInfo d : outputs()) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) spk = d;
                if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.getType() == 26 /* BLE_HEADSET */ || d.getType() == 27 /* BLE_SPEAKER */) bt = true;
            }
            if (bt) lastBt = now;
            boolean call = inCall();
            if (call && !pausedForCall && bt && musicPlaying(music)) {   // e.g. a camera app's talk button: media would go to the earpiece
                sh("cmd media_session dispatch pause"); pausedForCall = true; log("call/voice mode -> music paused");
            }
            if (!call && pausedForCall) {
                pausedForCall = false;
                if (!musicPlaying(music)) { sh("cmd media_session dispatch play"); log("call/voice mode over -> music resumed"); }
            }
            if (musicPlaying(music) || pausedForCall || focusArmed) lastMusic = now;   // a long call must not end the helper
            boolean want = bt && spk != null && now - lastMusic < 15000 && !call;   // drop the rule during calls
            if (want && policy == null) { try { register(spk); log("music on bt -> rule on"); } catch (Throwable e) { log("register fail " + e); policy = null; } }
            if (policy != null) multiFocusOnce(music);
            if (!want && policy != null) { unregister(); log((!bt ? "bt off" : call ? "call/voice mode" : "music stopped") + " -> rule off"); }
            if (now - lastBt > 120000 || now - Math.max(lastMusic, startAt) > 600000) {
                if (policy != null) unregister();
                log(now - lastBt > 120000 ? "bt gone 2min -> exit" : "no music 10min -> exit");
                System.exit(0);
            }
            focusWait(music, 2000);
        }
    }

    // Per-app volume: the file (written by approute.sh mixer from the app's choices) has lines "uid=0.35" (0 = mute).
    //   Every second each player of a listed uid gets PlayerProxy.setVolume(v) (a system API; the shell user holds the
    //   MODIFY_AUDIO_ROUTING it needs). An app may set its own player volume later, so values are re-sent every 5 s.
    //   A player whose app was taken off the list goes back to 1.0. When the file is gone or empty every touched player
    //   is set back to 1.0 and the process exits (approute.sh mixstop deletes the file).
    static java.util.List<?> playbackConfigs() throws Exception {
        Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
            .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
        return (java.util.List<?>) as.getClass().getMethod("getActivePlaybackConfigurations").invoke(as);
    }

    static java.util.TreeSet<Integer> playingUids() {
        java.util.TreeSet<Integer> s = new java.util.TreeSet<>();
        try {
            for (Object c : playbackConfigs())
                if ((Integer) c.getClass().getMethod("getPlayerState").invoke(c) == 2 /* STARTED */)
                    s.add((Integer) c.getClass().getMethod("getClientUid").invoke(c));
        } catch (Throwable e) { log("players err " + e); }
        return s;
    }

    static java.util.HashMap<Integer, Float> readVol(java.io.File f) {
        java.util.HashMap<Integer, Float> m = new java.util.HashMap<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            for (String l; (l = r.readLine()) != null; ) {
                int e = l.indexOf('=');
                if (e < 0) continue;
                try {
                    float v = Math.max(0f, Math.min(1f, Float.parseFloat(l.substring(e + 1).trim())));
                    if (v < 0.995f) m.put(Integer.parseInt(l.substring(0, e).trim()), v);
                } catch (NumberFormatException x) {}
            }
        } catch (Throwable e) { log("vol read fail " + e); }
        return m;
    }

    static boolean setPlayerVolume(Object px, float v) {
        try { px.getClass().getMethod("setVolume", float.class).invoke(px, v); return true; } catch (Throwable e) { return false; }
    }

    static void mixerMain(String path) throws Exception {
        java.io.File f = new java.io.File(path);
        java.util.HashMap<Integer, Float> want = new java.util.HashMap<>();
        java.util.HashMap<Integer, Float> applied = new java.util.HashMap<>();   // player id -> volume we set
        java.util.HashMap<Integer, Object> proxies = new java.util.HashMap<>();
        long mtime = -1, lastFull = 0;
        log("mixer start");
        while (f.exists()) {
            if (f.lastModified() != mtime) {
                mtime = f.lastModified(); want = readVol(f); lastFull = 0;
                log("mixer " + want);
                if (want.isEmpty()) break;
            }
            long now = System.currentTimeMillis(); boolean full = now - lastFull > 5000;
            if (full) lastFull = now;
            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            try {
                for (Object c : playbackConfigs()) {
                    Class<?> k = c.getClass();
                    int uid = (Integer) k.getMethod("getClientUid").invoke(c);
                    int piid = (Integer) k.getMethod("getPlayerInterfaceId").invoke(c);
                    seen.add(piid);
                    Float v = want.get(uid), a = applied.get(piid);
                    if (v == null && a == null) continue;
                    float target = v == null ? 1f : v;
                    if (a != null && a == target && !full) continue;
                    Object px = k.getMethod("getPlayerProxy").invoke(c);
                    if (px == null || !setPlayerVolume(px, target)) continue;
                    if (v == null) { applied.remove(piid); proxies.remove(piid); } else { applied.put(piid, target); proxies.put(piid, px); }
                }
            } catch (Throwable e) { log("mixer err " + e); }
            applied.keySet().retainAll(seen); proxies.keySet().retainAll(seen);
            Thread.sleep(1000);
        }
        for (Object px : proxies.values()) setPlayerVolume(px, 1f);   // everything back to normal before leaving
        log("mixer exit (" + proxies.size() + " players reset)");
        System.exit(0);
    }

    // Custom mode: each app picks its output. The map file (written by approute.sh startmap from the app's choices)
    //   has one line per target: "speaker:uid,uid" / "bt:uid,..." / "wired:uid,...". Apps not listed follow the system.
    //   A target whose device isn't connected gets no mix, so its apps follow the system too (never silence).
    //   The rule is on only while a listed app played in the last 15 s and no call is going on; it is rebuilt when the
    //   set of connected targets changes or the map file changes. Exits after no listed app played for 10 min.
    static final String[] TARGETS = {"speaker", "bt", "wired"};
    static final java.util.HashMap<String, int[]> map = new java.util.HashMap<>();
    static boolean mapMode = false;

    static String target(int type) {
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return "speaker";
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == 26 /* BLE_HEADSET */ || type == 27 /* BLE_SPEAKER */) return "bt";
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE) return "wired";
        return null;
    }

    static void readMap(java.io.File f) {
        map.clear(); routed.clear();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            for (String l; (l = r.readLine()) != null; ) {
                int c = l.indexOf(':');
                if (c < 0) continue;
                String t = l.substring(0, c).trim();
                java.util.ArrayList<Integer> u = new java.util.ArrayList<>();
                for (String s : l.substring(c + 1).split(",")) try { if (!s.trim().isEmpty()) u.add(Integer.parseInt(s.trim())); } catch (NumberFormatException e) {}
                if (u.isEmpty() || java.util.Arrays.asList(TARGETS).indexOf(t) < 0) continue;
                int[] arr = new int[u.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = u.get(i);
                map.put(t, arr);
                if (t.equals("speaker")) routed.addAll(u);   // volume keys: only speaker-routed apps change the speaker volume
            }
        } catch (Throwable e) { log("map read fail " + e); }
        StringBuilder sb = new StringBuilder();
        for (String t : TARGETS) if (map.containsKey(t)) sb.append(t).append('=').append(map.get(t).length).append(' ');
        log("map " + sb.toString().trim());
    }

    static java.util.HashSet<Integer> uidSet(String... targets) {
        java.util.HashSet<Integer> s = new java.util.HashSet<>();
        for (String t : targets) if (map.containsKey(t)) for (int u : map.get(t)) s.add(u);
        return s;
    }

    static void mapMain(String path) throws Exception {
        Looper.prepareMainLooper();
        mapMode = true;
        java.io.File f = new java.io.File(path);
        long mtime = -1; String regKey = "";
        startServers();
        long now = System.currentTimeMillis(), lastPlay = 0, startAt = now;
        while (true) {
            now = System.currentTimeMillis();
            if (f.lastModified() != mtime) { mtime = f.lastModified(); readMap(f); if (policy != null) unregister(); regKey = ""; }
            java.util.HashSet<Integer> all = uidSet(TARGETS), onBt = uidSet("bt");
            java.util.HashMap<String, AudioDeviceInfo> dev = new java.util.HashMap<>();
            for (AudioDeviceInfo d : outputs()) { String t = target(d.getType()); if (t != null && !dev.containsKey(t)) dev.put(t, d); }
            boolean call = inCall();
            // same as Simple mode: apps put on Bluetooth would follow a call/voice chat to the earpiece -> pause them meanwhile
            if (call && !pausedForCall && dev.containsKey("bt") && !onBt.isEmpty() && musicPlaying(onBt)) {
                sh("cmd media_session dispatch pause"); pausedForCall = true; log("call/voice mode -> music paused");
            }
            if (!call && pausedForCall) {
                pausedForCall = false;
                if (!musicPlaying(onBt)) { sh("cmd media_session dispatch play"); log("call/voice mode over -> music resumed"); }
            }
            if ((!all.isEmpty() && musicPlaying(all)) || pausedForCall || focusArmed) lastPlay = now;
            StringBuilder key = new StringBuilder();
            java.util.LinkedHashMap<AudioDeviceInfo, int[]> targets = new java.util.LinkedHashMap<>();
            for (String t : TARGETS) if (dev.containsKey(t) && map.containsKey(t)) {
                key.append(t).append('=').append(dev.get(t).getId()).append(';');
                targets.put(dev.get(t), map.get(t));
            }
            boolean want = !targets.isEmpty() && now - lastPlay < 15000 && !call;
            if (want && (policy == null || !key.toString().equals(regKey))) {
                if (policy != null) unregister();
                try { registerAll(targets); regKey = key.toString(); log("rule on " + regKey); }
                catch (Throwable e) { log("register fail " + e); policy = null; regKey = ""; }
            }
            if (policy != null) multiFocusOnce(all);
            if (!want && policy != null) { unregister(); regKey = ""; log((call ? "call/voice mode" : targets.isEmpty() ? "no target device" : "listed apps stopped") + " -> rule off"); }
            if (now - Math.max(lastPlay, startAt) > 600000) {
                if (policy != null) unregister();
                log("no listed app played 10min -> exit");
                System.exit(0);
            }
            focusWait(all, 2000);
        }
    }

    // Voice typing / assistant (e.g. Gboard's mic) takes transient focus and the music app pauses. With multi audio focus on,
    //   Android never gives focus back to apps in its multi-focus list (they keep LOSS_TRANSIENT, see dumpsys audio), so the
    //   music stays paused after the mic closes. Between ticks, watch the focus stack every 250 ms; if the music was playing
    //   right before the transient holder appeared and is still paused 1 s after it is gone, press play.
    static boolean focusArmed = false, holderSeen = false, playedBefore = false, focusErrLogged = false;
    static void focusWait(java.util.Set<Integer> music, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        do {
            String holder = transientHolder(music);
            if (holder != null && !holderSeen && playedBefore) { focusArmed = true; log(holder + " took transient focus -> resume music after it"); }
            if (holder == null) {
                if (focusArmed) {
                    focusArmed = false; Thread.sleep(1000);
                    if (!musicPlaying(music)) { sh("cmd media_session dispatch play"); log("transient focus over -> music resumed"); }
                }
                playedBefore = !music.isEmpty() && musicPlaying(music);
            }
            holderSeen = holder != null;
            Thread.sleep(250);
        } while (System.currentTimeMillis() < end);
    }

    // Package of a non-music app holding transient focus (GAIN_TRANSIENT / _MAY_DUCK / _EXCLUSIVE), or null
    static String transientHolder(java.util.Set<Integer> music) {
        try {
            Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
            java.util.List<?> l = (java.util.List<?>) as.getClass().getMethod("getFocusStack").invoke(as);
            for (Object f : l) {
                int g = (Integer) f.getClass().getMethod("getGainRequest").invoke(f);
                int uid = (Integer) f.getClass().getMethod("getClientUid").invoke(f);
                if (g >= 2 && g <= 4 && !music.contains(uid)) return (String) f.getClass().getMethod("getPackageName").invoke(f);
            }
        } catch (Throwable e) { if (!focusErrLogged) { focusErrLogged = true; log("focus stack err " + e); } }
        return null;
    }

    // Is a music app playing right now? (with MODIFY_AUDIO_ROUTING we can see the uid of every player)
    static boolean musicPlaying(java.util.Set<Integer> music) {
        try {
            Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
            java.util.List<?> l = (java.util.List<?>) as.getClass().getMethod("getActivePlaybackConfigurations").invoke(as);
            for (Object c : l) {
                int uid = (Integer) c.getClass().getMethod("getClientUid").invoke(c);
                int st = (Integer) c.getClass().getMethod("getPlayerState").invoke(c);
                if (st == 2 /* STARTED */ && (music == null || music.contains(uid))) return true;
            }
        } catch (Throwable e) { log("musicPlaying err " + e); }
        return false;
    }

    // Multi audio focus (built into Android 11+, off by default): without it, a video in another app takes the
    //   audio focus and the music app pauses itself; routing alone can't prevent that. It is only switched on by itself
    //   after the user said yes in the app (the app asks first): "multifocus on" writes MF_OK, "multifocus off" writes NO_MF
    //   (and removes MF_OK), so neither file = the user hasn't decided = leave the system setting alone.
    //   Android saves the setting, so it survives reboot. Calls and alarms still pause everything.
    //   Switching it (either way) makes Android send one focus loss to the current holder, so the music pauses
    //   once: resume it.
    static final String NO_MF = "/data/local/tmp/approute.nomf", MF_OK = "/data/local/tmp/approute.mf_ok";
    static boolean mfChecked = false;
    /** Switch it on by itself only when the user agreed (MF_OK), did not decline later (NO_MF) and it is not on already */
    static boolean mfShouldEnable(boolean okFile, boolean nomfFile, boolean alreadyOn) { return okFile && !nomfFile && !alreadyOn; }
    static void multiFocusOnce(java.util.Set<Integer> music) {
        if (mfChecked) return;
        mfChecked = true;
        if (!mfShouldEnable(new java.io.File(MF_OK).exists(), new java.io.File(NO_MF).exists(), false)) return;   // not agreed (or declined)
        if ("1".equals(sh("settings get system multi_audio_focus_enabled"))) return;   // already on
        setMultiFocus(true, music);
    }
    static void flag(String path, boolean present) {
        try { if (present) new java.io.File(path).createNewFile(); else new java.io.File(path).delete(); }
        catch (Throwable e) { log("flag " + path + " fail " + e); }
    }
    static void multiFocusCmd(String c) {
        if (c.equals("on")) { flag(NO_MF, false); flag(MF_OK, true); setMultiFocus(true, null); }
        else if (c.equals("off")) { flag(MF_OK, false); flag(NO_MF, true); setMultiFocus(false, null); }
        System.out.println("multifocus=" + ("1".equals(sh("settings get system multi_audio_focus_enabled")) ? "on" : "off"));
    }
    static void setMultiFocus(boolean on, java.util.Set<Integer> music) {
        boolean was = musicPlaying(music);
        try {
            Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
            as.getClass().getMethod("setMultiAudioFocusEnabled", boolean.class).invoke(as, on);
        } catch (Throwable e) { log("multi focus fail " + e); return; }
        sh("settings put system multi_audio_focus_enabled " + (on ? 1 : 0));
        log("multi focus " + (on ? "on" : "off"));
        for (int i = 0; was && i < 8; i++) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            if (!musicPlaying(music)) { sh("cmd media_session dispatch play"); log("music paused by the switch -> resumed"); return; }
        }
    }
    static String sh(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            String out = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream())).readLine();
            p.waitFor();
            return out == null ? "" : out.trim();
        } catch (Throwable e) { return ""; }
    }

    // Control channel and volume keys. The app (status, volume keys) talks to the running router over the abstract local socket
    //   "app.splitaudio.route": nothing on the network, so the app needs no INTERNET permission. Every connection is checked with
    //   the uid of the process on the other end (SO_PEERCRED): root, the shell user and the app that started this router
    //   (env SPLITAUDIO_UID, set by the app in the start command) are served, everybody else gets "denied" and is closed.
    //   One command per line: state -> on/off; mode -> map/legacy;
    //   vol up|down <package> -> ok <percent> / pass (app not routed to the speaker, or rule off).
    //   Debug only: with env SPLITAUDIO_TCP=1 the old plain TCP port 127.0.0.1:47821 is opened too (no peer check possible).
    static final String SOCK = "app.splitaudio.route";
    static final int VOL_PORT = 47821;   // debug only (see above)
    static final int VOL_STEPS = 15;     // the volume keys move the speaker volume in about this many steps, whatever the device's index range
    static final int OWNER_UID = parseUid(System.getenv("SPLITAUDIO_UID"));
    static int lastDeniedUid = -2;       // log a refused uid once, not on every attempt

    static int parseUid(String s) {
        try { return s == null ? -1 : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }
    /** Who may talk to the router: root, the shell user, and the app that started it (ownerUid >= 0) */
    static boolean allowed(int peerUid, int ownerUid) { return peerUid == 0 || peerUid == 2000 || (ownerUid >= 0 && peerUid == ownerUid); }
    /** Volume key step for a stream index range min..max: about VOL_STEPS steps, at least 1 index */
    static int volStep(int min, int max) { return Math.max(1, Math.round((max - min) / (float) VOL_STEPS)); }
    static int clampIdx(int idx, int step, boolean up, int min, int max) { return Math.max(min, Math.min(max, idx + (up ? step : -step))); }
    static int percent(int idx, int max) { return max <= 0 ? 0 : idx * 100 / max; }

    static void startServers() {
        Thread vt = new Thread(AppRoute::volServer); vt.setDaemon(true); vt.start();
        if ("1".equals(System.getenv("SPLITAUDIO_TCP"))) {
            Thread tt = new Thread(AppRoute::tcpServer); tt.setDaemon(true); tt.start();
            log("debug: plain TCP control port 127.0.0.1:" + VOL_PORT + " is open (no peer check)");
        }
    }

    static void volServer() {
        try (android.net.LocalServerSocket ss = new android.net.LocalServerSocket(SOCK)) {
            log("control socket @" + SOCK + (OWNER_UID >= 0 ? ", owner uid " + OWNER_UID : ", shell only"));
            while (true) {
                try (android.net.LocalSocket s = ss.accept()) {
                    int peer = s.getPeerCredentials().getUid();
                    if (!allowed(peer, OWNER_UID)) {
                        if (peer != lastDeniedUid) { lastDeniedUid = peer; log("control: denied uid " + peer); }
                        s.getOutputStream().write("denied\n".getBytes()); s.getOutputStream().flush();
                        continue;
                    }
                    s.setSoTimeout(1500);
                    String line = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream())).readLine();
                    String out = volCmd(line == null ? "" : line.trim());
                    s.getOutputStream().write((out + "\n").getBytes()); s.getOutputStream().flush();
                } catch (Throwable e) { log("control conn err " + e); }
            }
        } catch (Throwable e) { log("control server err " + e); }
    }

    static void tcpServer() {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(VOL_PORT, 8, java.net.InetAddress.getByName("127.0.0.1"))) {
            while (true) {
                try (java.net.Socket s = ss.accept()) {
                    s.setSoTimeout(1500);
                    String line = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream())).readLine();
                    String out = volCmd(line == null ? "" : line.trim());
                    s.getOutputStream().write((out + "\n").getBytes()); s.getOutputStream().flush();
                } catch (Throwable e) { log("tcp conn err " + e); }
            }
        } catch (Throwable e) { log("tcp server err " + e); }
    }

    // "AppRoute ask <command>": sends one command line to the running router as the calling user (shell) and prints the answer.
    //   For debugging and scripts (approute.sh ask state); the app connects to the socket itself.
    static void askMain(String cmd) {
        String r = null; int rc = 0;
        android.net.LocalSocket s = new android.net.LocalSocket();
        try {
            s.connect(new android.net.LocalSocketAddress(SOCK, android.net.LocalSocketAddress.Namespace.ABSTRACT));
            s.setSoTimeout(3000);
            s.getOutputStream().write((cmd + "\n").getBytes()); s.getOutputStream().flush();
            r = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream())).readLine();
        } catch (Throwable e) { r = "err " + e; rc = 2; }
        finally { try { s.close(); } catch (Throwable e) {} }
        System.out.println(r == null ? "no_reply" : r);
        if (rc != 0) System.exit(rc);
    }

    static Object audioService() throws Exception {
        return Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
            .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
    }

    // The music stream's index range differs per device (AOSP 0..15, Xiaomi 0..150, ...): ask the audio service once and keep it.
    static int volMin = 0, volMax = -1;
    static boolean volRange(Object as) {
        if (volMax > 0) return true;
        try {
            int max = (Integer) as.getClass().getMethod("getStreamMaxVolume", int.class).invoke(as, 3 /* STREAM_MUSIC */);
            int min = 0;
            try { min = (Integer) as.getClass().getMethod("getStreamMinVolume", int.class).invoke(as, 3); } catch (Throwable e) { log("no stream min volume: " + e); }
            if (max <= min) { log("odd music volume range " + min + ".." + max); return false; }
            volMin = min; volMax = max;
            log("music volume range " + volMin + ".." + volMax + ", key step " + volStep(volMin, volMax));
            return true;
        } catch (Throwable e) { log("volume range err " + e); return false; }   // unknown range: leave the keys alone (pass) instead of guessing
    }

    static String volCmd(String line) {
        String[] p = line.split(" ");
        if (p[0].equals("state")) return policy != null ? "on" : "off";
        if (p[0].equals("mode")) return mapMode ? "map" : "legacy";
        if (!p[0].equals("vol") || p.length < 3 || !(p[1].equals("up") || p[1].equals("down"))) return "err";
        if (policy == null) return "pass";
        try {
            Object pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package"));
            int uid = -1;
            for (Method m : pm.getClass().getMethods())
                if (m.getName().equals("getPackageUid") && m.getParameterTypes().length == 3) {
                    uid = (Integer) m.invoke(pm, p[2], m.getParameterTypes()[1] == long.class ? (Object) 0L : (Object) 0, 0); break; }
            if (!routed.contains(uid)) return "pass";
            Object as = audioService();
            if (!volRange(as)) return "pass";
            Class<?> ada = Class.forName("android.media.AudioDeviceAttributes"), vic = Class.forName("android.media.VolumeInfo"), vib = Class.forName("android.media.VolumeInfo$Builder");
            Object spk = ada.getConstructor(int.class, int.class, String.class).newInstance(2, 2 /* built-in speaker */, "");
            Object q = vib.getMethod("build").invoke(vib.getConstructor(int.class).newInstance(3 /* STREAM_MUSIC */));
            Object cur = as.getClass().getMethod("getDeviceVolume", vic, ada, String.class).invoke(as, q, spk, "com.android.shell");
            int idx = (Integer) vic.getMethod("getVolumeIndex").invoke(cur);
            idx = clampIdx(idx, volStep(volMin, volMax), p[1].equals("up"), volMin, volMax);
            Object b = vib.getConstructor(int.class).newInstance(3);
            vib.getMethod("setVolumeIndex", int.class).invoke(b, idx);
            vib.getMethod("setMinVolumeIndex", int.class).invoke(b, volMin);
            vib.getMethod("setMaxVolumeIndex", int.class).invoke(b, volMax);
            as.getClass().getMethod("setDeviceVolume", vic, ada, String.class).invoke(as, vib.getMethod("build").invoke(b), spk, "com.android.shell");
            return "ok " + percent(idx, volMax);
        } catch (Throwable e) { log("vol err " + e); return "pass"; }
    }

    // In a call (phone or VoIP) = audio mode is not NORMAL (0)
    static boolean inCall() {
        try {
            Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
            return ((Integer) as.getClass().getMethod("getMode").invoke(as)) != 0;
        } catch (Throwable e) { return false; }
    }

    static AudioDeviceInfo[] outputs() throws Exception {
        return (AudioDeviceInfo[]) AudioManager.class.getMethod("getDevicesStatic", int.class).invoke(null, AudioManager.GET_DEVICES_OUTPUTS);
    }

    // Native AudioPolicy keeps at most 20 criteria per mix (MAX_CRITERIA_PER_MIX); extra uids are silently
    //   dropped, and with ~100+ the whole mix is ignored. So: 20 uids per mix, up to 50 mixes (1000 uids),
    //   all in one policy. Verify with a normal app's playback, not a shell-uid test track.
    static final int CHUNK = 20;
    static void register(AudioDeviceInfo spk) throws Exception {   // Simple mode: every listed uid -> speaker
        java.util.LinkedHashMap<AudioDeviceInfo, int[]> m = new java.util.LinkedHashMap<>();
        m.put(spk, uids);
        registerAll(m);
    }

    // One policy, one or more RENDER mixes per output device (the 50-mix budget is shared across devices).
    static void registerAll(java.util.Map<AudioDeviceInfo, int[]> targets) throws Exception {
        Class<?> pb = Class.forName("android.media.audiopolicy.AudioPolicy$Builder");
        Object pbi = pb.getConstructor(android.content.Context.class).newInstance((Object) null);
        int mixes = 0, total = 0;
        for (java.util.Map.Entry<AudioDeviceInfo, int[]> e : targets.entrySet()) {
            int[] u = e.getValue();
            int n = Math.min(u.length, CHUNK * (50 - mixes));   // max 50 mixes per policy
            if (n < u.length) log("too many apps, routing first " + n + " of " + u.length);
            for (int i = 0; i < n; i += CHUNK) {
                Class<?> rb = Class.forName("android.media.audiopolicy.AudioMixingRule$Builder");
                Object rbi = rb.getConstructor().newInstance();
                for (int k = i; k < Math.min(n, i + CHUNK); k++)
                    rb.getMethod("addMixRule", int.class, Object.class).invoke(rbi, 4 /* RULE_MATCH_UID */, Integer.valueOf(u[k]));
                Object rule = rb.getMethod("build").invoke(rbi);
                Class<?> mb = Class.forName("android.media.audiopolicy.AudioMix$Builder");
                Object mbi = mb.getConstructor(Class.forName("android.media.audiopolicy.AudioMixingRule")).newInstance(rule);
                mb.getMethod("setFormat", AudioFormat.class).invoke(mbi, new AudioFormat.Builder().setSampleRate(48000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build());
                mb.getMethod("setRouteFlags", int.class).invoke(mbi, 1 /* ROUTE_FLAG_RENDER */);
                mb.getMethod("setDevice", AudioDeviceInfo.class).invoke(mbi, e.getKey());
                pb.getMethod("addMix", Class.forName("android.media.audiopolicy.AudioMix")).invoke(pbi, mb.getMethod("build").invoke(mbi));
                mixes++;
            }
            total += n;
        }
        if (mixes == 0) throw new RuntimeException("no mixes");
        log("mixes=" + mixes + " uids=" + total);
        Object p = pb.getMethod("build").invoke(pbi);
        Method reg = AudioManager.class.getDeclaredMethod("registerAudioPolicyStatic", Class.forName("android.media.audiopolicy.AudioPolicy"));
        reg.setAccessible(true);
        int r = (Integer) reg.invoke(null, p);
        if (r != 0) throw new RuntimeException("registerAudioPolicy=" + r);
        policy = p;
    }

    static void unregister() {
        try {
            Method un = AudioManager.class.getDeclaredMethod("unregisterAudioPolicyAsyncStatic", Class.forName("android.media.audiopolicy.AudioPolicy"));
            un.setAccessible(true); un.invoke(null, policy);
        } catch (Throwable e) { log("unregister fail " + e); }
        policy = null;
    }

    static void log(String s) { System.out.println(new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date()) + " " + s); }
}
