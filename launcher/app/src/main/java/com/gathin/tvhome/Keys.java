package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The remote's own OK and Back keys in place of the words: a hint such as "OK to open · Back to close" shows the TCL's
 * round black OK button (or the Streamer's pale one) and its Back key, drawn at the text's size and centred on its
 * line, instead of the letters. One place, so every panel, card and page says it the same way.
 */
final class Keys {
    private Keys() {
    }

    /** "OK", "Back" or "BACK" as a whole word (not "WELCOME BACK", which callers do not pass). */
    private static final Pattern WORD = Pattern.compile("\\b(OK|Back|BACK)\\b");

    /**
     * text with each OK and Back as the remote's key, sized for text of textPx (the key a little taller than the
     * letters); onDark: the OK key's ring lighter, for a dark ground. Plain text back when there is no key in it.
     */
    static CharSequence icons(Context c, CharSequence text, float textPx, boolean onDark) {
        if (text == null) return "";
        Matcher m = WORD.matcher(text);
        if (!m.find()) return text;
        String remote = Config.themeString(c, "ok_button", "tcl");
        int size = Math.max(8, Math.round(textPx * 1.45f));
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        m.reset();
        while (m.find()) {
            boolean ok = "OK".equals(m.group(1));
            Bitmap key = ok ? GoalWatchService.okButton(remote, size / 22f, onDark) : GoalWatchService.backButton(remote, size);
            s.setSpan(new KeySpan(key, size, textPx * 0.2f), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** icons() for a TextView: its own text size and the dark ground the launcher's hints sit on. */
    static void set(android.widget.TextView v, CharSequence text) {
        v.setText(icons(v.getContext(), text, v.getTextSize(), true));
    }

    /** A key's picture in a line of text: centred on the line, a little space either side. */
    private static final class KeySpan extends ReplacementSpan {
        private final Bitmap key;
        private final int size;
        private final float pad;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        KeySpan(Bitmap key, int size, float pad) {
            this.key = key;
            this.size = size;
            this.pad = pad;
        }

        @Override public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt own = p.getFontMetricsInt();
                int textH = own.descent - own.ascent;
                int extra = Math.max(0, size - textH);
                fm.ascent = own.ascent - extra / 2;
                fm.descent = own.descent + (extra - extra / 2);
                fm.top = Math.min(own.top, fm.ascent);
                fm.bottom = Math.max(own.bottom, fm.descent);
            }
            return Math.round(size + 2 * pad);
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint p) {
            Paint.FontMetricsInt fm = p.getFontMetricsInt();
            float centre = y + (fm.ascent + fm.descent) / 2f;
            int left = Math.round(x + pad), t = Math.round(centre - size / 2f);
            canvas.drawBitmap(key, null, new Rect(left, t, left + size, t + size), paint);
        }
    }
}
