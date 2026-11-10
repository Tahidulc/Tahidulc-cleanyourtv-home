package com.gathin.tvhomephone.live;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Formation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The starting elevens beside the scoreboard over the picture (ScoreBoard), in the TV's line-up card's style (its
 * LineupCard) and in line with the board, top to top: a navy band down its left (the competition, the club's crest big
 * with its shape under it, the line the page shows), and beside it the players a page at a time on a pale streaked
 * ground, head and shoulders, each with his number above and first name and surname below: the keeper and the
 * defence, then the midfield and the attack; then both elevens on one pitch in their shapes, numbers on their kits'
 * colours. Each page comes in behind a slanted navy wipe, a line along the foot filling as it runs. Drawn, not built of
 * views, so it is light over the playing picture.
 */
final class BoardLineups extends View {
    static final class Player {
        String number = "", first = "", last = "";
        Bitmap photo;
    }

    static final class Team {
        String name = "", formation = "";
        Bitmap crest;
        int kit = 0xFF2A4FD0;
        /** The players by line, the keeper first: {keeper}, the defence, each line of the midfield, the attack. */
        final List<List<Player>> rows = new ArrayList<>();
    }

    static final class Data {
        Team home = new Team(), away = new Team();
        String competition = "";
        Bitmap leagueLogo;
    }

    /** One page: a club's players (and what line they are), or the pitch (team null). */
    private static final class Page {
        Team team;
        List<Player> players;
        String caption = "";
        long ms;
    }

    private static final int NAVY = 0xFF151A63, INK = 0xFF0A0D33, BAND_TOP = 0xFF1B2591, BAND_FOOT = 0xFF0D1360,
            GROUND_TOP = 0xFFF4F5FD, GROUND_FOOT = 0xFFC2C5EE, PITCH_TOP = 0xFF2A35B8, PITCH_FOOT = 0xFF080B3A;
    static final long PAGE_MS = 3600L, PITCH_MS = 6000L, TURN_MS = 380L;

    private final List<Page> pages = new ArrayList<>();
    private Data data;
    private int at;
    /** The turn to this page (0 the last, 1 this one); how far this page has run (0 to 1, its line along the foot). */
    private float turn = 1f, ran;
    private ValueAnimator turning, running;
    private Runnable done;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), streak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path(), card = new Path();
    private final Runnable next = this::next;

    BoardLineups(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setVisibility(GONE);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Pure: its width for a height: as the TV's card is shaped. */
    static int widthFor(int h) {
        return Math.round(h * 2.95f);
    }

    /** Pure: how long it shows these elevens, page by page. */
    static long showFor(Data d) {
        long all = 0;
        for (Page p : pages(d)) all += p.ms + TURN_MS;
        return all;
    }

    /** In with these elevens, page after page; then() once the last has run and it has gone. */
    void show(Data d, Runnable then) {
        removeCallbacks(next);
        data = d;
        pages.clear();
        pages.addAll(pages(d));
        done = then;
        if (pages.isEmpty()) {
            finish();
            return;
        }
        at = 0;
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationX(-12 * dp);
        animate().alpha(1f).translationX(0f).setDuration(320).start();
        page(0);
    }

    /** Previews: shown at once on page i, this far through it. */
    void showNow(Data d, int i, float through) {
        removeCallbacks(next);
        data = d;
        pages.clear();
        pages.addAll(pages(d));
        at = Math.max(0, Math.min(i, pages.size() - 1));
        turn = 1f;
        ran = through;
        setAlpha(1f);
        setVisibility(VISIBLE);
        invalidate();
    }

    /** Away at once (the board going, the small window). */
    void gone() {
        removeCallbacks(next);
        if (turning != null) turning.cancel();
        if (running != null) running.cancel();
        animate().cancel();
        setVisibility(GONE);
        data = null;
        done = null;
    }

    boolean isOut() {
        return getVisibility() == VISIBLE;
    }

    /** Closed at once (Back): out as it goes at its end, what waited for it then coming. */
    void close() {
        if (!isOut()) return;
        removeCallbacks(next);
        if (turning != null) turning.cancel();
        if (running != null) running.cancel();
        animate().cancel();
        animate().alpha(0f).translationX(-12 * dp).setDuration(220).withEndAction(this::finish).start();
    }

    private void page(int i) {
        at = i;
        if (turning != null) turning.cancel();
        if (running != null) running.cancel();
        turn = i == 0 ? 1f : 0f;
        ran = 0f;
        if (i > 0) {
            ValueAnimator t = ValueAnimator.ofFloat(0f, 1f);
            t.setDuration(TURN_MS);
            t.addUpdateListener(a -> {
                turn = (float) a.getAnimatedValue();
                invalidate();
            });
            turning = t;
            t.start();
        }
        ValueAnimator run = ValueAnimator.ofFloat(0f, 1f);
        run.setDuration(pages.get(i).ms);
        run.setStartDelay(i > 0 ? TURN_MS : 0);
        run.setInterpolator(null);
        run.addUpdateListener(a -> {
            ran = (float) a.getAnimatedValue();
            invalidate();
        });
        running = run;
        run.start();
        postDelayed(next, pages.get(i).ms + (i > 0 ? TURN_MS : 0));
    }

    private void next() {
        if (data == null) return;
        if (at + 1 < pages.size()) {
            page(at + 1);
            return;
        }
        animate().alpha(0f).translationX(-12 * dp).setDuration(300).withEndAction(this::finish).start();
    }

    private void finish() {
        setVisibility(GONE);
        data = null;
        Runnable then = done;
        done = null;
        if (then != null) then.run();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(next);
        if (turning != null) turning.cancel();
        if (running != null) running.cancel();
    }

    // ---- what it shows ---------------------------------------------------------------------------------------------

    /** Pure: the pages for these elevens: each club's keeper and defence, then its midfield and attack; both on the pitch last. */
    private static List<Page> pages(Data d) {
        List<Page> out = new ArrayList<>();
        if (d == null) return out;
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.rows.isEmpty()) continue;
            List<List<Player>> rows = t.rows;
            List<Player> back = new ArrayList<>(), rest = new ArrayList<>();
            back.addAll(rows.get(0));
            if (rows.size() > 1) back.addAll(rows.get(1));
            for (int i = 2; i < rows.size(); i++) rest.addAll(rows.get(i));
            if (rows.size() <= 2 && back.size() > 6) {
                // No lines known: the eleven in two halves.
                rest.addAll(back.subList(6, back.size()));
                back = new ArrayList<>(back.subList(0, 6));
            }
            out.add(page(t, back, rows.size() > 2 ? "KEEPER · DEFENCE" : ""));
            if (rest.size() > 6) {
                // A long midfield and attack: the midfield, then the attack.
                List<Player> attack = new ArrayList<>(rows.get(rows.size() - 1)), mid = new ArrayList<>(rest);
                mid.removeAll(attack);
                out.add(page(t, mid, "MIDFIELD"));
                out.add(page(t, attack, "ATTACK"));
            } else if (!rest.isEmpty()) {
                out.add(page(t, rest, "MIDFIELD · ATTACK"));
            }
        }
        if (d.home != null && d.away != null && !d.home.rows.isEmpty() && !d.away.rows.isEmpty()) {
            Page p = new Page();
            p.ms = PITCH_MS;
            out.add(p);
        }
        return out;
    }

    private static Page page(Team t, List<Player> ps, String caption) {
        Page p = new Page();
        p.team = t;
        p.players = ps;
        p.caption = caption;
        p.ms = PAGE_MS;
        return p;
    }

    /**
     * Pure: a side's eleven from ESPN's line-up (Espn.Summary's rows) in its lines, as Formation gives them (the keeper,
     * then each line from the back); its first names and surnames.
     */
    static List<List<Player>> rows(String formation, List<String[]> lineup) {
        List<List<Player>> out = new ArrayList<>();
        for (List<Integer> row : Formation.rows(formation, lineup)) {
            List<Player> line = new ArrayList<>();
            for (int i : row) line.add(player(lineup.get(i)));
            out.add(line);
        }
        return out;
    }

    /** Pure: one of ESPN's line-up rows as a player: his number, first names and surname. */
    static Player player(String[] row) {
        Player p = new Player();
        p.number = row[Espn.P_NUMBER] == null ? "" : row[Espn.P_NUMBER];
        String full = row[Espn.P_NAME] == null ? "" : row[Espn.P_NAME].trim();
        String last = row[Espn.P_SHORT] == null || row[Espn.P_SHORT].isEmpty() ? full : row[Espn.P_SHORT].trim();
        p.last = last;
        p.first = full.endsWith(last) && full.length() > last.length() ? full.substring(0, full.length() - last.length()).trim() : "";
        return p;
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || data == null || pages.isEmpty()) return;
        float rad = Math.min(10 * dp, h * 0.07f), band = Math.round(h * 0.36f);
        r.set(0, 0, w, h);
        card.reset();
        card.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(card);
        Page p = pages.get(Math.min(at, pages.size() - 1));
        // The stage, the page before it under the slanted wipe as this one comes in.
        cv.save();
        cv.clipRect(band, 0, w, h);
        if (turn < 1f && at > 0) {
            stage(cv, pages.get(at - 1), band, w, h);
            float e = BoardSide.ease(turn), slant = h * 0.32f;
            float x = w + slant - (w - band + 2 * slant) * e;
            path.reset();
            path.moveTo(x, 0);
            path.lineTo(w + slant * 2, 0);
            path.lineTo(w + slant * 2, h);
            path.lineTo(x - slant, h);
            path.close();
            cv.save();
            cv.clipPath(path);
            stage(cv, p, band, w, h);
            cv.restore();
            // The wipe's own navy edge.
            fill.setShader(null);
            fill.setColor(NAVY);
            path.reset();
            path.moveTo(x, 0);
            path.lineTo(x + h * 0.12f, 0);
            path.lineTo(x + h * 0.12f - slant, h);
            path.lineTo(x - slant, h);
            path.close();
            cv.drawPath(path, fill);
        } else {
            stage(cv, p, band, w, h);
        }
        // The line along the foot, filling as the page runs.
        fill.setShader(null);
        fill.setColor(p.team == null ? 0x66FFFFFF : 0x33151A63);
        float lh = Math.max(2f, h * 0.018f);
        cv.drawRect(band, h - lh, w, h, fill);
        fill.setColor(p.team == null ? 0xFFFFFFFF : NAVY);
        cv.drawRect(band, h - lh, band + (w - band) * ran, h, fill);
        cv.restore();
        band(cv, p, band, h);
        cv.restore();
    }

    /** A ground shaded top to foot with slanted streaks across it, as the TV card's are. */
    private void ground(Canvas cv, float l, float t, float rr, float b, int top, int foot, int streakColour, float gap, float width) {
        fill.setShader(new LinearGradient(0, t, 0, b, top, foot, Shader.TileMode.CLAMP));
        cv.drawRect(l, t, rr, b, fill);
        fill.setShader(null);
        if (streakColour == 0) return;
        streak.setColor(streakColour);
        float slant = (b - t) * 0.36f;
        for (float x = l - slant; x < rr + slant; x += gap + width) {
            path.reset();
            path.moveTo(x, b);
            path.lineTo(x + width, b);
            path.lineTo(x + width + slant, t);
            path.lineTo(x + slant, t);
            path.close();
            cv.drawPath(path, streak);
        }
    }

    /** The band down the left: the competition, the club's crest and shape and the page's line; for the pitch, both crests. */
    private void band(Canvas cv, Page p, float band, int h) {
        ground(cv, 0, 0, band, h, BAND_TOP, BAND_FOOT, 0x12FFFFFF, h * 0.09f, h * 0.013f);
        float mid = band / 2f;
        if (data.leagueLogo != null) image(cv, data.leagueLogo, mid - h * 0.07f, h * 0.05f, h * 0.14f);
        if (p.team == null) {
            float cs = h * 0.26f;
            image(cv, data.home.crest, mid - cs / 2f, h * 0.22f, cs);
            words(cv, data.home.formation, mid, h * 0.22f + cs + h * 0.02f, h * 0.09f, h * 0.075f, 0xFFFFFFFF, Paint.Align.CENTER, 0.08f);
            image(cv, data.away.crest, mid - cs / 2f, h * 0.6f, cs);
            words(cv, data.away.formation, mid, h * 0.6f + cs + h * 0.02f, h * 0.09f, h * 0.075f, 0xFFFFFFFF, Paint.Align.CENTER, 0.08f);
            return;
        }
        float cs = h * 0.34f;
        image(cv, p.team.crest, mid - cs / 2f, h * 0.24f, cs);
        if (p.team.crest == null) words(cv, initials(p.team.name), mid, h * 0.24f, cs, h * 0.16f, 0xFFFFFFFF, Paint.Align.CENTER, 0f);
        words(cv, p.team.formation, mid, h * 0.24f + cs + h * 0.03f, h * 0.1f, h * 0.085f, 0xFFFFFFFF, Paint.Align.CENTER, 0.08f);
        if (!p.caption.isEmpty()) {
            float size = fit(p.caption, band * 0.9f, h * 0.06f, 0.06f);
            words(cv, p.caption, mid, h * 0.82f, h * 0.1f, size, 0xD9FFFFFF, Paint.Align.CENTER, 0.06f);
        }
    }

    /** The stage beside the band: a page of players on the pale ground, or both elevens on the pitch. */
    private void stage(Canvas cv, Page p, float band, int w, int h) {
        if (p.team == null) {
            pitch(cv, band, w, h);
            return;
        }
        ground(cv, band, 0, w, h, GROUND_TOP, GROUND_FOOT, 0x8CFFFFFF, h * 0.37f, h * 0.155f);
        int n = Math.max(1, p.players.size());
        float pad = h * 0.03f, cell = (w - band - 2 * pad) / n;
        for (int i = 0; i < p.players.size(); i++) player(cv, p.team, p.players.get(i), band + pad + i * cell, cell, h);
    }

    /** One player: his number above at the left, head and shoulders, his first name and surname below. */
    private void player(Canvas cv, Team t, Player pl, float x, float cw, int h) {
        float top = h * 0.13f, ph = h * 0.56f, pw = Math.min(cw * 0.96f, ph * 1.05f);
        Bitmap b = pl.photo != null ? pl.photo : avatar(t.kit);
        if (b != null) {
            float s = Math.min(pw / b.getWidth(), ph / b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            r.set(x + (cw - bw) / 2f, top + ph - bh, x + (cw + bw) / 2f, top + ph);
            image.setAlpha(255);
            cv.drawBitmap(b, null, r, image);
        }
        words(cv, pl.number, x + cw * 0.08f, h * 0.02f, h * 0.14f, h * 0.11f, NAVY, Paint.Align.LEFT, 0f);
        float fs = fit(pl.first, cw * 0.94f, h * 0.065f, 0f);
        words(cv, pl.first, x + cw / 2f, top + ph + h * 0.005f, h * 0.09f, fs, 0xFF2B3170, Paint.Align.CENTER, 0f);
        float ls = fit(pl.last, cw * 0.96f, h * 0.085f, 0f);
        words(cv, pl.last, x + cw / 2f, top + ph + h * 0.085f, h * 0.12f, ls, INK, Paint.Align.CENTER, 0f);
    }

    /** Both elevens on one pitch, home from the left, away from the right, each player his number on his kit and his surname. */
    private void pitch(Canvas cv, float band, int w, int h) {
        ground(cv, band, 0, w, h, PITCH_TOP, PITCH_FOOT, 0x0EFFFFFF, h * 0.26f, h * 0.017f);
        float l = band + h * 0.05f, rr = w - h * 0.05f, t = h * 0.06f, b = h * 0.94f;
        fill.setShader(null);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(1f, h * 0.008f));
        fill.setColor(0x40FFFFFF);
        r.set(l, t, rr, b);
        cv.drawRect(r, fill);
        float cx = (l + rr) / 2f;
        cv.drawLine(cx, t, cx, b, fill);
        cv.drawCircle(cx, (t + b) / 2f, (b - t) * 0.16f, fill);
        fill.setStyle(Paint.Style.FILL);
        side(cv, data.home, l, cx, t, b, false);
        side(cv, data.away, cx, rr, t, b, true);
    }

    /** One side's lines across its half: the keeper at its goal, each line on from it, each player in his own room. */
    private void side(Canvas cv, Team team, float from, float to, float t, float b, boolean mirrored) {
        int lines = team.rows.size();
        if (lines == 0) return;
        int most = 1;
        for (List<Player> row : team.rows) most = Math.max(most, row.size());
        float span = to - from, slotH = (b - t) / most, colW = span / (lines + 0.2f);
        float dot = Math.min(Math.min(slotH * 0.27f, colW * 0.3f), (b - t) * 0.09f);
        int ink = OverlayBug.ink(team.kit);
        for (int i = 0; i < lines; i++) {
            List<Player> row = team.rows.get(i);
            float frac = (i + 0.5f) / (lines + 0.2f), x = mirrored ? to - span * frac : from + span * frac;
            for (int j = 0; j < row.size(); j++) {
                // The dot and the name under it, the two together in the middle of his room.
                float mid = t + (b - t) * (j + 0.5f) / row.size(), y = mid - dot * 0.55f;
                fill.setColor(team.kit | 0xFF000000);
                cv.drawCircle(x, y, dot, fill);
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(Math.max(1f, dot * 0.12f));
                fill.setColor(0xCCFFFFFF);
                cv.drawCircle(x, y, dot, fill);
                fill.setStyle(Paint.Style.FILL);
                words(cv, row.get(j).number, x, y - dot, dot * 2, dot * 1.05f, ink, Paint.Align.CENTER, 0f);
                String last = row.get(j).last;
                float ls = fit(last, colW * 0.98f, dot * 0.82f, 0f);
                words(cv, last, x, y + dot * 1.02f, dot * 1.05f, ls, 0xF2FFFFFF, Paint.Align.CENTER, 0f);
            }
        }
    }

    private static String initials(String name) {
        if (name == null) return "";
        String n = name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }

    private Bitmap avatarFor;
    private int avatarKit;

    /** A player with no photo: the figure in his side's colour (as the TV's card has him). */
    private Bitmap avatar(int kit) {
        if (avatarFor != null && avatarKit == kit) return avatarFor;
        Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(kit | 0xFF000000);
        c.drawRoundRect(new RectF(14, 78, 106, 150), 40, 40, p);
        p.setColor(0xFFB9BED6);
        c.drawCircle(60, 48, 28, p);
        avatarFor = b;
        avatarKit = kit;
        return b;
    }

    private void image(Canvas cv, Bitmap b, float x, float y, float size) {
        if (b == null) return;
        float s = size / Math.max(b.getWidth(), b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (size - bw) / 2f, y + (size - bh) / 2f, x + (size + bw) / 2f, y + (size + bh) / 2f);
        image.setAlpha(255);
        cv.drawBitmap(b, null, r, image);
    }

    private float fit(String s, float room, float most, float spacing) {
        if (s == null || s.isEmpty()) return most;
        text.setTextSize(most);
        text.setLetterSpacing(spacing);
        float wd = text.measureText(s);
        text.setLetterSpacing(0f);
        return wd <= room ? most : Math.max(4 * dp, most * room / wd);
    }

    private void words(Canvas cv, String s, float x, float y, float rowH, float size, int colour, Paint.Align align, float spacing) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setLetterSpacing(spacing);
        text.setColor(colour);
        text.setTextAlign(align);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, text);
        text.setLetterSpacing(0f);
    }
}
