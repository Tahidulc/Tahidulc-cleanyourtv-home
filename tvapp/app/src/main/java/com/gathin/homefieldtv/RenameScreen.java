package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A name typed in, over the screen it was asked from (Rename channel and Rename group in the TV guide's menus): the
 * screen dimmed, a sheet of the panel's colour risen from the foot with the on-screen keyboard on it (Keyboard.FULL,
 * the field's name and the field over it, the name now in it), as Settings types its fields. Done keeps what was typed
 * (nothing at all: the playlist's name again), Back leaves it as it was.
 */
final class RenameScreen extends Screen implements Keyboard.Host {
    /** What is done with the name typed. */
    interface Named {
        void named(String text);
    }

    /** The sheet's top and the keyboard's (design pixels), as Settings' sheet. */
    static final float SHEET_TOP = 420f, KEYS_Y = 620f;

    private final String label, value;
    private final Named then;
    private Keyboard keys;

    RenameScreen(TvActivity tv, String label, String value, Named then) {
        super(tv);
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
        this.then = then;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        keys = new Keyboard(tv, Keyboard.FULL);
        keys.x = (1920f - keys.width()) / 2f;
        keys.y = KEYS_Y;
        keys.showField = true;
        keys.label = label;
        keys.setHost(this);
        keys.setText(value);
        keys.reset();
        f.addView(new Sheet(tv), new FrameLayout.LayoutParams(-1, -1));
        f.addView(keys, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    /** Over the screen it was asked from (dimmed), the live picture included. */
    @Override boolean overlay() {
        return true;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override boolean key(int code, KeyEvent e) {
        keys.key(code, e);
        // Nothing reaches the screen under it.
        return true;
    }

    @Override void ok() {
        keys.ok();
    }

    @Override public void typed(Keyboard k, String text) {}

    @Override public void done(Keyboard k, String text) {
        tv.pop();
        if (then != null) then.named(text);
    }

    @Override public boolean edge(Keyboard k, int dir) {
        return true;
    }

    /** Previews and tests: the name typed, as if from the keyboard, and Done. */
    void typeAndDone(String text) {
        keys.setText(text);
        done(keys, text);
    }

    /** The screen dimmed behind the keyboard, and a sheet of the panel's colour risen from the foot for it to sit on. */
    private static final class Sheet extends View {
        private final Paint dim = new Paint(), sheet = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Sheet(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            dim.setColor(Palette.SCRIM);
            c.drawRect(0, 0, getWidth(), getHeight(), dim);
            float rad = Ui.f(16);
            r.set(-rad, Ui.f(SHEET_TOP), getWidth() + rad, getHeight() + rad);
            sheet.setColor(Palette.PANEL);
            c.drawRoundRect(r, rad, rad, sheet);
        }
    }
}
