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
| `INTERNET` 權限 / permission | 有，本機 `127.0.0.1` 的指令口，加上每天最多一次查 GitHub 有沒有新版（從 F-Droid 裝的不查）；沒有任何資料離開手機 / yes, the helper's local control port on `127.0.0.1` plus one GitHub releases query per day at most to look for a new version (skipped when installed by an F-Droid client); no data leaves the phone | 有，連這支手機自己的 adbd 和本機指令口，沒有任何資料離開手機 / yes, to reach this phone's own adbd and the local control port; no data leaves the phone |

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
- **按麥克風時不暫停音樂**（0.5，預設開）：用語音輸入（例如 Gboard 的麥克風）或叫語音助理時，Android 會讓音樂 App 自己暫停。
  開著這個功能時，小程式一看到別的 App 搶走聲音，約 0.3 秒後就替音樂按播放，音樂只頓約半秒，麥克風照常能用。
  只在「開影片不打斷音樂」（多重焦點）開著時有效——多重焦點下，音樂 App 重新要聲音不會把麥克風的聲音搶走，兩邊才能同時；通話／語音對講時照舊先暫停音樂。
  **副作用：手機麥克風會收到喇叭放出來的音樂聲。** 主畫面的「按麥克風時不暫停音樂」開關可以關掉（關＝等麥克風用完才補播，跟以前一樣）。
  **Pressing the mic doesn't pause the music** (0.5, on by default): voice typing (e.g. Gboard's mic) or an assistant makes the music app pause itself.
  With this on, the helper sees another app take the sound and presses play about 0.3 s later, so the music only stalls for about half a second and the mic works as usual.
  It only works while "videos don't pause the music" (multi audio focus) is on — with multi audio focus a music app asking for the sound again does not take it away from the mic,
  so both can run at once; during calls / voice chats the music is still paused as before. **Side effect: the phone's microphone can pick up the music playing from the speaker.**
  Switch it off with the "mic doesn't pause the music" switch on the main screen (off = the music is resumed after the mic is done, as before).
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
                         control port 127.0.0.1:47821 (state / volume keys; every connection needs the app's token and the right uid)
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
- **指令口**：App 和小程式用本機 TCP（只綁 `127.0.0.1:47821`，網路上連不到）溝通。任何有 `INTERNET` 權限的 App 都連得上本機埠，所以每個連線要過兩關：
  ① 第一行必須是 `auth <token>`——token 是 App 自己產生的 32 字元隨機碼（`SecureRandom`），只存在 App 的私有儲存空間，啟動指令經環境變數 `SPLITAUDIO_TOKEN` 交給小程式，
  小程式另存一份 `/data/local/tmp/approute.token`（權限 0600、shell 擁有，其他 App 讀不到）給除錯用；② 小程式查 `/proc/net/tcp`／`tcp6`，確認對方連線的 uid
  是啟動它的那個 App（啟動指令帶 `SPLITAUDIO_UID`）、shell 或 root。兩關任一不過就回 `denied` 並斷線，其他 App 連得進埠也問不到任何東西。
  因此**兩個版本都需要 `INTERNET` 權限**，但只用在這支手機自己身上，沒有任何資料離開手機。除錯：`sh /data/local/tmp/approute.sh ask state`。
  **Control channel**: the app and the helper talk over local TCP, bound to `127.0.0.1:47821` only (not reachable from the network). Any app with the `INTERNET`
  permission can connect to a loopback port, so every connection has to pass two checks: (1) its first line must be `auth <token>` — the token is 32 random characters
  (`SecureRandom`) the app makes itself and keeps in its private storage; the start command hands it to the helper in the environment (`SPLITAUDIO_TOKEN`), and the helper
  also keeps a copy in `/data/local/tmp/approute.token` (mode 0600, owned by the shell user, so no app can read it) for debugging; (2) the helper looks the connection up in
  `/proc/net/tcp` / `tcp6` and checks that its owner uid is the app that started it (its start command carries `SPLITAUDIO_UID`), the shell user or root.
  A connection that fails either check gets `denied` and is closed, so another app can reach the port but gets nothing out of it. That is why **both builds need
  the `INTERNET` permission** — used on this phone only (plus, fdroid build, the optional once-a-day new-version query to GitHub); no data leaves the phone. Debugging: `sh /data/local/tmp/approute.sh ask state`.

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

介面有英文與繁體中文；其他語言歡迎到 [Weblate](https://hosted.weblate.org/engage/splitaudio/) 線上翻譯。UI in English and Traditional Chinese; other languages welcome on [Weblate](https://hosted.weblate.org/engage/splitaudio/).

## 疑難排解 / Troubleshooting
- **音量鍵沒反應，主畫面「音量鍵」那列變回橘色的「建議開」（未開）**：到系統「設定 → 無障礙」把 SplitAudio 重新打開。HyperOS 在 App 被強制停止（例如被清理記憶體的功能）後，會把它的無障礙服務關掉，要自己再開一次。
  **The volume keys do nothing and the "Volume keys" row on the main screen is orange again ("recommended" / 未開)**: open the system Accessibility settings and switch SplitAudio on again.
  HyperOS turns an app's accessibility service off after the app has been force-stopped (for example by a memory cleaner), so it has to be switched on again by hand.

## 回報與新版提示 / Reporting and update hint
主畫面只有一張「回報與意見」卡，按一下就送到、不用任何帳號：「能用！」直接把你這台的診斷文字（機型、系統、版本、模式、音訊裝置種類、最近幾行紀錄；App 當掉過就連同它自己的程式堆疊——裡面沒有帳號、聯絡人、藍牙名稱）送到專案自己的回報接口（程式公開在 [`server/`](server/)），「回報問題」可以先多寫一句發生什麼事；送不出去會改開已填好的 GitHub 表單，想追蹤進度也可以直接選「用 GitHub 表單」，或按「複製診斷文字」貼給我們。第一次分流順利跑約 10 分鐘，這張卡會升到主畫面頂端問一次「能用嗎」，回答後回到底下；換了版本或過 30 天會再問一次，按鈕本身一直都在，之後又不能用也隨時能再回報；分流失敗或上次 App 當掉時會提示一鍵附診斷回報。
**新版提示**：開 App 時每天最多查一次 GitHub 發布頁，有新版就在主畫面頂端顯示卡片（「看更新內容」開發布頁；卡片會提示到你更新為止，不會漏掉修正）；**fdroid／GitHub 版只提示、不會自己下載或安裝**；從 F-Droid 客戶端安裝的不查（由 F-Droid 更新）；Play 版沒有這功能（由 Play 更新）。回報卡片會自己收起（回報後、分流又正常了、或到期），不用你管；一鍵就把修你機型需要的資訊送到，你的機型也會列進相容清單、掛你的名字。
There is a single "Help and feedback" card on the main screen, and one tap sends the report with no account needed: "It works!" sends this phone's diagnostics (phone, ROM, version, mode, audio device types, the last few log lines, when the split was last active and, after a crash, the app's own stack trace; since 0.5.7 the one-tap report also carries a short audio-focus snapshot from the router (which apps hold the audio or are playing, the multi-audio-focus setting and the router's last log lines), so a "something pauses my music" report can be fixed without a round of questions; no accounts, contacts or Bluetooth names) to the project's own report endpoint (its code is public in [`server/`](server/)), and "Report a problem" lets you add a line about what happened first. If sending fails the pre-filled GitHub form opens instead; to follow up you can also pick "Use the GitHub form" directly, or tap "Copy diagnostics" and paste it to us. After the first clean 10 minutes of splitting the card moves to the top once to ask whether it works and returns to the bottom once you answer; it asks again after an update or after 30 days, the buttons are always there, and a phone that stops working later can always be reported again. When routing fails or the app crashed last time it offers a one-tap report with diagnostics.
**New-version hint**: when you open the app it looks at the GitHub releases at most once a day and shows a card on top if there is a newer version ("What's new" opens the release page; the card stays until you are on the latest version, so you never miss a fix). **The fdroid / GitHub build only tells you; it never downloads or installs anything by itself.** It is skipped when the app was installed by an F-Droid client (F-Droid updates it), and the Play build does not have it (Google Play updates the app). The report cards take care of themselves: they go away once you report, once the split is working again, or when they expire, so there is nothing to tidy up, and a single tap sends what is needed to get your phone fixed and listed in COMPATIBILITY.md with you credited.

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
單元測試也會編進並測試 `route/AppRoute.java`：純函式（音量格數、指令口的 token／uid 規則、`/proc/net/tcp` 解析、檔案讀取），以及在真的本機連線上跑一遍指令口。The unit tests also compile `route/AppRoute.java` and test its pure functions (volume steps, the control port's token / uid rules, the `/proc/net/tcp` parser, file readers) and the control port itself on real loopback sockets.
play 版的內建 ADB 函式庫（libadb-android）從 JitPack 下載，只有 play 版會用到。The play build's built-in ADB library (libadb-android) comes from JitPack and only the play build uses it.
GitHub Actions 每次 push 都會從原始碼編出 APK（含路由小程式）。CI builds the APKs (including the helper dex) from source on every push.

## 隱私 / Privacy
不收集任何資料，沒有任何資料自己離開手機。對外連線只有兩種：①fdroid／GitHub 版開 App 時每天最多一次向 GitHub 查有沒有新版，只帶 User-Agent（從 F-Droid 客戶端安裝的不查；Play 版沒有）；②**你按下**「能用！」「回報問題」或當機卡的回報鈕時，才把「複製診斷文字」那份內容（沒有帳號、聯絡人、藍牙名稱，也沒有任何裝置識別碼）送到專案的回報接口（接口程式公開在 [`server/`](server/)，只存一個加鹽雜湊當限流用、不存 IP），送之前每天最多一次向 GitHub 讀一個小檔確認接口網址。兩個版本都有 `INTERNET` 權限，除了上面那個查新版，只用在這支手機自己身上：App 和小程式之間的本機（`127.0.0.1`）指令口（要帶 App 自己的隨機 token、而且對方 uid 要是本 App，其他 App 連不進），
以及 play 版連這支手機自己的 adbd（內建 ADB）。無障礙服務（fdroid 版）只攔音量鍵、只讀前景 App 的套件名稱，不讀畫面文字。
Nothing is collected and no data leaves the phone on its own. There are only two outgoing connections: (1) the fdroid / GitHub build asks GitHub at most once a day, when you open the app, whether a newer version exists, sending only a User-Agent (skipped when the app was installed by an F-Droid client; the Play build has none); (2) **when you tap** "It works!", "Report a problem" or the crash card's report button, the "Copy diagnostics" text (no accounts, contacts, Bluetooth names or device identifiers) is sent to the project's report endpoint (its code is public in [`server/`](server/); it keeps only a salted hash for rate limiting, never the address), after reading a small file from GitHub at most once a day to confirm the endpoint's address. Both builds have the `INTERNET` permission, but apart from that query it is only used on this phone itself: the local (`127.0.0.1`) control port
between the app and its helper (every connection needs the app's own random token and must come from the app's uid, so other apps cannot use it), and, in the play build,
the connection to this phone's own adbd (built-in ADB). The accessibility service (fdroid build) only filters volume keys and reads the foreground package name;
it does not read screen text. See [SECURITY.md](SECURITY.md).

## 幫忙翻譯 / Help translate

[![Translation status](https://hosted.weblate.org/widget/splitaudio/app/svg-badge.svg)](https://hosted.weblate.org/engage/splitaudio/)

SplitAudio 目前有英文和繁體中文。想翻成你的語言？最簡單是在 [Weblate](https://hosted.weblate.org/engage/splitaudio/) 網頁上直接翻（不用 GitHub 帳號、不用改檔），大約 15～30 分鐘；想自己改檔看 [TRANSLATING.md](TRANSLATING.md)。

SplitAudio is available in English and Traditional Chinese. Want it in your language? The easiest way is to translate in the browser on [Weblate](https://hosted.weblate.org/engage/splitaudio/) (no GitHub account, no files to edit), about 15–30 minutes; to edit the files yourself see [TRANSLATING.md](TRANSLATING.md).

## 授權 / License
MIT
