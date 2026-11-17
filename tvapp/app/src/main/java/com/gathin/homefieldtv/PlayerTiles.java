package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.List;

/**
 * The foot of the player's OK overlay, as the family's TiviMate draws it: over the playback scrim (black rising from y
 * 700 to about 90 % at the foot), the seek line across (80 to 1839 at y 777–782: the accent up to where the programme
 * is, grey after, the white knob at the join) and under it the row of tiles (240 x 178 from (82, 813), 254 apart): TV
 * guide, History, then the channels last watched (logo, name, what's on in the light accent, its bar at the tile's foot).
 * The tile the remote is on is a shade lighter with a 2 px light-accent edge, and the row slides so it stays in the first
 * place; under the row, on a channel, its programme's times and title with its playlist at the right, else a chevron.
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
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt;
    /** Whether the seek line shows (catch-up keeps its own above the row). */
    boolean seekLine = true;
    /** Only peeking in under catch-up's transport: no tile lit, nothing under the row. */
    boolean peek;
    private final Paint scrim = new Paint(), tile = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), iconLine = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelP = Type.paint(32f, Type.REGULAR, Palette.TEXT1), nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final TextPaint onP = Type.paint(Type.SMALL, Type.REGULAR, Palette.ACCENT_LIGHT), lineP = Type.paint(32f, Type.REGULAR, Palette.TEXT1);
    private final TextPaint whereP = Type.paint(32f, Type.REGULAR, Palette.TEXT2), logoP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT1);
    private final RectF r = new RectF();
    private float scrimFor = -1f;
    private String whereKey = "", whereText = "";
    private final Runnable redraw = this::invalidate;

    PlayerTiles(Context c, Host host) {
        super(c);
        this.host = host;
        edge.setStyle(Paint.Style.STROKE);
        iconLine.setStyle(Paint.Style.STROKE);
        iconLine.setStrokeCap(Paint.Cap.ROUND);
        iconLine.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(List<Channel> channels, int at) {
        recent.clear();
        if (channels != null) recent.addAll(channels);
        focus = Math.max(0, Math.min(at, count() - 1));
        scroll = scrollFrom = scrollTo = focus * PITCH;
        scrollAt = 0;
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
                scrollFrom = scroll;
                scrollTo = focus * PITCH;
                scrollAt = Exec.inline ? 0 : SystemClock.uptimeMillis();
                if (Exec.inline) scroll = scrollTo;
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
        boolean moving = false;
        if (scrollAt != 0) {
            float k = Math.min(1f, (SystemClock.uptimeMillis() - scrollAt) / (float) SCROLL_MS);
            scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) scrollAt = 0;
            else moving = true;
        }
        if (seekLine) {
            float[] sk = host.seek();
            if (sk != null) Bars.seek(c, Ui.f(SEEK_X0), Ui.f(SEEK_X1), Ui.f(SEEK_Y), sk[0], sk[1], sk[2] > 0f);
        }
        long now = System.currentTimeMillis();
        Library lib = Library.get(getContext());
        for (int k = 0; k < count(); k++) {
            float x = TILE_X + k * PITCH - scroll;
            if (x > 1920f) break;
            if (x + TILE_W < 0f) continue;
            drawTile(c, k, Ui.f(x), Ui.f(TILE_Y), k == focus && !peek, now, lib);
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
                String sp = LiveText.span(s.start[i], s.stop[i]);
                c.drawText(sp, Ui.f(SEEK_X0), base, lineP);
                float tx = Ui.f(SEEK_X0) + lineP.measureText(sp) + Ui.f(24);
                c.drawText(Ui.fit(s.title[i], lineP, Ui.f(1888f) - ww - Ui.f(40) - tx), tx, base, lineP);
            }
        } else {
            iconLine.setColor(Palette.TEXT2);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(3f)));
            Ui.chevron(c, Ui.f(960f), Ui.f(1035f), Ui.f(10f), Ui.DOWN, iconLine);
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawTile(Canvas c, int k, float x, float y, boolean lit, long now, Library lib) {
        float tw = Ui.f(TILE_W), th = Ui.f(TILE_H), rad = Ui.f(10f);
        r.set(x, y, x + tw, y + th);
        tile.setColor(Palette.PLAYBACK);
        c.drawRoundRect(r, rad, rad, tile);
        if (lit) {
            tile.setColor(0x0DFFFFFF);
            c.drawRoundRect(r, rad, rad, tile);
            edge.setColor(Palette.ACCENT_LIGHT);
            edge.setStrokeWidth(Math.max(1f, Ui.f(2f)));
            r.inset(-Ui.f(1f), -Ui.f(1f));
            c.drawRoundRect(r, rad + Ui.f(1f), rad + Ui.f(1f), edge);
            r.inset(Ui.f(1f), Ui.f(1f));
        }
        Channel ch = channelAt(k);
        if (ch == null) {
            // TV guide, History: an icon over its name.
            iconLine.setColor(Palette.TEXT1);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(4f)));
            iconFill.setColor(Palette.TEXT1);
            Dark.icon(c, k == GUIDE ? Dark.SHEET : Dark.HISTORY, x + tw / 2f, y + Ui.f(68f), Ui.f(60f), iconLine, iconFill);
            String t = k == GUIDE ? "TV guide" : "History";
            labelP.setColor(Palette.TEXT1);
            c.drawText(t, x + (tw - labelP.measureText(t)) / 2f, y + Ui.f(147f), labelP);
            return;
        }
        Bitmap logo = Pics.get(ch.logo, Ui.px(208), Ui.px(78), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
        if (logo != null) c.drawBitmap(logo, x + (tw - logo.getWidth()) / 2f, y + Ui.f(12f) + (Ui.f(78f) - logo.getHeight()) / 2f, bmp);
        else {
            logoP.setColor(Palette.TEXT1);
            String nm = Ui.fit(ch.name, logoP, tw - Ui.f(32));
            c.drawText(nm, x + (tw - logoP.measureText(nm)) / 2f, y + Ui.f(60f), logoP);
        }
        nameP.setColor(Palette.TEXT1);
        c.drawText(Ui.fit(ch.name, nameP, tw - Ui.f(32)), x + Ui.f(16f), y + Ui.f(127f), nameP);
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(now);
        if (i >= 0) {
            onP.setColor(Palette.ACCENT_LIGHT);
            c.drawText(Ui.fit(s.title[i], onP, tw - Ui.f(32)), x + Ui.f(16f), y + Ui.f(162f), onP);
            Bars.thin(c, x + Ui.f(16f), y + th - Ui.f(4f), tw - Ui.f(32f), Ui.f(4f), LiveText.progress(now, s.start[i], s.stop[i]));
        }
    }
}
