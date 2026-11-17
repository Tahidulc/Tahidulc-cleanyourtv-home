package com.gathin.homefieldtv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Reminders;

/**
 * A reminder's card brought up full screen by its notification, where Android doesn't let Homefield TV show it over
 * other apps: the same card at the top right over what was on, OK to watch the channel, Back to put it away, gone by
 * itself after about 20 seconds.
 */
public final class ReminderActivity extends Activity implements ReminderCard.Face.Answer {
    private static final String KEY = "key", CHANNEL = "channel", LOGO = "logo", TITLE = "title", START = "start", END = "end";
    private final Handler h = new Handler(Looper.getMainLooper());
    private Reminders.Reminder r;

    static Intent intent(Context c, Reminders.Reminder r) {
        return new Intent(c, ReminderActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY)
                .putExtra(KEY, r.key).putExtra(CHANNEL, r.channel).putExtra(LOGO, r.logo).putExtra(TITLE, r.title)
                .putExtra(START, r.start).putExtra(END, r.end);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent i = getIntent();
        String key = i == null ? null : i.getStringExtra(KEY);
        if (key == null || key.isEmpty()) {
            finish();
            return;
        }
        r = new Reminders.Reminder(key, i.getStringExtra(CHANNEL), i.getStringExtra(LOGO), i.getStringExtra(TITLE),
                i.getLongExtra(START, 0L), i.getLongExtra(END, 0L));
        ReminderCard.Looks.ready(this);
        FrameLayout root = new FrameLayout(this);
        ReminderCard.Face face = new ReminderCard.Face(this, r, this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(ReminderCard.Face.W), Ui.px(ReminderCard.Face.H),
                Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.px(52);
        lp.rightMargin = Ui.px(56);
        root.addView(face, lp);
        setContentView(root);
        face.requestFocus();
        h.postDelayed(this::close, ReminderCard.SHOW_MS);
        long left = r.start - System.currentTimeMillis();
        if (left > 0) h.postDelayed(face::invalidate, left + 500);
    }

    @Override public void ok() {
        ReminderCard.watch(this, r.key);
        close();
    }

    @Override public void close() {
        h.removeCallbacksAndMessages(null);
        finish();
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
