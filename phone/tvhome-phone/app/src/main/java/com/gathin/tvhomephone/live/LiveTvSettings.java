package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

/**
 * Settings' Live TV section: the family's own source, an M3U playlist (with its guide, when it has one) or an Xtream
 * Codes login. Test and save tries it first and keeps it only when it gives live channels; Refresh reads it again;
 * Remove takes it, its channels and the Live TV favourites off the phone. Nothing here is synced or logged, and the
 * password stays masked.
 */
public final class LiveTvSettings {
    private LiveTvSettings() {}

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

        Source had = Source.load(c);
        String[] kind = {had != null ? had.type : Source.M3U};

        LinearLayout toggle = new LinearLayout(c);
        TextView m3u = Ui.chip(c, c.getString(R.string.live_kind_m3u), Source.M3U.equals(kind[0]));
        TextView xt = Ui.chip(c, c.getString(R.string.live_kind_xtream), Source.XTREAM.equals(kind[0]));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.rightMargin = Ui.dp(c, 8);
        toggle.addView(m3u, tp);
        toggle.addView(xt, new LinearLayout.LayoutParams(-2, -2));
        col.addView(toggle);

        LinearLayout m3uFields = new LinearLayout(c);
        m3uFields.setOrientation(LinearLayout.VERTICAL);
        EditText url = field(c, R.string.live_m3u_url, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText guide = field(c, R.string.live_guide_url, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        m3uFields.addView(url);
        m3uFields.addView(guide);
        col.addView(m3uFields);

        LinearLayout xtFields = new LinearLayout(c);
        xtFields.setOrientation(LinearLayout.VERTICAL);
        EditText server = field(c, R.string.live_server, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field(c, R.string.live_user, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = field(c, R.string.live_pass, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        xtFields.addView(server);
        xtFields.addView(user);
        xtFields.addView(pass);
        col.addView(xtFields);

        if (had != null) {
            url.setText(had.m3uUrl);
            guide.setText(had.guideUrl);
            server.setText(had.server);
            user.setText(had.user);
            pass.setText(had.pass);
        }

        Runnable showKind = () -> {
            boolean isM3u = Source.M3U.equals(kind[0]);
            m3uFields.setVisibility(isM3u ? View.VISIBLE : View.GONE);
            xtFields.setVisibility(isM3u ? View.GONE : View.VISIBLE);
            restyle(c, m3u, isM3u);
            restyle(c, xt, !isM3u);
        };
        m3u.setOnClickListener(v -> { kind[0] = Source.M3U; showKind.run(); });
        xt.setOnClickListener(v -> { kind[0] = Source.XTREAM; showKind.run(); });
        showKind.run();

        TextView status = Ui.text(c, "", 14, Ui.SOFT, false);
        status.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));

        LinearLayout actions = new LinearLayout(c);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Ui.dp(c, 8), 0, 0);
        MaterialButton save = button(c, R.string.live_test_save, true);
        MaterialButton refresh = button(c, R.string.live_refresh_now, false);
        MaterialButton remove = button(c, R.string.live_remove, false);
        actions.addView(save);
        actions.addView(refresh);
        col.addView(actions);
        // Remove on a line of its own, so the row fits a narrow phone.
        LinearLayout second = new LinearLayout(c);
        second.setPadding(0, Ui.dp(c, 6), 0, 0);
        second.addView(remove);
        col.addView(second);
        col.addView(status);

        Runnable show = () -> {
            Source s = Source.load(c);
            refresh.setVisibility(s == null ? View.GONE : View.VISIBLE);
            remove.setVisibility(s == null ? View.GONE : View.VISIBLE);
            if (lib.busy()) {
                status.setText(R.string.live_checking);
                status.setTextColor(Ui.SOFT);
            } else if (s == null) {
                status.setText(R.string.live_none);
                status.setTextColor(Ui.SOFT);
            } else if (!lib.status().isEmpty() && !lib.ready()) {
                status.setText(lib.status());
                status.setTextColor(Ui.PINK);
            } else {
                status.setText(c.getString(R.string.live_saved, s.describe(), lib.count(), lib.guideCount(),
                        lib.loadedAt() > 0 ? Ui.clock(lib.loadedAt()) : "—"));
                status.setTextColor(Ui.SOFT);
            }
        };
        save.setOnClickListener(v -> {
            Source s = new Source(kind[0], url.getText().toString(), guide.getText().toString(), server.getText().toString(),
                    user.getText().toString(), pass.getText().toString());
            if (!s.usable()) {
                status.setText(Source.M3U.equals(kind[0]) ? c.getString(R.string.live_m3u_url) : c.getString(R.string.live_server)
                        + " · " + c.getString(R.string.live_user) + " · " + c.getString(R.string.live_pass));
                status.setTextColor(Ui.PINK);
                return;
            }
            status.setText(R.string.live_checking);
            status.setTextColor(Ui.SOFT);
            save.setEnabled(false);
            lib.test(s, (n, error) -> {
                save.setEnabled(true);
                if (error != null) {
                    status.setText(error);
                    status.setTextColor(Ui.PINK);
                } else show.run();
            });
        });
        refresh.setOnClickListener(v -> {
            status.setText(R.string.live_checking);
            lib.refresh((n, error) -> {
                if (error != null) {
                    status.setText(error);
                    status.setTextColor(Ui.PINK);
                } else show.run();
            });
        });
        remove.setOnClickListener(v -> new AlertDialog.Builder(c)
                .setMessage(R.string.live_remove_ask)
                .setPositiveButton(R.string.live_remove, (d, w) -> {
                    lib.clear();
                    url.setText("");
                    guide.setText("");
                    server.setText("");
                    user.setText("");
                    pass.setText("");
                    show.run();
                })
                .setNegativeButton(R.string.live_cancel, null)
                .show());

        // Kept up to date while Settings is on screen.
        Runnable listener = show;
        col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                lib.addListener(listener);
                lib.ensure();
                show.run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                lib.removeListener(listener);
            }
        });
        show.run();
        return col;
    }

    private static void restyle(Context c, TextView chip, boolean on) {
        chip.setTextColor(on ? Ui.NAVY_INK : Ui.TEXT);
        chip.setBackground(Ui.touch(c, false, on ? Ui.TEXT : Ui.GLASS, 20));
    }

    private static EditText field(Context c, int hint, int type) {
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

    private static MaterialButton button(Context c, int label, boolean primary) {
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
