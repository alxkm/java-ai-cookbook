package com.example.cookbook;

import java.util.regex.Pattern;

/**
 * Regex-based redaction. It is not a complete PII detector and nothing of this shape ever is -
 * it is the cheap layer you run before the expensive one, and before the data leaves your network.
 */
final class Pii {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b");

    private Pii() {
    }

    static String redact(String text) {
        if (text == null) {
            return null;
        }
        String redacted = EMAIL.matcher(text).replaceAll("[EMAIL]");
        redacted = IBAN.matcher(redacted).replaceAll("[IBAN]");
        redacted = CARD.matcher(redacted).replaceAll("[CARD]");
        return redacted;
    }

    static boolean contains(String text) {
        return text != null && !redact(text).equals(text);
    }
}
