package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.Locale;

/**
 * The mini guide over a channel playing, in the 07 look (the approved still 07c): as Up and Down (or CH+ and CH−) go
 * through the channels, a frosted panel at the top right (600 wide, rounded 36: the plain dark material over the live
 * picture) with where the channels are from ("HOME • UK ENTERTAINMENT") and "Change channel" over five channels' rows,
 * the one shown in the middle. Each row is the channel's cell, the logo alone (ChannelLook: on its logo's deep colour,
 * no number, no tile, no name), then its programme: the programme's picture taking up the whole of its part
 * (PicTile.fill, its faces whole), its title and times with what is left over a soft shade at the left, and a white
 * line along its foot for how far through it is (the part plain when it has no picture). The remote's row lifts with
 * 07's white ring; going on a channel slides the rows a row, and the rows' pictures are asked for once it rests.
 * The shown channel's number is the view's content description (accessibility), not drawn. Beside it the box at the
 * top right says which channel is playing ("Watching 102"), and the keys' hints sit over the card at the foot (OK
 * Overlay, ◀ Channel list, ≡ Options).
 *
 * <pre>
 *   MiniGuide mini = new MiniGuide(view);
 *   if (mini.draw(c, channelShown, playing, settled)) postInvalidateOnAnimation();   true while it still moves
 *   mini.watching(c, playing)                     the box at the top right
 *   MiniGuide.hints(c)                            the keys' hints over the card
 * </pre>
 */
final class MiniGuide {
    /** The panel (design px): its right edge's margin, its top, its width; a row's height and pitch; the rows shown. */
    static final float RIGHT = 70f, TOP = 140f, W = 600f, PAD = 18f, HEAD = 40f, ROW = 88f, PITCH = 96f, LIST_H = 470f, R = 36f;
    static final int ROWS = 5;
    /** A row's channel cell (the logo alone) and the gap to its programme; both cells' corners. */
    static final float CH_W = 170f, GAP_X = 8f, R_CELL = 20f;

    private final View owner;
    private final Runnable redraw;
    private final Frost frost = new Frost(Look07.MAT_DARK), box = new Frost(Look07.MAT_DARK);
    private final PicTile tiles;
    private final Lift07 lift = new Lift07();
    /** The rows' slide when the channel shown moves on a row (design px: from a row's pitch back to 0). */
    private final Glide slide = new Glide(Look07.SCROLL_MS);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headP = Type.paint(17f, Type.SEMIBOLD, Look07.L2), titleP = Type.paint(20f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint subP = Type.clock(16f, Type.MEDIUM, 0xD6FFFFFF);
    private final TextPaint watchP = Type.paint(22f, Type.SEMIBOLD, Look07.L2), watchNumP = Type.clock(30f, Type.BOLD, Look07.L1);
    private final TextFit titles = new TextFit(titleP, 64), subs = new TextFit(subP, 64), heads = new TextFit(headP, 16);
    private final RectF r = new RectF(), o = new RectF();
    /** The channels in the rows (the shown one in the middle), and the ones a row further either way while sliding. */
    private final Channel[] rows = new Channel[ROWS + 2];
    private Channel shown;
    private String headKey = "", headText = "";
    /** Each row's second line ("19:00 – 20:10 · 40 min left"), kept by channel and minute. */
    private final Channel[] subFor = new Channel[ROWS + 2];
    private final long[] subMinute = new long[ROWS + 2];
    private final String[] subText = new String[ROWS + 2];
    private final long[] subStart = new long[ROWS + 2];
    private int subNext;

    MiniGuide(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        tiles = new PicTile(owner);
        ring.setStyle(Paint.Style.STROKE);
        headP.setLetterSpacing(0.04f);
        Shadows.prepare(Look07.SH_CHANNEL, 22f);
        Shadows.prepare(Look07.SH_CHANNEL_UP, 22f);
        Shadows.prepare(Look07.GLOW_CHANNEL, 22f);
    }

    /** Where the panel is (design px). */
    static float left() {
        return 1920f - RIGHT - W;
    }

    /**
     * The panel for the channel shown (the one playing, or another on CH+ and CH−): its rows around it. settled: the
     * remote has rested (the rows' pictures may be asked for). True while it still moves (draw again next frame).
     */
    boolean draw(Canvas c, Channel ch, Channel playing, boolean settled) {
        if (ch == null) return false;
        Library lib = Library.get(owner.getContext());
        if (shown != ch) {
            // A row on (or back): the rows slide by one; further: in place at once.
            int dir = shown == null ? 0 : lib.step(shown, 1) == ch ? 1 : lib.step(shown, -1) == ch ? -1 : 0;
            shown = ch;
            fillRows(lib, ch);
            owner.setContentDescription(GuideGrid.describe(lib.number(ch), ChannelEdits.name(ch), ""));
            if (dir != 0 && !Exec.inline) {
                slide.jump(dir * PITCH);
                slide.to(0f);
            } else {
                slide.jump(0f);
            }
        }
        boolean moving = slide.step() | lift.step(ch);
        float x0 = left(), top = TOP, h = PAD + HEAD + LIST_H + PAD;
        r.set(Ui.f(x0), Ui.f(top), Ui.f(x0 + W), Ui.f(top + h));
        frost.draw(c, r, Ui.f(R), 1f);
        // Where they are from, and Change channel.
        String head = head(ch);
        float hy = Ui.f(top + PAD + 6f + 14f);
        float rightX = Ui.f(x0 + W - PAD - 12f);
        String change = "Change channel";
        float cw = headP.measureText(change);
        heads.line(c, head, Ui.f(x0 + PAD + 12f), hy, rightX - cw - Ui.f(70f) - Ui.f(x0 + PAD + 12f));
        c.drawText(change, rightX - cw, hy, headP);
        Icons07.draw(c, Icons07.DOWN, rightX - cw - Ui.f(16f), hy - Ui.f(6f), Ui.f(18f), Look07.L2);
        Icons07.draw(c, Icons07.UP, rightX - cw - Ui.f(38f), hy - Ui.f(6f), Ui.f(18f), Look07.L2);
        // The rows, the shown one in the middle.
        float listTop = top + PAD + HEAD, off = slide.value();
        c.save();
        c.clipRect(Ui.f(x0), Ui.f(listTop - 8f), Ui.f(x0 + W), Ui.f(listTop + LIST_H));
        long now = System.currentTimeMillis();
        for (int k = 0; k < rows.length; k++) {
            Channel row = rows[k];
            if (row == null) continue;
            float y = listTop + (k - 1) * PITCH + off;
            if (y > listTop + LIST_H || y + ROW < listTop - 8f) continue;
            if (row == ch) continue;
            drawRow(c, k, row, x0, y, now, lib, settled, playing);
        }
        // The shown one last, lifted over its neighbours.
        for (int k = 0; k < rows.length; k++) {
            if (rows[k] != ch) continue;
            drawRow(c, k, ch, x0, listTop + (k - 1) * PITCH + off, now, lib, settled, playing);
            break;
        }
        c.restore();
        return moving;
    }

    /** The rows: two channels before the shown one, it, two after (and one more either way for the slide). */
    private void fillRows(Library lib, Channel ch) {
        int mid = 3;
        rows[mid] = ch;
        Channel up = ch, down = ch;
        for (int k = 1; k <= 3; k++) {
            up = up == null ? null : lib.step(up, -1);
            down = down == null ? null : lib.step(down, 1);
            if (mid - k >= 0) rows[mid - k] = up != null && !up.key.equals(ch.key) ? up : null;
            if (mid + k < rows.length) rows[mid + k] = down != null && !down.key.equals(ch.key) ? down : null;
        }
    }

    private void drawRow(Canvas c, int k, Channel ch, float x0, float y, long now, Library lib, boolean settled, Channel playing) {
        float left = x0 + PAD + 12f, right = x0 + W - PAD - 12f, up = lift.of(ch);
        // The channel's cell: the logo alone (ChannelLook), lifting where the remote is.
        r.set(Ui.f(left), Ui.f(y), Ui.f(left + CH_W), Ui.f(y + ROW));
        ChannelLook.cell(c, r, Ui.f(R_CELL), ch, up, 0, "", false, redraw);
        // Its programme: the picture taking up the whole cell (PicTile.fill), the words over a soft shade at its left.
        o.set(Ui.f(left + CH_W + GAP_X), Ui.f(y), Ui.f(right), Ui.f(y + ROW));
        float u = Math.max(0f, Math.min(1f, up)), rad = Ui.f(R_CELL);
        boolean scaled = u > 0.001f;
        if (scaled) {
            float s = 1f + (Look07.CHANNEL_LIFT - 1f) * u;
            c.save();
            c.scale(s, s, o.centerX(), o.centerY());
            Shadows.draw(c, o, rad, Look07.SH_CHANNEL_UP, u);
            Shadows.glow(c, o, rad, Look07.GLOW_CHANNEL, u);
        }
        Guide.Schedule sc = lib.schedule(ch.key);
        int i = sc == null ? -1 : sc.at(now);
        String title = i >= 0 ? sc.title[i] : "No information", sub = i >= 0 ? sub(k, ch, sc, i, now) : "";
        float tx = o.left + Ui.f(18f), room = o.right - Ui.f(14f) - tx;
        float wordsEnd = tx + Math.min(room, Math.max(titles.width(title), sub.isEmpty() ? 0f : subs.width(sub)));
        String url = i < 0 ? "" : settled && !Exec.inline ? ProgrammePictures.get(owner.getContext(), ch, sc, i, redraw) : ProgrammePictures.peek(ch, sc, i);
        boolean on = i >= 0 && com.gathin.homefieldtv.prefs.Look.progress;
        boolean pic = url != null && !url.isEmpty() && tiles.fill(c, o, rad, url, Math.round(o.width()), Pics.FILL, wordsEnd, true, false);
        if (pic) {
            tiles.shade(c, o, rad, wordsEnd, on);
        } else {
            fill.setColor(Look07.CELL);
            c.drawRoundRect(o, rad, rad, fill);
            float sw = Math.max(1f, Ui.f(1f));
            ring.setStrokeWidth(sw);
            ring.setColor(0x12FFFFFF);
            c.drawRoundRect(o.left + sw / 2f, o.top + sw / 2f, o.right - sw / 2f, o.bottom - sw / 2f, rad, rad, ring);
        }
        // How far through it is: a white line along its foot.
        if (on) {
            float d = LiveText.progress(now, sc.start[i], sc.stop[i]), to = o.left + Ui.f(6f) + (o.width() - Ui.f(12f)) * d;
            if (to > o.left + Ui.f(10f)) {
                fill.setColor(Look07.L1);
                c.drawRoundRect(o.left + Ui.f(6f), o.bottom - Ui.f(4f), to, o.bottom, Ui.f(2f), Ui.f(2f), fill);
            }
        }
        // The programme's title, its times and what's left.
        titleP.setColor(Look07.L1);
        titles.line(c, title, tx, Ui.f(y + 39f), room);
        if (!sub.isEmpty()) {
            subP.setColor(pic ? GuideGrid.SUB_ON_PICTURE : 0xD6FFFFFF);
            subs.line(c, sub, tx, Ui.f(y + 64f), room);
        }
        if (scaled) {
            // 07's focus: a 3 px white ring just outside it.
            float rw = Ui.f(Look07.CHANNEL_RING), out = rw / 2f;
            ring.setStrokeWidth(rw);
            ring.setColor(Palette.alpha(Look07.L1, Math.round(255 * u)));
            c.drawRoundRect(o.left - out, o.top - out, o.right + out, o.bottom + out, rad + out, rad + out, ring);
            c.restore();
        }
    }

    /** "19:00 – 20:10 · 40 min left": the programme's times and what is left of it (kept by row, channel and minute). */
    private String sub(int k, Channel ch, Guide.Schedule s, int i, long now) {
        long minute = now / 60_000L;
        // Kept by the programme (its channel and start) and the minute, in any of the slots: as the rows slide a row
        // moves to the next slot, its words found again (nothing made a frame).
        for (int j = 0; j < subFor.length; j++) {
            if (subFor[j] == ch && subMinute[j] == minute && subStart[j] == s.start[i] && subText[j] != null) return subText[j];
        }
        k = subNext;
        subNext = (subNext + 1) % subFor.length;
        String t = LiveText.hm(s.start[i]) + " – " + LiveText.hm(s.stop[i]) + " · "
                + GuideGrid.leftWords((int) Math.max(1L, (s.stop[i] - now + 59_999L) / 60_000L));
        subFor[k] = ch;
        subMinute[k] = minute;
        subStart[k] = s.start[i];
        subText[k] = t;
        return t;
    }

    /** "HOME • UK ENTERTAINMENT": where the channel shown is from, in capitals (kept by channel). */
    private String head(Channel ch) {
        if (!ch.key.equals(headKey)) {
            headKey = ch.key;
            String p = InfoCard.playlistName(owner.getContext(), ch), g = com.gathin.homefieldtv.prefs.Categories.label(owner.getContext(), ch.group);
            headText = InfoCard.where(p, g).replace(" • ", " · ").toUpperCase(Locale.UK);
        }
        return headText;
    }

    /** The box at the top right: "Watching 102" (the channel playing), or the number being typed ("Channel 10-"). */
    void watching(Canvas c, String label, String number) {
        if (number == null || number.isEmpty()) return;
        float lw = watchP.measureText(label), nw = watchNumP.measureText(number);
        float w = Ui.f(22f) + lw + Ui.f(12f) + nw + Ui.f(22f), right = Ui.f(1920f - RIGHT), top = Ui.f(54f), h = Ui.f(64f);
        r.set(right - w, top, right, top + h);
        box.draw(c, r, Ui.f(18f), 1f);
        c.drawText(label, r.left + Ui.f(22f), Type.centred(watchP, r.centerY()), watchP);
        c.drawText(number, r.left + Ui.f(22f) + lw + Ui.f(12f), Type.centred(watchNumP, r.centerY()), watchNumP);
    }

    /** The keys' hints over the card at the foot: OK Overlay, ◀ Channel list, ≡ Options. */
    static void hints(Canvas c) {
        float cy = Ui.f(1080f - 380f - 15f), x = Ui.f(70f);
        x += Pills.key(c, x, cy, "OK") + Ui.f(8f);
        x += Pills.hint(c, x, cy, "Overlay") + Ui.f(24f);
        x += keyIcon(c, x, cy, Icons07.LEFT) + Ui.f(8f);
        x += Pills.hint(c, x, cy, "Channel list") + Ui.f(24f);
        x += keyIcon(c, x, cy, Icons07.MENU) + Ui.f(8f);
        Pills.hint(c, x, cy, "Options");
    }

    private static final Paint KEY = new Paint(Paint.ANTI_ALIAS_FLAG), KEY_LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF KR = new RectF();

    static {
        KEY.setColor(0x2EFFFFFF);
        KEY_LINE.setStyle(Paint.Style.STROKE);
        KEY_LINE.setColor(0x4DFFFFFF);
    }

    /** A remote key's chip with an icon in it (as Pills.key's), 38 x 30. */
    private static float keyIcon(Canvas c, float x, float cy, int icon) {
        float h = Ui.f(30f), w = Ui.f(38f);
        KR.set(x, cy - h / 2f, x + w, cy + h / 2f);
        c.drawRoundRect(KR, h / 2f, h / 2f, KEY);
        KEY_LINE.setStrokeWidth(Math.max(1f, Ui.f(1f)));
        c.drawRoundRect(KR, h / 2f, h / 2f, KEY_LINE);
        Icons07.draw(c, icon, KR.centerX(), cy, Ui.f(16f), Look07.L1);
        return w;
    }
}
