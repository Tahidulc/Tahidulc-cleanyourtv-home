package com.gathin.tvhomephone.live;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

/**
 * A playlist's health on its card in Settings: the word (Healthy, Slow, Struggling) with when it was checked, and three
 * boxes (how long its channels take to start, how many failed, how long it takes to answer); "Check now" runs it again.
 */
final class HealthRow {
    private static final int AMBER = 0xFFFFC93C;

    final LinearLayout view;
    private final Context c;
    private final Sources.Entry e;
    private final TextView word;
    private final LinearLayout boxes;
    private boolean running;

    HealthRow(Context c, Sources.Entry e) {
        this.c = c;
        this.e = e;
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(0, Ui.dp(c, 8), 0, 0);
        word = Ui.text(c, "", 13, Ui.SOFT, true);
        view.addView(word);
        boxes = new LinearLayout(c);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = Ui.dp(c, 6);
        view.addView(boxes, bp);
        fill(PlaylistHealth.read(c, e.id));
    }

    private void fill(PlaylistHealth.Result r) {
        boxes.removeAllViews();
        if (r == null) {
            word.setText(R.string.f2_health_never);
            word.setTextColor(Ui.DIM);
            boxes.setVisibility(View.GONE);
            return;
        }
        int colour = PlaylistHealth.HEALTHY.equals(r.verdict) ? Ui.GREEN : PlaylistHealth.SLOW.equals(r.verdict) ? AMBER : Ui.PINK;
        word.setText("●  " + r.verdict + " · " + c.getString(R.string.f2_health_checked, PlaylistHealth.ago(r.checkedAt, System.currentTimeMillis())));
        word.setTextColor(colour);
        boxes.setVisibility(View.VISIBLE);
        box(PlaylistHealth.seconds(r.startMs), c.getString(R.string.f2_health_to_start), true);
        box(r.sampled > 0 ? r.failed + " of " + r.sampled : "—",
                c.getString(r.sampled > 0 ? R.string.f2_health_failed : R.string.f2_health_not_tried), true);
        box(PlaylistHealth.seconds(r.answerMs), c.getString(R.string.f2_health_to_answer), false);
    }

    private void box(String value, String label, boolean gap) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(Ui.round(c, 0x0FFFFFFF, 10));
        b.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 8), Ui.dp(c, 7));
        b.setGravity(Gravity.START);
        b.addView(Ui.text(c, value, 15, Ui.TEXT, true));
        TextView l = Ui.text(c, label, 11, Ui.DIM, false);
        l.setMaxLines(2);
        b.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (gap) lp.rightMargin = Ui.dp(c, 6);
        boxes.addView(b, lp);
    }

    /** "Check now": run again (not while something plays: it would share the connection). */
    void checkNow() {
        if (running) return;
        if (PlaylistHealth.busy(c)) {
            word.setText(R.string.f2_health_busy);
            word.setTextColor(AMBER);
            return;
        }
        running = true;
        word.setText(R.string.f2_health_checking);
        word.setTextColor(Ui.SOFT);
        Context app = c.getApplicationContext();
        App.io.execute(() -> {
            PlaylistHealth.Result r;
            try {
                r = PlaylistHealth.check(app, e);
            } catch (RuntimeException x) {
                r = null;
            }
            PlaylistHealth.Result got = r;
            App.main.post(() -> {
                running = false;
                if (got == null) {
                    word.setText(R.string.f2_health_busy);
                    word.setTextColor(AMBER);
                } else {
                    fill(got);
                }
            });
        });
    }
}
