package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The SWIPTV style (after SWIPTV on Apple TV): a slim icon rail down the left that opens with names when it has the
 * focus, charcoal pages beside it with one orange accent, big featured posters, a library of categories beside a
 * poster grid for Movies, TV shows and Kids, a hero over Sports, and a guide with the programme's picture in the corner.
 * Everything on it is the launcher's own (its rows, panels and actions, through the SkinHost).
 */
final class SkinSwip extends Skin {
    // ---- look -------------------------------------------------------------------------------------------------------
    static final int BG = 0xFF141519, PANEL = 0xFF1D1E24, PANEL2 = 0xFF26272E, PANEL3 = 0xFF30313A;
    static final int TEXT = 0xFFF5F5F7, TEXT2 = 0xFFA1A1AA, TEXT3 = 0xFF71717A, DARK = 0xFF111111;
    static final int ORANGE = 0xFFFF6B1A, ORANGE2 = 0xFFFF8A3D, RED = 0xFFE5332A;
    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    /** Where the pages start (right of the closed rail), and how wide a row shows. */
    static final int CX = 176, ROOM = 1688;

    // What a cell is: a featured poster, a shortcut, a wide picture, a poster, an app, a headline.
    static final int FEAT = 0, FAV = 1, WIDE = 2, POSTER = 3, APP = 4, TXT = 5;
    static final int[] KW = {270, 330, 400, 210, 300, 400};
    /** The card's height, and the cell's with its caption. */
    static final int[] CARD_H = {405, 96, 225, 315, 168, 225};
    static final int[] KH = {470, 96, 278, 368, 168, 225};
    static final int GAP = 26;

    // The pages, in the rail's order, then the rail's three at its foot.
    static final int HOME = 0, GUIDE = 1, MOVIES = 2, SHOWS = 3, SPORTS = 4, KIDS = 5, APPS = 6, MYLIST = 7;
    static final int R_SEARCH = 8, R_CC = 9, R_STYLE = 10;
    static final String[] RAIL_ICON = {"home", "live", "movies", "shows", "sports", "kids", "apps", "heart", "search", "sliders", "style"};
    static final String[] RAIL_NAME = {"Home", "TV Guide", "Movies", "TV Shows", "Sports", "Kids", "Apps", "My List", "Search",
            "Control Centre", "Launcher style"};

    // Where the focus is.
    static final int Z_RAIL = 0, Z_ROWS = 1, Z_CATS = 2, Z_GRID = 3, Z_GUIDE = 4;

    // How a row captions its cells.
    static final int CAP_NAME = 0, CAP_SUB = 1, CAP_FITS = 2;

    FrameLayout screen, pages;
    private final View[] pageViews = new View[8];
    int page = HOME, zone = Z_ROWS, railAt = 0;
    private Rows home, sports;
    private Library movies, shows, kids;
    private Grid apps, myList;
    private SkinSwipGuide guide;
    private boolean held;
    /** A key pressed since Home came up: until then rows arriving above keep the focus on the first row. */
    private boolean touched;
    /** The rows as last read, by key, and when. */
    final Map<String, List<Item>> data = new HashMap<>();
    private final Map<String, Long> dataAt = new HashMap<>();
    // Home's rows that are filled from more than one read.
    private Row featured, favourites, kidsRow;
    private final Map<String, Row> homeRows = new HashMap<>();
    private final Map<String, Row> sportRows = new HashMap<>();

    SkinSwip(SkinHost host) {
        super(host);
    }

    // ---- the screen -------------------------------------------------------------------------------------------------

    @Override View build() {
        screen = new FrameLayout(c);
        screen.setBackgroundColor(BG);
        screen.setClipChildren(false);
        pages = new FrameLayout(c);
        pages.setClipChildren(false);
        add(screen, pages, CX, 0, 1744, 1080);
        buildHome();
        pageViews[HOME] = home.view;
        pages.addView(home.view, new FrameLayout.LayoutParams(-1, -1));
        buildStatus();
        buildRail();
        for (String k : new String[]{ROW_CONTINUE, ROW_LIVE, ROW_TOP_TV, ROW_DEBRID, ROW_FITS, ROW_YOUR_SHOWS, ROW_SPORT, ROW_BECAUSE,
                ROW_TOP_FILMS, ROW_COMING, ROW_TRAILERS, ROW_KIDS_TV, ROW_KIDS_FILMS, ROW_DOCK}) read(k);
        readTicker();
        paint(false);
        return screen;
    }

    /** A row read off the main thread, kept, and handed to whichever pages show it. */
    private void read(String key) {
        load(key, items -> {
            data.put(key, items);
            dataAt.put(key, System.currentTimeMillis());
            arrived(key);
        });
    }

    private void arrived(String key) {
        List<Item> items = data.get(key);
        Row r = homeRows.get(key);
        if (r != null) {
            if (ROW_BECAUSE.equals(key)) {
                String n = host.rowName(ROW_BECAUSE);
                r.title = n != null ? n : "Because you watched";
                r.titleV.setText(r.title);
            }
            home.set(r, items);
        }
        if (ROW_CONTINUE.equals(key) || ROW_SPORT.equals(key) || ROW_LIVE.equals(key) || ROW_TOP_TV.equals(key) || ROW_TOP_FILMS.equals(key))
            home.set(featured, featuredItems());
        if (ROW_KIDS_TV.equals(key) || ROW_KIDS_FILMS.equals(key)) home.set(kidsRow, kidsItems());
        favouriteNotes();
        Row s = sportRows.get(key);
        if (s != null && sports != null) sports.set(s, ROW_NEWS.equals(key) ? calmOnly(items) : items);
        if (page == HOME) paint(true);
        else if (page == SPORTS && sports != null) paint(true);
    }

    private static List<Item> calmOnly(List<Item> items) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (calm(it.name)) out.add(it);
        return out;
    }

    private List<Item> rowData(String key) {
        List<Item> l = data.get(key);
        return l == null ? new ArrayList<>() : l;
    }

    /** Featured: what is part-watched, tonight's game and what is live, then the week's Top 10 and a film. */
    private List<Item> featuredItems() {
        List<Item> out = new ArrayList<>();
        List<Item> cw = rowData(ROW_CONTINUE), sport = rowData(ROW_SPORT), live = rowData(ROW_LIVE);
        if (cw.size() > 0) out.add(cw.get(0));
        if (sport.size() > 0) out.add(sport.get(0));
        if (cw.size() > 1) out.add(cw.get(1));
        for (Item l : live) {
            // A live programme with a picture of its own (Sky's), the sport first.
            if (l.backdrop == null && l.mark == null) continue;
            out.add(l);
            break;
        }
        int tv = 0;
        for (Item t : rowData(ROW_TOP_TV)) {
            if (contains(out, t)) continue;
            out.add(t);
            if (++tv >= 3) break;
        }
        for (Item t : rowData(ROW_TOP_FILMS)) {
            if (contains(out, t)) continue;
            out.add(t);
            break;
        }
        return out;
    }

    private static boolean contains(List<Item> l, Item t) {
        for (Item o : l) if (o.name != null && o.name.equals(t.name)) return true;
        return false;
    }

    private List<Item> kidsItems() {
        List<Item> out = new ArrayList<>();
        List<Item> tv = rowData(ROW_KIDS_TV), films = rowData(ROW_KIDS_FILMS);
        for (int i = 0; i < 6; i++) {
            if (i < tv.size()) out.add(tv.get(i));
            if (i < films.size()) out.add(films.get(i));
        }
        return out;
    }

    private void buildHome() {
        home = new Rows(120, false);
        featured = home.add("featured", "Featured for you", null, FEAT, CAP_NAME);
        favourites = home.add("favourites", "Your favourites", null, FAV, CAP_NAME);
        homeRows.put(ROW_CONTINUE, home.add(ROW_CONTINUE, "Continue watching", null, WIDE, CAP_SUB));
        homeRows.put(ROW_LIVE, home.add(ROW_LIVE, "On your channels now", "TiviMate", WIDE, CAP_SUB));
        homeRows.put(ROW_TOP_TV, home.add(ROW_TOP_TV, "Top 10 TV shows in the UK", null, POSTER, CAP_NAME));
        homeRows.put(ROW_DEBRID, home.add(ROW_DEBRID, "New on Debrid Stream", null, POSTER, CAP_NAME));
        homeRows.get(ROW_DEBRID).flag = "New";
        homeRows.put(ROW_FITS, home.add(ROW_FITS, "Fits tonight", "ends before bed at " + hm(bedTime()), POSTER, CAP_FITS));
        homeRows.put(ROW_YOUR_SHOWS, home.add(ROW_YOUR_SHOWS, "Your shows", "new episodes", POSTER, CAP_NAME));
        homeRows.put(ROW_SPORT, home.add(ROW_SPORT, "Match Day", "live and coming up on your channels", WIDE, CAP_SUB));
        homeRows.put(ROW_BECAUSE, home.add(ROW_BECAUSE, "Because you watched", null, POSTER, CAP_NAME));
        homeRows.put(ROW_TOP_FILMS, home.add(ROW_TOP_FILMS, "Top 10 films in the UK", null, POSTER, CAP_NAME));
        homeRows.put(ROW_COMING, home.add(ROW_COMING, "Coming soon", null, POSTER, CAP_SUB));
        homeRows.put(ROW_TRAILERS, home.add(ROW_TRAILERS, "Trailers on YouTube", null, WIDE, CAP_SUB));
        kidsRow = home.add("kids", "Kids & family", null, POSTER, CAP_NAME);
        homeRows.put(ROW_DOCK, home.add(ROW_DOCK, "Your apps", null, APP, CAP_NAME));
        home.set(favourites, favouriteItems());
    }

    /** Bedtime today, in ms. */
    private long bedTime() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int bed = host.bedMinutes();
        cal.set(java.util.Calendar.HOUR_OF_DAY, bed / 60);
        cal.set(java.util.Calendar.MINUTE, bed % 60);
        cal.set(java.util.Calendar.SECOND, 0);
        return cal.getTimeInMillis();
    }

    // ---- Your favourites: shortcuts to the pages ------------------------------------------------------------------

    private static final String[][] FAVS = {
            {"Continue Watching", "play", "#FF6B1A"}, {"Live Sports", "sports", "#E5332A"}, {"TV Guide", "live", "#3D6BFF"},
            {"Fits tonight", "movies", "#8B5CF6"}, {"My List", "heart", "#EC4899"}, {"Kids", "kids", "#10B981"}};

    private List<Item> favouriteItems() {
        List<Item> out = new ArrayList<>();
        for (String[] f : FAVS) {
            Item it = new Item();
            it.kind = "shortcut";
            it.name = f[0];
            it.mark = f[1];
            it.channel = f[2];
            it.sub = "";
            out.add(it);
        }
        return out;
    }

    private void favouriteNotes() {
        if (favourites == null || favourites.cells.isEmpty()) return;
        String[] notes = {
                count(ROW_CONTINUE, "title", "titles", "Nothing started yet"),
                count(ROW_SPORT, "game coming up", "games coming up", "Fixtures and tables"),
                count(ROW_LIVE, "channel on now", "channels on now", "What's on now"),
                count(ROW_FITS, "film before bed", "films before bed", "Films that end before bed"),
                "Saved to watch",
                "Kids TV and family films"};
        for (int i = 0; i < Math.min(notes.length, favourites.cells.size()); i++) {
            Cell cl = favourites.cells.get(i);
            if (cl.favNote != null) cl.favNote.setText(notes[i]);
        }
    }

    private String count(String key, String one, String many, String none) {
        List<Item> l = data.get(key);
        if (l == null || l.isEmpty()) return none;
        return l.size() + " " + (l.size() == 1 ? one : many);
    }

    /** OK on a shortcut: its page (Continue Watching: the row on Home). */
    private void shortcut(int which) {
        switch (which) {
            case 0: {
                Row r = homeRows.get(ROW_CONTINUE);
                int at = home.shown().indexOf(r);
                if (at >= 0) { home.ri = at; paint(true); }
                break;
            }
            case 1: open(SPORTS); break;
            case 2: open(GUIDE); break;
            case 3: {
                open(MOVIES);
                movies.pick("fits tonight");
                break;
            }
            case 4: open(MYLIST); break;
            default: open(KIDS); break;
        }
    }

    // ---- the rail ---------------------------------------------------------------------------------------------------

    private FrameLayout rail;
    private View railShade;
    private final List<LinearLayout> railItems = new ArrayList<>();
    private final List<FrameLayout> railIcons = new ArrayList<>();
    private final List<TextView> railLabels = new ArrayList<>();
    private final List<View> railMarks = new ArrayList<>();

    private int railY(int i) {
        return i < R_SEARCH ? 236 + i * 74 : 850 + (i - R_SEARCH) * 72;
    }

    private void buildRail() {
        rail = new FrameLayout(c);
        rail.setClipChildren(false);
        add(screen, rail, 0, 0, 420, 1080);
        railShade = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFA141519, 0xF5141519, 0xD9141519, 0x00141519});
        railShade.setBackground(g);
        railShade.setAlpha(0f);
        add(rail, railShade, 0, 0, 420, 1080);
        // The mark: the launcher's own H, in orange.
        TextView mark = text(26, host.display(), 0xFFFFFFFF);
        mark.setGravity(Gravity.CENTER);
        mark.setText("H");
        GradientDrawable mg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ORANGE2, 0xFFFF5A00});
        mg.setCornerRadius(px(15));
        mark.setBackground(mg);
        mark.setElevation(px(6));
        add(rail, mark, 40, 44, 52, 52);
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(17));
        clock.setTypeface(host.semi());
        clock.setTextColor(TEXT2);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        clock.setIncludeFontPadding(false);
        add(rail, clock, 0, 108, 132, 24);
        for (int i = 0; i < RAIL_NAME.length; i++) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setClipChildren(false);
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(fill(28, 0x00FFFFFF));
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(SkinIcons.get(RAIL_ICON[i], TEXT2, 2f));
            ic.addView(iv, new FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER));
            item.addView(ic, new LinearLayout.LayoutParams(px(56), px(56)));
            TextView lb = text(24, host.semi(), TEXT2);
            lb.setText(RAIL_NAME[i]);
            lb.setAlpha(0f);
            lb.setTranslationX(-px(10));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.leftMargin = px(22);
            item.addView(lb, ll);
            add(rail, item, 38, railY(i), 360, 56);
            View on = new View(c);
            on.setBackground(fill(2, ORANGE));
            on.setVisibility(View.INVISIBLE);
            add(rail, on, 26, railY(i) + 22, 4, 12);
            final int idx = i;
            item.setOnClickListener(v -> { zone = Z_RAIL; railAt = idx; railOk(); });
            railItems.add(item);
            railIcons.add(ic);
            railLabels.add(lb);
            railMarks.add(on);
        }
    }

    private void paintRail(boolean animate) {
        boolean open = zone == Z_RAIL;
        long d = animate ? (host.lite() ? 150 : 300) : 0;
        railShade.animate().alpha(open ? 1f : 0f).setDuration(d).start();
        for (int i = 0; i < railItems.size(); i++) {
            boolean f = open && i == railAt, on = i == page;
            FrameLayout ic = railIcons.get(i);
            ic.setBackground(fill(28, f ? TEXT : on ? 0x24FFFFFF : 0x00FFFFFF));
            ImageView iv = (ImageView) ic.getChildAt(0);
            ((SkinIcons.Icon) iv.getDrawable()).colour(f ? DARK : on ? TEXT : TEXT2);
            ic.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f).setDuration(d).start();
            TextView lb = railLabels.get(i);
            lb.setTextColor(f || on ? TEXT : TEXT2);
            lb.animate().alpha(open ? 1f : 0f).translationX(open ? 0 : -px(10)).setDuration(d).start();
            railMarks.get(i).setVisibility(on && i < R_SEARCH ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void toRail() {
        zone = Z_RAIL;
        railAt = page;
        paint(true);
    }

    private void railOk() {
        switch (railAt) {
            case R_SEARCH: host.open(OPEN_SEARCH); return;
            case R_CC: host.open(OPEN_CONTROL_CENTRE); return;
            case R_STYLE: host.open(OPEN_STYLES); return;
            default: open(railAt);
        }
    }

    // ---- the corner: weather and the ticker ---------------------------------------------------------------------------

    private LinearLayout status;
    private TextView weatherV, tickTag, tickText;
    private List<String[]> ticks = new ArrayList<>();
    private int tickAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!live()) return;
            weatherV.setText(weatherWords());
            if (!ticks.isEmpty()) {
                String[] t = ticks.get(tickAt++ % ticks.size());
                tickTag.setText(t[0]);
                tickText.setText(t[1]);
                tickTag.setVisibility(View.VISIBLE);
            }
            host.main().postDelayed(this, 5000);
        }
    };
    private final Runnable reTicker = this::readTicker;

    /** A headline fit for the family's Home: none about rape, murder, killings and the like. */
    static boolean calm(String words) {
        return words != null && !words.matches("(?is).*\\b(rape|raped|murder\\w*|kill(ed|ing|s)?|dies|died|death|dead|stab\\w*|abuse\\w*|assault\\w*|suicide|shoot\\w*|terror\\w*)\\b.*");
    }

    private String weatherWords() {
        String w = host.weather();
        return w == null ? "" : w.replaceAll("\\s+", " ");
    }

    private void buildStatus() {
        status = new LinearLayout(c);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        ImageView cloud = new ImageView(c);
        cloud.setImageDrawable(SkinIcons.get("cloud", TEXT2, 2f));
        status.addView(cloud, new LinearLayout.LayoutParams(px(26), px(26)));
        weatherV = text(19, host.semi(), TEXT);
        weatherV.setText(weatherWords());
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = px(10);
        status.addView(weatherV, wl);
        tickTag = text(13, host.semi(), 0xFFFFFFFF);
        tickTag.setLetterSpacing(0.06f);
        tickTag.setBackground(fill(5, ORANGE));
        tickTag.setPadding(px(7), px(5), px(7), px(4));
        tickTag.setVisibility(View.GONE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(18);
        status.addView(tickTag, tl);
        tickText = text(19, host.semi(), TEXT2);
        tickText.setMaxWidth(px(560));
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(-2, -2);
        xl.leftMargin = px(10);
        status.addView(tickText, xl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(1200), px(40), Gravity.END | Gravity.TOP);
        lp.topMargin = px(36);
        lp.rightMargin = px(56);
        screen.addView(status, lp);
        host.main().post(tick);
    }

    private void readTicker() {
        host.io().execute(() -> {
            List<String[]> got;
            try {
                got = host.ticker();
            } catch (Exception e) {
                got = new ArrayList<>();
            }
            List<String[]> done = new ArrayList<>();
            if (got != null) for (String[] t : got) if (t != null && t.length > 1 && calm(t[1])) done.add(t);
            host.main().post(() -> {
                if (!live()) return;
                if (!done.isEmpty()) {
                    ticks = done;
                    if (tickTag.getVisibility() != View.VISIBLE) {
                        String[] t = ticks.get(tickAt++ % ticks.size());
                        tickTag.setText(t[0]);
                        tickText.setText(t[1]);
                        tickTag.setVisibility(View.VISIBLE);
                    }
                }
                host.main().removeCallbacks(reTicker);
                host.main().postDelayed(reTicker, 10 * 60_000L);
            });
        });
    }

    // ---- pages --------------------------------------------------------------------------------------------------------

    void open(int p) {
        if (p < 0 || p > MYLIST) return;
        View v = pageViews[p];
        if (v == null) {
            v = buildPage(p);
            pageViews[p] = v;
            pages.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        for (int i = 0; i < pageViews.length; i++) if (pageViews[i] != null) pageViews[i].setVisibility(i == p ? View.VISIBLE : View.GONE);
        if (p != page) {
            v.setAlpha(0f);
            v.setTranslationY(px(14));
            v.animate().alpha(1f).translationY(0).setDuration(host.lite() ? 140 : 260).setInterpolator(EASE).start();
        }
        page = p;
        zone = zoneOf(p);
        if (p == GUIDE) guide.shown();
        paint(true);
    }

    private static int zoneOf(int p) {
        switch (p) {
            case HOME:
            case SPORTS: return Z_ROWS;
            case MOVIES:
            case SHOWS:
            case KIDS: return Z_CATS;
            case GUIDE: return Z_GUIDE;
            default: return Z_GRID;
        }
    }

    private View buildPage(int p) {
        switch (p) {
            case GUIDE:
                guide = new SkinSwipGuide(this);
                return guide.view;
            case MOVIES:
                movies = new Library("movies", "Movies");
                return movies.view;
            case SHOWS:
                shows = new Library("shows", "TV Shows");
                return shows.view;
            case KIDS:
                kids = new Library("kids", "Kids");
                return kids.view;
            case SPORTS:
                buildSports();
                return sports.view;
            case APPS:
                apps = new Grid("Apps", ROW_APPS, APP, 4, 380);
                return apps.view;
            default:
                myList = new Grid("My List", ROW_MY_LIST, POSTER, 6, 210);
                return myList.view;
        }
    }

    private void buildSports() {
        sports = new Rows(640, true);
        sportRows.put(ROW_SPORT, sports.add(ROW_SPORT, "Live and coming up", "on your sports channels", WIDE, CAP_SUB));
        sportRows.put(ROW_LIVE, sports.add(ROW_LIVE, "On your channels now", "TiviMate", WIDE, CAP_SUB));
        sportRows.put(ROW_NEWS, sports.add(ROW_NEWS, "Headlines", "BBC News and BBC Sport", TXT, CAP_NAME));
        for (String k : new String[]{ROW_SPORT, ROW_LIVE}) if (data.get(k) != null) sports.set(sportRows.get(k), data.get(k));
        read(ROW_NEWS);
    }

    // ---- painting ---------------------------------------------------------------------------------------------------

    void paint(boolean animate) {
        paintRail(animate);
        status.setVisibility(page == HOME || page == SPORTS ? View.VISIBLE : View.GONE);
        if (page == HOME) home.paint(animate, zone == Z_ROWS);
        else if (page == SPORTS) sports.paint(animate, zone == Z_ROWS);
        else if (page == MOVIES) movies.paint(animate);
        else if (page == SHOWS) shows.paint(animate);
        else if (page == KIDS) kids.paint(animate);
        else if (page == APPS) apps.paint(animate);
        else if (page == MYLIST) myList.paint(animate);
        else if (page == GUIDE) guide.paint(zone == Z_GUIDE);
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    @Override boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        touched = true;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP;
        if (code == KeyEvent.KEYCODE_MENU) {
            if (up) host.open(OPEN_CONTROL_CENTRE);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (up && !e.isCanceled()) back();
            return true;
        }
        if (isOk(code)) {
            if (down && e.getRepeatCount() == 0) held = false;
            else if (down && e.getRepeatCount() > 0) {
                if (!held) { held = true; hold(); }
            } else if (up) {
                if (held) held = false;
                else if (!e.isCanceled()) ok();
            }
            return true;
        }
        int dx = 0, dy = 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
            default: return false;
        }
        if (down) move(dx, dy);
        return true;
    }

    private void move(int dx, int dy) {
        if (zone == Z_RAIL) {
            if (dy != 0) {
                railAt = Math.max(0, Math.min(RAIL_NAME.length - 1, railAt + dy));
                paint(true);
            } else if (dx > 0) {
                zone = zoneOf(page);
                paint(true);
            }
            return;
        }
        boolean used;
        switch (page) {
            case HOME: used = home.move(dx, dy); break;
            case SPORTS: used = sports.move(dx, dy); break;
            case MOVIES: used = movies.move(dx, dy); break;
            case SHOWS: used = shows.move(dx, dy); break;
            case KIDS: used = kids.move(dx, dy); break;
            case APPS: used = apps.move(dx, dy); break;
            case MYLIST: used = myList.move(dx, dy); break;
            default: used = guide.move(dx, dy); break;
        }
        if (!used && dx < 0) { toRail(); return; }
        paint(true);
    }

    private void ok() {
        if (zone == Z_RAIL) { railOk(); return; }
        Item it;
        switch (page) {
            case HOME:
            case SPORTS: {
                Rows rs = page == HOME ? home : sports;
                Cell cl = rs.focusedCell();
                if (cl == null) return;
                if (rs.focused() == favourites) { shortcut(cl.index); return; }
                host.ok(cl.it);
                return;
            }
            case MOVIES: it = movies.focusedItem(); break;
            case SHOWS: it = shows.focusedItem(); break;
            case KIDS: it = kids.focusedItem(); break;
            case APPS: it = apps.focusedItem(); break;
            case MYLIST: it = myList.focusedItem(); break;
            default: guide.ok(); return;
        }
        if (it != null) host.ok(it);
    }

    private void hold() {
        if (zone == Z_RAIL) return;
        Item it = null;
        switch (page) {
            case HOME:
            case SPORTS: {
                Rows rs = page == HOME ? home : sports;
                Cell cl = rs.focusedCell();
                if (cl != null && rs.focused() != favourites) it = cl.it;
                break;
            }
            case MOVIES: it = movies.focusedItem(); break;
            case SHOWS: it = shows.focusedItem(); break;
            case KIDS: it = kids.focusedItem(); break;
            case APPS: it = apps.focusedItem(); break;
            case MYLIST: it = myList.focusedItem(); break;
            default: break;
        }
        if (it != null) host.details(it);
    }

    private void back() {
        if (zone == Z_RAIL) {
            if (page != HOME) open(HOME);
            else home();
            return;
        }
        if (zone == Z_GRID && (page == MOVIES || page == SHOWS || page == KIDS)) {
            zone = Z_CATS;
            paint(true);
            return;
        }
        toRail();
    }

    @Override void home() {
        touched = false;
        if (page != HOME) open(HOME);
        zone = Z_ROWS;
        railAt = HOME;
        home.ri = 0;
        for (Row r : home.rows) r.idx = 0;
        paint(true);
    }

    @Override void resume() {
        long now = System.currentTimeMillis();
        for (String k : new String[]{ROW_CONTINUE, ROW_LIVE, ROW_SPORT}) {
            Long at = dataAt.get(k);
            if (at == null || now - at > 60_000L) read(k);
        }
        host.main().removeCallbacks(tick);
        host.main().post(tick);
        if (page == GUIDE && guide != null) guide.shown();
    }

    @Override void stop() {
        host.main().removeCallbacks(tick);
    }

    @Override void destroy() {
        super.destroy();
        host.main().removeCallbacks(tick);
        host.main().removeCallbacks(reTicker);
        if (guide != null) guide.destroy();
    }

    // ---- cells ------------------------------------------------------------------------------------------------------

    /** One thing on a row or a grid: its card (scaled when focused) and its caption under it. */
    final class Cell {
        final Item it;
        final int kind, index;
        final FrameLayout box = new FrameLayout(c);
        final FrameLayout card = new FrameLayout(c);
        Pic pic;
        String url;
        int urlW;
        boolean loaded, focused;
        TextView chip, cap, favName, favNote;
        LinearLayout capRow;
        ImageView favIcon;
        ImageView logo;
        String logoUrl;
        int cardW, cardH;

        Cell(Item it, int kind, int index, int w, int h) {
            this.it = it;
            this.kind = kind;
            this.index = index;
            cardW = w;
            cardH = h;
            box.setClipChildren(false);
            card.setClipChildren(false);
            box.addView(card, new FrameLayout.LayoutParams(px(w), px(h)));
            final int r = kind == FEAT ? 18 : kind == FAV ? 16 : 14;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(r));
                }
            });
            card.setPivotX(px(w) / 2f);
            card.setPivotY(px(h) / 2f);
        }

        void load() {
            if (loaded) return;
            loaded = true;
            if (pic != null && url != null) host.image(pic, url, urlW);
            if (logo != null && logoUrl != null) host.image(logo, logoUrl, px(260));
        }

        void drop() {
            if (!loaded) return;
            loaded = false;
            if (pic != null && url != null) pic.setImageBitmap(null);
            if (logo != null && logoUrl != null) logo.setImageDrawable(null);
        }

        void focus(boolean on, boolean animate) {
            if (on == focused && animate) return;
            focused = on;
            long d = animate ? (host.lite() ? 120 : 250) : 0;
            float k = on ? (kind == FAV ? 1.04f : 1.06f) : 1f;
            card.animate().scaleX(k).scaleY(k).translationY(on && kind != FAV ? -px(4) : 0).setDuration(d).setInterpolator(EASE).start();
            card.setElevation(on ? px(18) : 0);
            if (kind == FAV) {
                card.setBackground(fill(16, on ? TEXT : PANEL));
                favName.setTextColor(on ? DARK : TEXT);
                favNote.setTextColor(on ? 0xFF555555 : TEXT2);
            }
            if (capRow != null) {
                capRow.animate().translationY(on ? px(10) : 0).setDuration(d).setInterpolator(EASE).start();
                if (cap != null) cap.setTextColor(on ? TEXT : TEXT2);
                if (chip != null) {
                    chip.setBackground(fill(5, on ? TEXT : PANEL3));
                    chip.setTextColor(on ? DARK : TEXT2);
                }
            }
        }
    }

    /** A cell for an item: what it shows depends on what the item is and the kind of cell. */
    Cell cell(Item it, int kind, int index, int capMode, int w) {
        int h = CARD_H[kind];
        if (kind == APP && w != KW[APP]) h = 168;
        Cell cl = new Cell(it, kind, index, w, h);
        switch (kind) {
            case FAV: favCard(cl); break;
            case APP: appCard(cl); break;
            case TXT: textCard(cl); break;
            default:
                if (Item.LIVE.equals(it.kind) || Item.SPORT.equals(it.kind)) liveCard(cl);
                else if (Item.NEWS.equals(it.kind)) textCard(cl);
                else if (Item.APP.equals(it.kind)) appCard(cl);
                else titleCard(cl);
                break;
        }
        if (kind == FEAT || kind == WIDE || kind == POSTER) caption(cl, capMode);
        return cl;
    }

    /** A film, series or video: its poster (or its picture), the rating, its place in a Top 10, how far through. */
    private void titleCard(Cell cl) {
        Item it = cl.it;
        boolean tall = cl.kind == FEAT || cl.kind == POSTER;
        String picture = tall ? (it.poster != null ? it.poster : it.backdrop) : (it.backdrop != null ? it.backdrop : it.poster);
        cl.pic = new Pic(c, px(cl.kind == FEAT ? 18 : 14), PANEL2);
        if (!tall) cl.pic.alignY(0.3f);
        cl.card.addView(cl.pic, new FrameLayout.LayoutParams(-1, -1));
        cl.url = picture;
        cl.urlW = px(tall ? Math.min(300, cl.cardW + 40) : cl.cardW);
        if (picture == null) {
            // No picture: its name, large, on the panel.
            TextView n = text(cl.kind == FEAT ? 30 : 22, host.display(), TEXT);
            n.setSingleLine(false);
            n.setMaxLines(4);
            n.setGravity(Gravity.CENTER);
            n.setText(it.name);
            n.setPadding(px(16), 0, px(16), 0);
            cl.card.addView(n, new FrameLayout.LayoutParams(-1, -1));
        } else if (!tall) {
            // A wide picture: a shade at its foot, the title's logo (or its name) over it.
            View scrim = new View(c);
            scrim.setBackground(shade(14, 0x00000000, 0xBF000000));
            cl.card.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            if (it.logo != null) {
                cl.logo = new ImageView(c);
                cl.logo.setAdjustViewBounds(true);
                cl.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cl.logo.setMaxWidth(px(cl.cardW * 0.62f));
                cl.logo.setMaxHeight(px(cl.cardH * 0.36f));
                cl.logoUrl = it.logo;
                FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                ll.leftMargin = px(16);
                ll.bottomMargin = px(30);
                cl.card.addView(cl.logo, ll);
            } else {
                TextView n = text(24, host.display(), TEXT);
                n.setText(it.name);
                n.setShadowLayer(px(8), 0, px(2), 0xB3000000);
                FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
                nl.leftMargin = px(16);
                nl.rightMargin = px(16);
                nl.bottomMargin = px(30);
                cl.card.addView(n, nl);
            }
            if (Item.VIDEO.equals(it.kind)) cl.card.addView(tag("YOUTUBE", RED), corner(14, 12, Gravity.TOP | Gravity.START));
        }
        if (it.vote > 0 && tall) cl.card.addView(rating(it.vote), corner(10, 10, Gravity.TOP | Gravity.START));
        // Its place in a Top 10, but not in Featured for you: that row takes the top TV shows and the top film, so it
        // would read 1, 2, 3, 1 with nothing to say which chart; the Top 10 rows below carry the numbers.
        if (it.rank > 0 && cl.kind != FEAT) {
            TextView rk = text(20, host.display(), 0xFFFFFFFF);
            rk.setGravity(Gravity.CENTER);
            rk.setText(String.valueOf(it.rank));
            rk.setBackground(fill(10, ORANGE));
            rk.setMinWidth(px(40));
            rk.setPadding(px(8), 0, px(8), 0);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, px(40), Gravity.TOP | Gravity.END);
            rl.topMargin = px(10);
            rl.rightMargin = px(10);
            cl.card.addView(rk, rl);
        } else if (it.flag != null && !it.flag.isEmpty() && it.app == null) {
            cl.card.addView(tag(flagWords(it.flag), ORANGE), corner(10, 10, Gravity.BOTTOM | Gravity.START));
        }
        if (it.progress > 0 && it.progress < 1) cl.card.addView(bar(it.progress), barParams(tall));
    }

    /** "NEW · S4 E1" as SWIPTV's corner word ("New episode" for a new episode, "New" for the rest). */
    private static String flagWords(String f) {
        String t = f.trim();
        if (t.length() <= 12) return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1).toLowerCase(Locale.ROOT);
        return t.toUpperCase(Locale.ROOT).startsWith("NEW") ? "New episode" : t;
    }

    /** A channel's programme or a game: its picture (Sky's), else the channel's colour or the two clubs' colours. */
    private void liveCard(Cell cl) {
        Item it = cl.it;
        boolean sport = Item.SPORT.equals(it.kind);
        boolean feat = cl.kind == FEAT;
        if (it.backdrop != null) {
            cl.pic = new Pic(c, px(feat ? 18 : 14), PANEL2);
            cl.pic.alignY(0.3f);
            cl.url = it.backdrop;
            cl.urlW = px(feat ? 560 : cl.cardW);
            cl.card.addView(cl.pic, new FrameLayout.LayoutParams(-1, -1));
        } else if (sport) {
            cl.card.addView(new Split(c, it.name, feat, px(feat ? 18 : 14), host.display()), new FrameLayout.LayoutParams(-1, -1));
        } else {
            cl.card.addView(channelPanel(it.channel, feat ? 18 : 14, feat ? 64 : 64, feat), new FrameLayout.LayoutParams(-1, -1));
        }
        View scrim = new View(c);
        scrim.setBackground(shade(feat ? 18 : 14, 0x14000000, 0xD9000000));
        cl.card.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        // The corner: LIVE (or the game's day and time) and the channel's logo, or its name.
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String word = sport ? gameWord(it) : "LIVE";
        boolean on = !sport || word.contains("LIVE") || word.contains("TONIGHT");
        top.addView(tag(word, on ? RED : ORANGE));
        if (it.mark != null) {
            cl.logo = new ImageView(c);
            cl.logo.setAdjustViewBounds(true);
            cl.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cl.logo.setMaxWidth(px(feat ? 110 : 130));
            cl.logo.setMaxHeight(px(24));
            cl.logoUrl = it.mark;
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.leftMargin = px(10);
            top.addView(cl.logo, ll);
        } else if (it.channel != null && !feat) {
            TextView ch = text(13, host.semi(), 0xFFFFFFFF);
            ch.setLetterSpacing(0.06f);
            ch.setText(it.channel.toUpperCase(Locale.ROOT));
            ch.setShadowLayer(px(4), 0, px(1), 0x99000000);
            LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-2, -2);
            cl2.leftMargin = px(8);
            top.addView(ch, cl2);
        }
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, px(30), Gravity.TOP | Gravity.START);
        tl.leftMargin = px(14);
        tl.topMargin = px(12);
        cl.card.addView(top, tl);
        // The foot: the channel or competition over the title (featured), else the title and how far on it is.
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.VERTICAL);
        if (feat) {
            TextView ps = text(14, host.semi(), 0xD1FFFFFF);
            ps.setLetterSpacing(0.08f);
            String over = sport ? it.line != null ? it.line : it.sub : it.channel;
            ps.setText(over == null ? "" : over.toUpperCase(Locale.ROOT));
            foot.addView(ps, new LinearLayout.LayoutParams(-1, -2));
            TextView pt = text(30, host.display(), 0xFFFFFFFF);
            pt.setSingleLine(false);
            pt.setMaxLines(3);
            pt.setLineSpacing(0, 1.0f);
            pt.setText(it.name == null ? "" : it.name.replaceFirst("^(?i)live:? ", ""));
            pt.setShadowLayer(px(10), 0, px(2), 0x99000000);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
            pl.topMargin = px(8);
            foot.addView(pt, pl);
        } else {
            TextView b = text(21, host.semi(), 0xFFFFFFFF);
            b.setText(it.name);
            b.setShadowLayer(px(8), 0, px(2), 0xB3000000);
            foot.addView(b, new LinearLayout.LayoutParams(-1, -2));
            if (it.progress > 0 && it.progress < 1) {
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, px(5));
                bl.topMargin = px(10);
                foot.addView(bar(it.progress), bl);
            }
        }
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fl.leftMargin = px(feat ? 18 : 14);
        fl.rightMargin = px(feat ? 18 : 14);
        fl.bottomMargin = px(feat ? 20 : 12);
        cl.card.addView(foot, fl);
    }

    private void favCard(Cell cl) {
        Item it = cl.it;
        cl.card.setBackground(fill(16, PANEL));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(22), 0, px(22), 0);
        FrameLayout sq = new FrameLayout(c);
        sq.setBackground(fill(12, android.graphics.Color.parseColor(it.channel)));
        cl.favIcon = new ImageView(c);
        cl.favIcon.setImageDrawable(SkinIcons.get(it.mark, 0xFFFFFFFF, 2f));
        sq.addView(cl.favIcon, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        row.addView(sq, new LinearLayout.LayoutParams(px(46), px(46)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        cl.favName = text(21, host.semi(), TEXT);
        cl.favName.setText(it.name);
        words.addView(cl.favName);
        cl.favNote = text(16, host.regular(), TEXT2);
        cl.favNote.setText(it.sub);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.topMargin = px(4);
        words.addView(cl.favNote, nl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(18);
        row.addView(words, wl);
        cl.card.addView(row, new FrameLayout.LayoutParams(-1, -1));
    }

    private void appCard(Cell cl) {
        Item it = cl.it;
        cl.card.setBackground(fill(14, PANEL));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(px(22), 0, px(22), 0);
        if (it.icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(it.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_START);
            col.addView(iv, new LinearLayout.LayoutParams(px(56), px(56)));
        } else if ("dice".equals(it.kind)) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(SkinIcons.get("dice", ORANGE, 1.8f));
            col.addView(iv, new LinearLayout.LayoutParams(px(46), px(46)));
        } else {
            TextView mono = text(22, host.display(), 0xFFFFFFFF);
            mono.setGravity(Gravity.CENTER);
            mono.setText(monogram(it.name));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, appColours(it));
            g.setCornerRadius(px(12));
            mono.setBackground(g);
            col.addView(mono, new LinearLayout.LayoutParams(px(52), px(52)));
        }
        TextView n = text(23, host.semi(), TEXT);
        n.setText(it.name);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = px(14);
        col.addView(n, nl);
        if (it.sub != null && !it.sub.isEmpty()) {
            TextView s = text(15, host.regular(), TEXT2);
            s.setText(it.sub);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = px(5);
            col.addView(s, sl);
        }
        cl.card.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    private void textCard(Cell cl) {
        Item it = cl.it;
        cl.card.setBackground(fill(14, PANEL));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(22), px(20), px(22), px(18));
        TextView k = text(14, host.semi(), ORANGE);
        k.setLetterSpacing(0.1f);
        k.setText(it.sub == null ? "" : it.sub.toUpperCase(Locale.ROOT));
        col.addView(k);
        TextView v = text(24, host.display(), TEXT);
        v.setSingleLine(false);
        v.setMaxLines(4);
        v.setLineSpacing(px(3), 1f);
        v.setText(it.name);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1, -2);
        vl.topMargin = px(10);
        col.addView(v, vl);
        cl.card.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    /** The caption under a card: a chip (the app's short tag, or the game's time) and a line. */
    private void caption(Cell cl, int capMode) {
        Item it = cl.it;
        String chip = chipFor(it);
        String line;
        if (Item.LIVE.equals(it.kind) || Item.SPORT.equals(it.kind)) line = it.sub != null ? it.sub : it.channel;
        else if (capMode == CAP_FITS && it.runtime > 0) line = "Ends " + hm(System.currentTimeMillis() + it.runtime * 60_000L);
        else if (capMode == CAP_SUB && it.sub != null && !it.sub.isEmpty()) line = it.sub;
        else line = it.name;
        line = dates(line == null ? "" : line);
        cl.capRow = new LinearLayout(c);
        cl.capRow.setOrientation(LinearLayout.HORIZONTAL);
        cl.capRow.setGravity(Gravity.CENTER_VERTICAL);
        if (chip != null) {
            cl.chip = text(12, host.semi(), TEXT2);
            cl.chip.setLetterSpacing(0.04f);
            cl.chip.setText(chip);
            cl.chip.setBackground(fill(5, PANEL3));
            cl.chip.setPadding(px(6), px(4), px(6), px(3));
            LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(-2, -2);
            chl.rightMargin = px(8);
            cl.capRow.addView(cl.chip, chl);
        }
        cl.cap = text(18, host.regular(), TEXT2);
        cl.cap.setText(line);
        cl.capRow.addView(cl.cap, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(cl.cardW), px(26));
        lp.topMargin = px(cl.cardH + 14);
        cl.box.addView(cl.capRow, lp);
    }

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** "2026-11-13" in a line as "13 Nov". */
    static String dates(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").matcher(line);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            int mon = Integer.parseInt(m.group(2));
            String d = Integer.parseInt(m.group(3)) + " " + (mon >= 1 && mon <= 12 ? MONTHS[mon - 1] : m.group(2));
            m.appendReplacement(b, d);
        }
        m.appendTail(b);
        return b.toString();
    }

    /** A game's word for its corner: its own ("LIVE NOW", "SAT 12:30"), else its day and time from its line. */
    static String gameWord(Item it) {
        if (it.flag != null && !it.flag.isEmpty()) return it.flag.toUpperCase(Locale.ROOT);
        if (it.sub != null) {
            String first = it.sub.split(" · ")[0].trim();
            if (first.matches("(?i)(tonight|today|tomorrow|mon|tue|wed|thu|fri|sat|sun)\\w* \\d{1,2}:\\d{2}")) return first.toUpperCase(Locale.ROOT);
        }
        return "COMING UP";
    }

    /** The chip: the app it plays in ("NF", "DS"), "LIVE", or a game's day and time. */
    private static String chipFor(Item it) {
        if (Item.LIVE.equals(it.kind)) return "LIVE";
        if (Item.SPORT.equals(it.kind)) return gameWord(it);
        if (Item.VIDEO.equals(it.kind)) return "YT";
        String t = tagFor(it.app != null ? it.app : it.line);
        return t;
    }

    static String tagFor(String app) {
        if (app == null) return null;
        String a = app.toLowerCase(Locale.ROOT);
        if (a.contains("netflix")) return "NF";
        if (a.contains("debrid")) return "DS";
        if (a.contains("disney")) return "D+";
        if (a.contains("amazon") || a.contains("prime")) return "PV";
        if (a.contains("youtube")) return "YT";
        if (a.contains("tvplayer") || a.contains("tivimate")) return "TV";
        if (a.contains("apple") || a.contains("atve")) return "TV+";
        if (a.contains("iplayer") || a.contains("bbc")) return "BBC";
        if (a.contains("itv")) return "ITVX";
        if (a.contains("nowtv") || a.equals("now")) return "NOW";
        return null;
    }

    // ---- small pieces ---------------------------------------------------------------------------------------------

    TextView tag(String words, int colour) {
        TextView t = text(13, host.semi(), 0xFFFFFFFF);
        t.setLetterSpacing(0.06f);
        t.setText(words);
        t.setBackground(fill(6, colour));
        t.setPadding(px(8), px(6), px(8), px(5));
        return t;
    }

    private FrameLayout.LayoutParams corner(int x, int y, int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravity);
        if ((gravity & Gravity.END) == Gravity.END) lp.rightMargin = px(x); else lp.leftMargin = px(x);
        if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) lp.bottomMargin = px(y); else lp.topMargin = px(y);
        return lp;
    }

    private View rating(double vote) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(fill(8, 0xC70F0F12));
        r.setPadding(px(8), px(5), px(9), px(5));
        ImageView s = new ImageView(c);
        s.setImageDrawable(SkinIcons.get("star", ORANGE));
        r.addView(s, new LinearLayout.LayoutParams(px(14), px(14)));
        TextView v = text(15, host.semi(), TEXT);
        v.setText(String.format(Locale.ROOT, "%.1f", vote));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.leftMargin = px(5);
        r.addView(v, vl);
        return r;
    }

    /** How far through: orange on a faint track. */
    View bar(float p) {
        return new Bar(c, p, ORANGE, 0x38FFFFFF);
    }

    /** A progress bar drawn in one go: a rounded track and the part done over it. */
    static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float p;
        private final int on, track;

        Bar(Context c, float p, int on, int track) {
            super(c);
            this.p = Math.max(0.04f, Math.min(1f, p));
            this.on = on;
            this.track = track;
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(), rad = h / 2f;
            paint.setColor(track);
            r.set(0, 0, w, h);
            canvas.drawRoundRect(r, rad, rad, paint);
            paint.setColor(on);
            r.set(0, 0, w * p, h);
            canvas.drawRoundRect(r, rad, rad, paint);
        }
    }

    private FrameLayout.LayoutParams barParams(boolean tall) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, px(5), Gravity.BOTTOM);
        lp.leftMargin = px(14);
        lp.rightMargin = px(14);
        lp.bottomMargin = px(12);
        return lp;
    }

    /** A gradient from clear to dark down a card (its foot darker), rounded like the card. */
    GradientDrawable shade(float radius, int top, int bottom) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, top, bottom});
        g.setCornerRadius(px(radius));
        return g;
    }

    /** A channel with no picture: its colour, its name large and faint. */
    View channelPanel(String channel, float radius, float wordSize, boolean feat) {
        FrameLayout f = new FrameLayout(c);
        int col = channelColour(channel);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{col, 0xFF050505});
        g.setCornerRadius(px(radius));
        f.setBackground(g);
        TextView w = text(wordSize, host.display(), 0x29FFFFFF);
        w.setEllipsize(null);
        w.setText(shortName(channel));
        w.setGravity(Gravity.END);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.END);
        wl.topMargin = px(feat ? 120 : 48);
        wl.rightMargin = px(18);
        wl.leftMargin = px(18);
        f.addView(w, wl);
        return f;
    }

    /** A channel's name without "HD", "UHD" and the like: "BBC One", "Main Event". */
    static String shortName(String channel) {
        if (channel == null) return "";
        String s = channel.replaceAll("(?i)\\s+(UHD|FHD|HD|4K|SD)$", "").trim();
        s = s.replaceFirst("(?i)^Sky Sports ", "");
        return s;
    }

    private static final String[][] CHANNEL_COLOURS = {
            {"(?i).*main event.*", "#C8102E"}, {"(?i).*premier league.*", "#3D195B"}, {"(?i).*sky sports f1.*", "#7A0C14"},
            {"(?i).*sky sports.*", "#B5121B"}, {"(?i).*tnt sport.*", "#1C1C1C"}, {"(?i).*bbc one.*", "#B3262D"},
            {"(?i).*bbc two.*", "#0F7D78"}, {"(?i).*bbc three.*", "#8C1D82"}, {"(?i).*bbc four.*", "#3E4C59"},
            {"(?i).*bbc news.*", "#B80000"}, {"(?i).*cbeebies.*", "#C9A400"}, {"(?i).*cbbc.*", "#E04E1B"},
            {"(?i).*itv.*", "#2A62D6"}, {"(?i).*channel 4.*|(?i)^e4.*|(?i).*more4.*", "#5B2CA0"}, {"(?i).*channel 5.*|(?i)^5.*", "#C4105A"},
            {"(?i).*sky news.*", "#C4001A"}, {"(?i).*sky atlantic.*", "#18325E"}, {"(?i).*sky cinema.*", "#0B5EA8"},
            {"(?i).*sky.*", "#1F4FA3"}, {"(?i).*dave.*", "#2E7D32"}, {"(?i).*discovery.*", "#1565C0"}};

    static int channelColour(String channel) {
        if (channel != null) for (String[] p : CHANNEL_COLOURS) if (channel.matches(p[0])) return android.graphics.Color.parseColor(p[1]);
        int[] pal = {0xFF2A3E8F, 0xFF7A2E8F, 0xFF0F6B5E, 0xFF8F3A1F, 0xFF27566E, 0xFF5A2A6E};
        return pal[Math.abs((channel == null ? 0 : channel.hashCode()) % pal.length)];
    }

    static String monogram(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] w = name.replaceAll("[^A-Za-z0-9 ]", " ").trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String s : w) if (!s.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(s.charAt(0)));
        return b.length() == 0 ? name.substring(0, 1) : b.toString();
    }

    private static int[] appColours(Item it) {
        String n = it.name == null ? "" : it.name.toLowerCase(Locale.ROOT);
        if (n.contains("tivimate")) return new int[]{0xFF3D2AA8, 0xFF1A1150};
        if (n.contains("youtube")) return new int[]{0xFFC4161C, 0xFF5A0A0D};
        if (n.contains("debrid")) return new int[]{0xFF0F8A7A, 0xFF063D36};
        if (n.contains("netflix")) return new int[]{0xFF8F0B12, 0xFF2A0306};
        if (n.contains("disney")) return new int[]{0xFF163A8F, 0xFF081840};
        if (n.contains("prime")) return new int[]{0xFF1B6FB8, 0xFF0A2A4A};
        if (n.contains("nord") || n.contains("vpn")) return new int[]{0xFF2D5FD6, 0xFF0D1F4D};
        int c = channelColour(n);
        return new int[]{c, 0xFF101014};
    }

    /** Two clubs' sides of a game with no picture: the names' first letters on two colours, cut by a slant. */
    static final class Split extends View {
        private final String a, b;
        private final int ca, cb;
        private final boolean tall;
        private final float radius;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), clip = new Path();
        private final RectF box = new RectF();

        Split(Context c, String game, boolean tall, float radius, Typeface face) {
            super(c);
            String[] sides = game == null ? new String[]{"", ""} : game.replaceFirst("^(?i)live:? ", "").split("\\s+v(s|\\.)?\\s+", 2);
            a = abbr(sides[0]);
            b = sides.length > 1 ? abbr(sides[1]) : "";
            ca = clubColour(sides[0]);
            cb = clubColour(sides.length > 1 ? sides[1] : "x");
            this.tall = tall;
            this.radius = radius;
            p.setTypeface(face);
        }

        private static String abbr(String team) {
            String t = team.trim().replaceAll("[^A-Za-z ]", "");
            if (t.isEmpty()) return "";
            String[] w = t.split("\\s+");
            if (w.length > 1 && w[0].length() <= 3 && !w[0].equalsIgnoreCase("the")) return (w[0] + w[1].charAt(0)).toUpperCase(Locale.ROOT).substring(0, Math.min(3, w[0].length() + 1));
            return w[0].substring(0, Math.min(3, w[0].length())).toUpperCase(Locale.ROOT);
        }

        private static int clubColour(String team) {
            int[] pal = {0xFF0E3A9C, 0xFFB01E23, 0xFF0F7A4D, 0xFF5A2A8C, 0xFFB86B00, 0xFF1C6E8C, 0xFF7A1F3D, 0xFF2B2B2B};
            return pal[Math.abs(team.trim().toLowerCase(Locale.ROOT).hashCode() % pal.length)];
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            box.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            path.reset();
            p.setColor(ca);
            if (tall) {
                path.moveTo(0, 0); path.lineTo(w, 0); path.lineTo(w, h * 0.42f); path.lineTo(0, h * 0.62f); path.close();
            } else {
                path.moveTo(0, 0); path.lineTo(w * 0.58f, 0); path.lineTo(w * 0.42f, h); path.lineTo(0, h); path.close();
            }
            canvas.drawPath(path, p);
            path.reset();
            p.setColor(cb);
            if (tall) {
                path.moveTo(0, h * 0.62f); path.lineTo(w, h * 0.42f); path.lineTo(w, h); path.lineTo(0, h); path.close();
            } else {
                path.moveTo(w * 0.58f, 0); path.lineTo(w, 0); path.lineTo(w, h); path.lineTo(w * 0.42f, h); path.close();
            }
            canvas.drawPath(path, p);
            canvas.drawColor(0x40000000);
            p.setColor(0xF2FFFFFF);
            p.setLetterSpacing(-0.04f);
            if (tall) {
                p.setTextSize(w * 0.34f);
                p.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(a, w * 0.08f, h * 0.22f + p.getTextSize() * 0.8f, p);
                p.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(b, w * 0.92f, h * 0.48f + p.getTextSize() * 0.8f, p);
            } else {
                p.setTextSize(w * 0.19f);
                p.setTextAlign(Paint.Align.LEFT);
                float y = h * 0.5f + p.getTextSize() * 0.32f;
                canvas.drawText(a, w * 0.07f, y, p);
                p.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(b, w * 0.93f, y, p);
            }
            canvas.restore();
        }
    }

    // ---- rows (Home and Sports) -------------------------------------------------------------------------------------

    /** One row: its heading, and its cells along a track that slides as the focus goes along. */
    final class Row {
        final String key;
        String title, sub;
        final int kind, capMode;
        List<Item> items = new ArrayList<>();
        final List<Cell> cells = new ArrayList<>();
        final FrameLayout view = new FrameLayout(c);
        final TextView titleV, subV;
        final LinearLayout track = new LinearLayout(c);
        int idx, top, shown = -1;
        int cellKind;
        /** What the last paint set: faded above the focus, and how far along. */
        boolean wasGone;
        float wasShift = -1;
        /** A word on every poster's corner ("New"), where the items carry none of their own. */
        String flag;

        Row(String key, String title, String sub, int kind, int capMode) {
            this.key = key;
            this.title = title;
            this.sub = sub;
            this.kind = kind;
            this.capMode = capMode;
            cellKind = kind;
            view.setClipChildren(false);
            LinearLayout head = new LinearLayout(c);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.BOTTOM);
            titleV = text(27, host.semi(), TEXT);
            titleV.setText(title);
            head.addView(titleV);
            subV = text(20, host.regular(), TEXT2);
            subV.setText(sub == null ? "" : sub);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = px(12);
            sl.bottomMargin = px(1);
            head.addView(subV, sl);
            add(view, head, 0, 0, 1600, 36);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setClipChildren(false);
            // Cut at the page's left edge (a little before it, for the focused card to grow), not at the screen's.
            FrameLayout cut = new FrameLayout(c);
            cut.setClipChildren(true);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, px(KH[kind] + 20));
            tl.leftMargin = px(24);
            tl.topMargin = px(24);
            cut.addView(track, tl);
            add(view, cut, -24, 58 - 24, 1744 + 24, KH[kind] + 64);
            cutBox = cut;
            cut.setClipBounds(new android.graphics.Rect(0, 0, px(1744 + 24), px(KH[kind] + 64)));
        }

        FrameLayout cutBox;

        /** The cut-off box sized for the row's cells (a poster row shown wide is taller). */
        void sizeCut() {
            ViewGroup.LayoutParams lp = cutBox.getLayoutParams();
            lp.height = px(KH[cellKind] + 64);
            cutBox.setLayoutParams(lp);
            cutBox.setClipBounds(new android.graphics.Rect(0, 0, px(1744 + 24), px(KH[cellKind] + 64)));
            ViewGroup.LayoutParams tl = track.getLayoutParams();
            tl.height = px(KH[cellKind] + 20);
            track.setLayoutParams(tl);
        }

        int width() {
            return KW[cellKind] + GAP;
        }

        int height() {
            return 58 + KH[cellKind] + 44;
        }

        float shift() {
            int w = width();
            int total = items.size() * w - GAP;
            return Math.max(0, Math.min(idx * w, total - ROOM));
        }
    }

    /** A page of rows (Home, Sports): the focused row's heading at `base` from the top, the rows above it faded away. */
    final class Rows {
        final FrameLayout view = new FrameLayout(c);
        final FrameLayout box = new FrameLayout(c);
        final List<Row> rows = new ArrayList<>();
        final int base;
        int ri;
        final SportsHero hero;

        Rows(int base, boolean withHero) {
            this.base = base;
            view.setClipChildren(false);
            box.setClipChildren(false);
            hero = withHero ? new SportsHero() : null;
            if (hero != null) view.addView(hero.view, new FrameLayout.LayoutParams(-1, px(660)));
            view.addView(box, new FrameLayout.LayoutParams(-1, -1));
        }

        Row add(String key, String title, String sub, int kind, int capMode) {
            Row r = new Row(key, title, sub, kind, capMode);
            r.view.setVisibility(View.GONE);
            rows.add(r);
            box.addView(r.view, new FrameLayout.LayoutParams(-1, px(r.height() + 40)));
            return r;
        }

        List<Row> shown() {
            List<Row> out = new ArrayList<>();
            for (Row r : rows) if (!r.items.isEmpty()) out.add(r);
            return out;
        }

        Row focused() {
            List<Row> s = shown();
            return s.isEmpty() ? null : s.get(Math.max(0, Math.min(ri, s.size() - 1)));
        }

        Cell focusedCell() {
            Row r = focused();
            return r == null || r.cells.isEmpty() ? null : r.cells.get(Math.min(r.idx, r.cells.size() - 1));
        }

        /** The row's items now: its cells made again, the focus kept on the same row. */
        void set(Row r, List<Item> items) {
            Row was = focused();
            r.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
            // A poster row whose items have no posters (a channel's or an app's own row) shows them wide instead.
            int withPoster = 0;
            for (Item it : r.items) if (it.poster != null) withPoster++;
            r.cellKind = r.kind == POSTER && withPoster * 2 < r.items.size() ? WIDE : r.kind;
            r.track.removeAllViews();
            r.cells.clear();
            for (int i = 0; i < r.items.size(); i++) {
                Item it = r.items.get(i);
                if (r.flag != null && it.flag == null && it.rank == 0) it.flag = r.flag;
                Cell cl = cell(it, r.cellKind, i, r.capMode, KW[r.cellKind]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(KW[r.cellKind]), px(KH[r.cellKind] + 20));
                if (i > 0) lp.leftMargin = px(GAP);
                r.track.addView(cl.box, lp);
                r.cells.add(cl);
            }
            if (r.idx >= r.items.size()) r.idx = Math.max(0, r.items.size() - 1);
            r.sizeCut();
            r.shown = -1;
            r.view.setVisibility(r.items.isEmpty() ? View.GONE : View.VISIBLE);
            ViewGroup.LayoutParams lp = r.view.getLayoutParams();
            lp.height = px(r.height() + 40);
            r.view.setLayoutParams(lp);
            List<Row> s = shown();
            if (!touched) ri = 0;
            else if (was != null && s.contains(was)) ri = s.indexOf(was);
            else ri = Math.max(0, Math.min(ri, s.size() - 1));
        }

        boolean move(int dx, int dy) {
            List<Row> s = shown();
            if (s.isEmpty()) return dx >= 0;
            Row r = s.get(Math.min(ri, s.size() - 1));
            if (dy != 0) {
                int n = ri + dy;
                if (n >= 0 && n < s.size()) ri = n;
                return true;
            }
            if (dx < 0 && r.idx == 0) return false;
            r.idx = Math.max(0, Math.min(r.items.size() - 1, r.idx + dx));
            return true;
        }

        void paint(boolean animate, boolean focus) {
            List<Row> s = shown();
            int y = 0;
            for (Row r : s) {
                r.top = y;
                y += r.height();
            }
            if (s.isEmpty()) return;
            ri = Math.max(0, Math.min(ri, s.size() - 1));
            long d = animate ? (host.lite() ? 200 : 450) : 0;
            float to = px(base - s.get(ri).top);
            for (Row r : s) {
                r.view.setTranslationY(px(r.top));
            }
            if (animate) box.animate().translationY(to).setDuration(d).setInterpolator(EASE).start();
            else box.setTranslationY(to);
            for (int i = 0; i < s.size(); i++) {
                Row r = s.get(i);
                boolean gone = i < ri;
                if (gone != r.wasGone || !animate) {
                    r.wasGone = gone;
                    if (animate) r.view.animate().alpha(gone ? 0f : 1f).setDuration(300).start();
                    else r.view.setAlpha(gone ? 0f : 1f);
                }
                float x = r.shift();
                if (x != r.wasShift || !animate) {
                    r.wasShift = x;
                    if (animate) r.track.animate().translationX(-px(x)).setDuration(host.lite() ? 200 : 400).setInterpolator(EASE).start();
                    else r.track.setTranslationX(-px(x));
                }
                for (int j = 0; j < r.cells.size(); j++) r.cells.get(j).focus(focus && i == ri && j == r.idx, animate);
                // Pictures for the rows in and near view only; the rest let go.
                boolean near = i >= ri - 1 && i <= ri + 3;
                for (Cell cl : r.cells) {
                    if (near) cl.load();
                    else if (i < ri - 2 || i > ri + 5) cl.drop();
                }
            }
            if (hero != null) {
                Cell f = focusedCell();
                if (f != null) hero.show(f.it);
            }
        }
    }

    /** Sports' top: the focused game or channel's picture to the right, its words to the left, what is coming up under. */
    final class SportsHero {
        final FrameLayout view = new FrameLayout(c);
        final Pic art;
        final FrameLayout artBox = new FrameLayout(c);
        final TextView chip, src, h1, tm, p;
        final LinearLayout up;
        private Item shown;

        SportsHero() {
            view.setClipChildren(false);
            art = new Pic(c, 0, BG);
            art.alignY(0.25f);
            artBox.addView(art, new FrameLayout.LayoutParams(-1, -1));
            View left = new View(c);
            left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{BG, 0xB3141519, 0x00141519}));
            artBox.addView(left, new FrameLayout.LayoutParams(px(460), -1));
            View foot = new View(c);
            foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{BG, 0x99141519, 0x00141519}));
            artBox.addView(foot, new FrameLayout.LayoutParams(-1, px(260), Gravity.BOTTOM));
            add(view, artBox, 1744 - 1180, 0, 1180, 640);
            LinearLayout inf = new LinearLayout(c);
            inf.setOrientation(LinearLayout.VERTICAL);
            LinearLayout pills = new LinearLayout(c);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            pills.setGravity(Gravity.CENTER_VERTICAL);
            chip = text(13, host.semi(), TEXT2);
            chip.setLetterSpacing(0.04f);
            chip.setBackground(fill(5, PANEL3));
            chip.setPadding(px(6), px(4), px(6), px(3));
            pills.addView(chip);
            src = text(18, host.regular(), TEXT2);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = px(10);
            pills.addView(src, sl);
            inf.addView(pills);
            h1 = text(54, host.display(), TEXT);
            h1.setSingleLine(false);
            h1.setMaxLines(2);
            h1.setLineSpacing(0, 1.02f);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
            hl.topMargin = px(16);
            inf.addView(h1, hl);
            tm = text(21, host.semi(), TEXT2);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = px(14);
            inf.addView(tm, tl);
            p = text(21, host.regular(), TEXT2);
            p.setSingleLine(false);
            p.setMaxLines(3);
            p.setLineSpacing(px(8), 1f);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
            pl.topMargin = px(14);
            inf.addView(p, pl);
            up = new LinearLayout(c);
            up.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-1, -2);
            ul.topMargin = px(18);
            inf.addView(up, ul);
            add(view, inf, 0, 64, 860, 560);
        }

        private Item want;
        private final Runnable later = () -> { if (want != null) now(want); };

        void show(Item it) {
            if (it == shown && want == null) return;
            if (host.lite()) {
                want = it;
                host.main().removeCallbacks(later);
                host.main().postDelayed(later, 280);
                return;
            }
            now(it);
        }

        private void now(Item it) {
            want = null;
            if (it == shown) return;
            shown = it;
            boolean sport = Item.SPORT.equals(it.kind), live = Item.LIVE.equals(it.kind);
            String c1 = sport ? (it.flag != null ? it.flag : "SPORT") : live ? "LIVE" : it.sub != null ? it.sub : "NEWS";
            chip.setText(c1.toUpperCase(Locale.ROOT));
            src.setText(sport || live ? (it.channel != null ? it.channel : "") : "");
            String name = it.name == null ? "" : it.name.replaceFirst("^(?i)live:? ", "");
            h1.setText(name);
            tm.setText(it.line != null ? it.line : it.sub != null && (sport || live) ? it.sub : "");
            p.setText(it.overview == null ? "" : it.overview);
            if (it.backdrop != null) {
                art.setVisibility(View.VISIBLE);
                host.image(art, it.backdrop, px(host.lite() ? 960 : 1280));
                clearPanel();
            } else {
                art.setImageBitmap(null);
                clearPanel();
                View panel = sport ? new Split(c, it.name, false, 0, host.display()) : channelPanel(it.channel != null ? it.channel : it.sub, 0, 150, false);
                panel.setTag("panel");
                artBox.addView(panel, 1, new FrameLayout.LayoutParams(-1, -1));
            }
            artBox.setAlpha(0f);
            artBox.animate().alpha(1f).setDuration(host.lite() ? 120 : 400).start();
            // What else is coming up: the next games on the sports channels.
            up.removeAllViews();
            int n = 0;
            for (Item g : rowData(ROW_SPORT)) {
                if (g == it || n >= 4) continue;
                LinearLayout line = new LinearLayout(c);
                line.setOrientation(LinearLayout.HORIZONTAL);
                TextView when = text(18, host.semi(), TEXT3);
                when.setText(cap(gameWord(g)));
                line.addView(when, new LinearLayout.LayoutParams(px(130), -2));
                TextView what = text(18, host.regular(), TEXT2);
                what.setText(g.name + (g.channel != null ? " · " + g.channel : ""));
                line.addView(what, new LinearLayout.LayoutParams(0, -2, 1f));
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
                ll.topMargin = px(n == 0 ? 0 : 8);
                up.addView(line, ll);
                n++;
            }
        }

        private void clearPanel() {
            for (int i = artBox.getChildCount() - 1; i >= 0; i--) if ("panel".equals(artBox.getChildAt(i).getTag())) artBox.removeViewAt(i);
        }
    }

    static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        String l = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    // ---- poster grids (the libraries, Apps and My List) --------------------------------------------------------------

    /** Cells in columns, moving up a row at a time as the focus goes down. */
    final class PosterGrid {
        final FrameLayout wrap = new FrameLayout(c);
        final FrameLayout g = new FrameLayout(c);
        final List<Cell> cells = new ArrayList<>();
        final int cols, cellW, kind;
        final int pitchX, pitchY;
        int at;
        final TextView empty;

        PosterGrid(int cols, int cellW, int kind) {
            this.cols = cols;
            this.cellW = cellW;
            this.kind = kind;
            pitchX = cellW + (kind == APP ? 30 : 26);
            pitchY = (kind == APP ? 168 : KH[POSTER]) + (kind == APP ? 34 : 30);
            wrap.setClipChildren(true);
            g.setClipChildren(false);
            wrap.addView(g, new FrameLayout.LayoutParams(-1, -2));
            empty = text(22, host.regular(), TEXT2);
            empty.setSingleLine(false);
            empty.setVisibility(View.GONE);
            FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(px(900), -2);
            el.topMargin = px(24);
            el.leftMargin = px(6);
            wrap.addView(empty, el);
        }

        void set(List<Item> items, String none) {
            g.removeAllViews();
            cells.clear();
            at = 0;
            for (int i = 0; i < items.size(); i++) {
                Cell cl = cell(items.get(i), kind, i, CAP_NAME, cellW);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(cellW), px(pitchY));
                lp.leftMargin = px(6 + (i % cols) * pitchX);
                lp.topMargin = px(14 + (i / cols) * pitchY);
                g.addView(cl.box, lp);
                cells.add(cl);
            }
            g.setTranslationY(0);
            empty.setVisibility(items.isEmpty() && none != null ? View.VISIBLE : View.GONE);
            empty.setText(none == null ? "" : none);
        }

        boolean move(int dx, int dy) {
            if (cells.isEmpty()) return dx >= 0;
            if (dx < 0 && at % cols == 0) return false;
            int n = at + dx + dy * cols;
            if (dy > 0 && n >= cells.size() && at / cols < (cells.size() - 1) / cols) n = cells.size() - 1;
            if (n < 0 || n >= cells.size()) return true;
            at = n;
            return true;
        }

        Item focused() {
            return cells.isEmpty() ? null : cells.get(Math.min(at, cells.size() - 1)).it;
        }

        void paint(boolean animate, boolean focus) {
            int rowN = at / cols;
            float y = -Math.max(0, rowN - 1) * px(pitchY);
            if (animate) g.animate().translationY(y).setDuration(host.lite() ? 200 : 400).setInterpolator(EASE).start();
            else g.setTranslationY(y);
            for (int i = 0; i < cells.size(); i++) {
                Cell cl = cells.get(i);
                cl.focus(focus && i == at, animate);
                int r = i / cols;
                if (r >= rowN - 2 && r <= rowN + 2) cl.load();
                else if (r < rowN - 4 || r > rowN + 4) cl.drop();
            }
        }
    }

    /** A library: the page's categories (its tabs) beside a grid of the focused one's posters. */
    final class Library {
        final String name;
        final FrameLayout view = new FrameLayout(c);
        final TextView heading;
        final LinearLayout cats = new LinearLayout(c);
        final FrameLayout catsBox = new FrameLayout(c);
        final PosterGrid grid = new PosterGrid(5, 210, POSTER);
        final List<String> tabs;
        final List<LinearLayout> catViews = new ArrayList<>();
        final List<TextView> counts = new ArrayList<>();
        final Map<Integer, List<Item>> got = new HashMap<>();
        int cat, showing = -1;
        /** Still on the category it opened on: an empty one there moves on to the next by itself. */
        private boolean auto = true;
        private final Runnable readLater = this::readNow;

        Library(String name, String title) {
            this.name = name;
            view.setClipChildren(false);
            TextView h2 = text(38, host.display(), TEXT);
            h2.setText(title);
            add(view, h2, 0, 40, 600, 48);
            catsBox.setClipChildren(true);
            catsBox.addView(cats, new FrameLayout.LayoutParams(-1, -2));
            cats.setOrientation(LinearLayout.VERTICAL);
            add(view, catsBox, 0, 116, 446, 940);
            catsBox.setClipBounds(new android.graphics.Rect(0, 0, px(446), px(940)));
            heading = text(30, host.display(), TEXT);
            add(view, heading, 500, 48, 1100, 40);
            add(view, grid.wrap, 494, 108, 1250, 972);
            grid.wrap.setClipBounds(new android.graphics.Rect(0, 0, px(1250), px(972)));
            List<String> t;
            try {
                t = host.tabs(name);
            } catch (Exception e) {
                t = new ArrayList<>();
            }
            tabs = t;
            for (int i = 0; i < tabs.size(); i++) {
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(px(18), 0, px(18), 0);
                ImageView ic = new ImageView(c);
                ic.setImageDrawable(SkinIcons.get(catIcon(tabs.get(i), name), TEXT3, 2f));
                row.addView(ic, new LinearLayout.LayoutParams(px(22), px(22)));
                TextView n = text(20, host.semi(), TEXT2);
                n.setText(tabs.get(i));
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
                nl.leftMargin = px(14);
                row.addView(n, nl);
                TextView cn = text(17, host.regular(), TEXT3);
                row.addView(cn);
                counts.add(cn);
                ImageView cv = new ImageView(c);
                cv.setImageDrawable(SkinIcons.get("chev", TEXT3, 2.6f));
                LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(px(16), px(16));
                vl.leftMargin = px(12);
                row.addView(cv, vl);
                cats.addView(row, new LinearLayout.LayoutParams(-1, px(52)));
                catViews.add(row);
                final int idx = i;
                row.setOnClickListener(v -> { zone = Z_CATS; cat = idx; readSoon(); SkinSwip.this.paint(true); });
            }
            // The first category with something in it: Continue Watching only once something has been started.
            cat = 0;
            readNow();
        }

        /** Opens on the category of this name (Fits tonight from Home's shortcut). */
        void pick(String words) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).toLowerCase(Locale.ROOT).equals(words)) {
                    auto = false;
                    cat = i;
                    readNow();
                    zone = Z_GRID;
                    SkinSwip.this.paint(true);
                    return;
                }
            }
        }

        private void readSoon() {
            host.main().removeCallbacks(readLater);
            if (got.containsKey(cat)) { readNow(); return; }
            heading.setText(cat < tabs.size() ? tabs.get(cat) : "");
            host.main().postDelayed(readLater, 250);
        }

        private void readNow() {
            final int t = cat;
            heading.setText(t < tabs.size() ? tabs.get(t) : "");
            List<Item> have = got.get(t);
            if (have != null) { show(t, have); return; }
            if (showing != t) {
                grid.set(new ArrayList<>(), "Loading " + (t < tabs.size() ? tabs.get(t) : "") + "…");
                showing = -1;
            }
            load(name + "#" + t, items -> {
                got.put(t, items);
                if (t < counts.size()) counts.get(t).setText(items.isEmpty() ? "" : String.valueOf(items.size()));
                if (cat != t) return;
                if (items.isEmpty() && auto && t + 1 < tabs.size() && t < 2) {
                    cat = t + 1;
                    readNow();
                    SkinSwip.this.paint(false);
                    return;
                }
                show(t, items);
            });
        }

        private void show(int t, List<Item> items) {
            if (showing == t) return;
            showing = t;
            grid.set(items, "Nothing here yet.");
            SkinSwip.this.paint(false);
        }

        boolean move(int dx, int dy) {
            if (zone == Z_CATS) {
                if (dx < 0) return false;
                if (dx > 0) {
                    if (!grid.cells.isEmpty()) zone = Z_GRID;
                    return true;
                }
                int n = cat + dy;
                if (n >= 0 && n < tabs.size()) {
                    cat = n;
                    auto = false;
                    readSoon();
                }
                return true;
            }
            if (!grid.move(dx, dy)) {
                zone = Z_CATS;
            }
            return true;
        }

        Item focusedItem() {
            if (zone == Z_CATS) {
                if (!grid.cells.isEmpty()) zone = Z_GRID;
                SkinSwip.this.paint(true);
                return null;
            }
            return grid.focused();
        }

        void paint(boolean animate) {
            for (int i = 0; i < catViews.size(); i++) {
                LinearLayout row = catViews.get(i);
                boolean sel = i == cat, f = sel && zone == Z_CATS;
                row.setBackground(fill(26, f ? TEXT : sel ? 0x1AFFFFFF : 0x00FFFFFF));
                ((TextView) row.getChildAt(1)).setTextColor(f ? DARK : sel ? TEXT : TEXT2);
                ((SkinIcons.Icon) ((ImageView) row.getChildAt(0)).getDrawable()).colour(f ? 0xFF555555 : TEXT3);
                ((TextView) row.getChildAt(2)).setTextColor(f ? 0xFF555555 : TEXT3);
                ((SkinIcons.Icon) ((ImageView) row.getChildAt(3)).getDrawable()).colour(f ? 0xFF555555 : TEXT3);
            }
            // The list follows the focus once it is longer than the room.
            int visible = 940 / 52;
            int first = Math.max(0, Math.min(cat - visible / 2, tabs.size() - visible));
            float y = -px(52) * first;
            if (animate) cats.animate().translationY(y).setDuration(250).setInterpolator(EASE).start();
            else cats.setTranslationY(y);
            grid.paint(animate, zone == Z_GRID);
        }
    }

    private static String catIcon(String tab, String page) {
        String t = tab.toLowerCase(Locale.ROOT);
        if (t.contains("continue")) return "play";
        if (t.contains("my list") || t.contains("favourite") || t.contains("your shows")) return "heart";
        if (t.contains("top")) return "star";
        if (t.contains("surprise") || t.contains("dice")) return "dice";
        if (t.contains("trailer") || t.contains("youtube")) return "play";
        if (t.contains("coming") || t.contains("new episodes")) return "bell";
        if (t.contains("netflix") || t.contains("prime") || t.contains("disney") || t.contains("apple") || t.equals("now")) return "apps";
        if (t.contains("kids") || t.contains("family") || t.contains("cbeebies") || t.contains("animat") || t.contains("bangla")) return "kids";
        if (t.contains("trending") || t.contains("popular")) return "chart";
        if (t.contains("new")) return "plus";
        return "shows".equals(page) ? "shows" : "movies";
    }

    /** Apps and My List: a heading over a grid. */
    final class Grid {
        final FrameLayout view = new FrameLayout(c);
        final PosterGrid grid;
        final String key;

        Grid(String title, String key, int kind, int cols, int cellW) {
            this.key = key;
            view.setClipChildren(false);
            TextView h2 = text(38, host.display(), TEXT);
            h2.setText(title);
            add(view, h2, 0, 40, 800, 48);
            grid = new PosterGrid(cols, cellW, kind);
            add(view, grid.wrap, -6, 112, 1750, 968);
            grid.wrap.setClipBounds(new android.graphics.Rect(0, 0, px(1750), px(968)));
            grid.set(new ArrayList<>(), "Loading…");
            load(key, items -> {
                grid.set(items, ROW_MY_LIST.equals(key) ? "Nothing saved yet. Hold OK on a film or a series to add it to My List." : "No apps found.");
                SkinSwip.this.paint(false);
            });
        }

        boolean move(int dx, int dy) {
            return grid.move(dx, dy);
        }

        Item focusedItem() {
            return grid.focused();
        }

        void paint(boolean animate) {
            grid.paint(animate, zone == Z_GRID);
        }
    }

    static String nonEmpty(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }
}
