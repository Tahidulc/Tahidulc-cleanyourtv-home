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
import com.gathin.tvhomephone.R;

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
            case "goal": case "kickoff": case "lineups": return new int[]{0xFF3DDC84, 0xFF06281A};
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
        // The channel's logo on a chip in its own colour, nothing, or black (Behind logos: LogoTiles).
        int[] chip = channelLogo == null ? null : com.gathin.tvhomephone.live.LogoTiles.tileFor(c, channelLogo);
        return draw(a, s, home, away, comp, channelLogo, chip);
    }

    /** The drawing itself, the pictures given (for tests and previews), the channel's logo on the soft glass chip. */
    static Bitmap draw(Events.Alert a, float s, Bitmap homeCrest, Bitmap awayCrest, Bitmap compLogo, Bitmap channelLogo) {
        return draw(a, s, homeCrest, awayCrest, compLogo, channelLogo, null);
    }

    /** As draw, the channel logo's chip in these colours ({top, foot}; {0, 0} none; null the soft glass). */
    static Bitmap draw(Events.Alert a, float s, Bitmap homeCrest, Bitmap awayCrest, Bitmap compLogo, Bitmap channelLogo, int[] chip) {
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
            RectF lr = new RectF(start + tw, lineY - 15 * s, start + tw + lw + 12 * s, lineY + 7 * s);
            if (chip == null) {
                p.setColor(0x26FFFFFF);
                c.drawRoundRect(lr, 6 * s, 6 * s, p);
            } else if (chip[0] != 0 || chip[1] != 0) {
                p.setShader(new LinearGradient(0, lr.top, 0, lr.bottom, chip[0], chip[1], Shader.TileMode.CLAMP));
                c.drawRoundRect(lr, 6 * s, 6 * s, p);
                p.setShader(null);
            }
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

    /** The box's parts across, as the TV's (ScoreBug): the competition, the home crest, the score, the away crest. */
    static final int[] PARTS = {52, 110, 142, 110};
    /**
     * The line under the score, as parts of the box's height, left empty in the drawing: the clock goes there as words
     * (LiveCard). sports_notif_live_row's weights are these: 62 above it, 24 for it, 14 under it.
     */
    static final float CLOCK_TOP = 0.62f, CLOCK_BOTTOM = 0.86f;
    /** The score's size and its baseline, as parts of the box's height (ScoreBug's 26 sp in its 64 dp). */
    static final float SCORE_SIZE = 26 / 64f, SCORE_BASE = 0.525f;

    /** Where the parts meet across a box w pixels wide: 0, then the end of each part, on whole pixels. Pure. */
    static int[] edges(int w) {
        int total = 0;
        for (int part : PARTS) total += part;
        int[] x = new int[PARTS.length + 1];
        int at = 0;
        for (int i = 0; i < PARTS.length; i++) {
            at += PARTS[i];
            x[i + 1] = Math.round(w * at / (float) total);
        }
        return x;
    }

    /**
     * A live game's box for the quiet "Following live" notification, as the app's scores box (ScoreBug) at the
     * notification's width: the competition's logo on navy, the crests on white, the score on the blue middle, and the
     * line under the score left empty. The clock goes there as words laid over the picture (LiveCard), so it can go on
     * second by second without the picture being drawn or sent again. Loads the crests (off the main thread).
     */
    static Bitmap row(Context c, Game g) {
        float s = scale(c);
        int w = Math.round(W * s), h = Math.round(w / ScoreBug.RATIO);
        int[] x = edges(w);
        float pad = h * 9 / 64f;
        Bitmap comp = logo(c, Competitions.logo(g), Math.round(x[1] - 2 * pad), Math.round(h - 2 * pad));
        int crest = Math.round(h * 44 / 64f);
        Bitmap home = logo(c, g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId), crest, crest);
        Bitmap away = logo(c, g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId), crest, crest);
        int[] colours = {c.getColor(R.color.bug_navy_top), c.getColor(R.color.bug_navy_bottom), c.getColor(R.color.bug_blue_top),
                c.getColor(R.color.bug_blue_bottom)};
        return row(g, w, h, colours, comp, home, away);
    }

    /** The drawing itself, the pictures given (for tests and previews): colours {navy top, navy bottom, blue top, blue bottom}. */
    static Bitmap row(Game g, int w, int h, int[] colours, Bitmap comp, Bitmap home, Bitmap away) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        // The box's rounded corners.
        android.graphics.Path round = new android.graphics.Path();
        float r = h * 6 / 64f;
        round.addRoundRect(new RectF(0, 0, w, h), r, r, android.graphics.Path.Direction.CW);
        cv.clipPath(round);
        int[] x = edges(w);
        // The parts meet on whole pixels, unblended, so no seam shows between them.
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, colours[0], colours[1], Shader.TileMode.CLAMP));
        cv.drawRect(x[0], 0, x[1], h, p);
        p.setShader(new LinearGradient(0, 0, 0, h, colours[2], colours[3], Shader.TileMode.CLAMP));
        cv.drawRect(x[2], 0, x[3], h, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        cv.drawRect(x[1], 0, x[2], h, p);
        cv.drawRect(x[3], 0, x[4], h, p);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        // The competition's logo, or its tag when none loads.
        float pad = h * 9 / 64f;
        if (comp != null) {
            fit(cv, comp, new RectF(x[0] + pad, pad, x[1] - pad, h - pad));
        } else {
            t.setTextSize(h * 10 / 64f);
            t.setColor(Color.WHITE);
            String tag = Glyphs.fit(g.league == null ? "" : g.league, t, x[1] - x[0] - pad);
            cv.drawText(tag, (x[0] + x[1]) / 2f, h / 2f + t.getTextSize() * 0.35f, t);
        }
        // The crests on their white tiles, each club's short name where a crest does not load.
        float size = h * 44 / 64f;
        tile(cv, t, home, g.home, (x[1] + x[2]) / 2f, h / 2f, size);
        tile(cv, t, away, g.away, (x[3] + x[4]) / 2f, h / 2f, size);
        // The score; the line under it is the clock's.
        t.setTextSize(h * SCORE_SIZE);
        t.setColor(Color.WHITE);
        cv.drawText(g.score(), (x[2] + x[3]) / 2f, h * SCORE_BASE, t);
        return out;
    }

    /** A crest fitted in a size by size square about (cx, cy), or the club's short name in navy when there is none. */
    private static void tile(Canvas cv, TextPaint t, Bitmap crest, String tag, float cx, float cy, float size) {
        if (crest != null) {
            fit(cv, crest, new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2));
            return;
        }
        t.setTextSize(size * 0.3f);
        t.setColor(0xFF10164A);
        cv.drawText(tag == null ? "" : tag, cx, cy + t.getTextSize() * 0.35f, t);
    }

    // ---- the line-ups ------------------------------------------------------------------------------------------

    /**
     * Both elevens on a pitch across the card, the home side attacking to the right and the away side to the left,
     * each player a dot in his kit's colour with his number and his surname, the clubs and their formations above.
     */
    public static Bitmap lineups(Context c, Game g, Espn.Summary sum) {
        if (g == null || sum == null) return null;
        List<List<Integer>> hr = Formation.rows(sum.homeFormation, sum.homeLineup), ar = Formation.rows(sum.awayFormation, sum.awayLineup);
        if (hr.isEmpty() || ar.isEmpty()) return null;
        float s = scale(c);
        int w = Math.round(W * s), h = Math.round(208 * s);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        p.setColor(0xFF141849);
        cv.drawRoundRect(new RectF(0, 0, w, h), 18 * s, 18 * s, p);
        Bitmap comp = logo(c, Competitions.logo(g), Math.round(18 * s), Math.round(18 * s));
        float x = 14 * s;
        if (comp != null) {
            fit(cv, comp, new RectF(x, 9 * s, x + 18 * s, 27 * s));
            x += 26 * s;
        }
        t.setTypeface(bold);
        t.setTextSize(10.5f * s);
        t.setLetterSpacing(0.1f);
        t.setColor(0xD9E4E6F5);
        String when = java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        cv.drawText(Glyphs.fit("LINE-UPS · KICK-OFF " + when, t, w - x - 14 * s), x, 22 * s, t);
        t.setLetterSpacing(0);
        int[] kits = Glyphs.kits(sum.homeKit, sum.homeKitAlt, g.homeColor, sum.awayKit, sum.awayKitAlt, g.awayColor);
        t.setTextSize(12.5f * s);
        t.setColor(Color.WHITE);
        t.setTextAlign(Paint.Align.LEFT);
        cv.drawText(Glyphs.fit(g.homeName + "  " + sum.homeFormation, t, w / 2f - 20 * s), 14 * s, 44 * s, t);
        t.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(Glyphs.fit(sum.awayFormation + "  " + g.awayName, t, w / 2f - 20 * s), w - 14 * s, 44 * s, t);
        // The pitch.
        RectF pr = new RectF(10 * s, 52 * s, w - 10 * s, h - 10 * s);
        p.setShader(new LinearGradient(pr.left, 0, pr.right, 0, new int[]{0xFF1C6A44, 0xFF175A3A, 0xFF1C6A44}, null, Shader.TileMode.CLAMP));
        cv.drawRoundRect(pr, 12 * s, 12 * s, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f * s);
        p.setColor(0x47FFFFFF);
        cv.drawLine(pr.centerX(), pr.top + 4 * s, pr.centerX(), pr.bottom - 4 * s, p);
        cv.drawCircle(pr.centerX(), pr.centerY(), pr.height() * 0.17f, p);
        float boxW = pr.height() * 0.58f, boxD = pr.width() * 0.13f;
        cv.drawRect(pr.left, pr.centerY() - boxW / 2, pr.left + boxD, pr.centerY() + boxW / 2, p);
        cv.drawRect(pr.right - boxD, pr.centerY() - boxW / 2, pr.right, pr.centerY() + boxW / 2, p);
        p.setStyle(Paint.Style.FILL);
        side(cv, p, t, sum.homeLineup, hr, kits[0], true, pr, s);
        side(cv, p, t, sum.awayLineup, ar, kits[1], false, pr, s);
        return out;
    }

    private static void side(Canvas cv, Paint p, TextPaint t, List<String[]> lineup, List<List<Integer>> rows, int kit, boolean home,
                             RectF pr, float s) {
        int n = rows.size();
        float half = pr.width() / 2f, keeper = 14 * s, last = half - 18 * s;
        float step = n > 1 ? (last - keeper) / (n - 1) : 0;
        int ink = Glyphs.ink(kit);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        for (int r = 0; r < n; r++) {
            List<Integer> row = rows.get(r);
            float along = keeper + step * r;
            float x = home ? pr.left + along : pr.right - along;
            int k = row.size();
            float gap = pr.height() / (k + 1);
            for (int j = 0; j < k; j++) {
                // The side's left to its right: for the side attacking right that is top to bottom.
                int slot = home ? j : k - 1 - j;
                float y = pr.top + gap * (slot + 1) - 4 * s;
                String[] pl = lineup.get(row.get(j));
                p.setColor(0x59000000);
                cv.drawCircle(x, y + s, 8 * s, p);
                p.setColor(kit);
                cv.drawCircle(x, y, 7.5f * s, p);
                t.setTypeface(bold);
                t.setTextAlign(Paint.Align.CENTER);
                t.setTextSize(7.5f * s);
                t.setColor(ink);
                cv.drawText(pl[Espn.P_NUMBER], x, y + 2.7f * s, t);
                t.setTextSize(7.5f * s);
                t.setColor(Color.WHITE);
                t.setShadowLayer(2 * s, 0, s, 0xB0000000);
                cv.drawText(Glyphs.fit(pl[Espn.P_SHORT], t, Math.min(step > 0 ? step + 6 * s : 60 * s, 54 * s)), x, y + 16 * s, t);
                t.clearShadowLayer();
            }
        }
    }

    // ---- F1 ------------------------------------------------------------------------------------------------------

    /** The podium: the top three on their steps in their teams' colours, each with his photo and name. */
    public static Bitmap podium(Context c, Events.Alert a) {
        String text = a.text == null ? "" : a.text;
        String[] fam = new String[3];
        for (String part : text.split(" · ")) {
            String q = part.trim();
            int sp = q.indexOf(' ');
            if (sp <= 0) continue;
            int pos = Espn.parseInt(q.substring(0, sp));
            if (pos >= 1 && pos <= 3) fam[pos - 1] = q.substring(sp + 1);
        }
        if (fam[0] == null) return null;
        List<F1.Standing> drivers = F1.drivers();
        float s = scale(c);
        int w = Math.round(W * s), h = Math.round(196 * s);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF1C1C26, 0xFF0D0D12}, null, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0, 0, w, h), 18 * s, 18 * s, p);
        p.setShader(null);
        p.setColor(0xFFE10600);
        cv.drawRoundRect(new RectF(14 * s, 12 * s, 46 * s, 30 * s), 4 * s, 4 * s, p);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        t.setTextSize(12 * s);
        t.setColor(Color.WHITE);
        t.setTextAlign(Paint.Align.CENTER);
        cv.drawText("F1", 30 * s, 25.5f * s, t);
        t.setTypeface(bold);
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(10.5f * s);
        t.setLetterSpacing(0.1f);
        t.setColor(0xFFFF8A80);
        cv.drawText("RACE RESULT", 54 * s, 20 * s, t);
        t.setLetterSpacing(0);
        t.setTextSize(13 * s);
        t.setColor(Color.WHITE);
        String race = a.title == null ? "" : a.title.replace("F1 RESULT · ", "");
        cv.drawText(Glyphs.fit(race, t, w - 70 * s), 54 * s, 34 * s, t);
        // The steps: second, first, third.
        int[] order = {1, 0, 2};
        float stepW = (w - 40 * s) / 3f, base = h - 12 * s;
        float[] tall = {58 * s, 42 * s, 32 * s};
        for (int k = 0; k < 3; k++) {
            int place = order[k];
            if (fam[place] == null) continue;
            F1.Standing d = find(drivers, fam[place]);
            int colour = d == null ? 0xFF8A8A99 : F1.colour(d.teamId);
            float left = 20 * s + k * stepW, cx = left + stepW / 2f, top = base - tall[place];
            RectF step = new RectF(left + 3 * s, top, left + stepW - 3 * s, base);
            p.setShader(new LinearGradient(0, top, 0, base, new int[]{colour, mix(colour, 0xFF0D0D12, 0.55f)}, null, Shader.TileMode.CLAMP));
            cv.drawRoundRect(step, 8 * s, 8 * s, p);
            p.setShader(null);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(22 * s);
            t.setColor(Glyphs.ink(colour) == Color.WHITE ? 0xE6FFFFFF : 0xE610142E);
            cv.drawText(String.valueOf(place + 1), cx, top + 25 * s, t);
            float pr = 22 * s, py = top - pr - 16 * s;
            p.setColor(colour);
            cv.drawCircle(cx, py, pr + 2 * s, p);
            p.setColor(0xFF22222C);
            cv.drawCircle(cx, py, pr, p);
            Bitmap photo = d == null || d.headshot == null ? null : logo(c, d.headshot, Math.round(64 * s), Math.round(64 * s));
            if (photo != null) {
                cv.save();
                android.graphics.Path round = new android.graphics.Path();
                round.addCircle(cx, py, pr, android.graphics.Path.Direction.CW);
                cv.clipPath(round);
                float k2 = Math.max(2 * pr / photo.getWidth(), 2 * pr / photo.getHeight());
                float bw = photo.getWidth() * k2, bh = photo.getHeight() * k2;
                cv.drawBitmap(photo, null, new RectF(cx - bw / 2, py - pr, cx + bw / 2, py - pr + bh), null);
                cv.restore();
            }
            t.setTypeface(bold);
            t.setTextSize(11.5f * s);
            t.setColor(Color.WHITE);
            cv.drawText(Glyphs.fit(fam[place].toUpperCase(Locale.UK), t, stepW - 6 * s), cx, top - 4 * s, t);
        }
        return out;
    }

    private static F1.Standing find(List<F1.Standing> drivers, String family) {
        for (F1.Standing d : drivers) if (family.equalsIgnoreCase(d.family)) return d;
        return null;
    }
}
