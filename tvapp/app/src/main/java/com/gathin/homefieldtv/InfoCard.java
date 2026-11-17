package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
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
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.util.List;

/**
 * The player's info card, as the family's TiviMate draws it ("Card style"): 1680 x 281 at x 120 in the playback panel
 * colour (Palette.PLAYBACK, rounded 16), at the foot (FOOT_Y 767: the banner, on INFO, after a channel change, and for
 * the channel before or after on CH− and CH+) or at the top (TOP_Y 32: OK's overlay, catch-up's).
 *
 * <pre>
 *   the channel's logo           in the 180 x 104 box at (152, +39), the channel's name under it (28, two lines)
 *   the programme's title        x 456, its foot 65 down (32, medium); the date and clock right-aligned to 1766 (30)
 *   the line under it, 118 down  "10:00 — 11:00" (grey), the programme's bar (80 x 6) and "29 min" left (grey), then
 *                                "Royal • TNT Sports" (the playlist and the group, white) and the picture's and sound's
 *                                chips (FHD, 50 FPS, STEREO); from the archive: the catch-up mark and the whole length
 *   what it is about             two lines in grey (26) from 137 down, 33 a line
 *   what's next                  248 down: "11:00 — 12:00" (grey) and its title
 * </pre>
 *
 * One card draws for one view (its logos ask that view to draw again when they come). Lines are laid out once for a
 * programme and kept.
 */
final class InfoCard {
    static final float X = 120f, W = 1680f, H = 281f, FOOT_Y = 767f, TOP_Y = 32f, RADIUS = 16f;
    static final float LOGO_X = 152f, LOGO_W = 180f, LOGO_H = 104f, TEXT_X = 456f;

    private final View owner;
    private final Runnable redraw;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint iconLine = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(32f, Type.MEDIUM, Palette.TEXT1), dateP = Type.paint(30f, Type.REGULAR, Palette.TEXT1);
    private final TextPaint greyP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2), whiteP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final TextPaint nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), descP = Type.paint(26f, Type.REGULAR, Palette.TEXT2);
    private final TextPaint logoP = Type.paint(Type.ITEM, Type.MEDIUM, Palette.TEXT1);
    private final RectF r = new RectF();
    /** Laid out once: the channel's name (for nameKey), what the programme is about (for descKey). */
    private String nameKey = "", descKey = "", whereKey = "", whereText = "";
    private StaticLayout nameL, descL;

    InfoCard(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        iconLine.setStyle(Paint.Style.STROKE);
        iconLine.setStrokeCap(Paint.Cap.ROUND);
        iconLine.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * The card with its top at top (design pixels) for channel ch: the programme on at time at (now when live; the
     * picture's time from the archive), the archive's look when archived, the chips given (none for another channel).
     */
    void draw(Canvas c, float top, Channel ch, long at, boolean archived, List<String> chips) {
        if (ch == null) return;
        Context ctx = owner.getContext();
        long now = System.currentTimeMillis();
        r.set(Ui.f(X), Ui.f(top), Ui.f(X + W), Ui.f(top + H));
        card.setColor(Palette.PLAYBACK);
        c.drawRoundRect(r, Ui.f(RADIUS), Ui.f(RADIUS), card);

        // The channel: its logo, its name under it.
        Bitmap logo = Pics.get(ch.logo, Ui.px(LOGO_W), Ui.px(LOGO_H), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
        float lx = Ui.f(LOGO_X), ly = Ui.f(top + 39f);
        if (logo != null) {
            c.drawBitmap(logo, lx + (Ui.f(LOGO_W) - logo.getWidth()) / 2f, ly + (Ui.f(LOGO_H) - logo.getHeight()) / 2f, bmp);
        } else {
            logoP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(initials(ch.name), logoP, Ui.f(LOGO_W)), lx, ly + Ui.f(LOGO_H / 2f + 12f), logoP);
        }
        if (!ch.key.equals(nameKey) || nameL == null) {
            nameP.setColor(Palette.TEXT1);
            nameL = lines(ch.name, nameP, Ui.px(268), 2, 1f);
            nameKey = ch.key;
        }
        Ui.draw(c, nameL, lx, Ui.f(top + 168f));

        // The programme.
        Guide.Schedule s = Library.get(ctx).schedule(ch.key);
        int i = s == null ? -1 : s.at(at);
        float x0 = Ui.f(TEXT_X), right = Ui.f(X + W - 34f), base = Ui.f(top + 65f);
        String date = Ui.dateClock(now);
        dateP.setColor(Palette.TEXT1);
        float dw = dateP.measureText(date);
        c.drawText(date, right - dw, base, dateP);
        titleP.setColor(Palette.TEXT1);
        String title = i >= 0 ? s.title[i] : ch.name;
        c.drawText(Ui.fit(title, titleP, right - dw - Ui.f(40) - x0), x0, base, titleP);

        // Its times, how far it is (or the archive's mark and its length), where the channel is from, the chips.
        float y = Ui.f(top + 118f), x = x0;
        greyP.setColor(Palette.TEXT2);
        whiteP.setColor(Palette.TEXT1);
        if (i >= 0) {
            String span = LiveText.span(s.start[i], s.stop[i]);
            c.drawText(span, x, y, greyP);
            x += greyP.measureText(span) + Ui.f(25);
            String mins;
            if (archived) {
                iconLine.setColor(Palette.TEXT2);
                iconLine.setStrokeWidth(Math.max(1f, Ui.f(2.4f)));
                iconFill.setColor(Palette.TEXT2);
                Dark.icon(c, Dark.HISTORY, x + Ui.f(14), y - Ui.f(10), Ui.f(28), iconLine, iconFill);
                x += Ui.f(28) + Ui.f(24);
                mins = minutes(s.stop[i] - s.start[i]);
            } else {
                Bars.thin(c, x, y - Ui.f(10), Ui.f(80), Ui.f(6), LiveText.progress(now, s.start[i], s.stop[i]));
                x += Ui.f(80) + Ui.f(24);
                mins = s.stop[i] > now ? minutes(s.stop[i] - now) : "";
            }
            if (!mins.isEmpty()) {
                c.drawText(mins, x, y, greyP);
                x += greyP.measureText(mins) + Ui.f(40);
            }
        } else {
            String none = s == null ? "No programme information" : "Nothing in the guide now";
            c.drawText(none, x, y, greyP);
            x += greyP.measureText(none) + Ui.f(40);
        }
        String where = where(ctx, ch);
        if (!where.isEmpty()) {
            String w = Ui.fit(where, whiteP, Math.max(Ui.f(120), right - x - Ui.f(200)));
            c.drawText(w, x, y, whiteP);
            x += whiteP.measureText(w) + Ui.f(40);
        }
        if (chips != null) {
            for (String chip : chips) {
                if (chip == null || chip.isEmpty()) continue;
                if (x + Chips.width(chip) > right) break;
                x += Chips.media(c, x, y - Ui.f(10), chip) + Ui.f(Chips.GAP + 9f);
            }
        }

        // What it is about, then what's next.
        if (i >= 0 && !s.desc[i].isEmpty()) {
            String k = ch.key + "|" + s.start[i];
            if (!k.equals(descKey) || descL == null) {
                descP.setColor(Palette.TEXT2);
                descL = lines(s.desc[i], descP, Ui.px(W - (TEXT_X - X) - 40f), 2, 33f / 26f);
                descKey = k;
            }
            Ui.draw(c, descL, x0, Ui.f(top + 137f));
        }
        int n = s == null ? -1 : i >= 0 ? (i + 1 < s.start.length ? i + 1 : -1) : s.after(at);
        if (n >= 0) {
            float ny = Ui.f(top + 248f);
            String sp = LiveText.span(s.start[n], s.stop[n]);
            c.drawText(sp, x0, ny, greyP);
            float nx = x0 + greyP.measureText(sp) + Ui.f(25);
            c.drawText(Ui.fit(s.title[n], whiteP, right - nx), nx, ny, whiteP);
        }
    }

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
