package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.engine.LogoPack;

import java.util.ArrayList;
import java.util.List;

/**
 * The foot of the player's OK overlay, as the family's TiviMate draws it: over the playback scrim (black rising from y
 * 700 to about 90 % at the foot), the seek line across (80 to 1839 at y 777–782: the accent up to where the programme
 * is, grey after, the white knob at the join) and under it the row of tiles (240 x 178 from (82, 813), 254 apart): TV
 * guide, History, then the channels last watched, in the 07 look (3.4): each channel's tile its programme's own picture
 * filling it (Pics.FILL, its faces whole; asked for once the row rests), else its logo's own colour, with the channel's
 * logo alone on its colour at its top left (ChannelLook), the programme's title over a shade at its foot and how far
 * through it is along it. The tile the remote is on lifts toward you (Lift07) with a white ring, and the row slides so it
 * stays in the first place; under the row, on a channel, its programme's times and title with its playlist at the right,
 * else a chevron.
 *
 * <pre>
 *   set(recent, at)            the channels for the row (the one playing left out), the remote on tile at
 *   key(code) / ok()           Left and Right along the row; Up and Down go to the host (edge); OK: the tile's
 * </pre>
 */
final class PlayerTiles extends View {
    static final float SEEK_X0 = 80f, SEEK_X1 = 1839f, SEEK_Y = 779.5f;
    static final float TILE_X = 82f, TILE_Y = 813f, TILE_W = 240f, TILE_H = 178f, PITCH = 254f, SCRIM_Y = 700f;
    static final long SCROLL_MS = 150;
    /** The first two tiles. */
    static final int GUIDE = 0, HISTORY = 1;

    interface Host {
        void openGuide();

        void openHistory();

        void tune(Channel c);

        /** Up or Down off the row (Ui.UP, Ui.DOWN): true when the screen took it. */
        boolean edge(int dir);

        /** The seek line: {share played, share aired (catch-up's grey), 1 when live}; null for none. */
        float[] seek();
    }

    private final Host host;
    private final List<Channel> recent = new ArrayList<>();
    private int focus;
    /** The row's scroll (design pixels), gliding on the display's frame clock so the remote's tile is first. */
    private final Glide scroll = new Glide(SCROLL_MS);
    /** Names and titles measured once, cut from their kept widths (TextFit). */
    private TextFit names, titles, lines, logos;
    /** Whether the seek line shows (catch-up keeps its own above the row). */
    boolean seekLine = true;
    /** Only peeking in under catch-up's transport: no tile lit, nothing under the row. */
    boolean peek;
    private final Paint scrim = new Paint(), tile = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), iconLine = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelP = Type.paint(32f, Type.REGULAR, Palette.TEXT1), nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final TextPaint onP = Type.paint(Type.SMALL, Type.REGULAR, Palette.ACCENT_LIGHT), lineP = Type.paint(32f, Type.REGULAR, Palette.TEXT1);
    private final TextPaint whereP = Type.paint(32f, Type.REGULAR, Palette.TEXT2), logoP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT1);
    private final RectF r = new RectF(), cell = new RectF();
    private float scrimFor = -1f;
    /** 07: the remote's tile lifting, the tiles' rounded clip and their foot's shade (made once a size). */
    private final Lift07 lift = new Lift07();
    private final android.graphics.Path clip = new android.graphics.Path();
    private final Paint footShade = new Paint(), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint progP = Type.paint(21f, Type.SEMIBOLD, Look07.L1);
    private float shadeFor = -1f;
    private static final Object GUIDE_TILE = new Object(), HISTORY_TILE = new Object();
    /** 07's lift: how much bigger the remote's tile is. */
    static final float LIFT = 1.06f;
    private String whereKey = "", whereText = "";
    private final Runnable redraw = this::invalidate;

    PlayerTiles(Context c, Host host) {
        super(c);
        this.host = host;
        edge.setStyle(Paint.Style.STROKE);
        ring.setStyle(Paint.Style.STROKE);
        iconLine.setStyle(Paint.Style.STROKE);
        iconLine.setStrokeCap(Paint.Cap.ROUND);
        iconLine.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(List<Channel> channels, int at) {
        recent.clear();
        if (channels != null) recent.addAll(channels);
        focus = Math.max(0, Math.min(at, count() - 1));
        scroll.jump(focus * PITCH);
        invalidate();
    }

    int focus() {
        return focus;
    }

    private int count() {
        return 2 + recent.size();
    }

    /** The channel on tile k, null for the TV guide and History. */
    private Channel channelAt(int k) {
        return k >= 2 && k - 2 < recent.size() ? recent.get(k - 2) : null;
    }

    boolean key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int n = focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (n < 0 || n >= count()) return true;
                focus = n;
                scroll.to(focus * PITCH);
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                return host.edge(Ui.UP);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return host.edge(Ui.DOWN);
            default:
                return false;
        }
    }

    void ok() {
        if (focus == GUIDE) host.openGuide();
        else if (focus == HISTORY) host.openHistory();
        else if (channelAt(focus) != null) host.tune(channelAt(focus));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), hh = getHeight();
        if (scrimFor != hh) {
            scrim.setShader(new LinearGradient(0, Ui.f(SCRIM_Y), 0, hh, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
            scrimFor = hh;
        }
        c.drawRect(0, Ui.f(SCRIM_Y), w, hh, scrim);
        boolean moving = scroll.step();
        float sc = scroll.value();
        moving |= lift.step(peek ? null : itemAt(focus));
        if (shadeFor != Ui.f(TILE_H)) {
            shadeFor = Ui.f(TILE_H);
            footShade.setShader(new LinearGradient(0, Ui.f(TILE_H) * 0.35f, 0, Ui.f(TILE_H), 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
        }
        if (names == null) {
            names = new TextFit(nameP, 64);
            titles = new TextFit(progP, 64);
            lines = new TextFit(lineP, 64);
            logos = new TextFit(logoP, 32);
        }
        if (seekLine) {
            float[] sk = host.seek();
            if (sk != null) Bars.seek(c, Ui.f(SEEK_X0), Ui.f(SEEK_X1), Ui.f(SEEK_Y), sk[0], sk[1], sk[2] > 0f);
        }
        long now = System.currentTimeMillis();
        Library lib = Library.get(getContext());
        for (int k = 0; k < count(); k++) {
            float x = TILE_X + k * PITCH - sc;
            if (x > 1920f) break;
            if (x + TILE_W < 0f) continue;
            drawTile(c, k, Ui.f(x), Ui.f(TILE_Y), k == focus && !peek, now, lib, moving);
        }
        // Under the row: the programme on the tile's channel, else a chevron saying the bar is below.
        Channel ch = channelAt(focus);
        if (peek) {
            // Nothing under the row while it only peeks in.
        } else if (ch != null) {
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            float base = Ui.f(1052f);
            if (!ch.key.equals(whereKey)) {
                whereKey = ch.key;
                whereText = InfoCard.playlistName(getContext(), ch);
            }
            String where = whereText;
            float ww = where.isEmpty() ? 0f : whereP.measureText(where);
            whereP.setColor(Palette.TEXT2);
            if (!where.isEmpty()) c.drawText(where, Ui.f(1888f) - ww, base, whereP);
            if (i >= 0) {
                lineP.setColor(Palette.TEXT1);
                String sp = Words.span(s.start[i], s.stop[i]);
                c.drawText(sp, Ui.f(SEEK_X0), base, lineP);
                float tx = Ui.f(SEEK_X0) + lines.width(sp) + Ui.f(24);
                lines.line(c, s.title[i], tx, base, Ui.f(1888f) - ww - Ui.f(40) - tx);
            }
        } else {
            iconLine.setColor(Palette.TEXT2);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(3f)));
            Ui.chevron(c, Ui.f(960f), Ui.f(1035f), Ui.f(10f), Ui.DOWN, iconLine);
        }
        if (moving) postInvalidateOnAnimation();
    }

    /** What tile k is for the lift: its channel, or the TV guide's or History's own. */
    private Object itemAt(int k) {
        return k == GUIDE ? GUIDE_TILE : k == HISTORY ? HISTORY_TILE : channelAt(k);
    }

    private void drawTile(Canvas c, int k, float x, float y, boolean lit, long now, Library lib, boolean moving) {
        float tw = Ui.f(TILE_W), th = Ui.f(TILE_H), rad = Ui.f(Look07.R_CARD);
        float up = lift.of(itemAt(k)), g = (LIFT - 1f) * Math.max(0f, up);
        r.set(x - tw * g / 2f, y - th * g / 2f, x + tw + tw * g / 2f, y + th + th * g / 2f);
        Channel ch = channelAt(k);
        if (ch == null) {
            // TV guide, History: on glass, an icon over its name.
            tile.setColor(lit ? 0x40FFFFFF : 0x1FFFFFFF);
            c.drawRoundRect(r, rad, rad, tile);
            iconLine.setColor(Palette.TEXT1);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(4f)));
            iconFill.setColor(Palette.TEXT1);
            Dark.icon(c, k == GUIDE ? Dark.SHEET : Dark.HISTORY, r.centerX(), r.top + r.height() * 0.38f, Ui.f(60f), iconLine, iconFill);
            String t = k == GUIDE ? "TV guide" : "History";
            labelP.setColor(Palette.TEXT1);
            c.drawText(t, r.centerX() - labelP.measureText(t) / 2f, r.top + r.height() * 0.83f, labelP);
            ringAround(c, rad, up);
            return;
        }
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(now);
        // The programme's picture filling the tile (asked for once the row rests), else the logo's own colour.
        String pic = i < 0 ? "" : moving ? ProgrammePictures.peek(ch, s, i) : ProgrammePictures.get(getContext(), ch, s, i, redraw);
        Bitmap b = pic == null || pic.isEmpty() ? null : Pics.get(pic, Math.round(tw), Math.round(th), Pics.FILL, redraw);
        c.save();
        clip.reset();
        clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        int base = ChannelLook.colour(ch);
        tile.setColor(base == 0 ? 0xFF1E2430 : base | 0xFF000000);
        c.drawRect(r, tile);
        if (b != null) {
            c.drawBitmap(b, null, r, bmp);
        } else {
            // No picture: the logo large in the middle of its colour (never blank).
            cell.set(r.left + r.width() * 0.2f, r.top + r.height() * 0.16f, r.right - r.width() * 0.2f, r.top + r.height() * 0.62f);
            Logos.drawCell(c, com.gathin.homefieldtv.engine.LogoPack.url(ch), cell, redraw);
        }
        c.save();
        c.translate(r.left, r.top);
        c.scale(r.width() / tw, r.height() / th);
        c.drawRect(0, 0, tw, th, footShade);
        c.restore();
        c.restore();
        // The channel's logo alone on its colour at the top left (over a programme's own picture, not the channel's).
        if (b != null && !pic.startsWith(Pics.ON_COLOUR)) {
            cell.set(r.left + Ui.f(10f), r.top + Ui.f(10f), r.left + Ui.f(10f + 92f), r.top + Ui.f(10f + 56f));
            ChannelLook.cell(c, cell, Ui.f(12f), ch, 0f, 0, null, false, redraw);
        }
        // The programme's title and how far through it is, at the foot.
        if (i >= 0) {
            progP.setColor(Look07.L1);
            titles.line(c, s.title[i], r.left + Ui.f(14f), r.bottom - Ui.f(22f), r.width() - Ui.f(28f));
            Bars.thin(c, r.left + Ui.f(14f), r.bottom - Ui.f(9f), r.width() - Ui.f(28f), Ui.f(4f), LiveText.progress(now, s.start[i], s.stop[i]));
        } else {
            nameP.setColor(Palette.TEXT1);
            names.line(c, ChannelEdits.name(ch), r.left + Ui.f(14f), r.bottom - Ui.f(18f), r.width() - Ui.f(28f));
        }
        ringAround(c, rad, up);
    }

    /** 07's focus: a white ring just outside the lifted tile, as it lifts. */
    private void ringAround(Canvas c, float rad, float up) {
        if (up <= 0.01f) return;
        float rw = Ui.f(3f), out = rw / 2f;
        cell.set(r.left - out, r.top - out, r.right + out, r.bottom + out);
        ring.setStrokeWidth(rw);
        ring.setColor(Palette.alpha(Look07.L1, Math.round(255 * Math.min(1f, up))));
        c.drawRoundRect(cell, rad + out, rad + out, ring);
    }
}
