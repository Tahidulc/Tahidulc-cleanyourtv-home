package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
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

import java.util.List;

/**
 * Settings' Live TV section: the family's own playlists, one or several, each an M3U playlist (with its guide, when it
 * has one) or an Xtream Codes login. Each shows its name, its kind and host, when it runs out (as its provider says)
 * and how many channels it gave; Use picks all of them together or just one. Add playlist (and Edit) tries it first and
 * keeps it only when it gives live channels; Refresh reads them again; Remove takes one off, Remove everything takes
 * them all, their channels and the Live TV favourites off the phone. Nothing here is synced or logged, and the password
 * stays masked.
 */
public final class LiveTvSettings {
    private LiveTvSettings() {}

    /** Under this many days left, the expiry date is shown in amber. */
    private static final int SOON_DAYS = 14;
    private static final int AMBER = 0xFFFFC93C;

    /** Whether the app opens on Live TV (on until switched off) rather than Home. */
    public static boolean openOnLive(Context c) {
        return c.getSharedPreferences("start", Context.MODE_PRIVATE).getBoolean("open_live", true);
    }

    static void setOpenOnLive(Context c, boolean on) {
        c.getSharedPreferences("start", Context.MODE_PRIVATE).edit().putBoolean("open_live", on).apply();
    }

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        Library lib = Library.get(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 8));

        col.addView(Ui.text(c, c.getString(R.string.live_settings_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.live_settings_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12));
        col.addView(body);

        // Open on Live TV: the app opens straight on it (the channel last watched); off, on Home.
        BoardSettings.row(c, col, c.getString(R.string.live82_open_live), openOnLive(c), on -> setOpenOnLive(c, on));
        TextView openLine = Ui.text(c, c.getString(R.string.live82_open_live_line), 13, Ui.DIM, false);
        openLine.setPadding(0, 0, 0, Ui.dp(c, 12));
        col.addView(openLine);

        // Use: all together, or one of them (only when there are two or more).
        TextView useLabel = Ui.text(c, c.getString(R.string.live_use), 13, Ui.DIM, true);
        useLabel.setPadding(0, 0, 0, Ui.dp(c, 6));
        col.addView(useLabel);
        HorizontalScrollView useScroll = new HorizontalScrollView(c);
        useScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout useRow = new LinearLayout(c);
        useScroll.addView(useRow);
        LinearLayout.LayoutParams usp = new LinearLayout.LayoutParams(-1, -2);
        usp.bottomMargin = Ui.dp(c, 12);
        col.addView(useScroll, usp);

        // The playlists, one card each.
        LinearLayout listBox = new LinearLayout(c);
        listBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(listBox);

        // The form, for a new playlist or one changed.
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackground(Ui.round(c, 0x14FFFFFF, 18));
        int fp = Ui.dp(c, 14);
        form.setPadding(fp, fp, fp, fp);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(c, 8);
        form.setLayoutParams(flp);
        TextView formTitle = Ui.text(c, "", 16, Ui.TEXT, true);
        form.addView(formTitle);
        EditText name = field(c, R.string.live_name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(name);

        String[] kind = {Source.M3U};
        String[] editing = {""};
        LinearLayout toggle = new LinearLayout(c);
        toggle.setPadding(0, Ui.dp(c, 10), 0, 0);
        TextView m3u = pill(c, c.getString(R.string.live_kind_m3u), true);
        TextView xt = pill(c, c.getString(R.string.live_kind_xtream), false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.rightMargin = Ui.dp(c, 8);
        toggle.addView(m3u, tp);
        toggle.addView(xt, new LinearLayout.LayoutParams(-2, -2));
        form.addView(toggle);

        LinearLayout m3uFields = new LinearLayout(c);
        m3uFields.setOrientation(LinearLayout.VERTICAL);
        EditText url = field(c, R.string.live_m3u_url, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText guide = field(c, R.string.live_guide_url, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        m3uFields.addView(url);
        m3uFields.addView(guide);
        form.addView(m3uFields);

        LinearLayout xtFields = new LinearLayout(c);
        xtFields.setOrientation(LinearLayout.VERTICAL);
        EditText server = field(c, R.string.live_server, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field(c, R.string.live_user, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = field(c, R.string.live_pass, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        xtFields.addView(server);
        xtFields.addView(user);
        xtFields.addView(pass);
        form.addView(xtFields);

        Runnable showKind = () -> {
            boolean isM3u = Source.M3U.equals(kind[0]);
            m3uFields.setVisibility(isM3u ? View.VISIBLE : View.GONE);
            xtFields.setVisibility(isM3u ? View.GONE : View.VISIBLE);
            restyle(c, m3u, isM3u);
            restyle(c, xt, !isM3u);
        };
        m3u.setOnClickListener(v -> { kind[0] = Source.M3U; showKind.run(); });
        xt.setOnClickListener(v -> { kind[0] = Source.XTREAM; showKind.run(); });

        TextView formStatus = Ui.text(c, "", 14, Ui.SOFT, false);
        formStatus.setPadding(0, Ui.dp(c, 8), 0, 0);
        LinearLayout formActions = new LinearLayout(c);
        formActions.setGravity(Gravity.CENTER_VERTICAL);
        formActions.setPadding(0, Ui.dp(c, 10), 0, 0);
        MaterialButton save = button(c, R.string.live_test_save, true);
        MaterialButton cancel = button(c, R.string.live_cancel, false);
        formActions.addView(save);
        formActions.addView(cancel);
        form.addView(formActions);
        form.addView(formStatus);
        form.setVisibility(View.GONE);
        col.addView(form);

        TextView status = Ui.text(c, "", 14, Ui.SOFT, false);
        status.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));

        LinearLayout actions = new LinearLayout(c);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Ui.dp(c, 10), 0, 0);
        MaterialButton add = button(c, R.string.live_add, true);
        MaterialButton refresh = button(c, R.string.live_refresh_now, false);
        actions.addView(add);
        actions.addView(refresh);
        col.addView(actions);
        // Remove everything on a line of its own, so the row fits a narrow phone.
        LinearLayout second = new LinearLayout(c);
        second.setPadding(0, Ui.dp(c, 6), 0, 0);
        MaterialButton removeAll = button(c, R.string.live_remove_all, false);
        second.addView(removeAll);
        col.addView(second);
        col.addView(status);
        playing(c, col);

        // The form opened for a new playlist (null) or to change one.
        java.util.function.Consumer<Sources.Entry> open = e -> {
            editing[0] = e == null ? "" : e.id;
            formTitle.setText(e == null ? c.getString(R.string.live_add) : c.getString(R.string.live_edit_title, e.name));
            Source s = e == null ? null : e.source;
            kind[0] = s == null ? Source.M3U : s.type;
            name.setText(e == null ? "" : e.name);
            url.setText(s == null ? "" : s.m3uUrl);
            guide.setText(s == null ? "" : s.guideUrl);
            server.setText(s == null ? "" : s.server);
            user.setText(s == null ? "" : s.user);
            pass.setText(s == null ? "" : s.pass);
            formStatus.setText("");
            showKind.run();
            form.setVisibility(View.VISIBLE);
            add.setVisibility(View.GONE);
            name.requestFocus();
        };
        Runnable closeForm = () -> {
            form.setVisibility(View.GONE);
            add.setVisibility(View.VISIBLE);
            editing[0] = "";
            // The login out of the fields once it isn't being typed.
            pass.setText("");
        };
        cancel.setOnClickListener(v -> closeForm.run());
        add.setOnClickListener(v -> open.accept(null));

        Runnable[] show = new Runnable[1];
        show[0] = () -> {
            List<Sources.Entry> all = Sources.list(c);
            String active = Sources.active(c);
            boolean any = !all.isEmpty();
            refresh.setVisibility(any ? View.VISIBLE : View.GONE);
            removeAll.setVisibility(any ? View.VISIBLE : View.GONE);
            useLabel.setVisibility(all.size() > 1 ? View.VISIBLE : View.GONE);
            useScroll.setVisibility(all.size() > 1 ? View.VISIBLE : View.GONE);

            // Use: All playlists, then each.
            useRow.removeAllViews();
            if (all.size() > 1) {
                boolean allOn = active.isEmpty() || Sources.get(c, active) == null;
                useRow.addView(useChip(c, c.getString(R.string.live_use_all), allOn, () -> use(c, lib, Sources.ALL, show[0])), chipParams(c));
                for (Sources.Entry e : all) {
                    useRow.addView(useChip(c, e.name, !allOn && e.id.equals(active), () -> use(c, lib, e.id, show[0])), chipParams(c));
                }
            }

            // The cards.
            listBox.removeAllViews();
            List<Sources.Entry> inUse = Sources.inUse(c);
            long now = System.currentTimeMillis();
            for (Sources.Entry e : all) {
                boolean used = false;
                for (Sources.Entry u : inUse) if (u.id.equals(e.id)) used = true;
                listBox.addView(card(c, lib, e, used, all.size() > 1, now, () -> open.accept(e), () -> removeOne(c, lib, e, show[0])));
            }

            if (lib.busy()) {
                status.setText(R.string.live_checking);
                status.setTextColor(Ui.SOFT);
            } else if (!any) {
                status.setText(R.string.live_none);
                status.setTextColor(Ui.SOFT);
            } else if (!lib.status().isEmpty() && !lib.ready()) {
                status.setText(lib.status());
                status.setTextColor(Ui.PINK);
            } else {
                String when = lib.loadedAt() > 0 ? Ui.clock(lib.loadedAt()) : "—";
                String line = inUse.size() > 1
                        ? c.getString(R.string.live_saved_all, lib.count(), inUse.size(), lib.guideCount(), when)
                        : c.getString(R.string.live_saved, inUse.isEmpty() ? "" : inUse.get(0).name, lib.count(), lib.guideCount(), when);
                // A playlist that didn't load, beside the ones that did.
                if (!lib.status().isEmpty()) line += "\n" + lib.status();
                status.setText(line);
                status.setTextColor(lib.status().isEmpty() ? Ui.SOFT : AMBER);
            }
        };
        if (Sources.list(c).isEmpty()) open.accept(null);

        save.setOnClickListener(v -> {
            Source s = new Source(kind[0], url.getText().toString(), guide.getText().toString(), server.getText().toString(),
                    user.getText().toString(), pass.getText().toString());
            if (!s.usable()) {
                formStatus.setText(Source.M3U.equals(kind[0]) ? c.getString(R.string.live_m3u_url) : c.getString(R.string.live_server)
                        + " · " + c.getString(R.string.live_user) + " · " + c.getString(R.string.live_pass));
                formStatus.setTextColor(Ui.PINK);
                return;
            }
            formStatus.setText(R.string.live_checking);
            formStatus.setTextColor(Ui.SOFT);
            save.setEnabled(false);
            lib.test(editing[0], name.getText().toString(), s, (n, error) -> {
                save.setEnabled(true);
                if (error != null) {
                    formStatus.setText(error);
                    formStatus.setTextColor(Ui.PINK);
                } else {
                    closeForm.run();
                    show[0].run();
                }
            });
        });
        refresh.setOnClickListener(v -> {
            status.setText(R.string.live_checking);
            lib.refresh((n, error) -> {
                if (error != null) {
                    status.setText(error);
                    status.setTextColor(Ui.PINK);
                } else show[0].run();
            });
        });
        removeAll.setOnClickListener(v -> new AlertDialog.Builder(c)
                .setMessage(R.string.live_remove_ask)
                .setPositiveButton(R.string.live_remove, (d, w) -> {
                    // This phone only: nothing removed elsewhere, and Playlists sync switched off here (sync.LiveSync).
                    com.gathin.tvhomephone.sync.LiveSync.removeEverything(c, lib::clear);
                    closeForm.run();
                    show[0].run();
                    android.widget.Toast.makeText(c, R.string.live_removed_here, android.widget.Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.live_cancel, null)
                .show());

        // Kept up to date while Settings is on screen.
        Runnable listener = () -> show[0].run();
        col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                lib.addListener(listener);
                lib.ensure();
                show[0].run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                lib.removeListener(listener);
            }
        });
        show[0].run();
        return col;
    }

    /** One playlist's card: its name (and whether it is in use), its kind and host, when it runs out, its channels. */
    private static View card(Context c, Library lib, Sources.Entry e, boolean used, boolean several, long now, Runnable edit,
                             Runnable remove) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(c, used ? 0x1FFFFFFF : 0x0FFFFFFF, 18));
        int p = Ui.dp(c, 14);
        card.setPadding(p, Ui.dp(c, 12), p, Ui.dp(c, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(c, 10);
        card.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(c, e.name, 16, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (several) {
            TextView tag = Ui.text(c, c.getString(used ? R.string.live_in_use : R.string.live_not_in_use), 11, used ? Ui.NAVY_INK : Ui.DIM, true);
            tag.setBackground(Ui.round(c, used ? Ui.GREEN : 0x14FFFFFF, 10));
            tag.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
            top.addView(tag);
        }
        card.addView(top);

        TextView kind = Ui.text(c, e.source.describe(), 13, Ui.SOFT, false);
        kind.setPadding(0, Ui.dp(c, 2), 0, 0);
        card.addView(kind);

        // When it runs out, as its provider says (an Xtream account, or an M3U address from one).
        Sources.Info info = Sources.info(c, e.id);
        String exp = Sources.expiry(info, now);
        TextView expiry = Ui.text(c, exp.isEmpty() ? c.getString(R.string.live_expiry_unknown) : exp, 14,
                exp.isEmpty() ? Ui.DIM : expiryColour(info, now), !exp.isEmpty());
        expiry.setPadding(0, Ui.dp(c, 6), 0, 0);
        card.addView(expiry);

        StringBuilder more = new StringBuilder();
        int n = lib.countIn(e.id);
        if (used && n > 0) more.append(c.getString(R.string.live_count, n));
        if (info != null && info.maxConnections > 0) {
            if (more.length() > 0) more.append(" · ");
            more.append(c.getString(R.string.live_connections, info.activeConnections, info.maxConnections));
        }
        if (info != null && !info.status.isEmpty() && !info.status.equalsIgnoreCase("Active")) {
            if (more.length() > 0) more.append(" · ");
            more.append(c.getString(R.string.live_account_status, info.status));
        }
        if (more.length() > 0) {
            TextView m = Ui.text(c, more.toString(), 13, Ui.SOFT, false);
            m.setPadding(0, Ui.dp(c, 2), 0, 0);
            card.addView(m);
        }

        // How it is doing (PlaylistHealth): checked about once a week, or now.
        HealthRow health = new HealthRow(c, e);
        card.addView(health.view);

        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, Ui.dp(c, 4), 0, 0);
        row.addView(small(c, R.string.live_edit, edit));
        row.addView(small(c, R.string.live_remove, remove));
        row.addView(small(c, R.string.f2_health_check, health::checkNow));
        card.addView(row);
        return card;
    }

    /** How it plays: the picture's quality (adaptive or fixed), data saver, 4K on mobile data, the rewind, night sound. */
    private static void playing(Context c, LinearLayout col) {
        TextView head = Ui.text(c, c.getString(R.string.live3_playing), 18, Ui.TEXT, true);
        head.setPadding(0, Ui.dp(c, 24), 0, Ui.dp(c, 4));
        col.addView(head);
        col.addView(label(c, c.getString(R.string.live3_quality)));
        HorizontalScrollView qs = new HorizontalScrollView(c);
        qs.setHorizontalScrollBarEnabled(false);
        LinearLayout qRow = new LinearLayout(c);
        qs.addView(qRow);
        List<TextView> chips = new java.util.ArrayList<>();
        int choice = Quality.choice(c);
        for (int lv : Quality.CHOICES) {
            TextView t = pill(c, Quality.label(lv), lv == choice);
            t.setOnClickListener(v -> {
                Quality.setChoice(c, lv);
                for (TextView x : chips) restyle(c, x, x == v);
                Playback.get(c).requality(false);
            });
            chips.add(t);
            qRow.addView(t, chipParams(c));
        }
        col.addView(qs);
        TextView note = Ui.text(c, c.getString(R.string.live3_quality_note), 13, Ui.DIM, false);
        note.setPadding(0, Ui.dp(c, 6), 0, 0);
        col.addView(note);
        col.addView(toggle(c, c.getString(R.string.live3_data_saver), Quality.saver(c), on -> {
            Quality.setSaver(c, on);
            Playback.get(c).requality(false);
        }));
        col.addView(toggle(c, c.getString(R.string.live3_ask_4k), Quality.ask4k(c), on -> {
            Quality.setAsk4k(c, on);
            Playback.get(c).requality(false);
        }));
        col.addView(label(c, c.getString(R.string.live3_rewind)));
        LinearLayout rRow = new LinearLayout(c);
        List<TextView> rs = new java.util.ArrayList<>();
        int mins = Timeshift.get(c).minutes();
        for (int m : Timeshift.CHOICES) {
            TextView t = pill(c, m == 0 ? c.getString(R.string.live3_off) : c.getString(R.string.live3_minutes, m), m == mins);
            t.setOnClickListener(v -> {
                Timeshift.get(c).setMinutes(m);
                for (TextView x : rs) restyle(c, x, x == v);
            });
            rs.add(t);
            rRow.addView(t, chipParams(c));
        }
        col.addView(rRow);
        col.addView(toggle(c, c.getString(R.string.live3_hold), LiveAlerts.holding(c), on -> LiveAlerts.setHolding(c, on)));
        if (NightSound.available()) col.addView(toggle(c, c.getString(R.string.live3_night), NightSound.on(c), on -> NightSound.set(c, on)));
        // Automatic subtitles: made on the phone from what is said on the channel, as it plays.
        col.addView(toggle(c, c.getString(R.string.captions_setting), LiveCaptions.on(c), on -> LiveCaptions.setOn(c, on)));
        TextView capLine = Ui.text(c, c.getString(R.string.captions_setting_line), 13, Ui.DIM, false);
        capLine.setPadding(0, 0, 0, Ui.dp(c, 6));
        col.addView(capLine);
    }

    interface Flip {
        void set(boolean on);
    }

    static TextView label(Context c, String words) {
        TextView t = Ui.text(c, words, 13, Ui.DIM, true);
        t.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 6));
        return t;
    }

    /** A line with its switch (On/Off chip). */
    static View toggle(Context c, String words, boolean on, Flip flip) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 52));
        row.addView(Ui.text(c, words, 15, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        boolean[] state = {on};
        TextView chip = pill(c, c.getString(on ? R.string.live3_on : R.string.live3_off), on);
        chip.setOnClickListener(v -> {
            state[0] = !state[0];
            chip.setText(state[0] ? R.string.live3_on : R.string.live3_off);
            restyle(c, chip, state[0]);
            flip.set(state[0]);
        });
        row.addView(chip);
        return row;
    }

    /** Green while it has a fortnight or more, amber under that, pink once run out. */
    static int expiryColour(Sources.Info i, long now) {
        if (i == null || i.expires == 0) return Ui.DIM;
        if (i.expires < 0) return Ui.GREEN;
        long days = (long) Math.ceil((i.expires - now) / 86_400_000.0);
        if (days < 0) return Ui.PINK;
        return days < SOON_DAYS ? AMBER : Ui.GREEN;
    }

    private static void use(Context c, Library lib, String id, Runnable show) {
        if (id.equals(Sources.active(c))) return;
        Sources.setActive(c, id);
        lib.switched();
        show.run();
    }

    private static void removeOne(Context c, Library lib, Sources.Entry e, Runnable show) {
        new AlertDialog.Builder(c)
                .setMessage(c.getString(R.string.live_remove_one_ask, e.name))
                .setPositiveButton(R.string.live_remove, (d, w) -> {
                    Sources.remove(c, e.id);
                    if (Sources.list(c).isEmpty()) lib.clear();
                    else lib.switched();
                    show.run();
                })
                .setNegativeButton(R.string.live_cancel, null)
                .show();
    }

    private static TextView useChip(Context c, String label, boolean on, Runnable click) {
        TextView t = pill(c, label, on);
        t.setOnClickListener(v -> click.run());
        return t;
    }

    /**
     * A chip on one line that keeps its 40dp: Ui.chip's single-line mode sets its height to the line's, and in a
     * row that scrolls sideways it centres the words in an endless line, off the chip.
     */
    static TextView pill(Context c, String label, boolean on) {
        TextView t = Ui.chip(c, label, on);
        t.setSingleLine(false);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxWidth(Ui.dp(c, 220));
        t.setMinHeight(Ui.dp(c, 40));
        return t;
    }

    static LinearLayout.LayoutParams chipParams(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.rightMargin = Ui.dp(c, 8);
        return p;
    }

    private static TextView small(Context c, int label, Runnable click) {
        TextView t = Ui.text(c, c.getString(label), 14, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(Ui.dp(c, 44));
        t.setPadding(0, 0, Ui.dp(c, 22), 0);
        t.setOnClickListener(v -> click.run());
        return t;
    }

    static void restyle(Context c, TextView chip, boolean on) {
        chip.setTextColor(on ? Ui.NAVY_INK : Ui.TEXT);
        chip.setBackground(Ui.touch(c, false, on ? Ui.TEXT : Ui.GLASS, 20));
    }

    static EditText field(Context c, int hint, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Ui.DIM);
        e.setTextColor(Ui.TEXT);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setBackground(Ui.round(c, Ui.GLASS, 14));
        e.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 50));
        lp.topMargin = Ui.dp(c, 10);
        e.setLayoutParams(lp);
        return e;
    }

    static MaterialButton button(Context c, int label, boolean primary) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setCornerRadius(Ui.dp(c, 22));
        if (primary) {
            b.setBackgroundTintList(ColorStateList.valueOf(Ui.TEXT));
            b.setTextColor(Ui.NAVY_INK);
        } else {
            b.setBackgroundTintList(ColorStateList.valueOf(Ui.GLASS));
            b.setTextColor(Ui.TEXT);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
        lp.rightMargin = Ui.dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }
}
