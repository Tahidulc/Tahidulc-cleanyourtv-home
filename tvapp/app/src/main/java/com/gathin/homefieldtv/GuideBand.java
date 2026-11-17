package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Reminders;

/**
 * The top of the TV guide, as the family's TiviMate draws it (DEVICE.md §2.7): the preview at the top left, 640 x 360
 * with rounded corners (the live picture goes over it; black while it tunes), and to its right the programme the
 * remote is on: its title (41 px, medium), a star at the right (filled in the accent for a favourite channel), then its
 * times ("10:00 — 11:00"), for one on now a bar of how far through it is and the minutes left ("16 min"), and the
 * playlist and the group at the right ("Home  •  UK | Sport"); under them what it is about, in grey, as many lines as
 * fit. Homefield's own on the times' line: the catch-up mark and its length for a programme the archive keeps, a bell
 * for one with a reminder, and HD or 4K as the channel's name says, as the player's chips. The channel number being
 * typed shows at the top right.
 */
final class GuideBand extends View {
    // Where things go (design pixels), the side panels closed: the whole guide moves right with them.
    static final float PREVIEW_X = 32f, PREVIEW_Y = 24f, PREVIEW_W = 640f, PREVIEW_H = 360f, PREVIEW_R = 17f;
    private static final float TEXT_X = 716f, TEXT_R = 1872f, TITLE_Y = 78f, META_Y = 132f, BAR_Y = 123f, DESC_TOP = 152f,
            DESC_BOTTOM = 360f, STAR_X = 1856f, STAR_Y = 66f, GAP = 24f;

    private final Library lib;
    private Channel ch;
    /** What the remote is on: its programme's index (-1: none) and {start, end}. */
    private int programme = -1;
    private long start, end;
    private boolean favourite;
    private String group = "", typed = "", descFor = "";
    private StaticLayout descL;
    private int descWidth;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(Type.HERO, Type.MEDIUM, Palette.TEXT1), metaP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint groupP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), descP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint typedP = Type.paint(48f, Type.MEDIUM, Palette.TEXT1);
    private final RectF r = new RectF();

    GuideBand(Context c, Library lib) {
        super(c);
        this.lib = lib;
    }

    /** The preview's place on the screen (screen pixels) with the guide pushed right by shift (design pixels). */
    static Rect previewRect(float shift) {
        return new Rect(Ui.px(PREVIEW_X + shift), Ui.px(PREVIEW_Y), Ui.px(PREVIEW_X + PREVIEW_W + shift), Ui.px(PREVIEW_Y + PREVIEW_H));
    }

    /**
     * What the remote is on: a channel, its programme's index in its schedule (-1 for none: nothing in the guide then)
     * with {start, end}, whether the channel is a favourite, and its playlist and group.
     */
    void show(Channel c, int index, long[] times, boolean isFavourite, String playlistAndGroup) {
        ch = c;
        programme = index;
        start = times == null ? 0 : times[0];
        end = times == null ? 0 : times[1];
        favourite = isFavourite;
        group = playlistAndGroup == null ? "" : playlistAndGroup;
        invalidate();
    }

    void typed(String digits) {
        typed = digits == null ? "" : digits;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        // The preview's frame: black until the picture comes over it.
        r.set(Ui.f(PREVIEW_X), Ui.f(PREVIEW_Y), Ui.f(PREVIEW_X + PREVIEW_W), Ui.f(PREVIEW_Y + PREVIEW_H));
        fill.setColor(0xFF000000);
        c.drawRoundRect(r, Ui.f(PREVIEW_R), Ui.f(PREVIEW_R), fill);
        if (ch != null) drawDetails(c);
        if (!typed.isEmpty()) drawTyped(c);
    }

    private void drawDetails(Canvas c) {
        long now = System.currentTimeMillis();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s != null && programme >= 0 && programme < s.start.length ? programme : -1;

        // The star: a favourite channel's filled in the accent.
        RoundButton.glyph(c, favourite ? Dark.STAR : Dark.STAR_LINE, Ui.f(STAR_X), Ui.f(STAR_Y), Ui.f(36f),
                favourite ? Palette.ACCENT : Palette.TEXT2);
        titleP.setColor(Palette.TEXT1);
        String title = i >= 0 ? s.title[i] : ch.name;
        c.drawText(Ui.fit(title, titleP, Ui.f(STAR_X - 40f - TEXT_X)), Ui.f(TEXT_X), Ui.f(TITLE_Y), titleP);

        // The times, how far through and what's left (on now), or Homefield's marks; the playlist and group at the right.
        metaP.setColor(Palette.TEXT2);
        float x = Ui.f(TEXT_X), base = Ui.f(META_Y), cy = Ui.f(BAR_Y);
        if (i >= 0) {
            String day = Ui.day(start, now), span = LiveText.span(start, end);
            if (!"Today".equals(day)) span = day + ", " + span;
            c.drawText(span, x, base, metaP);
            x += metaP.measureText(span) + Ui.f(GAP);
            if (start <= now && now < end) {
                if (com.gathin.homefieldtv.prefs.Look.progress) {
                    Bars.thin(c, x, cy, Ui.f(80f), Ui.f(6f), LiveText.progress(now, start, end));
                    x += Ui.f(80f + GAP);
                }
                String left = minutes(end - now);
                c.drawText(left, x, base, metaP);
                x += metaP.measureText(left) + Ui.f(GAP);
            } else if (end <= now && Catchup.has(ch, start, now)) {
                // Already shown, in the archive: the catch-up mark and how long it is (as the player's banner has it).
                RoundButton.glyph(c, Dark.HISTORY, x + Ui.f(14f), cy, Ui.f(28f), Palette.TEXT2);
                x += Ui.f(36f);
                String len = minutes(end - start);
                c.drawText(len, x, base, metaP);
                x += metaP.measureText(len) + Ui.f(GAP);
            } else if (start > now && Reminders.has(getContext(), ch.key, start)) {
                fill.setColor(Palette.TEXT2);
                Dark.bell(c, x + Ui.f(12f), cy, Ui.f(12f), fill);
                x += Ui.f(34f);
                c.drawText("Reminder set", x, base, metaP);
                x += metaP.measureText("Reminder set") + Ui.f(GAP);
            }
        } else {
            String none = "No information";
            c.drawText(none, x, base, metaP);
            x += metaP.measureText(none) + Ui.f(GAP);
        }
        String q = LiveText.quality(ch.name);
        if (!q.isEmpty()) x += Chips.media(c, x, cy, q) + Ui.f(GAP);
        if (!group.isEmpty()) {
            groupP.setColor(Palette.TEXT1);
            String g = Ui.fit(group, groupP, Ui.f(TEXT_R) - x);
            c.drawText(g, Ui.f(TEXT_R) - groupP.measureText(g), base, groupP);
        }

        // What it is about, as many lines as fit.
        if (i >= 0 && !s.desc[i].isEmpty()) {
            int w = Ui.px(TEXT_R - TEXT_X);
            String key = ch.key + "|" + start;
            if (descL == null || w != descWidth || !key.equals(descFor)) {
                descP.setColor(Palette.TEXT2);
                Paint.FontMetrics fm = descP.getFontMetrics();
                int lines = Math.max(1, (int) (Ui.f(DESC_BOTTOM - DESC_TOP) / (fm.descent - fm.ascent)));
                String d = s.desc[i];
                descL = StaticLayout.Builder.obtain(d, 0, d.length(), descP, w).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1f).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
                descFor = key;
                descWidth = w;
            }
            Ui.draw(c, descL, Ui.f(TEXT_X), Ui.f(DESC_TOP));
        }
    }

    /** "16 min": whole minutes, rounded up, as the family's TV writes them (never hours). */
    static String minutes(long ms) {
        return Math.max(1L, (ms + 59_999L) / 60_000L) + " min";
    }

    /** The channel number being typed, "10-" until it is done, on a card at the top right. */
    private void drawTyped(Canvas c) {
        r.set(Ui.f(TEXT_R - 220f), Ui.f(PREVIEW_Y), Ui.f(TEXT_R + 24f), Ui.f(PREVIEW_Y + 104f));
        fill.setColor(Palette.PANEL);
        c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
        String d = typed.length() >= 3 ? typed : (typed + "---").substring(0, 3);
        typedP.setColor(Palette.TEXT1);
        c.drawText(d, r.centerX() - typedP.measureText(d) / 2f, Type.centred(typedP, r.centerY()), typedP);
    }
}
