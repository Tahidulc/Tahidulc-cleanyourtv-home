package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.gathin.tvhomephone.glass.Icons;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where a film or episode can play from, shown so that TorBox's copies and the IPTV playlists' can't be mistaken: each
 * copy's mark (TorBox's green with a cloud, the playlists' blue with a screen and the playlist's name), its picture's
 * and sound's logos as its name says them (4K Ultra HD, Dolby Vision, Dolby Atmos…, Marks), its file's name and size.
 * As a sheet from the screen's foot to pick one (before playing, and in the player, where the one playing is ticked
 * and another carries on from the same moment), and as the cover page's lines under Play.
 */
public final class SourceSheet {
    private SourceSheet() {}

    /** The marks' own colours (neither service's): TorBox's green, the playlists' blue. */
    static final int TORBOX = 0xFF0E9F6E, IPTV = 0xFF3B5BDB;
    private static final int SHEET = 0xFF14161F, MUTED = 0x99FFFFFF;

    /** A copy picked: its place in the plays given. */
    public interface Pick {
        void pick(int index);
    }

    // ---- the marks -------------------------------------------------------------------------------------------------

    /**
     * Where a copy comes from, heightPx tall: a filled pill, TorBox's green with a cloud ("TorBox"), or the playlists'
     * blue with a screen ("IPTV", and the playlist's name after it).
     */
    public static View mark(Context c, SourceInfo s, int heightPx) {
        LinearLayout m = new LinearLayout(c);
        m.setOrientation(LinearLayout.HORIZONTAL);
        m.setGravity(Gravity.CENTER_VERTICAL);
        m.setMinimumHeight(heightPx);
        GradientDrawable g = new GradientDrawable();
        g.setColor(s.torbox ? TORBOX : IPTV);
        g.setCornerRadius(heightPx / 2f);
        m.setBackground(g);
        m.setPadding(Math.round(heightPx * 0.34f), 0, Math.round(heightPx * 0.42f), 0);
        ImageView glyph = new ImageView(c);
        glyph.setImageDrawable(new Icons(c, s.torbox ? Icons.CLOUD : Icons.LIVE, 0xFFFFFFFF));
        int gp = Math.round(heightPx * 0.62f);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(gp, gp);
        glp.rightMargin = Math.round(heightPx * 0.2f);
        m.addView(glyph, glp);
        float px = heightPx * 0.46f;
        TextView kind = Ui.text(c, s.torbox ? TorBox.NAME : "IPTV", 12, 0xFFFFFFFF, true);
        kind.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        kind.setIncludeFontPadding(false);
        kind.setSingleLine(true);
        m.addView(kind);
        if (!s.torbox && !s.playlist.isEmpty()) {
            TextView which = Ui.text(c, " · " + s.playlist, 12, 0xD9FFFFFF, false);
            which.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
            which.setIncludeFontPadding(false);
            which.setSingleLine(true);
            which.setEllipsize(TextUtils.TruncateAt.END);
            which.setMaxWidth(heightPx * 6);
            m.addView(which);
        }
        m.setContentDescription(s.from());
        return m;
    }

    // ---- the sheet -------------------------------------------------------------------------------------------------

    /** Pure but for reading the plays: their places grouped by where they come from, in the order each first comes. */
    static List<List<Integer>> groups(List<SourceInfo> infos) {
        Map<String, List<Integer>> by = new LinkedHashMap<>();
        for (int i = 0; i < infos.size(); i++) {
            String key = infos.get(i).from();
            List<Integer> g = by.get(key);
            if (g == null) by.put(key, g = new ArrayList<>());
            g.add(i);
        }
        return new ArrayList<>(by.values());
    }

    /**
     * The sheet's content: its title and note, then the copies, each source's under its heading when there are several
     * sources, the one playing ticked (playing ≥ 0) or else the first marked the best match; a tap picks one.
     */
    public static View content(Context c, String title, String note, List<String[]> plays, int playing, Pick pick) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(c, 18);
        col.setPadding(side, Ui.dp(c, 10), side, Ui.dp(c, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SHEET);
        float r = Ui.dp(c, 22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        col.setBackground(bg);
        View handle = new View(c);
        handle.setBackground(Ui.round(c, 0x40FFFFFF, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(c, 12);
        col.addView(handle, hp);
        col.addView(Ui.text(c, title, 18, Ui.TEXT, true));
        if (note != null && !note.isEmpty()) {
            TextView n = Ui.text(c, note, 13, MUTED, false);
            n.setPadding(0, Ui.dp(c, 3), 0, 0);
            col.addView(n);
        }
        List<SourceInfo> infos = new ArrayList<>();
        for (String[] p : plays) infos.add(SourceInfo.of(p));
        List<List<Integer>> groups = groups(infos);
        for (List<Integer> g : groups) {
            SourceInfo first = infos.get(g.get(0));
            if (groups.size() > 1) {
                String head = first.torbox ? "FROM YOUR TORBOX" : first.playlist.isEmpty() ? "FROM YOUR IPTV PLAYLIST"
                        : "FROM YOUR IPTV PLAYLIST · " + first.playlist.toUpperCase(java.util.Locale.ROOT);
                TextView h = Ui.text(c, head, 11, first.torbox ? 0xFF5EE0B0 : 0xFF91A7FF, true);
                h.setLetterSpacing(0.08f);
                h.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
                col.addView(h);
            } else {
                View gap = new View(c);
                col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(c, 10)));
            }
            for (int i : g) {
                String status = i == playing ? "✓  Playing" : playing < 0 && i == 0 && plays.size() > 1 ? "Best match" : "";
                View row = row(c, infos.get(i), plays.get(i), status, i == playing);
                int at = i;
                row.setOnClickListener(v -> pick.pick(at));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.topMargin = Ui.dp(c, 6);
                col.addView(row, rp);
            }
        }
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.setFillViewport(true);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        return scroll;
    }

    /** One copy: its mark, its logos and what it is ("Playing", "Best match"); under them its file's name, size and kind. */
    static View row(Context c, SourceInfo s, String[] play, String status, boolean current) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 12);
        row.setPadding(pad, Ui.dp(c, 11), pad, Ui.dp(c, 11));
        row.setBackground(Ui.touch(c, current, current ? 0x24FFFFFF : 0x0FFFFFFF, 14));
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        // The mark, then the logos beside it, on to a second line under it when the row is narrow (a phone upright).
        Marks.Flow flow = new Marks.Flow(c, Ui.dp(c, 6), Ui.dp(c, 6));
        flow.addView(mark(c, s, Ui.dp(c, 26)));
        for (Marks.Mark m : s.marks()) flow.addView(Marks.box(c, m, Ui.dp(c, 22), 11f));
        flow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        top.addView(flow, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!status.isEmpty()) {
            TextView st = Ui.text(c, status, 12, current ? 0xFFFFFFFF : MUTED, true);
            st.setSingleLine(true);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.leftMargin = Ui.dp(c, 8);
            top.addView(st, sp);
        }
        row.addView(top);
        String line = detail(s, play);
        if (!line.isEmpty()) {
            TextView d = Ui.text(c, line, 12, MUTED, false);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            d.setPadding(0, Ui.dp(c, 7), 0, 0);
            row.addView(d);
        }
        row.setContentDescription(s.spoken() + (status.isEmpty() ? "" : ", " + status.replace("✓", "").trim()));
        return row;
    }

    /** Pure: a copy's second line: its file's (or entry's) name, its size, its kind ("Glass.Harbour.2024.2160p.mkv · 14.2 GB"). */
    static String detail(SourceInfo s, String[] play) {
        List<String> parts = new ArrayList<>();
        if (!s.name.isEmpty()) parts.add(s.name);
        String size = SourceInfo.size(s.size);
        if (!size.isEmpty()) parts.add(size);
        if (!s.container.isEmpty() && !s.name.toUpperCase(java.util.Locale.ROOT).endsWith("." + s.container)) parts.add(s.container);
        if (parts.isEmpty() && play != null && play.length > 3 && play[3] != null) return play[3];
        return String.join(" · ", parts);
    }

    /**
     * The copies in a sheet from the screen's foot (all of it open at once): a tap shuts it and picks that one. Returns
     * the sheet, already showing.
     */
    public static BottomSheetDialog show(Activity a, String title, String note, List<String[]> plays, int playing, Pick pick) {
        BottomSheetDialog d = new BottomSheetDialog(a);
        d.setContentView(content(a, title, note, plays, playing, i -> {
            d.dismiss();
            pick.pick(i);
        }));
        d.setOnShowListener(x -> {
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
        });
        BottomSheetBehavior<?> b = d.getBehavior();
        b.setSkipCollapsed(true);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        // Only on a page still open (a search finishing after the family left it would close the app otherwise).
        if (a.isFinishing() || a.isDestroyed()) return d;
        try {
            d.show();
        } catch (android.view.WindowManager.BadTokenException gone) {
            return d;
        }
        // A tablet: a card in the middle of the screen rather than a sheet across its foot.
        com.gathin.tvhomephone.glass.Wide.card(d);
        return d;
    }

    // ---- the cover page's lines ------------------------------------------------------------------------------------

    /**
     * One line: a source's mark, its best copy's logos, then what more there is ("+2 more", "12 episodes"); on to a
     * second line when the page is narrow, centred on a phone.
     */
    private static View line(Context c, SourceInfo s, String more, boolean centre) {
        Marks.Flow l = new Marks.Flow(c, Ui.dp(c, 6), Ui.dp(c, 5));
        l.setCentre(centre);
        l.addView(mark(c, s, Ui.dp(c, 22)));
        for (Marks.Mark m : s.marks()) l.addView(Marks.box(c, m, Ui.dp(c, 20), 10f));
        if (!more.isEmpty()) {
            TextView m = Ui.text(c, more, 12, MUTED, false);
            m.setSingleLine(true);
            l.addView(m);
        }
        l.setContentDescription(s.spoken() + (more.isEmpty() ? "" : ", " + more));
        l.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return l;
    }

    private static LinearLayout lines(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private static void add(LinearLayout col, View line, Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        if (col.getChildCount() > 0) lp.topMargin = Ui.dp(c, 6);
        col.addView(line, lp);
    }

    /** Under a film's Play: each source once (TorBox's, each playlist's), with its best copy's logos and how many more. */
    public static View strip(Context c, List<String[]> plays, boolean centre) {
        LinearLayout col = lines(c);
        List<SourceInfo> infos = new ArrayList<>();
        for (String[] p : plays) infos.add(SourceInfo.of(p));
        for (List<Integer> g : groups(infos)) {
            add(col, line(c, infos.get(g.get(0)), g.size() > 1 ? "+" + (g.size() - 1) + " more" : "", centre), c);
        }
        return col;
    }

    /** Under a series' Play: each source once, with its first copy's logos and how many of the episodes it has. */
    public static View stripEpisodes(Context c, List<Vod.Episode> eps, boolean centre) {
        LinearLayout col = lines(c);
        Map<String, SourceInfo> first = new LinkedHashMap<>();
        Map<String, Set<Integer>> have = new LinkedHashMap<>();
        for (Vod.Episode e : eps) {
            for (String[] p : e.plays()) {
                SourceInfo s = SourceInfo.of(p);
                String key = s.from();
                if (!first.containsKey(key)) first.put(key, s);
                Set<Integer> set = have.get(key);
                if (set == null) have.put(key, set = new TreeSet<>());
                set.add(e.season * 10000 + e.number);
            }
        }
        for (Map.Entry<String, SourceInfo> en : first.entrySet()) {
            int n = have.get(en.getKey()).size();
            add(col, line(c, en.getValue(), n + (n == 1 ? " episode" : " episodes"), centre), c);
        }
        return col;
    }

    /** Fills a holder with lines (or empties it for none). */
    public static void fill(ViewGroup holder, View lines) {
        holder.removeAllViews();
        if (lines != null) holder.addView(lines, new FrameLayout.LayoutParams(-1, -2));
    }
}
