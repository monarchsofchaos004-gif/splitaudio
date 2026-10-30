# SplitAudio — 藍牙只放音樂，其他 App 走手機喇叭 / Music on Bluetooth, everything else on the phone speaker

<img src="fastlane/metadata/android/en-US/images/icon.png" width="112" align="right" alt="SplitAudio icon">

> **狀態：alpha（實驗版）。** 只在 Xiaomi 15（HyperOS 3、Android 16）實機驗證過。需要 Android 12 以上，**不需要 root**。
> 兩個版本（見下面「兩個版本」）：F-Droid／GitHub 版要 [Shizuku](https://shizuku.rikka.app/)；Play 版有 Shizuku 就用，沒有就用內建的無線偵錯（不用另外裝 Shizuku）。
> **Status: alpha.** Verified on one device only: Xiaomi 15 (HyperOS 3, Android 16). Needs Android 12 or newer, **no root**.
> Two builds (see "Two builds" below): the F-Droid / GitHub build needs [Shizuku](https://shizuku.rikka.app/); the Play build uses Shizuku if it is there, otherwise its built-in Wireless debugging client (no Shizuku needed).

> ⭐ **好用的話，請按右上角的 Star 給個星星！** 這是我們知道有人在用、繼續維護下去的最大動力。
> ⭐ **If this app is useful to you, please give it a Star (top-right).** It's how we know people use it, and it keeps the project going.

藍牙喇叭正在放 YouTube Music，你一打開 Facebook／IG／TikTok 滑影片，影片聲音也跟著跑到藍牙喇叭。
Android 沒有「指定某個 App 從哪裡出聲」的設定（三星的「個別應用程式聲音」只有三星有）。
SplitAudio 讓 **音樂 App 留在藍牙、其他所有 App 改走手機喇叭**。

You are playing YouTube Music on a Bluetooth speaker, then open Facebook / Instagram / TikTok — and the video
sound also goes to the speaker. Stock Android has no per-app output setting (Samsung's "Separate app sound" is
Samsung-only). SplitAudio keeps **the music app on Bluetooth and sends every other app to the phone speaker**.

## 兩個版本 / Two builds
同一份原始碼、兩個版本（Gradle product flavor）。Same source code, two builds (Gradle product flavors).

| | **fdroid**（F-Droid／GitHub Releases） | **play**（Google Play，上架準備中 / in preparation） |
|---|---|---|
| App id | `app.splitaudio` | `app.splitaudio.play` |
| shell 權限怎麼來 / Shell access | [Shizuku](https://shizuku.rikka.app/) | Shizuku，沒有就用內建 ADB 無線偵錯 / Shizuku, otherwise the built-in ADB client (Wireless debugging, one-time pairing) |
| 功能 / Features | 全部免費 / everything free | 簡單模式免費；自訂模式與每個 App 音量是 Pro（買斷，裝好有 7 天試用；購買功能之後的版本開放）/ Simple mode free; custom mode and per-app volume are Pro (one-time purchase, 7-day trial; purchasing comes in a later version) |
| 音量鍵（無障礙服務）/ Volume keys (accessibility) | 有（Android 13 以上）/ yes (Android 13+) | 沒有 / no |
| `INTERNET` 權限 / permission | **沒有 / none** | 有，只用來連這支手機自己的 adbd / yes, only to reach this phone's own adbd |

需求 / Requirements: Android 12（API 31）以上；音量鍵功能要 Android 13 以上（要讀系統的 VolumeInfo）。 Android 12 or newer; the volume keys need Android 13 or newer (they read the system's VolumeInfo).

## 功能 / What it does
- 藍牙連著＋音樂 App 正在播 → 音樂 App 走藍牙，其他 App 全走手機喇叭。
  While Bluetooth is connected and the music app is playing: music → Bluetooth, every other app → phone speaker.
- 音樂停 15 秒或藍牙斷開 → 規則拿掉，一切恢復原狀。講電話（含網路電話）時也會先拿掉。
- **別的 App 進入通話／語音對講**（例如監視器 App 按麥克風）：Android 這時會把媒體改送到聽筒，所以先暫停音樂，結束後自動續播。
  While another app is in a call / voice chat (e.g. a camera app's talk button) Android sends media to the earpiece, so the music is paused and resumed afterwards.
  Music stopped for 15 s, or Bluetooth gone → the rule is removed and audio behaves normally. Also removed during calls.
- **音量鍵**（fdroid 版，Android 13 以上）：在走喇叭的 App 畫面按＝調手機喇叭（畫面提示「手機喇叭音量 xx%」）；在音樂 App／桌面按＝照常調藍牙。
  音量一格多大是向系統問音量範圍算出來的（約 15 格），桌面 App 也是動態查的，不綁特定廠牌。
  **Volume keys** (fdroid build, Android 13+): in a speaker-routed app they change the phone speaker volume; in the music app / home screen they change Bluetooth as usual.
  The step size comes from the device's own volume range (about 15 steps) and the home screen app is looked up, so nothing is tied to one brand.
- **開影片不打斷音樂**：Android 預設「別的 App 一開影片，音樂 App 就自己暫停」（播放權只給一個 App），光分流擋不住。
  要靠 Android 內建的「多個 App 同時播放」（multi audio focus，Android 11 起內建、預設關）。**第一次分流時 App 會先問你**（通知＋主畫面一張卡：開啟／不要），
  你同意才會打開，不會自己動系統設定；打開那一下音樂會停一秒，會自動按回播放。這是系統設定、重開機仍有效；來電、鬧鐘照樣會讓全部暫停。
  之後隨時可以用主畫面的「開影片不打斷音樂」開關改；按過「不要」就不會再問、也不會自動打開。**移除 App 前想還原，先把開關關掉。**
  **A video doesn't pause the music**: by default, when another app starts a video it takes the audio focus and the music
  app pauses itself — routing alone can't stop that. It needs Android's built-in *multi audio focus* (Android 11+, off by default).
  **The first time the split turns on, SplitAudio asks you** (a notification plus a card on the main screen: turn on / no thanks);
  it only switches the system setting on after you agree. The music pauses for a second at that moment and is resumed
  automatically. It is a system setting, so it survives reboot; calls and alarms still pause everything. You can change your mind
  any time with the "videos don't pause the music" switch on the main screen; "no thanks" means it is never asked or switched on again.
  **To restore the default before uninstalling, switch it off first.**
- **故障安全**：路由小程式一旦結束，規則跟著消失＝全部回到藍牙，不會變成沒聲音。
  **Fail-safe**: if the helper process dies, its rule dies with it — audio falls back to Bluetooth, never to silence.

**音樂 App 自己選**：簡單模式下，主畫面按「選音樂 App」，在清單（只列有桌面圖示的 App）勾選，可多選、至少留一個；勾選的 App 留在藍牙，其他 App 走喇叭。
預設是 YouTube Music（`com.google.android.apps.youtube.music`，以及 Morphe 修改版）。
**Choose your music apps**: in Simple mode tap "Choose music apps" on the main screen and tick them (only apps with a launcher icon are listed; several are fine, at least one must stay ticked).
The ticked apps stay on Bluetooth, everything else plays on the speaker. The default is YouTube Music (plus the Morphe-patched package).

## 自訂模式 / Custom mode (0.3)
> **只在 Xiaomi 15（HyperOS 3、Android 16）實機驗證過。** Only verified on a Xiaomi 15 (HyperOS 3, Android 16).

- 打開「自訂模式」→「選每個 App 的輸出」：每個 App 可以選 **預設（跟系統）／手機喇叭／藍牙／有線或 USB 耳機**。
  Turn on "Custom mode" → "Choose output for each app": every app can play on **Default (follow the system) / Phone
  speaker / Bluetooth / Wired or USB headphones** — like Samsung's "Separate app sound", on any phone with Shizuku (or the Play build's built-in ADB).
- 選的裝置沒連著時，那個 App 就跟系統走（不會沒聲音）；講電話時規則先拿掉。
  If the chosen device isn't connected, that app follows the system (never silence). The rule is removed during calls.
- 只列有桌面圖示的 App（不需要「讀取所有 App 清單」權限）。
  Only apps with a launcher icon are listed (no "query all packages" permission).
- 簡單模式（音樂 App 走藍牙、其他走喇叭）仍是預設，升級後行為不變。
  Simple mode (music app on Bluetooth, everything else on the speaker) stays the default, so upgrading changes nothing.
- 原理同下：App 把設定寫成 `/data/local/tmp/approute.pkgmap`，`approute.sh startmap` 換成 uid 後起 `AppRoute map`，
  每種輸出各一組 RENDER 規則。
  Same mechanism as below: the app writes `/data/local/tmp/approute.pkgmap`, `approute.sh startmap` turns package names
  into uids and starts `AppRoute map`, which registers one set of RENDER mixes per output device.

## 每個 App 分開調音量 / Volume for each app (0.4)
> **只在 Xiaomi 15（HyperOS 3、Android 16）實機驗證過。** Only verified on a Xiaomi 15 (HyperOS 3, Android 16).

- 主畫面「每個 App 分開調音量」：每個 App 一條拉桿＋靜音鈕，正在出聲的 App 排最前面；100% ＝不動它。跟分流各自獨立，不開分流也能用。
  Main screen → "Volume for each app": a slider and a mute button per app; apps playing right now come first; 100% = left alone.
  Independent of the split: works with or without it.
- 原理：小程式（`AppRoute mixer`）以 shell 身分對那個 App 的每個播放器呼叫系統的 `PlayerProxy.setVolume`（需要 `MODIFY_AUDIO_ROUTING`，shell 有）；
  App 自己也可能改音量，所以每 5 秒重設一次。設定全回 100% 時，它先把音量還原再結束。
  How: a helper (`AppRoute mixer`) running as the shell user calls the system `PlayerProxy.setVolume` on each player of that app
  (needs `MODIFY_AUDIO_ROUTING`, which the shell user has). An app may change its own volume, so the value is re-sent every 5 s.
  When every app is back at 100% it restores the volumes and exits.

## 原理 / How it works
```
 SplitAudio app ──Shizuku or built-in ADB (shell user)──► /data/local/tmp/approute.sh start
                                               │
                                               ▼
                         app_process AppRoute (route/AppRoute.java)
                         registers a system AudioPolicy:
                           RENDER mix, rule = RULE_MATCH_UID(<every app except the music apps>)
                           device = built-in speaker
                         local socket @app.splitaudio.route (state / volume keys; the peer's uid is checked)
```
- shell 身分有 `MODIFY_AUDIO_ROUTING`，所以能註冊系統層的路由規則；有些廠牌 ROM 不理 App 自己指定的輸出裝置，但系統規則有效。
  The shell user holds `MODIFY_AUDIO_ROUTING`, so it can register a system-level routing policy. Some OEM ROMs ignore an
  app's own `setPreferredDevice()`, but a system policy is honoured.
- 系統底層每條規則最多只收 **20 個 uid**（多的會被靜默丟掉），所以每 20 個 App 一條規則、最多 50 條（1000 個 App）。
  Native AudioPolicy keeps at most **20 criteria per mix** (extras are silently dropped), so apps are split into
  mixes of 20, up to 50 mixes (1000 apps).
- App 本身只負責：偵測「藍牙＋音樂在播」→ 經 Shizuku（或 Play 版的內建 ADB）把小程式放進 `/data/local/tmp` 並啟動（內容沒變就略過部署，靠 `approute.ver` 的指紋）；小程式用 `setsid` 脫離，App 被系統殺掉也照跑。
  The app only detects "Bluetooth + music playing", deploys the helper via Shizuku (or the Play build's built-in ADB; skipped when nothing
  changed, by a fingerprint in `approute.ver`) and starts it; the helper is detached with `setsid`, so it keeps running even if the app is
  killed. It exits by itself after Bluetooth is gone for 2 min or the music app has been silent for 10 min.
- **指令口**：App 和小程式用本機 socket（abstract namespace `app.splitaudio.route`）溝通，不走網路，所以 fdroid 版不需要網路權限。小程式會檢查連進來的 uid：
  只有啟動它的那個 App（啟動指令帶 `SPLITAUDIO_UID`）、shell 和 root 會得到回答，別的 App 一律 `denied`。除錯：`sh /data/local/tmp/approute.sh ask state`。
  **Control channel**: the app and the helper talk over a local socket (abstract namespace `app.splitaudio.route`), not the network, so the fdroid build
  needs no network permission. The helper checks the uid of every connection: only the app that started it (its start command carries `SPLITAUDIO_UID`),
  the shell user and root get an answer; any other app gets `denied`. Debugging: `sh /data/local/tmp/approute.sh ask state`.

## 安裝 / Install
> 從 0.2.1 起改用正式金鑰簽名；裝過更早測試版的要先解除安裝再裝。
> Since 0.2.1 the APK is signed with a release key; if you installed an earlier test build, uninstall it first.

**fdroid 版（F-Droid／GitHub Releases）/ fdroid build**
1. 安裝並啟動 [Shizuku](https://shizuku.rikka.app/)（無線偵錯啟動即可）。Install and start Shizuku.
2. 從 [Releases](../../releases) 下載 APK 安裝。Install the APK from Releases.
3. 打開 SplitAudio：按「① Shizuku 授權」；想用音量鍵功能再按「② 開啟無障礙」。
   Open SplitAudio → "① Shizuku 授權" (grant Shizuku); optionally "② 開啟無障礙" (accessibility, for volume keys).

**play 版 / play build**（不用 Shizuku / no Shizuku needed）
1. 打開 SplitAudio，照「一次性設定」四步：開發者選項 → 無線偵錯（要連 WiFi）→ 配對（通知列直接輸入 6 位數配對碼）→ 連線。
   Open SplitAudio and follow the four "One-time setup" steps: Developer options → Wireless debugging (on Wi-Fi) → pair (type the 6-digit code in the notification) → connect.
2. 之後重開機只要連上 WiFi 就會自己接回，不用再配對。（手機上有 Shizuku 並授權的話，就直接用 Shizuku，這幾步可以略過。）
   After a reboot it reconnects by itself whenever you are on Wi-Fi; no pairing again. (If Shizuku is running and granted, it is used and the steps can be skipped.)

**兩個版本都要 / both builds**
1. 小米／HyperOS：自啟動打開、省電策略設「無限制」，不然常駐監看會被清掉。
   Xiaomi / HyperOS: enable Autostart and set battery saver to "No restrictions", otherwise the monitor gets killed.
2. 連藍牙、播 YouTube Music → 通知欄出現「SplitAudio：分流中」。用別的音樂 App？到「選音樂 App」勾選。
   Connect Bluetooth, play YouTube Music → the notification shows "SplitAudio：分流中" (routing on). Another music app? Tick it under "Choose music apps".

介面目前是繁體中文；歡迎英文翻譯 PR。The UI is Traditional Chinese for now; translation PRs welcome.

## 自己編譯 / Build
JDK 17、Android SDK（`platforms;android-36`、任一版 `build-tools`，例如 `36.0.0`）、Gradle 8.9。JDK 17, Android SDK (`platforms;android-36` and any `build-tools`, e.g. `36.0.0`), Gradle 8.9:
```
sh route/build_dex.sh                                   # always first: route/AppRoute.java -> app/src/main/assets/approute.dex
gradle assembleFdroidDebug                              # fdroid build, debug APK
gradle assemblePlayDebug                                # play build, debug APK
gradle testFdroidDebugUnitTest testPlayDebugUnitTest   # JVM unit tests of both builds
gradle assembleFdroidRelease                            # fdroid build, release APK (unsigned unless you set up signing)
gradle bundlePlayRelease                                # play build, Android App Bundle
```
單元測試也會編進並測試 `route/AppRoute.java` 裡的純函式（音量格數、指令口的 uid 規則、檔案讀取）。The unit tests also compile and test the pure functions in `route/AppRoute.java`.
play 版的內建 ADB 函式庫（libadb-android）從 JitPack 下載，只有 play 版會用到。The play build's built-in ADB library (libadb-android) comes from JitPack and only the play build uses it.
GitHub Actions 每次 push 都會從原始碼編出 APK（含路由小程式）。CI builds the APKs (including the helper dex) from source on every push.

## 隱私 / Privacy
不連外網、不收集任何資料。fdroid 版**沒有 `INTERNET` 權限**（App 和小程式用本機 socket 溝通）；play 版有，但只用來連這支手機自己的 adbd（內建 ADB）。
無障礙服務（fdroid 版）只攔音量鍵、只讀前景 App 的套件名稱，不讀畫面文字。
No network access and no data collection. The fdroid build has **no `INTERNET` permission** (the app and its helper talk over a local socket); the play build has it
only to reach this phone's own adbd (built-in ADB). The accessibility service (fdroid build) only filters volume keys and reads the foreground package name;
it does not read screen text. See [SECURITY.md](SECURITY.md).

## 幫忙翻譯 / Help translate

SplitAudio 目前有英文和繁體中文。想翻成你的語言？看 [TRANSLATING.md](TRANSLATING.md)，大約 15～30 分鐘。

SplitAudio is available in English and Traditional Chinese. Want it in your language? See [TRANSLATING.md](TRANSLATING.md) — it takes about 15–30 minutes.

## 授權 / License
MIT
