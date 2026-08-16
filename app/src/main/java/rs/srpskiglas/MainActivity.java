package rs.srpskiglas;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
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
    private TextView step1Status;
    private TextView step2Status;
    private TextView step3Status;
    private TextView setupCompleteText;
    private Button microphonePermissionButton;
    private boolean startDictationAfterPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        resultText = findViewById(R.id.resultText);
        step1Status = findViewById(R.id.step1Status);
        step2Status = findViewById(R.id.step2Status);
        step3Status = findViewById(R.id.step3Status);
        setupCompleteText = findViewById(R.id.setupCompleteText);
        microphonePermissionButton = findViewById(R.id.microphonePermissionButton);
        Button dictate = findViewById(R.id.dictateButton);
        Button copy = findViewById(R.id.copyButton);
        Button enableKeyboard = findViewById(R.id.enableKeyboardButton);
        Button selectKeyboard = findViewById(R.id.selectKeyboardButton);
        dictate.setOnClickListener(v -> ensurePermissionAndDictate());
        copy.setOnClickListener(v -> copyResult());
        microphonePermissionButton.setOnClickListener(v -> requestAudioPermission());
        enableKeyboard.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        selectKeyboard.setOnClickListener(v -> {
            InputMethodManager manager = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            manager.showInputMethodPicker();
        });
        updateSetupStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateSetupStatus();
    }

    private void updateSetupStatus() {
        boolean enabled = isKeyboardEnabled();
        boolean selected = isKeyboardSelected();
        boolean microphone = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        step1Status.setText(enabled
                ? "✓  1. Тастатура је омогућена"
                : "① Омогућите тастатуру");
        step2Status.setText(selected
                ? "✓  2. „Српски глас“ је изабран"
                : "② Изаберите „Српски глас“");
        step3Status.setText(microphone
                ? "✓  3. Микрофон је дозвољен"
                : "③ Дозволите микрофон");
        microphonePermissionButton.setText(microphone
                ? "Микрофон је већ дозвољен"
                : "Дозволи микрофон");
        microphonePermissionButton.setEnabled(!microphone);

        if (enabled && selected && microphone) {
            setupCompleteText.setText(
                    "✓ Све је спремно! Отворите Viber, WhatsApp, поруке или било коју другу апликацију и почните да пишете или диктирате.");
        } else {
            setupCompleteText.setText(
                    "Када завршите сва три корака, отворићете тастатуру у било којој апликацији додиром на поље за писање.");
        }
    }

    private boolean isKeyboardEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        return enabled != null && enabled.contains(getPackageName());
    }

    private boolean isKeyboardSelected() {
        String selected = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName selectedService = selected == null
                ? null : ComponentName.unflattenFromString(selected);
        ComponentName ownService = new ComponentName(
                this, SerbianVoiceInputMethod.class);
        return ownService.equals(selectedService);
    }

    private void requestAudioPermission() {
        startDictationAfterPermission = false;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST);
        } else {
            updateSetupStatus();
        }
    }

    private void ensurePermissionAndDictate() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            startDictationAfterPermission = true;
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST);
        } else {
            startDictation();
        }
    }

    private void startDictation() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори на српском");
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
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            updateSetupStatus();
            boolean granted = results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && startDictationAfterPermission) startDictation();
            startDictationAfterPermission = false;
        }
    }

    @Override protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SPEECH_REQUEST
                || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> matches =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
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
