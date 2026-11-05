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
 * "Also Showing On": the family's own playlists' channels for the same game, each with its logo, the likeliest first
 * and the one to watch marked: an event channel named for the game ("EPL 03: Arsenal v Chelsea"), those whose guide
 * has the game, and the playlists' channels of the broadcasters it is on (its 4K/UHD channel too, marked 4K).
 * Broadcasters the playlists have none of are named only when they have nothing.
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
        boolean now, uhd, best;
        /** How sure it is to show the game: by the guide, by a broadcaster, a favourite, the better picture. */
        int score;
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
        return rows(c, g, current, when(g, System.currentTimeMillis()));
    }

    /** When to look in the guides for a game: now once it has started, its kick-off (a minute in) before. */
    static long when(Game g, long now) {
        return g != null && g.state == Game.PRE && g.kickoff > now ? g.kickoff + 60_000L : now;
    }

    /**
     * The channels for a game, from the family's own playlist: those whose guide has the game at that time (the best
     * evidence), and the playlist's channels of the broadcasters it is on (ESPN's, the British channel, the
     * competition's, its 4K/UHD one too), each once, the likeliest first: what plays now, then a channel both ways, then
     * by the guide, then by the broadcaster, a favourite and the better picture ahead. The first playable is marked as
     * the one to watch. Broadcasters the playlist has none of are named only when it has nothing at all for the game.
     */
    static List<Row> rows(Context c, Game g, Channel current, long when) {
        Library lib = Library.get(c);
        lib.ensureNow();
        java.util.Map<String, Row> byKey = new java.util.LinkedHashMap<>();
        List<Row> elsewhere = new ArrayList<>();
        List<Game> one = new ArrayList<>();
        one.add(g);
        java.util.Set<String> fav = lib.favourites();
        List<Channel> all = lib.list(Library.ALL, "");
        // The playlists' event channels named for the game ("EPL 03: Arsenal v Chelsea 15:00"): the surest of all.
        String[][] clubs = {clubNames(g.homeName, g.homeFull), clubNames(g.awayName, g.awayFull)};
        for (Channel ch : all) {
            if (!names(words(ch.name), clubs)) continue;
            Row r = row(ch, current, fav);
            r.under = c.getString(R.string.tile_also_event);
            r.score += 5;
            byKey.put(ch.key, r);
            if (byKey.size() >= 12) break;
        }
        // The playlists' channels with the game in their guide.
        for (Channel ch : all) {
            Guide.Schedule s = lib.schedule(ch.key);
            int at = s == null ? -1 : s.at(when);
            if (at < 0) continue;
            String title = s.title[at], desc = s.desc[at];
            if (!OnAir.looksLikeFixture(title) && !OnAir.looksLikeFixture(desc) && !mentions(title + " " + desc, g)) continue;
            Game m = OnAir.match(title, desc, s.start[at], s.stop[at], one);
            if (m == null) continue;
            Row r = byKey.get(ch.key);
            if (r == null) {
                r = row(ch, current, fav);
                r.under = title;
                byKey.put(ch.key, r);
            }
            r.score += 4;
            if (byKey.size() >= 24) break;
        }
        // The broadcasters it is on, as the playlist has them: its 4K/UHD channel and its HD one.
        for (String name : Channels.names(g)) {
            Channel[] tier = lib.tiers(name);
            if (tier[0] == null && tier[1] == null) {
                Row r = new Row();
                r.name = name;
                String logo = ChannelLogos.find(c, name);
                r.logo = logo != null ? logo : ChannelLogos.sky(name);
                r.under = c.getString(R.string.tile_also_not_in_list);
                elsewhere.add(r);
                continue;
            }
            for (int t = 1; t >= 0; t--) {
                Channel ch = tier[t];
                if (ch == null) continue;
                Row r = byKey.get(ch.key);
                if (r == null) {
                    r = row(ch, current, fav);
                    r.under = c.getString(t == 1 ? R.string.tile_also_uhd : R.string.tile_also_listed);
                    byKey.put(ch.key, r);
                }
                if (r.logo == null || r.logo.isEmpty()) {
                    String logo = ChannelLogos.find(c, name);
                    r.logo = logo != null ? logo : ChannelLogos.sky(name);
                }
                r.uhd |= t == 1;
                r.score += 3;
            }
        }
        List<Row> out = new ArrayList<>(byKey.values());
        out.sort((a, b) -> Integer.compare(rank(b), rank(a)));
        for (Row r : out) {
            if (r.ch != null && !r.now) {
                r.best = true;
                break;
            }
        }
        if (out.isEmpty()) out.addAll(elsewhere);
        return out;
    }

    /** A playlist channel's row, before what says it shows the game is added. */
    private static Row row(Channel ch, Channel current, java.util.Set<String> fav) {
        Row r = new Row();
        r.ch = ch;
        r.name = ch.name;
        r.logo = Ui.logo(ch);
        r.uhd = Names.uhd(ch.name);
        r.now = current != null && current.key.equals(ch.key);
        if (fav.contains(ch.key)) r.score += 2;
        if (r.uhd) r.score += 1;
        return r;
    }

    /** The playlist channel to watch a game on (the first of rows that can be played), or null. Off the main thread. */
    public static Channel best(Context c, Game g) {
        if (g == null) return null;
        for (Row r : rows(c, g, null)) if (r.ch != null) return r.ch;
        return null;
    }

    private static int rank(Row r) {
        return (r.now ? 1000 : 0) + (r.ch != null ? 100 : 0) + r.score;
    }

    /** Whether words name both clubs (a guide that says "Dortmund v Betis" without the word "Live"). */
    private static boolean mentions(String words, Game g) {
        return names(words(words), new String[][]{clubNames(g.homeName, g.homeFull), clubNames(g.awayName, g.awayFull)});
    }

    /** Words made comparable (pure): lower case, accents and punctuation gone, a space either side. */
    static String words(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replace("&", " and ");
        return " " + n.replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    /** Whether words (as words() makes them) name both clubs, each by one of its names. Pure. */
    static boolean names(String words, String[][] clubs) {
        for (String[] club : clubs) {
            boolean found = false;
            for (String n : club) {
                if (words.contains(" " + n + " ")) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return clubs.length > 0;
    }

    /**
     * The words a club goes by in a channel's name or a guide (pure): ESPN's short name and its full one ("Man United",
     * "Manchester United"), United as Utd, without FC, and its own word when it has one ("Tottenham", "Wolverhampton";
     * never a word two clubs share, as City, United or Manchester).
     */
    static String[] clubNames(String shortName, String full) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String name : new String[]{shortName, full}) {
            String w = words(name).trim();
            if (w.isEmpty()) continue;
            w = (" " + w + " ").replace(" fc ", " ").replace(" afc ", " ").replace(" cf ", " ").trim();
            if (w.isEmpty()) continue;
            out.add(w);
            if ((" " + w + " ").contains(" united ")) out.add((" " + w + " ").replace(" united ", " utd ").trim());
            for (String part : w.split(" ")) {
                if (part.length() >= 5 && !SHARED.contains(part)) {
                    out.add(part);
                    break;
                }
            }
        }
        return out.toArray(new String[0]);
    }

    /** Words in more than one club's name, never enough on their own. */
    private static final java.util.Set<String> SHARED = new java.util.HashSet<>(java.util.Arrays.asList(
            "united", "city", "town", "county", "rovers", "athletic", "athletico", "atletico", "albion", "wanderers", "hotspur",
            "manchester", "sheffield", "bristol", "nottingham", "birmingham", "borussia", "sporting", "olympique", "olympic",
            "racing", "deportivo", "union", "dynamo", "dinamo", "inter", "internacional", "real", "royal", "young", "saint",
            "queens", "rangers", "celtic", "academy", "women", "ladies", "reserves", "under", "north", "south", "east", "west",
            "club", "football", "sport", "sports", "stade", "stadium"));

    private static void show(Context c, Game g, List<Row> rows, Pick pick) {
        BottomSheetDialog[] mine = new BottomSheetDialog[1];
        mine[0] = Sheets.open(c, sheet(c, g, rows, pick, mine), true);
    }

    /** The sheet's content: the heading, the fixture and a row for each channel. */
    static LinearLayout sheet(Context c, Game g, List<Row> rows, Pick pick, BottomSheetDialog[] mine) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 20));
        TextView head = Ui.text(c, c.getString(R.string.tile_also_showing), 13, Ui.G_SELECT, true);
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
            String says = r.under == null ? "" : r.under;
            if (r.best) says = c.getString(R.string.tile_also_best) + (says.isEmpty() ? "" : " · " + says);
            TextView under = Ui.text(c, says, 12, r.best ? 0xFF8EF0B8 : Ui.SOFT, r.best);
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
