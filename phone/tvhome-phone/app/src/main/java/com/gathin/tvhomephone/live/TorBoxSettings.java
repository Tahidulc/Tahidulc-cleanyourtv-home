package com.gathin.tvhomephone.live;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;

/**
 * Settings' TorBox section: the family's own TorBox account as a source for films and series beside the playlists
 * (TorBox), on or off, before the playlists' copies or after them, and its API key (kept on this phone, never shown
 * again once saved), checked with TorBox when saved: how many films and episodes it has ready, or why not.
 */
public final class TorBoxSettings {
    private TorBoxSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 28), 0, Ui.dp(c, 8));
        col.addView(Ui.text(c, c.getString(R.string.torbox_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.torbox_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        col.addView(body);

        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.torbox_on), TorBox.wanted(c), on -> TorBox.setOn(c, on)));
        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.torbox_first), TorBox.first(c), on -> TorBox.setFirst(c, on)));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(c, 0x14FFFFFF, 18));
        int fp = Ui.dp(c, 14);
        card.setPadding(fp, fp, fp, fp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(c, 18);
        col.addView(card, clp);
        card.addView(Ui.text(c, c.getString(R.string.torbox_account), 16, Ui.TEXT, true));
        TextView status = Ui.text(c, "", 13, Ui.SOFT, false);
        status.setPadding(0, Ui.dp(c, 4), 0, 0);
        card.addView(status);
        EditText key = LiveTvSettings.field(c, R.string.torbox_key, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // A password field would write in a typewriter's letters: the app's own instead.
        key.setTypeface(android.graphics.Typeface.DEFAULT);
        card.addView(key);
        LinearLayout buttons = new LinearLayout(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(c, 12);
        MaterialButton save = LiveTvSettings.button(c, R.string.torbox_save, true);
        MaterialButton forget = LiveTvSettings.button(c, R.string.torbox_forget, false);
        buttons.addView(save);
        buttons.addView(forget);
        card.addView(buttons, blp);

        Runnable show = () -> {
            boolean has = TorBox.hasKey(c);
            status.setText(has ? R.string.torbox_saved : R.string.torbox_none);
            key.setText("");
            key.setHint(has ? c.getString(R.string.torbox_saved) : c.getString(R.string.torbox_key));
            forget.setVisibility(has ? View.VISIBLE : View.GONE);
        };
        show.run();
        save.setOnClickListener(v -> {
            String k = key.getText().toString().trim();
            if (k.isEmpty() && !TorBox.hasKey(c)) {
                key.setError(c.getString(R.string.torbox_key));
                return;
            }
            // A key left empty keeps the one saved, checked again.
            if (!k.isEmpty()) TorBox.setKey(c, k);
            show.run();
            status.setText(R.string.torbox_checking);
            save.setEnabled(false);
            new Thread(() -> {
                String words;
                try {
                    int n = TorBox.check(c);
                    words = c.getResources().getQuantityString(R.plurals.torbox_ready, n, n);
                } catch (IOException e) {
                    // TorBox's own words, never the address or the key.
                    words = e.getMessage();
                }
                String w = words;
                status.post(() -> {
                    if (!status.isAttachedToWindow()) return;
                    save.setEnabled(true);
                    status.setText(w);
                });
            }, "torbox-check").start();
        });
        forget.setOnClickListener(v -> {
            TorBox.forget(c);
            show.run();
        });
        return col;
    }
}
