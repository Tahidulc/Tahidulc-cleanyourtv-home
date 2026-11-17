package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.List;

/**
 * Search: the letters on the left, and as they are typed, the family's channels, films and TV shows whose names have
 * them, on the right. OK on a channel watches it, on a title opens its page.
 */
final class SearchScreen extends Screen implements Keyboard.Host, ListPanel.Host {
    private Strip strip;
    private Keyboard keys;
    private ListPanel results;
    private boolean inResults;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable run = this::search;

    SearchScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        strip = new Strip(tv);
        keys = new Keyboard(tv, Keyboard.SEARCH);
        keys.x = 262f;
        keys.y = 64f;
        keys.label = "Search";
        keys.hint = "Channels, films and TV shows";
        keys.setHost(this);
        results = new ListPanel(tv);
        results.x = 1000f;
        results.y = 108f;
        results.w = 860f;
        results.setHost(this);
        f.addView(keys, new FrameLayout.LayoutParams(-1, -1));
        f.addView(results, new FrameLayout.LayoutParams(-1, -1));
        f.addView(strip, new FrameLayout.LayoutParams(-1, -1));
        search();
        return f;
    }

    /** Previews: the letters typed already. */
    void type(String text) {
        keys.setText(text);
        search();
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
    }

    @Override void hidden() {
        h.removeCallbacks(run);
    }

    @Override void tick() {
        strip.invalidate();
    }

    @Override void channelsChanged() {
        h.removeCallbacks(run);
        h.postDelayed(run, 150);
    }

    @Override void catalogChanged() {
        channelsChanged();
    }

    /** The search asked last: an answer to an older one is let go. */
    private int asked;

    private void search() {
        String q = keys.text().trim();
        int ask = ++asked;
        if (q.isEmpty()) {
            List<ListPanel.Item> out = new ArrayList<>();
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "Type a channel, a film or a TV show.", ""));
            results.setItems(out);
            return;
        }
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        // Every channel, film and series looked through off the main thread; the answer shown once it comes, if the
        // letters are still the same.
        Exec.work(() -> {
            List<Channel> ch = lib.search(q, 8);
            List<Catalog.Title> films = cat.search(q, false, 12), shows = cat.search(q, true, 12);
            Runnable show = () -> {
                if (ask == asked) show(ch, films, shows);
            };
            if (Exec.inline) show.run();
            else Exec.main(show);
        });
    }

    private void show(List<Channel> ch, List<Catalog.Title> films, List<Catalog.Title> shows) {
        Library lib = Library.get(tv);
        long now = System.currentTimeMillis();
        List<ListPanel.Item> out = new ArrayList<>();
        if (!ch.isEmpty()) {
            out.add(new ListPanel.Item(null, ListPanel.HEADING, "Channels", ""));
            for (int i = 0; i < ch.size() && i < 8; i++) {
                Channel c = ch.get(i);
                Guide.Schedule s = lib.schedule(c.key);
                int p = s == null ? -1 : s.at(now);
                ListPanel.Item it = new ListPanel.Item(c, ListPanel.LOGO, lib.number(c) + "  " + c.name,
                        p >= 0 ? "Now: " + s.title[p] : c.group);
                it.picture = c.logo;
                out.add(it);
            }
        }
        add(out, "Films", films);
        add(out, "TV shows", shows);
        if (out.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, "Nothing in your playlist called that.", ""));
        results.setItems(out);
    }

    private static void add(List<ListPanel.Item> out, String heading, List<Catalog.Title> titles) {
        if (titles.isEmpty()) return;
        out.add(new ListPanel.Item(null, ListPanel.HEADING, heading, ""));
        for (Catalog.Title t : titles) {
            String sub = t.category + (t.year.isEmpty() ? "" : (t.category.isEmpty() ? "" : "  ·  ") + t.year);
            ListPanel.Item it = new ListPanel.Item(t, ListPanel.POSTER, t.name, sub);
            it.picture = t.poster;
            out.add(it);
        }
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (inResults) return results.key(code);
        return keys.key(code, e);
    }

    @Override void ok() {
        if (inResults) results.ok();
        else keys.ok();
    }

    @Override boolean back() {
        if (inResults) {
            toKeys();
            return true;
        }
        return false;
    }

    private void toKeys() {
        inResults = false;
        results.setActive(false);
        keys.setActive(true);
    }

    @Override public void typed(Keyboard k, String text) {
        h.removeCallbacks(run);
        h.postDelayed(run, 180);
    }

    @Override public void done(Keyboard k, String text) {}

    @Override public boolean edge(Keyboard k, int dir) {
        if (dir == Ui.RIGHT && results.canFocus()) {
            inResults = true;
            keys.setActive(false);
            results.setActive(true);
            return true;
        }
        if (dir == Ui.LEFT) {
            tv.pop();
            return true;
        }
        return true;
    }

    @Override public void chosen(ListPanel list, ListPanel.Item item) {
        if (item.tag instanceof Channel) tv.watch((Channel) item.tag);
        else if (item.tag instanceof Catalog.Title) tv.openTitle((Catalog.Title) item.tag);
    }

    @Override public void focused(ListPanel list, ListPanel.Item item) {}

    @Override public boolean edge(ListPanel list, int dir) {
        if (dir == Ui.LEFT) {
            toKeys();
            return true;
        }
        return true;
    }
}
