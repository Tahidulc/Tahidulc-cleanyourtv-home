package com.gathin.tvhomephone.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.glass.Wide;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings: one scrolling page of each part's own section (the TVs and sync, the alerts and clubs, Live TV's playlist,
 * the posters' shape, the films' subtitles), then the app's own updates (its version, and the newer one the TVs have).
 * On a tablet with the room (Wide.twoColumns), the list of sections and the chosen one side by side: the list down the
 * left, the section's page beside it in a column that reads well; the one chosen last opens again.
 */
public class SettingsFragment extends Fragment {
    /** Set before Settings opens from a playlist's notification: scrolled down to Live TV's playlists once. */
    public static boolean toPlaylists;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        if (Wide.panes(requireContext())) return panes();
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        col.setPadding(pad, pad, pad, pad);
        // A tablet held upright: the one page in a column no wider than reads well, in the middle.
        if (Wide.tablet(requireContext())) scroll.addView(centred(requireContext(), col, pad));
        else scroll.addView(col);
        // The matchday intro first: its own video is the thing most often changed here.
        col.addView(com.gathin.tvhomephone.live.IntroSettings.build(requireContext()));
        col.addView(com.gathin.tvhomephone.sync.SyncSettings.build(this, col));
        col.addView(com.gathin.tvhomephone.sports.SportsSettings.build(this, col));
        android.view.View live = com.gathin.tvhomephone.live.LiveTvSettings.build(this, col);
        col.addView(live);
        // Films and TV shows: portrait or landscape posters on Home and Watch.
        col.addView(com.gathin.tvhomephone.live.PosterSettings.build(this, col));
        // The family's own TorBox files, a source for films and series beside the playlists.
        col.addView(com.gathin.tvhomephone.live.TorBoxSettings.build(this, col));
        col.addView(com.gathin.tvhomephone.live.SubtitleSettings.build(this, col));
        if (toPlaylists) {
            toPlaylists = false;
            scroll.post(() -> scroll.smoothScrollTo(0, live.getTop()));
        }
        // Last, in place of the version line: this version and the newer one the TVs have, to update to here.
        col.addView(com.gathin.tvhomephone.update.UpdateSettings.build(this));
        return scroll;
    }

    // ---- a tablet: the list and the page side by side ---------------------------------------------------------------

    /** The sections in the list's order: {key, name, Icons glyph}; LIVE is where a playlist's notification opens. */
    static final String INTRO = "intro", TVS = "tvs", SPORTS = "sports", LIVE = "live", POSTERS = "posters", TORBOX = "torbox",
            SUBTITLES = "subtitles", UPDATES = "updates";
    static final String[] KEYS = {INTRO, TVS, SPORTS, LIVE, POSTERS, TORBOX, SUBTITLES, UPDATES};
    private static final String PREFS = "settings_ui", CHOSEN = "section";

    private final List<View> pages = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private ScrollView pageScroll;
    private int chosen;

    /**
     * A column in the middle of what it is given, as wide as it may be (Wide.TEXT_MAX_DP) with at least least either
     * side: the room either side worked out as it is measured, so the first frame has it right.
     */
    static FrameLayout centred(Context c, View column, int least) {
        int most = Glass.dp(c, Wide.TEXT_MAX_DP);
        FrameLayout centre = new FrameLayout(c) {
            @Override protected void onMeasure(int ws, int hs) {
                // The column's own padding counts towards the room either side.
                int side = Math.max(0, Wide.side(MeasureSpec.getSize(ws), most, least) - column.getPaddingLeft());
                if (getPaddingLeft() != side) setPadding(side, 0, side, 0);
                super.onMeasure(ws, hs);
            }
        };
        centre.addView(column, new FrameLayout.LayoutParams(-1, -2));
        return centre;
    }

    private View panes() {
        Context c = requireContext();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.HORIZONTAL);
        boolean roomy = getResources().getConfiguration().screenWidthDp >= 1100;

        // The list, down the left, on a card of its own: the page's name, then a row for each section.
        ScrollView listScroll = new ScrollView(c);
        listScroll.setVerticalScrollBarEnabled(false);
        GradientDrawable card = new GradientDrawable();
        card.setColor(c.getColor(R.color.ground_deep));
        card.setCornerRadius(Glass.dp(c, 24));
        card.setStroke(Math.max(1, Glass.dp(c, 1)), 0x1AFFFFFF);
        listScroll.setBackground(card);
        listScroll.setClipToOutline(true);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int p = Glass.dp(c, 16);
        list.setPadding(p, Glass.dp(c, 24), p, Glass.dp(c, 24));
        TextView title = Glass.text(c, getString(R.string.tab_settings), 28, Glass.TEXT, Glass.heading(c, 600));
        title.setPadding(Glass.dp(c, 8), 0, 0, Glass.dp(c, 18));
        list.addView(title);
        String[] names = {"Matchday intro", "Your TVs and sync", "Sports and alerts", getString(R.string.live_settings_title),
                getString(R.string.posters_title), getString(R.string.torbox_title), getString(R.string.subs_title), "App updates"};
        int[] glyphs = {Icons.PLAY, Icons.SHARE, Icons.SPORTS, Icons.LIVE, Icons.FILM, Icons.CLOUD, Icons.WATCH, Icons.DOWN};
        for (int i = 0; i < KEYS.length; i++) {
            TextView row = row(c, names[i], glyphs[i]);
            int at = i;
            row.setOnClickListener(v -> choose(at, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Glass.dp(c, 52));
            lp.bottomMargin = Glass.dp(c, 4);
            list.addView(row, lp);
            rows.add(row);
        }
        listScroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(Glass.dp(c, roomy ? 300 : 264), -1);
        lsp.setMargins(Glass.dp(c, 16), Glass.dp(c, 12), 0, Glass.dp(c, 4));
        root.addView(listScroll, lsp);

        // The page: every section made once (each keeps what it is listening to), only the chosen one showing, in a
        // column no wider than reads well, in the middle of what is left.
        pageScroll = new ScrollView(c);
        pageScroll.setClipToPadding(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Glass.dp(c, 8), 0, Glass.dp(c, 32));
        pageScroll.addView(centred(c, col, Glass.dp(c, 32)), new FrameLayout.LayoutParams(-1, -2));
        root.addView(pageScroll, new LinearLayout.LayoutParams(0, -1, 1f));

        pages.add(com.gathin.tvhomephone.live.IntroSettings.build(c));
        pages.add(com.gathin.tvhomephone.sync.SyncSettings.build(this, col));
        pages.add(com.gathin.tvhomephone.sports.SportsSettings.build(this, col));
        pages.add(com.gathin.tvhomephone.live.LiveTvSettings.build(this, col));
        pages.add(com.gathin.tvhomephone.live.PosterSettings.build(this, col));
        pages.add(com.gathin.tvhomephone.live.TorBoxSettings.build(this, col));
        pages.add(com.gathin.tvhomephone.live.SubtitleSettings.build(this, col));
        pages.add(com.gathin.tvhomephone.update.UpdateSettings.build(this));
        for (View page : pages) col.addView(page, new LinearLayout.LayoutParams(-1, -2));

        int start = keyAt(prefs(c).getString(CHOSEN, INTRO));
        if (toPlaylists) {
            toPlaylists = false;
            start = keyAt(LIVE);
        }
        choose(start, false);
        return root;
    }

    private SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Pure: a section's place in the list (the first for one not known). */
    static int keyAt(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    /** That section's page shown (from its top), its row lit, and kept for next time. */
    private void choose(int at, boolean keep) {
        chosen = Math.max(0, Math.min(at, pages.size() - 1));
        for (int i = 0; i < pages.size(); i++) pages.get(i).setVisibility(i == chosen ? View.VISIBLE : View.GONE);
        for (int i = 0; i < rows.size(); i++) light(rows.get(i), i == chosen);
        if (pageScroll != null) pageScroll.scrollTo(0, 0);
        if (keep && isAdded()) prefs(requireContext()).edit().putString(CHOSEN, KEYS[chosen]).apply();
    }

    /** A section's row in the list: its glyph and name, a glass pill when it is the one showing. */
    private TextView row(Context c, String name, int glyph) {
        TextView t = Glass.text(c, name, 16, Glass.SOFT, Glass.bodySemi(c));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Glass.dp(c, 14), 0, Glass.dp(c, 12), 0);
        Icons icon = new Icons(c, glyph, Glass.MUTED);
        int s = Glass.dp(c, 22);
        icon.setBounds(0, 0, s, s);
        t.setCompoundDrawablesRelative(icon, null, null, null);
        t.setCompoundDrawablePadding(Glass.dp(c, 14));
        t.setTag(icon);
        return t;
    }

    private void light(TextView row, boolean on) {
        Context c = row.getContext();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Glass.dp(c, 16));
        g.setColor(on ? 0x24FFFFFF : 0x00000000);
        if (on) g.setStroke(Math.max(1, Glass.dp(c, 1)), 0x33FFFFFF);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF), g,
                null));
        row.setTextColor(on ? Glass.TEXT : Glass.SOFT);
        ((Icons) row.getTag()).setColour(on ? Glass.TEXT : Glass.MUTED);
        row.setSelected(on);
        row.setContentDescription(row.getText());
    }
}
