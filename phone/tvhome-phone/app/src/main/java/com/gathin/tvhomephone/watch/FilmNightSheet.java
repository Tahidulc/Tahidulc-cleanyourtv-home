package com.gathin.tvhomephone.watch;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhomephone.SyncStore;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Film night from a film's page: four start times (the next half hours) each with its end (amber within ten minutes
 * of bedtime or past it), the lights, the reminder and quiet pop-ups; saved as the synced "filmnight" plan, so the
 * TVs remind, dim the lights and hold their pop-ups as they do for a night planned on them.
 */
final class FilmNightSheet {
    private FilmNightSheet() {}

    /** The plan, when one is set and not over yet; null otherwise. */
    static JSONObject plan(Context c) {
        JSONObject p = SyncStore.get(c, "filmnight", "plan");
        if (p == null) return null;
        long end = p.optLong("start") + p.optInt("runtime", 120) * 60_000L;
        return end < System.currentTimeMillis() ? null : p;
    }

    /** The page's button: "Film night ▾", or the night planned for this film ("Film night 20:00 ✓"). */
    static String words(Context c, Title t) {
        JSONObject p = plan(c);
        if (p == null || p.optInt("id") != t.id) return "☾  Film night";
        return "☾  Film night " + clock(p.optLong("start")) + "  ✓";
    }

    static String clock(long ms) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(ms);
        return Plan.hm(k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE));
    }

    static void show(Activity a, Title t, JSONObject details, Runnable after) {
        int runtime = details == null ? 0 : details.optInt("runtime");
        if (runtime <= 0) runtime = 120;
        int run = runtime;
        JSONObject had = plan(a);
        boolean mine = had != null && had.optInt("id") == t.id;
        LinearLayout box = Ui.column(a);
        int pad = Ui.dp(a, 20);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);
        TextView bed = Ui.text(a, "Bed at " + WatchPrefs.bedtimeText(a) + " · " + Plan.length(run), 13, Ui.DIM, false);
        box.addView(bed);
        long[] starts = Plan.starts(System.currentTimeMillis());
        long[] chosen = {mine ? had.optLong("start") : starts.length > 1 ? starts[1] : starts[0]};
        LinearLayout times = Ui.column(a);
        Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            times.removeAllViews();
            for (long s : starts) {
                LinearLayout row = Ui.row(a);
                row.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), 0);
                row.setMinimumHeight(Ui.dp(a, 52));
                boolean on = s == chosen[0];
                GradientDrawable bg = Ui.round(on ? 0xFFFFFFFF : 0x14FFFFFF, 12, a);
                row.setBackground(bg);
                int startMin = minutes(s);
                int end = startMin + run;
                int spare = Plan.spare(end, WatchPrefs.bedtime(a));
                row.addView(Ui.text(a, Plan.hm(startMin), 18, on ? 0xFF10123A : 0xFFFFFFFF, true), new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(Ui.text(a, "ends " + Plan.hm(end) + (spare < 10 ? (spare < 0 ? " · past bed" : " · close to bed") : ""),
                        14, spare < 10 ? 0xFFFFB238 : on ? 0xFF3B3F6E : Ui.SOFT, on));
                row.setOnClickListener(v -> { chosen[0] = s; paint[0].run(); });
                times.addView(row, Ui.margins(Ui.fill(), a, 0, 8, 0, 0));
            }
        };
        paint[0].run();
        box.addView(times, Ui.fill());
        MaterialSwitch lights = toggle(a, "Dim the lights as it starts", !mine || had.optBoolean("lights", true));
        MaterialSwitch remind = toggle(a, "Remind me 10 minutes before", !mine || had.optBoolean("remind", true));
        MaterialSwitch quiet = toggle(a, "Quiet pop-ups until it ends", !mine || had.optBoolean("quiet", true));
        box.addView(lights, Ui.margins(Ui.fill(), a, 0, 12, 0, 0));
        box.addView(remind, Ui.fill());
        box.addView(quiet, Ui.fill());
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle("Film night · " + t.title).setView(box)
                .setPositiveButton(mine ? "Save" : "Set", (d, w) -> {
                    JSONObject p = new JSONObject();
                    try {
                        p.put("id", t.id).put("title", t.title).put("start", chosen[0]).put("runtime", run)
                                .put("lights", lights.isChecked()).put("remind", remind.isChecked()).put("quiet", quiet.isChecked())
                                .put("poster", t.poster == null ? "" : t.poster);
                    } catch (JSONException ignored) {
                        // plain values
                    }
                    SyncStore.put(a, "filmnight", "plan", p);
                    Toast.makeText(a, "Film night at " + clock(chosen[0]) + " on every TV", Toast.LENGTH_SHORT).show();
                    after.run();
                })
                .setNegativeButton("Close", null);
        if (mine) b.setNeutralButton("Cancel the night", (d, w) -> {
            SyncStore.delete(a, "filmnight", "plan");
            after.run();
        });
        b.show();
    }

    private static int minutes(long ms) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(ms);
        Calendar day = Calendar.getInstance();
        int base = k.get(Calendar.DAY_OF_YEAR) != day.get(Calendar.DAY_OF_YEAR) ? 24 * 60 : 0;
        return base + k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
    }

    private static MaterialSwitch toggle(Context c, String words, boolean on) {
        MaterialSwitch s = new MaterialSwitch(c);
        s.setText(words);
        s.setTextColor(0xFFFFFFFF);
        s.setChecked(on);
        s.setMinHeight(Ui.dp(c, 48));
        return s;
    }
}
