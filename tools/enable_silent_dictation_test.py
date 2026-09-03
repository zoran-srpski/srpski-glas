from pathlib import Path

# Seventh test: app owns AudioRecord and feeds PCM to SpeechRecognizer via EXTRA_AUDIO_SOURCE.
# Goal: test whether bypassing recognizer-owned microphone removes system start/stop beeps.
p = Path("app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java")
s = p.read_text(encoding="utf-8")

old_import = "import android.inputmethodservice.InputMethodService;\n"
new_import = """import android.inputmethodservice.InputMethodService;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
"""
if old_import not in s: raise SystemExit("InputMethodService import not found")
s = s.replace(old_import, new_import, 1)

old_fields = """    private SpeechRecognizer recognizer;
    private Button micButton;"""
new_fields = """    private SpeechRecognizer recognizer;
    private AudioRecord dictationAudioRecord;
    private ParcelFileDescriptor dictationAudioRead;
    private ParcelFileDescriptor dictationAudioWrite;
    private Thread dictationAudioThread;
    private volatile boolean dictationAudioRunning;
    private long lastVoiceActivityAt;
    private boolean appSilenceStopPending;
    private static final long APP_SILENCE_TIMEOUT_MS = 5000L;
    private static final int DICTATION_SAMPLE_RATE = 16000;
    private static final double VOICE_RMS_AMPLITUDE = 450.0;
    private Button micButton;"""
if old_fields not in s: raise SystemExit("Recognizer field block not found")
s = s.replace(old_fields, new_fields, 1)

# Insert audio-pipe helpers before network helper.
marker = "    private boolean isInternetAvailable() {"
helpers = """    private boolean prepareDictationAudioSource(Intent intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        stopDictationAudioSource();
        try {
            int minBuffer = AudioRecord.getMinBufferSize(DICTATION_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(minBuffer, 4096);
            dictationAudioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    DICTATION_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (dictationAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                stopDictationAudioSource();
                return false;
            }
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            dictationAudioRead = pipe[0];
            dictationAudioWrite = pipe[1];
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, dictationAudioRead);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                    DICTATION_SAMPLE_RATE);
            dictationAudioRunning = true;
            speechStarted = false;
            lastVoiceActivityAt = SystemClock.uptimeMillis();
            appSilenceStopPending = false;
            dictationAudioRecord.startRecording();
            final AudioRecord record = dictationAudioRecord;
            final ParcelFileDescriptor writeSide = dictationAudioWrite;
            dictationAudioThread = new Thread(() -> pumpDictationAudio(record, writeSide),
                    "SrpskiGlasAudioPipe");
            dictationAudioThread.start();
            return true;
        } catch (Exception e) {
            stopDictationAudioSource();
            return false;
        }
    }

    private void pumpDictationAudio(AudioRecord record, ParcelFileDescriptor writeSide) {
        short[] samples = new short[1024];
        byte[] pcm = new byte[samples.length * 2];
        try (FileOutputStream out = new FileOutputStream(writeSide.getFileDescriptor())) {
            while (dictationAudioRunning) {
                int count = record.read(samples, 0, samples.length);
                if (count <= 0) continue;
                double sumSquares = 0.0;
                int j = 0;
                for (int i = 0; i < count; i++) {
                    short sample = samples[i];
                    sumSquares += (double) sample * sample;
                    pcm[j++] = (byte) (sample & 0xff);
                    pcm[j++] = (byte) ((sample >> 8) & 0xff);
                }
                double rms = Math.sqrt(sumSquares / count);
                long now = SystemClock.uptimeMillis();
                if (rms >= VOICE_RMS_AMPLITUDE) {
                    speechStarted = true;
                    lastVoiceActivityAt = now;
                } else if (speechStarted && now - lastVoiceActivityAt >= APP_SILENCE_TIMEOUT_MS) {
                    appSilenceStopPending = true;
                    dictationAudioRunning = false;
                }
                out.write(pcm, 0, count * 2);
            }
        } catch (IOException ignored) {
        } finally {
            try { record.stop(); } catch (Exception ignored) {}
            try { writeSide.close(); } catch (IOException ignored) {}
            handler.post(() -> {
                if (appSilenceStopPending && continuousMode) {
                    listening = false;
                    showStatus(\"Обрађујем…\");
                }
            });
        }
    }

    private void stopDictationAudioSource() {
        dictationAudioRunning = false;
        AudioRecord record = dictationAudioRecord;
        dictationAudioRecord = null;
        if (record != null) {
            try { record.stop(); } catch (Exception ignored) {}
            record.release();
        }
        if (dictationAudioWrite != null) {
            try { dictationAudioWrite.close(); } catch (IOException ignored) {}
            dictationAudioWrite = null;
        }
        if (dictationAudioRead != null) {
            try { dictationAudioRead.close(); } catch (IOException ignored) {}
            dictationAudioRead = null;
        }
        dictationAudioThread = null;
    }

    private boolean isInternetAvailable() {"""
if marker not in s: raise SystemExit("Internet helper marker not found")
s = s.replace(marker, helpers, 1)

old_start = """        showStatus(\"Слушам…\");
        speechStarted = false;
        listening = true;
        setKeepScreenOnWhileDictating(true);
        recognizer.startListening(intent);"""
new_start = """        showStatus(\"Слушам…\");
        speechStarted = false;
        listening = true;
        setKeepScreenOnWhileDictating(true);
        if (!prepareDictationAudioSource(intent)) {
            listening = false;
            continuousMode = false;
            setKeepScreenOnWhileDictating(false);
            micButton.setText(dictationButtonLabel());
            showStatus(\"Аудио извор није доступан\");
            return;
        }
        recognizer.startListening(intent);"""
if old_start not in s: raise SystemExit("startListening block not found")
s = s.replace(old_start, new_start, 1)

old_stop_voice = """    private void stopVoiceInput() {
        manualStopPending = false;
        continuousMode = false;
        listening = false;"""
new_stop_voice = """    private void stopVoiceInput() {
        stopDictationAudioSource();
        manualStopPending = false;
        continuousMode = false;
        listening = false;"""
if old_stop_voice not in s: raise SystemExit("stopVoiceInput block not found")
s = s.replace(old_stop_voice, new_stop_voice, 1)

old_manual = """        if (recognizer != null && listening) {
            recognizer.stopListening();
            handler.postDelayed(forceManualStop, 3000L);"""
new_manual = """        if (recognizer != null && listening) {
            appSilenceStopPending = true;
            dictationAudioRunning = false;
            handler.postDelayed(forceManualStop, 3000L);"""
if old_manual not in s: raise SystemExit("manual stop block not found")
s = s.replace(old_manual, new_manual, 1)

old_complete = """    private void completeVoiceInputStop() {
        handler.removeCallbacks(forceManualStop);"""
new_complete = """    private void completeVoiceInputStop() {
        stopDictationAudioSource();
        handler.removeCallbacks(forceManualStop);"""
if old_complete not in s: raise SystemExit("completeVoiceInputStop block not found")
s = s.replace(old_complete, new_complete, 1)

old_continue = """    private void continueListening() {
        listening = false;
        if (continuousMode) handler.postDelayed(this::startVoiceInput, 100);
    }"""
new_continue = """    private void continueListening() {
        stopDictationAudioSource();
        listening = false;
        continuousMode = false;
        appSilenceStopPending = false;
        setKeepScreenOnWhileDictating(false);
        if (micButton != null) micButton.setText(dictationButtonLabel());
        showStatus(\"Заустављено — спреман\");
    }"""
if old_continue not in s: raise SystemExit("continueListening block not found")
s = s.replace(old_continue, new_continue, 1)

old_finish = """    private void finishDictationAfterPause() {
        continuousMode = false;"""
new_finish = """    private void finishDictationAfterPause() {
        stopDictationAudioSource();
        continuousMode = false;"""
if old_finish not in s: raise SystemExit("finishDictationAfterPause block not found")
s = s.replace(old_finish, new_finish, 1)

old_empty = """        if (matches == null || matches.isEmpty()) {
            if (manualStopPending) completeVoiceInputStop();
            else handler.postDelayed(this::startVoiceInput, 120);
            return;
        }"""
new_empty = """        if (matches == null || matches.isEmpty()) {
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
            finishDictationAfterPause();
            return;
        }"""
if old_recover not in s: raise SystemExit("recoverable error block not found")
s = s.replace(old_recover, new_recover, 1)

p.write_text(s, encoding="utf-8")
print("Seventh test: app AudioRecord -> SpeechRecognizer EXTRA_AUDIO_SOURCE pipe applied")
