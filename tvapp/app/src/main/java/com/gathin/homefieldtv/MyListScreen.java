package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Arrays;
import java.util.List;

/**
 * My list, from the menu: for now the rail, its lists down a groups column beside it (My TV programmes, My reminders,
 * My films, My TV shows: GroupsPanel, the remote on the first) and the list the remote is on as an empty list's message
 * in the room to the right. A screen of its own so the menu has every section; what fills its lists comes later. Left
 * from the lists opens the menu; Right or Back closes it; Back from the lists goes back.
 */
final class MyListScreen extends Screen implements GroupsPanel.Host {
    private static final String[] LISTS = {"My TV programmes", "My reminders", "My films", "My TV shows"};
    private static final String[] EMPTY = {"No programmes", "No reminders", "No films", "No TV shows"};
    private static final String[] HOW = {"Hold OK on a programme in the TV guide to add it here.",
            "Hold OK on a programme still to come in the TV guide and choose Remind me.",
            "Films you add to My list from their page will be here.", "TV shows you add to My list from their page will be here."};

    private Rail rail;
    private GroupsPanel lists;
    private Empty empty;

    MyListScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Palette.GROUND);
        rail = new Rail(tv, Rail.MY_LIST);
        lists = new GroupsPanel(tv);
        lists.setHost(this);
        List<GroupsPanel.Entry> entries = Arrays.asList(GroupsPanel.Entry.group(LISTS[0], 0), GroupsPanel.Entry.group(LISTS[1], 1),
                GroupsPanel.Entry.group(LISTS[2], 2), GroupsPanel.Entry.group(LISTS[3], 3));
        lists.set(entries, 0);
        lists.choose(0);
        empty = new Empty(tv);
        f.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        f.addView(lists, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
    }

    @Override boolean key(int code, KeyEvent e) {
        if (rail.active()) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                fromRail();
                return true;
            }
            return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
        }
        return lists.key(code);
    }

    private void fromRail() {
        rail.setActive(false);
        lists.setActive(true);
    }

    @Override void ok() {
        if (rail.active()) {
            if (rail.focus == Rail.MY_LIST) fromRail();
            else Rail.go(tv, rail.focus, Rail.MY_LIST);
            return;
        }
        lists.ok();
    }

    @Override boolean back() {
        if (rail.active()) {
            fromRail();
            return true;
        }
        return false;
    }

    @Override public void focused(GroupsPanel panel, GroupsPanel.Entry entry) {
        // The list the remote is on, at once.
        lists.choose(entry.tag);
        empty.which = (Integer) entry.tag;
        empty.invalidate();
    }

    @Override public void chosen(GroupsPanel panel, GroupsPanel.Entry entry) {}

    @Override public boolean edge(GroupsPanel panel, int dir) {
        if (dir == Ui.LEFT) {
            lists.setActive(false);
            rail.setActive(true);
        }
        return true;
    }

    /** The list's empty message, in the room right of the groups column. */
    private static final class Empty extends View {
        int which;
        private final RectF area = new RectF();

        Empty(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            area.set(Ui.f(Rail.W + GroupsPanel.W), 0, getWidth(), getHeight());
            EmptyState.draw(c, area, Dark.BOOKMARK, 120f, EMPTY[which], HOW[which]);
        }
    }
}
