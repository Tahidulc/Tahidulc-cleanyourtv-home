package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Genres;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The TV guide: the panel down the left with the mini TV and the categories (All channels, Entertainment, Sport, News,
 * Movies, Kids, the favourites, then the playlist's own groups), and the grid of programmes to its right. OK watches
 * the channel; OK held adds it to the favourites (or takes it off); the number keys go to a channel.
 */
final class GuideScreen extends Screen implements GuideGrid.Host {
    private static final String ALL = "All channels", FAVOURITES = "Favourites";

    private final String startCategory;
    private Panel panel;
    private GuideGrid grid;
    private final List<String> cats = new ArrayList<>();
    private final List<String> kinds = new ArrayList<>();
    private final Map<String, String> genreOf = new HashMap<>();
    private boolean inPanel;
    private int shownCat = -1;
    private String digits = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable digitsDone = this::jump;

    /** category: one of Genres' names to open on, or null for all the channels. */
    GuideScreen(TvActivity tv, String category) {
        super(tv);
        this.startCategory = category;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        panel = new Panel(tv);
        panel.first = 560f;
        grid = new GuideGrid(tv, Library.get(tv));
        grid.setHost(this);
        f.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        f.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        categories();
        int at = startCategory == null ? 0 : Math.max(0, cats.indexOf(startCategory));
        panel.setItems(cats, at);
        load(at);
        // No channels to go through yet: the remote starts on the categories.
        inPanel = grid.count() == 0;
        panel.setActive(inPanel);
        grid.setActive(!inPanel);
        return f;
    }

    /** The categories that have channels: the guide's own kinds first, then the playlist's groups. */
    private void categories() {
        Library lib = Library.get(tv);
        cats.clear();
        kinds.clear();
        genreOf.clear();
        Map<String, Integer> counts = new HashMap<>();
        for (Channel c : lib.all()) {
            String g = Genres.of(c.group, c.name);
            genreOf.put(c.key, g);
            Integer n = counts.get(g);
            counts.put(g, n == null ? 1 : n + 1);
        }
        cats.add(ALL);
        kinds.add("all");
        for (String g : Genres.ORDER) {
            if (counts.containsKey(g) || g.equals(startCategory)) {
                cats.add(g);
                kinds.add("genre");
            }
        }
        cats.add(FAVOURITES);
        kinds.add("fav");
        for (String g : lib.groups()) {
            if (cats.contains(g)) continue;
            cats.add(g);
            kinds.add("group");
        }
    }

    private List<Channel> channelsOf(int i) {
        Library lib = Library.get(tv);
        if (i < 0 || i >= cats.size()) return lib.all();
        switch (kinds.get(i)) {
            case "fav":
                return lib.list(Library.FAVOURITES, "");
            case "genre": {
                List<Channel> out = new ArrayList<>();
                String g = cats.get(i);
                for (Channel c : lib.all()) if (g.equals(genreOf.get(c.key))) out.add(c);
                return out;
            }
            case "group":
                return lib.list(cats.get(i), "");
            default:
                return lib.all();
        }
    }

    private void load(int i) {
        load(i, null);
    }

    /** A category's channels in the grid, the remote on focusOn if it is there, else on the channel playing. */
    private void load(int i, Channel focusOn) {
        shownCat = i;
        Library lib = Library.get(tv);
        List<Channel> list = channelsOf(i);
        Channel playing = focusOn != null ? focusOn : tv.live.channel() != null ? tv.live.channel() : lib.lastChannel();
        grid.setChannels(list, playing);
        if (!lib.hasSource()) grid.empty = "Add your playlist in Settings, and its channels and guide show here.";
        else if (!lib.ready()) grid.empty = lib.busy() ? "Loading your channels…" : lib.status();
        else if (FAVOURITES.equals(cats.get(i))) grid.empty = "No favourites yet. Hold OK on a channel to add it.";
        else grid.empty = "No channels here.";
    }

    @Override void shown() {
        tv.live.place(panel.tvRect());
        Channel c = tv.channelToPlay();
        if (c != null) tv.live.play(c);
        else tv.live.forget();
        grid.refresh();
    }

    @Override void hidden() {
        h.removeCallbacks(digitsDone);
    }

    @Override void tick() {
        panel.invalidate();
        grid.invalidate();
    }

    @Override void channelsChanged() {
        String was = shownCat >= 0 && shownCat < cats.size() ? cats.get(shownCat) : ALL;
        Channel on = grid.channel();
        categories();
        int at = Math.max(0, cats.indexOf(was));
        panel.setItems(cats, at);
        load(at, on);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits += (char) ('0' + code - KeyEvent.KEYCODE_0);
            h.removeCallbacks(digitsDone);
            if (digits.length() >= 4) jump();
            else h.postDelayed(digitsDone, 1500);
            return true;
        }
        if (inPanel) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                toGrid();
                return true;
            }
            if (panel.key(code)) {
                if (panel.focus != shownCat) load(panel.focus);
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if (grid.count() == 0 && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            leftEdge();
            return true;
        }
        return grid.key(code);
    }

    private void toGrid() {
        if (grid.count() == 0) return;
        inPanel = false;
        panel.setActive(false);
        grid.setActive(true);
    }

    private void jump() {
        h.removeCallbacks(digitsDone);
        if (digits.isEmpty()) return;
        int n;
        try {
            n = Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            n = -1;
        }
        digits = "";
        if (n < 0) return;
        if (!grid.jumpTo(n) && shownCat != 0) {
            // Not in this category: all the channels, and there.
            panel.setItems(cats, 0);
            load(0);
            grid.jumpTo(n);
        }
        toGrid();
    }

    @Override void ok() {
        if (inPanel) {
            toGrid();
            return;
        }
        Channel c = grid.channel();
        if (c != null) tv.watch(c);
    }

    @Override void okLong() {
        if (inPanel) return;
        Channel c = grid.channel();
        if (c == null) return;
        boolean now = Library.get(tv).toggleFavourite(c);
        android.widget.Toast.makeText(tv, now ? c.name + " added to your favourites" : c.name + " taken off your favourites",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override public void moved() {}

    @Override public void leftEdge() {
        inPanel = true;
        panel.setActive(true);
        grid.setActive(false);
    }
}
