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

## Model availability troubleshooting (v1.3)

In Settings, use **Load models available to my API key**. The app calls
`GET https://api.openai.com/v1/models` using the locally encrypted key
and shows returned text model IDs. Selecting one fills the model ID field.
Then tap **Check selected model access**, followed by **Save settings**.

The model list is specific to the account/project associated with your key.
Even a listed model can reject a Responses API request if its usage, key
permissions, regional availability, billing, or parameters differ.

**API key saved** only confirms there is an encrypted string on the device.
It is not an authentication or model-access success indicator. For a
`401`, replace an invalid key. For `403/404`, check the project's model
usage permissions. For `429`, check Platform billing and limits. For
`400`, inspect the sanitized error code and try another supported model.
No API secret is added to logs or GitHub artifacts.

## Astra 7.1 3D tracking (v1.2)

Tap **📷 Start camera** to request Android camera permission and activate front-camera
face/upper-body tracking. ML Kit face and pose detectors operate on-camera frames
**on the device**, returning only yaw/pitch/roll and a few arm/torso coordinates to
the bundled 3D avatar. The bundled lightweight pose detector recognizes up to
33 body landmarks internally; the app only uses shoulder, hip and wrist positions.

Tap **🎙 Start mic** to request microphone permission and react to sound energy
by moving the avatar's mouth. **Microphone input is not speech recognition**;
the app does not save, transcribe or upload raw microphone audio. The pre-existing
optional text-to-speech output remains separate.

**Permissions are off by default.** The user must approve each Android permission
dialog and start each sensor. Stop buttons turn capture off immediately; both
sensors also stop whenever the activity pauses. Android may show its camera/mic
privacy indicator while these are active. System Settings can revoke permissions.

For clearer pose tracking, stand far enough away to fit shoulders and both arms
in the front camera. Detection depends on lighting, orientation and hardware.
The app contains no biometric login, identity recognition, or remote surveillance.
The browser demo cannot access Android native ML Kit tracking; use the APK.

**Unverified until tested on hardware:** front-camera capture rate, pose accuracy,
GPU thermals/battery usage, speech engine availability and Galaxy A07 performance.
A passing CI build or simulated WebGL test does not prove live camera/mic function.
