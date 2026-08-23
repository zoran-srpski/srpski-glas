package rs.srpskiglas;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
    static final String EXTRA_OPEN_SETTINGS = "open_keyboard_settings";
    static final String PREFERENCES = "srpski_glas_preferences";
    static final String PREF_FONT_SIZE = "keyboard_font_size";
    static final String PREF_THEME = "keyboard_theme";
    static final String PREF_HAPTIC = "keyboard_haptic";
    private static final int SPEECH_REQUEST = 41;
    private static final int AUDIO_PERMISSION_REQUEST = 42;
    private EditText resultText;
    private TextView step3Status;
    private Button microphonePermissionButton;
    private boolean startDictationAfterPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            showSettingsScreen();
            return;
        }
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

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            showSettingsScreen();
        }
    }

    private void showSettingsScreen() {
        setContentView(R.layout.activity_settings);
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        configureSettingButton(findViewById(R.id.settingsFontButton),
                "Величина слова", PREF_FONT_SIZE,
                new String[]{"small", "medium", "large"},
                new String[]{"Мала", "Средња", "Велика"},
                preferences.getString(PREF_FONT_SIZE, "medium"));
        configureSettingButton(findViewById(R.id.settingsThemeButton),
                "Тема тастатуре", PREF_THEME,
                new String[]{"system", "light", "dark"},
                new String[]{"Према телефону", "Светла", "Тамна"},
                preferences.getString(PREF_THEME, "system"));
        configureSettingButton(findViewById(R.id.settingsHapticButton),
                "Вибрација тастера", PREF_HAPTIC,
                new String[]{"off", "weak", "normal"},
                new String[]{"Искључена", "Слаба", "Нормална"},
                preferences.getString(PREF_HAPTIC, "weak"));
        findViewById(R.id.settingsDoneButton).setOnClickListener(v -> finish());
    }

    private void configureSettingButton(Button button, String title, String key,
            String[] values, String[] labels, String currentValue) {
        button.setText(title + ": " + labelForValue(values, labels, currentValue));
        button.setOnClickListener(v -> {
            int checked = indexOf(values,
                    getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                            .getString(key, values[0]));
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                                .edit().putString(key, values[which]).apply();
                        button.setText(title + ": " + labels[which]);
                        dialog.dismiss();
                    })
                    .show();
        });
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    private String labelForValue(String[] values, String[] labels, String value) {
        return labels[indexOf(values, value)];
    }

    private void updateMicrophoneStatus() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            step3Status.setText("✓  3. Микрофон је дозвољен на овом уређају");
            microphonePermissionButton.setText(
                    "Дозвола је одобрена");
            microphonePermissionButton.setBackgroundTintList(
                    ColorStateList.valueOf(0xFFE1E3E2));
            microphonePermissionButton.setTextColor(0xFF52615B);
            microphonePermissionButton.setEnabled(false);
        } else {
            step3Status.setText("③ Дозволите микрофон");
            microphonePermissionButton.setText("Дозволи микрофон");
            microphonePermissionButton.setBackgroundTintList(
                    ColorStateList.valueOf(0xFF19785B));
            microphonePermissionButton.setTextColor(0xFFFFFFFF);
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
