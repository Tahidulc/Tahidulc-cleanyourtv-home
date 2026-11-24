package com.gathin.tvhomephone.live;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;

import java.util.List;

/**
 * Settings' Channels &amp; categories section, Homefield TV's on the phone: One row per channel (merge qualities), on as it
 * comes; Hidden channels, Manage categories and Rename channels, each opening its sheet with how many there are; Channels
 * sorting and Categories sorting. Kept up to date while it is on screen.
 */
public final class ChannelSettings {
    private ChannelSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        Library lib = Library.get(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 28), 0, Ui.dp(c, 8));
        col.addView(Ui.text(c, c.getString(R.string.ps_channels_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.ps_channels_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        col.addView(body);

        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.ps_merge), ChannelEdits.merging(c), on -> ChannelEdits.setMerging(c, on)));
        TextView mergeLine = Ui.text(c, c.getString(R.string.ps_merge_line), 13, Ui.DIM, false);
        mergeLine.setPadding(0, 0, 0, Ui.dp(c, 8));
        col.addView(mergeLine);

        TextView[] hidden = new TextView[1], cats = new TextView[1], renamed = new TextView[1];
        col.addView(link(c, c.getString(R.string.ps_hidden), hidden, () -> Sheets.hidden(c, lib)));
        col.addView(link(c, c.getString(R.string.ps_categories), cats, () -> Sheets.categories(c, lib)));
        col.addView(link(c, c.getString(R.string.ps_rename), renamed, () -> EditSheets.renameChannels(c, lib)));

        col.addView(LiveTvSettings.label(c, c.getString(R.string.ps_sorting)));
        col.addView(LiveTvSettings.choices(c, ChannelEdits.SORT_NAMES, ChannelEdits.sort(c), i -> ChannelEdits.setSort(c, i)));
        TextView sortLine = Ui.text(c, c.getString(R.string.ps_sorting_line), 13, Ui.DIM, false);
        sortLine.setPadding(0, Ui.dp(c, 6), 0, 0);
        col.addView(sortLine);

        col.addView(LiveTvSettings.label(c, c.getString(R.string.ps_cat_sorting)));
        col.addView(LiveTvSettings.choices(c, new int[]{R.string.ps_cat_sort_playlist, R.string.ps_cat_sort_own}, Groups.ordered(c) ? 1 : 0,
                i -> Groups.setOrdered(c, i == 1)));
        TextView note = Ui.text(c, c.getString(R.string.ps_channels_note), 13, Ui.DIM, false);
        note.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
        col.addView(note);

        Runnable show = () -> {
            int h = hiddenLines(c, lib);
            hidden[0].setText(h == 0 ? c.getString(R.string.ps_none) : c.getResources().getQuantityString(R.plurals.ps_channels_count, h, h));
            List<String> all = lib.groups();
            int off = 0;
            java.util.Set<String> gone = Groups.hidden(c);
            for (String g : all) if (gone.contains(g)) off++;
            cats[0].setText(all.isEmpty() ? "" : off > 0 ? c.getString(R.string.ps_categories_some_hidden, all.size() - off, off)
                    : c.getString(R.string.ps_categories_shown, all.size()));
            cats[0].setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
            int r = lib.renamedChannels().size();
            renamed[0].setText(r == 0 ? c.getString(R.string.ps_rename_none) : c.getString(R.string.ps_rename_count, r));
        };
        col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                lib.addListener(show);
                show.run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                lib.removeListener(show);
            }
        });
        show.run();
        return col;
    }

    /** Hidden channels' lines as the sheet lists them (a channel hidden whole in several qualities once). */
    private static int hiddenLines(Context c, Library lib) {
        java.util.Map<String, Channel> found = lib.hiddenChannels();
        java.util.Set<String> lines = new java.util.HashSet<>();
        for (String k : Hidden.keys(c)) {
            Channel ch = found.get(k);
            List<String> fam = ch == null ? null : lib.familyKeys(ch.key);
            lines.add(fam == null || fam.size() < 2 ? k : java.util.Collections.min(fam));
        }
        return lines.size();
    }

    /** A line that opens a sheet: its name, what it holds under it (value), a chevron. */
    private static View link(Context c, String title, TextView[] value, Runnable open) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 60));
        row.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        row.setBackground(Ui.touch(c, false, 0, 14));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(c, title, 15, Ui.TEXT, false));
        value[0] = Ui.text(c, "", 13, Ui.DIM, false);
        words.addView(value[0]);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chevron = Ui.text(c, "›", 24, Ui.DIM, false);
        chevron.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), Ui.dp(c, 2));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron);
        row.setContentDescription(title);
        row.setOnClickListener(v -> open.run());
        return row;
    }
}
