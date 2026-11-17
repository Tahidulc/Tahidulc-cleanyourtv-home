package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The keyboard for typing with the remote: a white box with the words so far, then the digits, three rows of letters
 * and stops, a row of emoji, and (Say it,) ⇧, Space, ⌫ and Send. The arrows move across and between the rows, OK
 * presses, and a keyboard remote (or the remote's own digits) types straight in. Capitals come by themselves
 * (TypeRules). Message the other TV (TvLink.Panel) and Quick reply (QuickReply) both type with it; what sits above
 * its keys (the TVs, the quick replies) is theirs, reached with Up from the top row.
 */
final class RemoteKeyboard {
    /** How its owner draws: a size in the owner's design pixels, and a text in its fonts. */
    interface Look {
        int px(float design);

        TextView text(float size, Typeface face, int colour);
    }

    /** What sits above the keys, which Up from the top row reaches. */
    interface Above {
        /** Whether there is anything up there to go to. */
        boolean any();

        /** Left (-1) or Right (+1) along it. */
        void move(int by);

        /** OK on it: true when the focus stays up there, false to come down to the keys. */
        boolean ok();
    }

    final LinearLayout view;
    final TextView field;
    final StringBuilder text = new StringBuilder();
    boolean shift;
    /** The key in focus: its row (-1: what is above the keys) and its place in the row. */
    int row = 1, col = 0;
    /** Send, Say it, every change (the owner draws itself and paint()), and a word for its status line. */
    Runnable onSend, onVoice, onChange;
    Consumer<String> onStatus;
    Above above;

    private final Look look;
    private final Typeface semi;
    private final int max;
    private final List<List<TextView>> keys = new ArrayList<>();
    private final List<List<Runnable>> acts = new ArrayList<>();

    /** hint: the empty box's words; mic: a Say it key (only where an activity can hear it); max: the longest message. */
    RemoteKeyboard(Context c, Look look, String hint, boolean mic, int max) {
        this.look = look;
        this.max = max;
        semi = Fonts.get(c, R.font.inter_semibold);
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        field = look.text(30, semi, 0xFF10157E);
        field.setHint(hint);
        field.setHintTextColor(0x8010157E);
        field.setMaxLines(2);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(look.px(22), look.px(10), look.px(22), look.px(10));
        GradientDrawable fb = new GradientDrawable();
        fb.setCornerRadius(look.px(18));
        fb.setColor(Color.WHITE);
        field.setBackground(fb);
        field.setMinHeight(look.px(76));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.bottomMargin = look.px(14);
        view.addView(field, fl);
        String[] rows = {"1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm,.?"};
        for (String r : rows) {
            List<String> labels = new ArrayList<>();
            for (char ch : r.toCharArray()) labels.add(String.valueOf(ch));
            keyRow(c, labels, null);
        }
        keyRow(c, Arrays.asList("!", "😊", "❤️", "👍", "😂", "🍛", "🕌", "⚽", "🎉", "🌙"), null);
        if (mic) keyRow(c, Arrays.asList("mic", "shift", "space", "delete", "send"), new float[]{1.6f, 1.6f, 3.4f, 1.4f, 2f});
        else keyRow(c, Arrays.asList("shift", "space", "delete", "send"), new float[]{1.6f, 5f, 1.4f, 2f});
    }

    private void keyRow(Context c, List<String> labels, float[] weights) {
        LinearLayout r = new LinearLayout(c);
        List<TextView> views = new ArrayList<>();
        List<Runnable> did = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String l = labels.get(i);
            TextView key = look.text(l.length() == 1 ? 26 : 22, semi, Color.WHITE);
            key.setTag(l);
            key.setGravity(Gravity.CENTER);
            key.setText("mic".equals(l) ? "🎤 Say it" : "space".equals(l) ? "Space" : "delete".equals(l) ? "⌫" : "send".equals(l) ? "Send ➤" : l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, look.px(56), weights == null ? 1f : weights[i]);
            if (i > 0) lp.leftMargin = look.px(6);
            r.addView(key, lp);
            views.add(key);
            did.add("mic".equals(l) ? () -> run(onVoice) : "shift".equals(l) ? () -> shift = !shift : "space".equals(l) ? () -> type(" ")
                    : "delete".equals(l) ? this::delete : "send".equals(l) ? () -> run(onSend) : () -> type(l));
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = look.px(6);
        view.addView(r, rl);
        keys.add(views);
        acts.add(did);
    }

    private static void run(Runnable r) {
        if (r != null) r.run();
    }

    private void status(String s) {
        if (onStatus != null) onStatus.accept(s);
    }

    private void changed() {
        if (onChange != null) onChange.run(); else paint();
    }

    /** The keys drawn afresh: the letters in capitals where one comes next, the key in focus white, Send green. */
    void paint() {
        boolean upper = TypeRules.upperNext(text, shift);
        for (int r = 0; r < keys.size(); r++) {
            for (int k = 0; k < keys.get(r).size(); k++) {
                TextView key = keys.get(r).get(k);
                String label = String.valueOf(key.getTag());
                if (TypeRules.letter(label)) key.setText(upper ? label.toUpperCase(Locale.UK) : label);
                if ("shift".equals(label)) key.setText(shift ? "⇧ ABC" : "⇧ abc");
                boolean on = r == row && k == col;
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(look.px(12));
                g.setColor(on ? 0xFFFFFFFF : "send".equals(label) ? 0xFF3DDC84 : 0x22FFFFFF);
                key.setBackground(g);
                key.setTextColor(on ? 0xFF10157E : "send".equals(label) ? 0xFF06301A : Color.WHITE);
            }
        }
        field.setText(text.length() == 0 ? "" : text + "▏");
    }

    /** Words typed in at the end (a key, a keyboard remote's character, words said aloud). */
    void type(String s) {
        if (s == null || s.isEmpty()) return;
        s = TypeRules.cased(text, s, shift);
        if (!TypeRules.fits(text, s, max)) { status("That's as long as a message goes"); return; }
        text.append(s);
        if (TypeRules.letter(s)) shift = false;
        status("");
        changed();
    }

    /** ⌫: the last character off (an emoji whole). */
    void delete() {
        if (text.length() == 0) return;
        text.setLength(TypeRules.deleted(text));
        changed();
    }

    static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static boolean arrow(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** The character a keyboard remote's key (or the remote's digit) types; 0 for none. */
    private static int character(KeyEvent e) {
        int ch = e.getUnicodeChar();
        return ch > 0 && !Character.isISOControl(ch) && !arrow(e.getKeyCode()) ? ch : 0;
    }

    /** Whether key() answers this key: the arrows, OK, ⌫, Space and a typed character (Back is the owner's). */
    static boolean takes(KeyEvent e) {
        int code = e.getKeyCode();
        return arrow(code) || isOk(code) || code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_SPACE || character(e) != 0;
    }

    /**
     * A key for the keyboard (Back aside, which its owner decides): OK presses the key in focus as it comes up, the
     * arrows move across and between the rows (keeping roughly the same place) and up to what is above, and a
     * keyboard remote's characters are typed. Every key is taken.
     */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (isOk(code)) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (row < 0) {
                    if (above == null || !above.ok()) row = 0;
                } else {
                    acts.get(row).get(col).run();
                }
                changed();
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        // A keyboard remote, or the remote's own digits: typed straight in.
        if (code == KeyEvent.KEYCODE_DEL) { delete(); return true; }
        if (code == KeyEvent.KEYCODE_SPACE) { type(" "); return true; }
        int ch = character(e);
        if (ch != 0) {
            type(new String(Character.toChars(ch)));
            return true;
        }
        if (row < 0) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && above != null) above.move(-1);
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && above != null) above.move(1);
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) row = 0;
            changed();
            return true;
        }
        int cols = keys.get(row).size();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < cols - 1) col++;
        else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 0) { if (above != null && above.any()) row = -1; }
            else { col = place(row, col, row - 1); row--; }
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && row < keys.size() - 1) {
            col = place(row, col, row + 1);
            row++;
        }
        changed();
        return true;
    }

    /** The key in row to under the middle of key col in row from. */
    private int place(int from, int c, int to) {
        TextView k = keys.get(from).get(c);
        float mid = k.getLeft() + k.getWidth() / 2f;
        List<TextView> r = keys.get(to);
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            float d = Math.abs(r.get(i).getLeft() + r.get(i).getWidth() / 2f - mid);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
}
