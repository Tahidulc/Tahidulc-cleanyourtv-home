package com.gathin.homefieldtv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.PlayRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The TV launcher's "play this channel" (PlayRequest.ACTION: every channel link on it, from its guide, its cards, its
 * scores and alerts): Homefield TV straight on that channel, full screen with its info card, whether it was running or
 * not, from the channels kept on the TV (nothing read again from the provider first).
 *
 * <p>Started afresh (TvActivity: the fast path), only what playing needs is made: Home is not built, What's new, the
 * update checks and the playlists' refresh wait until the picture is up (or for the next ordinary start), and Home stays
 * out of sight until the channel is found, the moment the channels are read. Running with the picture up, the channel
 * changes in place (LiveScreen.switchTo: no screen made again); otherwise it plays as from the guide (TvActivity.watch).
 * A locked channel asks for its PIN first, as from anywhere in the app.
 *
 * <p>Found by its key, else by its names as ChannelMatch reads them (at once on the main thread when every channel's name
 * has been read and one reads the same; else off it, every reading tried), else its number. One row per channel: the row
 * is played, its best version (or the one chosen in its Quality). Nothing found, or channels that aren't in within
 * WAIT_MS: the TV guide (Live TV) with a note, rather than nothing.
 */
final class PlayChannel {
    private PlayChannel() {}

    static final long WAIT_MS = 30_000L;
    /** The playlists and the guide not read again for this long while a channel from the launcher starts. */
    static final long QUIET_MS = 20_000L;

    /** The request being waited for (one at a time: a newer one takes its place). */
    private static Pending pending;

    /** The request in an intent, or null (another intent, an empty or stale request). Nothing taken from it. */
    static PlayRequest request(Intent i) {
        if (i == null || !PlayRequest.ACTION.equals(i.getAction())) return null;
        return PlayRequest.of(i.getAction(), i.getStringExtra(PlayRequest.EXTRA_KEY), i.getStringExtra(PlayRequest.EXTRA_NAME),
                names(i.getExtras()), number(i.getExtras()), i.getLongExtra(PlayRequest.EXTRA_AT, 0L), System.currentTimeMillis());
    }

    /** Whether the intent asks for a channel (TvActivity's fast path), without taking it. */
    static boolean asks(Intent i) {
        return request(i) != null;
    }

    /**
     * An intent for TvActivity (onCreate: starting true; onNewIntent: false): true when it asks for a channel, which is
     * then played (now, or once the channels are in). The intent is taken once: given again (the activity made again
     * for a new theme) it opens as usual.
     */
    static boolean handle(TvActivity tv, Intent i, boolean starting) {
        if (i == null || !PlayRequest.ACTION.equals(i.getAction())) return false;
        PlayRequest r = request(i);
        i.setAction(Intent.ACTION_MAIN);
        i.removeExtra(PlayRequest.EXTRA_KEY);
        i.removeExtra(PlayRequest.EXTRA_NAME);
        i.removeExtra(PlayRequest.EXTRA_NAMES);
        i.removeExtra(PlayRequest.EXTRA_NUMBER);
        i.removeExtra(PlayRequest.EXTRA_AT);
        if (r == null) return false;
        // A newer request in place of one still waiting: Home kept out of sight if it was.
        boolean hidden = pending != null && pending.starting;
        if (pending != null) pending.finish();
        pending = new Pending(tv, r, starting || hidden);
        pending.start();
        return true;
    }

    /** The number however it was given (--ei, or as words). */
    static int number(Bundle b) {
        Object o = b == null ? null : b.get(PlayRequest.EXTRA_NUMBER);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** The names however they were given (a String array, a list, or one line of them separated by commas from adb's --esa). */
    static String[] names(Bundle b) {
        Object o = b == null ? null : b.get(PlayRequest.EXTRA_NAMES);
        if (o instanceof String[]) return (String[]) o;
        if (o instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object x : (List<?>) o) if (x != null) out.add(x.toString());
            return out.toArray(new String[0]);
        }
        if (o instanceof String) return ((String) o).split(",");
        return null;
    }

    /** Library's lookups as the request finds a channel: rows only (one row per channel), listed ones by name. */
    static PlayRequest.Finder finder(Library lib) {
        return new PlayRequest.Finder() {
            @Override public Channel byKey(String key) {
                return lib.byKey(key);
            }

            @Override public List<Channel> named(String name) {
                // Only the names with its word in them read, unless every name has been (no index made for it).
                return rows(lib.sameName(name));
            }

            @Override public List<Channel> withKey(String key) {
                return rows(lib.withKey(key));
            }

            @Override public Collection<String> keys() {
                return lib.nameKeys();
            }

            @Override public Channel numbered(int number) {
                return lib.numbered(number);
            }

            /** The listed ones' rows, once each, in the lists' order. */
            private List<Channel> rows(List<Channel> all) {
                List<Channel> out = new ArrayList<>();
                for (Channel c : all) {
                    if (!lib.listed(c)) continue;
                    Channel row = lib.row(c);
                    if (row != null && !out.contains(row)) out.add(row);
                }
                return out;
            }
        };
    }

    /**
     * One request: found by its key, or by a name read the same, at once as soon as the channels are in (the names read
     * already); else off the main thread, every reading of every name tried, then its number.
     */
    private static final class Pending implements Runnable {
        final TvActivity tv;
        final PlayRequest r;
        final boolean starting;
        final Library lib;
        final long until;
        boolean asking, done;
        private final Runnable giveUp = () -> gaveUp(null);

        Pending(TvActivity tv, PlayRequest r, boolean starting) {
            this.tv = tv;
            this.r = r;
            this.starting = starting;
            this.lib = Library.get(tv);
            this.until = System.currentTimeMillis() + WAIT_MS;
        }

        void start() {
            // Started afresh: Home stays out of sight while the channels are read (a moment from their copy on the TV).
            if (starting) tv.screens.setVisibility(View.INVISIBLE);
            // The picture first: the playlists and the guide read again from their providers once it has started.
            lib.holdRefresh(QUIET_MS);
            lib.addListener(this);
            Exec.main.postDelayed(giveUp, WAIT_MS);
            run();
        }

        /** The channels changed (or now): the channel looked for. */
        @Override public void run() {
            if (done) return;
            if (tv.isFinishing() || tv.isDestroyed()) {
                finish();
                return;
            }
            if (!lib.ready()) {
                if (System.currentTimeMillis() > until) gaveUp(null);
                return;
            }
            PlayRequest.Finder f = finder(lib);
            // At once: by its key, or (every name read already) a name that reads the same.
            Channel c = lib.namesRead() ? r.exact(f) : r.byKey(f);
            if (c != null) {
                play(c);
                return;
            }
            if (asking) return;
            asking = true;
            // Every reading of every name, then its number: off the main thread, on the quick work's own thread (never
            // queued behind a download, as the threads that read playlists and pictures may be).
            Exec.work(() -> {
                Channel found = r.find(f);
                Exec.main(() -> {
                    if (done) return;
                    if (found != null) play(found);
                    else gaveUp(r.said() + " isn't in your playlists");
                });
            });
        }

        private void play(Channel c) {
            finish();
            tv.screens.setVisibility(View.VISIBLE);
            // Running with the picture up: the channel changed in place, no screen made again (after its PIN when locked).
            Screen top = tv.top();
            if (!starting && top instanceof LiveScreen && ((LiveScreen) top).live()) {
                ((LiveScreen) top).switchTo(c);
                return;
            }
            // A locked channel: its PIN's pad over Home, as from anywhere in the app (never past it); Home made for it then.
            // Else a Home that was made is kept out from under the picture as it comes in.
            HomeScreen home = tv.homeScreen();
            boolean pin = com.gathin.homefieldtv.prefs.Parental.blocks(tv, c);
            if (pin && tv.top() == home) tv.homeView();
            else if (starting && home.built()) home.view().setVisibility(View.GONE);
            tv.watch(c);
        }

        /** Not found (or the channels not in): Live TV's guide rather than nothing, with a note. */
        private void gaveUp(String note) {
            if (done) return;
            finish();
            tv.screens.setVisibility(View.VISIBLE);
            if (tv.isFinishing() || tv.isDestroyed()) return;
            android.widget.Toast.makeText(tv, (note != null ? note : "Couldn't open " + r.said() + ": the channels aren't in yet")
                    + ". Here's the guide.", android.widget.Toast.LENGTH_LONG).show();
            tv.toGuide();
        }

        void finish() {
            done = true;
            lib.removeListener(this);
            Exec.main.removeCallbacks(giveUp);
            if (pending == this) pending = null;
        }
    }
}
