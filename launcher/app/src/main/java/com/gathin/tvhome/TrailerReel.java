package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Trailer reel (the theme's "trailer_reel", on unless set false): trailers back to back, full screen and with sound,
 * of what is new on this TV's own streaming apps (TMDB's UK providers for the apps installed: films out in the last
 * five months, series with episodes in the last month), with filters along the top: All new, Films, Series, Coming
 * soon (films still to come out) and My list (the ones on the apps). Opened from Discover's and Movies' Trailers tab
 * (OK on a poster starts there). As the mock-up draws it: the title's section ("NEW ON DISNEY+ · SERIES"), logo, line
 * and story at the bottom left, OK Add to My list, Down Watch now (in the app on this TV that streams it, or the store
 * that sells it; never anything else), Next trailer; the trailer's time along the foot, the next three on the right,
 * "3 of 20" at the top. Left and Right previous and next, Up the filters, Back closes. A title with no trailer that
 * plays is passed over. Played in Home's own YouTube player (TilePlayer), as the highlights are.
 */
final class TrailerReel implements TilePlayer.Film {
    static final int ALL = 0, FILMS = 1, SERIES = 2, SOON = 3, LIST = 4;
    private static final String[] FILTERS = {"All new", "Films", "Series", "Coming soon", "My list"};
    private static final int PINK = 0xFFFF4FA3, BLUE = 0xFF8FB2FF, SOFT = 0xFFC9CCE4, INK = 0xFF10123A;
    /** YouTube writes the video's title over its first seconds: the picture stays over it until then. */
    private static final long FIRST_REVEAL_MS = 4_200L;
    private static final long LIST_MS = 30 * 60_000L;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Map<Integer, Object[]> LISTS = new HashMap<>();
    private static TrailerReel shown;

    /** One title in the reel, with what is found out about it as it comes up. */
    private static final class Item {
        PagesV2.Title t;
        /** Its trailers (comma-separated, best first), null until looked up; the one playing. */
        String ids;
        int tried;
        JSONObject details;
        /** Where it streams on this TV ({app, provider, name}), null for none. */
        String[] where;
        boolean looked, soon;
    }

    static boolean on(Context c) {
        return Config.themeFlag(c, "trailer_reel", true);
    }

    // ---- the lists ------------------------------------------------------------------------------------------------

    /** A filter's titles (off the main thread), kept half an hour: the tab's posters and the reel the same list. */
    static List<PagesV2.Title> titles(HomeV2 h, int filter) {
        synchronized (LISTS) {
            Object[] had = LISTS.get(filter);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < LIST_MS) {
                @SuppressWarnings("unchecked") List<PagesV2.Title> l = (List<PagesV2.Title>) had[1];
                return new ArrayList<>(l);
            }
        }
        List<PagesV2.Title> out = read(h, filter);
        if (!out.isEmpty()) synchronized (LISTS) { LISTS.put(filter, new Object[]{System.currentTimeMillis(), out}); }
        return new ArrayList<>(out);
    }

    private static List<PagesV2.Title> read(HomeV2 h, int filter) {
        List<PagesV2.Title> out = new ArrayList<>();
        String key = h.tmdbKey;
        if (key == null || key.isEmpty()) return out;
        String providers = ReleaseAlerts.providers(h.a);
        LocalDate today = LocalDate.now();
        String on = "watch_region=GB&with_watch_monetization_types=flatrate&with_watch_providers=" + providers;
        List<PagesV2.Title> got;
        switch (filter) {
            case FILMS:
                if (providers.isEmpty()) return out;
                got = PagesV2.tmdbRow(key, "discover/movie?" + on + "&release_date.gte=" + today.minusDays(150)
                        + "&release_date.lte=" + today + "&sort_by=popularity.desc&vote_count.gte=20", false);
                break;
            case SERIES:
                if (providers.isEmpty()) return out;
                got = PagesV2.tmdbRow(key, "discover/tv?" + on + "&air_date.gte=" + today.minusDays(30) + "&air_date.lte=" + today
                        + "&sort_by=popularity.desc&without_genres=10763,10764,10767&with_original_language=en", true);
                break;
            case SOON:
                got = PagesV2.tmdbRow(key, "discover/movie?region=GB&with_release_type=2|3|4&primary_release_date.gte="
                        + today.plusDays(1) + "&primary_release_date.lte=" + today.plusDays(120) + "&sort_by=popularity.desc", false);
                break;
            case LIST:
                got = new ArrayList<>();
                for (PagesV2.Title t : MyList.titles(h.a)) {
                    if (t.id > 0 && HomeV2.onYourApps(h, PagesV2.providersGb(key, t.id, t.tv)) != null) got.add(t);
                }
                break;
            default:
                got = PagesV2.mix(titles(h, FILMS), titles(h, SERIES));
                break;
        }
        for (PagesV2.Title t : got) {
            if (out.size() >= 20) break;
            if (t.id > 0) out.add(t);
        }
        return out;
    }

    /** The Trailers tab: the filter's posters, each marked TRAILER, OK playing the reel from it. */
    static PagesV2.Row row(HomeV2 h, int filter) {
        List<PagesV2.Title> items = new ArrayList<>();
        if (on(h.a)) {
            List<PagesV2.Title> list = titles(h, filter);
            for (int i = 0; i < list.size(); i++) {
                PagesV2.Title t = copy(list.get(i));
                t.badge = "▶  TRAILER";
                t.badgeBg = PINK;
                t.badgeFg = Color.WHITE;
                int at = i;
                t.ok = () -> open(h, filter, at);
                items.add(t);
            }
        }
        PagesV2.Row r = PagesV2.row(null, null, false, items);
        r.empty = "No new trailers on this TV's apps just now.";
        return r;
    }

    private static PagesV2.Title copy(PagesV2.Title s) {
        PagesV2.Title t = new PagesV2.Title();
        t.title = s.title;
        t.poster = s.poster;
        t.backdrop = s.backdrop;
        t.year = s.year;
        t.overview = s.overview;
        t.what = s.what;
        t.vote = s.vote;
        t.id = s.id;
        t.tv = s.tv;
        t.youtube = s.youtube;
        return t;
    }

    // ---- open and close -------------------------------------------------------------------------------------------

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        return shown != null && shown.onKey(e);
    }

    static void close() {
        if (shown != null) shown.hide(true);
    }

    /** The reel on a filter, from its at'th title. */
    static void open(HomeV2 h, int filter, int at) {
        close();
        TrailerReel r = new TrailerReel(h);
        shown = r;
        r.build();
        r.load(filter, at);
    }

    private final HomeV2 h;
    private final Context c;
    private final Typeface heavy, semi, regular;
    private FrameLayout overlay, frame;
    private ImageView cover, logo;
    private TextView section, title, meta, story, counter, now, length, note;
    private LinearLayout info, filterRow, upNext;
    private final List<TextView> chips = new ArrayList<>();
    private LinearLayout listPill, watchPill, nextPill;
    private View progress;
    private final List<Item> items = new ArrayList<>();
    private int filter, at, chip, dir = 1, skipped, generation;
    private boolean onFilters, revealed, everPlayed, closing;
    private final Runnable reveal = this::reveal;

    private TrailerReel(HomeV2 h) {
        this.h = h;
        c = h.a;
        heavy = Fonts.get(c, R.font.outfit_extrabold);
        semi = Fonts.get(c, R.font.inter_semibold);
        regular = Fonts.get(c, R.font.inter_regular);
    }

    private void hide(boolean fade) {
        if (shown == this) shown = null;
        if (closing) return;
        closing = true;
        generation++;
        h.main.removeCallbacks(reveal);
        h.players.filmOff();
        FrameLayout o = overlay;
        if (o == null) return;
        overlay = null;
        if (fade) o.animate().alpha(0f).setDuration(200).withEndAction(() -> remove(o)).start();
        else remove(o);
        h.paint();
    }

    private void remove(FrameLayout o) {
        if (o.getParent() instanceof ViewGroup) ((ViewGroup) o.getParent()).removeView(o);
    }

    // ---- building -------------------------------------------------------------------------------------------------

    private void build() {
        overlay = new FrameLayout(c);
        overlay.setBackgroundColor(0xFF05060F);
        overlay.setClickable(true);
        overlay.setKeepScreenOn(true);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        // The video, full screen; the title's picture over it until it is really playing.
        frame = new FrameLayout(c);
        frame.setBackgroundColor(Color.BLACK);
        overlay.addView(frame, new FrameLayout.LayoutParams(h.px(1920), h.px(1080)));
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        overlay.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        // The shades the words sit on: down the left, along the foot, across the top. Drawn once, never moving.
        overlay.addView(shade(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xD1040510, 0x73040510, 0x00040510}), at(0, 0, 1190, 1080));
        overlay.addView(shade(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00040510, 0xE0040510, 0xFF04050F}), at(0, 560, 1920, 520));
        overlay.addView(shade(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB3040510, 0x00040510}), at(0, 0, 1920, 140));

        // The top: TRAILER REEL and the filters; the count, the sound and the time on the right.
        filterRow = new LinearLayout(c);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = h.text(16, heavy, Color.WHITE);
        badge.setText("▶  TRAILER REEL");
        badge.setLetterSpacing(0.12f);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(h.px(18), 0, h.px(18), 0);
        badge.setBackground(h.fill(22, PINK));
        filterRow.addView(badge, new LinearLayout.LayoutParams(-2, h.px(44)));
        for (String f : FILTERS) {
            TextView chipView = h.text(18, regular, 0xC7FFFFFF);
            chipView.setText(f);
            chipView.setGravity(Gravity.CENTER);
            chipView.setPadding(h.px(18), 0, h.px(18), 0);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, h.px(44));
            cl.leftMargin = h.px(12);
            filterRow.addView(chipView, cl);
            chips.add(chipView);
        }
        overlay.addView(filterRow, at(56, 34, 1300, 44));
        LinearLayout right = new LinearLayout(c);
        right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        counter = h.text(18, semi, 0xFFE9EBF8);
        right.addView(counter);
        View sound = new Speaker(c);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(h.px(44), h.px(44));
        sl.leftMargin = h.px(16);
        right.addView(sound, sl);
        android.widget.TextClock clock = new android.widget.TextClock(c);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(44));
        clock.setTypeface(h.regular);
        clock.setTextColor(Color.WHITE);
        clock.setIncludeFontPadding(false);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
        kl.leftMargin = h.px(16);
        right.addView(clock, kl);
        overlay.addView(right, at(1264, 34, 600, 48));

        // The title, at the bottom left.
        info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        section = h.text(15, semi, BLUE);
        section.setLetterSpacing(0.2f);
        info.addView(section);
        FrameLayout name = new FrameLayout(c);
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setAdjustViewBounds(true);
        logo.setMaxWidth(h.px(480));
        logo.setMaxHeight(h.px(110));
        name.addView(logo, new FrameLayout.LayoutParams(-2, h.px(110), Gravity.START | Gravity.CENTER_VERTICAL));
        title = h.text(54, heavy, Color.WHITE);
        title.setMaxLines(2);
        name.addView(title, new FrameLayout.LayoutParams(h.px(1000), -2, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, h.px(110));
        nl.topMargin = h.px(16);
        info.addView(name, nl);
        meta = h.text(21, regular, 0xDBFFFFFF);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.topMargin = h.px(20);
        info.addView(meta, ml);
        story = h.text(22, regular, 0xF0FFFFFF);
        story.setMaxLines(2);
        story.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(h.px(900), -2);
        stl.topMargin = h.px(10);
        info.addView(story, stl);
        LinearLayout pills = new LinearLayout(c);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        listPill = pill(pills, "OK", "Add to My list", true);
        watchPill = pill(pills, "▼", "Watch now", false);
        nextPill = pill(pills, null, "Next trailer  ▶", false);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, h.px(58));
        pl.topMargin = h.px(26);
        info.addView(pills, pl);
        note = h.text(16, regular, SOFT);
        LinearLayout.LayoutParams ntl = new LinearLayout.LayoutParams(-2, -2);
        ntl.topMargin = h.px(10);
        info.addView(note, ntl);
        overlay.addView(info, at(72, 590, 1300, 420));

        // Up next, on the right.
        upNext = new LinearLayout(c);
        upNext.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(upNext, at(1500, 300, 364, 720));

        // The trailer's time along the foot.
        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        now = h.text(16, semi, 0xFFE9EBF8);
        now.setText("0:00");
        foot.addView(now);
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(h.fill(3, 0x38FFFFFF));
        progress = new View(c);
        progress.setBackground(h.fill(3, Color.WHITE));
        progress.setPivotX(0);
        progress.setScaleX(0f);
        bar.addView(progress, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, h.px(5), 1f);
        bl.leftMargin = bl.rightMargin = h.px(18);
        foot.addView(bar, bl);
        length = h.text(16, regular, SOFT);
        length.setText("");
        foot.addView(length);
        TextView hint = h.text(15, regular, SOFT);
        hint.setText("◀ ▶ trailers  ·  ▲ filters  ·  Back Home");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.leftMargin = h.px(28);
        foot.addView(hint, hl);
        overlay.addView(foot, at(72, 1000, 1792, 30));
        paintChips();
    }

    private FrameLayout.LayoutParams at(int x, int y, int w, int hh) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hh));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        return p;
    }

    private View shade(GradientDrawable.Orientation o, int[] colours) {
        View v = new View(c);
        v.setBackground(new GradientDrawable(o, colours));
        return v;
    }

    /** One of the three pills: the key's mark (OK, ▼) on a disc, then the words. */
    private LinearLayout pill(LinearLayout into, String mark, String words, boolean focus) {
        LinearLayout p = new LinearLayout(c);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(h.px(mark == null ? 26 : 12), 0, h.px(26), 0);
        if (mark != null) {
            TextView m = h.text(13, heavy, INK);
            m.setText(mark);
            m.setGravity(Gravity.CENTER);
            m.setBackground(h.fill(6, Color.WHITE));
            m.setPadding(h.px(7), h.px(3), h.px(7), h.px(3));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
            ml.rightMargin = h.px(12);
            p.addView(m, ml);
        }
        TextView w = h.text(21, focus ? semi : regular, Color.WHITE);
        w.setText(words);
        p.addView(w);
        p.setTag(w);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(58));
        lp.rightMargin = h.px(16);
        into.addView(p, lp);
        return p;
    }

    private void paintPills() {
        // OK's pill as focused (the remote's OK acts on it); Watch now white, as its own key's; Next plain glass.
        GradientDrawable a = h.fill(29, 0x3DFFFFFF);
        a.setStroke(h.px(2.5f), Color.WHITE);
        listPill.setBackground(a);
        watchPill.setBackground(h.fill(29, Color.WHITE));
        ((TextView) watchPill.getTag()).setTextColor(INK);
        View disc = watchPill.getChildAt(0);
        if (disc instanceof TextView) { disc.setBackground(h.fill(6, INK)); ((TextView) disc).setTextColor(Color.WHITE); }
        GradientDrawable n = h.fill(29, 0x1FFFFFFF);
        n.setStroke(h.px(1.5f), 0x4DFFFFFF);
        nextPill.setBackground(n);
    }

    private void paintChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView v = chips.get(i);
            boolean current = i == filter, focus = onFilters && i == chip;
            GradientDrawable g = h.fill(22, focus ? Color.WHITE : current ? 0x33FFFFFF : 0);
            if (current && !focus) g.setStroke(h.px(1.5f), 0x99FFFFFF);
            v.setBackground(g);
            v.setTextColor(focus ? INK : current ? Color.WHITE : 0xC7FFFFFF);
            v.setTypeface(current || focus ? semi : regular);
        }
    }

    // ---- the titles -----------------------------------------------------------------------------------------------

    private void load(int f, int from) {
        filter = f;
        chip = f;
        paintChips();
        int gen = ++generation;
        h.players.filmOff();
        items.clear();
        upNext.removeAllViews();
        showWords("Finding trailers…");
        IO.execute(() -> {
            List<PagesV2.Title> list = titles(h, f);
            h.main.post(() -> {
                if (gen != generation || closing) return;
                for (PagesV2.Title t : list) {
                    Item it = new Item();
                    it.t = t;
                    it.soon = f == SOON;
                    items.add(it);
                }
                if (items.isEmpty()) {
                    showWords(f == LIST ? "Nothing on My list is on this TV's apps yet" : "No trailers to show here just now");
                    return;
                }
                at = Math.max(0, Math.min(from, items.size() - 1));
                dir = 1;
                skipped = 0;
                play();
            });
        });
    }

    /** Nothing to play: the words alone where the title would be. */
    private void showWords(String words) {
        section.setText("");
        logo.setImageDrawable(null);
        title.setText(words);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(34));
        meta.setText("");
        story.setText("");
        note.setText("");
        counter.setText("");
        cover.setImageDrawable(null);
        cover.setAlpha(1f);
        cover.setVisibility(View.VISIBLE);
        paintPills();
    }

    /** The title at 'at': its words and picture now, its trailer once looked up. */
    private void play() {
        if (closing || items.isEmpty()) return;
        int gen = ++generation;
        Item it = items.get(at);
        h.main.removeCallbacks(reveal);
        revealed = false;
        everPlayed = false;
        h.players.filmOff();
        progress.setScaleX(0f);
        now.setText("0:00");
        length.setText("");
        cover.animate().cancel();
        cover.setVisibility(View.VISIBLE);
        cover.setAlpha(1f);
        h.image(cover, it.t.backdrop, h.px(1280));
        words(it);
        upNext();
        info.setAlpha(0f);
        info.animate().alpha(1f).setDuration(220).start();
        IO.execute(() -> {
            look(it);
            // The next two looked up meanwhile, so they start at once.
            for (int n = at + 1; n < Math.min(items.size(), at + 3); n++) look(items.get(n));
            h.main.post(() -> {
                if (gen != generation || closing) return;
                words(it);
                String id = nextId(it);
                if (id == null) { skip(); return; }
                skipped = 0;
                h.players.film(frame, id, this);
            });
        });
    }

    /** Off the main thread: the title's details (its length, episodes, where it streams) and its trailers. */
    private void look(Item it) {
        if (it.looked) return;
        String key = h.tmdbKey;
        try {
            it.details = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (it.t.tv ? "tv/" : "movie/") + it.t.id
                    + "?language=en-GB&append_to_response=watch/providers&api_key=" + key));
            it.where = FilmNight.where(h, YourShows.gb(it.details));
            // A provider with no streaming app (a store only) is not "new on" anything.
            if (it.where != null && it.where[1] == null && !it.soon) it.where = null;
        } catch (Exception ignored) {
            // the words from the list alone
        }
        it.ids = it.t.youtube != null ? it.t.youtube : PagesV2.trailer(key, it.t);
        it.t.youtube = it.ids;
        String back = it.t.backdrop;
        if (back != null) h.prefetch(back, h.px(1280));
        h.prefetchLogo(it.t.id, it.t.tv, 480);
        it.looked = true;
    }

    /** The trailer to try next for a title, or null when none is left. */
    private static String nextId(Item it) {
        if (it.ids == null || it.ids.isEmpty()) return null;
        String[] all = it.ids.split(",");
        return it.tried < all.length ? all[it.tried] : null;
    }

    /** A title with no trailer that plays: on in the same direction (all of them tried: say so). */
    private void skip() {
        if (++skipped >= items.size()) { showWords("None of these trailers will play here"); return; }
        at = (at + dir + items.size()) % items.size();
        play();
    }

    private void words(Item it) {
        PagesV2.Title t = it.t;
        JSONObject d = it.details;
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(54));
        String sec;
        if (it.soon) {
            String rel = d == null ? "" : d.optString("release_date", "");
            String out = "";
            try { out = " · OUT " + LocalDate.parse(rel).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK); }
            catch (Exception ignored) { /* no date */ }
            sec = "COMING SOON · FILM" + out;
        } else {
            String svc = it.where == null ? null : service(it.where[1], it.where[2]);
            sec = (filter == LIST ? "ON YOUR LIST" : "NEW") + (svc != null ? " ON " + svc.toUpperCase(Locale.UK) : "") + " · " + (t.tv ? "SERIES" : "FILM");
        }
        section.setText(sec);
        logo.setImageDrawable(null);
        title.setText(t.title);
        title.setVisibility(View.VISIBLE);
        int gen = generation;
        IO.execute(() -> {
            String url = h.tmdbLogo(t.id, t.tv);
            h.main.post(() -> {
                if (gen != generation || closing || url == null) return;
                h.image(logo, url, h.px(960), () -> { if (logo.getDrawable() != null) title.setVisibility(View.INVISIBLE); });
            });
        });
        List<String> bits = new ArrayList<>();
        if (d != null && t.tv && d.optInt("number_of_episodes") > 0) bits.add(d.optInt("number_of_episodes") + " episodes");
        if (d != null && !t.tv && d.optInt("runtime") > 0) bits.add(d.optInt("runtime") / 60 + "h " + d.optInt("runtime") % 60 + "m");
        if (t.year != null && !t.year.isEmpty()) bits.add(t.year);
        if (t.what != null && !t.what.isEmpty()) bits.add(t.what);
        if (t.vote > 0) bits.add(String.format(Locale.UK, "★ %.1f", t.vote));
        meta.setText(String.join("  ·  ", bits));
        story.setText(t.overview == null ? "" : t.overview);
        counter.setText((at + 1) + " of " + items.size());
        listWords(it);
        note.setText(it.soon ? "Not out yet: Watch now is ready the day it reaches your apps" : "");
        paintPills();
    }

    private void listWords(Item it) {
        boolean listed = MyList.has(c, it.t.id, it.t.tv);
        ((TextView) listPill.getTag()).setText(listed ? "✓  On My list" : "Add to My list");
    }

    /** The next three along, on the right. */
    private void upNext() {
        upNext.removeAllViews();
        TextView head = h.text(15, semi, 0xFFD9DCF2);
        head.setText("UP NEXT");
        head.setLetterSpacing(0.18f);
        upNext.addView(head);
        for (int n = 1; n <= 3 && at + n < items.size(); n++) {
            Item it = items.get(at + n);
            FrameLayout card = new FrameLayout(c);
            card.setClipToOutline(true);
            card.setOutlineProvider(h.round(12));
            card.setBackground(h.fill(12, 0x22FFFFFF));
            ImageView pic = new ImageView(c);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.image(pic, it.t.backdrop != null ? it.t.backdrop.replace("/w1280/", "/w780/") : it.t.poster, h.px(400));
            card.addView(pic, new FrameLayout.LayoutParams(-1, -1));
            card.addView(shade(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xD9000000}), new FrameLayout.LayoutParams(-1, -1));
            LinearLayout w = new LinearLayout(c);
            w.setOrientation(LinearLayout.VERTICAL);
            TextView nm = h.text(19, semi, Color.WHITE);
            nm.setText(it.t.title);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            w.addView(nm);
            TextView ln = h.text(14, regular, 0xFFDFE2F2);
            List<String> bits = new ArrayList<>();
            bits.add(it.t.tv ? "Series" : "Film");
            if (it.t.year != null && !it.t.year.isEmpty()) bits.add(it.t.year);
            if (it.t.vote > 0) bits.add(String.format(Locale.UK, "★ %.1f", it.t.vote));
            ln.setText(String.join(" · ", bits));
            w.addView(ln);
            FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            wl.leftMargin = wl.rightMargin = h.px(14);
            wl.bottomMargin = h.px(12);
            card.addView(w, wl);
            if (n == 1) {
                GradientDrawable ring = new GradientDrawable();
                ring.setCornerRadius(h.px(12));
                ring.setStroke(h.px(2), 0xB3FFFFFF);
                card.setForeground(ring);
            }
            card.setAlpha(n == 1 ? 1f : n == 2 ? 0.8f : 0.6f);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(h.px(364), h.px(205));
            cl.topMargin = h.px(18);
            upNext.addView(card, cl);
        }
    }

    /** TMDB's provider as people say it ("Disney+", not "Disney Plus"). */
    private static String service(String provider, String name) {
        if (provider == null) return name;
        switch (provider) {
            case "8": return "Netflix";
            case "9": case "119": return "Prime Video";
            case "337": return "Disney+";
            case "350": return "Apple TV";
            case "38": return "BBC iPlayer";
            case "39": return "NOW";
            case "531": case "582": return "Paramount+";
            case "41": case "54": return "ITVX";
            case "103": return "Channel 4";
            case "333": return "My5";
            default: return name;
        }
    }

    // ---- the player's news -----------------------------------------------------------------------------------------

    private void reveal() {
        if (closing || revealed) return;
        revealed = true;
        cover.animate().cancel();
        cover.animate().alpha(0f).setDuration(450).withEndAction(() -> { if (revealed) cover.setVisibility(View.INVISIBLE); }).start();
    }

    @Override public void state(int s) {
        if (closing) return;
        if (s == 1) {
            boolean first = !everPlayed;
            everPlayed = true;
            h.main.removeCallbacks(reveal);
            h.main.postDelayed(reveal, first ? FIRST_REVEAL_MS : 300L);
        } else if (s == 2 || s == 3) {
            // Paused or loading: YouTube draws its own signs over the picture, so the title's picture comes back over it.
            if (s == 2 && revealed) {
                revealed = false;
                cover.setVisibility(View.VISIBLE);
                cover.animate().cancel();
                cover.animate().alpha(1f).setDuration(180).start();
            }
        } else if (s == 0) {
            // Finished: the next one along.
            next(1);
        }
    }

    @Override public void time(double at, double len) {
        if (closing || len <= 0) return;
        progress.setScaleX((float) Math.max(0, Math.min(1, at / len)));
        now.setText(clock(at));
        length.setText(clock(len));
    }

    @Override public void failed(int code) {
        if (closing || items.isEmpty()) return;
        // YouTube will not play this one here: its next trailer, else the next title.
        Item it = items.get(at);
        it.tried++;
        String id = nextId(it);
        if (id != null) h.players.film(frame, id, this);
        else skip();
    }

    @Override public void gone() {
        // Home went into the background, or another player took the page: closed, nothing left behind.
        if (!closing) hide(false);
    }

    private static String clock(double secs) {
        long s = Math.max(0, Math.round(secs));
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    private void next(int by) {
        if (items.isEmpty()) return;
        dir = by >= 0 ? 1 : -1;
        skipped = 0;
        at = (at + by + items.size()) % items.size();
        items.get(at).tried = 0;
        play();
    }

    private boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (onFilters) { onFilters = false; paintChips(); } else hide(true);
            }
            return true;
        }
        if (ok) {
            if (e.getAction() != KeyEvent.ACTION_UP || e.isCanceled()) return true;
            if (onFilters) { choose(); return true; }
            if (items.isEmpty()) return true;
            Item it = items.get(at);
            boolean onApps = it.where != null && it.where[1] != null;
            MyList.toggle(c, it.t, onApps);
            listWords(it);
            return true;
        }
        if (!down) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!onFilters) { onFilters = true; chip = filter; paintChips(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (onFilters) { choose(); return true; }
                watch();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (onFilters) { if (chip > 0) chip--; paintChips(); } else next(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (onFilters) { if (chip < chips.size() - 1) chip++; paintChips(); } else next(1);
                return true;
            default:
                return true;
        }
    }

    /** The filter in focus chosen: its titles, from the first. */
    private void choose() {
        onFilters = false;
        if (chip != filter) load(chip, 0); else paintChips();
    }

    /** Down: the title in the streaming app on this TV that has it (or its store); the reel closes. */
    private void watch() {
        if (items.isEmpty()) return;
        Item it = items.get(at);
        if (it.soon || it.where == null) {
            note.setText(it.soon ? "Not out yet: on My list, a pop-up says when it reaches your apps" : it.t.title + " isn't on this TV's apps");
            return;
        }
        PagesV2.Title t = it.t;
        String app = it.where[0];
        hide(false);
        // On one of the TV's streaming apps: through Debrid Stream as the posters do; else (a store) straight there.
        if (it.where[1] != null) h.openOnYourApps(t, app);
        else h.searchTitle(app, t.title, t.tv, t.id, t.backdrop != null ? t.backdrop : t.poster);
    }

    /** The sound's mark: a speaker and two waves, in white, in a ring. */
    private static final class Speaker extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Speaker(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), hh = getHeight(), k = w / 24f;
            p.setColor(0x73FFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, w / 30f));
            cv.drawCircle(w / 2f, hh / 2f, w / 2f - p.getStrokeWidth(), p);
            p.setColor(Color.WHITE);
            p.setStrokeWidth(1.5f * k);
            path.reset();
            path.moveTo(6 * k, 10 * k);
            path.lineTo(8.5f * k, 10 * k);
            path.lineTo(11.5f * k, 7.5f * k);
            path.lineTo(11.5f * k, 16.5f * k);
            path.lineTo(8.5f * k, 14 * k);
            path.lineTo(6 * k, 14 * k);
            path.close();
            cv.drawPath(path, p);
            cv.drawArc(11 * k, 9 * k, 16 * k, 15 * k, -55, 110, false, p);
            cv.drawArc(11 * k, 6.5f * k, 18.5f * k, 17.5f * k, -55, 110, false, p);
        }
    }
}
