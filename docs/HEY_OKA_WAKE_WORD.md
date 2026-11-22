# HEY OKA offline voice activation

This branch replaces the Picovoice/Porcupine dependency with Vosk Android. No Picovoice account, AccessKey, or custom `.ppn` file is required.

## How it works

- The user explicitly starts the microphone foreground service from a visible Activity after granting microphone permission.
- On first use, the app downloads the public `vosk-model-small-en-us-0.15` model (about 40 MB) from the Vosk model host and extracts it into app-private storage.
- The phrase grammar is constrained to `hey oka`; subsequent recognition runs locally without internet.
- When the phrase is recognized, listening stops and an Android notification offers to open HEY OKA.
- A persistent notification includes a **Matikan** action. The service does not auto-start at boot.

## Important limitations

Vosk is offline speech recognition with grammar constraints, not a dedicated low-power wake-word detector. Continuous microphone capture can consume battery and may be less accurate at recognizing a short custom phrase such as “Hey Oka”. The initial model download requires internet; after successful download, recognition itself is offline.

The current service is a technical foundation. A user-facing settings toggle, direct handoff into the chat voice session, model download progress/retry UI, and real-device testing are still required before calling the feature production-ready.

## Model and license

The selected small US English model is listed by Vosk as a mobile model under Apache 2.0. The model is downloaded at runtime rather than checked into this repository. See https://alphacephei.com/vosk/models and https://alphacephei.com/vosk/android.

## Runtime entry points

- Start: `HeyOkaWakeWordService.start(context)`, only from a visible Activity after explicit user opt-in and `RECORD_AUDIO` permission.
- Stop: `HeyOkaWakeWordService.stop(context)`.
