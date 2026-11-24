package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.util.List;

/**
 * The player's info card in the 07 look (the approved still 07c): a card 1824 x 322 from x 48, rounded 44, at the foot
 * (FOOT_Y 718: on INFO, after a channel change, and for the channel before or after on CH− and CH+), over the recently
 * watched channels (STACK_Y 352: live TV's OK overlay, the family's "move the information panel to the bottom so if I
 * press ok when watching a channel it will be at bottom", the channels and then the settings under it) or at the top
 * (TOP_Y 40: catch-up's overlay). Behind its words, as the family asked ("when scrolling through and you see the
 * information panel like this, have a picture of the programme background on this"), the programme's own picture
 * fills the whole card (Pics.FILL_CARD: its faces whole, placed between the words and what's next), with a shade only
 * where the words are: from the left under the channel and the programme's words, ending a little past them, and from
 * the right under what's next; the faces stay clear in between, and the words stop before them. As Up and Down (and
 * CH+ and CH−) go through the channels, the picture cross-fades to each programme's (FADE_MS), the channels either side
 * read ahead once the card rests. With no picture (or while one is still to come for another channel), the plain dark
 * material as before (Frost over the live picture).
 *
 * <pre>
 *   the channel        its logo, as the guide's channel cells have it (ChannelLook.cell: 150 x 150 at (88, +40), in
 *                      the logo's own colour), and its name under it (20 px, centred, back since 3.9: "the channel
 *                      name should be underneath the channel logo here like before"); no number ("I don't want
 *                      channels numbers now": the number is in the content description, and the number keys still go
 *                      to a channel)
 *   (no kicker)        no LIVE badge and no category since 3.7: the words start at the card's top
 *   the title          44 px bold, one line
 *   its line           "19:30 – 22:00", the bar (200 x 6) and what's left ("40 min left"), then the chips for the
 *                      picture and the sound as the player has them (FHD, 50 FPS, STEREO); from the archive its length
 *   what it is about   21 px, up to three lines
 *   what's next        at the right, 420 wide: "NEXT · 22:00", its picture (420 x 170, fitted round its faces:
 *                      PicTile), its title (22 px) and times
 * </pre>
 *
 * One card draws for one view (its logos and pictures ask that view to draw again when they come); draw() is true while
 * the picture cross-fades (draw again next frame). Lines are laid out once for a programme (and its words' width) and
 * kept, and the words a frame writes are kept words (Words) cut from kept widths (TextFit). The pictures are asked for
 * once the card rests on a programme (settle), and the channels either side read ahead then, at the card's size, so
 * holding Up or Down finds them ready.
 */
final class InfoCard {
    static final float X = 48f, W = 1824f, H = 322f, FOOT_Y = 1080f - 40f - 322f, TOP_Y = 40f, RADIUS = 44f;
    /** Live TV's OK overlay: the card's top, so it ends (674) a line above the recently watched channels (RecentGrid). */
    static final float STACK_Y = 352f;
    /** Where the words stand in the card (design pixels from its top): the title's baseline, its line's middle and what
     *  it is about, moved up into the place the kicker had (no LIVE or category since 3.7), with room for three lines. */
    static final float TITLE_Y = 80f, LINE_Y = 118f, DESC_Y = 151f;
    static final int DESC_LINES = 3;
    static final float LOGO_X = 88f, LOGO_W = 150f, LOGO_H = 104f, CELL_H = 150f, TEXT_X = 280f, TEXT_R = 1372f;
    /** The channel's name under its logo: its baseline (design px from the card's top) and the room it is cut to,
     *  centred under the logo and clear of the words at TEXT_X. */
    static final float NAME_Y = 40f + 150f + 30f, NAME_W = 170f;
    /** What's next: its left, its width, its picture's height. */
    static final float NEXT_X = 1412f, NEXT_W = 420f, NEXT_H = 170f;
    /** The picture's cross-fade from one programme's to the next. */
    static final long FADE_MS = 220;
    /** Over a picture: the words stop this far before its faces (design px), and never narrower than WORDS_MIN. */
    static final float FACE_ROOM = 40f, WORDS_MIN = 640f;
    /**
     * The words' shade over the picture: its colour, its strength at the card's left and where the words end, its fade
     * past them (design px: ending by the edge of the faces' box, never under FADE_MIN); what's next's, from the card's
     * right (its strength there and at NEXT_X, fading in over NEXT_FADE before it).
     */
    static final int SHADE = 0x070A12, SHADE_LEFT = 0xB8, SHADE_WORDS = 0x99, SHADE_NEXT = 0xA8, SHADE_NEXT_IN = 0x8A;
    static final float FADE = 200f, FADE_MIN = 60f, NEXT_FADE = 110f;
    /** The words' colours over a picture (a touch brighter than on the plain card). */
    static final int ON_PICTURE = 0xEBFFFFFF, ON_PICTURE_QUIET = 0xC7FFFFFF;

    private final View owner;
    private final Runnable redraw;
    private final Frost frost = new Frost(Look07.MAT_DARK);
    private final PicTile next;
    private final Shaders shaders = new Shaders();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wordsShade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), wordsFade = new Paint(Paint.DITHER_FLAG),
            nextShade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final TextPaint titleP = Type.paint(44f, Type.BOLD, Look07.L1), nameP = Type.paint(20f, Type.SEMIBOLD, Look07.L2);
    private final TextPaint metaP = Type.clock(21f, Type.MEDIUM, Look07.L2), descP = Type.paint(21f, Type.REGULAR, Look07.L2);
    private final TextPaint chipP = Type.paint(16f, Type.BOLD, Look07.L1), nextLabelP = Type.label(Type.LABEL, Look07.L3);
    private final TextPaint nextTitleP = Type.paint(22f, Type.SEMIBOLD, Look07.L1), nextTimeP = Type.clock(18f, Type.MEDIUM, Look07.L3);
    private final RectF r = new RectF();
    private final float[] lay = new float[PicFill.SIZE];
    /** Laid out once: what the programme is about (for descKey, at descWidth), and its lines' widest. */
    private String descKey = "", whereKey = "", whereText = "", nextKey = "", nextLabel = "";
    private long nextStart = Long.MIN_VALUE;
    private long descStart = Long.MIN_VALUE;
    private int descWidth = -1;
    private float descWide;
    private StaticLayout descL;
    /** The programme it rests on, its picture (and the next one's) asked for once it has rested. */
    private Channel restCh;
    private long restStart = Long.MIN_VALUE;
    private boolean settled;
    private final Runnable settle = this::settle;
    /** The picture behind (and its channel), the one it cross-fades from, and when the fade began. */
    private Bitmap pic, was;
    private String picFor = "", picUrl = "";
    private long picAt = -10_000L;
    private boolean fading;
    private final TextFit titles = new TextFit(titleP, 64), names = new TextFit(nameP, 64), metas = new TextFit(metaP, 64),
            nextTitles = new TextFit(nextTitleP, 64), chips = new TextFit(chipP, 32);

    InfoCard(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        next = new PicTile(owner);
        ring.setStyle(Paint.Style.STROKE);
        // Unit boxes, stretched to the words (made once: nothing made a frame).
        PicTile.shades(wordsShade, wordsFade, SHADE, SHADE_LEFT, SHADE_WORDS);
        nextShade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{SHADE, SHADE_NEXT_IN << 24 | SHADE, SHADE_NEXT << 24 | SHADE},
                new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
    }

    /** The channel the card is for, said for the TV's talk-back: its number and name (only when it changes). */
    private void describe(Context ctx, Channel ch, String name) {
        int n = Library.get(ctx).number(ch);
        String said = described(n, name);
        if (!said.equals(saidFor)) {
            saidFor = said;
            owner.setContentDescription(said);
        }
    }

    private String saidFor = "";

    /** Pure: the card's content description: "Channel 101, Riverside One" ("Riverside One" with no number). */
    static String described(int number, String name) {
        String n = name == null ? "" : name;
        return number > 0 ? "Channel " + number + ", " + n : n;
    }

    /**
     * The card has rested on a programme: its picture and what's next's asked for, and the programmes on now on the
     * channels either side of it read ahead, at the card's size (Up, Down, CH+ and CH− go there next).
     */
    private void settle() {
        if (restCh == null) return;
        settled = true;
        owner.invalidate();
        if (Exec.inline) return;
        Library lib = Library.get(owner.getContext());
        long now = System.currentTimeMillis();
        int cw = Ui.px(W), chh = Ui.px(H);
        for (int d = -1; d <= 1; d += 2) {
            Channel n = lib.step(restCh, d);
            if (n == null || n.key.equals(restCh.key)) continue;
            Guide.Schedule ns = lib.schedule(n.key);
            int i = ns == null ? -1 : ns.at(now);
            if (i < 0) continue;
            String u = ProgrammePictures.get(owner.getContext(), n, ns, i, () -> {
                String got = ProgrammePictures.peek(n, ns, i);
                if (got != null && !got.isEmpty()) Pics.prefetch(got, cw, chh, Pics.FILL_CARD);
            });
            if (u != null && !u.isEmpty()) Pics.prefetch(u, cw, chh, Pics.FILL_CARD);
            ProgrammePictures.get(owner.getContext(), n, ns, i + 1, null);
        }
    }

    /**
     * The card with its top at top (design pixels) for channel ch: the programme on at time at (now when live; the
     * picture's time from the archive), the archive's look when archived, the chips given (none for another channel).
     * True while its picture cross-fades (draw again next frame).
     */
    boolean draw(Canvas c, float top, Channel ch, long at, boolean archived, List<String> chipWords) {
        if (ch == null) return false;
        Context ctx = owner.getContext();
        long now = System.currentTimeMillis();
        r.set(Ui.f(X), Ui.f(top), Ui.f(X + W), Ui.f(top + H));
        // Under a picture that fills the card whole (not fading), only the card's shadow: its frost would be hidden.
        if (pic != null && !fading) Shadows.under(c, r, Ui.f(RADIUS), Look07.SH_PANEL, 1f);
        else frost.draw(c, r, Ui.f(RADIUS), 1f);
        Guide.Schedule s = Library.get(ctx).schedule(ch.key);
        int i = s == null ? -1 : s.at(at);
        long st = i >= 0 ? s.start[i] : Long.MIN_VALUE;
        if (!ch.key.equals(restCh == null ? "" : restCh.key) || st != restStart) {
            restCh = ch;
            restStart = st;
            settled = Exec.inline;
            owner.removeCallbacks(settle);
            owner.postDelayed(settle, ProgrammePictures.REST_MS);
        }

        // The programme's picture behind everything (cross-fading), its words' shade, where its faces are.
        float shown = picture(c, ch, s, i);
        float wordsRight = Ui.f(TEXT_R), faces = r.right;
        if (shown > 0f && pic != null) {
            float[] f = Pics.faces(picUrl);
            if (!PicFill.none(f)) {
                PicFill.layout(f[4], f, pic.getWidth(), pic.getHeight(), true, lay);
                faces = r.left + lay[PicFill.FL];
                wordsRight = Math.max(Ui.f(TEXT_X + WORDS_MIN), Math.min(wordsRight, faces - Ui.f(FACE_ROOM)));
            }
        }
        boolean onPicture = shown > 0f;
        int quiet = onPicture ? ON_PICTURE_QUIET : Look07.L3, words = onPicture ? ON_PICTURE : Look07.L2;
        nameP.setColor(words);
        metaP.setColor(words);
        descP.setColor(onPicture ? ON_PICTURE_QUIET : Look07.L2);
        nextLabelP.setColor(onPicture ? ON_PICTURE_QUIET : Look07.L3);
        nextTimeP.setColor(quiet);

        // What it is about, laid out once for its programme and width (its widest line kept for the shade).
        int dw = Math.round(wordsRight - Ui.f(TEXT_X));
        if (i >= 0 && !s.desc[i].isEmpty()) {
            if (!ch.key.equals(descKey) || s.start[i] != descStart || descL == null || dw != descWidth) {
                descL = lines(s.desc[i], descP, dw, DESC_LINES, 1.42f / 1.21f);
                descKey = ch.key;
                descStart = s.start[i];
                descWidth = dw;
                descWide = 0f;
                for (int k = 0; k < descL.getLineCount(); k++) descWide = Math.max(descWide, descL.getLineWidth(k));
            }
        }
        boolean hasDesc = i >= 0 && !s.desc[i].isEmpty() && descL != null;

        // The words' extent, for their shade: the title, its line, what it is about (kept widths).
        String name = ChannelEdits.name(ch);
        String title = i >= 0 ? s.title[i] : name;
        if (onPicture) {
            float x0 = Ui.f(TEXT_X);
            float end = x0 + Math.min(wordsRight - x0, titles.width(title));
            end = Math.max(end, x0 + Math.min(wordsRight - x0, lineWidth(s, i, now, archived, chipWords)));
            if (hasDesc) end = Math.max(end, x0 + descWide);
            shade(c, end, faces, shown);
        }

        // The channel: its logo, as the guide's channel cells, and its name centred under it (no number).
        float lx = Ui.f(LOGO_X), ly = Ui.f(top + 40f);
        RectF lr = r;
        lr.set(lx, ly, lx + Ui.f(LOGO_W), ly + Ui.f(CELL_H));
        ChannelLook.cell(c, lr, Ui.f(Look07.R_CHANNEL), ch, 0f, 0, "", false, redraw);
        float mid = Ui.f(LOGO_X + LOGO_W / 2f), room = Ui.f(NAME_W), nw = Math.min(names.width(name), room);
        names.line(c, name, mid - nw / 2f, Ui.f(top + NAME_Y), room);
        describe(ctx, ch, name);

        // No kicker (no LIVE, no category: the family's ask, 24 Nov): the title starts at the top, the words moved up.
        float x0 = Ui.f(TEXT_X), right = wordsRight, x;

        // The title.
        titles.line(c, title, x0, Ui.f(top + TITLE_Y), right - x0);

        // Its line: the times, how far through and what's left (or the archive's length), the chips.
        float cy = Ui.f(top + LINE_Y), base = Type.centred(metaP, cy), gap = Ui.f(16f);
        x = x0;
        if (i >= 0) {
            String span = Words.span(s.start[i], s.stop[i]);
            c.drawText(span, x, base, metaP);
            x += metas.width(span) + gap;
            if (archived) {
                Icons07.draw(c, Icons07.CATCHUP, x + Ui.f(10f), cy, Ui.f(20f), words);
                x += Ui.f(20f) + Ui.f(10f);
                String len = Words.minutes(s.stop[i] - s.start[i]);
                c.drawText(len, x, base, metaP);
                x += metas.width(len) + gap;
            } else if (s.start[i] <= now && now < s.stop[i]) {
                if (com.gathin.homefieldtv.prefs.Look.progress) {
                    Pills.progress(c, x, cy, Ui.f(200f), LiveText.progress(now, s.start[i], s.stop[i]));
                    x += Ui.f(200f) + gap;
                }
                String left = leftWords(s.stop[i] - now);
                c.drawText(left, x, base, metaP);
                x += metas.width(left) + gap;
            }
        } else {
            String none = s == null ? "No programme information" : "Nothing in the guide now";
            c.drawText(none, x, base, metaP);
            x += metas.width(none) + gap;
        }
        if (chipWords != null) {
            for (int k = 0; k < chipWords.size(); k++) {
                String w = chipWords.get(k);
                if (w == null || w.isEmpty()) continue;
                float cw = chips.width(w) + Ui.f(20f);
                if (x + cw > right) break;
                drawChip(c, x, cy, w, cw);
                x += cw + Ui.f(10f);
            }
        }

        // What it is about, two lines.
        if (hasDesc) Ui.draw(c, descL, x0, Ui.f(top + DESC_Y));

        // What's next: its time, its picture fitted round its faces, its title and times.
        int nx = s == null ? -1 : i >= 0 ? (i + 1 < s.start.length ? i + 1 : -1) : s.after(at);
        if (nx >= 0) drawNext(c, top, ch, s, nx);
        return fading;
    }

    /** The width of the card's line of times (kept widths and fixed sizes, as it is drawn). */
    private float lineWidth(Guide.Schedule s, int i, long now, boolean archived, List<String> chipWords) {
        if (i < 0) return 0f;
        float gap = Ui.f(16f), w = metas.width(Words.span(s.start[i], s.stop[i])) + gap;
        if (archived) {
            w += Ui.f(30f) + metas.width(Words.minutes(s.stop[i] - s.start[i])) + gap;
        } else if (s.start[i] <= now && now < s.stop[i]) {
            if (com.gathin.homefieldtv.prefs.Look.progress) w += Ui.f(200f) + gap;
            w += metas.width(leftWords(s.stop[i] - now)) + gap;
        }
        if (chipWords != null) {
            for (int k = 0; k < chipWords.size(); k++) {
                String c = chipWords.get(k);
                if (c != null && !c.isEmpty()) w += chips.width(c) + Ui.f(30f);
            }
        }
        return w;
    }

    /**
     * The programme's picture filling the card (rounded as it is), cross-fading from the last one: how much of a
     * picture shows (0 none, 1 all), for the shade and the words' colours.
     */
    private float picture(Canvas c, Channel ch, Guide.Schedule s, int i) {
        String url = i < 0 || s == null ? "" : settled && !Exec.inline ? ProgrammePictures.get(owner.getContext(), ch, s, i, redraw)
                : ProgrammePictures.peek(ch, s, i);
        // Read at the card's size as settle() reads the neighbours' (whichever top it is drawn at).
        int cw = Ui.px(W), chh = Ui.px(H);
        Bitmap want = url == null || url.isEmpty() ? null : Pics.get(url, cw, chh, Pics.FILL_CARD, redraw);
        boolean coming = url == null || want == null && !url.isEmpty() && !Pics.failed(url);
        long now = Exec.inline ? 0L : AnimationUtils.currentAnimationTimeMillis();
        if (want != pic) {
            if (want != null) {
                // Another programme's picture: in over the last one (or over the plain card).
                was = pic;
                pic = want;
                picFor = ch.key;
                picUrl = url;
                picAt = now;
                fading = !Exec.inline;
            } else if (!(coming && ch.key.equals(picFor))) {
                // None, or another channel's still to come: out to the plain card (never the wrong channel's picture).
                if (pic != null) {
                    was = pic;
                    pic = null;
                    picFor = "";
                    picAt = now;
                    fading = !Exec.inline;
                }
            }
        }
        float k = fading ? Fade.ease((now - picAt) / (float) FADE_MS) : 1f;
        if (fading && k >= 1f) {
            fading = false;
            was = null;
        }
        float rad = Ui.f(RADIUS);
        if (was != null && fading) drawPicture(c, was, pic == null ? 1f - k : 1f, rad);
        if (pic != null) drawPicture(c, pic, fading ? k : 1f, rad);
        return pic != null ? 1f : was != null && fading ? 1f - k : 0f;
    }

    private void drawPicture(Canvas c, Bitmap b, float alpha, float rad) {
        if (alpha <= 0f) return;
        Paint p = shaders.paint(b, r.width(), r.height(), 0f);
        p.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        c.save();
        c.translate(r.left, r.top);
        c.drawRoundRect(0, 0, r.width(), r.height(), rad, rad, p);
        c.restore();
        p.setAlpha(255);
    }

    /**
     * The shades over the picture: from the left to the words' end, fading on (FADE) to stop before the faces, and from
     * the right under what's next; k: how much of a picture shows.
     */
    private void shade(Canvas c, float end, float faces, float k) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, k)));
        float rad = Ui.f(RADIUS), h = r.height();
        float fade = Math.max(Ui.f(FADE_MIN), Math.min(Ui.f(FADE), faces - end + Ui.f(24f)));
        wordsShade.setAlpha(a);
        wordsFade.setAlpha(a);
        PicTile.shade(c, r, rad, end, fade, wordsShade, wordsFade);
        float nx = Ui.f(NEXT_X - NEXT_FADE), nw = r.right - nx;
        nextShade.setAlpha(a);
        c.save();
        c.translate(nx, r.top);
        c.scale(nw, 1f);
        c.drawRoundRect(0, 0, 1f, h, rad / nw, rad, nextShade);
        c.restore();
    }

    /** NEXT · 22:00, its picture (420 x 170, faces whole: PicTile), its title and times. */
    private void drawNext(Canvas c, float top, Channel ch, Guide.Schedule s, int n) {
        // Kept by channel and start (compared as they are: nothing made a frame).
        if (!ch.key.equals(nextKey) || s.start[n] != nextStart) {
            nextKey = ch.key;
            nextStart = s.start[n];
            nextLabel = "NEXT · " + LiveText.hm(s.start[n]);
        }
        float x = Ui.f(NEXT_X);
        c.drawText(nextLabel, x, Ui.f(top + 50f), nextLabelP);
        r.set(x, Ui.f(top + 64f), x + Ui.f(NEXT_W), Ui.f(top + 64f + NEXT_H));
        float rad = Ui.f(20f);
        next.draw(c, r, rad, ch, s, n, PicTile.COVER, false, false, settled);
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x24FFFFFF);
        c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, rad, rad, ring);
        nextTitles.line(c, s.title[n], x, Ui.f(top + 64f + NEXT_H + 33f), Ui.f(NEXT_W));
        c.drawText(Words.span(s.start[n], s.stop[n]), x, Ui.f(top + 64f + NEXT_H + 58f), nextTimeP);
    }

    /** A chip on the card: white at 14 % with a hairline at 22 %, 30 high, its letters bold and spaced. */
    private void drawChip(Canvas c, float x, float cy, String w, float cw) {
        float h = Ui.f(30f);
        RectF cr = chipBox;
        cr.set(x, cy - h / 2f, x + cw, cy + h / 2f);
        fill.setColor(0x24FFFFFF);
        c.drawRoundRect(cr, Ui.f(8f), Ui.f(8f), fill);
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x38FFFFFF);
        c.drawRoundRect(cr.left + sw / 2f, cr.top + sw / 2f, cr.right - sw / 2f, cr.bottom - sw / 2f, Ui.f(8f), Ui.f(8f), ring);
        c.drawText(w, x + Ui.f(10f), Type.centred(chipP, cy), chipP);
    }

    private final RectF chipBox = new RectF();

    /** "40 min left", "1h 10m left": what is left of a programme on now (kept words: GuideGrid.leftWords, by the minute). */
    private String leftWords(long ms) {
        int m = (int) Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m != leftFor) {
            leftFor = m;
            leftText = GuideGrid.leftWords(m);
        }
        return leftText;
    }

    private int leftFor = -1;
    private String leftText = "";

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
