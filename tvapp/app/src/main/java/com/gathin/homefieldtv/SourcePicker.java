package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Formats;
import com.gathin.homefieldtv.engine.TorBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where a film or episode can play from, as the phone's source sheet: TorBox's copies (a green mark with a cloud) and
 * the IPTV playlists' (a blue one with a screen and the playlist's name), so that the two can't be mistaken; beside each
 * mark the logos of the picture, its HDR and its sound as its name says them (4K Ultra HD, Dolby Vision, Dolby Atmos…)
 * and the file's kind, under it the file's name and size. While a film plays (open: the player's Sources, or Up on the
 * plain picture; a card at the right, the one playing ticked "Playing"; OK plays the one chosen from the same moment,
 * Back shuts it; it goes by itself after a while untouched). Before playing, the cover (CoverPage) lists them. In the 07 look of the player's pickers: a card of 07's dark material, rounded 40, the remote's row
 * white with dark words, its marks' logos inked dark on it.
 */
final class SourcePicker extends View {
    interface Pick {
        void picked(int index);
    }

    /** The marks' own colours (neither service's): TorBox's green, the playlists' blue; the headings' lighter ones. */
    static final int TORBOX = 0xFF0E9F6E, IPTV = 0xFF3B5BDB, TORBOX_SOFT = 0xFF5EE0B0, IPTV_SOFT = 0xFF91A7FF;
    private static final long IDLE_MS = 20_000;
    private static final float X = 1032f, W = 840f, TOP = 48f, BOTTOM = 1032f, HEAD = 138f, ROW = 100f, GROUP = 46f, FOOT = 64f,
            RADIUS = 40f;

    private final List<Catalog.Play> plays = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int playing, focus;
    private float scroll;
    private Pick pick;
    private boolean open;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable idle = this::close;
    private final Mark mark = new Mark();
    private final Frost card = new Frost(Look07.MAT_DARK);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chip = Dark.line(0x66FFFFFF, 1.5f), tick = Dark.line(Dark.TEXT, 3.2f);
    /** The logos on the remote's white row: inked dark. */
    private final android.graphics.ColorFilter ink = new android.graphics.PorterDuffColorFilter(Look07.INK,
            android.graphics.PorterDuff.Mode.SRC_IN);
    private final TextPaint title = Type.paint(Type.PICKER, Type.MEDIUM, Palette.TEXT1), sub = Dark.text(22, 500, Dark.MUTED);
    private final TextPaint chipP = Dark.text(20, 700, Dark.SOFT), status = Dark.text(22, 700, Dark.TEXT);
    private final TextPaint detail = Dark.text(21, 500, Dark.MUTED), hint = Dark.text(21, 500, Dark.MUTED);
    private final TextPaint torboxHead = Dark.text(19, 700, TORBOX_SOFT), iptvHead = Dark.text(19, 700, IPTV_SOFT);
    private final TextPaint markP = Dark.text(19, 700, 0xFFFFFFFF), best = Dark.text(22, 700, Dark.MUTED);
    private final Paint markLine = Dark.line(0xBFFFFFFF, 2f), logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF logoBox = new RectF();
    private final android.graphics.Rect logoSrc = new android.graphics.Rect();
    private final RectF r = new RectF();
    private final Path check = new Path();

    /** One line of the panel: a source's heading (play -1), or a copy (its place in the plays). */
    private static final class Line {
        final int play;
        final String heading;
        final boolean torbox;

        Line(int play, String heading, boolean torbox) {
            this.play = play;
            this.heading = heading;
            this.torbox = torbox;
        }
    }

    SourcePicker(Context c) {
        super(c);
        setVisibility(GONE);
        setFocusable(false);
        Shadows.prepare(Look07.SH_PANEL, RADIUS);
        Shadows.prepare(Look07.SH_ROW, Look07.R_ROW);
        torboxHead.setLetterSpacing(0.08f);
        iptvHead.setLetterSpacing(0.08f);
    }

    boolean isOpen() {
        return open;
    }

    /** Open on the copy playing (its place in plays), the remote on it. */
    void open(List<Catalog.Play> copies, int now, Pick p) {
        if (copies.isEmpty()) return;
        plays.clear();
        plays.addAll(copies);
        playing = now;
        pick = p;
        lines.clear();
        List<List<Integer>> groups = groups(plays);
        for (List<Integer> g : groups) {
            Catalog.Play first = plays.get(g.get(0));
            // Each source under its own heading when there are several.
            if (groups.size() > 1) lines.add(new Line(-1, heading(first), first.torbox));
            for (int i : g) lines.add(new Line(i, "", plays.get(i).torbox));
        }
        focus = 0;
        // The one playing, or before playing the best: the first.
        int on = playing >= 0 ? playing : 0;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).play == on) focus = i;
        if (lines.get(focus).play < 0) focus = next(focus, 1);
        scroll = 0;
        open = true;
        setVisibility(VISIBLE);
        setAlpha(1f);
        poke();
        invalidate();
    }

    void close() {
        h.removeCallbacks(idle);
        if (!open) return;
        open = false;
        pick = null;
        setVisibility(GONE);
    }

    private void poke() {
        h.removeCallbacks(idle);
        // Over a film it goes by itself after a while.
        h.postDelayed(idle, IDLE_MS);
    }

    /** A key while open: true when it was the panel's (the volume keys go on to the TV). */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        poke();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                focus = next(focus, -1);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                focus = next(focus, 1);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) ok();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                close();
                return true;
            default:
                return true;
        }
    }

    /** The copy the remote is on chosen, and the panel shut. */
    void ok() {
        Pick p = pick;
        int chosen = focus < lines.size() ? lines.get(focus).play : -1;
        close();
        if (p != null && chosen >= 0) p.picked(chosen);
    }

    /** The next copy's line from line i that way (headings passed over), or i at the end. */
    private int next(int i, int dir) {
        for (int j = i + dir; j >= 0 && j < lines.size(); j += dir) if (lines.get(j).play >= 0) return j;
        return i;
    }

    // ---- words --------------------------------------------------------------------------------------------------

    /** Pure: the copies' places grouped by where they come from (TorBox, each playlist), in the order each first comes. */
    static List<List<Integer>> groups(List<Catalog.Play> plays) {
        List<String> keys = new ArrayList<>();
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < plays.size(); i++) {
            Catalog.Play p = plays.get(i);
            String key = p.torbox ? "torbox" : "iptv:" + p.playlist;
            int at = keys.indexOf(key);
            if (at < 0) {
                keys.add(key);
                out.add(new ArrayList<>());
                at = out.size() - 1;
            }
            out.get(at).add(i);
        }
        return out;
    }

    /** Pure: a source's heading ("FROM YOUR TORBOX", "FROM YOUR IPTV PLAYLIST · HOME"). */
    static String heading(Catalog.Play p) {
        if (p.torbox) return "FROM YOUR TORBOX";
        return p.playlist.isEmpty() ? "FROM YOUR IPTV PLAYLIST" : "FROM YOUR IPTV PLAYLIST · " + p.playlist.toUpperCase(Locale.UK);
    }

    /** Pure: a copy's second line: its file's (or entry's) name and its size ("Glass.Harbour.2024.2160p.mkv · 14.2 GB"). */
    static String detail(Catalog.Play p) {
        String size = size(p.size);
        if (p.name.isEmpty()) return size;
        return size.isEmpty() ? p.name : p.name + "  ·  " + size;
    }

    /** Pure: a size in words ("14.2 GB", "850 MB"; "" when not known). */
    static String size(long bytes) {
        if (bytes <= 0) return "";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 1000) return Math.max(1, Math.round(mb)) + " MB";
        return String.format(Locale.UK, "%.1f GB", mb / 1024.0);
    }

    /**
     * Pure: the marks beside a copy's mark, as the phone's (Marks): its picture ("4K", "1080p", "720p", "SD"), its HDR
     * ("Dolby Vision", "HDR10+", "HDR10", "HLG", "HDR"), its sound ("Dolby Atmos"; "Dolby" and its channels for Dolby's
     * other sound; else its words, "DTS-HD 7.1", "AAC Stereo", "5.1"), then its file's kind ("MKV"). Those with a logo
     * are drawn with it (logo()), the rest in words.
     */
    static List<String> marks(Catalog.Play p) {
        List<String> out = new ArrayList<>();
        String q = Catalog.pictureName(p.picture);
        if (!q.isEmpty()) out.add(q);
        // A provider's page may say SDR: no mark for it.
        if (p.hdr != null && !p.hdr.isEmpty() && !p.hdr.equals("SDR")) out.add(p.hdr);
        String a = p.sound == null ? "" : p.sound;
        if (a.startsWith("Dolby Atmos")) {
            out.add("Dolby Atmos");
        } else if (a.startsWith("Dolby")) {
            out.add("Dolby");
            String ch = Formats.channelsOf(a);
            if (!ch.isEmpty()) out.add(ch);
        } else if (!a.isEmpty()) {
            out.add(a);
        }
        if (p.kind != null && !p.kind.isEmpty()) out.add(p.kind);
        return out;
    }

    /** Pure: a mark's logo (a drawable), or 0 for one drawn in words. */
    static int logo(String mark) {
        switch (mark) {
            case "4K": return R.drawable.fmt_4k;
            case "1080p": return R.drawable.fmt_1080;
            case "720p": return R.drawable.fmt_720;
            case "SD": return R.drawable.fmt_sd;
            case "Dolby Vision": return R.drawable.fmt_dolby_vision;
            case "HDR10+": return R.drawable.fmt_hdr10plus;
            case "HDR10": return R.drawable.fmt_hdr10;
            case "HLG": return R.drawable.fmt_hlg;
            case "Dolby Atmos": return R.drawable.fmt_dolby_atmos;
            case "Dolby": return R.drawable.fmt_dolby;
            default: return 0;
        }
    }

    /** How much of its box's height a logo takes: Dolby's long and low, HDR10's tallest (to carry its small "10"). */
    private static float logoHeight(int logo) {
        if (logo == R.drawable.fmt_hdr10 || logo == R.drawable.fmt_hdr10plus) return 0.8f;
        if (logo == R.drawable.fmt_dolby || logo == R.drawable.fmt_dolby_atmos || logo == R.drawable.fmt_dolby_vision) return 0.6f;
        return 0.7f;
    }

    /** The logos, read once (they're small). */
    private static final java.util.Map<Integer, Bitmap> LOGOS = new java.util.HashMap<>();

    private Bitmap bitmap(int logo) {
        Bitmap b = LOGOS.get(logo);
        if (b == null && !LOGOS.containsKey(logo)) {
            try {
                b = BitmapFactory.decodeResource(getResources(), logo);
            } catch (RuntimeException e) {
                b = null;
            }
            LOGOS.put(logo, b);
        }
        return b;
    }

    /** Pure: what its name says of a copy beside its mark ("1080p · MKV"; "" when it says nothing). */
    static String facts(Catalog.Play p) {
        String q = Catalog.pictureName(p.picture);
        if (q.isEmpty()) return p.kind;
        return p.kind.isEmpty() ? q : q + "  ·  " + p.kind;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private float lineHeight(Line l) {
        return l.play < 0 ? GROUP : ROW;
    }

    @Override protected void onDraw(Canvas c) {
        if (!open || lines.isEmpty()) return;
        float x = Ui.f(X), w = Ui.f(W);
        r.set(x, Ui.f(TOP), x + w, Ui.f(BOTTOM));
        // As the player's pickers: a card of 07's dark material over the film.
        Shadows.draw(c, r, Ui.f(RADIUS), Look07.SH_PANEL, 1f);
        card.draw(c, r, Ui.f(RADIUS), 1f);
        title.setColor(Palette.TEXT1);
        c.drawText("Sources", x + Ui.f(48), Ui.f(TOP + 70), title);
        String count = plays.size() + " copies  ·  " + (plays.get(0).torbox ? "TorBox's first" : "your playlist's first")
                + "  ·  another carries on from here";
        c.drawText(Ui.fit(count, sub, w - Ui.f(96)), x + Ui.f(48), Ui.f(TOP + 108), sub);

        // The list scrolled so that the line the remote is on stays in sight.
        float listTop = TOP + HEAD, listBottom = BOTTOM - FOOT, room = listBottom - listTop;
        float y = 0, focusTop = 0, focusBottom = 0, total = 0;
        for (int i = 0; i < lines.size(); i++) {
            float lh = lineHeight(lines.get(i));
            if (i == focus) {
                focusTop = y;
                focusBottom = y + lh;
            }
            y += lh;
        }
        total = y;
        if (focusBottom - scroll > room) scroll = focusBottom - room;
        if (focusTop - scroll < 0) scroll = focusTop;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - room)));

        c.save();
        c.clipRect(x, Ui.f(listTop), x + w, Ui.f(listBottom));
        float ly = listTop - scroll;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            float lh = lineHeight(l);
            if (ly + lh >= listTop && ly <= listBottom) {
                if (l.play < 0) drawHeading(c, l, x, ly);
                else drawCopy(c, l.play, i == focus, x, w, ly);
            }
            ly += lh;
        }
        c.restore();
        c.drawText("OK plays it from here  ·  Back closes", x + Ui.f(48), Ui.f(BOTTOM - 26), hint);
    }

    private void drawHeading(Canvas c, Line l, float x, float y) {
        TextPaint p = l.torbox ? torboxHead : iptvHead;
        c.drawText(Ui.fit(l.heading, p, Ui.f(W - 80)), x + Ui.f(40), Ui.f(y + 32), p);
    }

    private void drawCopy(Canvas c, int index, boolean on, float x, float w, float y) {
        Catalog.Play p = plays.get(index);
        r.set(x + Ui.f(22), Ui.f(y + 4), x + w - Ui.f(22), Ui.f(y + ROW - 4));
        // Where the remote is: 07's white row (its words dark), lifted on a soft shadow.
        if (on) {
            Shadows.draw(c, r, Ui.f(Look07.R_ROW), Look07.SH_ROW, 1f);
            lit.setColor(Look07.FOCUS_FILL);
            c.drawRoundRect(r, Ui.f(Look07.R_ROW), Ui.f(Look07.R_ROW), lit);
        }
        float left = x + Ui.f(44), right = x + w - Ui.f(44);
        float cy = Ui.f(y + 38);
        float mx = left + mark.draw(c, left, cy, Ui.f(38), p.torbox, p.playlist, Ui.f(240));
        // Its picture's, HDR's and sound's logos (and its file's kind) beside the mark, as far as there is room.
        String flag = index == playing ? "Playing" : playing < 0 && index == 0 && plays.size() > 1 ? "Best match" : "";
        // The marks have the line's whole width; what it is (Playing, Best match) is at the end of the line under them.
        float room = right, fy = Ui.f(y + 74);
        for (String m : marks(p)) {
            float mw = markWidth(m);
            if (mx + Ui.f(12) + mw > room) break;
            drawMark(c, m, mx + Ui.f(12), cy, on);
            mx += Ui.f(12) + mw;
        }
        if (flag.equals("Best match")) {
            float tw = best.measureText(flag);
            best.setColor(on ? Look07.INK2 : Dark.MUTED);
            c.drawText(flag, right - tw, fy + Ui.f(8), best);
        }
        if (index == playing) {
            // The one playing: a tick and its word at the line's end (07's green, dark on the white row).
            String word = "Playing";
            float tw = status.measureText(word);
            status.setColor(on ? Look07.INK : Dark.TEXT);
            c.drawText(word, right - tw, fy + Ui.f(8), status);
            float tx = right - tw - Ui.f(30);
            check.reset();
            check.moveTo(tx - Ui.f(10), fy + Ui.f(1));
            check.lineTo(tx - Ui.f(2), fy + Ui.f(9));
            check.lineTo(tx + Ui.f(12), fy - Ui.f(9));
            tick.setColor(on ? Look07.INK : Look07.GREEN);
            c.drawPath(check, tick);
        }
        String d = detail(p);
        detail.setColor(on ? Look07.INK2 : Dark.MUTED);
        float flagW = flag.isEmpty() ? 0f : (flag.equals("Playing") ? status : best).measureText(flag) + Ui.f(64);
        if (!d.isEmpty()) c.drawText(Ui.fit(d, detail, right - left - flagW), left, Ui.f(y + 80), detail);
    }

    private static final float MARK_H = 34f;

    /** A mark's width in its box: its logo's, or its words'. */
    private float markWidth(String m) {
        float bh = Ui.f(MARK_H);
        int logo = logo(m);
        Bitmap b = logo == 0 ? null : bitmap(logo);
        if (b != null && b.getHeight() > 0) return bh * logoHeight(logo) * b.getWidth() / b.getHeight() + bh * 0.6f;
        return markP.measureText(m) + bh * 0.56f;
    }

    /** A mark in its outlined box, MARK_H tall, from x, centred on cy: its logo in white (dark on the white row), or its words. */
    private void drawMark(Canvas c, String m, float x, float cy, boolean on) {
        float bh = Ui.f(MARK_H), w = markWidth(m);
        logoBox.set(x, cy - bh / 2f, x + w, cy + bh / 2f);
        markLine.setColor(on ? 0xB3111111 : 0xBFFFFFFF);
        markP.setColor(on ? Look07.INK : 0xFFFFFFFF);
        logoPaint.setColorFilter(on ? ink : null);
        c.drawRoundRect(logoBox, bh * 0.2f, bh * 0.2f, markLine);
        int logo = logo(m);
        Bitmap b = logo == 0 ? null : bitmap(logo);
        if (b != null && b.getHeight() > 0) {
            float lh = bh * logoHeight(logo), lw = lh * b.getWidth() / b.getHeight();
            logoSrc.set(0, 0, b.getWidth(), b.getHeight());
            logoBox.set(x + bh * 0.3f, cy - lh / 2f, x + bh * 0.3f + lw, cy + lh / 2f);
            c.drawBitmap(b, logoSrc, logoBox, logoPaint);
        } else {
            c.drawText(m, x + bh * 0.28f, cy + markP.getTextSize() * 0.36f, markP);
        }
    }

    /** An outlined chip ("1080p", "MKV") at x, centred on cy; its width. */
    private float chip(Canvas c, String words, float x, float cy) {
        float tw = chipP.measureText(words), cw = tw + Ui.f(20), ch = Ui.f(30);
        r.set(x, cy - ch / 2f, x + cw, cy + ch / 2f);
        c.drawRoundRect(r, Ui.f(8), Ui.f(8), chip);
        c.drawText(words, x + Ui.f(10), cy + Ui.f(7), chipP);
        return cw;
    }

    // ---- the marks ------------------------------------------------------------------------------------------------

    /**
     * Where a copy comes from, h tall: a filled pill, TorBox's green with a cloud ("TorBox"), or the playlists' blue with
     * a screen ("IPTV", and the playlist's name after it). Drawn here and on the player's bar.
     */
    static final class Mark {
        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint kind = Dark.text(20, 700, 0xFFFFFFFF), which = Dark.text(20, 500, 0xD9FFFFFF);
        private final RectF box = new RectF();

        Mark() {
            glyph.setColor(0xFFFFFFFF);
            glyphLine.setColor(0xFFFFFFFF);
            glyphLine.setStyle(Paint.Style.STROKE);
            glyphLine.setStrokeCap(Paint.Cap.ROUND);
            glyphLine.setStrokeJoin(Paint.Join.ROUND);
        }

        private void size(float h) {
            kind.setTextSize(h * 0.48f);
            which.setTextSize(h * 0.48f);
        }

        private String rest(boolean torbox, String playlist, float maxW, float used) {
            if (torbox || playlist == null || playlist.isEmpty()) return "";
            return Ui.fit("  ·  " + playlist, which, Math.max(0f, maxW - used));
        }

        /** Its width at h tall (the playlist's name cut to fit maxW). */
        float width(float h, boolean torbox, String playlist, float maxW) {
            size(h);
            float used = h * 0.34f + h * 0.62f + h * 0.2f + kind.measureText(torbox ? TorBox.NAME : "IPTV");
            return used + which.measureText(rest(torbox, playlist, maxW, used + h * 0.42f)) + h * 0.42f;
        }

        /** Drawn from x, centred on cy, h tall; its width. */
        float draw(Canvas c, float x, float cy, float h, boolean torbox, String playlist, float maxW) {
            float w = width(h, torbox, playlist, maxW);
            pill.setColor(torbox ? TORBOX : IPTV);
            box.set(x, cy - h / 2f, x + w, cy + h / 2f);
            c.drawRoundRect(box, h / 2f, h / 2f, pill);
            float gs = h * 0.62f, gx = x + h * 0.34f + gs / 2f;
            if (torbox) cloud(c, gx, cy, gs);
            else screen(c, gx, cy, gs);
            float tx = x + h * 0.34f + gs + h * 0.2f, base = cy + h * 0.17f;
            String k = torbox ? TorBox.NAME : "IPTV";
            c.drawText(k, tx, base, kind);
            float used = tx - x + kind.measureText(k);
            String more = rest(torbox, playlist, maxW, used + h * 0.42f);
            if (!more.isEmpty()) c.drawText(more, tx + kind.measureText(k), base, which);
            return w;
        }

        /** A cloud s across, centred on (cx, cy): three puffs on a flat foot. */
        private void cloud(Canvas c, float cx, float cy, float s) {
            cloud(c, cx, cy, s, glyph, box);
        }

        /** The same in a paint given (on the main thread: box is any rectangle to draw with). */
        static void cloud(Canvas c, float cx, float cy, float s, Paint p, RectF box) {
            c.drawCircle(cx - s * 0.2f, cy + s * 0.06f, s * 0.2f, p);
            c.drawCircle(cx + s * 0.03f, cy - s * 0.08f, s * 0.27f, p);
            c.drawCircle(cx + s * 0.26f, cy + s * 0.08f, s * 0.18f, p);
            box.set(cx - s * 0.4f, cy + s * 0.04f, cx + s * 0.44f, cy + s * 0.26f);
            c.drawRoundRect(box, s * 0.11f, s * 0.11f, p);
        }

        /** A screen s across, centred on (cx, cy): an outlined picture on a short stand. */
        private void screen(Canvas c, float cx, float cy, float s) {
            glyphLine.setStrokeWidth(Math.max(1f, s * 0.11f));
            box.set(cx - s * 0.42f, cy - s * 0.3f, cx + s * 0.42f, cy + s * 0.2f);
            c.drawRoundRect(box, s * 0.08f, s * 0.08f, glyphLine);
            c.drawLine(cx - s * 0.2f, cy + s * 0.38f, cx + s * 0.2f, cy + s * 0.38f, glyphLine);
        }
    }

    private static final Paint BADGE = new Paint(Paint.ANTI_ALIAS_FLAG), BADGE_INK = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BADGE_BOX = new RectF();

    static {
        BADGE.setColor(TORBOX);
        BADGE_INK.setColor(0xFFFFFFFF);
    }

    /**
     * The round green TorBox badge with its cloud, d across, centred on (cx, cy), at an opacity: on a poster or an
     * episode of the family's own that no playlist has. On the main thread.
     */
    static void badge(Canvas c, float cx, float cy, float d, int alpha) {
        BADGE.setAlpha(alpha);
        BADGE_INK.setAlpha(alpha);
        c.drawCircle(cx, cy, d / 2f, BADGE);
        Mark.cloud(c, cx, cy + d * 0.02f, d * 0.62f, BADGE_INK, BADGE_BOX);
    }
}
