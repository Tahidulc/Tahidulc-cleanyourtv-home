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
import com.gathin.homefieldtv.prefs.ChannelEdits;

/**
 * The top of the TV guide in the 07 look (the approved stills 05c and 06c), under the tab bar: the preview at the left,
 * 586 x 330 rounded 30 with its deep shadow (the live picture goes over it, its corners and its "playing" pill drawn over
 * the picture by GuideScreen's frame; black while it tunes), and to its right, on a frosted panel of its own (Frost,
 * 1130 x 330, rounded 34), the programme the remote is on:
 *
 * <pre>
 *   the kicker      the amber star for a favourite channel, then the playlist and the group ("Home  •  UK Sport"), 17 px
 *   the title       36 px bold, two lines at most
 *   its line        its times ("19:30 — 22:00"), for one on now a bar and the minutes left ("40 min"), the chips (HD or
 *                   4K as the channel's name says, the picture's frames a second for the channel playing or one seen
 *                   before: "50 FPS", its sound), and the catch-up mark for one the archive keeps (with its length for
 *                   one already shown); a bell and "Reminder set" for one to come with a reminder
 *   what it's about 20 px, three lines
 *   its picture     at its own shape on the panel's right (587 x 330), fitted round its faces with its left edge faded
 *                   into the panel and nothing over it (PanelArt, Pics.PANEL), cross-fading as the remote moves; with
 *                   no picture the words take the whole panel
 * </pre>
 *
 * The channel number being typed shows in a frosted box at the panel's top right. GuideScreen asks for the picture once
 * the remote rests and reads its neighbours' ahead (settled, prefetch), so a move finds it ready.
 */
final class GuideBand extends View {
    // Where things go (design pixels), the side panels closed: the whole guide moves right with them.
    static final float PREVIEW_X = 90f, PREVIEW_Y = 136f, PREVIEW_W = 586f, PREVIEW_H = 330f, PREVIEW_R = 30f;
    /** The details' panel, and the picture's box across its right-hand part. */
    static final float CARD_X = 700f, CARD_W = 1130f, CARD_R = 34f, CARD_Y = PREVIEW_Y, CARD_B = PREVIEW_Y + PREVIEW_H, ART_W = 587f;
    /** The words: from x 736, 600 wide beside a picture (1058 without), the kicker's, the title's and the line's places. */
    static final float TEXT_X = 736f, TEXT_W = 600f, TEXT_W_WIDE = 1058f, KICK_Y = 178f, TITLE_Y = 223f, TITLE_LINE = 39f;
    /** The preview's shadow (0 24 50 black 50 %). */
    static final float[] SH_PREVIEW = {24f, 50f, 0f, 128f};

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
    private int descWidth, descLines;
    /** The programme's picture beside the words. */
    final PanelArt art;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Frost panel = new Frost(Look07.MAT), box = new Frost(Look07.MAT_DARK);
    private final TextPaint kickP = Type.paint(17f, Type.SEMIBOLD, Look07.L2), titleP = Type.paint(36f, Type.BOLD, Look07.L1);
    private final TextPaint metaP = Type.clock(19f, Type.MEDIUM, Look07.L2), descP = Type.paint(20f, Type.REGULAR, Look07.L2);
    private final TextPaint typedP = Type.clock(46f, Type.BOLD, Look07.L1), typedLabel = Type.label(Type.LABEL, Look07.L3);
    /** The title and the kicker, measured once and cut each frame (TextFit). */
    private final TextFit titles = new TextFit(titleP, 64), kicks = new TextFit(kickP, 32), metas = new TextFit(metaP, 64);
    private final RectF r = new RectF();

    GuideBand(Context c, Library lib) {
        super(c);
        this.lib = lib;
        art = new PanelArt(this, ART_W, PREVIEW_H, 1f);
        art.mode = Pics.PANEL;
        art.standIn = false;
        Shadows.prepare(SH_PREVIEW, PREVIEW_R);
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
        // The preview's shadow and frame: black until the picture comes over it.
        r.set(Ui.f(PREVIEW_X), Ui.f(PREVIEW_Y), Ui.f(PREVIEW_X + PREVIEW_W), Ui.f(PREVIEW_Y + PREVIEW_H));
        Shadows.draw(c, r, Ui.f(PREVIEW_R), SH_PREVIEW, 1f);
        fill.setColor(0xFF000000);
        c.drawRoundRect(r, Ui.f(PREVIEW_R), Ui.f(PREVIEW_R), fill);
        // The details' panel and the programme's picture at its right, cross-fading as the remote moves.
        r.set(Ui.f(CARD_X), Ui.f(CARD_Y), Ui.f(CARD_X + CARD_W), Ui.f(CARD_B));
        panel.draw(c, r, Ui.f(CARD_R), 1f);
        boolean fading = art.draw(c, Ui.f(CARD_X + CARD_W - ART_W), Ui.f(CARD_Y));
        if (ch != null) drawDetails(c);
        if (!typed.isEmpty()) drawTyped(c);
        if (fading) postInvalidateOnAnimation();
    }

    private void drawDetails(Canvas c) {
        long now = System.currentTimeMillis();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s != null && programme >= 0 && programme < s.start.length ? programme : -1;
        float tx = Ui.f(TEXT_X), tw = Ui.f(art.none() ? TEXT_W_WIDE : TEXT_W);

        // The kicker: the favourite's star, the playlist and the group.
        float kx = tx;
        if (favourite) {
            Icons07.draw(c, Icons07.STAR, kx + Ui.f(9f), Ui.f(KICK_Y - 6f), Ui.f(18f), Look07.AMBER);
            kx += Ui.f(26f);
        }
        if (!group.isEmpty()) kicks.line(c, group, kx, Ui.f(KICK_Y), tw - (kx - tx));

        // The title, two lines at most.
        String title = i >= 0 ? s.title[i] : ChannelEdits.name(ch);
        boolean one = titles.fitsOne(title, tw);
        titles.lines(c, title, tx, Ui.f(TITLE_Y), Ui.f(TITLE_Y), Ui.f(TITLE_Y + TITLE_LINE), tw, 2);
        float rowCy = Ui.f(TITLE_Y + (one ? 33.5f : 33.5f + TITLE_LINE));

        // Its line: the times, how far through and what's left (on now), the chips, the catch-up mark; or a reminder.
        float x = tx, gap = Ui.f(12f), base = Type.centred(metaP, rowCy);
        if (i >= 0) {
            String span = Words.span(start, end);
            if (!Words.sameDay(start, now)) span = dayAndSpan(now);
            c.drawText(span, x, base, metaP);
            x += metas.width(span) + gap;
            if (start <= now && now < end) {
                if (com.gathin.homefieldtv.prefs.Look.progress) {
                    Pills.progress(c, x, rowCy, Ui.f(96f), LiveText.progress(now, start, end));
                    x += Ui.f(96f) + gap;
                }
                String left = Words.minutes(end - now);
                c.drawText(left, x, base, metaP);
                x += metas.width(left) + gap;
            } else if (end <= now && Catchup.has(ch, start, now)) {
                // Already shown, in the archive: how long it is (the catch-up mark after the chips).
                String len = Words.minutes(end - start);
                c.drawText(len, x, base, metaP);
                x += metas.width(len) + gap;
            } else if (start > now && Reminders.has(getContext(), ch.key, start)) {
                Icons07.draw(c, Icons07.BELL, x + Ui.f(9f), rowCy, Ui.f(18f), Look07.AMBER);
                x += Ui.f(26f);
                c.drawText("Reminder set", x, base, metaP);
                x += metas.width("Reminder set") + gap;
            }
        } else {
            String none = "No information";
            c.drawText(none, x, base, metaP);
            x += metas.width(none) + gap;
        }
        String q = qualityFor(ch);
        if (!q.isEmpty()) x += Pills.chipFilled(c, x, rowCy, q) + gap;
        String fps = fpsFor(ch);
        if (!fps.isEmpty()) x += Pills.chipFilled(c, x, rowCy, fps) + gap;
        if (i >= 0 && (start <= now && now < end || end <= now) && Catchup.has(ch, start, now)) {
            Icons07.draw(c, Icons07.CATCHUP, x + Ui.f(9f), rowCy, Ui.f(18f), Look07.L2);
        }

        // What it is about, three lines.
        if (i >= 0 && !s.desc[i].isEmpty()) {
            int w = Math.round(tw);
            if (descL == null || w != descWidth || !ch.key.equals(descFor) || descStart != start) {
                String d = s.desc[i];
                descL = StaticLayout.Builder.obtain(d, 0, d.length(), descP, Math.max(1, w)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.42f / 1.21f).setIncludePad(false).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build();
                descFor = ch.key;
                descStart = start;
                descWidth = w;
                descLines = descL.getLineCount();
            }
            float top = rowCy + Ui.f(13f + 14f + 2f);
            if (descLines > 0) Ui.draw(c, descL, tx, top);
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

    /** The channel number being typed, "10-" until it is done, in a frosted box at the panel's top right. */
    private void drawTyped(Canvas c) {
        float right = CARD_X + CARD_W - 24f, top = CARD_Y + 24f;
        r.set(Ui.f(right - 220f), Ui.f(top), Ui.f(right), Ui.f(top + 112f));
        box.draw(c, r, Ui.f(26f), 1f);
        c.drawText("CHANNEL", r.left + Ui.f(24f), r.top + Ui.f(34f), typedLabel);
        String d = typed.length() >= 3 ? typed : (typed + "---").substring(0, 3);
        c.drawText(d, r.left + Ui.f(24f), r.top + Ui.f(88f), typedP);
    }
}
