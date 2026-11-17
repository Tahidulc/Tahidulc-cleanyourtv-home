package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A film's or a series' page under its words (VodHero) and picture, as the family's other app has it: at y 813 the
 * buttons, a pill with a play shape and its words ("Play", "Resume S2 E8"), "Play from beginning" in a pill of its own
 * where something was left part-way, then round ones with an icon each (the trailer, My list's bookmark, taking it off
 * Continue watching); the remote's in the focus colour, the others in white at 8.5 %. Under them a series' seasons,
 * each a row: "Season 1" with "n / total" at its right, its episodes as 16:9 stills (400 x 225) with a caption strip
 * ("E2. Its title"), the part watched in the accent along a still's foot and TorBox's cloud on an episode only the
 * family's TorBox has. Down from the buttons the rows rise, the remote's row under the title's words with the next one
 * peeking in; Left and Right go along a row, which slides so the remote's episode is first. Homefield's More like this
 * comes last, a row of posters. Back from a row goes back to the buttons.
 */
final class DetailPage extends View {
    interface Host {
        void play(Catalog.Episode ep);

        void open(Catalog.Title t);

        /** Left from the first button: the page closes. */
        void left();

        /** How far through an episode is (0 to 1), below 0 when not started. */
        float progress(Catalog.Episode ep);
    }

    /** Icons for buttons: Dark's kinds, or Glyphs' kinds plus GLYPH. */
    static final int GLYPH = 100;

    /** One button: a pill with its words (pill) or a round one with an icon alone. */
    static final class Button {
        final String label;
        final int icon;
        final boolean pill;
        final Runnable run;

        Button(String label, int icon, boolean pill, Runnable run) {
            this.label = label;
            this.icon = icon;
            this.pill = pill;
            this.run = run;
        }
    }

    /** A row under the buttons: a season's episodes, or More like this. */
    private static final class Row {
        final String title;
        final List<Catalog.Episode> eps;
        final List<PosterArt.Item> more;
        int focus;
        float x;

        Row(String title, List<Catalog.Episode> eps, List<PosterArt.Item> more) {
            this.title = title;
            this.eps = eps;
            this.more = more;
        }

        int size() {
            return eps != null ? eps.size() : more.size();
        }
    }

    /** Places (design pixels): the buttons' middle, the rows' first top on the buttons and the remote's row's top. */
    static final float BUTTONS_Y = 840f, PILL_H = 54f, ROUND = 72f, ROWS_REST = 948f, ROW_TOP = 668f, LEFT = 52f;
    static final float EP_W = 400f, EP_H = 225f, CAPTION = 52f, EP_GAP = 16f, HEAD = 46f, AFTER = 24f;
    private static final long MOVE_MS = 200;

    private final Host host;
    private final VodHero hero;
    private Catalog.Title title;
    private Catalog.Details details;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int button, row = -1;
    private float rowsY = ROWS_REST, rowsFrom, rowsTo = ROWS_REST;
    private long rowsAt, last;

    private final PosterArt art;
    private final TextPaint pillP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), headP = Type.paint(30f, Type.MEDIUM, Palette.TEXT1);
    private final TextPaint countP = Type.paint(30f, Type.REGULAR, Palette.TEXT2), capP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;

    DetailPage(Context c, Host host, VodHero hero) {
        super(c);
        this.host = host;
        this.hero = hero;
        art = new PosterArt(Ui.f(PosterArt.landscape ? 434f : PosterGrid.POSTER_W));
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    /** The title, its details (null until read), its buttons, more like it, the episode to start on ("" none). */
    void set(Catalog.Title t, Catalog.Details d, List<Button> list, List<PosterArt.Item> moreLike, String resumeId) {
        boolean first = title == null || details == null && d != null;
        title = t;
        details = d;
        buttons.clear();
        buttons.addAll(list);
        button = Math.max(0, Math.min(button, buttons.size() - 1));
        List<Row> was = new ArrayList<>(rows);
        rows.clear();
        if (d != null && t.series) {
            for (Catalog.Season s : d.seasons) {
                if (s.episodes.isEmpty()) continue;
                rows.add(new Row(seasonName(s), s.episodes, null));
            }
        }
        if (!moreLike.isEmpty()) rows.add(new Row("More like this", null, moreLike));
        // The remote kept where it was along each row; on the episode to start on the first time.
        for (int i = 0; i < rows.size() && i < was.size(); i++) {
            if (rows.get(i).title.equals(was.get(i).title)) {
                rows.get(i).focus = Math.min(was.get(i).focus, rows.get(i).size() - 1);
                rows.get(i).x = rows.get(i).focus;
            }
        }
        if (first && resumeId != null && !resumeId.isEmpty()) {
            for (Row rw : rows) {
                if (rw.eps == null) continue;
                for (int k = 0; k < rw.eps.size(); k++) {
                    if (rw.eps.get(k).id.equals(resumeId)) {
                        rw.focus = k;
                        rw.x = k;
                    }
                }
            }
        }
        if (row >= rows.size()) row = rows.size() - 1;
        settle(true);
        invalidate();
    }

    /** Pure: a season's name as the other app writes it: "Season 2" (a provider's "Series 2" too). */
    static String seasonName(Catalog.Season s) {
        String n = s.name == null ? "" : s.name.trim();
        if (n.isEmpty() || n.matches("(?i)(series|season|s)\\s*0*" + s.number)) return "Season " + s.number;
        return n;
    }

    // ---- moving -------------------------------------------------------------------------------------------------

    private float rowPitch(Row rw) {
        float card = rw.eps != null ? EP_H + CAPTION : art.tileH() / Ui.s;
        return HEAD + card + AFTER;
    }

    /** The rows' first top for where the remote is: under the buttons, or the remote's row under the title's words. */
    private void settle(boolean now) {
        float target = ROWS_REST;
        if (row >= 0) {
            float y = ROW_TOP;
            for (int i = 0; i < row; i++) y -= rowPitch(rows.get(i));
            target = y;
        }
        hero.bottom = row >= 0 ? ROW_TOP - 24f : 790f;
        hero.invalidate();
        if (now || Exec.inline) {
            rowsY = rowsFrom = rowsTo = target;
            rowsAt = 0;
        } else if (target != rowsTo) {
            rowsFrom = rowsY;
            rowsTo = target;
            rowsAt = SystemClock.uptimeMillis();
        }
    }

    boolean key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row + 1 < rows.size()) {
                    row++;
                    settle(false);
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row >= 0) {
                    row--;
                    settle(false);
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (row < 0) {
                    if (dir < 0 && button == 0) host.left();
                    else button = Math.max(0, Math.min(buttons.size() - 1, button + dir));
                } else {
                    Row rw = rows.get(row);
                    rw.focus = Math.max(0, Math.min(rw.size() - 1, rw.focus + dir));
                }
                invalidate();
                return true;
            }
            default:
                return false;
        }
    }

    void ok() {
        if (row < 0) {
            if (button < buttons.size()) buttons.get(button).run.run();
            return;
        }
        Row rw = rows.get(row);
        if (rw.eps != null && rw.focus < rw.eps.size()) host.play(rw.eps.get(rw.focus));
        else if (rw.more != null && rw.focus < rw.more.size()) host.open(rw.more.get(rw.focus).title);
    }

    boolean back() {
        if (row >= 0) {
            row = -1;
            settle(false);
            invalidate();
            return true;
        }
        return false;
    }

    /** Previews: the remote on a row (-1: the buttons) and along it. */
    void focus(int rowIndex, int along) {
        row = Math.min(rowIndex, rows.size() - 1);
        if (row >= 0) {
            Row rw = rows.get(row);
            rw.focus = Math.max(0, Math.min(along, rw.size() - 1));
            rw.x = rw.focus;
        }
        settle(true);
        invalidate();
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (title == null) return;
        long now = SystemClock.uptimeMillis();
        boolean moving = false;
        if (rowsAt != 0) {
            float k = Math.min(1f, (now - rowsAt) / (float) MOVE_MS);
            rowsY = rowsFrom + (rowsTo - rowsFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) rowsAt = 0;
            else moving = true;
        }
        float dt = last == 0 ? 16f : Math.min(48f, now - last);
        last = now;
        float step = Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0);
        for (Row rw : rows) {
            if (Math.abs(rw.focus - rw.x) > 0.002f) {
                rw.x += (rw.focus - rw.x) * step;
                moving = true;
            } else {
                rw.x = rw.focus;
            }
        }
        if (!moving) last = 0;
        // The buttons: under the title's words while the remote is on them, gone with the rows up.
        if (row < 0) drawButtons(c);
        // The rows, from under the title's words down.
        c.save();
        c.clipRect(0, Ui.f(ROW_TOP - 16f), getWidth(), getHeight());
        float y = rowsY;
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            float p = rowPitch(rw);
            if (y > 1080f) break;
            if (y + p > ROW_TOP - 16f) drawRow(c, rw, y, i == row);
            y += p;
        }
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    private void drawButtons(Canvas c) {
        float x = 48f;
        float cy = Ui.f(BUTTONS_Y);
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean on = i == button;
            if (b.pill) {
                pillP.setColor(Palette.TEXT1);
                float tw = pillP.measureText(b.label) / Ui.s;
                float w = 28f + 26f + 18f + tw + 32f;
                r.set(Ui.f(x), cy - Ui.f(PILL_H / 2f), Ui.f(x + w), cy + Ui.f(PILL_H / 2f));
                fill.setColor(on ? Palette.FOCUS : Palette.SELECTED);
                c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
                icon(c, b.icon, Ui.f(x + 28f + 13f), cy, Ui.f(26f));
                c.drawText(b.label, Ui.f(x + 28f + 26f + 18f), Type.centred(pillP, cy), pillP);
                x += w + 24f;
            } else {
                float cx = x + ROUND / 2f;
                if (on) {
                    Focus.circle(c, Ui.f(cx), cy, Ui.f(ROUND));
                } else {
                    fill.setColor(Palette.SELECTED);
                    c.drawCircle(Ui.f(cx), cy, Ui.f(ROUND) / 2f, fill);
                }
                icon(c, b.icon, Ui.f(cx), cy, Ui.f(32f));
                x += ROUND + 24f;
            }
        }
    }

    /** A button's icon: Dark's kinds, or Glyphs' (GLYPH + kind). */
    private static void icon(Canvas c, int icon, float cx, float cy, float s) {
        if (icon >= GLYPH) Glyphs.draw(c, icon - GLYPH, cx, cy, s, Palette.TEXT1);
        else if (icon >= 0) RoundButton.glyph(c, icon, cx, cy, s, Palette.TEXT1);
    }

    private void drawRow(Canvas c, Row rw, float y, boolean here) {
        // The row's name in white where the remote is, grey elsewhere (the first row's too while the remote is on the buttons).
        headP.setColor(here ? Palette.TEXT1 : Palette.TEXT2);
        c.drawText(Ui.fit(rw.title, headP, Ui.f(1400)), Ui.f(LEFT + 4f), Ui.f(y + 30f), headP);
        // "n / total": where the remote is along the row, on its row only, and in grey on the first row under the buttons.
        if (here || row < 0 && rows.indexOf(rw) == 0) {
            String n = (rw.focus + 1) + "", of = " / " + rw.size();
            countP.setColor(Palette.TEXT2);
            float ow = countP.measureText(of), right = Ui.f(1864);
            c.drawText(of, right - ow, Ui.f(y + 30f), countP);
            countP.setColor(here ? Palette.TEXT1 : Palette.TEXT2);
            c.drawText(n, right - ow - countP.measureText(n), Ui.f(y + 30f), countP);
        }
        float top = y + HEAD;
        if (rw.eps != null) {
            float pitch = EP_W + EP_GAP;
            int from = Math.max(0, (int) Math.floor(rw.x) - 1), to = Math.min(rw.size() - 1, from + 6);
            for (int i = from; i <= to; i++) {
                float x = LEFT + (i - rw.x) * pitch;
                if (x > 1920f) break;
                drawEpisode(c, rw.eps.get(i), Ui.f(x), Ui.f(top), here && i == rw.focus);
            }
        } else {
            float pitch = art.w / Ui.s + PosterGrid.GAP;
            int from = Math.max(0, (int) Math.floor(rw.x) - 1), to = Math.min(rw.size() - 1, from + 9);
            for (int i = from; i <= to; i++) {
                float x = LEFT + (i - rw.x) * pitch;
                if (x > 1920f) break;
                art.draw(c, rw.more.get(i), Ui.f(x), Ui.f(top), here && i == rw.focus, 255, redraw);
            }
        }
    }

    /** An episode: its still (or the series' picture), the caption strip "E2. Its title", the part watched, the cloud. */
    private void drawEpisode(Canvas c, Catalog.Episode e, float x, float y, boolean on) {
        float w = Ui.f(EP_W), h = Ui.f(EP_H), cap = Ui.f(CAPTION), rad = Ui.f(10);
        c.save();
        clip.reset();
        r.set(x, y, x + w, y + h + cap);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        String still = e.still.isEmpty() ? (details != null && !details.backdrop.isEmpty() ? details.backdrop : title.backdrop) : e.still;
        Bitmap b = still.isEmpty() ? null : Pics.get(still, Math.round(w), Math.round(h), Pics.CROP, redraw);
        r.set(x, y, x + w, y + h);
        if (b != null) {
            c.drawBitmap(b, null, r, bmp);
        } else {
            fill.setColor(PosterArt.blank(title.name + e.number));
            c.drawRect(r, fill);
        }
        float p = host.progress(e);
        if (p >= 0f) {
            float bh = Ui.f(5), inset = Ui.f(16), by = y + h - Ui.f(14);
            fill.setColor(0x80000000);
            c.drawRect(x + inset, by - bh, x + w - inset, by, fill);
            fill.setColor(Palette.ACCENT);
            c.drawRect(x + inset, by - bh, x + inset + (w - inset * 2) * Math.max(0.03f, Math.min(1f, p)), by, fill);
        }
        fill.setColor(on ? Palette.over(Palette.GROUND, Palette.FOCUS) : Palette.PANEL);
        c.drawRect(x, y + h, x + w, y + h + cap, fill);
        c.restore();
        if (e.torbox) {
            // An episode only the family's TorBox has: its cloud on the still's top left.
            float d = Ui.f(36);
            SourcePicker.badge(c, x + Ui.f(12) + d / 2f, y + Ui.f(12) + d / 2f, d, 255);
        }
        String t = "E" + e.number + ". " + (e.title.isEmpty() ? "Episode " + e.number : e.title);
        capP.setColor(Palette.TEXT1);
        c.drawText(PosterArt.fitDots(t, capP, w - Ui.f(24)), x + Ui.f(12), y + h + cap / 2f + capP.getTextSize() * 0.36f, capP);
        if (on) {
            r.set(x, y, x + w, y + h + cap);
            Focus.border(c, r, rad);
        }
    }

    /** Pure: "1h 7m, 9 Oct 2022", either half alone, or "". */
    static String episodeFacts(int minutes, String aired) {
        String d = "";
        if (aired != null && aired.length() >= 10) {
            try {
                d = LocalDate.parse(aired.substring(0, 10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK));
            } catch (RuntimeException ignored) {
                d = "";
            }
        }
        String m = Ui.duration(minutes);
        if (m.isEmpty()) return d;
        return d.isEmpty() ? m : m + ", " + d;
    }
}
