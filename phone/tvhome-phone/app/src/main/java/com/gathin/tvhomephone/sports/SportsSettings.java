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
import com.gathin.tvhomephone.live.FanFeed;
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

        // The fans' posts about these clubs on Home: on unless switched off here.
        LinearLayout fans = Ui.row(c);
        fans.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 4));
        LinearLayout fansWords = Ui.column(c);
        fansWords.addView(Ui.text(c, c.getString(R.string.fans_home_on), 16, c.getColor(R.color.text), false));
        TextView fansLine = Ui.text(c, c.getString(R.string.fans_home_line), 13, c.getColor(R.color.text_dim), false);
        fansLine.setPadding(0, Ui.dp(c, 2), Ui.dp(c, 12), 0);
        fansWords.addView(fansLine);
        fans.addView(fansWords, Ui.weight(1));
        MaterialSwitch fansSwitch = new MaterialSwitch(c);
        fansSwitch.setChecked(FanFeed.on(c));
        fansSwitch.setContentDescription(c.getString(R.string.fans_home_on));
        fansSwitch.setOnCheckedChangeListener((b, on) -> FanFeed.setOn(c, on));
        fans.addView(fansSwitch);
        fans.setOnClickListener(v -> fansSwitch.toggle());
        col.addView(fans);

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

        // The TV's pop-ups over apps, on the phone: on or off, which kinds, Android's permission, one to try.
        popups(c, col);

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

        // Each of them, and the other notifications, to try now.
        tests(c, col);
        return col;
    }

    /**
     * Settings' "Test notifications": the alerts (all in turn, or one kind; the line-ups') and the other notifications and
     * pop-ups over apps, as they really come (TestAlerts), on a made-up game of the starred clubs. What shows over the
     * picture is tried in the player's options.
     */
    private static void tests(Context c, LinearLayout col) {
        col.addView(Ui.heading(c, c.getString(R.string.live4_tests), c.getColor(R.color.text_soft)));
        col.addView(Ui.text(c, c.getString(R.string.live7_settings_line), 13, c.getColor(R.color.text_dim), false));
        java.util.function.IntConsumer note = res -> android.widget.Toast.makeText(c, res, android.widget.Toast.LENGTH_SHORT).show();
        LinearLayout alerts = testRow(c, col, c.getString(R.string.live7_settings_alerts));
        testChip(c, alerts, c.getString(R.string.live4_tests_all), () -> TestAlerts.play(c, null, null));
        for (String[] k : TestAlerts.kinds()) testChip(c, alerts, k[1], () -> TestAlerts.play(c, null, k[0]));
        testChip(c, alerts, c.getString(R.string.live7_lineups_alert), () -> {
            note.accept(R.string.live7_looking);
            TestAlerts.lineups(c, null, note);
        });
        LinearLayout others = testRow(c, col, c.getString(R.string.live7_settings_other));
        for (String id : TestAlerts.OTHERS) testChip(c, others, c.getString(TestAlerts.label(id)), () -> TestAlerts.other(c, null, id, note));
    }

    /** A labelled row of chips that scrolls sideways. */
    private static LinearLayout testRow(Context c, LinearLayout col, String label) {
        TextView g = Ui.text(c, label.toUpperCase(Locale.UK), 11, c.getColor(R.color.text_soft), true);
        g.setLetterSpacing(0.1f);
        g.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
        col.addView(g);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(c);
        hs.addView(chips);
        col.addView(hs, Ui.matchWrap());
        return chips;
    }

    /** A chip that runs a test when tapped. */
    private static void testChip(Context c, LinearLayout row, String label, Runnable r) {
        TextView chip = Ui.text(c, label, 14, c.getColor(R.color.text), true);
        chip.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        chip.setBackground(Ui.fill(c, 0x1FFFFFFF, 18));
        chip.setContentDescription(c.getString(R.string.live7_try, label));
        chip.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 8);
        row.addView(chip, lp);
    }

    /** Settings' "Pop-ups over apps". */
    private static void popups(Context c, LinearLayout col) {
        col.addView(Ui.heading(c, c.getString(R.string.pop_title), c.getColor(R.color.text_soft)));
        col.addView(Ui.text(c, c.getString(R.string.pop_line), 13, c.getColor(R.color.text_dim), false));
        LinearLayout r = Ui.row(c);
        r.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        r.addView(Ui.text(c, c.getString(R.string.pop_on), 16, c.getColor(R.color.text), false), Ui.weight(1));
        MaterialSwitch sw = new MaterialSwitch(c);
        sw.setChecked(Popups.on(c));
        sw.setContentDescription(c.getString(R.string.pop_on));
        sw.setOnCheckedChangeListener((b, on) -> {
            Popups.setOn(c, on);
            if (on && !Popups.allowed(c)) open(c, Popups.permission(c));
        });
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        col.addView(r);
        if (!Popups.allowed(c)) {
            LinearLayout card = Ui.card(c, null);
            card.addView(Ui.text(c, c.getString(R.string.pop_allow), 14, c.getColor(R.color.text), false));
            Button allow = new Button(c);
            allow.setText(R.string.pop_allow_button);
            allow.setOnClickListener(v -> open(c, Popups.permission(c)));
            card.addView(allow, new LinearLayout.LayoutParams(-2, -2));
            col.addView(card, Ui.margins(c, 0, 8, 0, 4));
        }
        // Which kinds pop up: a chip each, by the groups the notifications use.
        col.addView(Ui.text(c, c.getString(R.string.pop_which), 15, c.getColor(R.color.text), true), Ui.margins(c, 0, 10, 0, 0));
        col.addView(Ui.text(c, c.getString(R.string.pop_which_line), 13, c.getColor(R.color.text_dim), false));
        for (String group : Kinds.GROUPS) {
            TextView g = Ui.text(c, group.toUpperCase(Locale.UK), 11, c.getColor(R.color.text_soft), true);
            g.setLetterSpacing(0.1f);
            g.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
            col.addView(g);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = Ui.row(c);
            hs.addView(chips);
            for (String[] k : Kinds.ALL) {
                if (!group.equals(k[3])) continue;
                TextView chip = Ui.text(c, k[1], 14, 0, true);
                chip.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
                Runnable paint = () -> {
                    boolean on = Popups.kind(c, k[0]);
                    chip.setBackground(Ui.fill(c, on ? 0xFFFFFFFF : 0x1FFFFFFF, 18));
                    chip.setTextColor(on ? 0xFF0A0D33 : c.getColor(R.color.text_dim));
                    chip.setContentDescription(k[1] + (on ? ", pops up" : ", does not pop up"));
                };
                paint.run();
                chip.setOnClickListener(v -> {
                    Popups.setKind(c, k[0], !Popups.kind(c, k[0]));
                    paint.run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.rightMargin = Ui.dp(c, 8);
                chips.addView(chip, lp);
            }
            col.addView(hs, Ui.matchWrap());
        }
        Button tryOne = new Button(c);
        tryOne.setText(R.string.pop_try);
        tryOne.setOnClickListener(v -> {
            if (!Popups.allowed(c)) {
                open(c, Popups.permission(c));
                return;
            }
            Events.Alert demo = new Events.Alert("news", "demo", "This is a pop-up", "Swipe it away left, right or up, or tap it.", null);
            demo.channel = "Homefield";
            App.io.execute(() -> Popups.show(c, demo));
        });
        col.addView(tryOne, Ui.margins(c, 0, 12, 0, 0));
    }

    private static void open(Context c, Intent i) {
        try {
            c.startActivity(i);
        } catch (RuntimeException ignored) {
            // no such screen on this phone
        }
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
