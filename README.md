# 🎙️ YapText Android — Voice Dictation + AI Polishing

The Android port of [YapText iOS](https://github.com/moshbari/yaptext-ios-app). An Android
app with a custom keyboard that transcribes your voice via the YapText API
(**OpenAI Whisper** / **Sarvam**) and polishes the text with **GPT-4o-mini**.
Speak messy, get clean text out.

![Android](https://img.shields.io/badge/Android-8.0%2B-green) ![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![Compose](https://img.shields.io/badge/Jetpack%20Compose-yes-brightgreen) ![License](https://img.shields.io/badge/license-MIT-green)

> Same backend as iOS/Mac/Chrome/Web — transcription and polish output are
> identical across every platform. API keys live only on the server.

## Features

- **Voice transcription** — English, Bengali & Banglish (server picks the engine)
- **Polish with AI** — 8 tones powered by GPT-4o-mini
- **Auto-copy to clipboard** — text copies automatically after transcription
- **Custom keyboard** — blue QWERTY keyboard with an orange mic key (`InputMethodService`)
- **Keyboard dictation** — tap the mic → app records → text auto-inserts when you return to the keyboard
- **Home / lock screen widget** — one tap to start dictating
- **Auto-stop** — stops after 30 s of silence; hard 5-minute cap per session
- **Free trial + Pro** — 30 minutes free, then unlock unlimited via JVZoo

## AI Polish Tones

Professional · Casual · Friendly Pro · Executive · Supportive · Creator · Academic · Simple

## How it maps to the iOS app

| iOS (Swift) | Android (Kotlin) |
|---|---|
| `WhisperService` | `data/TranscriptionService.kt` (MediaRecorder + OkHttp) |
| `PolishService` | `data/PolishService.kt` |
| `Config.swift` | `Config.kt` |
| `SubscriptionManager` (RevenueCat) | `data/SubscriptionManager.kt` + `LicenseService.kt` (JVZoo) |
| `ContentView` / `PaywallView` / `SettingsView` | `ui/MainScreen.kt` / `PaywallScreen.kt` / `SettingsScreen.kt` (Compose) |
| `KeyboardViewController` (keyboard extension) | `ime/YapTextKeyboardService.kt` (`InputMethodService`) |
| `YapTextWidget` (WidgetKit) | `widget/YapTextWidgetProvider.kt` (App Widget) |
| App Group `UserDefaults` | `data/AppStorage.kt` (`SharedPreferences`, same package) |
| `yaptext://` URL scheme | `yaptext://` deep link + Intent extras |

## Payments — JVZoo

Unlike the iOS build (RevenueCat / App Store), Pro on Android is sold through
**JVZoo**:

1. The paywall opens the JVZoo checkout URL in the browser (`Config.JVZOO_CHECKOUT_URL`).
2. After purchase the buyer gets a **license key** and enters it in the app.
3. The app validates it against the server (`Config.LICENSE_VERIFY_PATH`) and unlocks Pro locally.

**You still need to:**
- Set `JVZOO_CHECKOUT_URL` to your real JVZoo product link in `Config.kt`.
- Implement the `/verify-license` route on the Railway server (it should call
  the JVZoo transaction/IPN API and return `{ "valid": true }` for paid keys).
  Until then, license unlock fails closed and only the free trial works.

> ⚠️ Google Play policy generally requires in-app digital goods to use Google
> Play Billing. The JVZoo flow is appropriate for direct/sideloaded
> distribution; if you publish on the Play Store you'll likely need Play
> Billing instead.

## Build & run

1. Open the project in **Android Studio** (Koala or newer).
2. Let it sync Gradle (downloads dependencies + generates the wrapper if needed).
3. Pick a device/emulator (Android 8.0 / API 26+) and press **Run**.

Or from the command line (once the Gradle wrapper jar is present):

```bash
./gradlew assembleDebug      # build the APK
./gradlew installDebug       # install on a connected device
```

### Enable the keyboard

Settings → System → Languages & input → On-screen keyboard → Manage keyboards →
enable **YapText Keyboard**, then switch to it with the 🌐 key.

### Permissions

The app requests **microphone** access on first launch (required for recording).

## Architecture

```
app/src/main/java/com/moshbari/yaptext/
├── YapTextApp.kt              → Application (init storage + subscription)
├── Config.kt                  → backend URLs, app secret, limits, JVZoo
├── MainActivity.kt            → Compose host, mic permission, deep links, clipboard
├── data/
│   ├── AppStorage.kt          → SharedPreferences (replaces iOS App Group)
│   ├── Models.kt              → Language + Tone enums
│   ├── Http.kt                → shared OkHttp client
│   ├── TranscriptionService.kt→ record + upload to /transcribe
│   ├── PolishService.kt       → POST /polish
│   ├── LicenseService.kt      → JVZoo license verification
│   └── SubscriptionManager.kt → Pro state + free-trial gate
├── ui/
│   ├── MainViewModel.kt
│   ├── MainScreen.kt          → recording UI + tone picker
│   ├── PaywallScreen.kt       → JVZoo checkout + license unlock
│   ├── SettingsScreen.kt
│   └── theme/Theme.kt
├── ime/YapTextKeyboardService.kt → custom keyboard
└── widget/YapTextWidgetProvider.kt → home/lock widget
```

## Requirements

- Android Studio Koala+ / AGP 8.5
- Android 8.0 (API 26) or newer
- The YapText API server (shared with iOS)

## License

MIT — Built by Mosh Bari. Ported to Android with Claude.
