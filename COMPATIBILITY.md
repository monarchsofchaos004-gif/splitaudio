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
| Google | Pixel 10 Pro XL | Android 17 | 0.5.6 | ❓ | ⚠️ | ✅ | Custom mode routes and the volume keys work; videos in other apps were reported to still pause the music with "Videos don't pause the music" on (being looked into). | [#17](https://github.com/monarchsofchaos004-gif/splitaudio/issues/17) (in-app report) |
| Redmi | 2411DRN47I | Android 16 | 0.5.6 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#15](https://github.com/monarchsofchaos004-gif/splitaudio/issues/15) (in-app report) |
| Redmi | 2303CRA44A | Android 15 | 0.5.6 | ⚠️ | ❓ | ❓ | Reported working; Shizuku was not running when the report was sent, so it is waiting for a confirming report. | [#16](https://github.com/monarchsofchaos004-gif/splitaudio/issues/16) (in-app report) |

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
