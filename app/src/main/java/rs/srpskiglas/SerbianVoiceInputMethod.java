package rs.srpskiglas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.regex.Pattern;

public final class SerbianVoiceInputMethod extends InputMethodService
        implements RecognitionListener {
    private static final long SCRIPT_RESET_AFTER_IDLE_MS = 3 * 60 * 1000L;
    private static final long LETTERS_RESET_AFTER_IDLE_MS = 60 * 1000L;
    private static final Pattern WHITESPACE_BEFORE_CLOSING_PUNCTUATION =
            Pattern.compile("[ \\t\\u00a0]+([.,!?:;/%'\\)\\]\\}])");
    private SpeechRecognizer recognizer;
    private Button micButton;
    private Button switchKeyboardButton;
    private Button scriptButton;
    private Button symbolsButton;
    private Button emojiButton;
    private Button shiftButton;
    private Button enterButton;
    private Button openKeyboardButton;
    private Button spaceButton;
    private LinearLayout letterRows;
    private LinearLayout keyboardBody;
    private LinearLayout topControlRow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private boolean continuousMode;
    private boolean listening;
    private boolean latinScript;
    private boolean shifted;
    private boolean capsLock;
    private boolean suppressShiftClickAfterLongPress;
    private boolean symbolMode;
    private boolean emojiMode;
    private int emojiCategory;
    private boolean lastSpaceAddedByDictation;
    private boolean lastSpaceAddedManually;
    private boolean dictationContextKnown;
    private boolean dictationNextStartsSentence;
    private boolean speechStarted;
    private int startSilenceRetries;
    private long initialSpeechDeadline;
    private long suppressKeyboardAutoOpenUntil;
    private long keyboardHiddenAt = -1L;
    private final Runnable restoreSwitchKeyboardButton = () -> {
        if (switchKeyboardButton == null) return;
        switchKeyboardButton.setText("⇄");
        switchKeyboardButton.setBackgroundTintList(
                ColorStateList.valueOf(0xFFE1E3E2));
    };
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
    private static final String[] EMOJI_CATEGORY_LABELS = {
            "★", "😀", "🧑", "🐻", "🍔", "⚽", "🚗", "💡", "❤️", "🚩"
    };
    private static final String[][] EMOJI_CATEGORIES = {
            emoji("😀 😂 🤣 😊 😍 🥰 😘 😉 😎 🤗 🤔 😢 😭 😡 👍 👎 👏 🙏 💪 ❤️ 💔 🔥 🎉 ✅ ❌ ⭐ 💯"),
            emoji("😀 😃 😄 😁 😆 😅 😂 🤣 🥲 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫣 🤭 🫢 🫡 🤫 🫠 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 😈 👿 👻 💀 ☠️ 👽 🤖 💩"),
            emoji("👋 🤚 🖐️ ✋ 🖖 🫱 🫲 🫳 🫴 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦵 🦶 👂 👃 👀 👁️ 🧠 🫀 🫁 🦷 🦴 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 👷 💂 🕵️ 👩‍⚕️ 👩‍🌾 👩‍🍳 👩‍🎓 👩‍🎤 👩‍🏫 👩‍💻 👩‍💼 👩‍🔧 👩‍🔬 👩‍🎨 👩‍🚒 👩‍✈️ 👩‍🚀 👩‍⚖️ 🤴 👸 🥷 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 👯 🧖 🧗 🤺 🏇 ⛷️ 🏂 🏌️ 🏄 🚣 🏊 ⛹️ 🏋️ 🚴 🤸 🤼 🤽 🤾 🤹 🧘 🛀 🛌"),
            emoji("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 🐌 🪲 🐞 🦗 🪳 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🦧 🐘 🦛 🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🦮 🐕‍🦺 🐈 🐈‍⬛ 🪶 🐓 🦃 🦤 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 🦦 🦥 🐁 🐀 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍 🪴 🎋 🍃 🍂 🍁 🍄 🐚 🪨 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌍 🌎 🌏 ⭐ 🌟 ✨ ⚡ ☄️ 🔥 🌈 ☀️ 🌤️ ⛅ 🌧️ ⛈️ ❄️ ☃️ 💨 💧 🌊"),
            emoji("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🫘 🥐 🥯 🍞 🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🥫 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🦪 🍤 🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 ☕ 🫖 🍵 🧃 🥤 🧋 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️"),
            emoji("⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🛝 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🪂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🩰 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩"),
            emoji("🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍️ 🛺 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ 🛟 ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏕️ ⛺ 🛖 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 ⛪ 🕌 🕍 ⛩️ 🕋 ⛲ 🌁 🌃 🏙️ 🌄 🌅 🌆 🌇 🌉 ♨️ 🎑 🏞️"),
            emoji("⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 🖲️ 🕹️ 🗜️ 💽 💾 💿 📀 📼 📷 📸 📹 🎥 📽️ 🎞️ 📞 ☎️ 📟 📠 📺 📻 🎙️ 🎚️ 🎛️ 🧭 ⏱️ ⏲️ ⏰ 🕰️ ⌛ ⏳ 📡 🔋 🪫 🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💴 💶 💷 🪙 💰 💳 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 ⚒️ 🛠️ ⛏️ 🪚 🔩 ⚙️ 🪤 🧱 ⛓️ 🧲 🔫 💣 🧨 🪓 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🪦 ⚱️ 🏺 🔮 📿 🧿 💈 ⚗️ 🔭 🔬 🕳️ 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡️ 🧹 🪠 🧺 🧻 🚽 🚿 🛁 🧼 🪥 🪒 🧽 🪣 🧴 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🧸 🪆 🖼️ 🪞 🪟 🛍️ 🛒 🎁 🎈 🎏 🎀 🪄 🪅 🎊 🎉 🧧 ✉️ 📩 📨 📧 💌 📥 📤 📦 🏷️ 📪 📫 📬 📭 📮 📯 📜 📃 📄 📑 🧾 📊 📈 📉 🗒️ 🗓️ 📆 📅 🗑️ 📇 🗃️ 🗳️ 🗄️ 📋 📁 📂 🗂️ 🗞️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗 📎 🖇️ 📐 📏 🧮 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔒 🔓"),
            emoji("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️ 🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹 ❇️ ✳️ ❎ 🌐 💠 Ⓜ️ 🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 ⚧️ 🚻 🚮 🎦 📶 🈁 🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 🔢 ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁 🔂 ◀️ 🔼 🔽 ⏫ ⏬ ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↕️ ↔️ ↪️ ↩️ ⤴️ ⤵️ 🔄 🔃 🎵 🎶 ➕ ➖ ➗ ✖️ 🟰 ♾️ 💲 ™️ ©️ ®️ 〰️ ➰ ➿ ✔️ ☑️ 🔘 ⚪ ⚫ 🔴 🔵 🟤 🟣 🟠 🟡 🟢 ◼️ ◻️ 🔸 🔹 🔶 🔷 🔺 🔻"),
            emoji("🏳️ 🏴 🏁 🚩 🏳️‍🌈 🏳️‍⚧️ 🇷🇸 🇲🇪 🇧🇦 🇭🇷 🇸🇮 🇲🇰 🇦🇱 🇽🇰 🇬🇷 🇧🇬 🇷🇴 🇭🇺 🇦🇹 🇩🇪 🇨🇭 🇮🇹 🇫🇷 🇪🇸 🇵🇹 🇬🇧 🇮🇪 🇳🇱 🇧🇪 🇱🇺 🇩🇰 🇳🇴 🇸🇪 🇫🇮 🇮🇸 🇵🇱 🇨🇿 🇸🇰 🇺🇦 🇷🇺 🇧🇾 🇲🇩 🇱🇹 🇱🇻 🇪🇪 🇹🇷 🇨🇾 🇬🇪 🇦🇲 🇦🇿 🇺🇸 🇨🇦 🇲🇽 🇧🇷 🇦🇷 🇨🇱 🇨🇴 🇵🇪 🇺🇾 🇨🇺 🇯🇲 🇨🇳 🇯🇵 🇰🇷 🇮🇳 🇮🇩 🇹🇭 🇻🇳 🇵🇭 🇲🇾 🇸🇬 🇦🇺 🇳🇿 🇿🇦 🇪🇬 🇲🇦 🇹🇳 🇩🇿 🇳🇬 🇰🇪 🇮🇱 🇵🇸 🇸🇦 🇦🇪 🇮🇷 🇮🇶 🇺🇳 🇪🇺")
    };

    private static String[] emoji(String values) {
        return values.split(" ");
    }

    @Override public View onCreateInputView() {
        applyLightNavigationBar();
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
        view.post(this::applyLightNavigationBar);
        micButton = view.findViewById(R.id.keyboardMicButton);
        topControlRow = view.findViewById(R.id.topControlRow);
        switchKeyboardButton = view.findViewById(R.id.switchKeyboardButton);
        Button backspace = view.findViewById(R.id.backspaceButton);
        scriptButton = view.findViewById(R.id.scriptButton);
        symbolsButton = view.findViewById(R.id.symbolsButton);
        emojiButton = view.findViewById(R.id.emojiButton);
        shiftButton = view.findViewById(R.id.shiftButton);
        letterRows = view.findViewById(R.id.letterRows);
        Button comma = view.findViewById(R.id.commaButton);
        spaceButton = view.findViewById(R.id.spaceButton);
        Button period = view.findViewById(R.id.periodButton);
        enterButton = view.findViewById(R.id.enterButton);
        openKeyboardButton = view.findViewById(R.id.openKeyboardButton);
        keyboardBody = view.findViewById(R.id.keyboardBody);
        micButton.setOnClickListener(v -> toggleVoiceInput());
        switchKeyboardButton.setOnClickListener(v -> {
            keyHandler.removeCallbacks(restoreSwitchKeyboardButton);
            switchKeyboardButton.setText(latinScript ? "DRŽI" : "ДРЖИ");
            switchKeyboardButton.setBackgroundTintList(
                    ColorStateList.valueOf(0xFFFF9800));
            keyHandler.postDelayed(restoreSwitchKeyboardButton, 2000);
        });
        final boolean[] keyboardPickerOpened = {false};
        final Runnable openKeyboardPicker = () -> {
            keyboardPickerOpened[0] = true;
            keyHandler.removeCallbacks(restoreSwitchKeyboardButton);
            restoreSwitchKeyboardButton.run();
            switchKeyboardButton.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS);
            InputMethodManager manager =
                    getSystemService(InputMethodManager.class);
            if (manager != null) manager.showInputMethodPicker();
        };
        switchKeyboardButton.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    keyboardPickerOpened[0] = false;
                    keyHandler.postDelayed(openKeyboardPicker, 1000);
                    return true;
                case MotionEvent.ACTION_UP:
                    keyHandler.removeCallbacks(openKeyboardPicker);
                    if (!keyboardPickerOpened[0]) v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    keyHandler.removeCallbacks(openKeyboardPicker);
                    return true;
                default:
                    return true;
            }
        });
        backspace.setOnTouchListener((v, event) -> handleBackspaceTouch(event));
        scriptButton.setOnClickListener(v -> toggleScript());
        symbolsButton.setOnClickListener(v -> toggleSymbols());
        emojiButton.setOnClickListener(v -> toggleEmoji());
        shiftButton.setOnClickListener(v -> {
            if (suppressShiftClickAfterLongPress) return;
            toggleShift();
        });
        shiftButton.setOnLongClickListener(v -> {
            suppressShiftClickAfterLongPress = true;
            return toggleCapsLock();
        });
        shiftButton.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                // A long press can otherwise be followed by the normal click on
                // some Android versions. Clear the guard only after that event
                // has had a chance to run.
                keyHandler.post(() -> suppressShiftClickAfterLongPress = false);
            }
            return false;
        });
        comma.setOnClickListener(v -> commitText(","));
        spaceButton.setOnClickListener(v -> commitText(" "));
        period.setOnClickListener(v -> commitText("."));
        enterButton.setOnClickListener(v -> pressEditorAction());
        openKeyboardButton.setOnClickListener(v -> setKeyboardExpanded(true));
        setKeyboardExpanded(true);
        updateKeyboardControlLabels();
        buildLetterRows();
        updateEditorAction(getCurrentInputEditorInfo());
        handler.post(this::updateAutomaticShift);
        return view;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        applyLightNavigationBar();
        restoreMicButtonLayout();
        if (micButton != null && !continuousMode && !listening) {
            micButton.setText(dictationButtonLabel());
        }
        updateEditorAction(info);
        if (!restarting) {
            setKeyboardExpanded(true);
            lastSpaceAddedByDictation = false;
            lastSpaceAddedManually = false;
        } else if (keyboardBody != null
                && keyboardBody.getVisibility() != View.VISIBLE
                && SystemClock.uptimeMillis() >= suppressKeyboardAutoOpenUntil) {
            setKeyboardExpanded(true);
        }
        handler.post(this::updateAutomaticShift);
    }

    @Override public void onWindowShown() {
        super.onWindowShown();
        restoreLettersAfterKeyboardReturn();
        applyLightNavigationBar();
    }

    @Override public void onWindowHidden() {
        keyboardHiddenAt = SystemClock.elapsedRealtime();
        super.onWindowHidden();
    }

    private void restoreLettersAfterKeyboardReturn() {
        if (keyboardHiddenAt < 0L) return;
        long idleTime = SystemClock.elapsedRealtime() - keyboardHiddenAt;
        keyboardHiddenAt = -1L;
        boolean resetScript = idleTime >= SCRIPT_RESET_AFTER_IDLE_MS;
        boolean resetLetters = symbolMode
                && idleTime >= LETTERS_RESET_AFTER_IDLE_MS;
        boolean resetEmoji = emojiMode;
        if (!resetScript && !resetLetters && !resetEmoji) return;
        if (resetScript) latinScript = false;
        if (resetLetters) symbolMode = false;
        if (resetEmoji) emojiMode = false;
        shifted = capsLock;
        if (scriptButton != null) scriptButton.setText("Ћир/Lat");
        if (symbolsButton != null) symbolsButton.setText("123/#+=");
        if (emojiButton != null) emojiButton.setText("😀");
        if (shiftButton != null) {
            shiftButton.setEnabled(true);
            updateShiftButtonLabel();
        }
        if (micButton != null) {
            micButton.setText(continuousMode
                    ? stopDictationButtonLabel()
                    : dictationButtonLabel());
        }
        updateKeyboardControlLabels();
        buildLetterRows();
        if (!capsLock) handler.post(this::updateAutomaticShift);
    }

    private void applyLightNavigationBar() {
        if (getWindow() == null) return;
        Window window = getWindow().getWindow();
        if (window == null) return;
        window.setNavigationBarColor(Color.rgb(246, 244, 238));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd, int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart,
                newSelEnd, candidatesStart, candidatesEnd);
        boolean selectionChanged = oldSelStart != newSelStart
                || oldSelEnd != newSelEnd;
        if (selectionChanged && keyboardBody != null
                && keyboardBody.getVisibility() != View.VISIBLE
                && SystemClock.uptimeMillis() >= suppressKeyboardAutoOpenUntil) {
            setKeyboardExpanded(true);
        }
        handler.post(this::updateAutomaticShift);
    }

    private void setKeyboardExpanded(boolean expanded) {
        if (openKeyboardButton == null || keyboardBody == null) return;
        if (!expanded) {
            suppressKeyboardAutoOpenUntil = SystemClock.uptimeMillis() + 350L;
        }
        openKeyboardButton.setVisibility(expanded ? View.GONE : View.VISIBLE);
        keyboardBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        keyboardBody.requestLayout();
    }

    private void buildLetterRows() {
        if (letterRows == null) return;
        letterRows.removeAllViews();
        if (emojiMode) {
            buildEmojiRows();
            return;
        }
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
                        0, dp(40), 1f);
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
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        }
    }

    private void buildEmojiRows() {
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        categories.setGravity(Gravity.CENTER);
        for (int i = 0; i < EMOJI_CATEGORY_LABELS.length; i++) {
            Button category = new Button(this);
            category.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1f));
            category.setMinWidth(0);
            category.setPadding(0, 0, 0, 0);
            category.setAllCaps(false);
            category.setText((i == emojiCategory ? "•" : "") + EMOJI_CATEGORY_LABELS[i]);
            category.setTextSize(16);
            final int selected = i;
            category.setOnClickListener(v -> {
                emojiCategory = selected;
                buildLetterRows();
            });
            categories.addView(category);
        }
        letterRows.addView(categories, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        String[] values = EMOJI_CATEGORIES[emojiCategory];
        int perRow = (values.length + 1) / 2;
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            HorizontalScrollView scroller = new HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.setFillViewport(true);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int start = rowIndex * perRow;
            int end = Math.min(values.length, start + perRow);
            for (int i = start; i < end; i++) {
                Button keyButton = new Button(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        dp(50), dp(42));
                params.setMargins(dp(1), dp(1), dp(1), dp(1));
                keyButton.setLayoutParams(params);
                keyButton.setMinWidth(0);
                keyButton.setPadding(0, 0, 0, 0);
                keyButton.setAllCaps(false);
                keyButton.setText(values[i]);
                keyButton.setTextSize(21);
                final String value = values[i];
                keyButton.setOnClickListener(v -> commitText(value));
                row.addView(keyButton);
            }
            scroller.addView(row);
            letterRows.addView(scroller, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
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
        emojiMode = false;
        scriptButton.setText("Ћир/Lat");
        symbolsButton.setText("123/#+=");
        emojiButton.setText("😀");
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

    private void resetScriptToCyrillic() {
        if (!latinScript && !symbolMode && !emojiMode) return;
        latinScript = false;
        symbolMode = false;
        emojiMode = false;
        if (scriptButton != null) scriptButton.setText("Ћир/Lat");
        if (symbolsButton != null) symbolsButton.setText("123/#+=");
        if (emojiButton != null) emojiButton.setText("😀");
        if (shiftButton != null) shiftButton.setEnabled(true);
        if (micButton != null) {
            micButton.setText(continuousMode
                    ? stopDictationButtonLabel()
                    : dictationButtonLabel());
        }
        updateKeyboardControlLabels();
        buildLetterRows();
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
        if (spaceButton != null) {
            spaceButton.setText(latinScript ? "RAZMAK" : "РАЗМАК");
        }
    }

    private void toggleShift() {
        if (symbolMode || emojiMode) return;
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
        if (symbolMode || emojiMode) return true;
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
        emojiMode = false;
        shifted = !symbolMode && capsLock;
        updateShiftButtonLabel();
        shiftButton.setEnabled(!symbolMode);
        symbolsButton.setText(symbolMode
                ? (latinScript ? "ABC" : "АБВ")
                : "123/#+=");
        emojiButton.setText("😀");
        buildLetterRows();
        if (!symbolMode) handler.post(this::updateAutomaticShift);
        showStatus(symbolMode
                ? "Бројеви и посебни знакови"
                : (latinScript ? "Српска латиница" : "Српска ћирилица"));
    }

    private void toggleEmoji() {
        emojiMode = !emojiMode;
        symbolMode = false;
        shifted = !emojiMode && capsLock;
        symbolsButton.setText("123/#+=");
        emojiButton.setText(emojiMode
                ? (latinScript ? "ABC" : "АБВ")
                : "😀");
        shiftButton.setEnabled(!emojiMode);
        updateShiftButtonLabel();
        buildLetterRows();
        if (!emojiMode) handler.post(this::updateAutomaticShift);
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
        if (symbolMode || emojiMode || capsLock || shiftButton == null) return;
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
                || "/".equals(value) || "'".equals(value)
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
            label = "↲";
        } else {
            switch (action) {
                case EditorInfo.IME_ACTION_DONE: label = "✓"; break;
                case EditorInfo.IME_ACTION_GO: label = "ОК"; break;
                case EditorInfo.IME_ACTION_NEXT: label = "Даље"; break;
                case EditorInfo.IME_ACTION_PREVIOUS: label = "Назад"; break;
                case EditorInfo.IME_ACTION_SEARCH: label = "🔍"; break;
                case EditorInfo.IME_ACTION_SEND: label = "Пошаљи"; break;
                default: label = "↲";
            }
        }
        enterButton.setText(label);
        enterButton.setTextSize("↲".equals(label) ? 26 : 14);
    }

    private void toggleVoiceInput() {
        restoreMicButtonLayout();
        if (continuousMode) {
            stopVoiceInput();
        } else {
            continuousMode = true;
            dictationContextKnown = false;
            speechStarted = false;
            startSilenceRetries = 0;
            initialSpeechDeadline = SystemClock.uptimeMillis() + 5000L;
            micButton.setText(stopDictationButtonLabel());
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        if (!continuousMode || listening) return;
        if (!isInternetAvailable()) {
            continuousMode = false;
            listening = false;
            setKeepScreenOnWhileDictating(false);
            micButton.setText(dictationButtonLabel());
            String message = latinScript
                    ? "Nema internet veze. Uključi internet i pokušaj ponovo."
                    : "Нема интернет везе. Укључи интернет и покушај поново.";
            showTemporaryMicMessage(message);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            continuousMode = false;
            setKeepScreenOnWhileDictating(false);
            micButton.setText(dictationButtonLabel());
            Toast.makeText(this, "Отвори Српски глас и дозволи микрофон.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            continuousMode = false;
            setKeepScreenOnWhileDictating(false);
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
                5000L);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                5000L);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                5000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false);
        }
        showStatus("Слушам…");
        speechStarted = false;
        listening = true;
        setKeepScreenOnWhileDictating(true);
        recognizer.startListening(intent);
    }

    private boolean isInternetAvailable() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void stopVoiceInput() {
        continuousMode = false;
        listening = false;
        setKeepScreenOnWhileDictating(false);
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.cancel();
        if (micButton != null) micButton.setText(dictationButtonLabel());
        showStatus("Заустављено — спреман");
    }

    private void continueListening() {
        listening = false;
        if (continuousMode) handler.postDelayed(this::startVoiceInput, 100);
    }

    private void finishDictationAfterPause() {
        continuousMode = false;
        listening = false;
        setKeepScreenOnWhileDictating(false);
        handler.removeCallbacksAndMessages(null);
        if (micButton != null) micButton.setText(dictationButtonLabel());
        showStatus("Заустављено — притисни за наставак");
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
            if (continuousMode
                    && SystemClock.uptimeMillis() < initialSpeechDeadline) {
                listening = false;
                handler.postDelayed(this::startVoiceInput, 120);
                return;
            }
            finishDictationAfterPause();
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
            boolean shouldStartSentence = dictationContextKnown
                    ? dictationNextStartsSentence
                    : startsNewSentence(connection);
            if (shouldStartSentence) {
                converted = uppercaseFirstLetter(converted);
            } else {
                converted = lowercaseFirstLetter(converted);
            }
            converted = SerbianTransliterator.normalizeUnexpectedCapitals(
                    converted, shouldStartSentence);
            suppressKeyboardAutoOpenUntil = SystemClock.uptimeMillis() + 1000L;
            boolean addTrailingSpace = !endsWithTightRightPunctuation(converted);
            connection.commitText(converted + (addTrailingSpace ? " " : ""), 1);
            dictationNextStartsSentence =
                    endsWithSentencePunctuation(converted);
            dictationContextKnown = true;
            lastSpaceAddedByDictation = addTrailingSpace;
            lastSpaceAddedManually = false;
        }
        finishDictationAfterPause();
        // The dictated text may end with sentence punctuation. Refresh Shift
        // after stopping dictation so the next manually typed letter starts in
        // uppercase immediately, even when the editor delays selection updates.
        handler.post(this::updateAutomaticShift);
    }

    private boolean endsWithSentencePunctuation(String text) {
        if (text == null) return false;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c == '.' || c == '?' || c == '!';
        }
        return false;
    }

    private boolean endsWithTightRightPunctuation(String text) {
        if (text == null) return false;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c == '(' || c == '/' || c == '\'';
        }
        return false;
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
        if ((error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_NO_MATCH)
                && continuousMode
                && SystemClock.uptimeMillis() < initialSpeechDeadline) {
            startSilenceRetries++;
            handler.postDelayed(this::startVoiceInput, 120);
            return;
        }
        if (continuousMode) {
            finishDictationAfterPause();
        } else {
            finishListening("Заустављено");
        }
        showRecognitionError(error);
    }

    private void showRecognitionError(int error) {
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = latinScript
                        ? "Proveri internet vezu i pokušaj ponovo."
                        : "Провери интернет везу и покушај поново.";
                break;
            case SpeechRecognizer.ERROR_SERVER:
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = latinScript
                        ? "Google prepoznavanje trenutno nije dostupno. Pokušaj ponovo."
                        : "Google препознавање тренутно није доступно. Покушај поново.";
                break;
            case SpeechRecognizer.ERROR_AUDIO:
                message = latinScript
                        ? "Mikrofon trenutno nije dostupan."
                        : "Микрофон тренутно није доступан.";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = latinScript
                        ? "Dozvoli aplikaciji korišćenje mikrofona."
                        : "Дозволи апликацији коришћење микрофона.";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = latinScript
                        ? "Govor nije prepoznat. Pokušaj ponovo."
                        : "Говор није препознат. Покушај поново.";
                break;
            default:
                message = latinScript
                        ? "Diktiranje nije uspelo. Pokušaj ponovo."
                        : "Диктирање није успело. Покушај поново.";
        }
        showTemporaryMicMessage(message);
    }

    private void showTemporaryMicMessage(String message) {
        if (micButton != null) {
            micButton.setText(message);
            micButton.setSingleLine(false);
            micButton.setMaxLines(2);
            ViewGroup.LayoutParams buttonParams = micButton.getLayoutParams();
            buttonParams.height = dp(84);
            micButton.setLayoutParams(buttonParams);
        }
        if (scriptButton != null) scriptButton.setVisibility(View.GONE);
        if (topControlRow != null) {
            ViewGroup.LayoutParams rowParams = topControlRow.getLayoutParams();
            rowParams.height = dp(84);
            topControlRow.setLayoutParams(rowParams);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        handler.postDelayed(() -> {
            restoreMicButtonLayout();
            if (micButton != null && !continuousMode && !listening) {
                micButton.setText(dictationButtonLabel());
            }
        }, 4500);
    }

    private void restoreMicButtonLayout() {
        if (micButton != null) {
            micButton.setSingleLine(true);
            micButton.setMaxLines(1);
            ViewGroup.LayoutParams buttonParams = micButton.getLayoutParams();
            buttonParams.height = dp(50);
            micButton.setLayoutParams(buttonParams);
        }
        if (scriptButton != null) scriptButton.setVisibility(View.VISIBLE);
        if (topControlRow != null) {
            ViewGroup.LayoutParams rowParams = topControlRow.getLayoutParams();
            rowParams.height = dp(50);
            topControlRow.setLayoutParams(rowParams);
        }
    }

    private void setKeepScreenOnWhileDictating(boolean keepOn) {
        if (micButton != null) micButton.setKeepScreenOn(keepOn);
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
    @Override public void onBeginningOfSpeech() {
        speechStarted = true;
        startSilenceRetries = 0;
        showStatus("Препознајем…");
    }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { showStatus("Обрађујем…"); }
    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}
}
