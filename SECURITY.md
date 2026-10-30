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
  Its `INTERNET` permission is used to reach this phone's own adbd and the helper's local control port (below). The fdroid build
  declares `INTERNET` too, for that control port only. Nothing is ever sent off the phone.

## Local control channel（本機指令口）
- The helper listens on **TCP 127.0.0.1:47821** only (loopback; it cannot be reached from the network). Any app that has the
  `INTERNET` permission can connect to a loopback port, so **both builds declare `INTERNET`, and every connection has to pass two
  checks** before a command is even read:
  1. **Token.** The first line must be `auth <token>`. The token is 32 random hex characters (`SecureRandom`), made by the app on
     first use and kept in its private storage (`route` preferences, no backup). The app puts it in the environment of the start
     command (`SPLITAUDIO_TOKEN`); `approute.sh` passes it on to the helper and also writes it to `/data/local/tmp/approute.token`
     (mode 0600, owned by the shell user, so no app can read it) for `approute.sh ask`. It is compared in constant time.
  2. **Uid.** The helper looks the connection up by its source port in `/proc/net/tcp6` and `/proc/net/tcp` (the shell user can
     read them, ordinary apps cannot since Android 10) and takes the uid that owns the client socket. Only the app that started the helper (its uid is handed over in
     the environment variable `SPLITAUDIO_UID` of the start command), the shell user (2000) and root (0) are served. When the
     helper starts it tries this lookup on a connection to itself. Where that works, a connection that cannot be found in the
     tables is refused too (a client that hangs up right after sending would otherwise slip through); where it does not work at
     all (e.g. a ROM that hides those files from the shell user) the token alone decides, and the helper logs that at start.
  Anything else gets `denied` and is disconnected: another app can reach the port, but it gets nothing out of it. A helper started
  without `SPLITAUDIO_UID` serves shell and root only; without a usable token it does not open the port at all.
- Commands (one line after the `auth` line, at most 512 characters, parsed strictly, nothing is passed to a shell): `state` →
  `on`/`off`; `mode` → `map`/`legacy`; `vol up|down <package>` → changes the phone speaker volume by one step (about 1/15 of the
  music stream's range, read from the audio service), only while the rule is on and only for apps that are being routed to the
  speaker. Anything else → `err`. Commands run one at a time; at most 8 connections are served at once and each has 1.5 s to send
  its lines, so a silent client cannot block the others.
- Limits you should know about: the token travels unencrypted over the loopback interface, so a program that grabs port 47821
  *before* the helper starts would see the token the app sends. It still could not use it against the real helper (check 2 turns
  that program away), and nothing sensitive crosses this channel (a status word, one volume step), but it could give the app wrong
  status answers (a nuisance, not a data leak). Clearing the app's data changes the token (and reinstalling usually changes the uid
  too); a helper that is still running then answers `denied` until it is stopped ("Stop split" works without the token) or ends by itself
  (Bluetooth gone for 2 minutes / music silent for 10 minutes).
- For debugging from a shell: `sh /data/local/tmp/approute.sh ask state` (reads the token from the 0600 file; the shell user is
  allowed by check 2).
- 中文摘要：指令口是本機 `127.0.0.1:47821` 的 TCP；每個連線第一行要帶 App 自己的隨機 token（`auth <token>`），而且小程式查
  `/proc/net/tcp` 確認對方 uid 是本 App（或 shell／root），其他 App 連得進埠也一律 `denied`。所以兩個版本都需要 `INTERNET` 權限，
  但沒有任何資料離開手機。

## Known limits（已知極限）
- Native AudioPolicy keeps at most 20 uids per mix and 50 mixes per policy → at most 1000 app uids are routed; any extra
  apps stay on Bluetooth (user-installed apps are listed first).
- Music apps are chosen in the app (default: YouTube Music + the Morphe build) and passed to the helper as package names; apps
  that are not installed are ignored.
- Verified on one device (Xiaomi 15, HyperOS 3, Android 16). Other ROMs may ignore or reject the policy; if the helper
  dies or the policy is rejected, audio simply stays on Bluetooth.
- Apps installed **after** routing started are not included until the next start (music stop → start again).

## Reporting
Please open a GitHub issue. The only listener is bound to 127.0.0.1 (nothing is reachable from the network), so there is no private disclosure channel.
