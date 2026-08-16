package rs.srpskiglas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public final class SerbianVoiceInputMethod extends InputMethodService
        implements RecognitionListener {
    private SpeechRecognizer recognizer;
    private TextView status;
    private Button micButton;

    @Override public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.keyboard_voice, null);
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int navigationBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                navigationBottom = insets.getInsets(
                        WindowInsets.Type.navigationBars()).bottom;
            } else {
                navigationBottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left, top, right, bottom + navigationBottom);
            return insets;
        });
        view.requestApplyInsets();
        status = view.findViewById(R.id.keyboardStatus);
        micButton = view.findViewById(R.id.keyboardMicButton);
        Button switchButton = view.findViewById(R.id.switchKeyboardButton);
        Button backspace = view.findViewById(R.id.backspaceButton);
        micButton.setOnClickListener(v -> startVoiceInput());
        switchButton.setOnClickListener(v -> switchToNextInputMethod(false));
        backspace.setOnClickListener(v -> deleteOneCharacter());
        return view;
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Отвори Српски глас и дозволи микрофон.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatus("Препознавање говора није доступно");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        } else {
            recognizer.cancel();
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        showStatus("Слушам…");
        micButton.setEnabled(false);
        recognizer.startListening(intent);
    }

    private void deleteOneCharacter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.deleteSurroundingText(1, 0);
    }

    private void showStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void finishListening(String message) {
        showStatus(message);
        if (micButton != null) micButton.setEnabled(true);
    }

    @Override public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            finishListening("Нисам разумео — покушај поново");
            return;
        }
        String converted = SerbianTransliterator.convert(matches.get(0));
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(converted + " ", 1);
        finishListening("Унето — спреман за наставак");
    }

    @Override public void onError(int error) {
        finishListening("Покушај поново (грешка " + error + ")");
    }

    @Override public void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        recognizer = null;
        super.onDestroy();
    }

    @Override public void onReadyForSpeech(Bundle params) { showStatus("Говори…"); }
    @Override public void onBeginningOfSpeech() { showStatus("Препознајем…"); }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { showStatus("Обрађујем…"); }
    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}
}
