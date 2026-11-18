package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.util.List;

/**
 * The player's info card in the 07 look (the approved still 07c): a frosted card 1824 x 322 from x 48, rounded 44, at
 * the foot (FOOT_Y 718: on INFO, after a channel change, and for the channel before or after on CH− and CH+) or at the
 * top (TOP_Y 40: OK's overlay, catch-up's). Over the live picture its material is the plain dark one (Frost: no
 * blurred copy of the picture, and no faded copy of it behind the words either, as the family asked).
 *
 * <pre>
 *   the channel        its logo on its tile (150 x 104, in its own colour: Logos) at (88, +40), its number (26 px, bold)
 *                      and its name (17 px) under it
 *   the kicker         the red LIVE badge (from the archive: the catch-up mark) and where the channel is from ("Home •
 *                      UK Entertainment"), 20 px
 *   the title          44 px bold, one line
 *   its line           "19:30 – 22:00", the bar (200 x 6) and what's left ("40 min left"), then the chips for the
 *                      picture and the sound as the player has them (FHD, 50 FPS, STEREO); from the archive its length
 *   what it is about   21 px, two lines
 *   what's next        at the right, 420 wide: "NEXT · 22:00", its picture (420 x 170, fitted round its faces:
 *                      PicTile), its title (22 px) and times
 * </pre>
 *
 * One card draws for one view (its logos and pictures ask that view to draw again when they come). Lines are laid out
 * once for a programme and kept, and the words a frame writes are kept words (Words) cut from kept widths (TextFit).
 * The pictures are asked for once the card rests on a programme (settle), and the channels either side read ahead then,
 * so holding Up or Down finds them ready.
 */
final class InfoCard {
    static final float X = 48f, W = 1824f, H = 322f, FOOT_Y = 1080f - 40f - 322f, TOP_Y = 40f, RADIUS = 44f;
    static final float LOGO_X = 88f, LOGO_W = 150f, LOGO_H = 104f, TEXT_X = 280f, TEXT_R = 1372f;
    /** What's next: its left, its width, its picture's height. */
    static final float NEXT_X = 1412f, NEXT_W = 420f, NEXT_H = 170f;

    private final View owner;
    private final Runnable redraw;
    private final Frost frost = new Frost(Look07.MAT_DARK);
    private final PicTile next;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(44f, Type.BOLD, Look07.L1), kickP = Type.paint(20f, Type.SEMIBOLD, Look07.L2);
    private final TextPaint metaP = Type.clock(21f, Type.MEDIUM, Look07.L2), descP = Type.paint(21f, Type.REGULAR, Look07.L2);
    private final TextPaint numP = Type.clock(26f, Type.BOLD, Look07.L1), nameP = Type.paint(17f, Type.SEMIBOLD, Look07.L3);
    private final TextPaint chipP = Type.paint(16f, Type.BOLD, Look07.L1), nextLabelP = Type.label(Type.LABEL, Look07.L3);
    private final TextPaint nextTitleP = Type.paint(22f, Type.SEMIBOLD, Look07.L1), nextTimeP = Type.clock(18f, Type.MEDIUM, Look07.L3);
    private final TextPaint logoP = Type.paint(Type.ITEM, Type.BOLD, Look07.L1);
    private final RectF r = new RectF();
    /** Laid out once: what the programme is about (for descKey). */
    private String descKey = "", whereKey = "", whereText = "", nextKey = "", nextLabel = "";
    private long descStart = Long.MIN_VALUE;
    private StaticLayout descL;
    /** The programme it rests on, its picture (and the next one's) asked for once it has rested. */
    private Channel restCh;
    private long restStart = Long.MIN_VALUE;
    private boolean settled;
    private final Runnable settle = this::settle;
    private final TextFit titles = new TextFit(titleP, 64), kicks = new TextFit(kickP, 64), metas = new TextFit(metaP, 64),
            names = new TextFit(nameP, 64), nextTitles = new TextFit(nextTitleP, 64), chips = new TextFit(chipP, 32);

    InfoCard(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        next = new PicTile(owner);
        ring.setStyle(Paint.Style.STROKE);
    }

    /**
     * The card has rested on a programme: what's next's picture asked for, and the programmes on now on the channels
     * either side of it read ahead (Up, Down, CH+ and CH− go there next).
     */
    private void settle() {
        if (restCh == null) return;
        settled = true;
        owner.invalidate();
        Library lib = Library.get(owner.getContext());
        long now = System.currentTimeMillis();
        for (int d = -1; d <= 1; d += 2) {
            Channel n = lib.step(restCh, d);
            if (n == null || n.key.equals(restCh.key)) continue;
            Guide.Schedule ns = lib.schedule(n.key);
            int i = ns == null ? -1 : ns.at(now);
            if (i >= 0 && !com.gathin.homefieldtv.engine.Exec.inline) ProgrammePictures.get(owner.getContext(), n, ns, i + 1, null);
        }
    }

    /**
     * The card with its top at top (design pixels) for channel ch: the programme on at time at (now when live; the
     * picture's time from the archive), the archive's look when archived, the chips given (none for another channel).
     */
    void draw(Canvas c, float top, Channel ch, long at, boolean archived, List<String> chipWords) {
        if (ch == null) return;
        Context ctx = owner.getContext();
        long now = System.currentTimeMillis();
        r.set(Ui.f(X), Ui.f(top), Ui.f(X + W), Ui.f(top + H));
        frost.draw(c, r, Ui.f(RADIUS), 1f);
        Guide.Schedule s = Library.get(ctx).schedule(ch.key);
        int i = s == null ? -1 : s.at(at);
        long st = i >= 0 ? s.start[i] : Long.MIN_VALUE;
        if (!ch.key.equals(restCh == null ? "" : restCh.key) || st != restStart) {
            restCh = ch;
            restStart = st;
            settled = com.gathin.homefieldtv.engine.Exec.inline;
            owner.removeCallbacks(settle);
            owner.postDelayed(settle, ProgrammePictures.REST_MS);
        }

        // The channel: its logo on its tile, its number and name under it.
        float lx = Ui.f(LOGO_X), ly = Ui.f(top + 40f);
        r.set(lx, ly, lx + Ui.f(LOGO_W), ly + Ui.f(LOGO_H));
        if (!Logos.draw(c, ch, r, redraw)) {
            String in = initials(ChannelEdits.name(ch));
            c.drawText(in, r.centerX() - logoP.measureText(in) / 2f, Type.centred(logoP, r.centerY()), logoP);
        }
        float mid = Ui.f(LOGO_X + LOGO_W / 2f);
        int n = Library.get(ctx).number(ch);
        if (n > 0) {
            String num = Words.number(n);
            c.drawText(num, mid - numP.measureText(num) / 2f, Ui.f(top + 183f), numP);
        }
        String name = ChannelEdits.name(ch);
        float nw = Math.min(names.width(name), Ui.f(LOGO_W + 60f));
        names.line(c, name, mid - nw / 2f, Ui.f(top + 207f), Ui.f(LOGO_W + 60f));

        // The kicker: LIVE (or the archive's mark) and where the channel is from.
        float x0 = Ui.f(TEXT_X), right = Ui.f(TEXT_R), kcy = Ui.f(top + 55f), x = x0;
        if (archived) {
            Icons07.draw(c, Icons07.CATCHUP, x + Ui.f(11f), kcy, Ui.f(22f), Look07.AMBER);
            x += Ui.f(22f + 12f);
        } else if (i >= 0 && s.start[i] <= now && now < s.stop[i]) {
            x += Pills.live(c, x, kcy) + Ui.f(12f);
        }
        String where = where(ctx, ch);
        if (!where.isEmpty()) kicks.line(c, where, x, Type.centred(kickP, kcy), right - x);

        // The title.
        titles.line(c, i >= 0 ? s.title[i] : name, x0, Ui.f(top + 119f), right - x0);

        // Its line: the times, how far through and what's left (or the archive's length), the chips.
        float cy = Ui.f(top + 157f), base = Type.centred(metaP, cy), gap = Ui.f(16f);
        x = x0;
        if (i >= 0) {
            String span = Words.span(s.start[i], s.stop[i]);
            c.drawText(span, x, base, metaP);
            x += metas.width(span) + gap;
            if (archived) {
                Icons07.draw(c, Icons07.CATCHUP, x + Ui.f(10f), cy, Ui.f(20f), Look07.L2);
                x += Ui.f(20f) + Ui.f(10f);
                String len = Words.minutes(s.stop[i] - s.start[i]);
                c.drawText(len, x, base, metaP);
                x += metas.width(len) + gap;
            } else if (s.start[i] <= now && now < s.stop[i]) {
                if (com.gathin.homefieldtv.prefs.Look.progress) {
                    Pills.progress(c, x, cy, Ui.f(200f), LiveText.progress(now, s.start[i], s.stop[i]));
                    x += Ui.f(200f) + gap;
                }
                String left = leftWords(s.stop[i] - now);
                c.drawText(left, x, base, metaP);
                x += metas.width(left) + gap;
            }
        } else {
            String none = s == null ? "No programme information" : "Nothing in the guide now";
            c.drawText(none, x, base, metaP);
            x += metas.width(none) + gap;
        }
        if (chipWords != null) {
            for (String w : chipWords) {
                if (w == null || w.isEmpty()) continue;
                float cw = chips.width(w) + Ui.f(20f);
                if (x + cw > right) break;
                drawChip(c, x, cy, w, cw);
                x += cw + Ui.f(10f);
            }
        }

        // What it is about, two lines.
        if (i >= 0 && !s.desc[i].isEmpty()) {
            if (!ch.key.equals(descKey) || s.start[i] != descStart || descL == null) {
                descL = lines(s.desc[i], descP, Ui.px(TEXT_R - TEXT_X), 2, 1.42f / 1.21f);
                descKey = ch.key;
                descStart = s.start[i];
            }
            Ui.draw(c, descL, x0, Ui.f(top + 190f));
        }

        // What's next: its time, its picture fitted round its faces, its title and times.
        int nx = s == null ? -1 : i >= 0 ? (i + 1 < s.start.length ? i + 1 : -1) : s.after(at);
        if (nx >= 0) drawNext(c, top, ch, s, nx);
    }

    /** NEXT · 22:00, its picture (420 x 170, faces whole: PicTile), its title and times. */
    private void drawNext(Canvas c, float top, Channel ch, Guide.Schedule s, int n) {
        String key = ch.key + "|" + s.start[n];
        if (!key.equals(nextKey)) {
            nextKey = key;
            nextLabel = "NEXT · " + LiveText.hm(s.start[n]);
        }
        float x = Ui.f(NEXT_X);
        c.drawText(nextLabel, x, Ui.f(top + 50f), nextLabelP);
        r.set(x, Ui.f(top + 64f), x + Ui.f(NEXT_W), Ui.f(top + 64f + NEXT_H));
        float rad = Ui.f(20f);
        next.draw(c, r, rad, ch, s, n, PicTile.COVER, false, false, settled);
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x24FFFFFF);
        c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, rad, rad, ring);
        nextTitles.line(c, s.title[n], x, Ui.f(top + 64f + NEXT_H + 33f), Ui.f(NEXT_W));
        c.drawText(Words.span(s.start[n], s.stop[n]), x, Ui.f(top + 64f + NEXT_H + 58f), nextTimeP);
    }

    /** A chip on the card: white at 14 % with a hairline at 22 %, 30 high, its letters bold and spaced. */
    private void drawChip(Canvas c, float x, float cy, String w, float cw) {
        float h = Ui.f(30f);
        r.set(x, cy - h / 2f, x + cw, cy + h / 2f);
        fill.setColor(0x24FFFFFF);
        c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), fill);
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x38FFFFFF);
        c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, Ui.f(8f), Ui.f(8f), ring);
        c.drawText(w, x + Ui.f(10f), Type.centred(chipP, cy), chipP);
    }

    /** "40 min left", "1h 10m left": what is left of a programme on now (kept words: GuideGrid.leftWords, by the minute). */
    private String leftWords(long ms) {
        int m = (int) Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m != leftFor) {
            leftFor = m;
            leftText = GuideGrid.leftWords(m);
        }
        return leftText;
    }

    private int leftFor = -1;
    private String leftText = "";

    /** "29 min", "1 h 5 min": what is left of a programme, or its whole length, as the card writes it. */
    static String minutes(long ms) {
        long m = Math.max(1L, (ms + 59_999L) / 60_000L);
        return m + " min";
    }

    /**
     * Where a channel is from: its playlist's name and its group ("Royal • TNT Sports"), as the card and the lists say
     * it; the group alone with one unnamed playlist. Worked out once a channel.
     */
    String where(Context ctx, Channel ch) {
        if (ch.key.equals(whereKey)) return whereText;
        whereKey = ch.key;
        whereText = where(playlistName(ctx, ch), com.gathin.homefieldtv.prefs.Categories.label(ctx, ch.group));
        return whereText;
    }

    /** Pure: "Royal • TNT Sports" from a playlist's name and a group's ("" for either left out). */
    static String where(String playlist, String group) {
        String p = playlist == null ? "" : playlist.trim(), g = group == null ? "" : group.trim();
        if (p.isEmpty()) return g;
        return g.isEmpty() ? p : p + " • " + g;
    }

    /** The name of the playlist a channel is from (its key's prefix), "" when it can't be told. */
    static String playlistName(Context ctx, Channel ch) {
        String id = PlayerPrefs.playlistOf(ch.key);
        for (Sources.Entry e : Sources.list(ctx)) if (e.id.equals(id)) return e.name;
        return "";
    }

    /** A logo's stand-in: the name's first letters ("Kestrel Sports 1" as "KS1"). */
    private static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            b.append(Character.toUpperCase(w.charAt(0)));
            if (b.length() >= 4) break;
        }
        return b.toString();
    }

    /** Lines of text in a width, at most max, an ellipsis on the last; spacing the line height over the size. */
    static StaticLayout lines(CharSequence text, TextPaint p, int width, int max, float spacing) {
        int w = Math.max(1, width);
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(max)
                .build();
    }
}
