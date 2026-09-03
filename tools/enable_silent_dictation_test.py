from pathlib import Path

# Fifth test: app-side silence timer using RecognitionListener.onRmsChanged().
p = Path("app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java")
s = p.read_text(encoding="utf-8")

old_import = "import android.inputmethodservice.InputMethodService;\n"
new_import = "import android.inputmethodservice.InputMethodService;\nimport android.media.AudioManager;\n"
if old_import not in s: raise SystemExit("InputMethodService import not found")
s = s.replace(old_import, new_import, 1)

old_fields = """    private SpeechRecognizer recognizer;
    private Button micButton;"""
new_fields = """    private SpeechRecognizer recognizer;
    private AudioManager audioManager;
    private int savedMusicVolume = -1;
    private int savedNotificationVolume = -1;
    private boolean recognitionAudioMuted;
    private long lastVoiceActivityAt;
    private boolean appSilenceStopPending;
    private static final long APP_SILENCE_TIMEOUT_MS = 5000L;
    private static final float VOICE_RMS_THRESHOLD_DB = 2.0f;
    private Button micButton;"""
if old_fields not in s: raise SystemExit("Recognizer field block not found")
s = s.replace(old_fields, new_fields, 1)

# Define the runnable after the Handler and dictation state fields so Java has no forward/self references.
handler_anchor = """    private final Runnable forceManualStop = () -> {
        if (manualStopPending) completeVoiceInputStop();
    };"""
runnable = handler_anchor + """
    private final Runnable appSilenceStop = new Runnable() {
        @Override public void run() {
            if (!continuousMode || !listening || manualStopPending || appSilenceStopPending) return;
            long quietFor = SystemClock.uptimeMillis() - lastVoiceActivityAt;
            if (speechStarted && quietFor >= APP_SILENCE_TIMEOUT_MS) {
                appSilenceStopPending = true;
                muteRecognitionBeepStreams();
                if (recognizer != null) recognizer.stopListening();
                handler.postDelayed(SerbianVoiceInputMethod.this::restoreRecognitionBeepStreams, 1100L);
            } else if (speechStarted) {
                handler.postDelayed(this, Math.max(100L, APP_SILENCE_TIMEOUT_MS - quietFor));
            }
        }
    };"""
if handler_anchor not in s: raise SystemExit("forceManualStop anchor not found")
s = s.replace(handler_anchor, runnable, 1)

old_start = """        showStatus(\"Слушам…\");
        speechStarted = false;
        listening = true;
        setKeepScreenOnWhileDictating(true);
        recognizer.startListening(intent);"""
new_start = """        showStatus(\"Слушам…\");
        speechStarted = false;
        appSilenceStopPending = false;
        lastVoiceActivityAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(appSilenceStop);
        listening = true;
        setKeepScreenOnWhileDictating(true);
        muteRecognitionBeepStreams();
        recognizer.startListening(intent);
        handler.postDelayed(this::restoreRecognitionBeepStreams, 900L);"""
if old_start not in s: raise SystemExit("startListening block not found")
s = s.replace(old_start, new_start, 1)

marker = "    private boolean isInternetAvailable() {"
helpers = """    private void muteRecognitionBeepStreams() {
        if (recognitionAudioMuted) return;
        if (audioManager == null) audioManager = getSystemService(AudioManager.class);
        if (audioManager == null || audioManager.isVolumeFixed()) return;
        try {
            savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            savedNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            recognitionAudioMuted = true;
        } catch (RuntimeException ignored) { recognitionAudioMuted = false; }
    }

    private void restoreRecognitionBeepStreams() {
        if (!recognitionAudioMuted || audioManager == null) return;
        try {
            if (savedMusicVolume >= 0) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0);
            if (savedNotificationVolume >= 0) audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotificationVolume, 0);
        } catch (RuntimeException ignored) {
        } finally {
            recognitionAudioMuted = false;
            savedMusicVolume = -1;
            savedNotificationVolume = -1;
        }
    }

    private boolean isInternetAvailable() {"""
if marker not in s: raise SystemExit("Internet helper marker not found")
s = s.replace(marker, helpers, 1)

old_continue = """    private void continueListening() {
        listening = false;
        if (continuousMode) handler.postDelayed(this::startVoiceInput, 100);
    }"""
new_continue = """    private void continueListening() {
        handler.removeCallbacks(appSilenceStop);
        listening = false;
        continuousMode = false;
        appSilenceStopPending = false;
        setKeepScreenOnWhileDictating(false);
        if (micButton != null) micButton.setText(dictationButtonLabel());
        showStatus(\"Заустављено — спреман\");
    }"""
if old_continue not in s: raise SystemExit("continueListening block not found")
s = s.replace(old_continue, new_continue, 1)

old_empty = """        if (matches == null || matches.isEmpty()) {
            if (manualStopPending) completeVoiceInputStop();
            else handler.postDelayed(this::startVoiceInput, 120);
            return;
        }"""
new_empty = """        if (matches == null || matches.isEmpty()) {
            handler.removeCallbacks(appSilenceStop);
            if (manualStopPending) completeVoiceInputStop();
            else finishDictationAfterPause();
            return;
        }"""
if old_empty not in s: raise SystemExit("empty results block not found")
s = s.replace(old_empty, new_empty, 1)

old_recover = """        if (continuousMode && isRecoverableRecognitionError(error)) {
            startSilenceRetries++;
            handler.postDelayed(this::startVoiceInput, 200);
            return;
        }"""
new_recover = """        if (continuousMode && isRecoverableRecognitionError(error)) {
            startSilenceRetries++;
            handler.removeCallbacks(appSilenceStop);
            finishDictationAfterPause();
            return;
        }"""
if old_recover not in s: raise SystemExit("recoverable error block not found")
s = s.replace(old_recover, new_recover, 1)

old_stop = """        if (recognizer != null && listening) {
            recognizer.stopListening();
            handler.postDelayed(forceManualStop, 3000L);"""
new_stop = """        if (recognizer != null && listening) {
            handler.removeCallbacks(appSilenceStop);
            muteRecognitionBeepStreams();
            recognizer.stopListening();
            handler.postDelayed(this::restoreRecognitionBeepStreams, 1100L);
            handler.postDelayed(forceManualStop, 3000L);"""
if old_stop not in s: raise SystemExit("manual stop block not found")
s = s.replace(old_stop, new_stop, 1)

old_rms = "    @Override public void onRmsChanged(float rmsdB) {}"
new_rms = """    @Override public void onRmsChanged(float rmsdB) {
        if (!continuousMode || !listening || manualStopPending || appSilenceStopPending) return;
        if (rmsdB >= VOICE_RMS_THRESHOLD_DB) {
            speechStarted = true;
            lastVoiceActivityAt = SystemClock.uptimeMillis();
            handler.removeCallbacks(appSilenceStop);
            handler.postDelayed(appSilenceStop, APP_SILENCE_TIMEOUT_MS);
        }
    }"""
if old_rms not in s: raise SystemExit("onRmsChanged block not found")
s = s.replace(old_rms, new_rms, 1)

p.write_text(s, encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text(encoding="utf-8")
permission = '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n'
if "android.permission.MODIFY_AUDIO_SETTINGS" not in m:
    anchor = '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
    if anchor not in m: raise SystemExit("Manifest RECORD_AUDIO permission not found")
    m = m.replace(anchor, anchor + permission, 1)
manifest.write_text(m, encoding="utf-8")
print("Fifth test fixed: app-side five-second RMS silence timer patch applied")
