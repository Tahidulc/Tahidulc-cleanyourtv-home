package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings' alerts section: the starred clubs (shared with the TVs: add from a search of the competitions' clubs,
 * take one off), exact alarms (so alerts start on time for kick-off), a switch per competition (with its logo), and the
 * notification switches, one per kind, grouped as the TV's Control Centre groups them; the switches kept on this phone.
 */
public final class SportsSettings {
    private SportsSettings() {}

    /** The competitions whose clubs the search looks through, and the national sides. */
    private static final String[] SEARCH = {"eng.1", "eng.2", "esp.1", "ita.1", "ger.1", "fra.1", "sco.1", "uefa.champions",
            "uefa.europa", "ned.1", "por.1", "fifa.world"};
    private static volatile List<String[]> allClubs;

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout col = Ui.column(c);
        col.addView(Ui.heading(c, c.getString(R.string.sports_alerts_title), c.getColor(R.color.sports)));
        col.addView(Ui.text(c, c.getString(R.string.sports_clubs), 17, c.getColor(R.color.text), true));
        LinearLayout list = Ui.column(c);
        col.addView(list, Ui.matchWrap());
        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            list.removeAllViews();
            for (String[] club : Clubs.describe(c)) {
                LinearLayout r = Ui.row(c);
                r.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
                android.widget.ImageView crest = Ui.image(c, club[2], 28);
                ((LinearLayout.LayoutParams) crest.getLayoutParams()).rightMargin = Ui.dp(c, 10);
                r.addView(crest);
                r.addView(Ui.text(c, club[1], 16, c.getColor(R.color.text), false), Ui.weight(1));
                TextView off = Ui.text(c, "✕", 18, c.getColor(R.color.text_soft), false);
                off.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                off.setContentDescription("Take " + club[1] + " off");
                off.setOnClickListener(v -> {
                    Set<String> s = new LinkedHashSet<>(Clubs.starred(c));
                    s.remove(club[0]);
                    Clubs.set(c, s);
                    redraw[0].run();
                });
                r.addView(off);
                list.addView(r);
            }
            if (list.getChildCount() == 0) Ui.empty(list, "No clubs starred: alerts are off for football.");
        };
        redraw[0].run();
        EditText search = new EditText(c);
        search.setHint(R.string.sports_search_hint);
        search.setSingleLine(true);
        search.setTextColor(c.getColor(R.color.text));
        search.setHintTextColor(c.getColor(R.color.text_dim));
        col.addView(search, Ui.margins(c, 0, 6, 0, 0));
        LinearLayout results = Ui.column(c);
        col.addView(results, Ui.matchWrap());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) {
                String q = e.toString().trim().toLowerCase(Locale.ROOT);
                if (q.length() < 2) { results.removeAllViews(); return; }
                App.io.execute(() -> {
                    List<String[]> found = find(q);
                    App.main.post(() -> {
                        if (!q.equals(search.getText().toString().trim().toLowerCase(Locale.ROOT))) return;
                        results.removeAllViews();
                        for (String[] t : found) {
                            LinearLayout r = Ui.row(c);
                            r.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
                            android.widget.ImageView crest = Ui.image(c, t[2], 26);
                            ((LinearLayout.LayoutParams) crest.getLayoutParams()).rightMargin = Ui.dp(c, 10);
                            r.addView(crest);
                            r.addView(Ui.text(c, t[1], 15, c.getColor(R.color.text), false), Ui.weight(1));
                            r.addView(Ui.text(c, "+", 22, c.getColor(R.color.sports), true));
                            r.setContentDescription("Star " + t[1]);
                            r.setOnClickListener(v -> {
                                Clubs.know(c, t[0], t[1], t[2]);
                                Set<String> s = new LinkedHashSet<>(Clubs.starred(c));
                                s.add(t[0]);
                                Clubs.set(c, s);
                                search.setText("");
                                results.removeAllViews();
                                redraw[0].run();
                            });
                            results.addView(r);
                        }
                    });
                });
            }
        });

        if (!Watcher.exactAllowed(c) && Build.VERSION.SDK_INT >= 31) {
            LinearLayout card = Ui.card(c, null);
            card.addView(Ui.text(c, c.getString(R.string.sports_exact_title), 16, c.getColor(R.color.text), true));
            card.addView(Ui.text(c, c.getString(R.string.sports_exact_line), 14, c.getColor(R.color.text_soft), false));
            Button allow = new Button(c);
            allow.setText(R.string.sports_exact_allow);
            allow.setOnClickListener(v -> {
                try {
                    c.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.getPackageName())));
                } catch (RuntimeException ignored) {
                    // no such screen on this phone
                }
            });
            card.addView(allow, new LinearLayout.LayoutParams(-2, -2));
            col.addView(card, Ui.margins(c, 0, 14, 0, 0));
        }

        // The competitions: one switch each, with its logo. Off holds back every alert from its games (the starred
        // clubs' included), or F1's; the reminders set by hand still come.
        col.addView(Ui.heading(c, c.getString(R.string.sports_competitions), c.getColor(R.color.text_soft)));
        col.addView(Ui.text(c, c.getString(R.string.sports_competitions_line), 13, c.getColor(R.color.text_dim), false));
        for (String[] comp : Competitions.all()) {
            LinearLayout r = Ui.row(c);
            r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
            android.widget.ImageView logo = new android.widget.ImageView(c);
            logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28));
            lp.rightMargin = Ui.dp(c, 12);
            r.addView(logo, lp);
            Ui.load(logo, Competitions.logo(comp[0]), null, null);
            r.addView(Ui.text(c, comp[1], 16, c.getColor(R.color.text), false), Ui.weight(1));
            MaterialSwitch sw = new MaterialSwitch(c);
            sw.setChecked(Competitions.on(c, comp[0]));
            sw.setContentDescription(comp[1]);
            sw.setOnCheckedChangeListener((b, on) -> Competitions.set(c, comp[0], on));
            r.addView(sw);
            r.setOnClickListener(v -> sw.toggle());
            col.addView(r);
        }

        // When a game's moments come: at once, or held back as the TV holds its cards back (a minute by default).
        col.addView(Ui.heading(c, c.getString(R.string.sports_timing), c.getColor(R.color.text_soft)));
        col.addView(Ui.text(c, c.getString(R.string.sports_timing_line), 13, c.getColor(R.color.text_dim), false));
        android.widget.RadioGroup timing = new android.widget.RadioGroup(c);
        long chosen = Kinds.delayMs(c);
        for (int i = 0; i < Kinds.DELAYS.length; i++) {
            com.google.android.material.radiobutton.MaterialRadioButton b = new com.google.android.material.radiobutton.MaterialRadioButton(c);
            b.setId(View.generateViewId());
            b.setText(Kinds.DELAY_NAMES[i]);
            b.setTextColor(c.getColor(R.color.text));
            b.setTextSize(16);
            long ms = Kinds.DELAYS[i];
            b.setChecked(ms == chosen);
            b.setOnCheckedChangeListener((v, on) -> {
                if (on) Kinds.setDelay(c, ms);
            });
            timing.addView(b);
        }
        col.addView(timing, Ui.matchWrap());

        // The scoreboard while something is watched: over the player's picture and over other apps.
        if (host.getActivity() != null) com.gathin.tvhomephone.live.BoardSettings.add(host.getActivity(), col);

        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (String g : Kinds.GROUPS) groups.put(g, new ArrayList<>());
        for (String[] k : Kinds.ALL) groups.get(k[3]).add(k);
        for (Map.Entry<String, List<String[]>> g : groups.entrySet()) {
            col.addView(Ui.heading(c, g.getKey(), c.getColor(R.color.text_soft)));
            for (String[] k : g.getValue()) {
                LinearLayout r = Ui.row(c);
                r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
                LinearLayout words = Ui.column(c);
                words.addView(Ui.text(c, k[1], 16, c.getColor(R.color.text), false));
                words.addView(Ui.text(c, k[2], 13, c.getColor(R.color.text_dim), false));
                r.addView(words, Ui.weight(1));
                MaterialSwitch sw = new MaterialSwitch(c);
                sw.setChecked(Kinds.on(c, k[0]));
                sw.setContentDescription(k[1]);
                sw.setOnCheckedChangeListener((b, on) -> Kinds.set(c, k[0], on));
                r.addView(sw);
                r.setOnClickListener(v -> sw.toggle());
                col.addView(r);
            }
        }
        return col;
    }

    /** Clubs whose names have the words searched, from the competitions' club lists (read once, then kept). */
    static List<String[]> find(String q) {
        List<String[]> clubs = allClubs;
        if (clubs == null) {
            Map<String, String[]> byId = new LinkedHashMap<>();
            for (String slug : SEARCH) for (String[] t : Espn.teams(slug)) byId.putIfAbsent(t[0], t);
            clubs = new ArrayList<>(byId.values());
            if (!clubs.isEmpty()) allClubs = clubs;
        }
        List<String[]> out = new ArrayList<>();
        for (String[] t : clubs) {
            if (t[1].toLowerCase(Locale.ROOT).contains(q)) out.add(t);
            if (out.size() >= 8) break;
        }
        return out;
    }
}
