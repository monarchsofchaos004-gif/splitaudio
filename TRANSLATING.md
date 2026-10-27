# Translating SplitAudio

SplitAudio is small (about 69 strings), so a full translation takes 15–30 minutes. Thank you for helping!

## Add a new language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`
   (for example `values-ja`, `values-de`, `values-pt-rBR`; see the
   [Android locale list](https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources)).
2. Translate the text between the tags. Keep these exactly as they are:
   - the `name="..."` attributes,
   - placeholders such as `%s`, `%d`, `%1$s`,
   - escapes such as `\'` and `\n`.
3. Delete any line marked `translatable="false"` from your copy.
4. Open a pull request. A partial translation is fine: missing strings fall back to English.

## Fix an existing translation

Edit the file under `app/src/main/res/values-<code>/` and open a pull request with a short note on what was wrong.

No build is needed to contribute; CI builds the APK for every pull request after a maintainer approves the run.
