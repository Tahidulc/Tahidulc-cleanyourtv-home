package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen keyboard, drawn as the family's TV draws its own keyboard (the dark floating panel the other app's
 * search opens at the bottom right): a strip across its top with suggestions as chips (Search's: names of what it
 * found), then four rows of keys 59 x 65 at 69 and 75 px steps: q to p with 1 to 0 small at their corners, a to l
 * and a comma, shift, z to m, a full stop and delete, then ?123, the caret's left and right, the space bar, - and _,
 * and the search key (or Done). ?123 turns the keys to digits and the marks addresses use. The key the remote is on is
 * white with dark writing; the others dark grey, shift, delete and the like a shade lighter, search a slate blue.
 *
 * <p>A keyboard plugged into the TV, or adb's input text, types into it too. A password and the TorBox key show as
 * dots. Its place is x, y (the panel's top left, design pixels); showField draws the field's name and the field over
 * it (Settings' sheet), as the other app's search field is drawn: light grey, its words dark, a caret where they go.
 */
final class Keyboard extends View {
    interface Host {
        void typed(Keyboard k, String text);

        void done(Keyboard k, String text);

        /** Past the keys' edge (Ui.LEFT, UP, RIGHT, DOWN); true when the screen took it. */
        boolean edge(Keyboard k, int dir);
    }

    static final int SEARCH = 0, FULL = 1;
    private static final int CHAR = 0, SPACE = 1, DELETE = 2, SHIFT = 3, PAGE = 4, DONE = 5, CARET_LEFT = 6, CARET_RIGHT = 7;
    /** The panel (design pixels), a key and the steps between keys. */
    static final float W = 735f, H = 420f, KEY_W = 59f, KEY_H = 65f, STEP_X = 69f, STEP_Y = 75f, KEYS_X = 26f, KEYS_Y = 103f,
            STRIP = 84f;
    /** Gboard's dark colours, as the family's TV draws them. */
    static final int PANEL = 0xFF1C2128, KEYS = 0xFF191D23, KEY = 0xFF272E35, KEY_SPECIAL = 0xFF313A43, KEY_ACTION = 0xFF3A4B5D,
            INK = 0xFFE2E4E8, INK_SOFT = 0xFF80868B, INK_ON = 0xFF3C4043, CHIP = 0xFF292D35, CHIP_INK = 0xFFBDC1C6;
    /** The search field's colours (the other app's: light grey, dark words, a grey hint). */
    static final int FIELD = 0xFFB9BBC6, FIELD_INK = 0xFF303134, FIELD_HINT = 0xFF7D7E85;

    private static final class Key {
        final String label;
        final int units, action;

        Key(String label, int units, int action) {
            this.label = label;
            this.units = units;
            this.action = action;
        }
    }

    private final int kind;
    private final List<Key[]> letters = new ArrayList<>(), symbols = new ArrayList<>();
    /** Where it goes (design pixels): the panel's top left. */
    float x = 1148f, y = 660f;
    boolean showField;
    String label = "", hint = "";
    boolean secret;
    private String text = "";
    private int caret;
    private boolean caps, digits, active = true;
    /** The remote's row (-1: the suggestions) and key. */
    private int fr, fk;
    private final List<String> suggestions = new ArrayList<>();
    private Host host;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint keyP = Type.paint(32f, Type.REGULAR, INK), smallP = Type.paint(Type.CHIP_BOLD, Type.REGULAR, INK);
    private final TextPaint digitP = Type.paint(18f, Type.REGULAR, INK_SOFT), chipP = Type.paint(30f, Type.REGULAR, CHIP_INK);
    private final TextPaint labelP = Type.paint(Type.TITLE, Type.MEDIUM, Palette.TEXT1), fieldP = Type.paint(Type.HINT, Type.REGULAR, FIELD_INK);
    private final RectF r = new RectF();

    Keyboard(Context c, int kind) {
        super(c);
        this.kind = kind;
        letters.add(chars("qwertyuiop"));
        letters.add(chars("asdfghjkl,"));
        letters.add(row(new Key("", 1, SHIFT), chars("zxcvbnm."), new Key("", 1, DELETE)));
        letters.add(new Key[]{new Key("?123", 1, PAGE), new Key("", 1, CARET_LEFT), new Key("", 1, CARET_RIGHT), new Key("", 3, SPACE),
                new Key("-", 1, CHAR), new Key("_", 1, CHAR), new Key(kind == SEARCH ? "" : "Done", 2, DONE)});
        symbols.add(chars("1234567890"));
        symbols.add(chars("@#$%&-+()/"));
        symbols.add(row(chars("~*\"':;!?="), new Key("", 1, DELETE)));
        symbols.add(new Key[]{new Key("ABC", 1, PAGE), new Key("", 1, CARET_LEFT), new Key("", 1, CARET_RIGHT), new Key("", 3, SPACE),
                new Key(".", 1, CHAR), new Key(",", 1, CHAR), new Key(kind == SEARCH ? "" : "Done", 2, DONE)});
    }

    private static Key[] chars(String s) {
        Key[] k = new Key[s.length()];
        for (int i = 0; i < s.length(); i++) k[i] = new Key(String.valueOf(s.charAt(i)), 1, CHAR);
        return k;
    }

    private static Key[] row(Object... parts) {
        List<Key> out = new ArrayList<>();
        for (Object p : parts) {
            if (p instanceof Key) out.add((Key) p);
            else for (Key k : (Key[]) p) out.add(k);
        }
        return out.toArray(new Key[0]);
    }

    private List<Key[]> rows() {
        return digits ? symbols : letters;
    }

    void setHost(Host h) {
        host = h;
    }

    void setText(String t) {
        text = t == null ? "" : t;
        caret = text.length();
        invalidate();
    }

    String text() {
        return text;
    }

    /** Where the caret is in the text (for a screen that draws the field itself). */
    int caret() {
        return caret;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    boolean active() {
        return active;
    }

    /** Back on q, letters, small. */
    void reset() {
        fr = 0;
        fk = 0;
        caps = false;
        digits = false;
        invalidate();
    }

    /** The strip's suggestions (Search's), the first few. */
    void suggest(List<String> words) {
        suggestions.clear();
        if (words != null) for (String w : words) if (suggestions.size() < 6 && !w.isEmpty() && !suggestions.contains(w)) suggestions.add(w);
        if (fr < 0 && suggestions.isEmpty()) fr = 0;
        fk = fr < 0 ? Math.min(fk, suggestions.size() - 1) : fk;
        invalidate();
    }

    float width() {
        return W;
    }

    float height() {
        return H;
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    /** The unit a key starts at in its row. */
    private static int startUnit(Key[] row, int index) {
        int u = 0;
        for (int i = 0; i < index; i++) u += row[i].units;
        return u;
    }

    private static int keyAtUnit(Key[] row, int unit) {
        int u = 0;
        for (int i = 0; i < row.length; i++) {
            if (unit < u + row[i].units) return i;
            u += row[i].units;
        }
        return row.length - 1;
    }

    boolean key(int code, KeyEvent e) {
        List<Key[]> rows = rows();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (fk > 0) fk--;
                else return host != null && host.edge(this, Ui.LEFT);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int n = fr < 0 ? suggestions.size() : rows.get(fr).length;
                if (fk < n - 1) fk++;
                else return host != null && host.edge(this, Ui.RIGHT);
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                boolean up = code == KeyEvent.KEYCODE_DPAD_UP;
                int nr = fr + (up ? -1 : 1);
                if (nr < -1 || nr == -1 && suggestions.isEmpty()) return host != null && host.edge(this, Ui.UP);
                if (nr >= rows.size()) return host != null && host.edge(this, Ui.DOWN);
                if (nr == -1) {
                    fr = -1;
                    fk = 0;
                } else {
                    int mid = fr < 0 ? 0 : startUnit(rows.get(fr), fk) + (rows.get(fr)[fk].units - 1) / 2;
                    fr = nr;
                    fk = keyAtUnit(rows.get(fr), mid);
                }
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DEL:
                press(new Key("", 1, DELETE));
                return true;
            case KeyEvent.KEYCODE_SPACE:
                press(new Key("", 1, SPACE));
                return true;
            default: {
                // A real keyboard (or adb's input text): its characters as they come.
                int u = e == null ? 0 : e.getUnicodeChar();
                if (u > 0 && !Character.isISOControl(u)) {
                    insert(new String(Character.toChars(u)));
                    changed();
                    return true;
                }
                return false;
            }
        }
    }

    void ok() {
        if (fr < 0) {
            if (fk >= 0 && fk < suggestions.size()) {
                setText(suggestions.get(fk));
                changed();
            }
            return;
        }
        press(rows().get(fr)[fk]);
    }

    private void insert(String s) {
        caret = Math.max(0, Math.min(caret, text.length()));
        text = text.substring(0, caret) + s + text.substring(caret);
        caret += s.length();
    }

    private void press(Key k) {
        switch (k.action) {
            case CHAR:
                insert(caps && !digits ? k.label.toUpperCase(Locale.ROOT) : k.label);
                break;
            case SPACE:
                insert(" ");
                break;
            case DELETE:
                if (caret > 0 && !text.isEmpty()) {
                    int from = text.offsetByCodePoints(caret, -1);
                    text = text.substring(0, from) + text.substring(caret);
                    caret = from;
                }
                break;
            case SHIFT:
                caps = !caps;
                invalidate();
                return;
            case PAGE:
                digits = !digits;
                fk = Math.min(fk, rows().get(fr).length - 1);
                invalidate();
                return;
            case CARET_LEFT:
                if (caret > 0) caret = text.offsetByCodePoints(caret, -1);
                invalidate();
                return;
            case CARET_RIGHT:
                if (caret < text.length()) caret = text.offsetByCodePoints(caret, 1);
                invalidate();
                return;
            case DONE:
                if (host != null) host.done(this, text);
                return;
            default:
                return;
        }
        changed();
    }

    private void changed() {
        invalidate();
        if (host != null) host.typed(this, text);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (showField) drawField(c);
        float px = Ui.f(x), py = Ui.f(y), rad = Ui.f(12);
        // The panel: its strip, then the keys' ground.
        r.set(px, py, px + Ui.f(W), py + Ui.f(H) + rad);
        fill.setColor(PANEL);
        c.drawRoundRect(r, rad, rad, fill);
        fill.setColor(KEYS);
        c.drawRect(px, py + Ui.f(STRIP), px + Ui.f(W), py + Ui.f(H), fill);
        drawStrip(c, px, py);
        List<Key[]> rows = rows();
        float krad = Ui.f(6);
        for (int ri = 0; ri < rows.size(); ri++) {
            Key[] row = rows.get(ri);
            float ky = py + Ui.f(KEYS_Y + ri * STEP_Y);
            int u = 0;
            for (int ki = 0; ki < row.length; ki++) {
                Key k = row[ki];
                float kx = px + Ui.f(KEYS_X + u * STEP_X);
                r.set(kx, ky, kx + Ui.f(KEY_W + (k.units - 1) * STEP_X), ky + Ui.f(KEY_H));
                boolean on = active && ri == fr && ki == fk;
                int back = on ? 0xFFFFFFFF : k.action == DONE ? KEY_ACTION : k.action == CHAR || k.action == SPACE ? KEY : KEY_SPECIAL;
                if (k.action == SHIFT && caps && !on) back = 0xFF4A5560;
                fill.setColor(back);
                if (on) {
                    // Where the remote is: a white key, a little bigger.
                    float g = Ui.f(3);
                    r.inset(-g, -g);
                }
                c.drawRoundRect(r, krad, krad, fill);
                drawKey(c, k, ri, ki, r, on);
                u += k.units;
            }
        }
    }

    private void drawKey(Canvas c, Key k, int ri, int ki, RectF box, boolean on) {
        int ink = on ? INK_ON : INK;
        float cx = box.centerX(), cy = box.centerY();
        switch (k.action) {
            case SHIFT:
                Glyphs.draw(c, Glyphs.SHIFT, cx, cy, Ui.f(30), ink);
                return;
            case DELETE:
                Glyphs.draw(c, Glyphs.BACKSPACE, cx, cy, Ui.f(32), ink);
                return;
            case CARET_LEFT:
                Glyphs.draw(c, Glyphs.LEFT, cx, cy, Ui.f(28), ink);
                return;
            case CARET_RIGHT:
                Glyphs.draw(c, Glyphs.RIGHT, cx, cy, Ui.f(28), ink);
                return;
            case SPACE:
                Glyphs.draw(c, Glyphs.SPACE, cx, cy + Ui.f(6), Ui.f(30), ink);
                return;
            case DONE:
                if (k.label.isEmpty()) {
                    RoundButton.glyph(c, Dark.SEARCH, cx, cy, Ui.f(34), ink);
                    return;
                }
                break;
            default:
                break;
        }
        String l = k.action == CHAR && caps && !digits ? k.label.toUpperCase(Locale.ROOT) : k.label;
        TextPaint p = l.length() > 1 ? smallP : keyP;
        p.setColor(ink);
        c.drawText(l, cx - p.measureText(l) / 2f, Type.centred(p, cy), p);
        // The letters' digits, small at their top right corner.
        if (!digits && ri == 0 && k.action == CHAR) {
            String d = String.valueOf((ki + 1) % 10);
            digitP.setColor(on ? INK_ON : INK_SOFT);
            c.drawText(d, box.right - Ui.f(8) - digitP.measureText(d), box.top + Ui.f(20), digitP);
        }
    }

    /** The strip: suggestions as chips (the remote's lit), or the field's name. */
    private void drawStrip(Canvas c, float px, float py) {
        float cy = py + Ui.f(STRIP / 2f);
        if (suggestions.isEmpty()) {
            String s = label == null ? "" : label;
            if (s.isEmpty() || showField) return;
            chipP.setColor(CHIP_INK);
            c.drawText(Ui.fit(s, chipP, Ui.f(W - 48)), px + (Ui.f(W) - Math.min(chipP.measureText(s), Ui.f(W - 48))) / 2f,
                    Type.centred(chipP, cy), chipP);
            return;
        }
        float cx = px + Ui.f(42);
        for (int i = 0; i < suggestions.size(); i++) {
            String s = Ui.fit(suggestions.get(i), chipP, Ui.f(260));
            float w = chipP.measureText(s) + Ui.f(44);
            if (cx + w > px + Ui.f(W)) break;
            r.set(cx, cy - Ui.f(23), cx + w, cy + Ui.f(23));
            boolean on = active && fr < 0 && fk == i;
            fill.setColor(on ? 0xFFFFFFFF : CHIP);
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
            chipP.setColor(on ? INK_ON : CHIP_INK);
            c.drawText(s, cx + Ui.f(22), Type.centred(chipP, cy), chipP);
            cx += w + Ui.f(16);
        }
    }

    /** The field's name and the field over the panel (Settings' sheet). */
    private void drawField(Canvas c) {
        float fx = Ui.f(x), top = Ui.f(y - 176f);
        labelP.setColor(Palette.TEXT1);
        if (!label.isEmpty()) c.drawText(Ui.fit(label, labelP, Ui.f(W)), fx, top + Ui.f(34), labelP);
        r.set(fx, top + Ui.f(56), fx + Ui.f(W), top + Ui.f(148));
        fill.setColor(FIELD);
        c.drawRoundRect(r, Ui.f(16), Ui.f(16), fill);
        field(c, r, fieldP, text, caret, secret, hint, true);
    }

    /**
     * A field's words in a box as the other app's search field writes them: the words dark from 48 px in (the end of a
     * long address kept in sight), the hint grey when there are none, a caret where the next letter goes.
     */
    static void field(Canvas c, RectF box, TextPaint p, String text, int caret, boolean secret, String hint, boolean showCaret) {
        String shown = secret ? dots(text.length()) : text;
        int at = secret ? Math.min(caret, shown.length()) : Math.max(0, Math.min(caret, shown.length()));
        float left = box.left + Ui.f(48), room = box.width() - Ui.f(96);
        String before = shown.substring(0, at);
        // The end of a long address stays in view: drop from the front until the caret fits.
        int from = 0;
        while (from < before.length() && p.measureText(shown, from, at) > room) from++;
        String visible = shown.substring(from);
        float base = Type.centred(p, box.centerY());
        if (shown.isEmpty() && hint != null && !hint.isEmpty()) {
            p.setColor(FIELD_HINT);
            c.drawText(Ui.fit(hint, p, room), left, base, p);
        } else {
            p.setColor(FIELD_INK);
            c.drawText(Ui.fit(visible, p, room), left, base, p);
        }
        if (showCaret) {
            float cx = left + p.measureText(shown, from, at);
            CARET.setColor(FIELD_INK);
            c.drawRect(cx, box.centerY() - p.getTextSize() * 0.55f, cx + Math.max(1f, Ui.f(2)), box.centerY() + p.getTextSize() * 0.45f, CARET);
        }
    }

    private static final Paint CARET = new Paint();

    private static String dots(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('•');
        return b.toString();
    }
}
