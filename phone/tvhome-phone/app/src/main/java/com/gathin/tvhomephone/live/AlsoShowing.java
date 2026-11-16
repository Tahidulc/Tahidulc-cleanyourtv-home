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
 * has the game, and the playlists' channels of the broadcasters it is on (its 4K/UHD channel too, marked 4K). English
 * channels come before the others (a "UK:" or "US:" tag, a British or American network), then those that don't say,
 * then those in another language. Broadcasters the playlists have none of are named only when they have nothing.
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
        /** In English: 2 sure, 1 not known, 0 another language (english()). */
        int english = 1;
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
            if (!names(channelWords(ch.name), clubs)) continue;
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
        r.english = english(ch.name + " " + (ch.group == null ? "" : ch.group));
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

    /** What plays now, then a playlist channel, then English before not known before another language, then how sure. */
    private static int rank(Row r) {
        return (r.now ? 1000 : 0) + (r.ch != null ? 100 + r.english * 30 : 0) + r.score;
    }

    /** For the match page when no British channel is listed for the game: the playlist channel to watch it on. */
    public static final class Shown {
        public final Channel ch;
        /** Its name made plain ("Premier Sports 1" for "UK: PREMIER SPORTS 1 FHD") and its logo (null for none). */
        public final String name, logo;

        Shown(Channel ch, String name, String logo) {
            this.ch = ch;
            this.name = name;
            this.logo = logo;
        }
    }

    /** The playlist channel likeliest to show the game, an English one first, with its logo; null for none. Off the main thread. */
    public static Shown shown(Context c, Game g) {
        if (g == null) return null;
        for (Row r : rows(c, g, null)) {
            if (r.ch == null) continue;
            String logo = r.logo != null && !r.logo.isEmpty() ? r.logo : Ui.logo(r.ch);
            if (logo == null || logo.isEmpty()) logo = ChannelLogos.find(c, tidy(r.ch.name));
            return new Shown(r.ch, tidy(r.ch.name), logo);
        }
        return null;
    }

    private static final java.util.Set<String> ENGLISH_TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "uk", "gb", "en", "eng", "english", "england", "ie", "irl", "ireland", "us", "usa", "ca", "can", "au", "aus", "nz",
            "sco", "scotland", "wales", "nir"));
    private static final java.util.Set<String> OTHER_TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "de", "ger", "at", "ch", "fr", "fra", "es", "esp", "spa", "nl", "pt", "por", "be", "it", "ita", "pl", "pol", "tr",
            "tur", "ar", "arab", "arabic", "br", "bra", "mx", "lat", "latam", "latino", "gr", "ro", "hu", "cz", "sk", "se",
            "swe", "no", "nor", "dk", "den", "fi", "fin", "ru", "rus", "ua", "il", "ir", "kr", "jp", "cn", "al", "alb", "exyu",
            "yu", "hr", "rs", "srb", "ba", "si", "bg", "mk", "ma", "dz", "tn", "eg", "sa", "ae", "qa", "kw", "af", "pk", "bd",
            "ku", "vf", "vost"));
    /** Words that say a channel speaks another language, wherever they are. */
    private static final String[] OTHER_WORDS = {" arabic ", " french ", " german ", " spanish ", " italian ", " portuguese ",
            " turkish ", " polish ", " dutch ", " greek ", " russian ", " hindi ", " urdu ", " bangla ", " deutsch ",
            " espanol ", " francais ", " deportes ", " latino ", " latam "};
    /** British, Irish, American and Australian networks, in English whatever their tag says. */
    private static final String[] ENGLISH_NETWORKS = {" sky sports ", " tnt sport ", " tnt sports ", " bt sport ", " bbc ",
            " itv ", " itv1 ", " itv4 ", " channel 4 ", " channel 5 ", " premier sports ", " viaplay sports ", " eurosport 1 ",
            " espn ", " fox sports ", " nbc ", " cbs ", " tsn ", " sportsnet ", " optus ", " stan sport ", " peacock ",
            " sky sport news ", " racing tv ", " free sports ", " s4c "};

    /**
     * Pure: how sure a channel (its name and group) is to be in English: 2 for an English tag ("UK:", "|US|", "IE -",
     * "[EN]") or a British, Irish, American or Australian network; 0 for another country's or language's tag or a word
     * that says so ("AR:", "|DE|", "Arabic", "Deportes"); 1 when it doesn't say.
     */
    static int english(String nameAndGroup) {
        String n = words(nameAndGroup);
        // Said outright ("Al Jazeera English", "beIN Sports English") whatever the first word looks like.
        if (n.contains(" english ")) return 2;
        String[] toks = n.trim().split(" ");
        if (toks.length > 0 && !toks[0].isEmpty()) {
            if (ENGLISH_TAGS.contains(toks[0])) return 2;
            if (OTHER_TAGS.contains(toks[0])) return 0;
        }
        for (String w : OTHER_WORDS) if (n.contains(w)) return 0;
        if (n.contains(" eng ")) return 2;
        for (String net : ENGLISH_NETWORKS) if (n.contains(net)) return 2;
        if (toks.length > 1) {
            String last = toks[toks.length - 1];
            if (ENGLISH_TAGS.contains(last)) return 2;
            if (OTHER_TAGS.contains(last)) return 0;
        }
        return 1;
    }

    private static final java.util.regex.Pattern LEAD = java.util.regex.Pattern.compile("^\\s*[\\[(|]?\\s*([A-Za-z]{2,7})\\s*[\\])|:\\-]+\\s*");
    private static final java.util.regex.Pattern TAIL = java.util.regex.Pattern.compile(
            "(?i)(\\s+(?:hd|fhd|uhd|4k|sd|hevc|raw|h265|1080p|720p|50fps|60fps|backup|bk|alt|vip|\\u1D34\\u1D30))+\\s*$");

    /** Names kept in capitals when a channel's name is all capitals (the rest become words: "BBC One", "TNT Sports"). */
    private static final java.util.Set<String> ACRONYMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "BBC", "ITV", "TNT", "ESPN", "NBC", "CBS", "TSN", "BT", "UK", "US", "USA", "MTV", "CNN", "RTE", "LFC", "MUTV", "NFL",
            "NBA", "NHL", "MLB", "UFC", "WWE", "PGA", "ATP", "WTA", "DAZN", "HD", "UHD", "EPL", "SPL", "SRF", "ORF", "ZDF", "ARD",
            "RAI", "TV", "EFL", "PDC", "NOW"));

    /** Pure: a playlist channel's name made plain: its country tag and picture tags gone, capitals made words ("TNT Sports 1"). */
    static String tidy(String name) {
        String n = name == null ? "" : name.trim();
        java.util.regex.Matcher m = LEAD.matcher(n);
        if (m.lookingAt() && m.end() < n.length()) {
            String tag = m.group(1).toLowerCase(java.util.Locale.ROOT);
            if (ENGLISH_TAGS.contains(tag) || OTHER_TAGS.contains(tag)) n = n.substring(m.end());
        }
        n = TAIL.matcher(n).replaceAll("").trim();
        n = n.replaceAll("\\s+", " ");
        if (!n.isEmpty() && n.equals(n.toUpperCase(java.util.Locale.ROOT))) {
            StringBuilder b = new StringBuilder();
            for (String w : n.split(" ")) {
                if (b.length() > 0) b.append(' ');
                boolean letters = w.chars().allMatch(Character::isLetter);
                if (!letters || ACRONYMS.contains(w)) b.append(w);
                else b.append(w.charAt(0)).append(w.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
            n = b.toString();
        }
        return n.isEmpty() ? (name == null ? "" : name.trim()) : n;
    }

    /** Whether words name both clubs (a guide that says "Dortmund v Betis" without the word "Live"). */
    private static boolean mentions(String words, Game g) {
        return names(words(words), new String[][]{clubNames(g.homeName, g.homeFull), clubNames(g.awayName, g.awayFull)});
    }

    /** Channels' names as words() makes them, kept: the Sport guide asks for every game of the day in turn. */
    private static final java.util.Map<String, String> CHANNEL_WORDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static String channelWords(String name) {
        String n = name == null ? "" : name;
        String w = CHANNEL_WORDS.get(n);
        if (w == null) {
            if (CHANNEL_WORDS.size() > 200_000) CHANNEL_WORDS.clear();
            w = words(n);
            CHANNEL_WORDS.put(n, w);
        }
        return w;
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
