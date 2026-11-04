package com.gathin.tvhomephone.live;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.ChannelLogos;
import com.gathin.tvhomephone.sports.Channels;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnAir;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * "Also showing on": every channel showing the same game, each with its logo: the playlist's channels whose guide has
 * the game on now, then the broadcasters it is on (the British channel, ESPN's, the competition's), those the playlist
 * has to tap and play (its 4K/UHD channel too, marked 4K, before its HD one), the others named so you know where else
 * it is.
 */
public final class AlsoShowing {
    private AlsoShowing() {}

    /** Where a pick goes: the player switching channel, or the match page opening the player. */
    public interface Pick {
        void play(Channel ch);
    }

    /** One line of the list: the playlist's channel (null when it has none), the name, the logo, the words under it. */
    static final class Row {
        Channel ch;
        String name, logo, under;
        boolean now, uhd;
    }

    /** Opens the list for a game, read off the main thread; a note when nothing else shows it. */
    public static void open(Context c, Game g, Channel current, Pick pick) {
        if (g == null) return;
        Context app = c.getApplicationContext();
        App.io.execute(() -> {
            List<Row> rows = rows(app, g, current);
            App.main.post(() -> {
                if (c instanceof android.app.Activity && ((android.app.Activity) c).isFinishing()) return;
                if (rows.isEmpty()) {
                    Toast.makeText(c, R.string.tile_also_none, Toast.LENGTH_SHORT).show();
                    return;
                }
                show(c, g, rows, pick);
            });
        });
    }

    static List<Row> rows(Context c, Game g, Channel current) {
        Library lib = Library.get(c);
        lib.ensureNow();
        List<Row> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<Game> one = new ArrayList<>();
        one.add(g);
        // The playlist's channels with the game in their guide now.
        for (Channel ch : lib.list(Library.ALL, "")) {
            Guide.Schedule s = lib.schedule(ch.key);
            int at = s == null ? -1 : s.at(now);
            if (at < 0) continue;
            String title = s.title[at], desc = s.desc[at];
            if (!OnAir.looksLikeFixture(title) && !OnAir.looksLikeFixture(desc) && !mentions(title + " " + desc, g)) continue;
            Game m = OnAir.match(title, desc, s.start[at], s.stop[at], one);
            if (m == null || keys.contains(ch.key)) continue;
            Row r = new Row();
            r.ch = ch;
            r.name = ch.name;
            r.logo = Ui.logo(ch);
            r.uhd = Names.uhd(ch.name);
            r.under = title;
            r.now = current != null && current.key.equals(ch.key);
            keys.add(ch.key);
            out.add(r);
            if (out.size() >= 24) break;
        }
        // The broadcasters it is on.
        for (String name : Channels.names(g)) {
            // The playlist's 4K/UHD channel for it first, then its HD one; only named when the playlist has neither.
            Channel[] tier = lib.tiers(name);
            if (tier[0] == null && tier[1] == null) {
                Row r = new Row();
                r.name = name;
                String logo = ChannelLogos.find(c, name);
                r.logo = logo != null ? logo : ChannelLogos.sky(name);
                r.under = c.getString(R.string.tile_also_not_in_list);
                out.add(r);
                continue;
            }
            for (int t = 1; t >= 0; t--) {
                Channel ch = tier[t];
                if (ch == null || keys.contains(ch.key)) continue;
                Row r = new Row();
                r.ch = ch;
                r.name = ch.name;
                r.uhd = t == 1;
                String logo = !ch.logo.isEmpty() ? ch.logo : ChannelLogos.find(c, name);
                if (logo == null) logo = ChannelLogos.sky(name);
                r.logo = logo != null ? logo : ChannelLogos.sky(ch.name);
                r.under = c.getString(r.uhd ? R.string.tile_also_uhd : R.string.tile_also_listed);
                r.now = current != null && current.key.equals(ch.key);
                keys.add(ch.key);
                out.add(r);
            }
        }
        // What plays now first, then what can be played, then the rest.
        out.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        return out;
    }

    private static int rank(Row r) {
        return r.now ? 0 : r.ch != null ? 1 : 2;
    }

    /** Whether words name both clubs (a guide that says "Dortmund v Betis" without the word "Live"). */
    private static boolean mentions(String words, Game g) {
        String w = words.toLowerCase(java.util.Locale.ROOT);
        return has(w, g.homeName) && has(w, g.awayName);
    }

    private static boolean has(String words, String club) {
        if (club == null || club.isEmpty()) return false;
        String c = club.toLowerCase(java.util.Locale.ROOT);
        if (words.contains(c)) return true;
        int sp = c.lastIndexOf(' ');
        return sp > 0 && c.length() - sp > 4 && words.contains(c.substring(sp + 1));
    }

    private static void show(Context c, Game g, List<Row> rows, Pick pick) {
        BottomSheetDialog[] mine = new BottomSheetDialog[1];
        mine[0] = Sheets.open(c, sheet(c, g, rows, pick, mine), true);
    }

    /** The sheet's content: the heading, the fixture and a row for each channel. */
    static LinearLayout sheet(Context c, Game g, List<Row> rows, Pick pick, BottomSheetDialog[] mine) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 20));
        TextView head = Ui.text(c, c.getString(R.string.tile_also_showing).toUpperCase(java.util.Locale.UK), 12, Ui.G_SELECT, true);
        head.setLetterSpacing(0.1f);
        box.addView(head);
        TextView fixture = Ui.text(c, g.homeName + " v " + g.awayName, 18, Ui.TEXT, true);
        fixture.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12));
        box.addView(fixture);
        for (Row r : rows) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
            row.setBackground(Ui.touch(c, false, 0, 12));
            FrameLayout chip = new FrameLayout(c);
            chip.setBackground(Ui.round(c, 0xFF0C3483, 8));
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int pad = Ui.dp(c, 6);
            chip.setPadding(pad, pad, pad, pad);
            chip.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            TextView initials = Ui.text(c, initials(r.name), 13, Ui.TEXT, true);
            initials.setGravity(Gravity.CENTER);
            initials.setMaxLines(1);
            chip.addView(initials, new FrameLayout.LayoutParams(-1, -1));
            if (r.logo != null && !r.logo.isEmpty()) {
                initials.setVisibility(View.GONE);
                com.gathin.tvhomephone.sports.Ui.load(logo, r.logo, null, () -> initials.setVisibility(View.VISIBLE));
            }
            if (r.uhd) {
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
                bl.setMargins(0, 0, -Ui.dp(c, 3), -Ui.dp(c, 3));
                chip.addView(Ui.uhdBadge(c), bl);
                chip.setClipChildren(false);
                chip.setClipToPadding(false);
            }
            row.addView(chip, new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 42)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
            TextView name = Ui.text(c, r.name, 16, Ui.TEXT, true);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(name);
            TextView under = Ui.text(c, r.under == null ? "" : r.under, 12, Ui.SOFT, false);
            under.setMaxLines(1);
            under.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(under);
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            if (r.now || r.ch != null) {
                TextView act = Ui.text(c, c.getString(r.now ? R.string.tile_also_playing : R.string.tile_also_watch), 13,
                        r.now ? Ui.SOFT : Ui.G_INK, true);
                act.setGravity(Gravity.CENTER);
                act.setMaxLines(1);
                act.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
                act.setBackground(Ui.round(c, r.now ? 0x26FFFFFF : Ui.G_SELECT, 16));
                row.addView(act);
            }
            if (r.ch != null && !r.now) {
                Channel ch = r.ch;
                row.setOnClickListener(v -> {
                    if (mine[0] != null) mine[0].dismiss();
                    pick.play(ch);
                });
            }
            row.setContentDescription(r.name + ", " + (r.under == null ? "" : r.under));
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return box;
    }

    private static String initials(String name) {
        if (name == null) return "";
        StringBuilder b = new StringBuilder();
        for (String w : name.split("\\s+")) {
            if (!w.isEmpty() && b.length() < 3) b.append(Character.toUpperCase(w.charAt(0)));
        }
        return b.toString();
    }
}
