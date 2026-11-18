package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search's keyboard in the 07 look (homefield-07-real 25): a frosted panel at the right (1120, 330, 710 wide, rounded
 * 40) with the names found as suggestion pills across its top (the words typed in bold), then the letters a to z with a
 * hyphen and an apostrophe in seven columns of keys 74 high, rounded 20, and under them 123 (digits and marks), space,
 * delete and Clear. The key the remote is on is white with dark writing, a little larger (1.08). A keyboard plugged into
 * the TV types into it too. It draws into its screen's own view (draw), and takes the remote's keys while its screen
 * says it has them; nothing is made while drawing.
 */
final class SearchKeys {
    interface Host {
        /** The words changed (typed, deleted, cleared, a suggestion taken). */
        void typed(String text);

        /** Past the keys' edge (Ui.UP, Ui.DOWN, Ui.LEFT); true when the screen took it. */
        boolean edge(int dir);
    }

    /** The panel (design px), its padding, a key's height, the gaps; the bottom row's keys' shares of it. */
    static final float X = 1120f, Y = 330f, W = 710f, PAD = 24f, KEY_H = 74f, GAP = 10f, SUG_H = 46f, SUG_AFTER = 18f, R = 40f;
    private static final String[] LETTERS = {"abcdefg", "hijklmn", "opqrstu", "vwxyz-'"};
    private static final String[] DIGITS = {"1234567", "890.,@&", "!?()/:+", "#$%_=;\""};
    private static final float[] BOTTOM = {1f, 2.4f, 1f, 1f};
    private static final int COLS = 7, ROWS = 4, BOTTOM_ROW = 4;
    private static final int K123 = 0, SPACE = 1, DELETE = 2, CLEAR = 3;

    private final Host host;
    private String text = "";
    private boolean digits;
    /** The remote's row (-1: the suggestions; 0-3 the keys; 4 the bottom row) and place along it. */
    private int fr, fk;
    private final List<String> suggestions = new ArrayList<>();
    private final List<String> lowered = new ArrayList<>();
    private final List<RectF> sugBoxes = new ArrayList<>();
    private float sugMadeFor = -1f;
    private float height;

    private final Frost panel = new Frost(Look07.MAT);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint keyP, keyOn, smallP, sugP, sugBold;
    private final RectF r = new RectF();

    SearchKeys(Host host) {
        this.host = host;
        keyP = Type.paint(28f, Type.MEDIUM, Look07.L1);
        keyOn = Type.paint(28f, Type.BOLD, Look07.INK);
        smallP = Type.paint(20f, Type.SEMIBOLD, Look07.L2);
        sugP = Type.paint(19f, Type.MEDIUM, Look07.L2);
        sugBold = Type.paint(19f, Type.BOLD, Look07.L1);
        Shadows.prepare(Look07.SH_ROW, Look07.R_KEY);
        Shadows.prepare(Look07.SH_PANEL, R);
    }

    // ---- what it holds -------------------------------------------------------------------------------------------------

    String text() {
        return text;
    }

    void setText(String t) {
        text = t == null ? "" : t;
    }

    /** Back on the keys' first, letters. */
    void reset() {
        fr = 0;
        fk = 0;
        digits = false;
    }

    /** The names found to suggest (the first five), laid out now. */
    void suggest(List<String> words) {
        suggestions.clear();
        lowered.clear();
        if (words != null) {
            for (String w : words) {
                if (suggestions.size() >= 5 || w == null || w.isEmpty()) continue;
                String low = w.toLowerCase(Locale.UK);
                if (lowered.contains(low)) continue;
                suggestions.add(low);
                lowered.add(low);
            }
        }
        sugMadeFor = -1f;
        if (fr < 0 && suggestions.isEmpty()) {
            fr = 0;
            fk = 0;
        }
        fk = fr < 0 ? Math.min(fk, suggestions.size() - 1) : fk;
        layout();
    }

    /** The panel's height (design px), as laid out last. */
    float height() {
        layout();
        return height;
    }

    /** The suggestions' pills in rows (flex-wrap, 10 apart), and the panel's height: made when they change, not each frame. */
    private void layout() {
        if (sugMadeFor == Ui.s) return;
        sugMadeFor = Ui.s;
        sugBoxes.clear();
        float x = PAD, y = PAD, room = W - 2 * PAD;
        for (String s : suggestions) {
            float w = Ui.f(18f) + Ui.f(18f) + Ui.f(8f) + sugBold.measureText(s) + Ui.f(18f);
            w /= Ui.s;
            if (x + w > PAD + room && x > PAD) {
                x = PAD;
                y += SUG_H + GAP;
            }
            sugBoxes.add(new RectF(x, y, Math.min(x + w, PAD + room), y + SUG_H));
            x += w + GAP;
        }
        float keysTop = suggestions.isEmpty() ? PAD : y + SUG_H + SUG_AFTER;
        height = keysTop + ROWS * KEY_H + (ROWS - 1) * GAP + GAP + KEY_H + PAD;
        top = keysTop;
    }

    private float top;

    // ---- the remote -------------------------------------------------------------------------------------------------

    private int rowLength(int row) {
        if (row < 0) return suggestions.size();
        return row == BOTTOM_ROW ? BOTTOM.length : COLS;
    }

    boolean key(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (fk > 0) fk--;
                else return host.edge(Ui.LEFT);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (fk < rowLength(fr) - 1) fk++;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP: {
                int nr = fr - 1;
                if (nr < -1 || nr == -1 && suggestions.isEmpty()) return host.edge(Ui.UP);
                moveTo(nr);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int nr = fr + 1;
                if (nr > BOTTOM_ROW) return host.edge(Ui.DOWN);
                moveTo(nr);
                return true;
            }
            case KeyEvent.KEYCODE_DEL:
                delete();
                return true;
            case KeyEvent.KEYCODE_SPACE:
                type(" ");
                return true;
            default: {
                // A real keyboard (or adb's input text): its characters as they come.
                int u = e == null ? 0 : e.getUnicodeChar();
                if (u > 0 && !Character.isISOControl(u)) {
                    type(new String(Character.toChars(u)));
                    return true;
                }
                return false;
            }
        }
    }

    /** To another row, at the key under the one the remote was on (by its middle). */
    private void moveTo(int nr) {
        float mid = middle(fr, fk);
        fr = nr;
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int k = 0; k < rowLength(fr); k++) {
            float d = Math.abs(middle(fr, k) - mid);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        fk = best;
    }

    /** Where a key's middle is across the panel (design px). */
    private float middle(int row, int k) {
        layout();
        if (row < 0) return k < sugBoxes.size() ? sugBoxes.get(k).centerX() : PAD;
        float room = W - 2 * PAD;
        if (row == BOTTOM_ROW) {
            float units = 0;
            for (float b : BOTTOM) units += b;
            float unit = (room - (BOTTOM.length - 1) * GAP) / units, x = PAD;
            for (int i = 0; i < k; i++) x += BOTTOM[i] * unit + GAP;
            return x + BOTTOM[k] * unit / 2f;
        }
        float kw = (room - (COLS - 1) * GAP) / COLS;
        return PAD + k * (kw + GAP) + kw / 2f;
    }

    void ok() {
        if (fr < 0) {
            if (fk < suggestions.size()) {
                text = suggestions.get(fk);
                host.typed(text);
            }
            return;
        }
        if (fr == BOTTOM_ROW) {
            switch (fk) {
                case K123:
                    digits = !digits;
                    return;
                case SPACE:
                    type(" ");
                    return;
                case DELETE:
                    delete();
                    return;
                default:
                    text = "";
                    host.typed(text);
                    return;
            }
        }
        String row = (digits ? DIGITS : LETTERS)[fr];
        type(String.valueOf(row.charAt(Math.min(fk, row.length() - 1))));
    }

    private void type(String s) {
        text = text + s;
        host.typed(text);
    }

    private void delete() {
        if (text.isEmpty()) return;
        text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        host.typed(text);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    /** The panel and its keys; active: the remote is on it (its key lit). */
    void draw(Canvas c, boolean active, int alpha) {
        layout();
        float px = Ui.f(X), py = Ui.f(Y);
        r.set(px, py, px + Ui.f(W), py + Ui.f(height));
        panel.draw(c, r, Ui.f(R), alpha / 255f);
        // The suggestions: a glass pill each, its search glass, the words typed in bold.
        for (int i = 0; i < suggestions.size() && i < sugBoxes.size(); i++) {
            RectF b = sugBoxes.get(i);
            r.set(px + Ui.f(b.left), py + Ui.f(b.top), px + Ui.f(b.right), py + Ui.f(b.bottom));
            boolean on = active && fr < 0 && fk == i;
            fill.setColor(on ? Look07.FOCUS_FILL : 0x1FFFFFFF);
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
            float cy = r.centerY(), x = r.left + Ui.f(18f);
            Icons07.draw(c, Icons07.SEARCH, x + Ui.f(9f), cy, Ui.f(18f), on ? Look07.INK2 : Look07.L2);
            x += Ui.f(18f) + Ui.f(8f);
            String s = suggestions.get(i);
            int at = text.isEmpty() ? -1 : s.indexOf(text.toLowerCase(Locale.UK).trim());
            int end = at < 0 ? at : Math.min(s.length(), at + text.trim().length());
            sugP.setColor(on ? Look07.INK2 : Look07.L2);
            sugBold.setColor(on ? Look07.INK : Look07.L1);
            float base = Type.centred(sugP, cy);
            if (at < 0 || end <= at) {
                c.drawText(s, x, base, sugP);
            } else {
                c.drawText(s, 0, at, x, base, sugP);
                x += sugP.measureText(s, 0, at);
                c.drawText(s, at, end, x, base, sugBold);
                x += sugBold.measureText(s, at, end);
                c.drawText(s, end, s.length(), x, base, sugP);
            }
        }
        // The keys.
        float room = W - 2 * PAD, kw = (room - (COLS - 1) * GAP) / COLS;
        String[] rows = digits ? DIGITS : LETTERS;
        for (int ri = 0; ri < ROWS; ri++) {
            for (int k = 0; k < COLS; k++) {
                float x = PAD + k * (kw + GAP), y = top + ri * (KEY_H + GAP);
                key(c, px + Ui.f(x), py + Ui.f(y), Ui.f(kw), String.valueOf(rows[ri].charAt(k)), active && fr == ri && fk == k, false);
            }
        }
        float units = 0;
        for (float b : BOTTOM) units += b;
        float unit = (room - (BOTTOM.length - 1) * GAP) / units, x = PAD, y = top + ROWS * (KEY_H + GAP);
        for (int k = 0; k < BOTTOM.length; k++) {
            float w = BOTTOM[k] * unit;
            boolean on = active && fr == BOTTOM_ROW && fk == k;
            String label = k == K123 ? digits ? "abc" : "123" : k == SPACE ? "space" : k == CLEAR ? "Clear" : "";
            key(c, px + Ui.f(x), py + Ui.f(y), Ui.f(w), label, on, true);
            if (k == DELETE) Icons07.draw(c, Icons07.LEFT, px + Ui.f(x + w / 2f), py + Ui.f(y + KEY_H / 2f), Ui.f(22f), on ? Look07.INK : Look07.L2);
            x += w + GAP;
        }
    }

    /** One key (screen px): white 10 %, or white with dark writing, 1.08 and its shadow where the remote is. */
    private void key(Canvas c, float x, float y, float w, String label, boolean on, boolean small) {
        float h = Ui.f(KEY_H), rad = Ui.f(Look07.R_KEY);
        r.set(x, y, x + w, y + h);
        c.save();
        if (on) {
            c.scale(1.08f, 1.08f, r.centerX(), r.centerY());
            Shadows.draw(c, r, rad, Look07.SH_ROW, 1f);
        }
        fill.setColor(on ? Look07.L1 : 0x1AFFFFFF);
        c.drawRoundRect(r, rad, rad, fill);
        if (!label.isEmpty()) {
            TextPaint p = small ? smallP : on ? keyOn : keyP;
            int was = p.getColor();
            if (small) p.setColor(on ? Look07.INK : Look07.L2);
            c.drawText(label, r.centerX() - p.measureText(label) / 2f, Type.centred(p, r.centerY()), p);
            p.setColor(was);
        }
        c.restore();
    }
}
