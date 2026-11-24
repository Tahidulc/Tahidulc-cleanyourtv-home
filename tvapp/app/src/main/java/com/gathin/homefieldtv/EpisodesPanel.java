package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;

import java.util.ArrayList;
import java.util.List;

/**
 * A series' episodes over the picture (the film player's Episodes): a card of 07's dark material at the right, its
 * seasons as pills along its top (the one shown white while the remote is on them), its episodes under them as rows,
 * each with its picture (its still, else the series'), its number and name, its length and when it was first shown;
 * watched ones ticked on their picture, one part-watched with its bar, the one playing ticked "Playing". The remote's row
 * is white with dark words. Up and Down go along the rows (Up from the first to the pills), Left and Right change season
 * (on the pills or the rows), OK plays the episode, Back closes it. Rows' words are made when a season is shown, not a
 * frame; their pictures are asked for at their size once and kept (Pics).
 */
final class EpisodesPanel extends View {
    interface Host {
        /** OK on an episode. */
        void play(Catalog.Episode e);

        /** How far one was watched: -1 not started, up to 1 watched. */
        float progress(Catalog.Episode e);

        /** Back: shut. */
        void closed();
    }

    static final float X = 1032f, Y = 48f, W = 840f, H = 984f, PILLS_Y = 176f, ROWS_TOP = 236f, ROW = 150f,
            PIC_W = 208f, PIC_H = 117f, RADIUS = 40f;

    private final Host host;
    private final List<Catalog.Season> seasons = new ArrayList<>();
    private final List<String[]> words = new ArrayList<>();
    private String backdrop = "";
    private String playing = "";
    private int season, focus;
    private boolean onPills, open;
    private float scroll;
    private final Frost card = new Frost(Look07.MAT_DARK);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(Type.H3, Type.BOLD, Look07.L1), nameP = Type.paint(24f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint lineP = Type.paint(18f, Type.MEDIUM, Look07.L3), statusP = Type.paint(19f, Type.BOLD, Look07.L1);
    private final TextPaint hintP = Type.paint(19f, Type.MEDIUM, Look07.L3);
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;

    EpisodesPanel(Context c, Host host) {
        super(c);
        this.host = host;
        setVisibility(GONE);
        Shadows.prepare(Look07.SH_PANEL, RADIUS);
        Shadows.prepare(Look07.SH_ROW, Look07.R_ROW);
        Pills.prepare();
    }

    boolean isOpen() {
        return open;
    }

    /** Open on the season of the one playing, the remote on it. */
    void open(Catalog.Details d, Catalog.Episode now, String seriesPicture) {
        seasons.clear();
        if (d != null) seasons.addAll(d.seasons);
        if (seasons.isEmpty()) return;
        backdrop = seriesPicture == null ? "" : seriesPicture;
        playing = now == null ? "" : now.id;
        season = 0;
        for (int s = 0; s < seasons.size(); s++) for (Catalog.Episode e : seasons.get(s).episodes) if (e.id.equals(playing)) season = s;
        showSeason(season, true);
        onPills = false;
        open = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    void close() {
        if (!open) return;
        open = false;
        setVisibility(GONE);
    }

    int season() {
        return season;
    }

    int focus() {
        return focus;
    }

    boolean onPills() {
        return onPills;
    }

    /** A season shown: its rows' words made, the remote on the one playing (or its first). */
    private void showSeason(int s, boolean toPlaying) {
        season = Math.max(0, Math.min(seasons.size() - 1, s));
        words.clear();
        focus = 0;
        List<Catalog.Episode> eps = seasons.get(season).episodes;
        for (int i = 0; i < eps.size(); i++) {
            Catalog.Episode e = eps.get(i);
            words.add(rowWords(e));
            if (toPlaying && e.id.equals(playing)) focus = i;
        }
        scroll = 0;
        keepInSight();
    }

    /** Pure: an episode's row: "3  The Lighthouse", "45m  ·  9 Oct 2022". */
    static String[] rowWords(Catalog.Episode e) {
        String name = e.number + "  " + (e.title.isEmpty() ? "Episode " + e.number : e.title);
        List<String> line = new ArrayList<>();
        if (e.minutes > 0) line.add(Ui.duration(e.minutes));
        if (!e.aired.isEmpty()) line.add(e.aired);
        if (e.torbox) line.add("In your TorBox");
        return new String[]{name, String.join("  ·  ", line)};
    }

    private List<Catalog.Episode> episodes() {
        return seasons.isEmpty() ? new ArrayList<>() : seasons.get(season).episodes;
    }

    private void keepInSight() {
        float room = (Y + H - 70f) - ROWS_TOP, top = focus * ROW, bottom = top + ROW;
        if (bottom - scroll > room) scroll = bottom - room;
        if (top - scroll < 0) scroll = top;
        scroll = Math.max(0f, Math.min(scroll, Math.max(0f, words.size() * ROW - room)));
    }

    /** A key while open: true when it was the panel's (the volume keys go on to the TV). */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (onPills) return true;
                if (focus == 0) onPills = true;
                else focus--;
                keepInSight();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (onPills) onPills = false;
                else if (focus + 1 < words.size()) focus++;
                keepInSight();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int to = season + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (to >= 0 && to < seasons.size()) showSeason(to, true);
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() > 0) return true;
                if (onPills) {
                    onPills = false;
                    invalidate();
                    return true;
                }
                List<Catalog.Episode> eps = episodes();
                if (focus < eps.size()) {
                    Catalog.Episode ep = eps.get(focus);
                    close();
                    host.play(ep);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (e.getRepeatCount() > 0) return true;
                close();
                host.closed();
                return true;
            default:
                return true;
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (!open || seasons.isEmpty()) return;
        float x = Ui.f(X), y = Ui.f(Y), w = Ui.f(W), h = Ui.f(H);
        r.set(x, y, x + w, y + h);
        Shadows.draw(c, r, Ui.f(RADIUS), Look07.SH_PANEL, 1f);
        card.draw(c, r, Ui.f(RADIUS), 1f);
        c.drawText("Episodes", Ui.f(X + 48f), Ui.f(118f), titleP);
        // The seasons.
        float px = Ui.f(X + 34f), cy = Ui.f(PILLS_Y);
        int first = Math.max(0, season - 3);
        for (int s = first; s < seasons.size(); s++) {
            String name = seasons.get(s).name;
            float pw = Pills.pillWidth(name, s == season);
            if (px + pw > x + w - Ui.f(34f)) break;
            if (s == season && !onPills) {
                r.set(px, cy - Ui.f(26f), px + pw, cy + Ui.f(26f));
                fill.setColor(Look07.SELECTED);
                c.drawRoundRect(r, Ui.f(26f), Ui.f(26f), fill);
            }
            px += Pills.pill(c, px, cy, name, s == season && onPills, false) + Ui.f(10f);
        }
        // The rows.
        List<Catalog.Episode> eps = episodes();
        c.save();
        c.clipRect(x, Ui.f(ROWS_TOP - 8f), x + w, Ui.f(Y + H - 70f));
        for (int i = 0; i < eps.size() && i < words.size(); i++) {
            float ry = ROWS_TOP + i * ROW - scroll;
            if (ry + ROW < ROWS_TOP - 8f || ry > Y + H - 70f) continue;
            drawRow(c, eps.get(i), words.get(i), !onPills && i == focus, ry);
        }
        c.restore();
        c.drawText("OK plays it  ·  Left and Right change season  ·  Back closes", Ui.f(X + 48f), Ui.f(Y + H - 30f), hintP);
    }

    private void drawRow(Canvas c, Catalog.Episode e, String[] w, boolean lit, float y) {
        float left = Ui.f(X + 22f), right = Ui.f(X + W - 22f);
        r.set(left, Ui.f(y + 6f), right, Ui.f(y + ROW - 6f));
        if (lit) {
            Shadows.draw(c, r, Ui.f(Look07.R_ROW), Look07.SH_ROW, 1f);
            fill.setColor(Look07.FOCUS_FILL);
            c.drawRoundRect(r, Ui.f(Look07.R_ROW), Ui.f(Look07.R_ROW), fill);
        }
        // Its picture.
        float pw = Ui.f(PIC_W), ph = Ui.f(PIC_H), px = left + Ui.f(16f), py = Ui.f(y + (ROW - PIC_H) / 2f);
        r.set(px, py, px + pw, py + ph);
        fill.setColor(Look07.CARD_FRAME);
        c.drawRoundRect(r, Ui.f(12f), Ui.f(12f), fill);
        String url = e.still.isEmpty() ? backdrop : e.still;
        Bitmap b = url.isEmpty() ? null : Pics.get(url, Math.round(pw), Math.round(ph), Pics.CROP, redraw);
        if (b != null) {
            c.save();
            c.clipRect(r);
            c.drawBitmap(b, null, r, pic);
            c.restore();
        }
        float p = host.progress(e);
        boolean now = e.id.equals(playing);
        if (p >= 1f && !now) {
            // Watched: a tick on its picture.
            float d = Ui.f(30f), tx = px + pw - Ui.f(10f) - d / 2f, ty = py + Ui.f(10f) + d / 2f;
            fill.setColor(0x99000000);
            c.drawCircle(tx, ty, d / 2f, fill);
            Icons07.draw(c, Icons07.CHECK, tx, ty, Ui.f(18f), Look07.L1);
        } else if (p > 0f && p < 1f) {
            Pills.progressOn(c, px + Ui.f(8f), py + ph - Ui.f(12f), pw - Ui.f(16f), p);
        }
        float tx = px + pw + Ui.f(22f), room = right - Ui.f(24f) - tx;
        String status = now ? "Playing" : "";
        if (now) room -= statusP.measureText(status) + Ui.f(44f);
        nameP.setColor(lit ? Look07.INK : Look07.L1);
        c.drawText(Ui.fit(w[0], nameP, room), tx, Ui.f(y + ROW / 2f - 4f), nameP);
        if (!w[1].isEmpty()) {
            lineP.setColor(lit ? Look07.INK2 : Look07.L3);
            c.drawText(Ui.fit(w[1], lineP, room), tx, Ui.f(y + ROW / 2f + 26f), lineP);
        }
        if (now) {
            statusP.setColor(lit ? Look07.INK : Look07.L1);
            float sw = statusP.measureText(status), sx = right - Ui.f(24f) - sw, cy = Ui.f(y + ROW / 2f);
            c.drawText(status, sx, Type.centred(statusP, cy), statusP);
            Icons07.draw(c, Icons07.CHECK, sx - Ui.f(20f), cy, Ui.f(24f), lit ? Look07.INK : Look07.GREEN);
        }
    }
}
