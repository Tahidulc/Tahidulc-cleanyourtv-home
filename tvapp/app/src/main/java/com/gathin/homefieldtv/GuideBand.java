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
 * for one with a reminder, HD or 4K as the channel's name says, and the picture's frames a second ("50 FPS") for the
 * channel playing (or one seen before: FrameRates), as the player's chips. The channel number being typed shows at the
 * top right.
 *
 * <p>As the family asked, the whole details panel has a background of its own: a card in the panel's colour (TiviMate's
 * info block, [692,24]–[1896,384], rounded as the preview) with the picture of the programme the remote is on across its
 * right-hand part (PanelArt: Sky's picture of it, else the channel's logo on its own tinted ground), faded into the
 * panel's colour from the left and from the foot so the words stay readable; moving to another programme (Left, Right,
 * Up, Down) cross-fades to its picture in 200 ms. GuideScreen asks for the picture once the remote rests and reads its
 * neighbours' ahead (settled, prefetch), so a move finds it ready.
 */
final class GuideBand extends View {
    // Where things go (design pixels), the side panels closed: the whole guide moves right with them.
    static final float PREVIEW_X = 32f, PREVIEW_Y = 24f, PREVIEW_W = 640f, PREVIEW_H = 360f, PREVIEW_R = 17f;
    private static final float TEXT_X = 716f, TEXT_R = 1872f, TITLE_Y = 78f, META_Y = 132f, BAR_Y = 123f, DESC_TOP = 152f,
            DESC_BOTTOM = 360f, STAR_X = 1856f, STAR_Y = 66f, GAP = 24f;
    /** The details' card (TiviMate's info block), and the picture's box across its right-hand part. */
    static final float CARD_X = 692f, CARD_R = 1896f, CARD_Y = PREVIEW_Y, CARD_B = PREVIEW_Y + PREVIEW_H, ART_W = 840f;

    private final Library lib;
    /** The player (its channel's frames a second); null in a test that makes the band alone. */
    LiveTv live;
    private Channel ch;
    /** What the remote is on: its programme's index (-1: none) and {start, end}. */
    private int programme = -1;
    private long start, end;
    private boolean favourite;
    private String group = "", typed = "", descFor = "";
    private StaticLayout descL;
    private int descWidth;
    /** The programme's picture behind the words. */
    final PanelArt art;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(Type.HERO, Type.MEDIUM, Palette.TEXT1), metaP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint groupP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), descP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint typedP = Type.paint(48f, Type.MEDIUM, Palette.TEXT1);
    /** The title and the playlist and group, measured once and cut each frame (TextFit). */
    private final TextFit titles = new TextFit(titleP, 64), groups = new TextFit(groupP, 32), metas = new TextFit(metaP, 64);
    private final RectF r = new RectF();

    GuideBand(Context c, Library lib) {
        super(c);
        this.lib = lib;
        art = new PanelArt(this, ART_W, PREVIEW_H, 0.75f);
    }

    /** The preview's place on the screen (screen pixels) with the guide pushed right by shift (design pixels). */
    static Rect previewRect(float shift) {
        return new Rect(Ui.px(PREVIEW_X + shift), Ui.px(PREVIEW_Y), Ui.px(PREVIEW_X + PREVIEW_W + shift), Ui.px(PREVIEW_Y + PREVIEW_H));
    }

    /**
     * What the remote is on: a channel, its programme's index in its schedule (-1 for none: nothing in the guide then)
     * with {start, end}, whether the channel is a favourite, and its playlist and group. Its picture shows if it is
     * already known; settled() asks for it.
     */
    void show(Channel c, int index, long[] times, boolean isFavourite, String playlistAndGroup) {
        ch = c;
        programme = index;
        start = times == null ? 0 : times[0];
        end = times == null ? 0 : times[1];
        favourite = isFavourite;
        group = playlistAndGroup == null ? "" : playlistAndGroup;
        art.show(c, c == null ? null : lib.schedule(c.key), index, false);
        invalidate();
    }

    /** The remote has rested on what is shown: its picture asked for if it isn't known yet. */
    void settled() {
        art.show(ch, ch == null ? null : lib.schedule(ch.key), programme, true);
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
        // The details' card and the programme's picture across its right-hand part, cross-fading as the remote moves.
        r.set(Ui.f(CARD_X), Ui.f(CARD_Y), Ui.f(CARD_R), Ui.f(CARD_B));
        fill.setColor(Palette.PANEL);
        c.drawRoundRect(r, Ui.f(PREVIEW_R), Ui.f(PREVIEW_R), fill);
        boolean fading = art.draw(c, Ui.f(CARD_R - ART_W), Ui.f(CARD_Y));
        if (ch != null) drawDetails(c);
        if (!typed.isEmpty()) drawTyped(c);
        if (fading) postInvalidateOnAnimation();
    }

    private void drawDetails(Canvas c) {
        long now = System.currentTimeMillis();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s != null && programme >= 0 && programme < s.start.length ? programme : -1;

        // The star: a favourite channel's filled in the accent.
        RoundButton.glyph(c, favourite ? Dark.STAR : Dark.STAR_LINE, Ui.f(STAR_X), Ui.f(STAR_Y), Ui.f(36f),
                favourite ? Palette.ACCENT : Palette.TEXT2);
        titleP.setColor(Palette.TEXT1);
        titles.line(c, i >= 0 ? s.title[i] : ch.name, Ui.f(TEXT_X), Ui.f(TITLE_Y), Ui.f(STAR_X - 40f - TEXT_X));

        // The times, how far through and what's left (on now), or Homefield's marks; the playlist and group at the right.
        metaP.setColor(Palette.TEXT2);
        float x = Ui.f(TEXT_X), base = Ui.f(META_Y), cy = Ui.f(BAR_Y);
        if (i >= 0) {
            String span = Words.span(start, end);
            if (!Words.sameDay(start, now)) span = dayAndSpan(now);
            c.drawText(span, x, base, metaP);
            x += metas.width(span) + Ui.f(GAP);
            if (start <= now && now < end) {
                if (com.gathin.homefieldtv.prefs.Look.progress) {
                    Bars.thin(c, x, cy, Ui.f(80f), Ui.f(6f), LiveText.progress(now, start, end));
                    x += Ui.f(80f + GAP);
                }
                String left = Words.minutes(end - now);
                c.drawText(left, x, base, metaP);
                x += metas.width(left) + Ui.f(GAP);
            } else if (end <= now && Catchup.has(ch, start, now)) {
                // Already shown, in the archive: the catch-up mark and how long it is (as the player's banner has it).
                RoundButton.glyph(c, Dark.HISTORY, x + Ui.f(14f), cy, Ui.f(28f), Palette.TEXT2);
                x += Ui.f(36f);
                String len = Words.minutes(end - start);
                c.drawText(len, x, base, metaP);
                x += metas.width(len) + Ui.f(GAP);
            } else if (start > now && Reminders.has(getContext(), ch.key, start)) {
                fill.setColor(Palette.TEXT2);
                Dark.bell(c, x + Ui.f(12f), cy, Ui.f(12f), fill);
                x += Ui.f(34f);
                c.drawText("Reminder set", x, base, metaP);
                x += metas.width("Reminder set") + Ui.f(GAP);
            }
        } else {
            String none = "No information";
            c.drawText(none, x, base, metaP);
            x += metas.width(none) + Ui.f(GAP);
        }
        String q = qualityFor(ch);
        if (!q.isEmpty()) x += Chips.media(c, x, cy, q) + Ui.f(Chips.GAP);
        String fps = fpsFor(ch);
        if (!fps.isEmpty()) x += Chips.media(c, x, cy, fps) + Ui.f(Chips.GAP);
        if (!q.isEmpty() || !fps.isEmpty()) x += Ui.f(GAP - Chips.GAP);
        if (!group.isEmpty()) {
            groupP.setColor(Palette.TEXT1);
            float room = Ui.f(TEXT_R) - x, gw = Math.min(room, groups.width(group));
            groups.line(c, group, Ui.f(TEXT_R) - gw, base, room);
        }

        // What it is about, as many lines as fit.
        if (i >= 0 && !s.desc[i].isEmpty()) {
            int w = Ui.px(TEXT_R - TEXT_X);
            if (descL == null || w != descWidth || !ch.key.equals(descFor) || descStart != start) {
                descP.setColor(Palette.TEXT2);
                Paint.FontMetrics fm = descP.getFontMetrics();
                int lines = Math.max(1, (int) (Ui.f(DESC_BOTTOM - DESC_TOP) / (fm.descent - fm.ascent)));
                String d = s.desc[i];
                descL = StaticLayout.Builder.obtain(d, 0, d.length(), descP, w).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1f).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
                descFor = ch.key;
                descStart = start;
                descWidth = w;
            }
            Ui.draw(c, descL, Ui.f(TEXT_X), Ui.f(DESC_TOP));
        }
    }

    private long descStart = Long.MIN_VALUE, dayFor = Long.MIN_VALUE;
    private String dayText = "", qualityKey = "", qualityText = "";

    /** "Tomorrow, 10:00 — 11:00": a programme on another day (kept while it is the one shown). */
    private String dayAndSpan(long now) {
        long k = start ^ (end * 31) ^ Words.dayIndex(now);
        if (k != dayFor) {
            dayFor = k;
            dayText = Ui.day(start, now) + ", " + LiveText.span(start, end);
        }
        return dayText;
    }

    /** HD or 4K as the channel's name says (worked out once a channel). */
    private String qualityFor(Channel c) {
        if (!c.key.equals(qualityKey)) {
            qualityKey = c.key;
            qualityText = LiveText.quality(c.name);
        }
        return qualityText;
    }

    /** "50 FPS" for the channel playing (as the player has it), else as last seen on this channel; "" when not known. */
    private String fpsFor(Channel c) {
        int f = live != null && live.channel() != null && live.channel().key.equals(c.key) ? live.fps() : 0;
        if (f <= 0) f = FrameRates.of(getContext(), c.key);
        return FrameRates.chip(f);
    }

    /** "16 min": whole minutes, rounded up, as the family's TV writes them (never hours). */
    static String minutes(long ms) {
        return Math.max(1L, (ms + 59_999L) / 60_000L) + " min";
    }

    /** The channel number being typed, "10-" until it is done, on a card at the top right. */
    private void drawTyped(Canvas c) {
        r.set(Ui.f(TEXT_R - 220f), Ui.f(PREVIEW_Y), Ui.f(TEXT_R + 24f), Ui.f(PREVIEW_Y + 104f));
        fill.setColor(Palette.HEADER);
        c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
        String d = typed.length() >= 3 ? typed : (typed + "---").substring(0, 3);
        typedP.setColor(Palette.TEXT1);
        c.drawText(d, r.centerX() - typedP.measureText(d) / 2f, Type.centred(typedP, r.centerY()), typedP);
    }
}
