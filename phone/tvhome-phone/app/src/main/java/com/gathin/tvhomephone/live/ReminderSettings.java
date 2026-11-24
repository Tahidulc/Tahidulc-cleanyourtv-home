package com.gathin.tvhomephone.live;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;

import java.util.List;

/**
 * Settings' Reminders section, as Homefield TV's Other › Reminders: the reminders set (in Live TV's guide, the player or
 * on a game, here or on the family's other devices), soonest first, each with its day, time and channel, and Cancel
 * (everywhere). Kept up to date while it shows.
 */
public final class ReminderSettings {
    private ReminderSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 28), 0, Ui.dp(c, 8));
        col.addView(Ui.text(c, c.getString(R.string.ps_reminders_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.ps_reminders_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        col.addView(body);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        Runnable[] show = new Runnable[1];
        show[0] = () -> {
            list.removeAllViews();
            List<Reminders.Kept> all = Reminders.upcoming(c);
            if (all.isEmpty()) {
                TextView none = Ui.text(c, c.getString(R.string.ps_reminders_none), 13, Ui.DIM, false);
                none.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
                list.addView(none);
            }
            for (Reminders.Kept r : all) list.addView(row(c, r, () -> {
                Reminders.cancel(c, r.key);
                show[0].run();
            }));
        };
        Runnable changed = () -> com.gathin.tvhomephone.App.main.post(show[0]);
        col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                SyncStore.addListener(changed);
                show[0].run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                SyncStore.removeListener(changed);
            }
        });
        show[0].run();
        return col;
    }

    private static View row(Context c, Reminders.Kept r, Runnable cancel) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10));
        row.setBackground(Ui.round(c, 0x14FFFFFF, 16));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, r.title, 15, Ui.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(title);
        String when = Ui.day(c, r.at) + " " + Ui.clock(r.at) + (r.channel.isEmpty() || r.channel.equals(r.title) ? "" : "  ·  " + r.channel);
        words.addView(Ui.text(c, when, 13, Ui.SOFT, false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView x = LiveTvSettings.pill(c, c.getString(R.string.ps_reminders_cancel), false);
        x.setContentDescription(c.getString(R.string.ps_reminders_cancel_named, r.title));
        x.setOnClickListener(v -> cancel.run());
        row.addView(x);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(c, 8);
        row.setLayoutParams(lp);
        return row;
    }
}
