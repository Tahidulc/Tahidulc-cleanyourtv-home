package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.History;
import com.gathin.homefieldtv.engine.Library;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * History, as the family's TiviMate shows it (Up held while watching, or its tile and button): over the picture, dimmed,
 * the programmes watched on this TV (engine.History), day by day ("Yesterday", "Today": 28 px grey at x 72, a rule
 * between days), each 111 high: its title (30; the light accent while it is on), its day and times with the catch-up
 * mark where the archive has it (or its bar and minutes left while it is on), the time it was watched at the right
 * (x 680), then the channel's logo (99 x 66 at 728) and name (x 851). The remote's entry is in the focus pill with its
 * delete button (Right goes to it); "History" and a bin for them all at the top right; the entry's card (1192, 160)
 * and its playlist and group at the foot.
 *
 * OK on a programme on now plays its channel; on one from the archive, plays it from its start. Up from the first goes
 * to the bin. Programmes that have ended and can't be played from an archive are left out (as TiviMate's "Show past
 * programs without catch-up": off).
 */
final class HistoryScreen extends Screen implements DialogCard.Host {
    static final float LIST_X = 48f, LIST_W = 1096f, TOP = 48f, HEADER_H = 90f, ENTRY_H = 111f, RULE_H = 33f;
    static final long SCROLL_MS = 150;
    private static final int HEADER = 0, ENTRY = 1, RULE = 2;

    /** A line of the list: a day's name, an entry, or the rule between days. */
    private static final class Line {
        final int kind;
        final String day;
        final History.Entry entry;
        final Channel channel;
        float y, h;
        /** Its day and times as the list writes them (made the first time it is drawn). */
        String span;

        Line(int kind, String day, History.Entry entry, Channel channel) {
            this.kind = kind;
            this.day = day;
            this.entry = entry;
            this.channel = channel;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    /** The remote: an entry's line, the bin at the top (BIN), and on the entry whether its delete button. */
    private int focus = -1;
    private boolean onDelete;
    private static final int BIN = -2;
    private Panel panel;
    private DialogCard dialog;
    /** The list's scroll (design pixels), gliding on the display's frame clock (a held key glides on smoothly). */
    private final Glide scroll = new Glide(SCROLL_MS);

    HistoryScreen(TvActivity tv) {
        super(tv);
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override View build() {
        FrameLayout root = new FrameLayout(tv);
        panel = new Panel(tv);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        root.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    @Override void shown() {
        load();
        tv.live.subs.room(false, -1f);
    }

    @Override void hidden() {
        tv.live.subs.room(true, -1f);
    }

    @Override void tick() {
        panel.invalidate();
    }

    /** The entries in their days, the remote on the newest. */
    private void load() {
        lines.clear();
        long now = System.currentTimeMillis();
        Library lib = Library.get(tv);
        String day = null;
        float y = TOP;
        for (History.Entry e : History.list(tv, now)) {
            Channel c = lib.byKey(e.key);
            if (c == null || !lib.listed(c)) continue;
            boolean onNow = e.stop == 0 || e.start <= now && now < e.stop;
            if (!onNow && (e.stop > now || !tv.live.canShow(c, e.start))) continue;
            String d = dayName(e.at, now);
            if (!d.equals(day)) {
                if (day != null) y = add(new Line(RULE, null, null, null), y, RULE_H);
                y = add(new Line(HEADER, d, null, null), y, HEADER_H);
                day = d;
            }
            y = add(new Line(ENTRY, null, e, c), y, ENTRY_H);
        }
        focus = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).kind == ENTRY) {
                focus = i;
                break;
            }
        }
        if (focus < 0) focus = BIN;
        onDelete = false;
        settle(true);
        panel.invalidate();
    }

    private float add(Line l, float y, float h) {
        l.y = y;
        l.h = h;
        lines.add(l);
        return y + h;
    }

    /** "Today", "Yesterday", else "Mon, 16 Nov". */
    static String dayName(long t, long now) {
        LocalDate d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        if (d.equals(today)) return "Today";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + ", " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
    }

    private Line focusedLine() {
        return focus >= 0 && focus < lines.size() ? lines.get(focus) : null;
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (dialog.showing()) return dialog.key(code);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (focus == BIN) {
                    if (dir > 0) moveTo(next(-1, 1));
                    return true;
                }
                int n = next(focus, dir);
                if (n >= 0) moveTo(n);
                else if (dir < 0) moveTo(BIN);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus >= 0) onDelete = true;
                panel.invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                onDelete = false;
                panel.invalidate();
                return true;
            default:
                return false;
        }
    }

    private int next(int from, int dir) {
        for (int k = from + dir; k >= 0 && k < lines.size(); k += dir) if (lines.get(k).kind == ENTRY) return k;
        return -1;
    }

    private void moveTo(int n) {
        if (n == -1) return;
        focus = n;
        onDelete = false;
        settle(false);
        panel.invalidate();
    }

    private void settle(boolean now) {
        Line l = focusedLine();
        float target = scroll.target();
        if (l != null) {
            float room = 1080f - TOP;
            float y0 = l.y - TOP, y1 = l.y + l.h - TOP;
            // A day's name just over the remote's entry stays in sight with it.
            if (focus > 0 && lines.get(focus - 1).kind == HEADER) y0 -= HEADER_H;
            float total = lines.isEmpty() ? 0f : lines.get(lines.size() - 1).y + lines.get(lines.size() - 1).h - TOP;
            target = RowList.scrollFor(scroll.target(), y0, y1, room, total);
        } else if (focus == BIN) {
            target = 0f;
        }
        if (now) scroll.jump(target);
        else scroll.to(target);
    }

    @Override void ok() {
        if (dialog.showing()) {
            dialog.ok();
            return;
        }
        if (focus == BIN) {
            if (lines.isEmpty()) return;
            List<RowList.Row> answers = new ArrayList<>();
            answers.add(RowList.Row.item("clear", "Clear history", null));
            answers.add(RowList.Row.item("keep", "Cancel", null));
            dialog.show("Clear history?", "Every programme watched on this TV comes off the list.", answers, 1);
            return;
        }
        Line l = focusedLine();
        if (l == null) return;
        if (onDelete) {
            History.remove(tv, l.entry);
            int was = focus;
            load();
            // The remote on the entry that took its place, or the one before.
            int n = next(Math.max(-1, Math.min(was, lines.size()) - 1), 1);
            if (n < 0) n = next(lines.size(), -1);
            focus = n >= 0 ? n : BIN;
            settle(true);
            panel.invalidate();
            return;
        }
        long now = System.currentTimeMillis();
        History.Entry e = l.entry;
        Channel c = l.channel;
        if (e.stop == 0 || e.start <= now && now < e.stop) {
            // On now: its channel, live.
            tv.pop();
            Screen under = tv.top();
            if (under instanceof LiveScreen) ((LiveScreen) under).tuneFromList(c);
            else tv.watch(c);
        } else {
            tv.watchFrom(c, e.start, e.stop);
        }
    }

    @Override boolean back() {
        if (dialog.showing()) {
            dialog.hide(null);
            return true;
        }
        return false;
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        d.hide(null);
        if ("clear".equals(answer.tag)) {
            History.clear(tv);
            load();
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private final class Panel extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint iconLine = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint dayP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT2), titleP = Type.paint(30f, Type.REGULAR, Palette.TEXT1);
        private final TextPaint lineP = Type.paint(26f, Type.REGULAR, Palette.TEXT2), nameP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT1);
        private final TextPaint headP = Type.paint(48f, Type.REGULAR, Palette.TEXT1), whereP = Type.paint(32f, Type.REGULAR, Palette.TEXT1);
        private final TextPaint cardTitleP = Type.paint(36f, Type.MEDIUM, Palette.TEXT1), descP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint emptyP = Type.paint(Type.ITEM, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private final InfoCard where = new InfoCard(this);
        /** The entry the card was laid out for; the words measured once (TextFit). */
        private History.Entry cardEntry;
        private TextFit titles, words, names;
        private StaticLayout cardTitle, cardDesc;

        Panel(Context c) {
            super(c);
            iconLine.setStyle(Paint.Style.STROKE);
            iconLine.setStrokeCap(Paint.Cap.ROUND);
            iconLine.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = scroll.step();
            float sc = scroll.value();
            // The picture dimmed under it all.
            c.drawColor(Palette.alpha(Palette.GROUND, 0xB8));
            long now = System.currentTimeMillis();
            Library lib = Library.get(getContext());

            // "History" and the bin at the top right.
            headP.setColor(Palette.TEXT1);
            c.drawText("History", Ui.f(1752f) - headP.measureText("History"), Ui.f(96f), headP);
            float bx = Ui.f(1836f), by = Ui.f(78f);
            if (focus == BIN) Focus.circle(c, bx, by, Ui.f(72f));
            else {
                fill.setColor(Palette.SELECTED);
                c.drawCircle(bx, by, Ui.f(36f), fill);
            }
            icon(c, Dark.BIN, bx, by, 32f, Palette.TEXT1);

            if (lines.isEmpty()) {
                emptyP.setColor(Palette.TEXT2);
                c.drawText("Programmes you watch for a little while come here", Ui.f(72f), Ui.f(150f), emptyP);
            }
            c.save();
            c.clipRect(0, Ui.f(TOP - 8f), Ui.f(LIST_X + LIST_W + 8f), getHeight());
            for (int i = 0; i < lines.size(); i++) {
                Line l = lines.get(i);
                float y = l.y - sc;
                if (y > 1080f) break;
                if (y + l.h < 0f) continue;
                switch (l.kind) {
                    case HEADER:
                        dayP.setColor(Palette.TEXT2);
                        c.drawText(l.day, Ui.f(72f), Type.centred(dayP, Ui.f(y + l.h / 2f)), dayP);
                        break;
                    case RULE:
                        fill.setColor(Palette.DIVIDER);
                        c.drawRect(Ui.f(72f), Ui.f(y + 31f), Ui.f(1120f), Ui.f(y + 33f), fill);
                        break;
                    default:
                        drawEntry(c, l, y, i == focus, now, lib);
                        break;
                }
            }
            c.restore();

            Line f = focusedLine();
            if (f != null) {
                drawCard(c, f, now, lib);
                String w = where.where(getContext(), f.channel);
                whereP.setColor(Palette.TEXT1);
                c.drawText(w, Ui.f(1872f) - whereP.measureText(w), Ui.f(1048f), whereP);
            }
            if (moving) postInvalidateOnAnimation();
        }

        private void icon(Canvas c, int kind, float cx, float cy, float s, int colour) {
            iconLine.setColor(colour);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(s * 0.075f)));
            iconFill.setColor(colour);
            Dark.icon(c, kind, cx, cy, Ui.f(s), iconLine, iconFill);
        }

        private void drawEntry(Canvas c, Line l, float y, boolean lit, long now, Library lib) {
            History.Entry e = l.entry;
            r.set(Ui.f(LIST_X), Ui.f(y), Ui.f(LIST_X + LIST_W), Ui.f(y + ENTRY_H));
            if (lit && !onDelete) Focus.pill(c, r);
            else if (lit) Focus.selected(c, r);
            boolean onNow = e.stop == 0 || e.start <= now && now < e.stop;
            titleP.setColor(onNow ? Palette.ACCENT_LIGHT : Palette.TEXT1);
            if (titles == null) {
                titles = new TextFit(titleP, 128);
                words = new TextFit(lineP, 128);
                names = new TextFit(nameP, 128);
            }
            titles.line(c, e.title, Ui.f(72f), Ui.f(y + 50f), Ui.f(640f));
            // Its day and times, the catch-up mark or how far it is, and when it was watched (kept with the line).
            lineP.setColor(Palette.TEXT2);
            float x = Ui.f(72f), base = Ui.f(y + 87f);
            if (e.stop > 0) {
                if (l.span == null) {
                    boolean today = dayName(e.start, now).equals("Today");
                    l.span = (today ? "" : dayName(e.start, now) + ", ") + LiveText.span(e.start, e.stop);
                }
                c.drawText(l.span, x, base, lineP);
                x += words.width(l.span) + Ui.f(24);
                if (onNow) {
                    Bars.thin(c, x, base - Ui.f(9), Ui.f(80), Ui.f(6), LiveText.progress(now, e.start, e.stop));
                    x += Ui.f(80 + 24);
                    c.drawText(Words.minutes(e.stop - now), x, base, lineP);
                } else {
                    icon(c, Dark.HISTORY, x + Ui.f(14), base - Ui.f(9), 28f, Palette.TEXT2);
                }
            }
            String at = Words.hm(e.at);
            c.drawText(at, Ui.f(680f) - words.width(at), base, lineP);
            // The channel.
            Bitmap logo = Pics.get(l.channel.logo, Ui.px(99), Ui.px(66), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) c.drawBitmap(logo, Ui.f(728f) + (Ui.f(99) - logo.getWidth()) / 2f, Ui.f(y + 22f) + (Ui.f(66) - logo.getHeight()) / 2f, bmp);
            nameP.setColor(Palette.TEXT1);
            names.line(c, l.channel.name, Ui.f(851f), Type.centred(nameP, Ui.f(y + ENTRY_H / 2f)), Ui.f(lit ? 196f : 260f));
            if (lit) {
                float cx = Ui.f(1092f), cy = Ui.f(y + ENTRY_H / 2f);
                if (onDelete) Focus.circle(c, cx, cy, Ui.f(72f));
                icon(c, Dark.BIN, cx, cy, 32f, Palette.TEXT1);
            }
        }

        /** The focused programme's card at the top right: title, times (and the catch-up mark), what it is about. */
        private void drawCard(Canvas c, Line l, long now, Library lib) {
            History.Entry e = l.entry;
            Guide.Schedule s = lib.schedule(e.key);
            int i = s == null || e.stop == 0 ? -1 : s.at(e.start);
            String desc = i >= 0 && s.start[i] == e.start ? s.desc[i] : "";
            float x0 = 1192f, y0 = 160f, inner = 680f - 64f;
            if (e != cardEntry) {
                cardEntry = e;
                cardTitleP.setColor(Palette.TEXT1);
                descP.setColor(Palette.TEXT2);
                cardTitle = InfoCard.lines(e.title, cardTitleP, Ui.px(inner), 2, 42f / 36f);
                cardDesc = desc.isEmpty() ? null : InfoCard.lines(desc, descP, Ui.px(inner), 4, 33f / 28f);
            }
            float th = cardTitle.getHeight() / Ui.s, dh = cardDesc == null ? 0f : cardDesc.getHeight() / Ui.s + 14f;
            float h = 32f + th + 18f + 36f + dh + 30f;
            r.set(Ui.f(x0), Ui.f(y0), Ui.f(1872f), Ui.f(y0 + h));
            fill.setColor(Palette.PLAYBACK);
            c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
            float tx = Ui.f(x0 + 32f), y = y0 + 32f;
            Ui.draw(c, cardTitle, tx, Ui.f(y));
            y += th + 18f;
            lineP.setColor(Palette.TEXT2);
            float base = Ui.f(y + 28f);
            if (e.stop > 0) {
                String sp = Words.span(e.start, e.stop);
                c.drawText(sp, tx, base, lineP);
                float ix = tx + words.width(sp) + Ui.f(24);
                boolean onNow = e.start <= now && now < e.stop;
                if (onNow) Bars.thin(c, ix, base - Ui.f(9), Ui.f(80), Ui.f(6), LiveText.progress(now, e.start, e.stop));
                else icon(c, Dark.HISTORY, ix + Ui.f(14), base - Ui.f(9), 28f, Palette.TEXT2);
            }
            y += 36f + 14f;
            if (cardDesc != null) Ui.draw(c, cardDesc, tx, Ui.f(y));
        }
    }
}
