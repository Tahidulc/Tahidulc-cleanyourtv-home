package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * A game's tab pages, drawn into a column: the timeline (the game on a pitch, each shot, goal and card where it happened,
 * then its moments newest first, the home side's on the left and the away side's on the right), the line-ups in the TV's
 * line-ups-over-apps look (each side on its pitch with the live ratings on top of the players' pictures, then its
 * substitutes and manager), and the stats in each side's colours. The game's page (GameActivity) and the player's match
 * centre (MatchPanel) both draw theirs with it.
 */
public final class GameTabs {
    public static final int TIMELINE = 0, LINEUPS = 1, STATS = 2;
    public static final String[] NAMES = {"Timeline", "Line-ups", "Stats"};

    private final Context c;
    /** Whether the screen it draws on is still there (the photos stop coming when it isn't). */
    private final BooleanSupplier alive;
    private Game game;
    private Espn.Summary summary = new Espn.Summary();
    private LinearLayout body;
    /** The stats in this many columns (two across a wide panel). */
    private int columns = 1;
    /** In a low, wide panel: the moments first, the shot map after them and narrower. */
    private boolean compact;
    /** The players' photos asked for by name (PlayerPhotos, when FotMob has no line-ups for the game). */
    private final java.util.Set<String> asked = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    /**
     * Both sides' sheets as FotMob gives them (live ratings, places, flags, pictures), the game they were read for,
     * when, and when FotMob was last asked; the side shown (0 home, 1 away, -1 not chosen: your club's, else home's);
     * the line-ups' box on the page now.
     */
    private TeamSheet[] sheets;
    private String sheetsFor = "";
    private long sheetsAt, sheetsTried;
    private boolean sheetsBusy;
    private int sheetSide = -1;
    private LinearLayout shownSheets;
    /** The line-ups as the TV's card shows them, above the pitch (the game's own page; not the player's panel). */
    private boolean tvCard;
    /** The card's elevens with their photos (read once for these line-ups), what they were read for, the card shown. */
    private com.gathin.tvhomephone.live.BoardLineups.Data cardData;
    private String cardFor = "";
    private com.gathin.tvhomephone.live.BoardLineups shownCard;
    /** The card's shape on a phone: the TV's, made taller for the narrower page. */
    static final float CARD_SHAPE = 2.3f;
    static final long CARD_PAGE_MS = 3_400L;

    public GameTabs(Context c, BooleanSupplier alive) {
        this.c = c;
        this.alive = alive;
    }

    /** The game and what ESPN has on it now. */
    public void set(Game g, Espn.Summary s) {
        game = g;
        summary = s == null ? new Espn.Summary() : s;
    }

    /** The stats two across (a wide panel) or one. */
    public void columns(int n) {
        columns = Math.max(1, n);
    }

    /** A low, wide panel: the timeline's moments before its shot map. */
    public void compact(boolean on) {
        compact = on;
    }

    /** The line-ups led by the TV's line-up card (the game's own page). */
    public void tvCard(boolean on) {
        tvCard = on;
    }

    /** The tabs there is something to show in, in order. */
    public List<Integer> available() {
        List<Integer> out = new ArrayList<>();
        if (game == null) return out;
        if (!summary.events.isEmpty() || game.state != Game.PRE) out.add(TIMELINE);
        if (Pitch.has(summary.homeLineup, summary.awayLineup)) out.add(LINEUPS);
        if (!summary.stats.isEmpty() && game.state != Game.PRE) out.add(STATS);
        return out;
    }

    /** One tab's page into the column. */
    public void paint(int tab, LinearLayout into) {
        body = into;
        if (game == null) return;
        if (tab == TIMELINE) timeline();
        else if (tab == LINEUPS) lineups();
        else if (tab == STATS) stats();
    }

    /** {home, away}: the kits worn, told apart. */
    private int[] kits() {
        return Glyphs.kits(summary.homeKit, summary.homeKitAlt, game.homeColor, summary.awayKit, summary.awayKitAlt, game.awayColor);
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** A player's name as the line-ups shorten it ("Koita", "Vinícius Júnior"), else his surname. */
    private String shortName(String name) {
        for (List<String[]> side : java.util.Arrays.asList(summary.homeLineup, summary.awayLineup)) {
            for (String[] p : side) if (p[Espn.P_NAME].equals(name) && !p[Espn.P_SHORT].isEmpty()) return p[Espn.P_SHORT];
        }
        return surname(name);
    }

    private static String surname(String name) {
        if (name == null) return "";
        int sp = name.lastIndexOf(' ');
        return sp > 0 && name.length() - sp > 3 ? name.substring(sp + 1) : name;
    }

    // ---- the timeline -------------------------------------------------------------------------------------------

    private void timeline() {
        List<String[]> ev = summary.events;
        // The score after each goal, from the scoreboard's goals when they agree with the summary's.
        List<Integer> goalAt = new ArrayList<>();
        for (int i = 0; i < ev.size(); i++) if (Glyphs.scored(ev.get(i)[1])) goalAt.add(i);
        String[] after = new String[ev.size()];
        int h = 0, a = 0;
        for (int n = 0; n < goalAt.size(); n++) {
            int i = goalAt.get(n);
            String side = goalAt.size() == game.goals.size() ? game.goals.get(n)[3] : ev.get(i)[3];
            if ("home".equals(side)) h++; else a++;
            after[i] = h + "–" + a;
        }
        int htH = 0, htA = 0;
        if (summary.halfTimeAt >= 0) {
            for (int n = 0; n < goalAt.size() && goalAt.get(n) < summary.halfTimeAt; n++) {
                String side = goalAt.size() == game.goals.size() ? game.goals.get(n)[3] : ev.get(goalAt.get(n))[3];
                if ("home".equals(side)) htH++; else htA++;
            }
        }
        if (game.state != Game.PRE && !compact) body.addView(map(), Ui.margins(c, 0, 12, 0, 6));
        if (game.state == Game.POST) body.addView(divider("FULL TIME", game.homeScore + "–" + game.awayScore));
        else if (game.state == Game.LIVE) body.addView(divider(game.halftime ? "HALF TIME" : "LIVE", game.homeScore + "–" + game.awayScore));
        if (ev.isEmpty() && game.state == Game.LIVE) {
            TextView none = Ui.text(c, "No goals or cards yet", 14, c.getColor(R.color.text_dim), false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
            body.addView(none, Ui.matchWrap());
        }
        for (int i = ev.size() - 1; i >= 0; i--) {
            if (summary.halfTimeAt >= 0 && i == summary.halfTimeAt - 1 && !(game.state == Game.LIVE && game.halftime)) {
                body.addView(divider("HALF TIME", htH + "–" + htA));
            }
            body.addView(moment(ev.get(i), after[i]));
        }
        String ko = java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        if (game.state != Game.PRE) body.addView(divider("KICK-OFF", ko));
        if (game.state != Game.PRE && compact) {
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(c, 460), ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.gravity = Gravity.CENTER_HORIZONTAL;
            mp.topMargin = Ui.dp(c, 10);
            body.addView(map(), mp);
        }
    }

    /** A colour that shows on the grass: a green kit turned to white (or to its second colour). */
    private static int onGrass(int kit, String alt) {
        if (Glyphs.distance(kit, 0xFF1C6A44) >= 0.6 && Glyphs.luminance(kit) > 0.03) return kit;
        int a = Glyphs.color(alt, 0);
        return a != 0 && Glyphs.distance(a, 0xFF1C6A44) >= 0.6 && Glyphs.luminance(a) > 0.03 ? a : 0xFFF4F5FA;
    }

    /**
     * The game on a pitch: each side's shots and how many were on target above it, which way each attacks, the shots,
     * goals and cards where they happened, the key under it and the newest moment in words.
     */
    private View map() {
        int[] k = kits();
        int hc = onGrass(k[0], summary.homeKitAlt), ac = onGrass(k[1], summary.awayKitAlt);
        if (Glyphs.distance(hc, ac) < 0.5) ac = Glyphs.luminance(hc) > 0.5 ? 0xFF1A2466 : 0xFFF4F5FA;
        LinearLayout col = Ui.column(c);
        int hs = 0, hon = 0, as = 0, aon = 0;
        for (String[] m : summary.plays) {
            String kind = m[Espn.M_KIND];
            boolean shot = Espn.GOAL.equals(kind) || Espn.ON_TARGET.equals(kind) || Espn.OFF_TARGET.equals(kind) || Espn.BLOCKED.equals(kind);
            boolean on = Espn.GOAL.equals(kind) || Espn.ON_TARGET.equals(kind);
            if (!shot) continue;
            if ("home".equals(m[Espn.M_SIDE])) { hs++; if (on) hon++; } else { as++; if (on) aon++; }
        }
        LinearLayout top = Ui.row(c);
        top.addView(shots(game.homeLogo, hs, hon, true), Ui.weight(1));
        top.addView(shots(game.awayLogo, as, aon, false), Ui.weight(1));
        col.addView(top, Ui.margins(c, 2, 0, 2, 8));
        MatchMap pitch = new MatchMap(c);
        pitch.show(summary.plays, hc, ac, game.state == Game.LIVE);
        pitch.setContentDescription("Shots: " + game.homeName + " " + hs + ", " + hon + " on target; " + game.awayName + " " + as + ", "
                + aon + " on target");
        col.addView(pitch, Ui.matchWrap());
        LinearLayout ways = Ui.row(c);
        TextView hw = Ui.text(c, game.homeName + "  →", 12, c.getColor(R.color.text_dim), true);
        hw.setMaxLines(1);
        TextView aw = Ui.text(c, "←  " + game.awayName, 12, c.getColor(R.color.text_dim), true);
        aw.setMaxLines(1);
        aw.setGravity(Gravity.END);
        ways.addView(hw, Ui.weight(1));
        ways.addView(aw, Ui.weight(1));
        col.addView(ways, Ui.margins(c, 4, 6, 4, 0));
        LinearLayout key = Ui.row(c);
        key.setGravity(Gravity.CENTER);
        key.addView(keyItem(dot(0xFFFFFFFF, true), "On target"));
        key.addView(keyItem(dot(0xFFFFFFFF, false), "Off target"));
        key.addView(keyItem(Glyphs.drawable(c, Glyphs.GOAL, 13), "Goal"));
        key.addView(keyItem(Glyphs.drawable(c, Glyphs.YELLOW, 13), "Card"));
        col.addView(key, Ui.margins(c, 0, 10, 0, 0));
        if (!summary.plays.isEmpty()) {
            String[] last = summary.plays.get(summary.plays.size() - 1);
            String what;
            switch (last[Espn.M_KIND]) {
                case Espn.GOAL: what = "scores"; break;
                case Espn.ON_TARGET: what = "shot saved"; break;
                case Espn.OFF_TARGET: what = "shot off target"; break;
                case Espn.BLOCKED: what = "shot blocked"; break;
                case Espn.YELLOW: what = "yellow card"; break;
                default: what = "red card";
            }
            TextView latest = Ui.text(c, (game.state == Game.LIVE ? "LATEST  ·  " : "LAST  ·  ") + last[Espn.M_MINUTE] + "  "
                    + shortName(last[Espn.M_PLAYER]) + " " + what, 12.5f, c.getColor(R.color.text), true);
            latest.setMaxLines(1);
            latest.setEllipsize(TextUtils.TruncateAt.END);
            latest.setGravity(Gravity.CENTER);
            latest.setBackground(Ui.fill(c, 0x1AFFFFFF, 12));
            latest.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            col.addView(latest, Ui.margins(c, 0, 10, 0, 0));
        }
        return col;
    }

    private View shots(String crest, int shots, int on, boolean home) {
        LinearLayout r = Ui.row(c);
        r.setGravity((home ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        ImageView v = Ui.image(c, crest, 20);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView t = Ui.text(c, shots + (shots == 1 ? " shot" : " shots") + " · " + on + " on target", 12.5f, c.getColor(R.color.text_soft), true);
        t.setMaxLines(1);
        t.setPadding(Ui.dp(c, 7), 0, Ui.dp(c, 7), 0);
        if (home) { r.addView(v); r.addView(t); } else { r.addView(t); r.addView(v); }
        return r;
    }

    private static Drawable dot(int color, boolean filled) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (filled) g.setColor(color); else g.setStroke(3, color);
        return g;
    }

    private View keyItem(Drawable icon, String words) {
        TextView t = Ui.text(c, words, 11.5f, c.getColor(R.color.text_dim), false);
        t.setMaxLines(1);
        int s = Ui.dp(c, icon instanceof GradientDrawable ? 9 : 13);
        icon.setBounds(0, 0, icon instanceof GradientDrawable ? s : icon.getIntrinsicWidth(), icon instanceof GradientDrawable ? s : icon.getIntrinsicHeight());
        t.setCompoundDrawables(icon, null, null, null);
        t.setCompoundDrawablePadding(Ui.dp(c, 5));
        t.setPadding(Ui.dp(c, 7), 0, Ui.dp(c, 7), 0);
        return t;
    }

    private View divider(String words, String score) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER);
        r.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        View l = new View(c), rr = new View(c);
        l.setBackgroundColor(c.getColor(R.color.surface_line));
        rr.setBackgroundColor(c.getColor(R.color.surface_line));
        r.addView(l, new LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1));
        TextView t = Ui.text(c, words + "   " + score, 11.5f, c.getColor(R.color.text_soft), true);
        t.setLetterSpacing(0.1f);
        if ("LIVE".equals(words)) {
            // "LIVE · 67:23   1–0", the clock going on in minutes and seconds.
            t.setFontFeatureSettings("tnum");
            Game g = game;
            LiveClock.bind(t, g, w -> "LIVE · " + w + "   " + score);
        }
        t.setBackground(Ui.fill(c, 0x14FFFFFF, 12));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(c, 10);
        tp.rightMargin = Ui.dp(c, 10);
        r.addView(t, tp);
        r.addView(rr, new LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1));
        return r;
    }

    /** One moment: its minute in the middle, the home side's on the left reading inwards, the away side's on the right. */
    private View moment(String[] e, String score) {
        boolean home = !"away".equals(e[3]);
        int glyph = Glyphs.of(e[1]);
        LinearLayout r = Ui.row(c);
        r.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
        LinearLayout content = Ui.row(c);
        content.setGravity((home ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        LinearLayout words = Ui.column(c);
        words.setGravity(home ? Gravity.END : Gravity.START);
        String second = e.length > 5 ? e[5] : "";
        boolean sub = glyph == Glyphs.SUB;
        TextView main = Ui.text(c, shortName(e[2]), 14.5f, sub ? 0xFF8EF0B8 : c.getColor(R.color.text), true);
        main.setMaxLines(1);
        main.setEllipsize(TextUtils.TruncateAt.END);
        main.setGravity(home ? Gravity.END : Gravity.START);
        words.addView(main);
        String under;
        if (sub) under = second.isEmpty() ? "" : "Off: " + shortName(second);
        else if (score != null) under = score + (second.isEmpty() ? "" : " · Assist " + surname(second))
                + (e[1].toLowerCase(Locale.ROOT).contains("penalty") ? " · Penalty" : "");
        else if (glyph == Glyphs.VAR || glyph == Glyphs.MISS) under = e[1];
        else under = "";
        if (!under.isEmpty()) {
            TextView u = Ui.text(c, under, 12, sub ? 0xFFFF9EA2 : c.getColor(R.color.text_soft), score != null);
            u.setMaxLines(1);
            u.setEllipsize(TextUtils.TruncateAt.END);
            u.setGravity(home ? Gravity.END : Gravity.START);
            words.addView(u);
        }
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(Glyphs.drawable(c, glyph, 20));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(c, glyph == Glyphs.VAR ? 34 : 26), Ui.dp(c, 26));
        if (home) {
            content.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ip.leftMargin = Ui.dp(c, 8);
            content.addView(icon, ip);
        } else {
            ip.rightMargin = Ui.dp(c, 8);
            content.addView(icon, ip);
            content.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        TextView minute = Ui.text(c, e[0], 12.5f, c.getColor(R.color.text), true);
        minute.setGravity(Gravity.CENTER);
        minute.setMaxLines(1);
        boolean goal = score != null;
        minute.setBackground(Ui.fill(c, goal ? 0x333DDC84 : 0x1AFFFFFF, 13));
        minute.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(c, 52), ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = Ui.dp(c, 8);
        mp.rightMargin = Ui.dp(c, 8);
        View space = new View(c);
        if (home) {
            r.addView(content, Ui.weight(1));
            r.addView(minute, mp);
            r.addView(space, Ui.weight(1));
        } else {
            r.addView(space, Ui.weight(1));
            r.addView(minute, mp);
            r.addView(content, Ui.weight(1));
        }
        String club = home ? game.homeName : game.awayName;
        r.setContentDescription(e[0] + " " + e[1] + ", " + e[2] + (sub && !second.isEmpty() ? " on for " + second : "") + ", " + club
                + (score != null ? ", " + score : ""));
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return r;
    }

    // ---- the line-ups -------------------------------------------------------------------------------------------

    /**
     * The line-ups in the TV's line-ups-over-apps look (the TV's animated card above on the game's own page): the side
     * chosen on its pitch, each man with his live rating on top of his picture, his number, position and flag, then
     * the substitutes and the manager (SheetViews). FotMob's sheets (its live ratings) are read again each minute while
     * the game is on; ESPN's line-ups stand in until they come, or when FotMob doesn't have the game.
     */
    private void lineups() {
        if (tvCard) body.addView(tvCardView(), Ui.margins(c, 0, 10, 0, 4));
        LinearLayout box = Ui.column(c);
        shownSheets = box;
        drawSheets(box);
        body.addView(box, Ui.margins(c, 0, 10, 0, 0));
        readSheets();
    }

    /** The sides' pills and the chosen side's sheet into the box. */
    private void drawSheets(LinearLayout box) {
        box.removeAllViews();
        TeamSheet[] s = sheetsNow();
        if (sheetSide < 0) sheetSide = favouriteSide();
        int[] k = kits();
        box.addView(SheetViews.sides(c, game.homeName, game.homeLogo, game.awayName, game.awayLogo, sheetSide, side -> {
            sheetSide = side;
            drawSheets(box);
        }), Ui.matchWrap());
        boolean home = sheetSide == 0;
        box.addView(SheetViews.sheet(c, s[sheetSide], home ? game.homeName : game.awayName, home ? game.homeLogo : game.awayLogo,
                k[sheetSide], TeamSheet.best(s)), Ui.margins(c, 0, 10, 0, 0));
        if (!"FotMob".equals(s[0].from)) espnPhotos();
    }

    /** FotMob's sheets for this game when read, else ESPN's (its exact substitutions named from the timeline). */
    private TeamSheet[] sheetsNow() {
        if (sheets != null && game.id.equals(sheetsFor)) return sheets;
        TeamSheet[] out = {TeamSheet.espn(summary.homeLineup, summary.homeFormation), TeamSheet.espn(summary.awayLineup, summary.awayFormation)};
        for (TeamSheet s : out) {
            for (List<TeamSheet.Man> list : java.util.Arrays.asList(s.starters, s.subs)) {
                for (TeamSheet.Man m : list) {
                    m.photo = "name:" + m.name;
                    String off = m.on.isEmpty() ? "" : replaced(m.name);
                    if (!off.isEmpty()) m.replaced = surname(off);
                }
            }
        }
        return out;
    }

    /** The side shown first: the away side when it is one of your clubs (and the home side isn't), else the home side. */
    private int favouriteSide() {
        java.util.Set<String> mine = Clubs.starred(c);
        return mine.contains(game.awayId) && !mine.contains(game.homeId) ? 1 : 0;
    }

    /**
     * FotMob's sheets read (off the main thread): once, then again each minute while the game is on (its live ratings);
     * when it hasn't the game, asked again a minute later. The box on the page is drawn again when they come.
     */
    private void readSheets() {
        if (sheetsBusy || game == null) return;
        long now = System.currentTimeMillis();
        boolean same = game.id.equals(sheetsFor);
        if (same && sheets != null && (game.state != Game.LIVE || now - sheetsAt < 50_000L)) return;
        if (same && sheets == null && now - sheetsTried < 60_000L) return;
        sheetsBusy = true;
        Game g = game;
        List<String[]> home = new ArrayList<>(summary.homeLineup), away = new ArrayList<>(summary.awayLineup);
        App.io.execute(() -> {
            TeamSheet[] got = null;
            try {
                got = TeamSheet.fotmob(com.gathin.tvhomephone.live.FotMob.matchJson(g.id, g.homeName, g.awayName, g.kickoff));
                if (got != null) {
                    TeamSheet.roles(got[0], home);
                    TeamSheet.roles(got[1], away);
                }
            } catch (RuntimeException ignored) {
                // ESPN's stand in
            }
            TeamSheet[] read = got;
            App.main.post(() -> {
                sheetsBusy = false;
                if (game == null || !g.id.equals(game.id)) return;
                sheetsFor = g.id;
                sheetsTried = System.currentTimeMillis();
                if (read == null) return;
                sheets = read;
                sheetsAt = sheetsTried;
                if (shownSheets != null && shownSheets.isAttachedToWindow() && alive.getAsBoolean()) drawSheets(shownSheets);
            });
        });
    }

    /** Pure: what a game's line-ups are, to know when they changed: its id and each starter's number and name. */
    static String lineupsKey(String gameId, Espn.Summary s) {
        StringBuilder b = new StringBuilder(gameId == null ? "" : gameId);
        for (List<String[]> side : java.util.Arrays.asList(s.homeLineup, s.awayLineup)) {
            b.append('|');
            for (String[] p : side) if ("1".equals(p[Espn.P_STARTER])) b.append(p[Espn.P_NUMBER]).append(':').append(p[Espn.P_NAME]).append(',');
        }
        return b.toString();
    }

    /**
     * The TV's line-up card as wide as the page: each side's players head and shoulders a page at a time, then both on
     * the pitch, round and round, a tap turning to the next page. Its elevens' photos are read once (off the main
     * thread); the card comes in when they have.
     */
    private View tvCardView() {
        int screen = c.getResources().getDisplayMetrics().widthPixels;
        int side = Math.max(Ui.dp(c, 14), (screen - Ui.dp(c, 620)) / 2);
        int w = screen - 2 * side, h = Math.round(w / CARD_SHAPE);
        com.gathin.tvhomephone.live.BoardLineups board = new com.gathin.tvhomephone.live.BoardLineups(c);
        board.phone(com.gathin.tvhomephone.live.BoardLineups.perPageFor(w, h), CARD_PAGE_MS, true);
        board.setContentDescription("Line-ups: " + describe(game.homeName, summary.homeFormation, summary.homeLineup) + " "
                + describe(game.awayName, summary.awayFormation, summary.awayLineup) + " Tap for the next page.");
        board.setLayoutParams(new LinearLayout.LayoutParams(-1, h));
        shownCard = board;
        String key = lineupsKey(game.id, summary);
        if (cardData != null && key.equals(cardFor)) {
            board.show(cardData, null);
            return board;
        }
        Game g = game;
        Espn.Summary s = summary;
        android.content.Context app = c.getApplicationContext();
        int px = Ui.dp(c, 56);
        App.io.execute(() -> {
            com.gathin.tvhomephone.live.BoardLineups.Data d;
            try {
                d = com.gathin.tvhomephone.live.ScoreBoard.lineupsData(app, g, s, px, true);
            } catch (RuntimeException e) {
                return;
            }
            App.main.post(() -> {
                if (!alive.getAsBoolean()) return;
                cardData = d;
                cardFor = key;
                if (shownCard == board && d.full()) board.show(d, null);
            });
        });
        return board;
    }

    /**
     * The players' photos for ESPN's sheets (FotMob not having the game): the starters' and then the substitutes', each
     * looked up once by name (PlayerPhotos), kept by name for the sheets as they come (one at a time).
     */
    private void espnPhotos() {
        List<String[]> want = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            List<String[]> lineup = side == 0 ? summary.homeLineup : summary.awayLineup;
            String club = side == 0 ? (game.homeFull.isEmpty() ? game.homeName : game.homeFull) : (game.awayFull.isEmpty() ? game.awayName : game.awayFull);
            for (String starter : new String[]{"1", "0"}) {
                for (String[] p : lineup) {
                    if (!starter.equals(p[Espn.P_STARTER]) || SheetArt.picture("name:" + p[Espn.P_NAME]) != null) continue;
                    if (asked.add(p[Espn.P_NAME])) want.add(new String[]{p[Espn.P_NAME], club, p.length > Espn.P_ID ? p[Espn.P_ID] : ""});
                }
            }
        }
        if (want.isEmpty()) return;
        android.content.Context app = c.getApplicationContext();
        App.io.execute(() -> {
            for (String[] w : want) {
                if (!alive.getAsBoolean()) return;
                android.graphics.Bitmap b = PlayerPhotos.get(app, w[0], w[1], w[2]);
                if (b != null) App.main.post(() -> SheetArt.put("name:" + w[0], b));
            }
        });
    }

    private String describe(String club, String formation, List<String[]> lineup) {
        StringBuilder b = new StringBuilder(club + (formation.isEmpty() ? "" : ", " + formation) + ":");
        for (List<Integer> row : Formation.rows(formation, lineup)) {
            for (int i : row) b.append(' ').append(lineup.get(i)[Espn.P_SHORT]).append(',');
        }
        return b.toString();
    }

    /** Whom a substitute replaced, from the timeline's substitution ("" when not told). */
    private String replaced(String name) {
        for (String[] e : summary.events) {
            if (Glyphs.of(e[1]) == Glyphs.SUB && e[2].equals(name) && e.length > 5) return e[5];
        }
        return "";
    }

    // ---- the stats ----------------------------------------------------------------------------------------------

    private void stats() {
        int[] k = kits();
        int hc = Glyphs.onDark(k[0]), ac = Glyphs.onDark(k[1]);
        if (Glyphs.distance(hc, ac) < 0.4) ac = 0x99FFFFFF;
        LinearLayout key = Ui.row(c);
        key.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6));
        key.addView(keyChip(game.homeName, hc, Gravity.START), Ui.weight(1));
        key.addView(keyChip(game.awayName, ac, Gravity.END), Ui.weight(1));
        body.addView(key);
        if (columns < 2) {
            for (String[] st : summary.stats) body.addView(statRow(st, hc, ac));
            return;
        }
        // Across a wide panel: the stats side by side, row by row.
        LinearLayout line = null;
        for (int i = 0; i < summary.stats.size(); i++) {
            if (i % columns == 0) {
                line = Ui.row(c);
                line.setGravity(Gravity.TOP);
                body.addView(line, Ui.matchWrap());
            }
            LinearLayout.LayoutParams lp = Ui.weight(1);
            if (i % columns > 0) lp.leftMargin = Ui.dp(c, 28);
            line.addView(statRow(summary.stats.get(i), hc, ac), lp);
        }
        int left = summary.stats.size() % columns;
        for (int i = left; left > 0 && i < columns; i++) line.addView(new View(c), Ui.weight(1));
    }

    private View keyChip(String name, int color, int gravity) {
        LinearLayout r = Ui.row(c);
        r.setGravity(gravity | Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(Ui.fill(c, color, 5));
        TextView t = Ui.text(c, name, 13, c.getColor(R.color.text_soft), true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10));
        if (gravity == Gravity.START) {
            dp.rightMargin = Ui.dp(c, 7);
            r.addView(dot, dp);
            r.addView(t);
        } else {
            dp.leftMargin = Ui.dp(c, 7);
            r.addView(t);
            r.addView(dot, dp);
        }
        return r;
    }

    private View statRow(String[] st, int hc, int ac) {
        LinearLayout r = Ui.column(c);
        r.setPadding(0, Ui.dp(c, 9), 0, Ui.dp(c, 9));
        float h = number(st[1]), a = number(st[2]);
        LinearLayout labels = Ui.row(c);
        TextView left = Ui.text(c, st[1], 16, h >= a ? c.getColor(R.color.text) : c.getColor(R.color.text_soft), h >= a);
        labels.addView(left, Ui.weight(1));
        TextView mid = Ui.text(c, st[0], 13, c.getColor(R.color.text_soft), false);
        mid.setGravity(Gravity.CENTER);
        labels.addView(mid, Ui.weight(2));
        TextView right = Ui.text(c, st[2], 16, a >= h ? c.getColor(R.color.text) : c.getColor(R.color.text_soft), a >= h);
        right.setGravity(Gravity.END);
        labels.addView(right, Ui.weight(1));
        r.addView(labels);
        if (h + a > 0) {
            LinearLayout bar = Ui.row(c);
            View hv = new View(c), av = new View(c);
            hv.setBackground(Ui.fill(c, h >= a ? hc : mix(hc, 0xFF161A4A, 0.45f), 4));
            av.setBackground(Ui.fill(c, a >= h ? ac : mix(ac, 0xFF161A4A, 0.45f), 4));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, Ui.dp(c, 8), Math.max(h, 0.0001f)),
                    ap = new LinearLayout.LayoutParams(0, Ui.dp(c, 8), Math.max(a, 0.0001f));
            hp.rightMargin = Ui.dp(c, 4);
            bar.addView(hv, hp);
            bar.addView(av, ap);
            r.addView(bar, Ui.margins(c, 0, 7, 0, 0));
        }
        r.setContentDescription(st[0] + ": " + game.homeName + " " + st[1] + ", " + game.awayName + " " + st[2]);
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return r;
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
