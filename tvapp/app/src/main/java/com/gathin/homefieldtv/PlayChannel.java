package com.gathin.homefieldtv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.PlayRequest;
import com.gathin.homefieldtv.prefs.Parental;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV launcher's "play this channel" (PlayRequest.ACTION, from a card of Homefield TV's on its dock): Homefield TV
 * straight on that channel, full screen with its info card, whether it was running or not. Started afresh, Home is kept
 * out of sight (and Settings' Start on not used) until the channel is found, the moment the channels are read; then it
 * plays as from the guide (TvActivity.watch: after the PIN for a locked channel, Home under the PIN's pad then, as ever).
 * One row per channel: the row is played, its best version (or the one chosen in its Quality). A channel that isn't
 * found by its key, its name or its number, or channels that aren't in within WAIT_MS, leave Home showing with a note.
 */
final class PlayChannel {
    private PlayChannel() {}

    static final long WAIT_MS = 30_000L;

    /** The request being waited for (one at a time: a newer one takes its place). */
    private static Pending pending;

    /**
     * An intent for TvActivity (onCreate: starting true; onNewIntent: false): true when it asks for a channel, which is
     * then played (now, or once the channels are in). The intent is taken once: given again (the activity made again
     * for a new theme) it opens as usual.
     */
    static boolean handle(TvActivity tv, Intent i, boolean starting) {
        if (i == null || !PlayRequest.ACTION.equals(i.getAction())) return false;
        PlayRequest r = PlayRequest.of(i.getAction(), i.getStringExtra(PlayRequest.EXTRA_KEY), i.getStringExtra(PlayRequest.EXTRA_NAME),
                number(i.getExtras()), i.getLongExtra(PlayRequest.EXTRA_AT, 0L), System.currentTimeMillis());
        i.setAction(Intent.ACTION_MAIN);
        i.removeExtra(PlayRequest.EXTRA_KEY);
        i.removeExtra(PlayRequest.EXTRA_NAME);
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

    /** Library's lookups as the request finds a channel: rows only (one row per channel), listed ones by name. */
    static PlayRequest.Finder finder(Library lib) {
        return new PlayRequest.Finder() {
            @Override public Channel byKey(String key) {
                return lib.byKey(key);
            }

            @Override public List<Channel> named(String name) {
                List<Channel> out = new ArrayList<>();
                for (Channel c : lib.named(name)) {
                    if (!lib.listed(c)) continue;
                    Channel row = lib.row(c);
                    if (row != null && !out.contains(row)) out.add(row);
                }
                return out;
            }

            @Override public Channel numbered(int number) {
                return lib.numbered(number);
            }
        };
    }

    /** One request: found by its key as soon as the channels are in, else by its name or number off the main thread. */
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
            Channel c = r.byKey(finder(lib));
            if (c != null) {
                play(c);
                return;
            }
            if (asking) return;
            asking = true;
            // By name (every channel's name read the first time) or number: off the main thread.
            Exec.work(() -> {
                Channel f = r.find(finder(lib));
                Exec.main(() -> {
                    if (done) return;
                    if (f != null) play(f);
                    else gaveUp(r.said() + " isn't in your playlists");
                });
            });
        }

        private void play(Channel c) {
            finish();
            // A locked channel: its PIN's pad over Home, as from anywhere in the app (never past it).
            boolean pin = Parental.blocks(tv, c);
            if (starting && !pin) tv.homeScreen().view().setVisibility(View.GONE);
            tv.screens.setVisibility(View.VISIBLE);
            tv.watch(c);
        }

        private void gaveUp(String note) {
            if (done) return;
            finish();
            tv.screens.setVisibility(View.VISIBLE);
            if (tv.isFinishing() || tv.isDestroyed()) return;
            android.widget.Toast.makeText(tv, note != null ? note : "Couldn't open " + r.said() + ": the channels aren't in yet",
                    android.widget.Toast.LENGTH_LONG).show();
        }

        void finish() {
            done = true;
            lib.removeListener(this);
            Exec.main.removeCallbacks(giveUp);
            if (pending == this) pending = null;
        }
    }
}
