package com.gathin.tvhomephone.sync;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.watch.Ui;
import com.gathin.tvhomephone.watch.WatchPrefs;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Settings' section for the TVs: the paired ones with when they last synced, Forget, Sync now; Find TVs on the home
 * network and Pair (the TV shows a code, typed in here); Add by address for a TV over the tailnet; and the bedtime the
 * Watch tab measures films and binge plans against.
 */
public final class SyncSettings {
    private SyncSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, 0, 0, Ui.dp(c, 24));
        LinearLayout list = Ui.column(c);
        LinearLayout found = Ui.column(c);
        Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            if (!host.isAdded()) return;
            fill(host, list, paint[0]);
        };
        box.addView(Ui.label(c, "YOUR TVS", Ui.SHOWS));
        TextView why = Ui.text(c, "My list, ratings, Film night, Coming up's pop-ups, reminders and starred clubs stay the "
                + "same on this phone and every paired TV.", 14, Ui.SOFT, false);
        why.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 10));
        box.addView(why);
        box.addView(list, Ui.fill());
        LinearLayout buttons = Ui.row(c);
        buttons.addView(Ui.pill(c, "Find TVs", true, v -> findTvs(host, found, paint[0])), Ui.margins(Ui.wrap(), c, 0, 8, 8, 0));
        buttons.addView(Ui.pill(c, "Sync now", false, v -> {
            Toast.makeText(c, "Syncing…", Toast.LENGTH_SHORT).show();
            Tvs.syncNow(c, paint[0]);
        }), Ui.margins(Ui.wrap(), c, 0, 8, 8, 0));
        buttons.addView(Ui.pill(c, "Add by address", false, v -> byAddress(host, paint[0])), Ui.margins(Ui.wrap(), c, 0, 8, 0, 0));
        box.addView(buttons, Ui.fill());
        box.addView(found, Ui.fill());
        box.addView(bedtime(c), Ui.margins(Ui.fill(), c, 0, 20, 0, 0));
        Runnable onChange = paint[0];
        Tvs.addListener(onChange);
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { Tvs.removeListener(onChange); }
        });
        paint[0].run();
        return box;
    }

    private static void fill(Fragment host, LinearLayout list, Runnable repaint) {
        Context c = host.requireContext();
        list.removeAllViews();
        boolean any = false;
        for (JSONObject tv : Tvs.list(c)) {
            any = true;
            LinearLayout card = Ui.row(c);
            int p = Ui.dp(c, 14);
            card.setPadding(p, p, p, p);
            card.setBackground(Ui.glass(c));
            LinearLayout words = Ui.column(c);
            words.addView(Ui.text(c, tv.optString("name"), 16, 0xFFFFFFFF, true));
            String state;
            if (tv.optString("token").isEmpty()) state = "Needs pairing again";
            else if (!tv.optString("error").isEmpty()) state = tv.optString("error") + (tv.optLong("synced") > 0 ? " · synced "
                    + DateUtils.getRelativeTimeSpanString(tv.optLong("synced")) : "");
            else if (tv.optLong("synced") > 0) state = "Synced " + DateUtils.getRelativeTimeSpanString(tv.optLong("synced"));
            else state = "Paired";
            words.addView(Ui.text(c, state + " · " + tv.optString("host"), 13, Ui.DIM, false));
            card.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            String name = tv.optString("name");
            if (tv.optString("token").isEmpty()) {
                card.addView(Ui.pill(c, "Pair", true, v -> pair(host, tv.optString("host"), tv.optInt("port", Link.PORT), repaint)));
            } else {
                card.addView(Ui.pill(c, "Forget", false, v -> new MaterialAlertDialogBuilder(c).setTitle("Forget " + name + "?")
                        .setMessage("It stops syncing with this phone. Pair again any time.")
                        .setPositiveButton("Forget", (d, w) -> Tvs.forget(c, name))
                        .setNegativeButton("Cancel", null).show()));
            }
            list.addView(card, Ui.margins(Ui.fill(), c, 0, 0, 0, 8));
        }
        if (!any) list.addView(Ui.text(c, "No TVs paired yet. At home, on the same Wi-Fi, press Find TVs.", 14, Ui.SOFT, false));
    }

    private static void findTvs(Fragment host, LinearLayout found, Runnable repaint) {
        Context c = host.requireContext();
        found.removeAllViews();
        TextView looking = Ui.text(c, "Looking for TVs on this Wi-Fi…", 14, Ui.SOFT, false);
        looking.setPadding(0, Ui.dp(c, 12), 0, 0);
        found.addView(looking);
        Set<String> paired = new HashSet<>(Tvs.names(c));
        int[] count = {0};
        Discovery.find(c, (name, ip, port) -> {
            if (!host.isAdded()) return;
            count[0]++;
            LinearLayout row = Ui.row(c);
            row.setPadding(0, Ui.dp(c, 8), 0, 0);
            row.addView(Ui.text(c, name + (paired.contains(name) ? " (paired)" : ""), 16, 0xFFFFFFFF, false),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.pill(c, paired.contains(name) ? "Pair again" : "Pair", !paired.contains(name), v -> pair(host, ip, port, repaint)));
            found.addView(row, Ui.fill());
        }, () -> {
            if (!host.isAdded()) return;
            looking.setText(count[0] == 0 ? "No TVs found. Check the phone is on the same Wi-Fi as the TVs, or use Add by address."
                    : "Found " + count[0] + (count[0] == 1 ? " TV" : " TVs") + ".");
        });
    }

    /** Pairing: the TV shows a four-digit code; it is typed in here. */
    static void pair(Fragment host, String ip, int port, Runnable repaint) {
        Context c = host.requireContext();
        Tvs.startPair(c, ip, port, (ok, err) -> {
            if (!host.isAdded()) return;
            if (err != null) {
                Toast.makeText(c, err, Toast.LENGTH_LONG).show();
                return;
            }
            EditText code = new EditText(c);
            code.setInputType(InputType.TYPE_CLASS_NUMBER);
            code.setHint("Code on the TV");
            int p = Ui.dp(c, 20);
            LinearLayout wrap = Ui.column(c);
            wrap.setPadding(p, Ui.dp(c, 8), p, 0);
            wrap.addView(code, Ui.fill());
            new MaterialAlertDialogBuilder(c).setTitle("Type the code on the TV").setView(wrap)
                    .setPositiveButton("Pair", (d, w) -> Tvs.confirmPair(c, ip, port, code.getText().toString(), (name, e) -> {
                        if (!host.isAdded()) return;
                        Toast.makeText(c, e != null ? e : "Paired with " + name, Toast.LENGTH_LONG).show();
                        repaint.run();
                    }))
                    .setNegativeButton("Cancel", null).show();
        });
    }

    /** A TV by its address (over Tailscale, or a network that hides the TVs from each other). */
    private static void byAddress(Fragment host, Runnable repaint) {
        Context c = host.requireContext();
        EditText where = new EditText(c);
        where.setHint("e.g. 192.168.1.50 or a tailnet name");
        where.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int p = Ui.dp(c, 20);
        LinearLayout wrap = Ui.column(c);
        wrap.setPadding(p, Ui.dp(c, 8), p, 0);
        wrap.addView(where, Ui.fill());
        new MaterialAlertDialogBuilder(c).setTitle("The TV's address").setView(wrap)
                .setPositiveButton("Next", (d, w) -> {
                    String ip = where.getText().toString().trim();
                    if (!ip.isEmpty()) pair(host, ip, Link.PORT, repaint);
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** The bedtime Fits tonight, the binge plan and Film night measure against (this phone's own). */
    private static View bedtime(Context c) {
        LinearLayout row = Ui.row(c);
        LinearLayout words = Ui.column(c);
        words.addView(Ui.text(c, "Bedtime", 16, 0xFFFFFFFF, true));
        words.addView(Ui.text(c, "For Fits tonight, binge plans and Film night", 13, Ui.DIM, false));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        com.google.android.material.button.MaterialButton b = Ui.pill(c, WatchPrefs.bedtimeText(c), false, null);
        b.setOnClickListener(v -> {
            int m = WatchPrefs.bedtime(c);
            new android.app.TimePickerDialog(c, (tp, h, min) -> {
                WatchPrefs.setBedtime(c, h * 60 + min);
                b.setText(WatchPrefs.bedtimeText(c));
            }, m / 60, m % 60, true).show();
        });
        row.addView(b);
        return row;
    }
}
