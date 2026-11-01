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

## How to add a row

1. Install the latest release and try **Simple** mode (music app → Bluetooth, everything else → speaker) and, if you like, **Custom** mode.
2. Edit this file on GitHub (pencil icon) and add one line to the table. Keep the columns in order; use ❓ for anything you did not test.
3. Open the pull request. A maintainer merges it, usually within a day. Partial rows are welcome.

If something does not work, please also open a [device report](https://github.com/monarchsofchaos004-gif/splitaudio/issues/new?template=device_report.yml)
so it can be fixed; the row can say ❌ until then.

## 中文說明

每台手機的音訊層都被廠商改過一點，所以能不能用要靠大家回報。**加一行你的機型**就是最有用的貢獻（不用會寫程式）：
在 GitHub 上點鉛筆編輯這個檔、照表格加一行、送出 pull request 即可；沒測到的欄位填 ❓。
不能用的話順便開一個「device report」issue，方便修。
