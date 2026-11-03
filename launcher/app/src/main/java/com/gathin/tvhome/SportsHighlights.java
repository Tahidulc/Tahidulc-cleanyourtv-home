package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Highlights for the Sports page, a row for each sport, from the official YouTube channels that put them up where the
 * TV is (each channel's public list of uploads, its feed; no key needed): highlights, extended highlights, full fights,
 * every goal or touchdown, condensed matches and the like first, newest first; the channel's other full-length
 * videos after them when there are few; never Shorts, live streams or anything older than three weeks. Each sport is
 * read again at most every 15 minutes, off the main thread. OK on one plays it in a big pop-up on Home (HighlightPlayer),
 * with the event's details beside it; in YouTube when it cannot play there.
 */
final class SportsHighlights {
    private SportsHighlights() { }

    /** One video: its YouTube id, title, the channel's name, the sport's mark and row, what it says, when it went up. */
    static final class Clip {
        String id, title, channel, mark, sport, description;
        long published;
        boolean highlight;
        /** Its competition's look (brand), worked out once. */
        Brand brand;
    }

    /** A sport: its row's name, the mark on its cards and its channels {id, name}. */
    static final class Sport {
        final String name, mark;
        final String[][] channels;

        Sport(String name, String mark, String[][] channels) {
            this.name = name;
            this.mark = mark;
            this.channels = channels;
        }
    }

    /** The sports, in the order of their rows; every channel's feed checked to answer, with highlights in it. */
    static final Sport[] SPORTS = {
            new Sport("FOOTBALL", "FOOTBALL", new String[][]{
                    {"UCNAf1k0yIjyGu3k9BwAg3lg", "Sky Sports Premier League"}, {"UCZ7wY7MRDSygp63HIEfdQZA", "Sky Sports Football"}}),
            new Sport("FORMULA 1", "F1", new String[][]{
                    {"UC3kxJQ9RfaS5CKeYbbFMi4Q", "Sky Sports F1"}, {"UCB_qr75-ydFVKSF9Dmo6izg", "Formula 1"}}),
            new Sport("BOXING", "BOXING", new String[][]{
                    {"UC7LReVje9aPB4B6XAsXX8WQ", "Matchroom Boxing"}, {"UCurvRE5fGcdUgCYWgh-BDsg", "DAZN Boxing"},
                    {"UC_JQGBtA7P0RwkRxd7xpJcA", "Sky Sports Boxing"}}),
            new Sport("MMA · UFC", "UFC", new String[][]{{"UCvgfXK4nTYKudb0rFR6noLA", "UFC"}}),
            new Sport("TENNIS", "TENNIS", new String[][]{
                    {"UCbcxFkd6B9xUU54InHv4Tig", "Tennis TV"}, {"UCaBIVVpHjq6j3tSyxwTE-8Q", "WTA"}}),
            new Sport("CRICKET", "CRICKET", new String[][]{{"UCkd4takjjF1EGD1TKIK2QiA", "Sky Sports Cricket"}}),
            new Sport("RUGBY", "RUGBY", new String[][]{
                    {"UCE28rwYoaV7jvU6GVzdu_GQ", "World Rugby"}, {"UCL5bAcVfbxSAs-UM5f5ncWg", "Six Nations"}}),
            new Sport("NFL", "NFL", new String[][]{{"UCDVYQ4Zhbm3S2dlz7P1GBDg", "NFL"}}),
            new Sport("NBA", "NBA", new String[][]{{"UCWJ2lWNubArHWmf3FIHbfcQ", "NBA"}}),
    };

    static final int PER_SPORT = 10;
    private static final long KEEP_MS = 15 * 60_000L, OLDEST_MS = 21L * 24 * 3600_000L;
    /** A highlight by its title ("highlights" used as a verb, "Carragher highlights the…", left out). */
    private static final Pattern HIGHLIGHT = Pattern.compile("highlights?\\b(?!\\s+(the|how|why|what|a|an|his|her|their|its)\\b)"
            + "|full (fight|match|game|race)|every (goal|touchdown|try|wicket|point)|top \\d+ plays|extended|condensed"
            + "|recap|knockouts|best (shots|points)\\b", Pattern.CASE_INSENSITIVE);
    /** Live streams, press conferences and tactical feeds: not highlights, and often not playable afterwards. */
    private static final Pattern LIVE = Pattern.compile("^\\s*live\\b|\\blive (stream|press|weigh|undercard|match|coverage)"
            + "|tactical feed|press conference", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Object[]> kept = new HashMap<>();

    /** A sport's videos, highlights first (newest first), then its channels' other full-length ones; off the main thread. */
    static List<Clip> load(Sport s) {
        synchronized (kept) {
            Object[] k = kept.get(s.name);
            if (k != null && System.currentTimeMillis() - (long) k[0] < KEEP_MS) {
                @SuppressWarnings("unchecked") List<Clip> c = (List<Clip>) k[1];
                return c;
            }
        }
        List<Clip> high = new ArrayList<>(), other = new ArrayList<>();
        long now = System.currentTimeMillis();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] ch : s.channels) {
            for (OfficialVideos.Video v : OfficialVideos.feed(ch[0])) {
                if (v.youtube == null || v.shorts || v.title == null || v.title.contains("#") || LIVE.matcher(v.title).find()) continue;
                if (v.published > 0 && now - v.published > OLDEST_MS) continue;
                if (!seen.add(v.youtube)) continue;
                Clip c = new Clip();
                c.id = v.youtube;
                c.title = v.title.trim();
                c.channel = ch[1];
                c.mark = s.mark;
                c.sport = s.name;
                c.description = v.description;
                c.published = v.published;
                c.highlight = HIGHLIGHT.matcher(c.title).find();
                (c.highlight ? high : other).add(c);
            }
        }
        high.sort((a, b) -> Long.compare(b.published, a.published));
        other.sort((a, b) -> Long.compare(b.published, a.published));
        List<Clip> out = new ArrayList<>();
        for (Clip c : high) if (out.size() < PER_SPORT) out.add(c);
        for (Clip c : other) if (out.size() < PER_SPORT) out.add(c);
        synchronized (kept) {
            kept.put(s.name, new Object[]{now, out});
        }
        return out;
    }

    /** The chip on a card: what kind of video it is, read from its title. */
    static String kind(Clip c) {
        String t = c.title.toLowerCase(Locale.UK);
        if (t.contains("full fight")) return "FULL FIGHT";
        if (t.contains("full match") || t.contains("full game highlights")) return "FULL GAME";
        if (t.contains("extended")) return "EXTENDED";
        if (t.contains("condensed")) return "CONDENSED";
        if (t.contains("every goal") || t.contains("every touchdown") || t.contains("every try")) return "EVERY SCORE";
        if (t.matches(".*top \\d+ plays.*")) return "TOP PLAYS";
        if (t.contains("recap")) return "RECAP";
        return c.highlight ? "HIGHLIGHTS" : null;
    }

    // ---- the competitions' own looks -----------------------------------------------------------------------------

    /**
     * A competition's (or sport's) look on the cards and in the player: its name, its logo's address, its colour and a
     * second colour to go with it (the line under the strip, the headings); null logo: the sport's word instead.
     */
    static final class Brand {
        final String name, logo;
        final int colour, accent;

        Brand(String name, String logo, int colour, int accent) {
            this.name = name;
            this.logo = logo;
            this.colour = colour;
            this.accent = accent;
        }
    }

    private static final String ESPN_SOCCER = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/";
    private static final String ESPN_LEAGUES = "https://a.espncdn.com/i/teamlogos/leagues/500/";

    /** Football competitions: {words in a title, ESPN's league id, its name, colour, second colour}. */
    private static final Object[][] FOOTBALL = {
            {"premier league", "23", "Premier League", 0xFF3D195B, 0xFF00FF85},
            {"champions league", "2", "Champions League", 0xFF0E1E5B, 0xFF5FB6FF},
            {"europa league", "2310", "Europa League", 0xFF1A1A1A, 0xFFF68E00},
            {"conference league", "20296", "Conference League", 0xFF0B3D2E, 0xFF00D26A},
            {"spfl", "45", "Scottish Premiership", 0xFF1B1F5E, 0xFFFFD100},
            {"scottish", "45", "Scottish Premiership", 0xFF1B1F5E, 0xFFFFD100},
            {"serie a", "12", "Serie A", 0xFF0B1F4B, 0xFF00B2FF},
            {"laliga", "15", "LaLiga", 0xFF1E1E2D, 0xFFFF4B44},
            {"la liga", "15", "LaLiga", 0xFF1E1E2D, 0xFFFF4B44},
            {"bundesliga", "10", "Bundesliga", 0xFF2A0A0D, 0xFFD20515},
            {"ligue 1", "9", "Ligue 1", 0xFF091C3E, 0xFFDAE025},
            {"saudi", "2488", "Saudi Pro League", 0xFF04342A, 0xFF00C38A},
            {"fa cup", "40", "FA Cup", 0xFF14214D, 0xFFE8B23A},
            {"carabao", "41", "Carabao Cup", 0xFF00274D, 0xFF2DC84D},
            {"league cup", "41", "Carabao Cup", 0xFF00274D, 0xFF2DC84D},
            {"championship", "24", "Championship", 0xFF0B1C3F, 0xFFE1B23B},
            {"efl", "24", "Championship", 0xFF0B1C3F, 0xFFE1B23B},
    };

    /** The ATP's, WTA's and Six Nations' badges (TheSportsDB's), the logos ESPN has none of. */
    private static final String ATP = "https://r2.thesportsdb.com/images/media/league/badge/q7aej51769857150.png",
            WTA = "https://r2.thesportsdb.com/images/media/league/badge/bddhun1768230678.png",
            SIX_NATIONS = "https://r2.thesportsdb.com/images/media/league/badge/7h1wr91738670253.png";

    /** A football competition's look by ESPN's league id ("23"), for one found on ESPN; null when not one of ours. */
    static Brand footballBrand(String espnId) {
        if (espnId == null) return null;
        for (Object[] f : FOOTBALL) if (f[1].equals(espnId)) return new Brand((String) f[2], ESPN_SOCCER + f[1] + ".png", (Integer) f[3], (Integer) f[4]);
        return null;
    }

    /** ESPN's league slugs ("eng.1") to their ids for the logos ("23"). */
    private static final String[][] SLUGS = {{"eng.1", "23"}, {"uefa.champions", "2"}, {"uefa.europa", "2310"}, {"uefa.europa.conf", "20296"},
            {"sco.1", "45"}, {"ita.1", "12"}, {"esp.1", "15"}, {"ger.1", "10"}, {"fra.1", "9"}, {"ksa.1", "2488"}, {"eng.fa", "40"},
            {"eng.league_cup", "41"}, {"eng.2", "24"}};

    /** A football competition's look by ESPN's slug ("sco.1"); null when it is not one of ours. */
    static Brand footballBrandBySlug(String slug) {
        if (slug == null) return null;
        for (String[] x : SLUGS) if (x[0].equals(slug)) return footballBrand(x[1]);
        return null;
    }

    /** The ATP's or the WTA's look. */
    static Brand tourBrand(boolean wta) {
        return wta ? new Brand("WTA", WTA, 0xFF2A1240, 0xFFB45DE6) : new Brand("ATP Tour", ATP, 0xFF0A1D3A, 0xFF00A1E0);
    }

    /** A clip's look: the competition named in its title (or its channel's), else its sport's own. */
    static Brand brand(Clip c) {
        if (c.brand != null) return c.brand;
        String sport = c.sport == null ? "" : c.sport;
        String t = (c.title == null ? "" : c.title).toLowerCase(Locale.UK);
        String ch = c.channel == null ? "" : c.channel;
        Brand b = null;
        if (sport.startsWith("FOOTBALL")) {
            for (Object[] f : FOOTBALL) {
                if (t.contains((String) f[0])) { b = new Brand((String) f[2], ESPN_SOCCER + f[1] + ".png", (Integer) f[3], (Integer) f[4]); break; }
            }
            if (b == null && ch.contains("Premier League")) b = footballBrand("23");
            if (b == null) b = new Brand("Football", null, 0xFF15402A, 0xFF3DDC84);
        } else if (sport.startsWith("FORMULA")) {
            b = new Brand("Formula 1", F1Results.F1_LOGO, 0xFF15151E, 0xFFE10600);
        } else if (sport.contains("UFC")) {
            b = new Brand("UFC", ESPN_LEAGUES + "ufc.png", 0xFF111111, 0xFFD20A0A);
        } else if (sport.startsWith("TENNIS")) {
            b = tourBrand(ch.contains("WTA") || t.contains("wta"));
        } else if (sport.equals("NFL")) {
            b = new Brand("NFL", ESPN_LEAGUES + "nfl.png", 0xFF013369, 0xFFD50A0A);
        } else if (sport.equals("NBA")) {
            b = new Brand("NBA", ESPN_LEAGUES + "nba.png", 0xFF1D428A, 0xFFC8102E);
        } else if (sport.startsWith("RUGBY")) {
            b = ch.contains("Six Nations") || t.contains("six nations") ? new Brand("Six Nations", SIX_NATIONS, 0xFF0E1F44, 0xFF2FA4E7)
                    : new Brand("Rugby", null, 0xFF0E2A3F, 0xFF2FA4E7);
        } else if (sport.startsWith("CRICKET")) {
            b = new Brand("Cricket", null, 0xFF0E3B23, 0xFF4CC36E);
        } else if (sport.startsWith("BOXING")) {
            b = new Brand("Boxing", null, 0xFF2A1A08, 0xFFE0A526);
        } else {
            b = new Brand(sport, null, 0xFF1B2591, 0xFF7FA7FF);
        }
        c.brand = b;
        return b;
    }

    /**
     * A highlight's card, clean: the video's own widescreen picture with nothing written over it but what it is
     * (HIGHLIGHTS, FULL FIGHT, EXTENDED…) at the top left and the competition's own logo (or, without one, the sport's
     * word) at the top right; its title, channel and age go under the row (caption). w and hgt in Home's design pixels;
     * small for the player's Up next.
     */
    static android.widget.FrameLayout card(HomeV2 h, Clip c, int w, int hgt, boolean small) {
        android.content.Context ctx = h.a;
        android.widget.FrameLayout f = new android.widget.FrameLayout(ctx);
        int r = small ? 12 : 16;
        f.setClipToOutline(true);
        f.setOutlineProvider(h.round(r));
        f.setBackground(h.fill(r, 0xFF15182E));
        android.widget.ImageView pic = new android.widget.ImageView(ctx);
        pic.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        f.addView(pic, new android.widget.FrameLayout.LayoutParams(-1, -1));
        // "#yt": the widescreen picture where the video has one, else the usual one cut free of its black bars.
        h.image(pic, "https://i.ytimg.com/vi/" + c.id + "/hqdefault.jpg#yt", h.px(w));
        // A light shade along the top only, so the chip and the logo read on any picture; the rest of it left clear.
        android.view.View shade = new android.view.View(ctx);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x73000000, 0x00000000, 0x00000000}));
        f.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
        int m = small ? 8 : 12;
        String kind = kind(c);
        if (kind != null) {
            android.widget.TextView k = h.text(small ? 11 : 14, h.semi, android.graphics.Color.WHITE);
            k.setText(kind);
            k.setLetterSpacing(0.08f);
            k.setPadding(h.px(small ? 7 : 10), h.px(small ? 2 : 3), h.px(small ? 7 : 10), h.px(small ? 2 : 3));
            Brand b = brand(c);
            k.setBackground(h.fill(small ? 6 : 8, (b.colour & 0x00FFFFFF) | 0xE6000000));
            android.widget.FrameLayout.LayoutParams kl = new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.START);
            kl.leftMargin = h.px(m);
            kl.topMargin = h.px(m);
            f.addView(k, kl);
        }
        f.addView(mark(h, c, small), markParams(h, small));
        return f;
    }

    /** The competition's logo on a small dark tab (its brand colour), or the sport's word when there is no logo. */
    static android.view.View mark(HomeV2 h, Clip c, boolean small) {
        Brand b = brand(c);
        android.content.Context ctx = h.a;
        if (b.logo != null) {
            android.widget.ImageView logo = new android.widget.ImageView(ctx);
            logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            // As wide as the logo needs at the tab's height (a wordmark wider than a badge).
            logo.setAdjustViewBounds(true);
            logo.setMaxWidth(h.px(small ? 90 : 140));
            logo.setMinimumWidth(h.px(small ? 34 : 48));
            int pad = h.px(small ? 4 : 6);
            logo.setPadding(pad, pad, pad, pad);
            logo.setBackground(h.fill(small ? 7 : 10, (b.colour & 0x00FFFFFF) | 0xD9000000));
            h.image(logo, b.logo, h.px(small ? 64 : 96));
            logo.setContentDescription(b.name);
            return logo;
        }
        android.widget.TextView word = h.text(small ? 11 : 13, h.semi, android.graphics.Color.WHITE);
        word.setText(c.mark);
        word.setLetterSpacing(0.1f);
        word.setGravity(android.view.Gravity.CENTER);
        word.setPadding(h.px(small ? 7 : 9), h.px(small ? 2 : 3), h.px(small ? 7 : 9), h.px(small ? 2 : 3));
        android.graphics.drawable.GradientDrawable g = h.fill(small ? 6 : 8, (b.colour & 0x00FFFFFF) | 0xD9000000);
        g.setStroke(Math.max(1, h.px(1)), (b.accent & 0x00FFFFFF) | 0x99000000);
        word.setBackground(g);
        return word;
    }

    private static android.widget.FrameLayout.LayoutParams markParams(HomeV2 h, boolean small) {
        int size = h.px(small ? 34 : 48);
        android.widget.FrameLayout.LayoutParams ml = new android.widget.FrameLayout.LayoutParams(-2, size, android.view.Gravity.TOP | android.view.Gravity.END);
        ml.rightMargin = h.px(small ? 8 : 12);
        ml.topMargin = h.px(small ? 8 : 12);
        return ml;
    }

    /** Under a row of highlights: the focused one's title, then its channel and how long ago it went up, lighter. */
    static CharSequence caption(Clip c) {
        String age = age(c.published);
        String rest = (c.channel == null ? "" : c.channel) + (age.isEmpty() ? "" : "  ·  " + age);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(c.title == null ? "" : c.title);
        int at = sb.length();
        if (!rest.isEmpty()) {
            sb.append("    ").append(rest);
            sb.setSpan(new android.text.style.ForegroundColorSpan(0x99FFFFFF), at, sb.length(), 0);
        }
        return sb;
    }

    /** "12 min ago", "3h ago", "Yesterday", "4 days ago". */
    static String age(long published) {
        if (published <= 0) return "";
        long mins = Math.max(1, (System.currentTimeMillis() - published) / 60_000L);
        if (mins < 60) return mins + " min ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days == 1 ? "Yesterday" : days + " days ago";
    }
}
