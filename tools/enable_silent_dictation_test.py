from pathlib import Path

p = Path("app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java")
s = p.read_text(encoding="utf-8")

old_import = "import android.inputmethodservice.InputMethodService;\n"
new_import = "import android.inputmethodservice.InputMethodService;\nimport android.media.AudioManager;\n"
if old_import not in s:
    raise SystemExit("InputMethodService import not found")
s = s.replace(old_import, new_import, 1)

old_fields = """    private SpeechRecognizer recognizer;
    private Button micButton;"""
new_fields = """    private SpeechRecognizer recognizer;
    private AudioManager audioManager;
    private int savedMusicVolume = -1;
    private int savedNotificationVolume = -1;
    private boolean recognitionAudioMuted;
    private Button micButton;"""
if old_fields not in s:
    raise SystemExit("Recognizer field block not found")
s = s.replace(old_fields, new_fields, 1)

old_start = """        showStatus(\"Слушам…\");
        speechStarted = false;
        listening = true;
        setKeepScreenOnWhileDictating(true);
        recognizer.startListening(intent);"""
new_start = """        showStatus(\"Слушам…\");
        speechStarted = false;
        listening = true;
        setKeepScreenOnWhileDictating(true);
        muteRecognitionBeepStreams();
        recognizer.startListening(intent);
        handler.postDelayed(this::restoreRecognitionBeepStreams, 450L);"""
if old_start not in s:
    raise SystemExit("startListening block not found")
s = s.replace(old_start, new_start, 1)

marker = """    private boolean isInternetAvailable() {"""
helpers = """    private void muteRecognitionBeepStreams() {
        if (recognitionAudioMuted) return;
        if (audioManager == null) {
            audioManager = getSystemService(AudioManager.class);
        }
        if (audioManager == null || audioManager.isVolumeFixed()) return;
        try {
            savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            savedNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            recognitionAudioMuted = true;
        } catch (RuntimeException ignored) {
            recognitionAudioMuted = false;
        }
    }

    private void restoreRecognitionBeepStreams() {
        if (!recognitionAudioMuted || audioManager == null) return;
        try {
            if (savedMusicVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0);
            }
            if (savedNotificationVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotificationVolume, 0);
            }
        } catch (RuntimeException ignored) {
        } finally {
            recognitionAudioMuted = false;
            savedMusicVolume = -1;
            savedNotificationVolume = -1;
        }
    }

    private boolean isInternetAvailable() {"""
if marker not in s:
    raise SystemExit("Internet helper marker not found")
s = s.replace(marker, helpers, 1)

old_stop = """        if (recognizer != null && listening) {
            recognizer.stopListening();
            handler.postDelayed(forceManualStop, 3000L);"""
new_stop = """        if (recognizer != null && listening) {
            muteRecognitionBeepStreams();
            recognizer.stopListening();
            handler.postDelayed(this::restoreRecognitionBeepStreams, 650L);
            handler.postDelayed(forceManualStop, 3000L);"""
if old_stop not in s:
    raise SystemExit("manual stop block not found")
s = s.replace(old_stop, new_stop, 1)

p.write_text(s, encoding="utf-8")

manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text(encoding="utf-8")
permission = '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n'
if "android.permission.MODIFY_AUDIO_SETTINGS" not in m:
    anchor = '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
    if anchor not in m:
        raise SystemExit("Manifest RECORD_AUDIO permission not found")
    m = m.replace(anchor, anchor + permission, 1)
manifest.write_text(m, encoding="utf-8")

print("Temporary beep suppression test patch applied")
