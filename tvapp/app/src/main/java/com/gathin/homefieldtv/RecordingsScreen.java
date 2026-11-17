package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Recordings, from the menu: for now the rail and an empty list's message in the middle of the screen (a record mark,
 * "No recordings" and a grey line). A screen of its own so the menu has every section; what fills it comes later.
 * Left (or Back) opens the menu; Right or Back closes it again; Back with the menu closed goes back.
 */
final class RecordingsScreen extends Screen {
    private Rail rail;

    RecordingsScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Palette.GROUND);
        rail = new Rail(tv, Rail.RECORDINGS);
        f.addView(new Empty(tv), new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
    }

    @Override boolean key(int code, KeyEvent e) {
        if (rail.active()) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                rail.setActive(false);
                return true;
            }
            return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            rail.setActive(true);
            return true;
        }
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    @Override void ok() {
        if (!rail.active()) return;
        if (rail.focus == Rail.RECORDINGS) rail.setActive(false);
        else Rail.go(tv, rail.focus, Rail.RECORDINGS);
    }

    @Override boolean back() {
        if (rail.active()) {
            rail.setActive(false);
            return true;
        }
        return false;
    }

    /** The empty list's message, in the middle of the room right of the rail. */
    private static final class Empty extends View {
        private final RectF area = new RectF();

        Empty(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            area.set(Ui.f(Rail.W), 0, getWidth(), getHeight());
            EmptyState.draw(c, area, Dark.RECORD, 64f, "No recordings", "Programmes recorded from the TV guide will be here.");
        }
    }
}
