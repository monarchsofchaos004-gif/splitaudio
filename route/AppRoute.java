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
//   focus back, so the helper presses play once the transient holder is gone (focusWait, 0.3.1). 0.5: unless the user turned
//   "mic doesn't pause the music" off (flag file approute.nomickeep), it presses play 0.3 s after the holder showed up instead.
// Exits by itself after Bluetooth has been gone 2 min, or the music app has been silent 10 min.
//   If this process dies, the rule dies with it -> everything falls back to Bluetooth (never silence).
// Custom mode (0.3): "AppRoute map <file>" reads per-app outputs instead (see mapMain).
// Per-app volume (0.4): "AppRoute mixer <file>" keeps each listed app at its own volume, 0 = mute (see mixerMain);
//   "AppRoute players" prints the uids that are playing right now (for the app's list).
// Usage: CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute <musicUid,musicUid> <uid> [uid...]
//        CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute map /data/local/tmp/approute.map
//        CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute multifocus on|off|state
//        CLASSPATH=/data/local/tmp/approute.dex app_process /system/bin AppRoute ask "<command>"   (asks a running router as the shell user)
// Control channel (0.5): a running router listens on 127.0.0.1:47821 (TCP, loopback only). Every connection must start with
//   "auth <token>" (the random token the app made, env SPLITAUDIO_TOKEN) and must come from the app that started the router
//   (env SPLITAUDIO_UID), the shell user (2000) or root (looked up in /proc/net/tcp); everybody else gets "denied".
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
        if (a.length > 0 && a[0].equals("diag")) { diagMain(); return; }   // 0.5.7: read-only audio-focus snapshot for the one-tap report
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
            // any player counts, not only the listed apps: a listed app (e.g. a notification reader's TTS engine) that starts speaking while
            //   only an unlisted one plays must already be routed from its first word
            if (musicPlaying(null) || pausedForCall || focusArmed) lastPlay = now;
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
            if (!want && policy != null) { unregister(); regKey = ""; log((call ? "call/voice mode" : targets.isEmpty() ? "no target device" : "nothing playing") + " -> rule off"); }
            if (now - Math.max(lastPlay, startAt) > 600000) {
                if (policy != null) unregister();
                log("nothing played 10min -> exit");
                System.exit(0);
            }
            focusWait(all, 2000);
        }
    }

    // Voice typing / assistant (e.g. Gboard's mic) takes transient focus and the music app pauses. With multi audio focus on,
    //   Android never gives focus back to apps in its multi-focus list (they keep LOSS_TRANSIENT, see dumpsys audio), so the
    //   music stays paused after the mic closes. Between ticks, watch the focus stack every 250 ms; if the music was playing
    //   right before the transient holder appeared and is still paused 1 s after it is gone, press play.
    // "Mic doesn't pause the music" (0.5, on unless the flag file approute.nomickeep exists; the file is looked at each time, so
    //   switching it needs no restart): with multi audio focus on, a music app asking for focus again does not take the focus away from
    //   the mic, so both can run at once. Then play is pressed already MIC_KEEP_WAIT_MS after the holder showed up (time for the music app
    //   to finish pausing) and the music only stalls for about half a second; waiting for the holder to go stays as the safety net.
    //   Not used (= the old behaviour) while the audio mode says call / voice chat (the call rule in main() pauses the music on
    //   purpose) and while multi audio focus is off (there the music app asking again would take the focus away from the mic).
    // 0.5.8: an app can hold transient focus without using audio (an equalizer, the system UI). The music then stopped because the
    //   user paused it, and pressing play would undo that, so a holder only counts once it records or plays (holderUsesAudio).
    static boolean focusArmed = false, holderSeen = false, playedBefore = false, focusErrLogged = false;
    static boolean holderReal = false, holderDone = false, recErrLogged = false;
    static int holderUid = -1;
    static String heldBy = null;
    static final String NO_MICKEEP = "/data/local/tmp/approute.nomickeep";
    static final long MIC_KEEP_WAIT_MS = 300;
    /** The flag file is the switch: it exists = the user turned "mic doesn't pause the music" off (approute.sh mickeep off) */
    static boolean micKeepOn(java.io.File noMicKeepFile) { return !noMicKeepFile.exists(); }
    static boolean micKeepOn() { return micKeepOn(new java.io.File(NO_MICKEEP)); }
    /** What to do about a transient-focus holder: 0 = nothing, 1 = only arm (press play once the holder is gone: the old behaviour),
     *  2 = press play at once and arm as well. newHolder = a holder counts for the first time (holderCounts); playedBefore = the
     *  music was playing right before it showed up; keepOn = "mic doesn't pause the music" is on and usable. */
    static int micAction(boolean newHolder, boolean playedBefore, boolean keepOn) {
        if (!newHolder || !playedBefore) return 0;
        return keepOn ? 2 : 1;
    }
    static boolean multiFocusOn() { return "1".equals(sh("settings get system multi_audio_focus_enabled")); }
    /** A holder counts on the first tick it is there and really uses audio */
    static boolean holderCounts(boolean holderThere, boolean real, boolean done) { return holderThere && real && !done; }
    /** Recording sources that mean someone is talking to the phone: not REMOTE_SUBMIX (8, audio capture), not HOTWORD (1999, always-on wake word) */
    static boolean isVoiceSource(int source) { return source != 8 && source != 1999; }

    /** Does the holder use audio? Any voice recording counts, because voice typing can record from another package than the one
     *  holding the focus. If this cannot be read the answer is yes, which is the behaviour before 0.5.8. */
    static boolean holderUsesAudio(int uid) {
        try {
            Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
            for (Object c : (java.util.List<?>) as.getClass().getMethod("getActiveRecordingConfigurations").invoke(as))
                if (isVoiceSource((Integer) c.getClass().getMethod("getClientAudioSource").invoke(c))) return true;
        } catch (Throwable e) {
            if (!recErrLogged) { recErrLogged = true; log("recording check err " + e); }
            return true;
        }
        java.util.Set<Integer> holderOnly = new java.util.HashSet<>();
        holderOnly.add(uid);
        return musicPlaying(holderOnly);
    }

    /** micAction 2: give the music app MIC_KEEP_WAIT_MS to finish pausing for the focus loss, then press play if it is not playing */
    static void micKeepPlaying(java.util.Set<Integer> music, String holder) throws InterruptedException {
        Thread.sleep(MIC_KEEP_WAIT_MS);
        if (inCall()) log(holder + " took transient focus in call/voice mode -> left to the call rule");
        else if (!musicPlaying(music)) { sh("cmd media_session dispatch play"); log(holder + " took transient focus -> music kept playing"); }
        else log(holder + " took transient focus -> music still playing");
    }

    static void focusWait(java.util.Set<Integer> music, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        do {
            String holder = transientHolder(music);
            if (holder != null && !holderSeen) { holderReal = false; holderDone = false; heldBy = holder; }
            if (holder != null && !holderReal) holderReal = holderUsesAudio(holderUid);   // asked every tick: the mic can open a moment later
            boolean counts = holderCounts(holder != null, holderReal, holderDone);
            boolean keep = counts && playedBefore && micKeepOn() && multiFocusOn();   // the file / the setting are only looked at when it matters
            int act = micAction(counts, playedBefore, keep);
            if (counts) holderDone = true;
            if (act != 0) focusArmed = true;
            if (act == 1) log(holder + " took transient focus -> resume music after it");
            if (act == 2) micKeepPlaying(music, holder);
            if (holder == null) {
                if (holderSeen && !holderReal && playedBefore) log(heldBy + " held the focus without using audio -> left alone");
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
                if (g >= 2 && g <= 4 && !music.contains(uid)) { holderUid = uid; return (String) f.getClass().getMethod("getPackageName").invoke(f); }
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

    // Control channel and volume keys. The app (status, volume keys) talks to the running router over a TCP port bound to 127.0.0.1
    //   only (not reachable from the network). Every app that has the INTERNET permission can connect to a loopback port, so each
    //   connection has to pass two checks before its command is even looked at:
    //   1. token: the first line must be "auth <token>". The token is made by the app (32 random characters, kept in its private
    //      storage) and handed over in the environment (SPLITAUDIO_TOKEN) by approute.sh, which also writes it to
    //      /data/local/tmp/approute.token (mode 0600, shell only) for "AppRoute ask".
    //   2. uid: the owner of the connecting socket (looked up by its source port in /proc/net/tcp6 and /proc/net/tcp) must be the app
    //      that started this router (env SPLITAUDIO_UID), the shell user (2000) or root. At start the router tries the lookup on a
    //      connection to itself: where that works (uidEnforced) a connection it cannot find is refused too (a client that hangs up
    //      right after sending would otherwise slip through); where it does not work at all (a ROM that hides those files from the
    //      shell user) the token alone decides, and that is logged at start.
    //   Anything else gets "denied" and is closed. After a good "auth" line one command line is read:
    //   state -> on/off; mode -> map/legacy; vol up|down <package>|@speaker -> ok <percent> / pass (app not routed to the speaker, or rule off).
    static final int VOL_PORT = 47821;   // 127.0.0.1 only
    static final int VOL_STEPS = 15;     // the volume keys move the speaker volume in about this many steps, whatever the device's index range
    static final String TOKEN_FILE = "/data/local/tmp/approute.token";
    static final int OWNER_UID = parseUid(System.getenv("SPLITAUDIO_UID"));
    static final int UID_UNKNOWN = -1, UID_AMBIGUOUS = -2;   // results of the peer uid lookup that are not a uid
    static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
    static final int MAX_CONN = 8;       // connections served at the same time; more are dropped at once
    static final int MAX_LINE = 512;     // longest line accepted from a client
    static String controlToken = null;   // what this router accepts after "auth" (null = the port is not opened at all)
    static String lastDenied = "";       // a refusal is logged once, not on every attempt
    static volatile boolean uidEnforced = false;   // the uid lookup works on this device (self test at start): a connection it cannot find is refused
    static final Object CMD_LOCK = new Object();   // commands run one at a time (a volume key press reads the volume, then sets it)
    static final java.util.concurrent.atomic.AtomicInteger activeConn = new java.util.concurrent.atomic.AtomicInteger();

    static int parseUid(String s) {
        try { return s == null ? -1 : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }
    /** Who may talk to the router: root, the shell user, and the app that started it (ownerUid >= 0) */
    static boolean allowed(int peerUid, int ownerUid) { return peerUid == 0 || peerUid == 2000 || (ownerUid >= 0 && peerUid == ownerUid); }
    /** Volume key step for a stream index range min..max: about VOL_STEPS steps, at least 1 index */
    static int volStep(int min, int max) { return Math.max(1, Math.round((max - min) / (float) VOL_STEPS)); }
    static int clampIdx(int idx, int step, boolean up, int min, int max) { return Math.max(min, Math.min(max, idx + (up ? step : -step))); }
    static int percent(int idx, int max) { return max <= 0 ? 0 : idx * 100 / max; }

    // ---- check 1: the token ----
    /** A usable token: 16..128 letters and digits (the app makes 32 hex characters). Returns it trimmed, or null when it is not usable. */
    static String validToken(String t) {
        if (t == null) return null;
        t = t.trim();
        if (t.length() < 16 || t.length() > 128) return null;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return null;
        }
        return t;
    }

    /** The token an "auth <token>" line presents (exactly one space, no other blanks); null when the line is anything else */
    static String authToken(String line) {
        if (line == null) return null;
        String l = line.trim();
        if (!l.startsWith("auth ")) return null;
        String t = l.substring(5);
        if (t.isEmpty()) return null;
        for (int i = 0; i < t.length(); i++) if (Character.isWhitespace(t.charAt(i))) return null;
        return t;
    }

    /** Same token? Compared in constant time, so the answer time does not tell how many leading characters were right. */
    static boolean tokenMatches(String presented, String expected) {
        if (presented == null || expected == null || expected.isEmpty()) return false;
        return java.security.MessageDigest.isEqual(presented.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                                   expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String readTokenFile(java.io.File f) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String l = r.readLine();
            return l == null ? null : l.trim();
        } catch (Throwable e) { return null; }
    }

    /** The token to accept: env SPLITAUDIO_TOKEN (set by approute.sh), else the token file; null when neither holds a usable one */
    static String loadToken() {
        String t = validToken(System.getenv("SPLITAUDIO_TOKEN"));
        return t != null ? t : validToken(readTokenFile(new java.io.File(TOKEN_FILE)));
    }

    // ---- check 2: the uid on the other end ----
    /** The lines of a /proc/net/tcp* file that mention the port as ":XXXX" (hex, the kernel writes capitals); empty when the file cannot be read.
     *  A phone has hundreds of sockets and this runs for every request, so everything else is dropped while reading. */
    static java.util.List<String> readLines(String path, int port) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        String up = String.format(java.util.Locale.ROOT, ":%04X", port), lo = up.toLowerCase(java.util.Locale.ROOT);
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) {
            for (String x; (x = r.readLine()) != null; ) if (x.contains(up) || x.contains(lo)) l.add(x);
        } catch (Throwable e) { /* unreadable: the caller treats it as "not found" */ }
        return l;
    }

    /** The port of a /proc/net/tcp address field ("0100007F:9C40" -> 40000); -1 when it cannot be read */
    static int hexPort(String addr) {
        int i = addr.lastIndexOf(':');
        if (i < 0) return -1;
        try { return Integer.parseInt(addr.substring(i + 1), 16); } catch (NumberFormatException e) { return -1; }
    }

    /** Is the address of a /proc/net/tcp (8 hex digits, 127.x.x.x ends in 7F) or /proc/net/tcp6 (32 digits: ::1 or ::ffff:127.x.x.x) field loopback? */
    static boolean loopback(String addr) {
        int i = addr.lastIndexOf(':');
        if (i < 0) return false;
        String ip = addr.substring(0, i).toUpperCase(java.util.Locale.ROOT);
        if (ip.length() == 8) return ip.endsWith("7F");
        if (ip.length() == 32) return ip.equals("00000000000000000000000001000000")
            || (ip.startsWith("0000000000000000FFFF0000") && ip.endsWith("7F"));
        return false;
    }

    /** The uid that owns the client end of a loopback connection, from the lines of /proc/net/tcp or /proc/net/tcp6: the entry whose local
     *  port is clientPort and whose remote port is serverPort, both ends loopback, not TIME_WAIT (those lines always say uid 0).
     *  UID_UNKNOWN when there is no such entry, UID_AMBIGUOUS when several entries disagree. */
    static int uidOfConnection(java.util.List<String> lines, int clientPort, int serverPort) {
        int found = UID_UNKNOWN;
        for (String line : lines) {
            String[] f = WHITESPACE.split(line.trim());
            if (f.length < 8 || !f[0].endsWith(":")) continue;          // the header line and anything odd
            if (f[3].equals("06")) continue;                             // TIME_WAIT
            if (hexPort(f[1]) != clientPort || hexPort(f[2]) != serverPort) continue;
            if (!loopback(f[1]) || !loopback(f[2])) continue;
            long uid;
            try { uid = Long.parseLong(f[7]); } catch (NumberFormatException e) { continue; }
            if (uid < 0 || uid > Integer.MAX_VALUE) continue;
            if (found == UID_UNKNOWN) found = (int) uid;
            else if (found != uid) return UID_AMBIGUOUS;
        }
        return found;
    }

    /** null = let the connection in, otherwise why not. peer = [peerUid] result; enforced = the lookup works on this device, so "not found" is a refusal too */
    static String uidProblem(int peer, int owner, boolean enforced) {
        if (peer == UID_UNKNOWN) return enforced ? "peer not found in /proc/net/tcp*" : null;
        if (peer == UID_AMBIGUOUS) return "peer uid ambiguous";
        return allowed(peer, owner) ? null : "uid " + peer;
    }

    /** Uid of the process on the other end of an accepted connection (IPv6 table first: Java sockets are IPv6 sockets, 127.0.0.1 shows up there as ::ffff:127.0.0.1) */
    static int peerUid(java.net.Socket s) {
        int client = s.getPort(), server = s.getLocalPort();
        int uid = uidOfConnection(readLines("/proc/net/tcp6", client), client, server);
        return uid != UID_UNKNOWN ? uid : uidOfConnection(readLines("/proc/net/tcp", client), client, server);
    }

    // ---- the server ----
    /** Can the uid lookup find a loopback connection of this very process here? (the same kind of Java socket the app uses; a few tries in case
     *  a stranger's connection is accepted instead of ours) */
    static boolean selfTestUidLookup(java.net.ServerSocket ss) {
        for (int i = 0; i < 3; i++) {
            try (java.net.Socket c = new java.net.Socket()) {
                c.connect(new java.net.InetSocketAddress("127.0.0.1", ss.getLocalPort()), 1000);
                ss.setSoTimeout(1000);
                try (java.net.Socket a = ss.accept()) {
                    if (a.getPort() == c.getLocalPort()) return peerUid(a) >= 0;
                }
            } catch (Throwable e) { return false; }
            finally { try { ss.setSoTimeout(0); } catch (Throwable e) {} }
        }
        return false;
    }

    static void startServers() {
        controlToken = loadToken();
        if (controlToken == null) { log("control port closed: no usable SPLITAUDIO_TOKEN in the environment or in " + TOKEN_FILE); return; }
        Thread vt = new Thread(AppRoute::volServer); vt.setDaemon(true); vt.start();
    }

    static void volServer() {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(VOL_PORT, 8, java.net.InetAddress.getByName("127.0.0.1"))) {
            uidEnforced = selfTestUidLookup(ss);
            log("control port 127.0.0.1:" + VOL_PORT + (OWNER_UID >= 0 ? ", owner uid " + OWNER_UID : ", shell and root only")
                + (uidEnforced ? ", uid checked in /proc/net/tcp*" : ", /proc/net/tcp* cannot be used to find the uid here: the token alone decides"));
            while (true) {
                final java.net.Socket s = ss.accept();
                if (activeConn.incrementAndGet() > MAX_CONN) { activeConn.decrementAndGet(); closeQuietly(s); continue; }
                Thread t = new Thread(() -> { try { serve(s, OWNER_UID, controlToken, uidEnforced); } finally { activeConn.decrementAndGet(); } });
                t.setDaemon(true); t.start();
            }
        } catch (Throwable e) { log("control server err " + e); }
    }

    static void closeQuietly(java.net.Socket s) { try { s.close(); } catch (Throwable e) {} }

    /** One line (up to '\n', a trailing '\r' is left to the caller's trim) read byte by byte, so nothing after it is consumed and no client
     *  can make the router buffer more than max characters. null on end of stream, timeout, or a longer line. */
    static String readLineLimited(java.io.InputStream in, int max) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) != -1; ) {
                if (c == '\n') return sb.toString();
                if (sb.length() >= max) return null;
                sb.append((char) c);
            }
        } catch (java.io.IOException e) { /* timeout or reset: treated like no line */ }
        return null;
    }

    /** Serves one control connection: token, uid, one command, one answer. owner, expected token and enforced are parameters so that tests can use their own. */
    static void serve(java.net.Socket s, int owner, String expected, boolean enforced) {
        try {
            s.setSoTimeout(1500);
            java.io.InputStream in = new java.io.BufferedInputStream(s.getInputStream(), 1024);
            String presented = authToken(readLineLimited(in, MAX_LINE));
            if (expected == null || presented == null || !tokenMatches(presented, expected)) { refuse(s, "bad or missing token"); return; }
            String why = uidProblem(peerUid(s), owner, enforced);   // only after a good token: a client without it costs no /proc reads
            if (why != null) { refuse(s, why); return; }
            String line = readLineLimited(in, MAX_LINE);
            String out;
            synchronized (CMD_LOCK) { out = volCmd(line == null ? "" : line.trim()); }
            s.getOutputStream().write((out + "\n").getBytes()); s.getOutputStream().flush();
        } catch (Throwable e) { log("control conn err " + e); }
        finally { closeQuietly(s); }
    }

    /** "denied" and goodbye. The client has usually sent its command already; closing with that still unread would turn into a reset that can
     *  swallow the answer, so the rest of what it sent (a little, for a short time) is read and thrown away first. */
    static void refuse(java.net.Socket s, String why) {
        if (!why.equals(lastDenied)) { lastDenied = why; log("control: denied (" + why + ")"); }
        try {
            s.getOutputStream().write("denied\n".getBytes()); s.getOutputStream().flush();
            s.shutdownOutput();
            s.setSoTimeout(300);
            byte[] junk = new byte[1024];
            long end = System.currentTimeMillis() + 500; int total = 0;
            while (total < 4096 && System.currentTimeMillis() < end) { int n = s.getInputStream().read(junk); if (n <= 0) break; total += n; }
        } catch (Throwable e) { /* the client is gone: nothing to do */ }
    }

    // "AppRoute ask <command>": sends one command line to the running router and prints the answer. It runs as the shell user and reads the
    //   token from the file approute.sh wrote (nobody else can read that file). For debugging and scripts (approute.sh ask state).
    static String askRouter(int port, String token, String cmd) throws java.io.IOException {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(3000);
            s.getOutputStream().write(("auth " + token + "\n" + cmd + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            return new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream())).readLine();
        }
    }

    static void askMain(String cmd) {
        String token = validToken(readTokenFile(new java.io.File(TOKEN_FILE)));
        if (token == null) { System.out.println("err no token in " + TOKEN_FILE + " (was the router started with approute.sh?)"); System.exit(2); return; }
        String r = null; int rc = 0;
        try { r = askRouter(VOL_PORT, token, cmd); } catch (Throwable e) { r = "err " + e; rc = 2; }
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
        boolean self = p[2].equals("@speaker");   // 0.5.9: the app's own screen is in front -> the keys change the speaker volume
        try {
            if (!self) {
            Object pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package"));
            int uid = -1;
            for (Method m : pm.getClass().getMethods())
                if (m.getName().equals("getPackageUid") && m.getParameterTypes().length == 3) {
                    uid = (Integer) m.invoke(pm, p[2], m.getParameterTypes()[1] == long.class ? (Object) 0L : (Object) 0, 0); break; }
            if (!routed.contains(uid)) return "pass";
            }
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

    // 0.5.7 "diag": a read-only snapshot for the one-tap report, so a "something pauses my music" report can be understood without
    //   asking questions: the audio mode, the audio focus stack (top first: package, what it asked for, what it lost) and who is playing.
    //   Nothing here changes anything on the phone. Lines are short text (package names and numbers only).
    static final int DIAG_MAX = 8;
    static void diagMain() {
        try {
            Object as = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio"));
            try { System.out.println("audio mode=" + as.getClass().getMethod("getMode").invoke(as)); }
            catch (Throwable e) { System.out.println("audio mode=? " + e.getClass().getSimpleName()); }
            try {
                java.util.List<?> l = (java.util.List<?>) as.getClass().getMethod("getFocusStack").invoke(as);
                System.out.println("focus stack=" + l.size());
                int n = 0;
                for (Object f : l) {
                    if (n++ >= DIAG_MAX) break;
                    Object loss = "?";
                    try { loss = f.getClass().getMethod("getLossReceived").invoke(f); } catch (Throwable e) { }
                    System.out.println(diagFocusLine(String.valueOf(f.getClass().getMethod("getPackageName").invoke(f)),
                        (Integer) f.getClass().getMethod("getGainRequest").invoke(f), String.valueOf(loss)));
                }
            } catch (Throwable e) { System.out.println("focus stack=? " + e.getClass().getSimpleName()); }
            try {
                java.util.List<?> l = (java.util.List<?>) as.getClass().getMethod("getActivePlaybackConfigurations").invoke(as);
                int n = 0;
                for (Object c : l) {
                    if ((Integer) c.getClass().getMethod("getPlayerState").invoke(c) != 2 /* STARTED */) continue;
                    if (n++ >= DIAG_MAX) continue;
                    int uid = (Integer) c.getClass().getMethod("getClientUid").invoke(c);
                    System.out.println("  playing " + pkgOf(uid) + " uid=" + uid);
                }
                System.out.println("playing=" + n);
            } catch (Throwable e) { System.out.println("playing=? " + e.getClass().getSimpleName()); }
            try {
                java.util.List<?> l = (java.util.List<?>) as.getClass().getMethod("getActiveRecordingConfigurations").invoke(as);
                for (Object c : l) {
                    int source = (Integer) c.getClass().getMethod("getClientAudioSource").invoke(c);
                    System.out.println("  recording source=" + source + (isVoiceSource(source) ? "" : " (not a voice)"));
                }
                System.out.println("recording=" + l.size());
            } catch (Throwable e) { System.out.println("recording=? " + e.getClass().getSimpleName()); }
        } catch (Throwable e) { System.out.println("diag err " + e.getClass().getSimpleName()); }
    }
    /** Pure: one focus stack line of the diag output. gain: 1 = wants to keep the audio, 2 = for a moment, 3 = for a moment and
     *  others may duck, 4 = for a moment and alone */
    static String diagFocusLine(String pkg, int gain, String loss) {
        String g = gain == 1 ? "gain" : gain == 2 ? "transient" : gain == 3 ? "transient-may-duck" : gain == 4 ? "transient-exclusive" : "gain" + gain;
        return "  focus " + pkg + " " + g + " loss=" + loss;
    }
    /** First package name of a uid ("?" when it cannot be looked up) */
    static String pkgOf(int uid) {
        try {
            Object pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", android.os.IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package"));
            String[] p = (String[]) pm.getClass().getMethod("getPackagesForUid", int.class).invoke(pm, uid);
            return p == null || p.length == 0 ? "?" : p[0];
        } catch (Throwable e) { return "?"; }
    }

    static void log(String s) { System.out.println(new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date()) + " " + s); }
}
