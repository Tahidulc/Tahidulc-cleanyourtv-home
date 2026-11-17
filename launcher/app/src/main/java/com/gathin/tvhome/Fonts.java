package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * The launcher's own fonts (res/font), each read once and kept for the process's life. Android keeps only the last 16
 * fonts read from an app's files; the launcher uses more than that (Inter, Inter Display and Outfit in a dozen
 * weights), so asking Resources.getFont again each time a card, a panel or a row was built read the font file again
 * and made a new font of it, over and over. Here each is made the first time and handed out after. A font that cannot
 * be read is not kept (the caller's own fallback, as before).
 */
final class Fonts {
    private Fonts() { }

    /** Makes a value; may throw, and then nothing is kept. */
    interface Maker<V> {
        V make();
    }

    /** Pure: each key's value made once and kept; the maker is never run again for a key that has its value. */
    static final class Memo<K, V> {
        private final Map<K, V> made = new HashMap<>();

        synchronized V get(K key, Maker<V> maker) {
            V v = made.get(key);
            if (v != null) return v;
            v = maker.make();
            if (v != null) made.put(key, v);
            return v;
        }

        synchronized int size() {
            return made.size();
        }
    }

    private static final Memo<Integer, Typeface> FONTS = new Memo<>();

    /** The font resource res (R.font.*), read once. Throws as Resources.getFont does when it cannot be read. */
    static Typeface get(Context c, int res) {
        return FONTS.get(res, () -> c.getResources().getFont(res));
    }

    /** How many fonts are held, for the self-check. */
    static int count() {
        return FONTS.size();
    }
}
