# Device compatibility / 機型相容清單

SplitAudio talks to Android's audio-policy layer through Shizuku, and every manufacturer tweaks that layer a little.
This list is built from user reports. **Please add your device**, it is the most useful contribution you can make
(see [How to add a row](#how-to-add-a-row), no build or coding needed).

Legend: ✅ works · ⚠️ works with notes · ❌ does not work · ❓ untested

| Brand | Model | ROM / Android | App version | Simple mode | Custom mode | Volume keys | Notes | Report |
|---|---|---|---|---|---|---|---|---|
| Xiaomi | Xiaomi 15 | HyperOS 3 / Android 16 | 0.5.0 | ✅ | ✅ | ✅ | Reference device. Enable Autostart + battery "No restrictions". | maintainer |
| ASUS | Zenfone 10 | Android 14 | 0.3.x | ⚠️ | ⚠️ | ❓ | Routing works; the two outputs have separate volumes, so Bluetooth volume can look like it "drops". | [#5](https://github.com/monarchsofchaos004-gif/splitaudio/issues/5) |
| OnePlus | OnePlus 11 5G | OxygenOS / Android 14 | 0.3.x | ❌ | ⚠️ | ❓ | Simple mode did nothing (music app not on the default list); Custom mode routes, see the issue for details. | [#7](https://github.com/monarchsofchaos004-gif/splitaudio/issues/7) |
| Samsung | (unspecified) | One UI | 0.2.x | ❌ | ❓ | ❓ | Reporter switched to Samsung's built-in *Separate app sound*. | [#2](https://github.com/monarchsofchaos004-gif/splitaudio/issues/2) |
| OPPO | CPH2629 | ColorOS 16 / Android 16 | 0.5.6 | ✅ | ⚠️ | ❓ | Simple mode reported working. Custom mode with a wired USB headset: a problem was reported, details pending. | [#14](https://github.com/monarchsofchaos004-gif/splitaudio/issues/14), [#13](https://github.com/monarchsofchaos004-gif/splitaudio/issues/13) (in-app report) |
| Google | Pixel 10 Pro XL | Android 17 | 0.5.7 | ❓ | ✅ | ✅ | Custom mode reported working with the split active on 0.5.7; the volume keys work. The earlier note about videos pausing the music was on 0.5.6. | [#17](https://github.com/monarchsofchaos004-gif/splitaudio/issues/17) (in-app report), [#103](https://github.com/monarchsofchaos004-gif/splitaudio/issues/103) |
| Redmi | 2411DRN47I | Android 16 | 0.5.6 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#15](https://github.com/monarchsofchaos004-gif/splitaudio/issues/15) (in-app report) |
| Redmi | 2303CRA44A | Android 15 | 0.5.6 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#16](https://github.com/monarchsofchaos004-gif/splitaudio/issues/16) (in-app report) |
| realme | RMX3988 | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active. | [#19](https://github.com/monarchsofchaos004-gif/splitaudio/issues/19) |
| Redmi | 24116RACCG | Android 16 | 0.5.6 | ❓ | ✅ | ❓ | Custom mode reported working. | [#21](https://github.com/monarchsofchaos004-gif/splitaudio/issues/21) |
| HONOR | ABR-NX1 | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | Custom mode reported working with the split active. | [#22](https://github.com/monarchsofchaos004-gif/splitaudio/issues/22) |
| realme | RMX3910 | Android 16 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#24](https://github.com/monarchsofchaos004-gif/splitaudio/issues/24) |
| realme | RMX3760 | Android 15 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#25](https://github.com/monarchsofchaos004-gif/splitaudio/issues/25) |
| vivo | V2437 | Android 16 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#26](https://github.com/monarchsofchaos004-gif/splitaudio/issues/26) |
| Google | Pixel 11 Pro | Android 17 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active. | [#27](https://github.com/monarchsofchaos004-gif/splitaudio/issues/27) |
| Samsung | SM-A556E | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active. | [#28](https://github.com/monarchsofchaos004-gif/splitaudio/issues/28) |
| motorola | moto g04s | Android 14 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active (three one-tap reports). | [#41](https://github.com/monarchsofchaos004-gif/splitaudio/issues/41) |
| Samsung | SM-A366B | Android 17 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active. | [#42](https://github.com/monarchsofchaos004-gif/splitaudio/issues/42) |
| PHILIPS | S7710 | Android 13 | 0.5.7 | ❌ | ❓ | ❓ | Reported not working. The diagnostics show the router started three times but no music app was playing while it ran (another app held audio focus), so the split never engaged; a report sent while music is playing would settle it. | [#40](https://github.com/monarchsofchaos004-gif/splitaudio/issues/40) |
| Redmi | miel | Android 17 | 0.5.6 | ❓ | ✅ | ❓ | One-tap report from the app. | [#18](https://github.com/monarchsofchaos004-gif/splitaudio/issues/18) |
| POCO | 23013PC75G | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#20](https://github.com/monarchsofchaos004-gif/splitaudio/issues/20) |
| ASUS | ASUS_AI2302 | Android 15 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working with Bluetooth connected. To keep music on Bluetooth when the phone output is switched, set the music app to Bluetooth under Custom mode; see the issues. | [#23](https://github.com/monarchsofchaos004-gif/splitaudio/issues/23), [#76](https://github.com/monarchsofchaos004-gif/splitaudio/issues/76) |
| OnePlus | CPH2609 | Android 16 | 0.5.7 | ❓ | ❌ | ❓ | Looking into it; details in the issue. | [#33](https://github.com/monarchsofchaos004-gif/splitaudio/issues/33) |
| POCO | 2310FPCA4G | Android 15 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#34](https://github.com/monarchsofchaos004-gif/splitaudio/issues/34) |
| Realme | RMX3371 | Android 14 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#35](https://github.com/monarchsofchaos004-gif/splitaudio/issues/35) |
| Redmi | 22071219AI | Android 14 | 0.5.7 | ⚠️ | ❓ | ❓ | Mixed reports: worked in one report, not in another; see the issues. The "not working" report was sent with no Bluetooth device connected; please re-test with Bluetooth on. | [#36](https://github.com/monarchsofchaos004-gif/splitaudio/issues/36), [#50](https://github.com/monarchsofchaos004-gif/splitaudio/issues/50) |
| Motorola | moto g86 5G | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#37](https://github.com/monarchsofchaos004-gif/splitaudio/issues/37) |
| Redmi | 23021RAA2Y | Android 15 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#38](https://github.com/monarchsofchaos004-gif/splitaudio/issues/38) |
| Redmi | 25078RA3EA | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#39](https://github.com/monarchsofchaos004-gif/splitaudio/issues/39) |
| POCO | 25053PC47G | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#43](https://github.com/monarchsofchaos004-gif/splitaudio/issues/43) |
| Redmi | 25062RN2DL | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#44](https://github.com/monarchsofchaos004-gif/splitaudio/issues/44) |
| Redmi | 2201117SG | Android 13 | 0.5.7 | ❓ | ⚠️ | ❓ | Mixed reports: worked in one report, not in another; see the issues. | [#45](https://github.com/monarchsofchaos004-gif/splitaudio/issues/45), [#46](https://github.com/monarchsofchaos004-gif/splitaudio/issues/46) |
| Infinix | X6870 | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#47](https://github.com/monarchsofchaos004-gif/splitaudio/issues/47) |
| Motorola | moto g power - 2025 | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#48](https://github.com/monarchsofchaos004-gif/splitaudio/issues/48) |
| OnePlus | PLC110 | Android 17 | 0.5.7 | ⚠️ | ❓ | ❓ | Mixed reports: worked in one report, not in another; see the issues. | [#49](https://github.com/monarchsofchaos004-gif/splitaudio/issues/49), [#51](https://github.com/monarchsofchaos004-gif/splitaudio/issues/51) |
| Xiaomi | 22071212AG | Android 13 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#52](https://github.com/monarchsofchaos004-gif/splitaudio/issues/52) |
| Xiaomi | 23127PN0CC | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#53](https://github.com/monarchsofchaos004-gif/splitaudio/issues/53) |
| Samsung | SM-S938U1 | Android 17 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#54](https://github.com/monarchsofchaos004-gif/splitaudio/issues/54) |
| Redmi | 23106RN0DA | Android 15 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#55](https://github.com/monarchsofchaos004-gif/splitaudio/issues/55) |
| Xiaomi | 22081212UG | Android 15 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#56](https://github.com/monarchsofchaos004-gif/splitaudio/issues/56) |
| Motorola | edge 40 | Android 15 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#57](https://github.com/monarchsofchaos004-gif/splitaudio/issues/57) |
| POCO | 2312FPCA6G | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#58](https://github.com/monarchsofchaos004-gif/splitaudio/issues/58) |
| Redmi | 23129RA5FL | Android 15 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#59](https://github.com/monarchsofchaos004-gif/splitaudio/issues/59) |
| Samsung | SM-A266B | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | One-tap report from the app. | [#60](https://github.com/monarchsofchaos004-gif/splitaudio/issues/60) |
| Xiaomi | 25098PN5AC | Android 17 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#61](https://github.com/monarchsofchaos004-gif/splitaudio/issues/61) |
| Infinix | X6887 | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#62](https://github.com/monarchsofchaos004-gif/splitaudio/issues/62) |
| Redmi | 24117RN76G | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#63](https://github.com/monarchsofchaos004-gif/splitaudio/issues/63) |
| Realme | RMX5070 | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | One-tap report from the app. | [#64](https://github.com/monarchsofchaos004-gif/splitaudio/issues/64) |
| Xiaomi | 2406APNFAG | Android 16 | 0.5.7 | ❓ | ⚠️ | ❓ | Mixed reports: worked in one report, not in another; see the issues. | [#65](https://github.com/monarchsofchaos004-gif/splitaudio/issues/65), [#66](https://github.com/monarchsofchaos004-gif/splitaudio/issues/66) |
| Samsung | SM-A155M | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active. | [#70](https://github.com/monarchsofchaos004-gif/splitaudio/issues/70) |
| Redmi | 23113RKC6C | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | Custom mode reported working with the split active. | [#71](https://github.com/monarchsofchaos004-gif/splitaudio/issues/71) |
| POCO | 23049PCD8G | Android 15 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#67](https://github.com/monarchsofchaos004-gif/splitaudio/issues/67) |
| Redmi | 22101316G | Android 14 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#68](https://github.com/monarchsofchaos004-gif/splitaudio/issues/68), [#69](https://github.com/monarchsofchaos004-gif/splitaudio/issues/69) |
| Infinix | X6882 | Android 14 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. A start-up crash from this model is fixed in the next release. | [#72](https://github.com/monarchsofchaos004-gif/splitaudio/issues/72), [#74](https://github.com/monarchsofchaos004-gif/splitaudio/issues/74) |
| POCO | 22101320G | Android 14 | 0.5.7 | ⚠️ | ✅ | ❓ | Custom mode confirmed working with the split active; the Simple mode report is waiting for a confirming report. | [#73](https://github.com/monarchsofchaos004-gif/splitaudio/issues/73) |
| POCO | 21121210G | Android 14 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#77](https://github.com/monarchsofchaos004-gif/splitaudio/issues/77) |
| Motorola | moto g13 | Android 14 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#78](https://github.com/monarchsofchaos004-gif/splitaudio/issues/78) |
| TECNO | CN5 | Android 16 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#79](https://github.com/monarchsofchaos004-gif/splitaudio/issues/79) |
| HONOR | ALT-LX3 | Android 14 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#80](https://github.com/monarchsofchaos004-gif/splitaudio/issues/80) |
| OnePlus | CPH2653 | Android 16 | 0.5.7 | ❌ | ❓ | ❓ | Reported not working; a fix is being looked into, see the issue. | [#81](https://github.com/monarchsofchaos004-gif/splitaudio/issues/81) |
| Samsung | SCG26 | Android 17 | 0.5.7 | ❓ | ❌ | ❓ | Reported not working: phone calls are affected while the split is on. A fix is being looked into; see the issue. | [#82](https://github.com/monarchsofchaos004-gif/splitaudio/issues/82) |
| Redmi | 2510DRK44C | Android 17 | 0.5.7 | ❓ | ✅ | ❓ | Custom mode reported working with the split active. | [#83](https://github.com/monarchsofchaos004-gif/splitaudio/issues/83) |
| Samsung | SM-A165F | Android 17 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#84](https://github.com/monarchsofchaos004-gif/splitaudio/issues/84) |
| HONOR | PNM-N49 | Android 17 | 0.5.7 | ❓ | ❌ | ❓ | Reported not working; a fix is being looked into, see the issue. | [#85](https://github.com/monarchsofchaos004-gif/splitaudio/issues/85) |
| HONOR | VER-N49 | Android 16 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active. | [#86](https://github.com/monarchsofchaos004-gif/splitaudio/issues/86) |
| Xiaomi | 25091RP04C | Android 17 | 0.5.7 | ✅ | ✅ | ❓ | Simple and Custom modes reported working with the split active. | [#87](https://github.com/monarchsofchaos004-gif/splitaudio/issues/87) |
| POCO | 2511FPC34G | Android 16 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#88](https://github.com/monarchsofchaos004-gif/splitaudio/issues/88) |
| Samsung | SM-S938B | Android 17 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#89](https://github.com/monarchsofchaos004-gif/splitaudio/issues/89) |
| TECNO | KM7 | Android 15 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#90](https://github.com/monarchsofchaos004-gif/splitaudio/issues/90) |
| Infinix | X6850 | Android 16 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#91](https://github.com/monarchsofchaos004-gif/splitaudio/issues/91) |
| TECNO | LH7n | Android 14 | 0.5.7 | ❓ | ✅ | ❓ | Custom mode reported working with the split active (four one-tap reports). | [#92](https://github.com/monarchsofchaos004-gif/splitaudio/issues/92) |
| OnePlus | PKG110 | Android 17 | 0.5.7 | ✅ | ❓ | ❓ | Simple mode reported working with the split active (three one-tap reports). | [#93](https://github.com/monarchsofchaos004-gif/splitaudio/issues/93) |
| POCO | 2311DRK48I | Android 16 | 0.5.7 | ❌ | ❓ | ❓ | Reported not working: sound keeps switching between the speaker and Bluetooth while music and a game play together. A fix is being looked into; see the issue. | [#94](https://github.com/monarchsofchaos004-gif/splitaudio/issues/94) |
| POCO | 2311DRK48G | Android 16 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#95](https://github.com/monarchsofchaos004-gif/splitaudio/issues/95) |
| vivo | V2404 | Android 16 | 0.5.7 | ❓ | ⚠️ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#96](https://github.com/monarchsofchaos004-gif/splitaudio/issues/96) |
| Redmi | Redmi Note 9 Pro | Android 15 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#97](https://github.com/monarchsofchaos004-gif/splitaudio/issues/97) |
| OPPO | PLG110 | Android 17 | 0.5.7 | ❓ | ⚠️ | ❓ | The split was active when the report was sent; the app closed once while switching back from the app list, and a fix is being looked into (see the issue). | [#98](https://github.com/monarchsofchaos004-gif/splitaudio/issues/98) |
| Infinix | X6725 | Android 15 | 0.5.7 | ⚠️ | ❓ | ❓ | Shizuku was not running when the reports were sent, so the split could not start yet; a report sent with Shizuku running will settle it. | [#99](https://github.com/monarchsofchaos004-gif/splitaudio/issues/99), [#100](https://github.com/monarchsofchaos004-gif/splitaudio/issues/100) |
| Redmi | M2103K19I | Android 13 | 0.5.7 | ⚠️ | ❓ | ❓ | Reported working; the split had not run yet when the report was sent, so it is waiting for a confirming report. | [#101](https://github.com/monarchsofchaos004-gif/splitaudio/issues/101) |
| POCO | 26077PC53G | Android 16 | 0.5.7 | ❓ | ✅ | ❓ | Custom mode reported working with the split active. | [#102](https://github.com/monarchsofchaos004-gif/splitaudio/issues/102) |
| POCO | 22021211RI | Android 14 | 0.5.7 | ✅ | ❌ | ❓ | Simple mode reported working with the split active ([#105](https://github.com/monarchsofchaos004-gif/splitaudio/issues/105)); Custom mode reported not working, a fix is being looked into ([#104](https://github.com/monarchsofchaos004-gif/splitaudio/issues/104)). | [#104](https://github.com/monarchsofchaos004-gif/splitaudio/issues/104), [#105](https://github.com/monarchsofchaos004-gif/splitaudio/issues/105) |

## How to add a row

**Easiest (0.5.6 and later): tap "It works!" or "Report a problem" in the app.** No account needed: the app sends the
diagnostics (no accounts, contacts or Bluetooth names) and a maintainer adds your row, with you credited. The steps
below are the manual way, for example when you want to write the row yourself.

1. Install the latest release and try **Simple** mode (music app → Bluetooth, everything else → speaker) and, if you like, **Custom** mode.
2. Edit this file on GitHub (pencil icon) and add one line to the table. Keep the columns in order; use ❓ for anything you did not test.
3. Open the pull request. A maintainer merges it, usually within a day. Partial rows are welcome.

If something does not work, please also open a [device report](https://github.com/monarchsofchaos004-gif/splitaudio/issues/new?template=device_report.yml)
so it can be fixed; the row can say ❌ until then.

## 中文說明

每台手機的音訊層都被廠商改過一點，所以能不能用要靠大家回報。**加一行你的機型**就是最有用的貢獻（不用會寫程式）：
在 GitHub 上點鉛筆編輯這個檔、照表格加一行、送出 pull request 即可；沒測到的欄位填 ❓。
不能用的話順便開一個「device report」issue，方便修。
