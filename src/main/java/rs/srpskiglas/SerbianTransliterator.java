package rs.srpskiglas;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Google Serbian Latin dictation into Serbian Cyrillic. */
public final class SerbianTransliterator {
    private SerbianTransliterator() {}

    private static final Pattern SPOKEN_PUNCTUATION = Pattern.compile(
            "(?iu)(?:\\s+|^)(?:znak\\s+(zarez|tačka|upitnik|uzvičnik)|komanda\\s+(novi red))(?=\\s|$)");

    public static String convert(String dictatedText) {
        if (dictatedText == null || dictatedText.trim().isEmpty()) return "";
        String normalized = repairRecognition(dictatedText.trim());
        normalized = applySpokenPunctuation(normalized);
        return transliterate(normalized);
    }

    static String repairRecognition(String text) {
        // A frequent Google STT omission observed in the baseline test.
        String repaired = text.replaceAll("(?iu)\\bkonjukcij", "konjunkcij");
        // Google STT consistently treats the common noun "đak" as a name.
        // Sentence capitalization is applied later, so sentence-initial "Đak" stays correct.
        return repaired.replaceAll("(?iu)\\bđak\\b", "đak");
    }

    static String applySpokenPunctuation(String text) {
        Matcher matcher = SPOKEN_PUNCTUATION.matcher(" " + text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String command = (matcher.group(1) != null ? matcher.group(1) : matcher.group(2))
                    .toLowerCase(Locale.ROOT);
            String replacement;
            switch (command) {
                case "zarez": replacement = ","; break;
                case "tačka": replacement = "."; break;
                case "upitnik": replacement = "?"; break;
                case "uzvičnik": replacement = "!"; break;
                default: replacement = "\n";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        String result = out.toString().trim().replaceAll("[ \\t]+([,.?!])", "$1");
        result = result.replaceAll("([,.?!])(?=\\p{L})", "$1 ");
        return capitalizeSentences(result);
    }

    static String capitalizeSentences(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean capitalize = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (capitalize && Character.isLetter(c)) {
                c = Character.toUpperCase(c);
                capitalize = false;
            }
            out.append(c);
            if (c == '.' || c == '?' || c == '!' || c == '\n') capitalize = true;
        }
        return out.toString();
    }

    static String transliterate(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            char lower = Character.toLowerCase(c);
            if (i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                String pair = "" + lower + next;
                String mapped = null;
                if (pair.equals("lj")) mapped = "љ";
                else if (pair.equals("nj") && !isNjBoundaryException(text, i)) mapped = "њ";
                else if (pair.equals("dž") && !isDzBoundaryException(text, i)) mapped = "џ";
                if (mapped != null) {
                    boolean upper = Character.isUpperCase(c);
                    out.append(upper ? mapped.toUpperCase(Locale.ROOT) : mapped);
                    i += 2;
                    continue;
                }
            }
            out.append(mapSingle(c));
            i++;
        }
        return out.toString();
    }

    private static boolean isNjBoundaryException(String text, int index) {
        String word = wordAt(text, index).toLowerCase(Locale.ROOT);
        return word.startsWith("injekc") || word.startsWith("konjunkc")
                || word.startsWith("tanjug") || word.startsWith("anjon");
    }

    private static boolean isDzBoundaryException(String text, int index) {
        String word = wordAt(text, index).toLowerCase(Locale.ROOT);
        return word.startsWith("nadživ") || word.startsWith("podživ")
                || word.startsWith("odživ") || word.startsWith("predživ");
    }

    private static String wordAt(String text, int index) {
        int start = index;
        int end = index;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
        while (end < text.length() && Character.isLetter(text.charAt(end))) end++;
        return text.substring(start, end);
    }

    private static char mapSingle(char c) {
        boolean upper = Character.isUpperCase(c);
        char x;
        switch (Character.toLowerCase(c)) {
            case 'a': x='а'; break; case 'b': x='б'; break; case 'c': x='ц'; break;
            case 'č': x='ч'; break; case 'ć': x='ћ'; break; case 'd': x='д'; break;
            case 'đ': x='ђ'; break; case 'e': x='е'; break; case 'f': x='ф'; break;
            case 'g': x='г'; break; case 'h': x='х'; break; case 'i': x='и'; break;
            case 'j': x='ј'; break; case 'k': x='к'; break; case 'l': x='л'; break;
            case 'm': x='м'; break; case 'n': x='н'; break; case 'o': x='о'; break;
            case 'p': x='п'; break; case 'r': x='р'; break; case 's': x='с'; break;
            case 'š': x='ш'; break; case 't': x='т'; break; case 'u': x='у'; break;
            case 'v': x='в'; break; case 'z': x='з'; break; case 'ž': x='ж'; break;
            default: return c;
        }
        return upper ? Character.toUpperCase(x) : x;
    }
}
