package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.CardsSnapshot;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Genres;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Parental;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Makes the cards the TV launcher shows for Homefield TV (CardsSnapshot, served by ChannelCardsProvider): the channels
 * watched last, the favourites and the sport channels, from the lists the screens show (hidden channels left out, and
 * locked ones too: what a locked channel shows never comes up on the launcher's Home), each with its next ten hours or so
 * of programmes, its logo as Homefield TV shows it and that logo's colours where they are known. Made again a moment
 * after the channels, the guide, the favourites or the channels watched change, and every TICK_MS while the app runs;
 * off the main thread, written to a small file (and handed to the provider in memory).
 *
 * <p>The programmes on now and next then get their pictures as Homefield TV finds them (ProgrammePictures: Sky's public
 * UK guide, the same pictures the guide and the info card show), asked for on the main thread as the screens ask, and
 * the cards written again once some have come.
 */
final class ChannelCards {
    private static final long SOON_MS = 1_500L, PICTURES_MS = 2_500L, TICK_MS = 20 * 60_000L;
    private static ChannelCards instance;

    private final Context app;
    /** The cards last made (pictures and all). */
    private volatile CardsSnapshot last;
    private final AtomicBoolean making = new AtomicBoolean(), again = new AtomicBoolean();
    private final Runnable build = this::build, pictures = this::askPictures;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            build();
            Exec.main.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable changed = () -> soon(SOON_MS);
    /** The recent channels, the last one and the favourites as kept: a channel watched or a favourite changed. Held here. */
    private final SharedPreferences.OnSharedPreferenceChangeListener picks = (p, key) -> {
        if ("recent".equals(key) || "favourites".equals(key) || "last".equals(key)) soon(SOON_MS);
    };

    private ChannelCards(Context app) {
        this.app = app;
    }

    /** From TvActivity as it starts (main thread): the cards kept up to date from now on, while the app's process lives. */
    static void start(Context c) {
        Context app = c.getApplicationContext();
        if (instance != null && instance.app == app) {
            instance.soon(SOON_MS);
            return;
        }
        if (instance != null) instance.stop();
        ChannelCards cc = new ChannelCards(app);
        instance = cc;
        Library.get(cc.app).addListener(cc.changed);
        cc.app.getSharedPreferences("live_tv", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(cc.picks);
        Exec.main.postDelayed(cc.tick, SOON_MS);
    }

    /** No longer kept up to date (tests: another application's). */
    private void stop() {
        Library.get(app).removeListener(changed);
        app.getSharedPreferences("live_tv", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(picks);
        Exec.main.removeCallbacks(tick);
        Exec.main.removeCallbacks(build);
        Exec.main.removeCallbacks(pictures);
    }

    private void soon(long ms) {
        Exec.main.removeCallbacks(build);
        Exec.main.postDelayed(build, ms);
    }

    /** Made again off the main thread (once more afterwards when asked meanwhile). */
    private void build() {
        if (!making.compareAndSet(false, true)) {
            again.set(true);
            return;
        }
        Exec.work(() -> {
            try {
                Library lib = Library.get(app);
                // Nothing to make before the channels are in: the cards written before stay meanwhile.
                if (!lib.ready()) return;
                CardsSnapshot prev = last != null ? last : ChannelCardsProvider.kept(app);
                CardsSnapshot s = snapshot(app, lib, prev, System.currentTimeMillis());
                last = s;
                write(s);
                Exec.main(pictures);
            } finally {
                making.set(false);
                if (again.getAndSet(false)) Exec.main(() -> soon(SOON_MS));
            }
        });
    }

    /**
     * The cards at now from the library: the recent channels (newest first), the favourites, and the sport channels with
     * something on now, then those with something soon (SPORT_KEPT at most). Pictures known in prev are kept.
     */
    static CardsSnapshot snapshot(Context c, Library lib, CardsSnapshot prev, long now) {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(now);
        for (Channel ch : lib.recent(null)) put(c, lib, b, ch, prev, CardsSnapshot.RECENT, now);
        for (Channel ch : lib.list(Library.FAVOURITES, "")) put(c, lib, b, ch, prev, CardsSnapshot.FAVOURITE, now);
        List<Channel> sport = lib.genre(Genres.SPORT).channels;
        int kept = 0;
        // On now first, then on within the next hours (they come on while the cards are kept).
        for (int pass = 0; pass < 2 && kept < CardsSnapshot.SPORT_KEPT; pass++) {
            for (Channel ch : sport) {
                if (kept >= CardsSnapshot.SPORT_KEPT) break;
                Guide.Schedule s = lib.schedule(ch.key);
                if (s == null) continue;
                boolean on = s.at(now) >= 0;
                int next = on ? -1 : s.after(now);
                boolean soon = !on && next >= 0 && s.start[next] < now + CardsSnapshot.AHEAD_MS / 2;
                if (pass == 0 ? on : soon) {
                    if (put(c, lib, b, ch, prev, CardsSnapshot.SPORT, now)) kept++;
                }
            }
        }
        return b.build();
    }

    /** One channel into a list (its card made the first time, with its programmes); false when it is left out (locked). */
    private static boolean put(Context c, Library lib, CardsSnapshot.Builder b, Channel ch, CardsSnapshot prev, String section, long now) {
        if (ch == null || Parental.locked(c, ch)) return false;
        String logo = LogoPack.url(ch);
        Logos.Tint t = Logos.tint(logo);
        CardsSnapshot.Card card = b.card(ch.key, ChannelEdits.name(ch), lib.number(ch), logo, t == null ? null : new int[]{t.tileTop, t.tileFoot});
        if (card == null) return false;
        if (card.progs.isEmpty()) {
            Guide.Schedule s = lib.schedule(ch.key);
            for (int i = s == null ? 0 : s.from(now); s != null && i < s.start.length; i++) {
                if (s.stop[i] <= now) continue;
                if (s.start[i] >= now + CardsSnapshot.AHEAD_MS || card.progs.size() >= CardsSnapshot.PROGRAMMES_MAX) break;
                b.prog(card, s.start[i], s.stop[i], s.title[i], s.desc[i], prev == null ? "" : prev.picture(ch.key, s.start[i]));
            }
        }
        b.add(section, ch.key);
        return true;
    }

    /**
     * Main thread: the pictures of what is on now and next on each card, as the screens find them (ProgrammePictures);
     * those already known go in at once, the others are asked for and come in a little later (asked here again then).
     */
    private void askPictures() {
        CardsSnapshot s = last;
        if (s == null) return;
        Library lib = Library.get(app);
        long now = System.currentTimeMillis();
        Map<String, Map<Long, String>> found = new HashMap<>();
        for (CardsSnapshot.Card k : s.channels.values()) {
            Channel ch = lib.byKey(k.key);
            Guide.Schedule sc = ch == null ? null : lib.schedule(ch.key);
            if (sc == null) continue;
            int asked = 0;
            for (CardsSnapshot.Prog p : k.progs) {
                if (p.end <= now) continue;
                if (asked++ >= 2) break;
                if (!p.picture.isEmpty()) continue;
                int i = sc.at(p.start);
                if (i < 0 || sc.start[i] != p.start) continue;
                // Previews and tests: only what is known (nothing is asked of Sky's guide there).
                String u = Exec.inline ? ProgrammePictures.peek(ch, sc, i) : ProgrammePictures.get(app, ch, sc, i, this::picturesCame);
                if (u != null && !u.isEmpty()) {
                    Map<Long, String> mine = found.get(k.key);
                    if (mine == null) found.put(k.key, mine = new HashMap<>());
                    mine.put(p.start, u);
                }
            }
        }
        if (found.isEmpty()) return;
        Exec.work(() -> {
            CardsSnapshot was = last;
            if (was == null) return;
            CardsSnapshot with = was.withPictures(found);
            last = with;
            write(with);
        });
    }

    private void picturesCame() {
        Exec.main.removeCallbacks(pictures);
        Exec.main.postDelayed(pictures, PICTURES_MS);
    }

    /** Written whole to a file beside it, then put in its place (a reader never sees half of it); the provider told. */
    private void write(CardsSnapshot s) {
        File f = ChannelCardsProvider.file(app), tmp = new File(f.getPath() + ".new");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(s.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(f)) return;
        ChannelCardsProvider.put(s, f.lastModified());
    }
}
