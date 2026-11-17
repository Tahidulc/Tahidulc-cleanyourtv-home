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
 * The on-screen keyboard, for the remote, in the dark look of the PIN pad: letters for Search, and a fuller one (digits,
 * capitals, the marks web addresses use) for a playlist's address and login. Its name over a dark field with an amber
 * caret, keys as dark rounded cards, the one the remote is on in amber inside a white ring (quietly lit while the remote
 * is elsewhere on the screen). A keyboard plugged into the TV, or adb's input text, types into it too. A password shows
 * as dots.
 */
final class Keyboard extends View {
    interface Host {
        void typed(Keyboard k, String text);

        void done(Keyboard k, String text);

        /** Past the keys' edge (Ui.LEFT, UP, RIGHT, DOWN); true when the screen took it. */
        boolean edge(Keyboard k, int dir);
    }

    static final int SEARCH = 0, FULL = 1;
    private static final int CHAR = 0, SPACE = 1, DELETE = 2, CLEAR = 3, CAPS = 4, DONE = 5;

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
    private final List<Key[]> rows = new ArrayList<>();
    private final int columns;
    /** Where it goes (design pixels): the field's label at y, the keys under the field. */
    float x = 262f, y = 60f;
    boolean showField = true;
    String label = "", hint = "";
    boolean secret;
    private String text = "";
    private boolean caps, active = true;
    private int fr, fk;
    private Host host;

    private final TextPaint keyP = Dark.text(30, 500, Dark.TEXT), keyOn = Dark.text(30, 700, Dark.ACCENT_INK);
    private final TextPaint smallKey = Dark.text(23, 600, Dark.SOFT), smallOn = Dark.text(23, 700, Dark.ACCENT_INK);
    private final TextPaint labelP = Dark.text(32, 600, Dark.TEXT), fieldP = Dark.text(34, 500, Dark.TEXT), hintP = Dark.text(30, 400, Dark.FAINT);
    private final Paint keyBack = Dark.fill(Dark.alpha(Dark.TEXT, 0x14)), keyLit = Dark.fill(Dark.ACCENT), keyQuiet = Dark.fill(Dark.CARD_HI);
    private final Paint ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 2.5f);
    private final Paint fieldBack = Dark.fill(Dark.CARD), fieldLine = Dark.line(Dark.alpha(Dark.TEXT, 0x33), 1.5f);
    private final Paint caret = Dark.fill(Dark.ACCENT);
    private final RectF r = new RectF();

    Keyboard(Context c, int kind) {
        super(c);
        this.kind = kind;
        if (kind == SEARCH) {
            columns = 6;
            for (String s : new String[]{"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890"}) rows.add(chars(s));
            rows.add(new Key[]{new Key("Space", 2, SPACE), new Key("Delete", 2, DELETE), new Key("Clear", 2, CLEAR)});
        } else {
            columns = 10;
            for (String s : new String[]{"1234567890", "qwertyuiop", "asdfghjkl.", "zxcvbnm-_/", ":@?=&%~+#!"}) rows.add(chars(s));
            rows.add(new Key[]{new Key("Aa", 2, CAPS), new Key("Space", 2, SPACE), new Key("Delete", 2, DELETE),
                    new Key("Clear", 2, CLEAR), new Key("Done", 2, DONE)});
        }
    }

    private static Key[] chars(String s) {
        Key[] k = new Key[s.length()];
        for (int i = 0; i < s.length(); i++) k[i] = new Key(String.valueOf(s.charAt(i)), 1, CHAR);
        return k;
    }

    void setHost(Host h) {
        host = h;
    }

    void setText(String t) {
        text = t == null ? "" : t;
        invalidate();
    }

    String text() {
        return text;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    void reset() {
        fr = 0;
        fk = 0;
        caps = false;
        invalidate();
    }

    private float keyW() {
        return kind == SEARCH ? 100f : 86f;
    }

    private float gap() {
        return kind == SEARCH ? 10f : 8f;
    }

    private static final float KEY_H = 64f, ROW_GAP = 10f;

    float keysTop() {
        return y + (showField ? 156f : 0f);
    }

    float width() {
        return columns * keyW() + (columns - 1) * gap();
    }

    float height() {
        return keysTop() - y + rows.size() * (KEY_H + ROW_GAP);
    }

    /** The unit a key starts at in its row. */
    private int startUnit(Key[] row, int index) {
        int u = 0;
        for (int i = 0; i < index; i++) u += row[i].units;
        return u;
    }

    private int keyAtUnit(Key[] row, int unit) {
        int u = 0;
        for (int i = 0; i < row.length; i++) {
            if (unit < u + row[i].units) return i;
            u += row[i].units;
        }
        return row.length - 1;
    }

    boolean key(int code, KeyEvent e) {
        Key[] row = rows.get(fr);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (fk > 0) fk--;
                else return host != null && host.edge(this, Ui.LEFT);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (fk < row.length - 1) fk++;
                else return host != null && host.edge(this, Ui.RIGHT);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int nr = fr + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (nr < 0) return host != null && host.edge(this, Ui.UP);
                if (nr >= rows.size()) return host != null && host.edge(this, Ui.DOWN);
                int mid = startUnit(row, fk) + (row[fk].units - 1) / 2;
                fr = nr;
                fk = keyAtUnit(rows.get(fr), mid);
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
                    text += new String(Character.toChars(u));
                    changed();
                    return true;
                }
                return false;
            }
        }
    }

    void ok() {
        press(rows.get(fr)[fk]);
    }

    private void press(Key k) {
        switch (k.action) {
            case CHAR:
                text += caps ? k.label.toUpperCase(Locale.ROOT) : k.label;
                break;
            case SPACE:
                text += " ";
                break;
            case DELETE:
                if (!text.isEmpty()) text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
                break;
            case CLEAR:
                text = "";
                break;
            case CAPS:
                caps = !caps;
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

    @Override protected void onDraw(Canvas c) {
        float left = Ui.f(x);
        if (showField) {
            if (!label.isEmpty()) c.drawText(label, left, Ui.f(y + 34), labelP);
            r.set(left, Ui.f(y + 52), left + Ui.f(width()), Ui.f(y + 128));
            c.drawRoundRect(r, Ui.f(16), Ui.f(16), fieldBack);
            c.drawRoundRect(r, Ui.f(16), Ui.f(16), fieldLine);
            String shown = secret ? dots(text.length()) : text;
            float room = r.width() - Ui.f(52);
            // The end of a long address stays in view.
            while (shown.length() > 1 && fieldP.measureText(shown) > room) shown = shown.substring(1);
            float base = Ui.f(y + 102);
            if (shown.isEmpty() && !hint.isEmpty()) {
                c.drawText(Ui.fit(hint, hintP, room), left + Ui.f(24), base, hintP);
            } else {
                c.drawText(shown, left + Ui.f(24), base, fieldP);
            }
            float cx = left + Ui.f(24) + (shown.isEmpty() ? 0 : fieldP.measureText(shown) + Ui.f(3));
            c.drawRoundRect(cx, Ui.f(y + 68), cx + Ui.f(3), Ui.f(y + 112), Ui.f(1.5f), Ui.f(1.5f), caret);
        }
        float top = Ui.f(keysTop()), unit = Ui.f(keyW() + gap()), rad = Ui.f(14);
        for (int ri = 0; ri < rows.size(); ri++) {
            Key[] row = rows.get(ri);
            float ky = top + ri * Ui.f(KEY_H + ROW_GAP);
            int u = 0;
            for (int ki = 0; ki < row.length; ki++) {
                Key k = row[ki];
                float kx = left + u * unit;
                r.set(kx, ky, kx + k.units * unit - Ui.f(gap()), ky + Ui.f(KEY_H));
                boolean on = ri == fr && ki == fk, lit = on && active;
                c.drawRoundRect(r, rad, rad, lit ? keyLit : on ? keyQuiet : keyBack);
                if (lit) Dark.ring(c, r, rad, ring);
                String l = k.action == CHAR && caps ? k.label.toUpperCase(Locale.ROOT) : k.label;
                if (k.action == CAPS && caps) l = "AA";
                TextPaint p = k.units > 1 ? (lit ? smallOn : smallKey) : lit ? keyOn : keyP;
                c.drawText(l, r.centerX() - p.measureText(l) / 2f, r.centerY() + p.getTextSize() * 0.34f, p);
                u += k.units;
            }
        }
    }

    private static String dots(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('•');
        return b.toString();
    }
}
