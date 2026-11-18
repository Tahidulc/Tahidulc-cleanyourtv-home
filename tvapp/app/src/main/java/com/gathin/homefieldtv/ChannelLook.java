package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import com.gathin.broadcast.LogoTile;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.prefs.ChannelEdits;

/**
 * A channel's cell, the logo alone, as the family asked ("I don't want channels numbers now. Also where the channel
 * logo is, I don't want a box around the logo and also get rid of the channel title and just stretch the logos to fill
 * the box but make it look good so it looks natural and not stretched"): wherever a channel's cell is drawn (the guide's
 * channel column, the mini guide, the channel list, the shelf guide's labels) it holds only the channel's logo, with no
 * number, no tile round it and no name, scaled up to fill the cell with the same room on every side (PAD of its shorter
 * side), centred, its own shape kept exactly (a wide logo fills the width, a tall one the height), read sharp from the
 * logo itself at the size drawn (Logos.drawCell: Pics' CELL modes). The channel's marks (▶ playing, its HD, the
 * catch-up mark, the favourite's star) are small quiet icons in the cell's right-hand corner, clear of the logo.
 *
 * <p>Behind it, as Settings › Appearance › Logos › Behind logos says: Logo's colour (as it comes): the cell filled with
 * the logo's own row colour (the shared rule, LogoColour) held to 07's approved deep, opaque shade (Logos.Tint.row), so
 * a pale logo stands out on it, falling softly from top to foot, a line of light along its top, a hairline round it and
 * a soft shadow under it; a logo with no real colour (or not read yet) on the neutral slate. None: nothing behind, the
 * logo on the plain 07 row (the halo rule for a faint one). Black tile: the cell black, the logo made ready for black.
 * Where the remote is, 07's focus on any of them: the cell lifts (1.035) with a 3 px white ring just outside it, a deep
 * shadow and a soft white glow. The channel's number is not drawn; the screens give it to accessibility (their content
 * descriptions), and number entry with the remote is as it was.
 *
 * <pre>
 *   ChannelLook.cell(c, cell, radius, channel, up, marks, quality, reserve, redraw)   cell: screen px; up: 0 to 1 (a
 *                       Lift07's value, so the focus follows 07's springy ease); marks: PLAYING | ARCHIVE | FAVOURITE;
 *                       quality: "HD", "4K" or ""; reserve: keep the marks' corner clear of the logo in every cell of
 *                       this kind (so every logo in a column is laid out alike, read once at one size)
 *   ChannelLook.logoBox(cell, reserve, out)                       where the logo goes (screen px)
 *   ChannelLook.lifted(cell, up, out)                             where the lifted cell is
 *   ChannelLook.colour(channel)                                   the cell's colour (its middle; 0: nothing behind)
 * </pre>
 *
 * Drawing makes nothing new: kept paints, the logo one picture read once at its box's size.
 */
final class ChannelLook {
    private ChannelLook() {}

    static final int PLAYING = 1, ARCHIVE = 2, FAVOURITE = 4;
    /** The room round the logo: this share of the cell's shorter side, the same on every side. */
    static final float PAD = 0.12f;
    /** The marks (design px): an icon's size, the column they stand in at the right, the gap to the logo, between them. */
    static final float MARK = 13f, MARKS_W = 22f, MARK_GAP = 6f, MARK_STEP = 5f;
    /** The quiet marks' colour (HD, catch-up), and the strong ones' opacity (▶ red, ★ amber). */
    static final int QUIET = 0xA8FFFFFF;
    static final int STRONG_ALPHA = 0xE6;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), EDGE = new Paint(Paint.ANTI_ALIAS_FLAG),
            RING = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF(), O = new RectF(), B = new RectF();
    /** The name in a logo's place (a channel with no logo at all), and a mark's HD; made on first use (Type is ready). */
    private static TextPaint nameP, chipP;
    private static TextFit names;

    static {
        EDGE.setStyle(Paint.Style.STROKE);
        RING.setStyle(Paint.Style.STROKE);
    }

    /** The cell's colours for a channel: its logo's (the slate's while it is read), or null when nothing is behind it. */
    static Logos.Tint tint(Channel ch) {
        if (Logos.behind() != Logos.COLOUR) return null;
        Logos.Tint t = ch == null ? null : Logos.tint(LogoPack.url(ch));
        return t == null ? Logos.Tint.WAITING : t;
    }

    /** The cell's colour (its middle): the logo's deep row, black for Black tile, 0 for None. */
    static int colour(Channel ch) {
        if (Logos.behind() == Logos.BLACK) return LogoTile.GROUND;
        Logos.Tint t = tint(ch);
        return t == null ? 0 : t.row;
    }

    /** Where the cell is drawn lifted by up (0 to 1): grown about its middle. */
    static RectF lifted(RectF cell, float up, RectF out) {
        float k = Math.max(0f, Math.min(1.2f, up)), g = (Look07.CHANNEL_LIFT - 1f) * k;
        float dx = cell.width() * g / 2f, dy = cell.height() * g / 2f;
        out.set(cell.left - dx, cell.top - dy, cell.right + dx, cell.bottom + dy);
        return out;
    }

    /** The room round the logo in a cell (screen px). */
    static float pad(RectF cell) {
        return PAD * Math.min(cell.width(), cell.height());
    }

    /** Whether a cell is laid out across (its marks in a column at its right) rather than up (in a row at its top). */
    private static boolean across(RectF cell) {
        return cell.width() >= cell.height() * 1.3f;
    }

    /**
     * Where the logo goes in a cell (screen px): the cell less its room on every side; with reserve, less the marks'
     * column at both sides (or their row at the top and the foot of an upright cell), so the logo stays centred and
     * clear of the marks.
     */
    static RectF logoBox(RectF cell, boolean reserve, RectF out) {
        float p = pad(cell);
        out.set(cell.left + p, cell.top + p, cell.right - p, cell.bottom - p);
        if (reserve) {
            float m = Ui.f(MARKS_W + MARK_GAP);
            if (across(cell)) out.inset(m, 0f);
            else out.inset(0f, Ui.f(MARK + MARK_GAP));
        }
        return out;
    }

    /**
     * The channel's cell, the logo alone, lifted by up (0 resting, 1 where the remote is; a Lift07's value between), its
     * marks in its right-hand corner.
     */
    static void cell(Canvas c, RectF cell, float radius, Channel ch, float up, int marks, String quality, boolean reserve, Runnable redraw) {
        float k = Math.max(0f, Math.min(1f, up));
        RectF r = lifted(cell, up, O);
        float rad = radius * (r.height() / Math.max(1f, cell.height()));
        int behind = Logos.behind();
        float sw = Math.max(1f, Ui.f(1f));
        if (behind == Logos.COLOUR) {
            Logos.Tint t = tint(ch);
            boolean slate = t.neutral;
            // Under it: its soft shadow, deep and with a white glow as it lifts.
            Shadows.draw(c, r, rad, Look07.SH_CHANNEL, (slate ? 0.8f : 1f) * (1f - k));
            if (k > 0f) {
                Shadows.draw(c, r, rad, Look07.SH_CHANNEL_UP, k);
                Shadows.glow(c, r, rad, Look07.GLOW_CHANNEL, k);
            }
            // The line of light along its top (CSS: inset 0 1px 0): the cell in that light, then its fall a pixel lower.
            float h = Math.max(1f, r.height() - sw);
            FILL.setShader(null);
            FILL.setColor(Theme.mix(t.rowTop, 0xFFFFFFFF, ((slate ? Look07.SLATE_TOP_LIGHT : Look07.CHANNEL_TOP_LIGHT) >>> 24) / 255f) | 0xFF000000);
            c.drawRoundRect(r, rad, rad, FILL);
            // Its fall, stretched from a unit box (its corners kept round), opaque.
            FILL.setShader(t.rowFall);
            FILL.setColor(0xFF000000);
            c.save();
            c.translate(r.left, r.top + sw);
            c.scale(1f, h);
            c.drawRoundRect(0, 0, r.width(), 1f, rad, rad / h, FILL);
            c.restore();
            FILL.setShader(null);
            edge(c, r, rad, sw, slate ? Look07.SLATE_EDGE : Look07.CHANNEL_EDGE);
        } else if (behind == Logos.BLACK) {
            Shadows.draw(c, r, rad, Look07.SH_CHANNEL, 1f - k);
            if (k > 0f) {
                Shadows.draw(c, r, rad, Look07.SH_CHANNEL_UP, k);
                Shadows.glow(c, r, rad, Look07.GLOW_CHANNEL, k);
            }
            FILL.setShader(null);
            FILL.setColor(LogoTile.GROUND);
            c.drawRoundRect(r, rad, rad, FILL);
            edge(c, r, rad, sw, Look07.SLATE_EDGE);
        } else if (k > 0f) {
            // None: the plain row; where the remote is, a soft glass under the ring (no white pill: the logo stays seen).
            Shadows.glow(c, r, rad, Look07.GLOW_CHANNEL, k);
            FILL.setShader(null);
            FILL.setColor(Palette.alpha(0x1FFFFFFF, Math.round(0x1F * k)));
            c.drawRoundRect(r, rad, rad, FILL);
        }
        if (k > 0f) {
            // 07's focus on any colour: a 3 px white ring just outside.
            float rw = Ui.f(Look07.CHANNEL_RING), out = rw / 2f;
            R.set(r.left - out, r.top - out, r.right + out, r.bottom + out);
            RING.setStrokeWidth(rw);
            RING.setColor(Palette.alpha(Look07.L1, Math.round(255 * k)));
            c.drawRoundRect(R, rad + out, rad + out, RING);
        }
        // The logo and the marks, lifted with the cell.
        float s = 1f + (Look07.CHANNEL_LIFT - 1f) * k;
        boolean scaled = s > 1.0005f;
        if (scaled) {
            c.save();
            c.scale(s, s, cell.centerX(), cell.centerY());
        }
        logoBox(cell, reserve, B);
        String url = ch == null ? "" : LogoPack.url(ch);
        if (!Logos.drawCell(c, url, B, redraw) && ch != null && (url.isEmpty() || Pics.failed(url))) standIn(c, ch, B);
        if (marks != 0 || quality != null && !quality.isEmpty()) marks(c, cell, marks, quality);
        if (scaled) c.restore();
    }

    private static void edge(Canvas c, RectF r, float rad, float sw, int colour) {
        EDGE.setStrokeWidth(sw);
        EDGE.setColor(colour);
        R.set(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f);
        c.drawRoundRect(R, rad, rad, EDGE);
    }

    private static void paints() {
        if (nameP != null) return;
        nameP = Type.paint(22f, Type.BOLD, Look07.L1);
        chipP = Type.paint(10f, Type.BOLD, QUIET);
        chipP.setLetterSpacing(0.06f);
        names = new TextFit(nameP, 64);
    }

    /** A channel with no logo at all: its name in the logo's place, so the cell still says which channel it is. */
    private static void standIn(Canvas c, Channel ch, RectF box) {
        paints();
        String name = ChannelEdits.name(ch);
        float w = Math.min(names.width(name), box.width());
        names.line(c, name, box.centerX() - w / 2f, Type.centred(nameP, box.centerY()), box.width());
    }

    /**
     * The marks, small and quiet, in the cell's right-hand corner: down a column at its right (a cell laid out across),
     * else along its top from the right: ▶ playing (red), ★ favourite (amber), the catch-up mark and the HD (white,
     * quiet), as many as there is room for.
     */
    private static void marks(Canvas c, RectF cell, int marks, String quality) {
        paints();
        boolean across = across(cell), hd = quality != null && !quality.isEmpty();
        int n = ((marks & PLAYING) != 0 ? 1 : 0) + ((marks & FAVOURITE) != 0 ? 1 : 0) + ((marks & ARCHIVE) != 0 ? 1 : 0) + (hd ? 1 : 0);
        float p = pad(cell), step = Ui.f(MARK_STEP), room = across ? cell.height() - 2 * p : cell.width() - 2 * p;
        // As large as MARK, smaller where they would not all fit in the corner's room.
        float size = Math.min(Ui.f(MARK), (room - (n - 1) * step) / Math.max(1, n));
        float x = across ? cell.right - p - Ui.f(MARKS_W) / 2f : cell.right - p - size / 2f, y = cell.top + p + size / 2f;
        for (int i = 0; i < 4; i++) {
            float used = across ? y - cell.top - p + size / 2f : cell.right - p - x + size / 2f;
            if (used > room + 0.5f) break;
            boolean drew = true;
            if (i == 0 && (marks & PLAYING) != 0) Icons07.draw(c, Icons07.PLAY, x, y, size * 0.9f, Palette.alpha(Look07.RED, STRONG_ALPHA));
            else if (i == 1 && (marks & FAVOURITE) != 0) Icons07.draw(c, Icons07.STAR, x, y, size, Palette.alpha(Look07.AMBER, STRONG_ALPHA));
            else if (i == 2 && (marks & ARCHIVE) != 0) Icons07.draw(c, Icons07.CATCHUP, x, y, size, QUIET);
            else if (i == 3 && quality != null && !quality.isEmpty()) {
                float tw = chipP.measureText(quality);
                c.drawText(quality, x - tw / 2f, Type.centred(chipP, y), chipP);
            } else drew = false;
            if (!drew) continue;
            if (across) y += size + step;
            else x -= size + step;
        }
    }
}
