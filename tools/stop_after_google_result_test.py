from pathlib import Path

path = Path('app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java')
s = path.read_text(encoding='utf-8')

old = '''        if (manualStopPending) completeVoiceInputStop();
        else continueListening();
        // Refresh Shift so the next manually typed letter starts in uppercase'''
new = '''        // Test mode: one Google recognition session per Dictate press.
        // Never auto-restart after Google returns the final result.
        finishDictationAfterPause();
        // Refresh Shift so the next manually typed letter starts in uppercase'''
if old not in s:
    raise SystemExit('onResults restart block not found')
s = s.replace(old, new, 1)

old_empty = '''        if (matches == null || matches.isEmpty()) {
            if (manualStopPending) completeVoiceInputStop();
            else handler.postDelayed(this::startVoiceInput, 120);
            return;
        }'''
new_empty = '''        if (matches == null || matches.isEmpty()) {
            finishDictationAfterPause();
            return;
        }'''
if old_empty not in s:
    raise SystemExit('empty-result restart block not found')
s = s.replace(old_empty, new_empty, 1)

old_error = '''        if (continuousMode && isRecoverableRecognitionError(error)) {
            startSilenceRetries++;
            handler.postDelayed(this::startVoiceInput, 200);
            return;
        }'''
new_error = '''        if (continuousMode && isRecoverableRecognitionError(error)) {
            // Test mode: do not create a new recognizer session automatically.
            finishDictationAfterPause();
            showRecognitionError(error);
            return;
        }'''
if old_error not in s:
    raise SystemExit('recoverable-error restart block not found')
s = s.replace(old_error, new_error, 1)

path.write_text(s, encoding='utf-8')
print('Applied one-session dictation test patch')
