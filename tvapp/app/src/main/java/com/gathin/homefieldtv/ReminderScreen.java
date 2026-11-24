package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Reminders;

/**
 * A reminder's panel inside Homefield TV when it is the app on screen: the same panel as over other apps
 * (ReminderCard.Face), at the top right over whatever screen is up, the picture playing on under it. Left and Right go
 * between Watch, Remind again in 5 min and Dismiss; OK does the one the remote is on; Back dismisses it; it goes by itself
 * after ReminderCard.SHOW_MS.
 */
final class ReminderScreen extends Screen {
    private final Reminders.Reminder r;
    private ReminderCard.Face face;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable gone = this::close;
    private boolean closed;

    private final ReminderCard.Face.Answer answer = new ReminderCard.Face.Answer() {
        @Override public void ok() {
            close();
            // Its channel full screen (after its PIN, when it is locked).
            Channel c = Library.get(tv).byKey(r.key);
            if (c != null) tv.watch(c);
        }

        @Override public void close() {
            ReminderScreen.this.close();
        }

        @Override public void again() {
            ReminderCard.again(tv, r);
            ReminderScreen.this.close();
        }
    };

    ReminderScreen(TvActivity tv, Reminders.Reminder r) {
        super(tv);
        this.r = r;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        face = new ReminderCard.Face(tv, r, answer);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(ReminderCard.Face.W), Ui.px(ReminderCard.Face.H),
                Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.px(56);
        lp.rightMargin = Ui.px(64);
        f.addView(face, lp);
        return f;
    }

    @Override boolean overlay() {
        return true;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override void opening() {
        face.setAlpha(0f);
        face.setTranslationX(Ui.f(60));
        face.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(Fade.EASE).start();
        h.postDelayed(gone, ReminderCard.SHOW_MS);
    }

    @Override boolean key(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            face.dispatchKeyEvent(e);
            return true;
        }
        // The volume keys are the TV's; Up and Down do nothing here.
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override void ok() {
        face.press(face.focus);
    }

    @Override boolean back() {
        close();
        return true;
    }

    private void close() {
        if (closed) return;
        closed = true;
        h.removeCallbacks(gone);
        if (tv.isTop(this)) tv.pop();
    }
}
