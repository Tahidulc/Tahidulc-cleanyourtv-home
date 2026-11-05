package com.gathin.tvhome;

import java.util.Locale;

/**
 * The rules for typing with the remote's keyboard (RemoteKeyboard), kept free of Android so the JVM tests reach them:
 * a capital comes by itself at the start and after a full stop, ! or ? (or with ⇧ on), a message stops at its
 * length, and ⌫ takes an emoji off whole.
 */
final class TypeRules {
    private TypeRules() { }

    /** A capital next: at the start, after a full stop, ! or ?, or with ⇧ on. */
    static boolean upperNext(CharSequence text, boolean shift) {
        if (shift) return true;
        String t = text.toString();
        return t.trim().isEmpty() || t.matches("(?s).*[.!?]\\s+$");
    }

    /** One letter, which ⇧ and the capitals apply to. */
    static boolean letter(String s) {
        return s != null && s.length() == 1 && Character.isLetter(s.charAt(0));
    }

    /** What a key adds after text: a letter in capitals where one comes next, anything else as it is. */
    static String cased(CharSequence text, String s, boolean shift) {
        return letter(s) && upperNext(text, shift) ? s.toUpperCase(Locale.UK) : s;
    }

    /** Whether s still fits after text, in a message of at most max characters. */
    static boolean fits(CharSequence text, String s, int max) {
        return text.length() + s.length() <= max;
    }

    /** The length ⌫ cuts text back to: its last character off, an emoji whole (❤️ is the heart and a variation sign). */
    static int deleted(CharSequence text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '️') end--;
        return end > 0 ? Character.offsetByCodePoints(text, end, -1) : 0;
    }
}
