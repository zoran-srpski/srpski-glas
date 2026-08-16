package rs.srpskiglas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.WindowInsets;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public final class SerbianVoiceInputMethod extends InputMethodService
        implements RecognitionListener {
    private SpeechRecognizer recognizer;
    private TextView status;
    private Button micButton;
    private Button scriptButton;
    private Button symbolsButton;
    private Button shiftButton;
    private LinearLayout letterRows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private boolean continuousMode;
    private boolean listening;
    private boolean latinScript;
    private boolean shifted;
    private boolean symbolMode;
    private final Runnable repeatBackspace = new Runnable() {
        @Override public void run() {
            deleteOneCharacter();
            keyHandler.postDelayed(this, 65);
        }
    };

    private static final String[] CYRILLIC_ROWS = {
            "љњертзуиопш", "асдфгхјклчћ", "џђцвбнмж"
    };
    private static final String[] LATIN_ROWS = {
            "qwertzuiopš", "asdfghjklčć", "yxcvbnmđž"
    };
    private static final String[] SYMBOL_ROWS = {
            "1234567890", "@#€_$&-+()", "*/\\:;!?\"'", "[]{}<>=%|~^`"
    };

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
        scriptButton = view.findViewById(R.id.scriptButton);
        symbolsButton = view.findViewById(R.id.symbolsButton);
        shiftButton = view.findViewById(R.id.shiftButton);
        letterRows = view.findViewById(R.id.letterRows);
        Button comma = view.findViewById(R.id.commaButton);
        Button space = view.findViewById(R.id.spaceButton);
        Button period = view.findViewById(R.id.periodButton);
        Button enter = view.findViewById(R.id.enterButton);
        Button question = view.findViewById(R.id.questionButton);
        Button exclamation = view.findViewById(R.id.exclamationButton);
        Button hyphen = view.findViewById(R.id.hyphenButton);
        Button colon = view.findViewById(R.id.colonButton);
        Button semicolon = view.findViewById(R.id.semicolonButton);
        Button paste = view.findViewById(R.id.pasteButton);
        Button copy = view.findViewById(R.id.copyButton);
        Button cut = view.findViewById(R.id.cutButton);
        Button selectAll = view.findViewById(R.id.selectAllButton);
        micButton.setOnClickListener(v -> toggleVoiceInput());
        switchButton.setOnClickListener(v -> switchToNextInputMethod(false));
        backspace.setOnTouchListener((v, event) -> handleBackspaceTouch(event));
        scriptButton.setOnClickListener(v -> toggleScript());
        symbolsButton.setOnClickListener(v -> toggleSymbols());
        shiftButton.setOnClickListener(v -> toggleShift());
        comma.setOnClickListener(v -> commitText(","));
        space.setOnClickListener(v -> commitText(" "));
        period.setOnClickListener(v -> commitText("."));
        enter.setOnClickListener(v -> pressEnter());
        question.setOnClickListener(v -> commitText("?"));
        exclamation.setOnClickListener(v -> commitText("!"));
        hyphen.setOnClickListener(v -> commitText("-"));
        colon.setOnClickListener(v -> commitText(":"));
        semicolon.setOnClickListener(v -> commitText(";"));
        paste.setOnClickListener(v -> editingAction(android.R.id.paste));
        copy.setOnClickListener(v -> editingAction(android.R.id.copy));
        cut.setOnClickListener(v -> editingAction(android.R.id.cut));
        selectAll.setOnClickListener(v -> editingAction(android.R.id.selectAll));
        buildLetterRows();
        return view;
    }

    private void buildLetterRows() {
        if (letterRows == null) return;
        letterRows.removeAllViews();
        String[] rows = symbolMode
                ? SYMBOL_ROWS
                : (latinScript ? LATIN_ROWS : CYRILLIC_ROWS);
        for (String rowText : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int i = 0; i < rowText.length();) {
                int codePoint = rowText.codePointAt(i);
                String key = new String(Character.toChars(codePoint));
                i += Character.charCount(codePoint);
                if (shifted) key = key.toUpperCase(new java.util.Locale("sr"));
                Button keyButton = new Button(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, dp(46), 1f);
                params.setMargins(dp(1), dp(1), dp(1), dp(1));
                keyButton.setLayoutParams(params);
                keyButton.setMinWidth(0);
                keyButton.setPadding(0, 0, 0, 0);
                keyButton.setText(key);
                keyButton.setTextSize(17);
                keyButton.setAllCaps(false);
                final String value = key;
                keyButton.setOnClickListener(v -> typeLetter(value));
                row.addView(keyButton);
            }
            letterRows.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void typeLetter(String value) {
        commitText(value);
        if (shifted && !symbolMode) {
            shifted = false;
            shiftButton.setText("⇧");
            buildLetterRows();
        }
    }

    private void toggleScript() {
        latinScript = !latinScript;
        symbolMode = false;
        scriptButton.setText("Ћир/Lat");
        symbolsButton.setText("123/#+=");
        shiftButton.setEnabled(true);
        if (!continuousMode) micButton.setText(dictationButtonLabel());
        buildLetterRows();
        showStatus(latinScript
                ? "Латиница — пиши или диктирај"
                : "Ћирилица — пиши или диктирај");
    }

    private String dictationButtonLabel() {
        return latinScript
                ? "🎙  Диктирај латиницом"
                : "🎙  Диктирај ћирилицом";
    }

    private void toggleShift() {
        if (symbolMode) return;
        shifted = !shifted;
        shiftButton.setText(shifted ? "⇧●" : "⇧");
        buildLetterRows();
    }

    private void toggleSymbols() {
        symbolMode = !symbolMode;
        shifted = false;
        shiftButton.setText("⇧");
        shiftButton.setEnabled(!symbolMode);
        symbolsButton.setText(symbolMode
                ? (latinScript ? "ABC" : "АБВ")
                : "123/#+=");
        buildLetterRows();
        showStatus(symbolMode
                ? "Бројеви и посебни знакови"
                : (latinScript ? "Српска латиница" : "Српска ћирилица"));
    }

    private void commitText(String value) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(value, 1);
    }

    private void pressEnter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    private void toggleVoiceInput() {
        if (continuousMode) {
            stopVoiceInput();
        } else {
            continuousMode = true;
            micButton.setText("⏹  Заустави");
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        if (!continuousMode || listening) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            continuousMode = false;
            micButton.setText(dictationButtonLabel());
            Toast.makeText(this, "Отвори Српски глас и дозволи микрофон.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            continuousMode = false;
            micButton.setText(dictationButtonLabel());
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false);
        }
        showStatus("Слушам…");
        listening = true;
        recognizer.startListening(intent);
    }

    private void stopVoiceInput() {
        continuousMode = false;
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.cancel();
        if (micButton != null) micButton.setText(dictationButtonLabel());
        showStatus("Заустављено — спреман");
    }

    private void continueListening() {
        listening = false;
        if (continuousMode) handler.postDelayed(this::startVoiceInput, 350);
    }

    private void deleteOneCharacter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.deleteSurroundingText(1, 0);
    }

    private boolean handleBackspaceTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            deleteOneCharacter();
            keyHandler.removeCallbacks(repeatBackspace);
            keyHandler.postDelayed(repeatBackspace, 420);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            keyHandler.removeCallbacks(repeatBackspace);
            return true;
        }
        return true;
    }

    private void editingAction(int actionId) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.performContextMenuAction(actionId);
    }

    private void showStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void finishListening(String message) {
        showStatus(message);
    }

    @Override public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            finishListening("Настави да говориш…");
            continueListening();
            return;
        }
        String converted = latinScript
                ? SerbianTransliterator.convertLatin(matches.get(0))
                : SerbianTransliterator.convert(matches.get(0));
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(converted + " ", 1);
        finishListening("Унето — настављам да слушам…");
        continueListening();
    }

    @Override public void onError(int error) {
        listening = false;
        if (continuousMode) {
            showStatus("Пауза — настављам да слушам…");
            handler.postDelayed(this::startVoiceInput, 500);
        } else {
            finishListening("Заустављено");
        }
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        keyHandler.removeCallbacks(repeatBackspace);
        stopVoiceInput();
        super.onFinishInputView(finishingInput);
    }

    @Override public void onDestroy() {
        continuousMode = false;
        handler.removeCallbacksAndMessages(null);
        keyHandler.removeCallbacksAndMessages(null);
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
