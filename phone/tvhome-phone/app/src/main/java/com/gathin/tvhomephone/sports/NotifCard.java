package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.LruCache;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

/**
 * The alerts' drawn cards. A game's moment (a goal, a red card, half time, full time, VAR, late drama, kick-off) as a
 * wide card: the competition's logo and name, what happened in its own coloured pill, both crests on white discs with
 * the score between them (the scoring side's number lit), who and when, and the game's 90 minutes as a line with each
 * goal on it. And the live games, one row each, for the quiet "Following live" notification. Off the main thread.
 */
public final class NotifCard {
    private NotifCard() {}

    /** The card's size in dp; drawn at up to 2.75 pixels a dp. */
    static final float W = 340, H = 184, ROW_H = 44;

    private static final LruCache<String, Bitmap> LOGOS = new LruCache<>(24);

    /** What a moment is called on its pill, and the pill's colours {fill, ink}. */
    static String pill(Events.Alert a) {
        String t = a.title == null ? "" : a.title;
        switch (a.kind) {
            case "goal": return "GOAL";
            case "red": return "RED CARD";
            case "ht": return "HALF TIME";
            case "ft": return "FULL TIME";
            case "var": return t.startsWith("PENALTY") ? "PENALTY MISSED" : "VAR";
            case "kickoff": return "KICK-OFF";
            default: {
                int dot = t.indexOf(" · ");
                return (dot > 0 ? t.substring(0, dot) : t).toUpperCase(Locale.ROOT);
            }
        }
    }

    static int[] pillColours(String kind) {
        switch (kind) {
            case "goal": case "kickoff": return new int[]{0xFF3DDC84, 0xFF06281A};
            case "red": return new int[]{Glyphs.RED_CARD, Color.WHITE};
            case "ht": case "var": return new int[]{Glyphs.AMBER, 0xFF2A1A00};
            case "ft": return new int[]{Color.WHITE, 0xFF10164A};
            default: return new int[]{0xFFFFD166, 0xFF2A1A00};
        }
    }

    /** Whether a moment has a card: a game's, and not the line-ups (their own words read better). */
    static boolean drawn(Events.Alert a) {
        return a.game != null && !a.key.startsWith("L|") && !a.kind.startsWith("f1") && !"weather".equals(a.kind)
                && !"reminder".equals(a.kind) && !"table".equals(a.kind);
    }

    /** The goal an alert is about, from its key; null for any other alert. */
    static String[] goal(Events.Alert a) {
        if (a.game == null) return null;
        String k = a.key.startsWith("D|") ? a.key.substring(2) : a.key;
        for (String[] g : a.game.goals) if (g[0].equals(k)) return g;
        return null;
    }

    /** The score just after that goal: {home, away}; the game's score for any other alert. */
    static int[] score(Events.Alert a) {
        Game g = a.game;
        String[] goal = goal(a);
        if (goal == null || !("goal".equals(a.kind) || "drama".equals(a.kind) && a.key.startsWith("D|"))) {
            return new int[]{g.homeScore, g.awayScore};
        }
        int i = g.goals.indexOf(goal);
        int[] b = Events.before(g, i);
        if ("home".equals(goal[3])) b[0]++; else b[1]++;
        return b;
    }

    /** "home", "away" or "" for whose moment it is (the scorer's side, the side sent off). */
    static String side(Events.Alert a) {
        String[] goal = goal(a);
        if (goal != null) return goal[3];
        if ("red".equals(a.kind)) {
            for (String[] r : a.game.reds) if (r[0].equals(a.key)) return r[3];
        }
        if (a.title != null && a.game.awayName != null && a.title.contains(a.game.awayName) && !a.title.contains(a.game.homeName)) {
            return "away";
        }
        if (a.title != null && a.game.homeName != null && a.title.contains(a.game.homeName) && !a.title.contains(a.game.awayName)) {
            return "home";
        }
        return "";
    }

    /** The collapsed notification's title: "GOAL! Man Utd 2–1 Roma", "Red card · Everton", "Half time · AEK 1–1 Real Madrid". */
    static String title(Events.Alert a) {
        Game g = a.game;
        int[] s = score(a);
        String line = g.homeName + " " + s[0] + "–" + s[1] + " " + g.awayName;
        switch (a.kind) {
            case "goal": return "GOAL!  " + line;
            case "red": {
                String sd = side(a);
                return "Red card · " + ("away".equals(sd) ? g.awayName : g.homeName);
            }
            case "ht": return "Half time · " + line;
            case "ft": return "Full time · " + line;
            case "kickoff": return "Kick-off · " + g.homeName + " v " + g.awayName;
            default: {
                String p = pill(a);
                return p.charAt(0) + p.substring(1).toLowerCase(Locale.ROOT) + " · " + line;
            }
        }
    }

    /** The collapsed notification's second line: who and when, then the competition. */
    static String line(Events.Alert a) {
        String[] goal = goal(a);
        String comp = a.game.leagueName == null ? "" : a.game.leagueName;
        if (goal != null && "goal".equals(a.kind)) {
            String club = "home".equals(goal[3]) ? a.game.homeName : a.game.awayName;
            String how = "pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (own goal)" : "";
            return goal[1] + " " + goal[2] + how + " · " + club;
        }
        if ("red".equals(a.kind)) {
            for (String[] r : a.game.reds) if (r[0].equals(a.key)) return r[1] + " " + r[2] + " · " + a.game.homeName + " "
                    + a.game.score() + " " + a.game.awayName;
        }
        return a.text == null ? comp : a.text.replace('\n', ' ');
    }

    // ---- the card -----------------------------------------------------------------------------------------------

    /** A crest, a competition's or a channel's logo, fitted in px by px, kept for the next alert. Null when none loads. */
    static Bitmap logo(Context c, String url, int w, int h) {
        if (url == null || url.isEmpty()) return null;
        String key = url + "@" + w + "x" + h;
        Bitmap b = LOGOS.get(key);
        if (b != null) return b;
        try {
            b = Glide.with(c.getApplicationContext()).asBitmap().load(url).fitCenter().submit(w, h).get(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
        if (b != null) LOGOS.put(key, b);
        return b;
    }

    private static float scale(Context c) {
        return Math.min(c.getResources().getDisplayMetrics().density, 2.75f);
    }

    /** The moment's card; null when it has no game. Loads the crests and the competition's logo (a few seconds at most). */
    public static Bitmap match(Context c, Events.Alert a, Bitmap channelLogo) {
        if (a.game == null) return null;
        float s = scale(c);
        Game g = a.game;
        Bitmap home = logo(c, g.homeLogo, Math.round(36 * s), Math.round(36 * s));
        Bitmap away = logo(c, g.awayLogo, Math.round(36 * s), Math.round(36 * s));
        Bitmap comp = logo(c, Competitions.logo(g), Math.round(18 * s), Math.round(18 * s));
        return draw(a, s, home, away, comp, channelLogo);
    }

    /** The drawing itself, the pictures given (for tests and previews). */
    static Bitmap draw(Events.Alert a, float s, Bitmap homeCrest, Bitmap awayCrest, Bitmap compLogo, Bitmap channelLogo) {
        Game g = a.game;
        int w = Math.round(W * s), h = Math.round(H * s);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD), medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        int homeKit = Glyphs.onDark(Glyphs.color(g.homeColor, 0xFF2A4FD0)), awayKit = Glyphs.onDark(Glyphs.color(g.awayColor, 0xFF8E96C8));
        String side = side(a);
        int navy = 0xFF141849;
        RectF r = new RectF(0, 0, w, h);
        p.setShader(new LinearGradient(0, 0, w, 0, new int[]{mix(navy, homeKit, 0.30f), navy, navy, mix(navy, awayKit, 0.30f)},
                new float[]{0, 0.4f, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 18 * s, 18 * s, p);
        float homeX = 58 * s, awayX = w - 58 * s, crestY = 78 * s;
        if (!side.isEmpty()) {
            float gx = "home".equals(side) ? homeX : awayX;
            int glow = "home".equals(side) ? homeKit : awayKit;
            p.setShader(new RadialGradient(gx, crestY, 92 * s, new int[]{(glow & 0x00FFFFFF) | 0x73000000, glow & 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 18 * s, 18 * s, p);
        }
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s);
        p.setColor(0x2EFFFFFF);
        c.drawRoundRect(new RectF(s / 2, s / 2, w - s / 2, h - s / 2), 18 * s, 18 * s, p);
        p.setStyle(Paint.Style.FILL);

        // The competition and the pill.
        float x = 14 * s;
        if (compLogo != null) {
            p.setColor(0x26FFFFFF);
            c.drawRoundRect(new RectF(x, 10 * s, x + 24 * s, 34 * s), 7 * s, 7 * s, p);
            fit(c, compLogo, new RectF(x + 3 * s, 13 * s, x + 21 * s, 31 * s));
            x += 32 * s;
        }
        t.setTypeface(bold);
        t.setTextSize(10.5f * s);
        t.setLetterSpacing(0.1f);
        t.setColor(0xD9E4E6F5);
        String pill = pill(a);
        int[] pc = pillColours(a.kind);
        t.setTextSize(11 * s);
        float pillW = t.measureText(pill) + 22 * s;
        t.setTextSize(10.5f * s);
        String comp = g.leagueName == null ? "" : g.leagueName.toUpperCase(Locale.ROOT);
        comp = Glyphs.fit(comp, t, w - x - pillW - 26 * s);
        c.drawText(comp, x, 26 * s, t);
        RectF pr = new RectF(w - 14 * s - pillW, 10 * s, w - 14 * s, 34 * s);
        p.setColor(pc[0]);
        c.drawRoundRect(pr, 12 * s, 12 * s, p);
        t.setTextSize(11 * s);
        t.setColor(pc[1]);
        t.setTextAlign(Paint.Align.CENTER);
        c.drawText(pill, pr.centerX(), pr.centerY() + 4 * s, t);
        t.setLetterSpacing(0);

        // The crests and the score.
        disc(c, p, t, homeCrest, g.home, homeX, crestY, 27 * s, s);
        disc(c, p, t, awayCrest, g.away, awayX, crestY, 27 * s, s);
        t.setTypeface(bold);
        t.setTextSize(12.5f * s);
        t.setColor(Color.WHITE);
        float nameW = 104 * s;
        c.drawText(Glyphs.fit(g.homeName, t, nameW), homeX, crestY + 44 * s, t);
        c.drawText(Glyphs.fit(g.awayName, t, nameW), awayX, crestY + 44 * s, t);
        boolean pre = "kickoff".equals(a.kind) && g.homeScore + g.awayScore == 0;
        int[] sc = score(a);
        t.setTextSize(38 * s);
        float cx = w / 2f, scoreY = crestY + 10 * s;
        if (pre) {
            t.setTextSize(30 * s);
            c.drawText("0 – 0", cx, scoreY, t);
        } else {
            String hs = String.valueOf(sc[0]), as = String.valueOf(sc[1]), dash = " – ";
            float hw = t.measureText(hs), dw = t.measureText(dash), aw = t.measureText(as);
            float start = cx - (hw + dw + aw) / 2;
            t.setTextAlign(Paint.Align.LEFT);
            boolean lit = "goal".equals(a.kind) || "drama".equals(a.kind) && a.key.startsWith("D|");
            t.setColor(lit && "home".equals(side) ? 0xFF5CF09E : Color.WHITE);
            c.drawText(hs, start, scoreY, t);
            t.setColor(0xB3FFFFFF);
            c.drawText(dash, start + hw, scoreY, t);
            t.setColor(lit && "away".equals(side) ? 0xFF5CF09E : Color.WHITE);
            c.drawText(as, start + hw + dw, scoreY, t);
            t.setTextAlign(Paint.Align.CENTER);
        }
        String clock = clock(a);
        if (!clock.isEmpty()) {
            t.setTypeface(bold);
            t.setTextSize(11 * s);
            float cw = t.measureText(clock) + 16 * s;
            RectF cr = new RectF(cx - cw / 2, scoreY + 10 * s, cx + cw / 2, scoreY + 30 * s);
            p.setColor(0x2EFFFFFF);
            c.drawRoundRect(cr, 10 * s, 10 * s, p);
            t.setColor(Color.WHITE);
            c.drawText(clock, cx, cr.centerY() + 4 * s, t);
        }

        // Who and when.
        float lineY = 146 * s;
        t.setTypeface(medium);
        t.setTextSize(13 * s);
        t.setColor(Color.WHITE);
        if ("ht".equals(a.kind) || "ft".equals(a.kind)) {
            t.setTextSize(12 * s);
            t.setColor(0xE6E4E6F5);
            t.setTextAlign(Paint.Align.LEFT);
            float half = w / 2f - 22 * s;
            String hsc = scorers(g, "home"), asc = scorers(g, "away");
            if (!hsc.isEmpty()) {
                Glyphs.draw(c, Glyphs.GOAL, 20 * s, lineY - 4 * s, 11 * s);
                c.drawText(Glyphs.fit(hsc, t, half - 14 * s), 30 * s, lineY, t);
            }
            if (!asc.isEmpty()) {
                t.setTextAlign(Paint.Align.RIGHT);
                Glyphs.draw(c, Glyphs.GOAL, w - 20 * s, lineY - 4 * s, 11 * s);
                c.drawText(Glyphs.fit(asc, t, half - 14 * s), w - 30 * s, lineY, t);
            }
            if (hsc.isEmpty() && asc.isEmpty()) {
                t.setTextAlign(Paint.Align.CENTER);
                c.drawText("No goals", cx, lineY, t);
            }
            t.setTextAlign(Paint.Align.CENTER);
        } else if ("kickoff".equals(a.kind) && channelLogo != null) {
            t.setTextSize(12 * s);
            t.setColor(0xD9E4E6F5);
            float lw = Math.min(channelLogo.getWidth(), 110 * s), lh = 18 * s;
            String on = "Live on";
            float tw = t.measureText(on) + 8 * s;
            float start = cx - (tw + lw + 12 * s) / 2;
            t.setTextAlign(Paint.Align.LEFT);
            c.drawText(on, start, lineY, t);
            p.setColor(0x26FFFFFF);
            RectF lr = new RectF(start + tw, lineY - 15 * s, start + tw + lw + 12 * s, lineY + 7 * s);
            c.drawRoundRect(lr, 6 * s, 6 * s, p);
            fit(c, channelLogo, new RectF(lr.left + 6 * s, lr.top + 2 * s, lr.right - 6 * s, lr.top + 2 * s + lh));
            t.setTextAlign(Paint.Align.CENTER);
        } else {
            int glyph = "red".equals(a.kind) ? Glyphs.RED : "var".equals(a.kind) ? Glyphs.VAR
                    : "kickoff".equals(a.kind) ? -1 : Glyphs.GOAL;
            String words = who(a);
            t.setTypeface(bold);
            float tw = Math.min(t.measureText(words), w - 70 * s);
            float gw = glyph < 0 ? 0 : glyph == Glyphs.VAR ? 26 * s : 18 * s;
            float start = cx - (tw + gw) / 2;
            if (glyph >= 0) Glyphs.draw(c, glyph, start + gw / 2 - 3 * s, lineY - 4.5f * s, 13 * s);
            t.setTextAlign(Paint.Align.LEFT);
            c.drawText(Glyphs.fit(words, t, w - 70 * s), start + gw, lineY, t);
            t.setTextAlign(Paint.Align.CENTER);
        }

        // The 90 minutes, each goal on them.
        timeline(c, p, a, homeKit, awayKit, 22 * s, w - 22 * s, 163 * s, s);
        return out;
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** A crest on its white disc (its short name in the side's place when there is no crest). */
    private static void disc(Canvas c, Paint p, TextPaint t, Bitmap crest, String tag, float x, float y, float r, float s) {
        p.setColor(0x40000000);
        c.drawCircle(x, y + 1.5f * s, r + s, p);
        p.setColor(Color.WHITE);
        c.drawCircle(x, y, r, p);
        if (crest != null) {
            float in = r * 0.7f;
            fit(c, crest, new RectF(x - in, y - in, x + in, y + in));
        } else {
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(12 * s);
            t.setColor(0xFF10164A);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(tag == null ? "" : tag, x, y + 4 * s, t);
        }
    }

    /** A picture fitted in a box, centred, its proportions kept. */
    private static void fit(Canvas c, Bitmap b, RectF box) {
        float k = Math.min(box.width() / b.getWidth(), box.height() / b.getHeight());
        float bw = b.getWidth() * k, bh = b.getHeight() * k;
        RectF dst = new RectF(box.centerX() - bw / 2, box.centerY() - bh / 2, box.centerX() + bw / 2, box.centerY() + bh / 2);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(b, null, dst, p);
    }

    /** The clock under the score: the goal's minute, HT, FT, or the game's clock. */
    static String clock(Events.Alert a) {
        String[] goal = goal(a);
        if (goal != null) return goal[2];
        switch (a.kind) {
            case "ht": return "HT";
            case "ft": return "FT";
            case "kickoff": return java.time.Instant.ofEpochMilli(a.game.kickoff).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            case "red":
                for (String[] r : a.game.reds) if (r[0].equals(a.key)) return r[2];
                return a.game.detail == null ? "" : a.game.detail;
            default: return a.game.detail == null ? "" : a.game.detail;
        }
    }

    /** Who did it, under the score: "Matheus Cunha 76' (pen)", "Koita 90'+5' · AEK Athens", or the alert's words. */
    static String who(Events.Alert a) {
        String[] goal = goal(a);
        if (goal != null) {
            // The minute is under the score.
            String how = "pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (own goal)" : "";
            return goal[1] + how;
        }
        if ("red".equals(a.kind)) {
            for (String[] r : a.game.reds) {
                if (r[0].equals(a.key)) return r[1] + " · " + ("home".equals(r[3]) ? a.game.homeName : a.game.awayName);
            }
        }
        if ("kickoff".equals(a.kind)) return a.game.venue == null ? "" : a.game.venue;
        String text = a.text == null ? "" : a.text, title = a.title == null ? "" : a.title;
        int td = title.indexOf(" · ");
        String after = td > 0 ? title.substring(td + 3) : "";
        if (a.key.startsWith("C|")) return after;
        if ("milestone".equals(a.kind)) {
            int dd = text.indexOf(" · ");
            return after + (dd > 0 ? " · " + text.substring(0, dd) : "");
        }
        int dot = text.indexOf(" · ");
        return dot > 0 && !"var".equals(a.kind) ? text.substring(0, dot) : text;
    }

    /** A side's scorers in order: "Koita 12', Pilios 75'". */
    static String scorers(Game g, String side) {
        StringBuilder b = new StringBuilder();
        for (String[] goal : g.goals) {
            if (!side.equals(goal[3])) continue;
            if (b.length() > 0) b.append(", ");
            String n = goal[1];
            int sp = n.lastIndexOf(' ');
            b.append(sp > 0 && n.length() - sp > 3 ? n.substring(sp + 1) : n).append(' ').append(goal[2]);
            if ("pen".equals(goal[4])) b.append(" (p)");
            if ("og".equals(goal[4])) b.append(" (og)");
        }
        return b.toString();
    }

    private static void timeline(Canvas c, Paint p, Events.Alert a, int homeKit, int awayKit, float x0, float x1, float y, float s) {
        Game g = a.game;
        float span = x1 - x0;
        int now = "ft".equals(a.kind) ? 90 : "ht".equals(a.kind) ? 45 : Math.min(90, Math.max(0, Game.minute(g.detail)));
        String[] mine = goal(a);
        if (mine != null) now = Math.max(now, Math.min(90, Game.minute(mine[2])));
        if ("kickoff".equals(a.kind)) now = 0;
        p.setColor(0x26FFFFFF);
        c.drawRoundRect(new RectF(x0, y - 1.5f * s, x1, y + 1.5f * s), 2 * s, 2 * s, p);
        if (now > 0) {
            p.setColor(0x66FFFFFF);
            c.drawRoundRect(new RectF(x0, y - 1.5f * s, x0 + span * now / 90f, y + 1.5f * s), 2 * s, 2 * s, p);
        }
        p.setColor(0x80FFFFFF);
        c.drawRect(x0 + span / 2 - 0.75f * s, y - 4 * s, x0 + span / 2 + 0.75f * s, y + 4 * s, p);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(8.5f * s);
        t.setColor(0x99FFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextAlign(Paint.Align.LEFT);
        c.drawText("KO", x0, y + 13 * s, t);
        t.setTextAlign(Paint.Align.CENTER);
        c.drawText("HT", x0 + span / 2, y + 13 * s, t);
        t.setTextAlign(Paint.Align.RIGHT);
        c.drawText("90'", x1, y + 13 * s, t);
        for (String[] goal : g.goals) {
            int m = Math.min(90, Math.max(1, Game.minute(goal[2])));
            float gx = x0 + span * m / 90f;
            boolean it = mine != null && goal[0].equals(mine[0]);
            int col = "home".equals(goal[3]) ? homeKit : awayKit;
            float rr = (it ? 5.5f : 4f) * s;
            if (it) {
                p.setColor((col & 0x00FFFFFF) | 0x59000000);
                c.drawCircle(gx, y, rr + 4.5f * s, p);
                p.setColor(Color.WHITE);
                c.drawCircle(gx, y, rr + 1.5f * s, p);
            }
            p.setColor(0xFF141849);
            c.drawCircle(gx, y, rr + 1f * s, p);
            p.setColor(col);
            c.drawCircle(gx, y, rr, p);
        }
    }

    // ---- the live games -----------------------------------------------------------------------------------------

    /** The live games, a row each (four at most): crest, short name, score, short name, crest, the clock. */
    public static Bitmap live(Context c, List<Game> games, int max) {
        int n = Math.min(max, games.size());
        if (n == 0) return null;
        float s = scale(c);
        int w = Math.round(W * s), rowH = Math.round(ROW_H * s), h = rowH * n;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        for (int i = 0; i < n; i++) {
            Game g = games.get(i);
            float top = i * rowH, cy = top + rowH / 2f;
            RectF r = new RectF(0, top + 2 * s, w, top + rowH - 2 * s);
            p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFF1A2466, 0xFF141849}, null, Shader.TileMode.CLAMP));
            cv.drawRoundRect(r, 12 * s, 12 * s, p);
            p.setShader(null);
            Bitmap hc = logo(c, g.homeLogo, Math.round(24 * s), Math.round(24 * s));
            Bitmap ac = logo(c, g.awayLogo, Math.round(24 * s), Math.round(24 * s));
            float mid = w * 0.47f;
            disc(cv, p, t, hc, g.home, 24 * s, cy, 14 * s, s);
            t.setTypeface(bold);
            t.setTextSize(13 * s);
            t.setColor(Color.WHITE);
            t.setTextAlign(Paint.Align.RIGHT);
            float nameW = mid - 34 * s - 48 * s;
            cv.drawText(Glyphs.fit(g.homeName, t, nameW), mid - 34 * s, cy + 5 * s, t);
            String sc = g.homeScore + " – " + g.awayScore;
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(15 * s);
            float sw = t.measureText(sc) + 16 * s;
            p.setColor(0xFF2A4FD0);
            cv.drawRoundRect(new RectF(mid - sw / 2, cy - 12 * s, mid + sw / 2, cy + 12 * s), 7 * s, 7 * s, p);
            cv.drawText(sc, mid, cy + 5.5f * s, t);
            t.setTextAlign(Paint.Align.LEFT);
            t.setTextSize(13 * s);
            float clockX = w - 54 * s;
            float awayW = clockX - (mid + 34 * s) - 34 * s;
            cv.drawText(Glyphs.fit(g.awayName, t, awayW), mid + 34 * s, cy + 5 * s, t);
            disc(cv, p, t, ac, g.away, clockX - 18 * s, cy, 14 * s, s);
            String clock = g.halftime ? "HT" : g.state == Game.POST ? "FT" : g.state == Game.PRE ? "KO" : g.detail;
            t.setTypeface(bold);
            t.setTextSize(11.5f * s);
            t.setColor(g.state == Game.LIVE && !g.halftime ? 0xFF5CF09E : 0xD9FFFFFF);
            t.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(clock == null ? "" : clock, w - 10 * s, cy + 4 * s, t);
        }
        return out;
    }
}
