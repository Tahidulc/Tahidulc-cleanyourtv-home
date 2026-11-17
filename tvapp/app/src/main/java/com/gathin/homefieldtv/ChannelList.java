package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Lineup;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Categories;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's channel list, as the family's TiviMate draws it, in its two modes:
 *
 * <ul>
 *   <li>OVERLAY (Left while watching): over the moving picture. The channels down the left (600 px of the playback
 *   panel, its band "Royal • TNT Sports" at the top in the header colour), the channel the remote is on's programmes in
 *   a column beside it over a 60 % shade, and its programme in a card at the top right.</li>
 *   <li>PREVIEW (Left held, or the bar's Channels list): opaque. The channels on the panel colour, the programmes on the
 *   ground, the date at the top right in the light accent, the picture playing in a 736 x 414 window (1144, 72) and the
 *   programme's card under it.</li>
 * </ul>
 *
 * A channel's row (125 a row): its logo (96 x 72), its name (28, white), what's on in the light accent and its bar (4);
 * the one playing has the accent's ▶. The remote's row is in the focus pill (568 x 117, 16 in) and stays the second row
 * while it can; programmes are 75 a row from y 115, the one on now in the light accent, five before it shown. Left opens
 * the groups (GroupsPanel: the playlist, Favourites, All channels, then its groups, the remote's group held at y 502):
 * the channels move right beside them, and moving onto a group shows its channels at once; Right or OK goes back to the
 * channels. OK on a channel plays it (the list stays); OK on the one playing goes full screen. Back closes the groups,
 * then the list.
 */
final class ChannelList extends FrameLayout implements GroupsPanel.Host {
    static final int OVERLAY = 0, PREVIEW = 1;
    static final float COL_W = 600f, BAND_H = 73f, ROW = 125f, ROWS_TOP = 89f, PILL_H = 117f, SCHED_X = 600f;
    static final float SCHED_ROW = 75f, SCHED_TOP = 115f, GROUPS_W = 500f, ROLL_MS = 120f;
    /** The preview's window for the picture (design pixels), and its card under it. */
    static final float VIDEO_X = 1144f, VIDEO_Y = 72f, VIDEO_W = 736f, VIDEO_H = 414f, CARD_Y = 526f;
    /** The groups' tags beside the playlist's own groups. */
    static final String ALL = Library.ALL, FAVOURITES = Library.FAVOURITES;

    interface Host {
        /** OK on a channel that isn't playing: play it (the list stays). */
        void tune(Channel c);

        /** OK on the channel playing: the list goes, full screen. */
        void full();

        /** Back out of the list. */
        void closed();

        /** A key used: the screen's wait before hiding starts again. */
        void poke();
    }

    private final TvActivity tv;
    private final int mode;
    private final Host host;
    private final Columns columns;
    private final GroupsPanel groups;
    private Lineup lineup;
    private String group = ALL;
    private int focus;
    private boolean groupsOpen;
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt;

    ChannelList(TvActivity tv, int mode, Host host) {
        super(tv);
        this.tv = tv;
        this.mode = mode;
        this.host = host;
        lineup = Library.get(tv).lineup(java.util.Collections.<Channel>emptyList());
        columns = new Columns(tv);
        groups = new GroupsPanel(tv);
        groups.x = 0f;
        groups.setHost(this);
        groups.setVisibility(GONE);
        addView(columns, new LayoutParams(-1, -1));
        addView(groups, new LayoutParams(-1, -1));
    }

    int mode() {
        return mode;
    }

    /** Drawn again (a minute on, a channel changed). */
    void refresh() {
        columns.invalidate();
        groups.invalidate();
    }

    /** The list on the playing channel's group, the remote on it. */
    void open(Channel playing) {
        Library lib = Library.get(tv);
        group = playing == null || lib.group(playing.group).indexOf(playing) < 0 ? ALL : playing.group;
        load();
        focus = Math.max(0, lineup.indexOf(playing));
        groupsOpen = false;
        groups.setVisibility(GONE);
        settle(true);
        columns.invalidate();
    }

    private void load() {
        Library lib = Library.get(tv);
        if (FAVOURITES.equals(group)) lineup = lib.lineup(lib.list(FAVOURITES, null));
        else if (ALL.equals(group)) lineup = lib.lineup();
        else lineup = lib.group(group);
    }

    private Channel focused() {
        return focus >= 0 && focus < lineup.channels.size() ? lineup.channels.get(focus) : null;
    }

    // ---- the groups -------------------------------------------------------------------------------------------------

    private void openGroups() {
        Library lib = Library.get(tv);
        List<GroupsPanel.Entry> e = new ArrayList<>();
        List<Sources.Entry> lists = Sources.list(tv);
        e.add(GroupsPanel.Entry.heading(lists.size() == 1 ? lists.get(0).name : "All playlists", null));
        e.add(GroupsPanel.Entry.group("Favourites", FAVOURITES));
        e.add(GroupsPanel.Entry.group("All channels", ALL));
        for (String g : Categories.arranged(tv, lib.rowGroups())) {
            if (!g.isEmpty()) e.add(GroupsPanel.Entry.group(Categories.label(tv, g), g));
        }
        groups.background = mode == PREVIEW ? Palette.PANEL : Palette.PLAYBACK;
        groups.set(e, 1);
        groups.choose(group);
        groups.setActive(true);
        groups.setVisibility(VISIBLE);
        groups.slideIn();
        groupsOpen = true;
        columns.invalidate();
    }

    private void closeGroups() {
        groupsOpen = false;
        groups.setVisibility(GONE);
        columns.invalidate();
    }

    @Override public void focused(GroupsPanel panel, GroupsPanel.Entry entry) {
        // A group shows at once as the remote moves onto it.
        if (entry.tag == null) return;
        group = (String) entry.tag;
        load();
        focus = Math.max(0, lineup.indexOf(tv.live.channel()));
        settle(true);
        columns.invalidate();
        host.poke();
    }

    @Override public void chosen(GroupsPanel panel, GroupsPanel.Entry entry) {
        closeGroups();
    }

    @Override public boolean edge(GroupsPanel panel, int dir) {
        if (dir == Ui.RIGHT) closeGroups();
        return true;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        host.poke();
        if (groupsOpen) return groups.key(code);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                move(-7);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                move(7);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                openGroups();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private void move(int by) {
        int n = Math.max(0, Math.min(lineup.channels.size() - 1, focus + by));
        if (n == focus) return;
        focus = n;
        settle(false);
        columns.invalidate();
    }

    void ok() {
        host.poke();
        if (groupsOpen) {
            groups.ok();
            return;
        }
        Channel c = focused();
        if (c == null) return;
        Channel playing = tv.live.channel();
        if (playing != null && playing.key.equals(c.key)) host.full();
        else host.tune(c);
        columns.invalidate();
    }

    /** Back: the groups close first, then the list (false: it was already closed). */
    boolean back() {
        if (groupsOpen) {
            closeGroups();
            return true;
        }
        host.closed();
        return true;
    }

    /** Pure: the scroll (design pixels) that keeps row focus the second row, within a list of n rows in room. */
    static float scrollFor(int focus, int n, float room) {
        float max = Math.max(0f, ROWS_TOP + n * ROW - room);
        return Math.max(0f, Math.min(max, (focus - 1) * ROW));
    }

    private void settle(boolean now) {
        float t = scrollFor(focus, lineup.channels.size(), 1080f);
        if (now || Exec.inline) {
            scroll = scrollFrom = scrollTo = t;
            scrollAt = 0;
        } else if (t != scrollTo) {
            scrollFrom = scroll;
            scrollTo = t;
            scrollAt = SystemClock.uptimeMillis();
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    /** The columns: channels, programmes, the card (and in the preview, the ground with the picture's window). */
    private final class Columns extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final TextPaint bandP = Type.paint(30f, Type.REGULAR, Palette.TEXT1), nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
        private final TextPaint onP = Type.paint(Type.ROW, Type.REGULAR, Palette.ACCENT_LIGHT), timeP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint headP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), dateP = Type.paint(Type.ROW, Type.MEDIUM, Palette.ACCENT_LIGHT);
        private final TextPaint cardTitleP = Type.paint(36f, Type.MEDIUM, Palette.TEXT1), descP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint logoP = Type.paint(Type.SMALL, Type.MEDIUM, Palette.TEXT1);
        private final RectF r = new RectF();
        private final Path hole = new Path(), tri = new Path();
        private final Runnable redraw = this::invalidate;
        private String cardKey = "", bandKey = "", bandText = "", whereKey = "", whereText = "";
        private StaticLayout cardTitle, cardDesc;
        private final InfoCard where;

        Columns(Context c) {
            super(c);
            where = new InfoCard(this);
        }

        @Override protected void onDraw(Canvas c) {
            long t = SystemClock.uptimeMillis();
            boolean moving = false;
            if (scrollAt != 0) {
                float k = Math.min(1f, (t - scrollAt) / ROLL_MS);
                scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
                if (k >= 1f) scrollAt = 0;
                else moving = true;
            }
            long now = System.currentTimeMillis();
            Library lib = Library.get(getContext());
            boolean preview = mode == PREVIEW;
            float x0 = groupsOpen ? GROUPS_W + (preview ? 2f : 0f) : 0f;
            if (preview) {
                // The ground everywhere but the picture's window.
                hole.reset();
                r.set(Ui.f(VIDEO_X), Ui.f(VIDEO_Y), Ui.f(VIDEO_X + VIDEO_W), Ui.f(VIDEO_Y + VIDEO_H));
                hole.addRoundRect(r, Ui.f(17f), Ui.f(17f), Path.Direction.CW);
                c.save();
                c.clipOutPath(hole);
                c.drawColor(Palette.GROUND);
                c.restore();
            }
            Channel f = focused();
            // The programmes of the channel the remote is on (not while the groups are open).
            if (!groupsOpen && f != null) drawSchedule(c, f, now, lib, preview);
            // The channels.
            fill.setColor(preview ? Palette.PANEL : Palette.PLAYBACK);
            c.drawRect(Ui.f(x0), Ui.f(BAND_H), Ui.f(x0 + COL_W), getHeight(), fill);
            fill.setColor(preview ? Palette.HEADER : Palette.alpha(Palette.HEADER, Palette.PLAYBACK_ALPHA));
            c.drawRect(Ui.f(x0), 0, Ui.f(x0 + COL_W), Ui.f(BAND_H), fill);
            if (!group.equals(bandKey)) {
                bandKey = group;
                List<Sources.Entry> lists = Sources.list(getContext());
                String g = FAVOURITES.equals(group) ? "Favourites" : ALL.equals(group) ? "All channels" : Categories.label(getContext(), group);
                bandText = InfoCard.where(lists.size() == 1 ? lists.get(0).name : "", g);
            }
            bandP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(bandText, bandP, Ui.f(COL_W - 64f)), Ui.f(x0 + 32f), Ui.f(48f), bandP);
            c.save();
            c.clipRect(Ui.f(x0), Ui.f(BAND_H), Ui.f(x0 + COL_W), getHeight());
            Channel playing = tv.live.channel();
            int first = Math.max(0, (int) ((scroll - ROWS_TOP) / ROW) - 1);
            for (int k = first; k < lineup.channels.size(); k++) {
                float top = ROWS_TOP + k * ROW - scroll;
                if (top > 1080f) break;
                if (top + ROW < BAND_H) continue;
                Channel ch = lineup.channels.get(k);
                drawRow(c, ch, x0, top, k == focus, playing != null && playing.key.equals(ch.key), now, lib);
            }
            c.restore();
            if (preview) {
                // The date and the clock over the picture's window, its programme's card under it.
                String date = Ui.dateClock(now);
                dateP.setColor(Palette.ACCENT_LIGHT);
                c.drawText(date, Ui.f(1880f) - dateP.measureText(date), Ui.f(46f), dateP);
                if (f != null) drawCard(c, f, VIDEO_X, CARD_Y, VIDEO_X + VIDEO_W, Palette.PANEL, now, lib);
            } else if (!groupsOpen && f != null) {
                drawCard(c, f, 1155f, 50f, 1871f, Palette.PLAYBACK, now, lib);
            }
            if (moving) postInvalidateOnAnimation();
        }

        private void drawRow(Canvas c, Channel ch, float x0, float top, boolean lit, boolean playing, long now, Library lib) {
            if (lit) {
                r.set(Ui.f(x0 + 16f), Ui.f(top), Ui.f(x0 + COL_W - 16f), Ui.f(top + PILL_H));
                if (groupsOpen) Focus.selected(c, r);
                else Focus.pill(c, r);
            }
            Bitmap logo = Pics.get(ch.logo, Ui.px(96), Ui.px(72), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) {
                c.drawBitmap(logo, Ui.f(x0 + 32f) + (Ui.f(96) - logo.getWidth()) / 2f, Ui.f(top + 22f) + (Ui.f(72) - logo.getHeight()) / 2f, bmp);
            } else {
                logoP.setColor(Palette.TEXT2);
                c.drawText(Ui.fit(ch.name, logoP, Ui.f(96)), Ui.f(x0 + 32f), Ui.f(top + 66f), logoP);
            }
            float tx = Ui.f(x0 + 144f), room = Ui.f(COL_W - 144f - 32f - (playing ? 48f : 0f));
            nameP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(ch.name, nameP, room), tx, Ui.f(top + 42f), nameP);
            if (playing) {
                // The channel playing: the accent's ▶.
                float px = Ui.f(x0 + 556f), py = Ui.f(top + 32f), s = Ui.f(16f);
                tri.reset();
                tri.moveTo(px - s * 0.7f, py - s);
                tri.lineTo(px + s, py);
                tri.lineTo(px - s * 0.7f, py + s);
                tri.close();
                fill.setColor(Palette.ACCENT);
                c.drawPath(tri, fill);
            }
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            if (i >= 0) {
                onP.setColor(Palette.ACCENT_LIGHT);
                c.drawText(Ui.fit(s.title[i], onP, Ui.f(COL_W - 144f - 32f)), tx, Ui.f(top + 82f), onP);
                Bars.thin(c, tx, Ui.f(top + 99f), Ui.f(424f), Ui.f(4f), LiveText.progress(now, s.start[i], s.stop[i]));
            }
        }

        private void drawSchedule(Canvas c, Channel ch, long now, Library lib, boolean preview) {
            float x0 = SCHED_X, x1 = preview ? 1104f : 1132f;
            if (!preview) {
                fill.setColor(Palette.SCRIM);
                c.drawRect(Ui.f(x0), 0, Ui.f(x1), getHeight(), fill);
            }
            // Its logo, name and where it is from at the top.
            Bitmap logo = Pics.get(ch.logo, Ui.px(108), Ui.px(72), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) c.drawBitmap(logo, Ui.f(624f) + (Ui.f(108) - logo.getWidth()) / 2f, Ui.f(20f) + (Ui.f(72) - logo.getHeight()) / 2f, bmp);
            headP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(ch.name, headP, Ui.f(x1 - 748f - 16f)), Ui.f(748f), Ui.f(46f), headP);
            if (!ch.key.equals(whereKey)) {
                whereKey = ch.key;
                whereText = where.where(getContext(), ch);
            }
            timeP.setColor(Palette.TEXT2);
            c.drawText(Ui.fit(whereText, timeP, Ui.f(x1 - 748f - 16f)), Ui.f(748f), Ui.f(86f), timeP);
            Guide.Schedule s = lib.schedule(ch.key);
            if (s == null) return;
            int at = s.at(now), from = Math.max(0, (at >= 0 ? at : Math.max(0, s.after(now))) - 5);
            for (int k = from, row = 0; k < s.start.length; k++, row++) {
                float cy = SCHED_TOP + row * SCHED_ROW + SCHED_ROW / 2f;
                if (cy > 1080f) break;
                boolean on = k == at;
                TextPaint p = on ? onP : timeP;
                p.setColor(on ? Palette.ACCENT_LIGHT : Palette.TEXT2);
                float base = Type.centred(p, Ui.f(cy));
                String hm = LiveText.hm(s.start[k]);
                c.drawText(hm, Ui.f(628f), base, p);
                c.drawText(Ui.fit(s.title[k], p, Ui.f(x1 - 727f - 4f)), Ui.f(727f), base, p);
            }
        }

        /** The programme's card: its title (two lines), times, bar and minutes left, what it is about. */
        private void drawCard(Canvas c, Channel ch, float x0, float y0, float x1, int colour, long now, Library lib) {
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            if (i < 0) return;
            String k = ch.key + "|" + s.start[i] + "|" + (x1 - x0);
            float inner = x1 - x0 - 64f;
            if (!k.equals(cardKey)) {
                cardKey = k;
                cardTitleP.setColor(Palette.TEXT1);
                descP.setColor(Palette.TEXT2);
                cardTitle = InfoCard.lines(s.title[i], cardTitleP, Ui.px(inner), 2, 42f / 36f);
                cardDesc = s.desc[i].isEmpty() ? null : InfoCard.lines(s.desc[i], descP, Ui.px(inner), 4, 33f / 28f);
            }
            float th = cardTitle.getHeight() / Ui.s, dh = cardDesc == null ? 0f : cardDesc.getHeight() / Ui.s + 18f;
            float h = 30f + th + 20f + 40f + dh + 28f;
            r.set(Ui.f(x0), Ui.f(y0), Ui.f(x1), Ui.f(y0 + h));
            fill.setColor(colour);
            c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
            float tx = Ui.f(x0 + 32f), y = y0 + 30f;
            Ui.draw(c, cardTitle, tx, Ui.f(y));
            y += th + 20f;
            String sp = LiveText.span(s.start[i], s.stop[i]);
            timeP.setColor(Palette.TEXT2);
            float base = Ui.f(y + 28f);
            c.drawText(sp, tx, base, timeP);
            float bx = tx + timeP.measureText(sp) + Ui.f(25);
            Bars.thin(c, bx, base - Ui.f(10), Ui.f(80), Ui.f(6), LiveText.progress(now, s.start[i], s.stop[i]));
            if (s.stop[i] > now) c.drawText(InfoCard.minutes(s.stop[i] - now), bx + Ui.f(80 + 24), base, timeP);
            y += 40f + 18f;
            if (cardDesc != null) Ui.draw(c, cardDesc, tx, Ui.f(y));
        }
    }

}
