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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.regex.Pattern;

public final class SerbianVoiceInputMethod extends InputMethodService
        implements RecognitionListener {
    private static final Pattern WHITESPACE_BEFORE_CLOSING_PUNCTUATION =
            Pattern.compile("[ \\t\\u00a0]+([.,!?:;%\\)\\]\\}])");
    private SpeechRecognizer recognizer;
    private Button micButton;
    private Button scriptButton;
    private Button symbolsButton;
    private Button shiftButton;
    private Button enterButton;
    private Button openKeyboardButton;
    private Button hideKeyboardButton;
    private Button spaceButton;
    private LinearLayout letterRows;
    private LinearLayout keyboardBody;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private boolean continuousMode;
    private boolean listening;
    private boolean latinScript;
    private boolean shifted;
    private boolean capsLock;
    private boolean symbolMode;
    private boolean lastSpaceAddedByDictation;
    private boolean lastSpaceAddedManually;
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
        micButton = view.findViewById(R.id.keyboardMicButton);
        Button switchButton = view.findViewById(R.id.switchKeyboardButton);
        Button backspace = view.findViewById(R.id.backspaceButton);
        scriptButton = view.findViewById(R.id.scriptButton);
        symbolsButton = view.findViewById(R.id.symbolsButton);
        shiftButton = view.findViewById(R.id.shiftButton);
        letterRows = view.findViewById(R.id.letterRows);
        Button comma = view.findViewById(R.id.commaButton);
        spaceButton = view.findViewById(R.id.spaceButton);
        Button period = view.findViewById(R.id.periodButton);
        enterButton = view.findViewById(R.id.enterButton);
        openKeyboardButton = view.findViewById(R.id.openKeyboardButton);
        keyboardBody = view.findViewById(R.id.keyboardBody);
        hideKeyboardButton = view.findViewById(R.id.hideKeyboardButton);
        micButton.setOnClickListener(v -> toggleVoiceInput());
        switchButton.setOnClickListener(v -> {
            v.setText(latinScript ? "DRŽI" : "ДРЖИ");
            keyHandler.postDelayed(() -> v.setText("⇄"), 1500);
        });
        switchButton.setOnLongClickListener(v -> {
            v.setText("⇄");
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            switchToNextInputMethod(false);
            return true;
        });
        backspace.setOnTouchListener((v, event) -> handleBackspaceTouch(event));
        scriptButton.setOnClickListener(v -> toggleScript());
        symbolsButton.setOnClickListener(v -> toggleSymbols());
        shiftButton.setOnClickListener(v -> toggleShift());
        shiftButton.setOnLongClickListener(v -> toggleCapsLock());
        comma.setOnClickListener(v -> commitText(","));
        spaceButton.setOnClickListener(v -> commitText(" "));
        period.setOnClickListener(v -> commitText("."));
        enterButton.setOnClickListener(v -> pressEditorAction());
        openKeyboardButton.setOnClickListener(v -> setKeyboardExpanded(true));
        hideKeyboardButton.setOnClickListener(v -> setKeyboardExpanded(false));
        setKeyboardExpanded(false);
        updateKeyboardControlLabels();
        buildLetterRows();
        updateEditorAction(getCurrentInputEditorInfo());
        handler.post(this::updateAutomaticShift);
        return view;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        updateEditorAction(info);
        if (!restarting) {
            setKeyboardExpanded(false);
            lastSpaceAddedByDictation = false;
            lastSpaceAddedManually = false;
        }
        handler.post(this::updateAutomaticShift);
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd, int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart,
                newSelEnd, candidatesStart, candidatesEnd);
        handler.post(this::updateAutomaticShift);
    }

    private void setKeyboardExpanded(boolean expanded) {
        if (openKeyboardButton == null || keyboardBody == null) return;
        openKeyboardButton.setVisibility(expanded ? View.GONE : View.VISIBLE);
        keyboardBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        keyboardBody.requestLayout();
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
    }

    private void toggleScript() {
        latinScript = !latinScript;
        symbolMode = false;
        scriptButton.setText("Ћир/Lat");
        symbolsButton.setText("123/#+=");
        shiftButton.setEnabled(true);
        micButton.setText(continuousMode
                ? stopDictationButtonLabel()
                : dictationButtonLabel());
        updateKeyboardControlLabels();
        buildLetterRows();
        showStatus(latinScript
                ? "Латиница — пиши или диктирај"
                : "Ћирилица — пиши или диктирај");
    }

    private String dictationButtonLabel() {
        return latinScript
                ? "🎙  Diktiraj latinicom"
                : "🎙  Диктирај ћирилицом";
    }

    private String stopDictationButtonLabel() {
        return latinScript
                ? "⏹  Zaustavi diktiranje"
                : "⏹  Заустави диктирање";
    }

    private void updateKeyboardControlLabels() {
        if (openKeyboardButton != null) {
            openKeyboardButton.setText(latinScript
                    ? "Otvori tastaturu"
                    : "Отвори тастатуру");
        }
        if (hideKeyboardButton != null) {
            hideKeyboardButton.setText(latinScript
                    ? "Sakrij tastaturu"
                    : "Сакриј тастатуру");
        }
        if (spaceButton != null) {
            spaceButton.setText(latinScript ? "RAZMAK" : "РАЗМАК");
        }
    }

    private void toggleShift() {
        if (symbolMode) return;
        if (capsLock) {
            capsLock = false;
            shifted = false;
            updateAutomaticShift();
            return;
        }
        shifted = !shifted;
        updateShiftButtonLabel();
        buildLetterRows();
    }

    private boolean toggleCapsLock() {
        if (symbolMode) return true;
        capsLock = !capsLock;
        shifted = capsLock;
        if (!capsLock) {
            updateAutomaticShift();
        } else {
            updateShiftButtonLabel();
            buildLetterRows();
        }
        return true;
    }

    private void updateShiftButtonLabel() {
        if (shiftButton == null) return;
        shiftButton.setText(capsLock ? "⇧⇧" : (shifted ? "⇧●" : "⇧"));
    }

    private void toggleSymbols() {
        symbolMode = !symbolMode;
        shifted = !symbolMode && capsLock;
        updateShiftButtonLabel();
        shiftButton.setEnabled(!symbolMode);
        symbolsButton.setText(symbolMode
                ? (latinScript ? "ABC" : "АБВ")
                : "123/#+=");
        buildLetterRows();
        if (!symbolMode) handler.post(this::updateAutomaticShift);
        showStatus(symbolMode
                ? "Бројеви и посебни знакови"
                : (latinScript ? "Српска латиница" : "Српска ћирилица"));
    }

    private void commitText(String value) {
        stopDictationForManualInput();
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (isClosingPunctuation(value) && !lastSpaceAddedManually) {
            removeWhitespaceBeforeCursor(connection);
        }
        connection.commitText(value, 1);
        lastSpaceAddedByDictation = false;
        lastSpaceAddedManually = " ".equals(value);
        updateAutomaticShift();
    }

    private void updateAutomaticShift() {
        if (symbolMode || capsLock || shiftButton == null) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        boolean shouldShift = startsNewSentence(connection);
        if (shifted == shouldShift) return;
        shifted = shouldShift;
        updateShiftButtonLabel();
        buildLetterRows();
    }

    private boolean isClosingPunctuation(String value) {
        return ".".equals(value) || ",".equals(value)
                || "!".equals(value) || "?".equals(value)
                || ":".equals(value) || ";".equals(value)
                || "%".equals(value) || ")".equals(value)
                || "]".equals(value) || "}".equals(value);
    }

    private boolean startsWithClosingPunctuation(String value) {
        if (value == null || value.isEmpty()) return false;
        int codePoint = value.codePointAt(0);
        return isClosingPunctuation(
                new String(Character.toChars(codePoint)));
    }

    private void removeWhitespaceBeforeCursor(
            InputConnection connection) {
        CharSequence before = connection.getTextBeforeCursor(32, 0);
        if (before == null) return;
        int count = 0;
        for (int i = before.length() - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (c != ' ' && c != '\t') break;
            count++;
        }
        if (count > 0) connection.deleteSurroundingText(count, 0);
    }

    private void pressEditorAction() {
        stopDictationForManualInput();
        lastSpaceAddedByDictation = false;
        lastSpaceAddedManually = false;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null
                ? EditorInfo.IME_ACTION_NONE
                : (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noAction = info != null
                && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (!noAction && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action);
            return;
        }
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        handler.post(this::updateAutomaticShift);
    }

    private void updateEditorAction(EditorInfo info) {
        if (enterButton == null) return;
        int action = info == null
                ? EditorInfo.IME_ACTION_NONE
                : (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noAction = info != null
                && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        String label;
        if (noAction) {
            label = "↵";
        } else {
            switch (action) {
                case EditorInfo.IME_ACTION_DONE: label = "✓"; break;
                case EditorInfo.IME_ACTION_GO: label = "ОК"; break;
                case EditorInfo.IME_ACTION_NEXT: label = "Даље"; break;
                case EditorInfo.IME_ACTION_PREVIOUS: label = "Назад"; break;
                case EditorInfo.IME_ACTION_SEARCH: label = "🔍"; break;
                case EditorInfo.IME_ACTION_SEND: label = "Пошаљи"; break;
                default: label = "↵";
            }
        }
        enterButton.setText(label);
    }

    private void toggleVoiceInput() {
        if (continuousMode) {
            stopVoiceInput();
        } else {
            continuousMode = true;
            micButton.setText(stopDictationButtonLabel());
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
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                2000L);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L);
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
        if (continuousMode) handler.postDelayed(this::startVoiceInput, 100);
    }

    private void deleteOneCharacter() {
        stopDictationForManualInput();
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        CharSequence selected = connection.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            connection.commitText("", 1);
            lastSpaceAddedByDictation = false;
            lastSpaceAddedManually = false;
            updateAutomaticShift();
            return;
        }
        connection.deleteSurroundingText(1, 0);
        lastSpaceAddedByDictation = false;
        lastSpaceAddedManually = false;
        updateAutomaticShift();
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

    private void stopDictationForManualInput() {
        if (continuousMode || listening) stopVoiceInput();
    }

    private void showStatus(String text) {
        if (micButton != null && continuousMode) {
            micButton.setText(stopDictationButtonLabel());
        }
    }

    private String uiTextLatin(String text) {
        switch (text) {
            case "Препознавање говора није доступно":
                return "Prepoznavanje govora nije dostupno";
            case "Слушам…": return "Slušam…";
            case "Заустављено — спреман": return "Zaustavljeno — spreman";
            case "Настави да говориш…": return "Nastavi da govoriš…";
            case "Унето — настављам да слушам…":
                return "Uneto — nastavljam da slušam…";
            case "Пауза — настављам да слушам…":
                return "Pauza — nastavljam da slušam…";
            case "Заустављено": return "Zaustavljeno";
            case "Говори…": return "Govori…";
            case "Препознајем…": return "Prepoznajem…";
            case "Обрађујем…": return "Obrađujem…";
            case "Латиница — пиши или диктирај":
                return "Latinica — piši ili diktiraj";
            case "Ћирилица — пиши или диктирај":
                return "Ćirilica — piši ili diktiraj";
            case "Бројеви и посебни знакови":
                return "Brojevi i posebni znakovi";
            case "Српска латиница": return "Srpska latinica";
            case "Српска ћирилица": return "Srpska ćirilica";
            default: return text;
        }
    }

    private void finishListening(String message) {
        showStatus(message);
    }

    @Override public void onResults(Bundle results) {
        if (!continuousMode) return;
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
        converted = normalizePunctuationSpacing(converted);
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            if (startsWithClosingPunctuation(converted)
                    && lastSpaceAddedByDictation) {
                removeWhitespaceBeforeCursor(connection);
            }
            if (startsNewSentence(connection)) {
                converted = uppercaseFirstLetter(converted);
            } else {
                converted = lowercaseFirstLetter(converted);
            }
            connection.commitText(converted + " ", 1);
            lastSpaceAddedByDictation = true;
            lastSpaceAddedManually = false;
        }
        finishListening("Унето — настављам да слушам…");
        continueListening();
    }

    private String normalizePunctuationSpacing(String text) {
        return WHITESPACE_BEFORE_CLOSING_PUNCTUATION
                .matcher(text)
                .replaceAll("$1");
    }

    private boolean startsNewSentence(InputConnection connection) {
        CharSequence before = connection.getTextBeforeCursor(120, 0);
        if (before == null || before.length() == 0) return true;
        for (int i = before.length() - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r') continue;
            return c == '.' || c == '?' || c == '!' || c == '\n';
        }
        return true;
    }

    private String uppercaseFirstLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            char upper = Character.toUpperCase(c);
            if (upper == c) return text;
            return text.substring(0, i) + upper + text.substring(i + 1);
        }
        return text;
    }

    private String lowercaseFirstLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            // Preserve abbreviations such as ОК and САД.
            if (i + 1 < text.length() && Character.isUpperCase(text.charAt(i + 1))) {
                return text;
            }
            char lower = Character.toLowerCase(c);
            if (lower == c) return text;
            return text.substring(0, i) + lower + text.substring(i + 1);
        }
        return text;
    }

    @Override public void onError(int error) {
        listening = false;
        if (continuousMode) {
            showStatus("Пауза — настављам да слушам…");
            handler.postDelayed(this::startVoiceInput, 200);
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
