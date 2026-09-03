from pathlib import Path

p = Path("app/src/main/java/rs/srpskiglas/SerbianVoiceInputMethod.java")
s = p.read_text(encoding="utf-8")
old = """        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        } else {
            recognizer.cancel();
        }"""
new = """        if (recognizer == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
            recognizer.setRecognitionListener(this);
        } else {
            recognizer.cancel();
        }"""
if old not in s:
    raise SystemExit("Expected SpeechRecognizer block not found")
p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("Silent dictation test patch applied")
