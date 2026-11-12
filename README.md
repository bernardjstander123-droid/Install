# Astra 7.1 — Standalone Android AI chat

An independent Android application with a local, HD glass chat interface. The UI is bundled into the APK and works offline. AI answers require internet connectivity and the user's own OpenAI Platform key.

- Native Android WebView host, HTML/CSS/JavaScript chat.
- Chat history is stored locally in WebView localStorage.
- API key is encrypted at rest via Android Keystore AES-GCM and is never embedded in source or APK.
- OpenAI Responses API, with a configurable model ID in app Settings.
- No external Javascript libraries, tracking SDKs, or paid credits beyond the API chosen by the owner.
- The app name **Astra 7.1** is a personal app name, not an assertion that this is an official OpenAI model.
- Successful APK compilation is not a real-device test.

## Build
Java 17, Android SDK 35, Gradle 8.10.2:
```bash
gradle --no-daemon :app:assembleDebug
```
Result: `app/build/outputs/apk/debug/app-debug.apk`.
The GitHub Actions workflow compiles and validates a debug-signed APK and uploads it for download.

## Privacy
A key you supply in Settings is used only for direct requests to `https://api.openai.com/v1/responses`. API requests send the chat context to the provider. Chat history remains locally stored on your phone and can be cleared from within the app. The API key is encrypted on-device. Do not install APKs from sources you do not trust.
