# Jarvis AI — Android App

Voice-first AI assistant built with Kotlin + Jetpack Compose, powered by Google Gemini.

## Setup

1. Open this folder (`JarvisAI/`) in Android Studio (Koala or newer recommended).
2. Let Gradle sync — no manual dependency setup needed, everything is in `app/build.gradle.kts`.
3. Run on a device/emulator with **Android 8.0 (API 26)** or higher.
4. On first launch, tap the **Settings (gear icon)** → paste your **Gemini API key**
   (get one free at https://aistudio.google.com/apikey) → tap **Save**.
5. Tap **Test Connection** to confirm the key works, then go back and tap the **mic button** to talk.

## What's implemented

- ✅ Animated orb (Idle / Listening / Thinking / Speaking states) — Compose `Canvas`
- ✅ Voice input via `SpeechRecognizer` (Hindi + English selectable in Settings)
- ✅ Voice output via Android `TextToSpeech` (male/female voice selection)
- ✅ Gemini API integration via Retrofit, with conversation history sent for context
- ✅ Encrypted API key storage (`EncryptedSharedPreferences`, AES-256/Keystore-backed)
- ✅ Text input fallback alongside voice
- ✅ Error handling: no internet, invalid key, rate limit — all shown as dismissible banners
- ✅ Runtime `RECORD_AUDIO` permission handling

## Notes / things to double-check before shipping

- **Package name**: currently `com.alphaorder.jarvisai` — change in `build.gradle.kts` (`namespace`,
  `applicationId`) and the folder structure if you want something else.
- **Launcher icon**: uses the system default icon as a placeholder (`sym_def_app_icon`) so the
  project builds immediately. Replace with your own via Android Studio's Image Asset tool
  (`res/mipmap` → right-click → New → Image Asset) before publishing.
- **Gemini model names**: Settings offers `gemini-2.0-flash` and `gemini-2.5-pro`. If Google
  renames/deprecates a model, update the ids in `SettingsScreen.kt`.
- **Room DB conversation history** (optional persistence across app restarts) is *not* included
  yet — current implementation keeps history in-memory for the session only, as the spec allowed
  either. Ask if you want the Room version added.
- **TTS amplitude-driven glow** during Speaking currently reuses `micLevel` as a placeholder
  pulse; true amplitude-reactive glow needs an `AudioTrack`/visualizer hook into the TTS
  output, which can be added as a follow-up.

## Project structure

```
app/src/main/java/com/alphaorder/jarvisai/
 ├── MainActivity.kt
 ├── JarvisApplication.kt
 ├── ui/
 │    ├── HomeScreen.kt
 │    ├── SettingsScreen.kt
 │    ├── components/OrbAnimation.kt
 │    └── theme/ (Color.kt, Theme.kt, Type.kt)
 ├── viewmodel/JarvisViewModel.kt
 ├── data/
 │    ├── GeminiApiService.kt
 │    ├── GeminiRepository.kt
 │    └── SettingsDataStore.kt
 ├── voice/
 │    ├── SpeechToTextManager.kt
 │    └── TextToSpeechManager.kt
 └── util/PermissionHelper.kt
```
