# Bangla TTS Tuner (Android)

A native Android application written in Kotlin and Jetpack Compose for fine-tuning Bangla Text-to-Speech (TTS) pronunciation. It processes Bangla text through user-defined word corrections and pronunciation rules using Microsoft Edge TTS (`edge-tts`) and Google Gemini API respelling.

## Features

- **Tune Pipeline**:
  1. **Deterministic Word Corrections**: Replaces matching base words with placeholders `[[W1]]`, `[[W2]]`... sorted longest-first, with whole-word or substring matching.
  2. **Pronunciation Rules Pass (Gemini)**: Sends text with placeholders and natural-language phonetic pronunciation rules to Google Gemini API (`gemini-flash-latest`) using structured JSON generation.
  3. **Protected Word Restoration**: Restores preferred respellings into placeholders, with automated validation and retry.
  4. **Word-Level Diff View**: Visual LCS diff showing struck-through old text and highlighted new text. Tap any segment to log it as a word correction or natural-language rule.
  5. **Live Audio & Playback**: Synthesize speech via native Edge-TTS WebSocket client with Sec-MS-GEC authentication or on-device Android TextToSpeech fallback.
  6. **Export Audio**: Save generated audio files directly as MP3 via Android Storage Access Framework.
- **Words Screen**:
  - Full CRUD management of word corrections.
  - Searchable list with Whole-word and Enabled chips.
  - Swipe/button to delete with undo Snackbar.
  - Import/Export `rules.md` (Markdown format) and `pronunciation.json` (Manim video tool format).
- **Rules Screen**:
  - Full CRUD management of natural-language pronunciation instructions.
  - Enable/disable toggles per rule.
- **Settings & Audio Diagnostics**:
  - Encrypted Gemini API key storage using AES-GCM (masked to show only the last 4 characters).
  - Built-in "Audio test" diagnostic synthesizing `নমস্কার, এটি একটি পরীক্ষা। সঠিক উত্তর হলো চার।` with complete timestamped protocol logs.
  - Full database backup and restore (JSON format, excluding secrets).
  - Tuning session history (newest 50 sessions, tap to reopen in Tune).

## Tech Stack
- **Language**: Kotlin 2.2.10
- **UI**: Jetpack Compose, Material Design 3
- **Database**: Room 2.8.5 with KSP
- **Networking**: OkHttp 4.12.0
- **Serialization**: Kotlinx Serialization JSON
- **Preferences**: Jetpack DataStore Preferences
- **Security**: AES-GCM Keystore Encryption
