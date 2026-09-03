# Local Whisper dictation experiment

Branch: `test/local-whisper`

## Goal

Replace Android/Google `SpeechRecognizer` for the experimental dictation path with app-owned microphone capture plus local speech recognition, so dictation can start and stop without system beeps.

The stable `main` branch must remain unchanged until the experiment is proven on the target phone.

## First implementation candidate

Use sherpa-onnx Android with a multilingual Whisper model. sherpa-onnx supports fully local Android ASR and provides Android examples and prebuilt native libraries.

## Test acceptance criteria

1. No start beep.
2. Serbian speech is transcribed.
3. Five seconds of silence ends the utterance.
4. No end beep.
5. Transcript is committed into the active `InputConnection`.
6. No system volume/mute manipulation.
7. Measure practical recognition delay on the target phone.

## Architecture

`AudioRecord` (16 kHz mono PCM16) -> app-side RMS/VAD -> local ASR -> existing Serbian text post-processing -> `InputConnection.commitText()`.

The microphone and silence timeout stay under app control. Google `SpeechRecognizer` is not used by this experimental path.

## Implementation stages

- Stage A: establish sherpa-onnx Android dependency/native-library integration and build successfully.
- Stage B: bundle or first-run-download a small multilingual Whisper model suitable for Serbian.
- Stage C: capture one utterance with `AudioRecord`, stop after 5 s silence, transcribe locally, and insert raw transcript.
- Stage D: reconnect existing punctuation/new-line/Cyrillic processing.
- Stage E: compare accuracy, latency, APK/model size and battery/CPU cost against the current Google recognizer.

## Model packaging decision

Do not commit a large speech model into the Git repository. Prefer a build-time test artifact or a controlled first-run model download after the engine integration is proven. This keeps the repository and normal APK small while we experiment.
