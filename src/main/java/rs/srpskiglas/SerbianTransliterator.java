package rs.srpskiglas;

import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Google Serbian Latin dictation into Serbian Cyrillic. */
public final class SerbianTransliterator {
    private SerbianTransliterator() {}

    private static final Set<String> COMMON_WORDS_GOOGLE_MAY_CAPITALIZE =
            new HashSet<>(Arrays.asList(
                    "a", "ali", "da", "dakle", "evo", "hajde", "hoćeš", "i", "ili",
                    "ima", "jer", "kad", "kada", "kako", "kojoj", "međutim", "na", "nakon",
                    "ne", "nego", "novom", "onda", "ovo", "pa", "po", "pre", "sad", "sada",
                    "sa", "samo", "tada", "tako", "to", "u", "za", "zato", "zatim", "znači", "što",
                    "а", "али", "да", "дакле", "ево", "хајде", "хоћеш", "и", "или",
                    "има", "јер", "кад", "када", "како", "којој", "међутим", "на", "након",
                    "не", "него", "новом", "онда", "ово", "па", "по", "пре", "сад", "сада",
                    "са", "само", "тада", "тако", "то", "у", "за", "зато", "затим", "значи", "што"));

    private static final Set<String> COMMON_FOREIGN_NAMES =
            new HashSet<>(Arrays.asList(
                    "android", "chatgpt", "facebook", "gboard", "google", "hyundai",
                    "instagram", "iphone", "microsoft", "tiktok", "whatsapp", "youtube"));

    private static final Pattern SPOKEN_PUNCTUATION = Pattern.compile(
            "(?iu)(?:\\s+|^)(?:znak\\s+(tačka\\s+zarez|zarez|tačka|upitnik|"
                    + "pitanja|uzvičnik|dve\\s+tačke|dvotačka|kosa\\s+crta|apostrof|"
                    + "otvorena\\s+zagrada|zatvorena\\s+zagrada)|"
                    + "(zarez|tačka|upitnik|uzvičnik)|"
                    + "komanda\\s+(novi red))(?=\\s|$)");

    public static String convert(String dictatedText) {
        if (dictatedText == null || dictatedText.trim().isEmpty()) return "";
        String normalized = repairRecognition(dictatedText.trim());
        normalized = applySpokenPunctuation(normalized);
        return transliteratePreservingForeignWords(normalized);
    }

    /** Keeps Serbian Latin output while applying the same repairs and punctuation commands. */
    public static String convertLatin(String dictatedText) {
        if (dictatedText == null || dictatedText.trim().isEmpty()) return "";
        String normalized = repairRecognition(dictatedText.trim());
        return applySpokenPunctuation(normalized);
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
            String command = matcher.group(1) != null
                    ? matcher.group(1)
                    : (matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            command = command.toLowerCase(Locale.ROOT);
            String replacement;
            switch (command) {
                case "zarez": replacement = ","; break;
                case "tačka": replacement = "."; break;
                case "upitnik":
                case "pitanja": replacement = "?"; break;
                case "uzvičnik": replacement = "!"; break;
                case "dve tačke":
                case "dvotačka": replacement = ":"; break;
                case "kosa crta": replacement = "/"; break;
                case "apostrof": replacement = "'"; break;
                case "tačka zarez": replacement = ";"; break;
                case "otvorena zagrada": replacement = " ("; break;
                case "zatvorena zagrada": replacement = ")"; break;
                default: replacement = "\n";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        String result = out.toString().trim().replaceAll("[ \\t]+([,.?!:;)/'])", "$1");
        result = result.replaceAll("([(/'])[ \\t]+", "$1");
        result = result.replaceAll("([,.?!:;])(?=\\p{L})", "$1 ");
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

    /**
     * Google sometimes treats a pause as a new sentence and capitalizes a common
     * word even though it did not return sentence punctuation. Correct only the
     * known unambiguous words so personal names and other proper nouns stay intact.
     */
    static String normalizeUnexpectedCapitals(String text, boolean startsSentence) {
        StringBuilder out = new StringBuilder(text);
        boolean sentenceStart = startsSentence;
        String previousWord = "";
        for (int i = 0; i < out.length();) {
            char c = out.charAt(i);
            if (!Character.isLetter(c)) {
                if (c == '.' || c == '?' || c == '!' || c == '\n') sentenceStart = true;
                i++;
                continue;
            }

            int wordStart = i;
            while (i < out.length() && Character.isLetter(out.charAt(i))) i++;
            String word = out.substring(wordStart, i);
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.equals("ок") || lower.equals("ok")) {
                out.setCharAt(wordStart,
                        Character.toUpperCase(out.charAt(wordStart)));
                out.setCharAt(wordStart + 1,
                        Character.toUpperCase(out.charAt(wordStart + 1)));
                sentenceStart = false;
                previousWord = lower;
                continue;
            }
            boolean allCapsAbbreviation = word.length() > 1
                    && word.equals(word.toUpperCase(Locale.ROOT));
            if (!sentenceStart && !allCapsAbbreviation
                    && Character.isUpperCase(word.charAt(0))
                    && COMMON_WORDS_GOOGLE_MAY_CAPITALIZE.contains(lower)
                    && !isProtectedProperNamePart(lower, previousWord)) {
                out.setCharAt(wordStart, Character.toLowerCase(word.charAt(0)));
            }
            sentenceStart = false;
            previousWord = lower;
        }
        return out.toString();
    }

    private static boolean isProtectedProperNamePart(String word, String previousWord) {
        return (word.equals("sad") && previousWord.equals("novi"))
                || (word.equals("сад") && previousWord.equals("нови"));
    }

    /** Keeps obvious foreign words in their original Latin spelling. */
    static String transliteratePreservingForeignWords(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            if (!Character.isLetter(text.charAt(i))) {
                out.append(text.charAt(i));
                i++;
                continue;
            }

            int wordStart = i;
            while (i < text.length() && Character.isLetter(text.charAt(i))) i++;
            String word = text.substring(wordStart, i);
            if (shouldKeepLatinWord(word)) out.append(word);
            else out.append(transliterate(word));
        }
        return out.toString().trim();
    }

    private static boolean shouldKeepLatinWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (COMMON_FOREIGN_NAMES.contains(lower)) return true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == 'q' || c == 'w' || c == 'x' || c == 'y') return true;
        }
        boolean allUppercase = word.length() > 1
                && word.equals(word.toUpperCase(Locale.ROOT));
        if (allUppercase) return false;
        for (int i = 1; i < word.length(); i++) {
            if (Character.isUpperCase(word.charAt(i))) return true;
        }
        return false;
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
