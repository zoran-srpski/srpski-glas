from pathlib import Path

p = Path('app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java')
s = p.read_text(encoding='utf-8')

s = s.replace('import android.inputmethodservice.InputMethodService;\n', '''import android.inputmethodservice.InputMethodService;\nimport android.media.AudioFormat;\nimport android.media.AudioRecord;\nimport android.media.MediaRecorder;\nimport java.io.ByteArrayOutputStream;\n''', 1)

s = s.replace('    private SpeechRecognizer recognizer;\n    private Button micButton;', '''    private SpeechRecognizer recognizer;\n    private AudioRecord localWhisperAudioRecord;\n    private Thread localWhisperThread;\n    private volatile boolean localWhisperRecording;\n    private static final int LOCAL_WHISPER_SAMPLE_RATE = 16000;\n    private static final long LOCAL_WHISPER_SILENCE_MS = 5000L;\n    private static final double LOCAL_WHISPER_VOICE_RMS = 300.0;\n    private Button micButton;''', 1)

old_start = '''        showStatus("Слушам…");\n        speechStarted = false;\n        listening = true;\n        setKeepScreenOnWhileDictating(true);\n        recognizer.startListening(intent);'''
new_start = '''        showStatus("Слушам…");\n        speechStarted = false;\n        listening = true;\n        setKeepScreenOnWhileDictating(true);\n        startLocalWhisperCapture();'''
if old_start not in s:
    raise SystemExit('startVoiceInput tail not found')
s = s.replace(old_start, new_start, 1)

marker = '    private boolean isInternetAvailable() {'
helpers = r'''    private void startLocalWhisperCapture() {
        stopLocalWhisperCapture(false);
        int minBuffer = AudioRecord.getMinBufferSize(LOCAL_WHISPER_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, 4096);
        try {
            localWhisperAudioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    LOCAL_WHISPER_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (localWhisperAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord init failed");
            }
            localWhisperRecording = true;
            final AudioRecord record = localWhisperAudioRecord;
            record.startRecording();
            localWhisperThread = new Thread(() -> runLocalWhisperCapture(record),
                    "SrpskiGlasLocalWhisper");
            localWhisperThread.start();
        } catch (Exception e) {
            localWhisperRecording = false;
            listening = false;
            continuousMode = false;
            setKeepScreenOnWhileDictating(false);
            if (micButton != null) micButton.setText(dictationButtonLabel());
            showStatus("Локални микрофон није доступан");
        }
    }

    private void runLocalWhisperCapture(AudioRecord record) {
        short[] buf = new short[1024];
        java.util.ArrayList<Float> audio = new java.util.ArrayList<>();
        boolean heardVoice = false;
        long lastVoiceAt = SystemClock.uptimeMillis();
        try {
            while (localWhisperRecording) {
                int n = record.read(buf, 0, buf.length);
                if (n <= 0) continue;
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    float sample = buf[i] / 32768.0f;
                    audio.add(sample);
                    sum += (double) buf[i] * buf[i];
                }
                double rms = Math.sqrt(sum / n);
                long now = SystemClock.uptimeMillis();
                if (rms >= LOCAL_WHISPER_VOICE_RMS) {
                    heardVoice = true;
                    lastVoiceAt = now;
                }
                if (heardVoice && now - lastVoiceAt >= LOCAL_WHISPER_SILENCE_MS) {
                    localWhisperRecording = false;
                }
                if (audio.size() >= LOCAL_WHISPER_SAMPLE_RATE * 28) {
                    localWhisperRecording = false;
                }
            }
        } finally {
            try { record.stop(); } catch (Exception ignored) {}
        }
        float[] samples = new float[audio.size()];
        for (int i = 0; i < samples.length; i++) samples[i] = audio.get(i);
        handler.post(() -> showStatus("Обрађујем локално…"));
        String text = "";
        try {
            if (samples.length > LOCAL_WHISPER_SAMPLE_RATE / 2) {
                text = LocalWhisperBridge.transcribe(this, samples, LOCAL_WHISPER_SAMPLE_RATE);
            }
        } catch (Throwable t) {
            final String error = t.getClass().getSimpleName();
            handler.post(() -> showStatus("Whisper грешка: " + error));
        }
        final String transcript = text == null ? "" : text.trim();
        handler.post(() -> {
            listening = false;
            manualStopPending = true;
            if (!transcript.isEmpty()) {
                Bundle b = new Bundle();
                ArrayList<String> matches = new ArrayList<>();
                matches.add(transcript);
                b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, matches);
                onResults(b);
            } else {
                completeVoiceInputStop();
                showStatus("Није препознат говор");
            }
        });
    }

    private void stopLocalWhisperCapture(boolean cancel) {
        localWhisperRecording = false;
        AudioRecord record = localWhisperAudioRecord;
        localWhisperAudioRecord = null;
        if (record != null) {
            try { record.stop(); } catch (Exception ignored) {}
            if (cancel) record.release();
        }
    }

    private boolean isInternetAvailable() {'''
if marker not in s:
    raise SystemExit('internet marker not found')
s = s.replace(marker, helpers, 1)

old_manual = '''        if (recognizer != null && listening) {\n            recognizer.stopListening();\n            handler.postDelayed(forceManualStop, 3000L);'''
new_manual = '''        if (listening) {\n            localWhisperRecording = false;\n            handler.postDelayed(forceManualStop, 12000L);'''
if old_manual not in s:
    raise SystemExit('manual stop block not found')
s = s.replace(old_manual, new_manual, 1)

p.write_text(s, encoding='utf-8')
print('Local Whisper keyboard test patch applied')
