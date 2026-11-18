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
 * the one shown in the middle: each row its channel's cell in its logo's colour (ChannelLook), its programme's picture
 * fitted round its faces (PicTile) with the logo's chip on it, the channel's number, the programme's title, the channel
 * and what is left, and a bar of how far through it is. The remote's row lifts with 07's white ring; going on a channel
 * slides the rows a row. Beside it the box at the top right says which channel is playing ("Watching 102"), and the
 * keys' hints sit over the card at the foot (OK Overlay, ◀ Channel list, ≡ Options).
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
    static final float THUMB_W = 112f, THUMB_H = 63f;

    private final View owner;
    private final Runnable redraw;
    private final Frost frost = new Frost(Look07.MAT_DARK), box = new Frost(Look07.MAT_DARK);
    private final PicTile tiles;
    private final Lift07 lift = new Lift07();
    /** The rows' slide when the channel shown moves on a row (design px: from a row's pitch back to 0). */
    private final Glide slide = new Glide(Look07.SCROLL_MS);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headP = Type.paint(17f, Type.SEMIBOLD, Look07.L2), titleP = Type.paint(20f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint subP = Type.paint(16f, Type.MEDIUM, 0xD6FFFFFF), numP = Type.clock(19f, Type.BOLD, 0xD6FFFFFF);
    private final TextPaint watchP = Type.paint(22f, Type.SEMIBOLD, Look07.L2), watchNumP = Type.clock(30f, Type.BOLD, Look07.L1);
    private final TextFit titles = new TextFit(titleP, 64), subs = new TextFit(subP, 64), heads = new TextFit(headP, 16);
    private final RectF r = new RectF(), o = new RectF();
    /** The channels in the rows (the shown one in the middle), and the ones a row further either way while sliding. */
    private final Channel[] rows = new Channel[ROWS + 2];
    private Channel shown;
    private String headKey = "", headText = "";
    /** Each row's second line ("Riverside One • 40 min left"), kept by channel and minute. */
    private final Channel[] subFor = new Channel[ROWS + 2];
    private final long[] subMinute = new long[ROWS + 2];
    private final String[] subText = new String[ROWS + 2];

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
        float left = x0 + PAD + 12f, right = x0 + W - PAD - 12f;
        r.set(Ui.f(left), Ui.f(y), Ui.f(right), Ui.f(y + ROW));
        float up = lift.of(ch);
        ChannelLook.fillCell(c, r, Ui.f(22f), ch, up);
        float s = 1f + (Look07.CHANNEL_LIFT - 1f) * Math.max(0f, Math.min(1f, up));
        boolean scaled = s > 1.0005f;
        if (scaled) {
            c.save();
            c.scale(s, s, r.centerX(), r.centerY());
        }
        float cy = y + ROW / 2f;
        Guide.Schedule sc = lib.schedule(ch.key);
        int i = sc == null ? -1 : sc.at(now);
        // Its programme's picture (or its logo), the logo's chip on a picture.
        o.set(Ui.f(left + 12f), Ui.f(cy - THUMB_H / 2f), Ui.f(left + 12f + THUMB_W), Ui.f(cy + THUMB_H / 2f));
        String url = i < 0 ? "" : settled && !Exec.inline ? ProgrammePictures.get(owner.getContext(), ch, sc, i, redraw) : ProgrammePictures.peek(ch, sc, i);
        float end = url == null || url.isEmpty() ? -1f : tiles.draw(c, o, Ui.f(12f), url, PicTile.COVER, true, false);
        if (end < 0f) {
            fill.setColor(0x59000000);
            c.drawRoundRect(o, Ui.f(12f), Ui.f(12f), fill);
            float lh = Ui.f(40f), lw = lh * 1.69f;
            Logos.draw(c, ch, rect(o.centerX() - lw / 2f, o.centerY() - lh / 2f, lw, lh), redraw);
        } else {
            Logos.draw(c, ch, rect(o.left + Ui.f(4f), o.bottom - Ui.f(30f), Ui.f(44f), Ui.f(26f)), redraw);
        }
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x1FFFFFFF);
        c.drawRoundRect(o.left + sw / 2f, o.top + sw / 2f, o.right - sw / 2f, o.bottom - sw / 2f, Ui.f(12f), Ui.f(12f), ring);
        // Its number, the programme's title, the channel and what's left, the bar.
        int words = ChannelLook.colour(ch) == 0 && up >= 0.5f ? Look07.INK : Look07.L1;
        numP.setColor(words == Look07.INK ? Look07.INK2 : 0xD6FFFFFF);
        int n = lib.number(ch);
        if (n > 0) c.drawText(Words.number(n), Ui.f(left + 12f + THUMB_W + 14f), Type.centred(numP, Ui.f(cy)), numP);
        float tx = left + 12f + THUMB_W + 14f + 40f + 14f, room = Ui.f(right - 12f - tx);
        titleP.setColor(words);
        titles.line(c, i >= 0 ? sc.title[i] : ChannelEdits.name(ch), Ui.f(tx), Ui.f(y + 36f), room);
        subP.setColor(words == Look07.INK ? Look07.INK2 : 0xD6FFFFFF);
        subs.line(c, sub(k, ch, sc, i, now), Ui.f(tx), Ui.f(y + 58f), room);
        if (i >= 0 && com.gathin.homefieldtv.prefs.Look.progress) {
            float bx = Ui.f(tx), by = Ui.f(y + 67f), bw = room;
            fill.setColor(words == Look07.INK ? 0x26000000 : 0x40FFFFFF);
            c.drawRoundRect(bx, by, bx + bw, by + Ui.f(4f), Ui.f(2f), Ui.f(2f), fill);
            float d = LiveText.progress(now, sc.start[i], sc.stop[i]);
            if (d > 0f) {
                fill.setColor(words == Look07.INK ? Look07.INK : Look07.L1);
                c.drawRoundRect(bx, by, bx + Math.max(Ui.f(4f), bw * d), by + Ui.f(4f), Ui.f(2f), Ui.f(2f), fill);
            }
        }
        if (scaled) c.restore();
    }

    private final RectF box2 = new RectF();

    private RectF rect(float x, float y, float w, float h) {
        box2.set(x, y, x + w, y + h);
        return box2;
    }

    /** "Riverside One • 40 min left": the channel and what is left of its programme (kept by channel and minute). */
    private String sub(int k, Channel ch, Guide.Schedule s, int i, long now) {
        long minute = now / 60_000L;
        if (subFor[k] == ch && subMinute[k] == minute && subText[k] != null) return subText[k];
        String name = ChannelEdits.name(ch);
        String t = i < 0 ? name : name + " · " + GuideGrid.leftWords((int) Math.max(1L, (s.stop[i] - now + 59_999L) / 60_000L));
        subFor[k] = ch;
        subMinute[k] = minute;
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
