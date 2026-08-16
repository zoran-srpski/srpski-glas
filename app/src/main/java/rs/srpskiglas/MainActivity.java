package rs.srpskiglas;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int SPEECH_REQUEST = 41;
    private static final int AUDIO_PERMISSION_REQUEST = 42;
    private EditText resultText;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        resultText = findViewById(R.id.resultText);
        Button dictate = findViewById(R.id.dictateButton);
        Button copy = findViewById(R.id.copyButton);
        dictate.setOnClickListener(v -> ensurePermissionAndDictate());
        copy.setOnClickListener(v -> copyResult());
    }

    private void ensurePermissionAndDictate() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
        } else {
            startDictation();
        }
    }

    private void startDictation() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори на српском");
        try {
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Google препознавање говора није доступно.", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) startDictation();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SPEECH_REQUEST || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches != null && !matches.isEmpty()) {
            resultText.setText(SerbianTransliterator.convert(matches.get(0)));
            resultText.setSelection(resultText.length());
        }
    }

    private void copyResult() {
        String text = resultText.getText().toString();
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Српски текст", text));
        Toast.makeText(this, "Текст је копиран.", Toast.LENGTH_SHORT).show();
    }
}
