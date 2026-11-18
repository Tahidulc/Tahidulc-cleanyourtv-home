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

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The TV guide as 07's shelves (the approved still 04c), the second Guide style (Settings › Appearance › TV guide):
 * "Live Guide" and what it shows ("Tonight · All channels · Wednesday 18 November") with the lists as glass pills at
 * the right; then a shelf a channel, the remote's at the top: its cell in its logo's colour (ChannelLook) with the logo
 * on a disc of its colour, the amber star of a favourite, its number and name; its evening as 07's cards (Card07: 400 x
 * 225, the picture fitted round its faces), what's on now first with LIVE and how far through, the later ones with
 * their times (a bell for a reminder), each card's title and times under it; the card the remote is on lifted and tilted,
 * the shelves under it darker; at the foot a frosted sheet with the programme the remote is on: the channel, its title,
 * its times and chips, what OK does (Watch Live, Remind Me), and what it is about.
 *
 * <p>It draws the grid's own state (GuideGrid: its channels, the remote's channel and its time), so every key does
 * what it does in the grid: Up and Down a channel (Up from the first: the tab bar), Left and Right a programme (Left
 * from what's on now: the groups), OK, OK held, the channel keys, the numbers. Nothing is made a frame: the words are
 * kept (TextFit, the grid's Lines), the pictures' addresses looked up once (the grid's Art), the pictures read without
 * a new key (Pics); they are asked for only once the remote rests.
 */
final class GuideShelves extends View {
    interface Host {
        /** The channel playing (its ▶), or null. */
        Channel playing();

        /** The lists' names as pills, and which is on screen. */
        List<String> pills();

        int pillOn();

        /** What the list on screen is called ("All channels"). */
        String listName();
    }

    /** Where things go (design px, the still's .gd-*). */
    static final float SHELVES_Y = 262f, SHELF = 330f, LABEL_X = 90f, LABEL_W = 160f, DISC = 120f, CARD_X = 300f, CARD_W = 400f,
            CARD_H = 225f, CARD_GAP = 40f, SHEET_Y = 746f, SHEET_X = 48f;
    static final int CARDS = 5;

    private final Library lib;
    private final GuideGrid grid;
    private Host host;
    private final Card07 cards = new Card07(Look07.R_CARD);
    private final Card07.Marks marks = new Card07.Marks();
    private final Frost sheet = new Frost(Look07.MAT);
    private final GuideGrid.Art art = new GuideGrid.Art();
    private final GuideGrid.Lines lines = new GuideGrid.Lines();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), disc = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
            bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(Type.H2, Type.BOLD, Look07.L1), subP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2);
    private final TextPaint numP = Type.clock(21f, Type.SEMIBOLD, 0xE0FFFFFF), nameP = Type.paint(20f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint kickP = Type.paint(21f, Type.SEMIBOLD, Look07.L2), bigP = Type.paint(54f, Type.BOLD, Look07.L1);
    private final TextPaint metaP = Type.clock(22f, Type.MEDIUM, Look07.L2), descP = Type.paint(23f, Type.REGULAR, Look07.L2);
    private final TextPaint progP = Type.clock(20f, Type.MEDIUM, Look07.L2);
    private final TextFit names = new TextFit(nameP, 64), bigs = new TextFit(bigP, 32), kicks = new TextFit(kickP, 32), metas = new TextFit(metaP, 32);
    private final RectF r = new RectF(), o = new RectF();
    private final Runnable redraw = this::invalidate;
    private final Runnable artKnown = () -> {
        art.generation++;
        invalidate();
    };
    /** Where the shelves are drawn (rows, gliding to the remote's), and the remote's shelf's cards (cards, gliding). */
    private final Glide down = new Glide(420, SHELF), along = new Glide(300, CARD_W + CARD_GAP);
    private Channel lastFocus;
    /** The card lifted (its shelf's channel's place and its programme's start) and the one settling. */
    private int liftRow = -1, wasRow = -1;
    private long liftAt = Long.MIN_VALUE, wasAt = Long.MIN_VALUE;
    private Glide up = spring(), settle = spring();
    private String headKey = "", headSub = "", sheetKey = "", sheetKick = "", sheetMeta = "", sheetMain = "", sheetQuality = "";
    private int sheetIcon;
    private StaticLayout descL;
    /** What's left of the programmes on now, kept by programme and minute (a few at a time: nothing made a frame). */
    private final String[] liveFor = new String[8];
    private final long[] liveMinute = new long[8];
    private final Guide.Schedule[] liveSched = new Guide.Schedule[8];
    private final int[] liveIndex = new int[8];
    private int liveNext;

    GuideShelves(Context c, Library lib, GuideGrid grid) {
        super(c);
        this.lib = lib;
        this.grid = grid;
        dark.setColor(0x80000000);
        Shadows.prepare(Look07.SH_CARD, Look07.R_CARD);
        Shadows.prepare(Look07.SH_CARD_UP, Look07.R_CARD);
        Shadows.prepare(Look07.SH_PANEL, Look07.R_SHEET);
    }

    void setHost(Host h) {
        host = h;
    }

    private static Glide spring() {
        Glide g = new Glide(Look07.LIFT_MS, 100f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK);
        g.jump(0f);
        return g;
    }

    /** The programme on now on a channel (else the next one), the first card of its shelf; -1 for none. */
    private static int first(Guide.Schedule s, long now) {
        if (s == null || s.start.length == 0) return -1;
        int i = s.at(now);
        return i >= 0 ? i : s.after(now);
    }

    /** The remote's card's place along its shelf (0 the first), or -1. */
    private int focusCard() {
        Channel ch = grid.channel();
        Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
        int i0 = first(s, System.currentTimeMillis()), i = s == null ? -1 : s.at(grid.cursor());
        if (i0 < 0) return -1;
        if (i < 0) i = s.after(grid.cursor());
        return i < 0 ? -1 : Math.max(0, i - i0);
    }

    // ---- what GuideScreen asks ----------------------------------------------------------------------------------------

    /** The remote has rested: the pictures of the cards on screen asked for (Sky's guide, through ProgrammePictures). */
    void askPictures() {
        if (Exec.inline || grid.count() == 0) return;
        long now = System.currentTimeMillis();
        for (int d = -1; d <= 2; d++) {
            Channel ch = grid.channelAt(d);
            Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
            int i0 = first(s, now);
            if (i0 < 0) continue;
            int from = d == 0 ? Math.max(0, focusCard() - 3) : 0;
            for (int k = from; k < from + CARDS && i0 + k < s.start.length; k++) ProgrammePictures.get(getContext(), ch, s, i0 + k, artKnown);
        }
    }

    /** Where the remote's card is (screen px, before the guide is pushed right), for the small menu under it. */
    RectF focusedCard(RectF out) {
        int j = Math.max(0, focusCard());
        float x = CARD_X + j * (CARD_W + CARD_GAP) - along.target() * (CARD_W + CARD_GAP);
        out.set(Ui.f(x), Ui.f(SHELVES_Y), Ui.f(x + CARD_W), Ui.f(SHELVES_Y + CARD_H));
        return out;
    }

    /** The remote's shelf (its label and cards), for the menus to leave undimmed. */
    RectF focusedShelf(RectF out) {
        out.set(Ui.f(LABEL_X - 14f), Ui.f(SHELVES_Y - 18f), Ui.f(1920f), Ui.f(SHELVES_Y + SHELF - 12f));
        return out;
    }

    void refresh() {
        art.clear();
        invalidate();
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        Channel here = grid.channel();
        if (here != lastFocus) {
            // Another channel: the shelves glide to it (at once for another list).
            int was = lastFocus == null ? -1 : indexNear(lastFocus);
            lastFocus = here;
            if (was < 0 || Exec.inline) down.jump(0f);
            else {
                // The new one starts where it was drawn (a shelf below when Down went to it), then glides up.
                down.shift(-was);
                down.to(0f);
            }
        }
        int fc = focusCard();
        along.to(Math.max(0, fc - 2));
        boolean moving = down.step() | along.step();
        moving |= stepCard(here == null ? -1 : 0, fc < 0 || here == null ? Long.MIN_VALUE : startOf(here, fc, now));
        drawHead(c, now);
        if (grid.count() == 0) {
            r.set(0, Ui.f(SHELVES_Y), Ui.f(1920f), getHeight());
            EmptyState.draw(c, r, Dark.TV, 120f, "No channels here", "");
            if (moving) postInvalidateOnAnimation();
            return;
        }
        c.save();
        c.clipRect(0, Ui.f(SHELVES_Y - 40f), getWidth(), Ui.f(SHEET_Y + 80f));
        float off = down.value();
        // Below first (the sheet hides most of them), the remote's last, over them.
        for (int d = 2; d >= -1; d--) {
            Channel ch = grid.channelAt(d);
            if (ch == null) continue;
            float y = SHELVES_Y + (d + off) * SHELF;
            if (y > SHEET_Y + 40f || y + SHELF < SHELVES_Y - 330f) continue;
            if (d == 0) continue;
            drawShelf(c, ch, d, y, now);
        }
        if (here != null) drawShelf(c, here, 0, SHELVES_Y + off * SHELF, now);
        c.restore();
        if (here != null) drawSheet(c, here, now);
        if (moving) postInvalidateOnAnimation();
    }

    /** The channel's place relative to the remote's now (-2 to 2), or -1 far away. */
    private int indexNear(Channel ch) {
        for (int d = -2; d <= 2; d++) if (grid.channelAt(d) == ch) return d;
        return -1;
    }

    private long startOf(Channel ch, int k, long now) {
        Guide.Schedule s = lib.schedule(ch.key);
        int i0 = first(s, now);
        return i0 < 0 || i0 + k >= s.start.length ? Long.MIN_VALUE : s.start[i0 + k];
    }

    /** The card lift (07's spring) following the remote's card. */
    private boolean stepCard(int row, long at) {
        if (row != liftRow || at != liftAt) {
            Glide t = settle;
            settle = up;
            up = t;
            if (!(at != Long.MIN_VALUE && row == wasRow && at == wasAt)) up.jump(0f);
            wasRow = liftRow;
            wasAt = liftAt;
            liftRow = row;
            liftAt = at;
            up.to(at == Long.MIN_VALUE ? 0f : 1f);
            settle.to(0f);
        }
        return up.step() | settle.step();
    }

    private float liftOf(int row, long at) {
        if (row == liftRow && at == liftAt && at != Long.MIN_VALUE) return up.value();
        if (row == wasRow && at == wasAt) return settle.value();
        return 0f;
    }

    /** "Live Guide", what it shows, and the lists as glass pills at the right. */
    private void drawHead(Canvas c, long now) {
        c.drawText("Live Guide", Ui.f(90f), Ui.f(180f), titleP);
        String list = host == null ? "" : host.listName();
        long day = Words.dayIndex(now);
        String key = list + "|" + day;
        if (!key.equals(headKey)) {
            headKey = key;
            java.time.LocalDate d = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
            headSub = "Tonight · " + list + " · " + d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.getDayOfMonth() + " "
                    + d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
        }
        c.drawText(headSub, Ui.f(90f), Ui.f(218f), subP);
        List<String> pills = host == null ? null : host.pills();
        if (pills == null || pills.isEmpty()) return;
        int on = host.pillOn();
        float h = 52f, pad = 6f, total = pad * 2;
        int n = 0;
        for (String p : pills) {
            float w = Pills.pillWidth(p, false) / Ui.s;
            if (total + w + 2f > 900f) break;
            total += w + 2f;
            n++;
        }
        float right = 1830f, x = right - total, cy = 150f + 32f;
        r.set(Ui.f(x), Ui.f(150f), Ui.f(right), Ui.f(150f + h + pad * 2));
        Frost.glass(c, r, r.height() / 2f, 1f);
        float px = Ui.f(x + pad);
        for (int i = 0; i < n; i++) px += Pills.pill(c, px, Ui.f(cy), pills.get(i), null, i == on, false, h) + Ui.f(2f);
    }

    /** A shelf: its channel's label, then its cards; shelves under the remote's darker, the one above leaving. */
    private void drawShelf(Canvas c, Channel ch, int d, float y, long now) {
        boolean here = d == 0;
        float dim = d > 0 ? 0.5f : d < 0 ? Math.min(1f, Math.max(0f, (SHELVES_Y - y) / SHELF)) : 0f;
        drawLabel(c, ch, y, here);
        if (d != 0) {
            // Darker, the colours staying true (the label's cell darkened rather than faded).
            r.set(Ui.f(LABEL_X - 10f), Ui.f(y - 14f), Ui.f(LABEL_X + LABEL_W + 10f), Ui.f(y + 221f));
            dark.setAlpha(Math.round(0x61 * (d > 0 ? 1f : 1f - dim) + (d < 0 ? 0xC0 * dim : 0)));
            c.drawRoundRect(r, Ui.f(30f), Ui.f(30f), dark);
        }
        Guide.Schedule s = lib.schedule(ch.key);
        int i0 = first(s, now);
        if (i0 < 0) return;
        float shift = here ? along.value() : 0f;
        int fromCard = Math.max(0, (int) Math.floor(shift));
        int fc = here ? focusCard() : -1;
        for (int k = fromCard; k < fromCard + CARDS + 1 && i0 + k < s.start.length; k++) {
            float x = CARD_X + (k - shift) * (CARD_W + CARD_GAP);
            if (x > 1920f) break;
            if (k == fc) continue;
            drawCard(c, ch, s, i0 + k, k, x, y, d, now);
        }
        if (fc >= 0 && i0 + fc < s.start.length) drawCard(c, ch, s, i0 + fc, fc, CARD_X + (fc - shift) * (CARD_W + CARD_GAP), y, d, now);
    }

    /** The channel's label: its cell in its logo's colour, the logo on a disc of its colour, the star, its number and name. */
    private void drawLabel(Canvas c, Channel ch, float y, boolean here) {
        r.set(Ui.f(LABEL_X - 10f), Ui.f(y - 14f), Ui.f(LABEL_X + LABEL_W + 10f), Ui.f(y + 221f));
        // The remote's card carries the focus: the label stays as it is (the plain row's white pill where Behind logos
        // is None or Black tile).
        int words = ChannelLook.fillCell(c, r, Ui.f(30f), ch, ChannelLook.colour(ch) == 0 && here ? 1f : 0f);
        float cx = Ui.f(LABEL_X + LABEL_W / 2f), cy = Ui.f(y + DISC / 2f), rad = Ui.f(DISC / 2f);
        // The disc in the logo's own colour (its tile's fall), the logo bare on it.
        Logos.Tint t = Logos.behind() == Logos.COLOUR ? Logos.tint(LogoPack.url(ch)) : null;
        if (t == null) t = Logos.Tint.WAITING;
        disc.setShader(null);
        disc.setColor(t.tileFoot | 0xFF000000);
        c.drawCircle(cx, cy, rad, disc);
        disc.setColor(Palette.alpha(t.tileTop, 0x99));
        c.drawCircle(cx, cy - rad * 0.08f, rad * 0.92f, disc);
        String url = LogoPack.url(ch);
        Bitmap b = url.isEmpty() ? null : Pics.get(url, Ui.px(DISC * 0.76f), Ui.px(DISC * 0.52f), Pics.LOGO, redraw);
        if (b != null) {
            c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, bmp);
        } else {
            Icons07.draw(c, Icons07.TV, cx, cy, Ui.f(44f), Look07.L3);
        }
        if (favourite(ch)) {
            float sx = cx + rad - Ui.f(2f + 15f), sy = cy - rad + Ui.f(2f + 15f);
            fill.setColor(Look07.AMBER);
            c.drawCircle(sx, sy, Ui.f(15f), fill);
            Icons07.draw(c, Icons07.STAR, sx, sy, Ui.f(16f), Look07.AMBER_INK);
        }
        int n = lib.number(ch);
        numP.setColor(words == Look07.INK ? Look07.INK2 : 0xE0FFFFFF);
        if (n > 0) {
            String num = Words.number(n);
            c.drawText(num, cx - numP.measureText(num) / 2f, Ui.f(y + DISC + 12f + 21f), numP);
        }
        nameP.setColor(words);
        String name = ChannelEdits.name(ch);
        float room = Ui.f(LABEL_W);
        if (names.fitsOne(name, room)) {
            float w = names.width(name);
            names.line(c, name, cx - w / 2f, Ui.f(y + DISC + 12f + 25f + 2f + 20f), room);
        } else {
            names.lines(c, name, cx - room / 2f, Ui.f(y + DISC + 59f), Ui.f(y + DISC + 59f), Ui.f(y + DISC + 83f), room, 2);
        }
    }

    private final java.util.Map<Channel, Boolean> favourites = new java.util.WeakHashMap<>();

    private boolean favourite(Channel ch) {
        Boolean f = favourites.get(ch);
        if (f == null) {
            f = lib.isFavourite(ch);
            favourites.put(ch, f);
        }
        return f;
    }

    void favouritesChanged() {
        favourites.clear();
        invalidate();
    }

    /** One card: the programme's picture fitted round its faces (else its channel's logo), its marks, its words under it. */
    private void drawCard(Canvas c, Channel ch, Guide.Schedule s, int i, int k, float x, float y, int d, long now) {
        float w = Ui.f(CARD_W), h = Ui.f(CARD_H), sx = Ui.f(x), sy = Ui.f(y);
        float upv = d == 0 ? liftOf(0, s.start[i]) : 0f;
        String url = art.url(ch, s, i);
        Bitmap pic = url == null || url.isEmpty() ? null : Pics.get(url, cards.picW(w), cards.picH(h), Pics.FACE_MID, redraw);
        boolean live = s.start[i] <= now && now < s.stop[i];
        marks.clear();
        if (live) {
            marks.live = true;
            if (com.gathin.homefieldtv.prefs.Look.progress) marks.progress = LiveText.progress(now, s.start[i], s.stop[i]);
        } else {
            marks.time = Words.hm(s.start[i]);
            marks.bell = s.start[i] > now && Reminders.any(getContext()) && Reminders.has(getContext(), ch.key, s.start[i]);
        }
        cards.begin(c, sx, sy, w, h, upv, pic);
        if (pic == null) {
            // No picture: the channel's logo on its tile in the middle.
            float lh = h * 0.36f, lw = lh * 1.69f;
            o.set(w / 2f - lw / 2f, h / 2f - lh / 2f, w / 2f + lw / 2f, h / 2f + lh / 2f);
            Logos.draw(c, ch, o, redraw);
        }
        cards.marks(c, w, h, marks);
        cards.end(c);
        if (d > 0) {
            // Under the remote's shelf: darker.
            r.set(sx, sy, sx + w, sy + h);
            dark.setAlpha(0x80);
            c.drawRoundRect(r, Ui.f(Look07.R_CARD), Ui.f(Look07.R_CARD), dark);
        }
        if (d < 0) return;
        String sub = live ? leftOf(s, i, now) : lines.of(s.start[i], s.stop[i], -1);
        cards.caption(c, sx, sy + h, w, upv, s.title[i], sub);
    }

    /** "40 min left" for a programme on now (kept by programme and minute). */
    private String leftOf(Guide.Schedule s, int i, long now) {
        long minute = now / 60_000L;
        for (int k = 0; k < liveFor.length; k++) {
            if (liveSched[k] == s && liveIndex[k] == i && liveMinute[k] == minute && liveFor[k] != null) return liveFor[k];
        }
        int slot = liveNext;
        liveNext = (liveNext + 1) % liveFor.length;
        liveSched[slot] = s;
        liveIndex[slot] = i;
        liveMinute[slot] = minute;
        liveFor[slot] = GuideGrid.leftWords((int) Math.max(1L, (s.stop[i] - now + 59_999L) / 60_000L));
        return liveFor[slot];
    }

    /** The frosted sheet at the foot: the programme the remote is on, what OK does, what it is about. */
    private void drawSheet(Canvas c, Channel ch, long now) {
        sheet.sheet(c, Ui.f(SHEET_X), Ui.f(SHEET_Y), Ui.f(1920f - 2 * SHEET_X), getHeight(), Ui.f(Look07.R_SHEET), 1f);
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(grid.cursor());
        if (i < 0 && s != null) i = s.after(grid.cursor());
        boolean live = i >= 0 && s.start[i] <= now && now < s.stop[i];
        String key = ch.key + "|" + (i >= 0 ? s.start[i] : 0) + "|" + live;
        if (!key.equals(sheetKey)) {
            sheetKey = key;
            int n = lib.number(ch);
            sheetKick = ChannelEdits.name(ch) + (n > 0 ? " · " + n : "");
            sheetMeta = i < 0 ? "No information" : LiveText.hm(s.start[i]) + " – " + LiveText.hm(s.stop[i]);
            sheetQuality = LiveText.quality(ch.name);
            boolean reminded = i >= 0 && s.start[i] > now && Reminders.has(getContext(), ch.key, s.start[i]);
            sheetMain = live ? "Watch Live" : i >= 0 && s.start[i] > now ? (reminded ? "Cancel Reminder" : "Remind Me") : "Watch";
            sheetIcon = live ? Icons07.PLAY : i >= 0 && s.start[i] > now ? Icons07.BELL : Icons07.PLAY;
            String d = i >= 0 ? s.desc[i] : "";
            descL = d.isEmpty() ? null : StaticLayout.Builder.obtain(d, 0, d.length(), descP, Ui.px(586f)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.42f / 1.21f).setIncludePad(false).setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build();
        }
        float x0 = SHEET_X + 64f, top = SHEET_Y + 40f;
        // The channel: its logo on its tile, its name and number, LIVE.
        r.set(Ui.f(x0), Ui.f(top), Ui.f(x0 + 70f), Ui.f(top + 42f));
        Logos.draw(c, ch, r, redraw);
        float kx = Ui.f(x0 + 70f + 14f), kcy = Ui.f(top + 21f);
        float kw = kicks.line(c, sheetKick, kx, Type.centred(kickP, kcy), Ui.f(700f));
        if (live) Pills.live(c, kx + kw + Ui.f(14f), kcy);
        // Its title, its times and chips.
        bigs.line(c, i >= 0 ? s.title[i] : ChannelEdits.name(ch), Ui.f(x0), Ui.f(top + 42f + 12f + 52f), Ui.f(1040f));
        float my = Ui.f(top + 42f + 12f + 56f + 12f + 15f), mx = Ui.f(x0);
        float mw = metas.line(c, sheetMeta, mx, Type.centred(metaP, my), Ui.f(600f));
        if (!sheetQuality.isEmpty()) Pills.chip(c, mx + mw + Ui.f(12f), my, sheetQuality);
        // What OK does, a second way, My list.
        float by = Ui.f(top + 42f + 12f + 56f + 12f + 30f + 24f + 35f), bx = Ui.f(x0);
        bx += Pills.button(c, bx, by, sheetMain, sheetIcon, true, false) + Ui.f(18f);
        if (live) bx += Pills.button(c, bx, by, "Start Over", Icons07.RESTART, false, false) + Ui.f(18f);
        else bx += Pills.button(c, bx, by, "Watch Channel", Icons07.PLAY, false, false) + Ui.f(18f);
        Pills.circle(c, bx + Ui.f(35f), by, Icons07.PLUS, false, false);
        // What it is about; how far through the one on now is.
        float rx = Ui.f(x0 + 1040f + 70f);
        if (descL != null) Ui.draw(c, descL, rx, Ui.f(top + 4f));
        if (live) {
            float py = Ui.f(top + 4f + 4 * 32.7f + 22f);
            String a = Words.hm(s.start[i]);
            c.drawText(a, rx, Type.centred(progP, py), progP);
            float bx0 = rx + progP.measureText(a) + Ui.f(16f), bw = Ui.f(300f);
            Pills.progress(c, bx0, py, bw, LiveText.progress(now, s.start[i], s.stop[i]));
            c.drawText(leftOf(s, i, now), bx0 + bw + Ui.f(16f), Type.centred(progP, py), progP);
        }
    }
}
