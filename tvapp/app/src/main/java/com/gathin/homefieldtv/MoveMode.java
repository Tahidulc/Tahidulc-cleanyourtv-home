package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * A row moved by hand, as TiviMate's Manage positions moves one: Move channel in a channel's menu in the TV guide, Move
 * group in a group's. The row lifts (a soft shadow round it, the accent's 2 px edge and ▲▼ at its right), Up and Down
 * carry it past its neighbours (the channel keys a screenful), OK puts it down there and keeps the new order, Back puts
 * it back where it was; a hint at the foot says so. Nothing else is done while it is lifted.
 *
 * <pre>
 *   Channels   a channel among its group's (each one's place kept: ChannelEdits.order, the group then sorted Manual),
 *              or among the favourites (their order kept: Library.orderFavourites)
 *   Groups     a group among the playlist's groups in the groups column (Categories.placeShown: hidden ones keep their
 *              places), the guide staying on the group it shows
 * </pre>
 */
final class MoveMode {
    private MoveMode() {}

    /** A row being moved: the remote's keys go to it until it is put down or back. */
    abstract static class Move {
        /** A key while it is lifted: always used. */
        abstract boolean key(int code);

        /** OK: put down where it is, the new order kept. */
        abstract void drop();

        /** Back: put back where it was. */
        abstract void cancel();
    }

    // ---- a channel ------------------------------------------------------------------------------------------------

    /** What the guide does for a channel moved. */
    interface ChannelsHost {
        /** The list as it is now, the remote on the lifted channel. */
        void show(List<Channel> list, Channel lifted);

        /** Put down (moved: the new order kept) or back. */
        void done(Channel lifted, boolean moved);

        /** How many rows a page is (the channel keys). */
        int page();
    }

    static final class Channels extends Move {
        private final TvActivity tv;
        private final ChannelsHost host;
        private final List<Channel> list;
        private final Channel lifted;
        /** The group it is moved in, or null: the favourites. */
        private final String group;
        private final int from;
        private int at;

        Channels(TvActivity tv, ChannelsHost host, List<Channel> channels, Channel lifted, String group) {
            this.tv = tv;
            this.host = host;
            this.list = new ArrayList<>(channels);
            this.lifted = lifted;
            this.group = group;
            int i = 0;
            for (int k = 0; k < list.size(); k++) if (list.get(k).key.equals(lifted.key)) i = k;
            from = at = i;
        }

        @Override boolean key(int code) {
            int to = at;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP: to = at - 1; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: to = at + 1; break;
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP: to = at - host.page(); break;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN: to = at + host.page(); break;
                default: return true;
            }
            to = Math.max(0, Math.min(list.size() - 1, to));
            if (to != at) {
                list.remove(at);
                list.add(to, lifted);
                at = to;
                host.show(list, lifted);
            }
            return true;
        }

        @Override void drop() {
            boolean moved = at != from;
            if (moved) {
                List<String> keys = ChannelEdits.keys(list);
                if (group == null) {
                    Library.get(tv).orderFavourites(keys);
                } else {
                    // Each one's place kept; the group then in its own order, as TiviMate's manual sorting.
                    ChannelEdits.order(tv, keys);
                    int own = Categories.sortOf(tv, group);
                    if ((own >= 0 ? own : ChannelEdits.sort(tv)) != ChannelEdits.MANUAL) {
                        Categories.setSort(tv, group, ChannelEdits.MANUAL);
                        android.widget.Toast.makeText(tv, Categories.label(tv, group) + " is in your own order now (Sort channels: Manual)",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
            host.done(lifted, moved);
        }

        @Override void cancel() {
            host.done(lifted, false);
        }

        /** The channels in the order they are now (tests). */
        List<Channel> order() {
            return list;
        }
    }

    // ---- a group ------------------------------------------------------------------------------------------------

    /** What the guide does for a group moved. */
    interface GroupsHost {
        /** Put down (moved: the new order kept) or back. */
        void done(boolean moved);
    }

    static final class Groups extends Move {
        private final TvActivity tv;
        private final GroupsPanel panel;
        private final GroupsHost host;
        private final String prefix;

        /** The panel's rows lo..hi are the playlist's groups (their tags prefix and the group's name). */
        Groups(TvActivity tv, GroupsPanel panel, int lo, int hi, String prefix, GroupsHost host) {
            this.tv = tv;
            this.panel = panel;
            this.host = host;
            this.prefix = prefix;
            panel.lift(lo, hi);
        }

        @Override boolean key(int code) {
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) panel.key(code);
            return true;
        }

        @Override void drop() {
            boolean moved = panel.liftMoved();
            List<Object> tags = panel.drop();
            if (moved) {
                List<String> names = new ArrayList<>();
                for (Object t : tags) if (t instanceof String && ((String) t).startsWith(prefix)) names.add(((String) t).substring(prefix.length()));
                Categories.placeShown(tv, Library.get(tv).groups(), names);
            }
            host.done(moved);
        }

        @Override void cancel() {
            panel.cancelLift();
            host.done(false);
        }
    }

    // ---- how a lifted row looks -------------------------------------------------------------------------------------

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), EDGE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path ARROW = new Path();

    /**
     * The lifted row (screen pixels): a soft shadow round it, the accent's 2 px edge, and ▲ over ▼ at arrowsX (its middle
     * cy): it is moving, Up and Down carry it.
     */
    static void drawLifted(Canvas c, RectF row, float arrowsX, float cy) {
        drawLifted(c, row, arrowsX, cy, 0);
    }

    /** drawLifted with a disc of backing (0: none) under the arrows, where they lie over words. */
    static void drawLifted(Canvas c, RectF row, float arrowsX, float cy, int backing) {
        float r = Ui.f(10f);
        c.save();
        c.clipOutRect(row);
        for (int i = 4; i >= 1; i--) {
            float g = Ui.f(i * 3f);
            FILL.setColor(Palette.alpha(0xFF000000, 30));
            c.drawRoundRect(row.left - g, row.top - g + Ui.f(3f), row.right + g, row.bottom + g + Ui.f(3f), r + g, r + g, FILL);
        }
        c.restore();
        EDGE.setStyle(Paint.Style.STROKE);
        float sw = Math.max(1f, Ui.f(2f));
        EDGE.setStrokeWidth(sw);
        EDGE.setColor(Palette.ACCENT);
        c.drawRoundRect(row.left + sw / 2f, row.top + sw / 2f, row.right - sw / 2f, row.bottom - sw / 2f, r, r, EDGE);
        if (backing != 0) {
            FILL.setColor(backing);
            c.drawCircle(arrowsX, cy, Ui.f(26f), FILL);
        }
        arrows(c, arrowsX, cy, Palette.TEXT1);
    }

    /** ▲ over ▼, centred on (x, cy). */
    static void arrows(Canvas c, float x, float cy, int colour) {
        float s = Ui.f(9f), gap = Ui.f(5f);
        FILL.setColor(colour);
        ARROW.reset();
        ARROW.moveTo(x - s, cy - gap);
        ARROW.lineTo(x + s, cy - gap);
        ARROW.lineTo(x, cy - gap - s * 1.1f);
        ARROW.close();
        ARROW.moveTo(x - s, cy + gap);
        ARROW.lineTo(x + s, cy + gap);
        ARROW.lineTo(x, cy + gap + s * 1.1f);
        ARROW.close();
        c.drawPath(ARROW, FILL);
    }

    // ---- the hint at the foot ---------------------------------------------------------------------------------------

    /**
     * "Moving BBC One  ·  Up and Down move it  ·  OK puts it here  ·  Back leaves it where it was", centred at the foot (of
     * the guide right of the groups column while a group moves).
     */
    static final class Hint extends View {
        static final String KEYS = "Up and Down move it  ·  OK puts it here  ·  Back leaves it where it was";
        private String what = "";
        /** Where the room for it starts (design pixels). */
        private float left;
        private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint nameP = Type.paint(Type.SMALL, Type.MEDIUM, Palette.TEXT1), keysP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();

        Hint(Context c) {
            super(c);
            setVisibility(GONE);
        }

        void show(String name) {
            show(name, 0f);
        }

        /** Shown for a row named so, centred right of left (design pixels: the groups column's edge, for a group). */
        void show(String name, float left) {
            what = "Moving " + (name == null ? "" : name);
            this.left = left;
            setVisibility(VISIBLE);
            invalidate();
        }

        void hide() {
            setVisibility(GONE);
        }

        @Override protected void onDraw(Canvas c) {
            String name = what, keys = KEYS;
            nameP.setColor(Palette.TEXT1);
            keysP.setColor(Palette.TEXT2);
            float x0 = Ui.f(left), room = getWidth() - x0 - Ui.f(32f), gap = Ui.f(28f), kw = keysP.measureText(keys);
            float nw = Math.min(nameP.measureText(name), Math.max(Ui.f(160f), Math.min(Ui.f(560f), room - gap - kw - Ui.f(96f))));
            float w = nw + gap + kw + Ui.f(96f), h = Ui.f(64f), cx = (x0 + getWidth()) / 2f, bottom = Ui.f(1080f - 36f);
            r.set(cx - w / 2f, bottom - h, cx + w / 2f, bottom);
            back.setColor(Palette.alpha(Palette.PANEL, 0xF2));
            c.drawRoundRect(r, h / 2f, h / 2f, back);
            arrows(c, r.left + Ui.f(40f), r.centerY(), Palette.ACCENT_LIGHT);
            float x = r.left + Ui.f(68f);
            c.drawText(Ui.fit(name, nameP, nw), x, Type.centred(nameP, r.centerY()), nameP);
            c.drawText(keys, x + nw + gap, Type.centred(keysP, r.centerY()), keysP);
        }
    }
}
