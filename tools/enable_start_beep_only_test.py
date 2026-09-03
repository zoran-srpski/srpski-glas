from pathlib import Path

p = Path('app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java')
s = p.read_text(encoding='utf-8')

s = s.replace('    private int startSilenceRetries;\n', '''    private int startSilenceRetries;\n    private String latestPartialTranscript = "";\n    private long quietSinceMs = -1L;\n    private static final float QUIET_RMS_DB = 2.0f;\n    private static final long QUIET_STOP_MS = 5000L;\n''')

s = s.replace('        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);', '        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);')

s = s.replace('        speechStarted = false;\n        listening = true;', '''        speechStarted = false;\n        latestPartialTranscript = "";\n        quietSinceMs = -1L;\n        listening = true;''')

old = '''    @Override public void onRmsChanged(float rmsdB) {}\n    @Override public void onBufferReceived(byte[] buffer) {}\n    @Override public void onEndOfSpeech() { showStatus("Обрађујем…"); }\n    @Override public void onPartialResults(Bundle partialResults) {}\n'''
new = '''    @Override public void onRmsChanged(float rmsdB) {\n        if (!continuousMode || !listening || !speechStarted) return;\n        long now = SystemClock.uptimeMillis();\n        if (rmsdB <= QUIET_RMS_DB) {\n            if (quietSinceMs < 0L) quietSinceMs = now;\n            if (now - quietSinceMs >= QUIET_STOP_MS) {\n                // Gboard-style experiment: cancel instead of stopListening so the\n                // recognizer service does not play its normal end-of-session tone.\n                // Preserve the latest partial transcript before cancelling.\n                String partial = latestPartialTranscript;\n                continuousMode = false;\n                listening = false;\n                setKeepScreenOnWhileDictating(false);\n                if (recognizer != null) recognizer.cancel();\n                if (partial != null && !partial.trim().isEmpty()) {\n                    Bundle b = new Bundle();\n                    ArrayList<String> matches = new ArrayList<>();\n                    matches.add(partial);\n                    b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, matches);\n                    continuousMode = true;\n                    manualStopPending = true;\n                    onResults(b);\n                } else {\n                    manualStopPending = false;\n                    if (micButton != null) micButton.setText(dictationButtonLabel());\n                }\n            }\n        } else {\n            quietSinceMs = -1L;\n        }\n    }\n    @Override public void onBufferReceived(byte[] buffer) {}\n    @Override public void onEndOfSpeech() { showStatus("Обрађујем…"); }\n    @Override public void onPartialResults(Bundle partialResults) {\n        ArrayList<String> matches = partialResults.getStringArrayList(\n                SpeechRecognizer.RESULTS_RECOGNITION);\n        if (matches != null && !matches.isEmpty()) {\n            latestPartialTranscript = matches.get(0);\n        }\n    }\n'''
if old not in s:
    raise SystemExit('listener block not found')
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')
