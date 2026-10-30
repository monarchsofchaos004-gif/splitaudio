# Security notes / 安全說明

SplitAudio is **alpha**. Please read the limits below before relying on it.

## Privileges（權限）
- The routing helper (`route/AppRoute.java`) runs as the **shell user** (started through Shizuku, or through the Play build's
  built-in ADB client). That is what lets it register a system audio policy (`MODIFY_AUDIO_ROUTING`).
- The app writes `approute.dex` / `approute.sh` and a few small setting files (`approute.music`, `approute.pkgmap`, `approute.volpkg`,
  flag files) into `/data/local/tmp` and runs the script there. Package names written to `approute.music` and
  `approute.pkgmap` are validated first (`[A-Za-z0-9_.]` only) and the script only ever uses them as data. Anything else that
  already has shell access could replace those files — this is the same trust level as shell/Shizuku itself, not an extra hole.
- The accessibility service (fdroid build only) filters volume keys and reads only the **foreground package name**
  (`rootInActiveWindow.packageName`). It is declared with `canRetrieveWindowContent` because Android requires that for
  `rootInActiveWindow`; screen text is never read. The Play build has no accessibility service.
- The Play build's built-in ADB client keeps its key pair in the app's private storage (no backup), pairs with this phone's own
  Wireless debugging, and grants itself `WRITE_SECURE_SETTINGS` once (used only to switch Wireless debugging back on after a reboot).
  Its `INTERNET` permission is only used to reach this phone's own adbd. The fdroid build declares no `INTERNET` permission at all.

## Local control channel（本機指令口）
- The helper listens on the **abstract local socket `app.splitaudio.route`** (a Unix socket, not a network port; no `INTERNET`
  permission is needed to use it).
- Every connection is checked with the **uid of the process on the other end** (`LocalSocket.getPeerCredentials()`):
  only the app that started the helper (its uid is handed over in the environment variable `SPLITAUDIO_UID` of the start
  command), the shell user (2000) and root (0) get an answer. Any other app gets `denied` and is disconnected.
  A helper started without `SPLITAUDIO_UID` answers shell and root only.
- Commands: `state` → `on`/`off`; `mode` → `map`/`legacy`; `vol up|down <package>` → changes the phone speaker volume by one
  step (about 1/15 of the music stream's range, read from the audio service), only while the rule is on and only for apps that
  are being routed to the speaker. Anything else → `err`. Input is parsed strictly; nothing is passed to a shell.
- For debugging from a shell: `sh /data/local/tmp/approute.sh ask state`. With the environment variable `SPLITAUDIO_TCP=1` the
  helper also opens the old plain TCP port 127.0.0.1:47821 (no peer check is possible there); it is off by default and the app
  never turns it on.

## Known limits（已知極限）
- Native AudioPolicy keeps at most 20 uids per mix and 50 mixes per policy → at most 1000 app uids are routed; any extra
  apps stay on Bluetooth (user-installed apps are listed first).
- Music apps are chosen in the app (default: YouTube Music + the Morphe build) and passed to the helper as package names; apps
  that are not installed are ignored.
- Verified on one device (Xiaomi 15, HyperOS 3, Android 16). Other ROMs may ignore or reject the policy; if the helper
  dies or the policy is rejected, audio simply stays on Bluetooth.
- Apps installed **after** routing started are not included until the next start (music stop → start again).

## Reporting
Please open a GitHub issue. There is no network-facing component, so there is no private disclosure channel.
