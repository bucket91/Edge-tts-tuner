# Bangla TTS Tuner (Android): one-shot build file for Google AI Studio

**For the human:** create a new AI Studio project, choose the native Android option if there is one, and give Gemini this whole file as the first message (or put it in System instructions and send "Build everything"). It is written so the AI builds the entire app in one session. Afterwards, install the APK and use Settings, Audio test first: if the audio works, everything else will; if not, tap Copy log and send the log to Claude.

---

# TASK

Build a NATIVE ANDROID app called **Bangla TTS Tuner** exactly as specified below, completely, in one session. It is NOT a web app: no React, no WebView, no HTML UI, no Node, no Python. Read all of Part A before writing any code; then do Part B in order; then do Part C.

## PART A: SPECIFICATION (frozen)

You are building **Bangla TTS Tuner**, a NATIVE ANDROID app (Kotlin, Jetpack Compose, Material 3, minSdk 26, single module, `applicationId com.bucket91.ttstuner`). It is NOT a web app: no React, no WebView, no HTML UI, no Node, no Python.

**Working rules**
1. Build the WHOLE app in this one session: complete milestones S0 to S7 of Part B in order, building and fixing compile errors after each milestone, without stopping to ask me anything. When something is ambiguous, choose the simplest reading and list it under `NOTES:` at the very end. Never leave a stub, a `TODO`, or a placeholder screen: every screen and button in this file must work.
2. Names, entities, algorithms and formats in this Part A are FROZEN. If something is ambiguous, choose the simplest reading and list it in a short `NOTES:` section at the end of your reply.
3. At the very end: build a debug APK and fix every compile error and warning that is a real problem; then print (a) the path of the APK, (b) a numbered list of things I should tap to test, with what I should see, (c) the `NOTES:` list.
4. Pure logic (word corrections, diff, import/export, prompt building) goes in plain Kotlin classes without Android imports, with JUnit tests (`src/test`). The UI only calls them.
5. No analytics, no crash reporters, no ads. The only permission is `INTERNET`. User data stays on the device unless the user taps Export or Share.
6. Show errors to the user in plain language (what failed and what to try), never raw stack traces. Keep the technical detail in an expandable "Details" line.

**What the app does.** I paste a Bangla paragraph or sentence. The app turns it into the exact text to send to the edge-tts voice so the speech sounds right, by applying my saved corrections. I can see what changed, edit the result by hand, hear the audio, and log any new correction I make so next time it is applied automatically. I use the app's exports in my video projects.

**A1. Pipeline (frozen)**
1. **Word corrections (deterministic, no AI).** Apply my saved word corrections to the input. Each replaced word becomes a placeholder `[[W1]]`, `[[W2]]`, ... in order of appearance, remembering the replacement text.
2. **Rules pass (Gemini).** Send the text with placeholders and my enabled general rules to Gemini (A4). Gemini may only change the SPELLING of words to guide pronunciation. It must keep placeholders, punctuation, digits, English words, and word order and count unchanged.
3. **Restore.** Put the replacement text back in place of each placeholder. A placeholder that Gemini lost or duplicated invalidates the result: retry once; if still invalid, skip the Gemini result, use step 1's text with placeholders restored, and show the notice "Rules pass skipped: Gemini changed protected words".
4. **Compare.** Show a word-level diff between my original input and the final text (A3).
5. **Edit and speak.** The final text is editable. Speak sends exactly the text in the edit box to edge-tts.
6. **Log.** From the diff the user can save a correction (A5).

**A2. Data model (frozen; Room database, one file)**
- `WordCorrection(id, base: String, preferred: String, note: String, wholeWord: Boolean = true, enabled: Boolean = true, createdAt: Long)`
- `Rule(id, text: String, enabled: Boolean = true, createdAt: Long)`: one natural-language rule per row, for example `স should be written শ when it is pronounced "sh", for example in "সঠিক" write "শঠিক"`.
- `HistoryItem(id, input, stage1, stage2, final, createdAt)`: keep the newest 50.
- Settings (DataStore): `geminiModel` (default `gemini-flash-latest`, editable text), `voice` (default `bn-BD-PradeepNeural`), `rate` (default `+4%`). The Gemini API key is stored encrypted with Android Keystore (AES-GCM; `EncryptedSharedPreferences` is acceptable) and never logged, never shown after saving (show only the last 4 characters), never included in exports.

**A3. Algorithms (frozen)**
- **Word corrections matching:** process entries sorted by `base` length, longest first, only `enabled` ones. For `wholeWord = true` a match requires that the character before and the character after are NOT in the Bengali Unicode block `U+0980..U+09FF` (so `।`, spaces, punctuation and English letters are boundaries). For `wholeWord = false` the base matches anywhere as a substring. Replace left to right, non-overlapping, and compare after Unicode NFC normalization of both the input and `base`.
- **Tokenization for the diff:** tokens are matches of the regex `[\p{L}\p{M}\p{N}_]+|[^\s\p{L}\p{M}\p{N}_]` (a word including Bengali combining marks `\p{M}`, OR a single other symbol); whitespace is ignored. Compute the longest common subsequence of the two token lists. Output segments: `equal`, `replace(oldTokens, newTokens)`, `insert`, `delete`. A word that differs is shown as one `replace` segment with the old word struck through and the new word highlighted.
- **Duplicate-correction check:** adding a `base` that already exists (NFC compared) opens the existing entry for editing instead of creating a second one.

**A4. Gemini request (frozen)**
- `POST https://generativelanguage.googleapis.com/v1beta/models/<geminiModel>:generateContent` with header `x-goog-api-key: <key>` and `Content-Type: application/json`, OkHttp only, 30 s timeout.
- Body: `systemInstruction` with the text below; `contents` with one user part; `generationConfig`: `temperature: 0`, `responseMimeType: "application/json"`, `responseSchema` `{type: OBJECT, properties: {text: {type: STRING}}, required: ["text"]}`.
- System instruction (use these words exactly, `{RULES}` is the numbered list of enabled rules, one per line):
```
You are a respelling assistant for Bangla text-to-speech. You receive Bangla text. Rewrite it ONLY by changing the spelling of words so that a Bangla text-to-speech engine pronounces them the way the rules below say. 
Hard constraints:
- Do not add, remove, reorder, translate, summarize or paraphrase words. The output must have exactly the same number of words as the input.
- Do not change punctuation, digits, English words, line breaks, or any token that looks like [[W1]], [[W2]] and so on. Copy those tokens exactly.
- If no rule applies to a word, copy the word unchanged.
- Apply every rule whenever its condition is met.
Rules:
{RULES}
Return JSON: {"text": "<the rewritten text>"}
```
- The user part is the text with placeholders, nothing else.
- Validation after the response: parse JSON; `text` must be a string; every placeholder from the input appears exactly once and in the same order; the whitespace-separated word count equals the input's. On failure retry once with the same request; on a second failure follow A1 step 3. HTTP 400/401/403 mean a bad key or model: show "Check your Gemini key and model name in Settings". HTTP 429: show "Gemini rate limit, wait a minute".
- With zero enabled rules, skip the Gemini call.

**A5. Logging corrections (frozen)**
- **From a diff segment** (tap it): a bottom sheet with two buttons.
  - **Save as word correction:** prefilled `base` = the old word, `preferred` = the new word; toggle "Whole word only" (default on); optional note. For a multi-word replace, offer one entry per word pair only when the old and new have the same word count, else save the whole span as one entry with `wholeWord = false`.
  - **Save as rule:** opens a text box prefilled with `"<old>" should be written "<new>" when pronounced this way`; I can reword it before saving.
- **From a manual edit:** after I edit the final text and tap **Log my edits**, the app diffs the pre-edit final text against my edit and lists each changed word pair with checkboxes (all checked); **Save selected as word corrections** stores them.
- A saved correction applies from the next run. Show a toast "Saved. Applies from the next run".

**A6. Import / export (frozen formats)**
- **`rules.md`:** a Markdown file for people and for AI assistants.
```
# TTS rules
## General rules
- <rule text>
## Word corrections
| base | preferred | whole word | note |
|---|---|---|---|
| সঠিক | শঠিক | yes | |
```
Only enabled items are exported. Import is additive: new rows are added, duplicates (NFC compared on rule text or on `base`) are skipped, and the app reports "Added N, skipped M".
- **`pronunciation.json`** (used by my Manim video tool):
```
{"format_version": 1,
 "rules": [{"pattern": "<regex>", "replacement": "<preferred>", "note": "<note>"}],
 "keep_s": []}
```
One entry per enabled word correction, in order of `base` length (longest first). `pattern` = the base with regex metacharacters escaped (`Regex.escape` style, but output Python-compatible: escape only `\ . ^ $ * + ? { } [ ] | ( )`), and for `wholeWord = true` wrapped as `(?<![\u0980-\u09FF])<escaped base>(?![\u0980-\u09FF])`, where `\u0980` and `\u09FF` are written literally as backslash, `u` and four hex digits (Python's `re` understands them; inside the JSON file each backslash is doubled, so they appear as `\\u0980`). General rules are NOT exported to this file (they are AI instructions). Import of this file reads each rule back as a word correction only when its pattern has the exact shape produced above; other rules are skipped and counted in "skipped (custom regex)".
- Use the Storage Access Framework (`CreateDocument` / `OpenDocument`) for files, plus a **Share** button for the exports. Also **Copy** buttons that put each export on the clipboard.
- A backup option: **Export backup** and **Import backup** (one JSON file containing rules, word corrections and settings except the key).

**A7. Screens (frozen)**
Bottom navigation with four tabs.
1. **Tune** (home): input box (multi-line, paste button, clear button, character count); button **Tune**; below it three result cards in this order: `1. Word corrections` (shows the text with replaced words highlighted), `2. Gemini rules` (shows the text, or the skipped notice), `Final text` (an editable box); then the diff view (struck-through old, highlighted new; tappable); below, the controls: **Speak** (generate and play), **Stop**, **Save mp3** (to a document the user picks), **Log my edits**. A progress line shows the current step. Everything stays editable and re-runnable; **Tune** again re-runs from the input.
2. **Words**: searchable list of word corrections (base, preferred, chips for whole word and disabled), tap to edit, swipe to delete with undo, a floating **+** button, overflow menu with Import/Export.
3. **Rules**: list of rules with an enable switch, tap to edit, add, delete with undo, Import/Export.
4. **Settings**: Gemini key (masked, **Save**, **Test** which sends a tiny request and reports OK or the plain-language error), model name, voice, rate; **Export backup** / **Import backup**; **History** (newest 50, tap to reopen in Tune, clear all).
Dark and light themes follow the system. Bangla text uses the system Bangla font through Compose `Text`; use a body size of at least 18 sp for Bangla text areas.

**A8. Audio (frozen)**
- Interface: `interface TtsClient { suspend fun synthesize(text: String, voice: String, rate: String, outFile: File, log: (String) -> Unit): Result<Long> }` (bytes written). Implementations: `EdgeTtsClient` (below, primary) and `AndroidTtsFallback` (below, used only when the user turns on the fallback switch in Settings, or when `EdgeTtsClient` fails and the user taps "Use device voice instead").
- **EdgeTtsClient:** native OkHttp WebSocket client for Microsoft Edge's online text-to-speech service (the same one the open-source Python library `edge-tts` uses). No Python, no server. Implement exactly the protocol below. If anything seems out of date, follow the current source of github.com/rany2/edge-tts (`communicate.py`, `drm.py`).

**Protocol (implement exactly; this is how the open-source Python library `edge-tts` works, so if anything here seems out of date, follow the current source of github.com/rany2/edge-tts, files `communicate.py` and `drm.py`):**
1. Constants: `TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"`. Chromium version string `CHROMIUM_FULL_VERSION = "130.0.2849.68"` and its major part `130`. `SEC_MS_GEC_VERSION = "1-" + CHROMIUM_FULL_VERSION`.
2. **Sec-MS-GEC token:** take the current Unix time in seconds (use the corrected clock, see step 6), add `11644473600`, round DOWN to a multiple of `300`, multiply by `10_000_000` (ticks, as a Long), build the string `"<ticks><TRUSTED_CLIENT_TOKEN>"`, take SHA-256 of its ASCII bytes and print it as UPPERCASE hex.
3. **WebSocket URL:** `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=<TRUSTED_CLIENT_TOKEN>&ConnectionId=<random uuid without dashes>&Sec-MS-GEC=<token>&Sec-MS-GEC-Version=<SEC_MS_GEC_VERSION>`
4. **Request headers on the upgrade:** `Pragma: no-cache`, `Cache-Control: no-cache`, `Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold`, `Accept-Encoding: gzip, deflate, br`, `Accept-Language: en-US,en;q=0.9`, `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0`. (Do not let OkHttp add its own User-Agent.)
5. **After the socket opens, send two TEXT messages**, each header block and body separated by a blank line (`\r\n\r\n`), header lines separated by `\r\n`:
   - Message 1 headers: `X-Timestamp:<timestamp>` and `Content-Type:application/json; charset=utf-8` and `Path:speech.config`. Body: `{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}`
   - Message 2 headers: `X-RequestId:<uuid without dashes>` and `Content-Type:application/ssml+xml` and `X-Timestamp:<timestamp>Z` and `Path:ssml`. Body: `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><voice name='<VOICE>'><prosody pitch='+0Hz' rate='<RATE>' volume='+0%'>ESCAPED_TEXT</prosody></voice></speak>`. Escape `&`, `<`, `>` in the text (and `"` and `'`).
   - `<timestamp>` format example: `Mon Jan 05 2026 14:03:09 GMT+0000 (Coordinated Universal Time)` (any valid-looking date string in that shape works).
6. **Receiving:**
   - TEXT messages: parse headers; when `Path:turn.end` arrives, the audio is complete, so close the socket normally and finish.
   - BINARY messages: the first 2 bytes are a big-endian unsigned length `L`; the next `L` bytes are text headers (they contain `Path:audio`); the rest of the message is mp3 data to append to the output. Ignore binary messages whose headers do not have `Path:audio`, and messages with no data after the headers.
   - Write the audio to the output file given to `synthesize`. If zero audio bytes arrive, treat it as a failure.
   - Timeout: 20 seconds total.
7. **Clock skew (important):** if the connection fails with HTTP 403, read the `Date` header of the response, compute `skew = serverDate - deviceNow` in seconds, store it, and retry ONCE using `now + skew` in step 2. Log the skew either way.

- **AndroidTtsFallback:** uses `android.speech.tts.TextToSpeech` with locale `bn-BD` (fall back to `bn-IN`), `synthesizeToFile` into the same cache file name with a `.wav` extension; if no Bangla voice is installed, return a failure whose message says "No Bangla voice installed on this device". It ignores `voice` and `rate` except that `rate` `+N%`/`-N%` maps to `setSpeechRate(1 + N/100)`.
- **Audio diagnostics (Settings, button "Audio test"):** synthesizes the sentence `নমস্কার, এটি একটি পরীক্ষা। সঠিক উত্তর হলো চার।` with the current voice and rate, plays it, and shows the full log in a selectable monospace box with a **Copy log** button. The log must contain what is described here:

log with a timestamp each of these: request start with voice, rate and text length; GEC token (first 8 characters only) and the time used; WebSocket open (with response code); config sent; ssml sent; each binary message received (index, header length, data bytes); the text message paths received; turn.end; total bytes; any failure with the HTTP status code and body, the close code and reason, or the exception class and message; whether the retry was used.

- Cache: mp3 files in `cacheDir/tts/<md5(voice|rate|text)>.mp3` (wav for the fallback); reuse a file if it exists and is larger than 1000 bytes. Cap the cache at 50 files (delete the oldest).
- Playback with `MediaPlayer` (or Media3 ExoPlayer), one player at a time; **Stop** releases it.
- Speak sends the CURRENT text of the editable final box, not the original input.
- If `EdgeTtsClient` fails, the Tune screen shows the plain-language message, a **Details** line with the last log lines, a **Copy log** button, and a button **Use device voice instead** that retries with `AndroidTtsFallback` for this one request.

**A9. Package layout (frozen)**
```
app/src/main/java/com/bucket91/ttstuner/
  MainActivity.kt
  data/        (Room: entities, dao, database; Settings, SecretStore)
  logic/       (WordCorrector.kt, WordDiff.kt, PromptBuilder.kt, GeminiClient.kt, RulesMd.kt, PronunciationJson.kt, Pipeline.kt)
  audio/       (TtsClient.kt interface, EdgeTtsClient.kt, AndroidTtsFallback.kt, AudioCache.kt, Player.kt)
  ui/          (TuneScreen.kt, WordsScreen.kt, RulesScreen.kt, SettingsScreen.kt, theme/, components/)
app/src/test/java/com/bucket91/ttstuner/   (unit tests for logic/)
```

## PART B: MILESTONES (do ALL of them, in this order, in this one session)

**S0: Skeleton.** Project, theme, bottom navigation with four placeholder screens, Room database with the three entities and DAOs, DataStore settings, `SecretStore` (save/load/mask the Gemini key). Settings screen shows the key field with Save and the model/voice/rate fields working and persisted. Test: set the key, close the app, reopen, see `••••1234`.

**S1: Words and Rules screens, rules.md and pronunciation.json.** Full lists with add, edit, delete with undo, search, enable switches, duplicate handling per A3. `RulesMd` and `PronunciationJson` classes with export and additive import (A6), file pickers, Share and Copy buttons. Unit tests: round trip `rules.md` (export then import into an empty database gives the same data), `pronunciation.json` pattern text for a whole-word and a substring entry, duplicate skipping, NFC-equal duplicates. Test: add `সঠিক` → `শঠিক`, export `pronunciation.json`, check the pattern.

**S2: Deterministic pass and diff.** `WordCorrector` (A3 matching with placeholders and restore) and `WordDiff` (A3 tokens, LCS, segments) with unit tests: whole word versus substring, longest first, punctuation `।` as a boundary, a word inside another word is not matched in whole-word mode, Bangla combining marks stay inside one token, placeholders restored exactly, replace/insert/delete segments. No UI beyond a temporary debug button on Tune that runs both and shows the result.

**S3: Gemini client.** `PromptBuilder` (A4 system instruction text with the numbered rules), `GeminiClient` (request, validation, one retry, plain-language errors), the **Test** button in Settings. Unit tests with a fake HTTP layer: valid response, missing placeholder triggers retry, second failure falls back, word-count mismatch, HTTP 400 and 429 messages. Test: the Test button reports OK with my key.

**S4: Tune screen end to end (text only).** `Pipeline` combines A1 steps 1 to 4. Tune screen per A7; the audio buttons may be disabled until S5. Diff view, editable final text, History saved. Test: enter a sentence containing `সঠিক`, see the replaced word highlighted in step 1, Gemini output in step 2, the diff, and edit the final text.

**S5: Audio.** Implement `TtsClient`, `EdgeTtsClient`, `AndroidTtsFallback`, `AudioCache`, `Player`, the Settings **Audio test**, and the Tune buttons Speak, Stop, Save mp3, Use device voice instead, exactly per A8. Unit tests for the pure parts: Sec-MS-GEC token computation with a fixed clock (inject the clock; for the fixed time `1700000000` seconds assert the rounding to a multiple of 300 and the tick value), binary frame parsing (a hand-made frame with a 2-byte header length), SSML escaping, cache naming and the 50-file cap.

**S6: Logging corrections.** Tap-a-diff-segment bottom sheet and **Log my edits** (A5), saving to the Words and Rules lists, toast text as specified. Unit tests for turning a diff into candidate word pairs. Test: change one word in the final text, tap Log my edits, save it, tap Tune again on the same input and see the saved correction applied automatically.

**S7: Polish and backup.** Export backup / Import backup, History screen, empty states, error messages checked against A4 and A7, app icon (simple, original), release build settings (minify off, signing left to me). Final pass: list every screen and the plain-language messages you added.

## PART C: FINAL CHECK (do this before you say you are done)
1. Every screen in A7 exists and every button listed there does something real.
2. No `TODO`, no stub, no `FakeTtsClient`, no hard-coded API key anywhere in the code, logs, exports or backups.
3. All unit tests pass (`./gradlew testDebugUnitTest`) and the debug APK builds.
4. Export `rules.md` and `pronunciation.json` formats match A6 exactly.
5. The app works with no network except for Gemini and the audio service; with the network off, Words, Rules, History, and the word-corrections step of Tune still work and the failing steps show plain-language messages.
6. Then print the final report described in working rule 3.

