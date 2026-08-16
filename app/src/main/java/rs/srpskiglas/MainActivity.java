package rs.srpskiglas;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final int SPEECH_REQUEST = 41;
    private static final int AUDIO_PERMISSION_REQUEST = 42;
    private EditText resultText;
    private TextView step3Status;
    private Button microphonePermissionButton;
    private boolean startDictationAfterPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        step3Status = findViewById(R.id.step3Status);
        microphonePermissionButton =
                findViewById(R.id.microphonePermissionButton);
        Button dictate = findViewById(R.id.dictateButton);
        Button copy = findViewById(R.id.copyButton);
        Button enableKeyboard = findViewById(R.id.enableKeyboardButton);
        Button selectKeyboard = findViewById(R.id.selectKeyboardButton);

        enableKeyboard.setOnClickListener(v -> {
            try {
                startActivity(new Intent(
                        Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this,
                        "Отворите Подешавања → Тастатуре и укључите „Српски глас“.",
                        Toast.LENGTH_LONG).show();
            }
        });

        selectKeyboard.setOnClickListener(v -> {
            try {
                InputMethodManager manager = (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (manager != null) manager.showInputMethodPicker();
            } catch (Exception e) {
                Toast.makeText(this,
                        "Изаберите „Српски глас“ преко дугмета за промену тастатуре.",
                        Toast.LENGTH_LONG).show();
            }
        });

        microphonePermissionButton.setOnClickListener(
                v -> requestAudioPermission(false));
        dictate.setOnClickListener(v -> ensurePermissionAndDictate());
        copy.setOnClickListener(v -> copyResult());
        updateMicrophoneStatus();
    }

    private void updateMicrophoneStatus() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            step3Status.setText("✓  3. Микрофон је дозвољен");
            microphonePermissionButton.setText(
                    "Микрофон је већ дозвољен");
            microphonePermissionButton.setEnabled(false);
        } else {
            step3Status.setText("③ Дозволите микрофон");
            microphonePermissionButton.setText("Дозволи микрофон");
            microphonePermissionButton.setEnabled(true);
        }
    }

    private void requestAudioPermission(boolean dictateAfterGrant) {
        startDictationAfterPermission = dictateAfterGrant;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST);
        } else if (dictateAfterGrant) {
            startDictation();
        } else {
            updateMicrophoneStatus();
        }
    }

    private void ensurePermissionAndDictate() {
        requestAudioPermission(true);
    }

    private void startDictation() {
        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Говори на српском");
        try {
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Google препознавање говора није доступно.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(
                requestCode, permissions, results);
        if (requestCode != AUDIO_PERMISSION_REQUEST) return;
        boolean granted = results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED;
        updateMicrophoneStatus();
        if (granted && startDictationAfterPermission) startDictation();
        startDictationAfterPermission = false;
    }

    @Override protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SPEECH_REQUEST
                || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> matches =
                data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
        if (matches != null && !matches.isEmpty()) {
            resultText.setText(
                    SerbianTransliterator.convert(matches.get(0)));
            resultText.setSelection(resultText.length());
        }
    }

    private void copyResult() {
        String text = resultText.getText().toString();
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText("Српски текст", text));
        Toast.makeText(this,
                "Текст је копиран.", Toast.LENGTH_SHORT).show();
    }
}
