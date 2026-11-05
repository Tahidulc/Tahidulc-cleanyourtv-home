package com.gathin.tvhomephone.live;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' Subtitles section, for films and series: on for every film, the languages in order, finding them on
 * OpenSubtitles when a film has none, SDH, the size and look, and the OpenSubtitles key and account (kept on this
 * phone; the key and password are never shown again once saved).
 */
public final class SubtitleSettings {
    private SubtitleSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 28), 0, Ui.dp(c, 8));
        col.addView(Ui.text(c, c.getString(R.string.subs_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.subs_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        col.addView(body);

        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.subs_on), Subtitles.on(c), on -> Subtitles.setOn(c, on)));
        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.subs_auto), Subtitles.autoFind(c), on -> Subtitles.setAutoFind(c, on)));
        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.subs_hi), Subtitles.hearingImpaired(c), on -> Subtitles.setHearingImpaired(c, on)));

        // The languages, in order, with a Change chip.
        col.addView(LiveTvSettings.label(c, c.getString(R.string.subs_languages)));
        LinearLayout langRow = new LinearLayout(c);
        langRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView langs = Ui.text(c, "", 15, Ui.TEXT, false);
        langRow.addView(langs, new LinearLayout.LayoutParams(0, -2, 1));
        TextView change = LiveTvSettings.pill(c, c.getString(R.string.subs_change), false);
        langRow.addView(change);
        col.addView(langRow);
        Runnable showLangs = () -> langs.setText(names(Subtitles.languages(c)));
        showLangs.run();
        change.setOnClickListener(v -> pickLanguages(c, showLangs));

        col.addView(LiveTvSettings.label(c, c.getString(R.string.subs_size)));
        col.addView(choices(c, Subtitles.SIZE_NAMES, Subtitles.size(c), i -> Subtitles.setSize(c, i)));
        col.addView(LiveTvSettings.label(c, c.getString(R.string.subs_look)));
        col.addView(choices(c, Subtitles.LOOK_NAMES, Subtitles.look(c), i -> Subtitles.setLook(c, i)));

        // OpenSubtitles: what's set up, and the form.
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(c, 0x14FFFFFF, 18));
        int fp = Ui.dp(c, 14);
        card.setPadding(fp, fp, fp, fp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(c, 18);
        col.addView(card, clp);
        card.addView(Ui.text(c, c.getString(R.string.subs_os), 16, Ui.TEXT, true));
        TextView status = Ui.text(c, "", 13, Ui.SOFT, false);
        status.setPadding(0, Ui.dp(c, 4), 0, 0);
        card.addView(status);
        EditText key = LiveTvSettings.field(c, R.string.subs_key, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText user = LiveTvSettings.field(c, R.string.subs_user, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText pass = LiveTvSettings.field(c, R.string.subs_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // Password fields would write in a typewriter's letters: the app's own instead.
        for (EditText e : new EditText[]{key, user, pass}) e.setTypeface(android.graphics.Typeface.DEFAULT);
        card.addView(key);
        card.addView(user);
        card.addView(pass);
        LinearLayout buttons = new LinearLayout(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(c, 12);
        MaterialButton save = LiveTvSettings.button(c, R.string.subs_save, true);
        MaterialButton forget = LiveTvSettings.button(c, R.string.subs_forget, false);
        buttons.addView(save);
        buttons.addView(forget);
        card.addView(buttons, blp);

        Runnable show = () -> {
            boolean has = OpenSubtitles.hasKey(c);
            String u = OpenSubtitles.user(c);
            status.setText(!has ? c.getString(R.string.subs_os_none)
                    : u.isEmpty() ? c.getString(R.string.subs_os_key_saved) : c.getString(R.string.subs_os_signed_in, u));
            key.setText("");
            pass.setText("");
            user.setText(u);
            key.setHint(has ? c.getString(R.string.subs_os_key_saved) : c.getString(R.string.subs_key));
            forget.setVisibility(has || !u.isEmpty() ? View.VISIBLE : View.GONE);
        };
        show.run();
        save.setOnClickListener(v -> {
            String k = key.getText().toString().trim(), u = user.getText().toString().trim(), p = pass.getText().toString();
            if (k.isEmpty() && !OpenSubtitles.hasKey(c)) {
                key.setError(c.getString(R.string.subs_key));
                return;
            }
            // A key left empty keeps the one saved; a name with no password keeps the saved password.
            OpenSubtitles.setAccount(c, k.isEmpty() ? null : k, u, u.isEmpty() ? "" : p.isEmpty() ? null : p);
            show.run();
            status.setText(R.string.subs_checking);
            save.setEnabled(false);
            new Thread(() -> {
                String words;
                try {
                    int daily = OpenSubtitles.check(c);
                    String base = OpenSubtitles.user(c).isEmpty() ? c.getString(R.string.subs_os_key_saved)
                            : c.getString(R.string.subs_os_signed_in, OpenSubtitles.user(c));
                    words = daily > 0 ? c.getString(R.string.subs_os_daily, base, daily) : base;
                } catch (java.io.IOException e) {
                    words = e.getMessage();
                }
                String w = words;
                status.post(() -> {
                    if (!status.isAttachedToWindow()) return;
                    save.setEnabled(true);
                    status.setText(w);
                });
            }, "opensubtitles-check").start();
        });
        forget.setOnClickListener(v -> {
            OpenSubtitles.forget(c);
            show.run();
        });
        return col;
    }

    private interface Pick {
        void set(int i);
    }

    /** One row of chips, one of them chosen. */
    private static View choices(Context c, String[] names, int chosen, Pick pick) {
        HorizontalScrollView s = new HorizontalScrollView(c);
        s.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        s.addView(row);
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            int at = i;
            TextView t = LiveTvSettings.pill(c, names[i], i == chosen);
            t.setOnClickListener(v -> {
                pick.set(at);
                for (TextView x : chips) LiveTvSettings.restyle(c, x, x == v);
            });
            chips.add(t);
            row.addView(t, LiveTvSettings.chipParams(c));
        }
        return s;
    }

    private static String names(List<String> codes) {
        List<String> out = new ArrayList<>();
        for (String code : codes) out.add(Subtitles.languageName(code));
        return String.join(", ", out);
    }

    /** The languages ticked, in the order ticked (those already chosen keep their places). */
    private static void pickLanguages(Context c, Runnable after) {
        List<String> order = new ArrayList<>(Subtitles.languages(c));
        String[] names = new String[Subtitles.CODES.length];
        boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = Subtitles.languageName(Subtitles.CODES[i]);
            on[i] = order.contains(Subtitles.CODES[i]);
        }
        new AlertDialog.Builder(c).setTitle(R.string.subs_pick_languages)
                .setMultiChoiceItems(names, on, (d, w, checked) -> {
                    String code = Subtitles.CODES[w];
                    order.remove(code);
                    if (checked) order.add(code);
                })
                .setPositiveButton(R.string.subs_done, (d, w) -> {
                    if (order.isEmpty()) order.add("en");
                    Subtitles.setLanguages(c, order);
                    after.run();
                })
                .setNegativeButton(R.string.live_cancel, null)
                .show();
    }
}
