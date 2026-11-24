package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.F1;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;
import com.gathin.homefieldtv.sports.Squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The sports hub in 07 (the approved mock-ups 15, 16 and 17), a page under the tab bar (its Sports tab): its title with
 * the sections' pills beside it (LIVE | TODAY | TOMORROW | YESTERDAY | TABLES | F1 &amp; MORE) and a line under them, over
 * the blurred picture of what the remote is on. Live: what is on now ("No football is live right now. Today's games are
 * under Today."); Tomorrow and Yesterday: the football in the competitions followed on that day ("No football tomorrow in
 * the competitions followed."), each as Today lists its games.
 *
 * <p>Today: the games and events in every sport the apps follow, live first, then later today, then those just
 * finished, under their sections' capitals with a count; a row each, washed at its ends in the two sides' colours (the
 * away side's second colour when the two are too close), the competition in small capitals, both sides with their
 * crests, the score in boxes of each side's colour (or the kick-off), how far on. The remote's row is white and lifted.
 * Beside the list the game the remote is on, big: its top split between the two sides' colours with a fine stripe, the
 * competition and LIVE, both crests and names, the score huge and the clock (a red card when one was shown), the
 * scorers and red cards, the family's own channels that show it (English first), whether the hub is in step with the
 * channel being watched, and what OK does. OK opens its page (GameScreen); Right goes on to a football game's two clubs
 * (a ring round the side), where OK opens the club's page (TeamScreen).
 *
 * <p>Tables: the leagues on a glass panel down the left, the one the remote is on as its table on glass beside them
 * (each club's place with ESPN's zone mark, crest, name, played, won, drawn, lost, goal difference and points); Right goes
 * into the table, OK on a club opens its page.
 *
 * <p>Formula 1: the drivers in championship order (place, the team's colour, formula1.com's picture of him in a round
 * frame, name, team and wins, points), the one the remote is on beside them on a card in his team's colour (him
 * standing, his number, name, team and nationality, his place and points, wins and podiums) with the next Grand Prix;
 * OK opens the driver's page (DriverScreen).
 *
 * <p>Up from the top of a list goes to the pills; Left and Right (or OK) there pick a section, Down comes back, Up goes on
 * to the tab bar; Back at a section's root goes up to the tab bar too.
 */
final class SportsScreen extends Screen {
    private static final long REFRESH_MS = 30_000L, REST_MS = 300L;
    /** The sections. */
    static final int LIVE = 0, TODAY = 1, TOMORROW = 2, YESTERDAY = 3, TABLES = 4, FORMULA = 5;
    static final String[] SECTIONS = {"LIVE", "TODAY", "TOMORROW", "YESTERDAY", "TABLES", "F1 & MORE"},
            SECTION_NAMES = {"Live", "Today", "Tomorrow", "Yesterday", "Tables", "F1 & more"};
    /** Where the remote is: the pills, today's games, a game's two clubs, the leagues, a table, the drivers. */
    static final int PILLS = 1, LIST = 2, SIDES = 3, LEAGUES = 4, TABLE = 5, DRIVERS = 6;

    private Hub hub;
    int zone = LIST;
    int section = TODAY;
    /** The pill the remote is on (while on the pills). */
    int pill = TODAY;
    /** The games the section shows (Live, Today, Tomorrow, Yesterday); today's every game; the other days' as read. */
    private List<SportsData.Item> items = new ArrayList<>(), today = new ArrayList<>();
    private final Map<Integer, List<SportsData.Item>> days = new HashMap<>();
    private final Set<Integer> dayAsking = new HashSet<>();
    int focus;
    /** On a game's clubs: 0 the home side, 1 the away side. */
    int side;
    private boolean loading = true;
    private final Map<String, List<Matching.Row>> channels = new HashMap<>();
    /** Tables: the league the remote is on (Espn.TABLES), the row in its table; each league's rows and clubs as read. */
    int league, row;
    final Map<String, List<String[]>> tables = new HashMap<>();
    private final Map<String, Map<String, String[]>> clubs = new HashMap<>();
    private final Set<String> asking = new HashSet<>();
    /** Formula 1: the drivers in championship order, the next Grand Prix, the driver the remote is on. */
    List<F1.Standing> drivers = new ArrayList<>();
    F1.Weekend weekend;
    int driver;
    private boolean f1Asking;
    private long f1At;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::load;
    private final Runnable askChannels = this::askChannels;
    private final Runnable rested = this::rested;

    SportsScreen(TvActivity tv) {
        super(tv);
    }

    /** For previews: the hub drawn with these, the remote on the first. */
    void preview(List<SportsData.Item> list, Map<String, List<Matching.Row>> rows) {
        today = new ArrayList<>(list);
        items = listFor(section);
        channels.clear();
        channels.putAll(rows);
        loading = false;
        focus = 0;
        if (hub != null) hub.laidOut();
    }

    /** For previews and tests: a league's table and clubs as if read. */
    void previewTable(String slug, List<String[]> rows, List<String[]> teams) {
        tables.put(slug, new ArrayList<>(rows));
        Map<String, String[]> byId = new HashMap<>();
        for (String[] t : teams) byId.put(t[Espn.C_ID], t);
        clubs.put(slug, byId);
        if (hub != null) hub.invalidate();
    }

    /** For previews: a driver's pictures from these addresses (drawn ones) instead of formula1.com's: {him, his car}. */
    final Map<F1.Standing, String[]> previewPictures = new HashMap<>();

    /** For previews and tests: the drivers' championship and the next Grand Prix as if read. */
    void previewF1(List<F1.Standing> list, F1.Weekend next) {
        drivers = new ArrayList<>(list);
        weekend = next;
        f1At = System.currentTimeMillis();
        if (hub != null) hub.invalidate();
    }

    /** Its page draws an opaque ground over the whole screen (the window's own isn't drawn under it). */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        // No background of its own: its page draws its opaque ground first (a full screen of fill saved each frame).
        hub = new Hub();
        f.addView(hub, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (!Exec.inline) {
            load();
            if (section == TABLES) askTable();
            if (section == FORMULA) askF1();
        }
        if (hub != null) hub.channelsKnown();
        restSoon();
    }

    @Override void hidden() {
        h.removeCallbacks(refresh);
        h.removeCallbacks(askChannels);
        h.removeCallbacks(rested);
    }

    @Override void tick() {
        hub.invalidate();
    }

    // ---- 07's shell -------------------------------------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    @Override int tab() {
        return TabBar.SPORTS;
    }

    /** Down from the tab bar: the sections' pills, the remote on the section shown. */
    @Override void fromTabs() {
        zone = PILLS;
        pill = section;
        hub.invalidate();
    }

    /** The "‹ back" words of a page opened from here: "Sports · Today". */
    String backWords() {
        return "Sports · " + SECTION_NAMES[section];
    }

    private void toTabs() {
        if (tv.tabs() != null) tv.tabs().enter();
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    private void load() {
        h.removeCallbacks(refresh);
        Exec.io(() -> {
            List<SportsData.Item> got = SportsData.load(20_000L);
            Exec.main(() -> {
                String was = focus < items.size() ? items.get(focus).key : "";
                today = new ArrayList<>(got);
                items = listFor(section);
                loading = false;
                focus = 0;
                for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(was)) focus = i;
                if (zone == SIDES && !football(chosen())) zone = LIST;
                hub.laidOut();
                askChannelsSoon();
                restSoon();
                h.postDelayed(refresh, REFRESH_MS);
            });
        });
    }

    private void askChannelsSoon() {
        h.removeCallbacks(askChannels);
        h.postDelayed(askChannels, 350);
    }

    /** The playlist's channels for the game the remote is on (and the next few), once each. */
    private void askChannels() {
        List<SportsData.Item> want = new ArrayList<>();
        for (int i = focus; i < Math.min(items.size(), focus + 4); i++) {
            SportsData.Item it = items.get(i);
            if (!it.over && !channels.containsKey(it.key)) want.add(it);
        }
        if (want.isEmpty()) return;
        Channel playing = tv.live.channel();
        Exec.io(() -> {
            Map<String, List<Matching.Row>> found = new HashMap<>();
            for (SportsData.Item it : want) {
                long when = it.live ? System.currentTimeMillis() : Math.max(System.currentTimeMillis(), it.start + 60_000L);
                found.put(it.key, Matching.rows(tv, it.game, playing, when));
            }
            Exec.main(() -> {
                channels.putAll(found);
                hub.channelsKnown();
                restSoon();
            });
        });
    }

    /** The league the remote is on, its table and its clubs (their colours), read once (again when it failed). */
    private void askTable() {
        String slug = Espn.TABLES[league][0];
        List<String[]> had = tables.get(slug);
        if (had != null && !had.isEmpty() || asking.contains(slug) || Exec.inline) return;
        asking.add(slug);
        Exec.io(() -> {
            List<String[]> rows = Espn.table(slug);
            Map<String, String[]> byId = new HashMap<>();
            for (String[] t : Espn.teams(slug)) byId.put(t[Espn.C_ID], t);
            Exec.main(() -> {
                asking.remove(slug);
                tables.put(slug, rows);
                clubs.put(slug, byId);
                if (row >= rows.size()) row = 0;
                hub.invalidate();
                restSoon();
            });
        });
    }

    /** The drivers' championship and the next Grand Prix, read when the section opens (kept ten minutes). */
    private void askF1() {
        if (f1Asking || Exec.inline || !drivers.isEmpty() && System.currentTimeMillis() - f1At < 10 * 60_000L) return;
        f1Asking = true;
        Exec.io(() -> {
            List<F1.Standing> got = F1.drivers();
            F1.Weekend next = F1.next();
            Exec.main(() -> {
                f1Asking = false;
                if (!got.isEmpty()) {
                    drivers = new ArrayList<>(got);
                    f1At = System.currentTimeMillis();
                }
                weekend = next;
                if (driver >= drivers.size()) driver = 0;
                hub.invalidate();
                restSoon();
            });
        });
    }

    /** Once the remote rests: the page's picture behind for what it is on, and a driver's season (his podiums). */
    private void restSoon() {
        h.removeCallbacks(rested);
        if (Exec.inline) rested();
        else h.postDelayed(rested, REST_MS);
    }

    private void rested() {
        if (hub == null) return;
        hub.backdrop();
        if (section == FORMULA && driver >= 0 && driver < drivers.size() && !Exec.inline) {
            F1.Standing st = drivers.get(driver);
            if (!st.driverId.isEmpty() && F1.keptSeason(st.driverId) == null) {
                Exec.io(() -> {
                    F1.season(st.driverId);
                    Exec.main(hub::invalidate);
                });
            }
        }
    }

    SportsData.Item chosen() {
        return focus >= 0 && focus < items.size() ? items.get(focus) : null;
    }

    /** A football game with its clubs known: its sides can be opened. */
    static boolean football(SportsData.Item it) {
        return it != null && SportsData.FOOTBALL.equals(it.kind) && it.game != null && it.game.homeId != null && it.game.awayId != null
                && !it.game.homeId.isEmpty() && !it.game.awayId.isEmpty();
    }

    List<String[]> table() {
        List<String[]> t = tables.get(Espn.TABLES[league][0]);
        return t == null ? new ArrayList<>() : t;
    }

    /** The section's own place for the remote: where Down from the pills goes. */
    int home() {
        return section == TABLES ? LEAGUES : section == FORMULA ? DRIVERS : LIST;
    }

    /** A section picked: its lists read when they haven't been. */
    void choose(int s) {
        section = s;
        pill = s;
        if (s == TABLES) askTable();
        if (s == FORMULA) askF1();
        if (s == TOMORROW || s == YESTERDAY) askDay(s);
        if (listSection(s)) {
            items = listFor(s);
            focus = 0;
            if (zone == SIDES) zone = LIST;
            if (hub != null) hub.laidOut();
            askChannelsSoon();
        }
        restSoon();
    }

    /** Whether a section lists games (Live, Today, Tomorrow, Yesterday). */
    static boolean listSection(int s) {
        return s == LIVE || s == TODAY || s == TOMORROW || s == YESTERDAY;
    }

    /** The games a section shows now. */
    private List<SportsData.Item> listFor(int s) {
        if (s == LIVE) return SportsData.liveOf(today);
        if (s == TOMORROW || s == YESTERDAY) {
            List<SportsData.Item> l = days.get(s);
            return l == null ? new ArrayList<>() : new ArrayList<>(l);
        }
        return new ArrayList<>(today);
    }

    /** Tomorrow's or yesterday's football, read once (off the main thread). */
    private void askDay(int s) {
        if (days.containsKey(s) || dayAsking.contains(s) || Exec.inline) return;
        dayAsking.add(s);
        Exec.io(() -> {
            List<SportsData.Item> got = SportsData.footballOn(s == TOMORROW ? 1 : -1, 10 * 60_000L);
            Exec.main(() -> {
                dayAsking.remove(s);
                days.put(s, got);
                if (section == s) choose(s);
            });
        });
    }

    /** Pure: the line under the title for a section of games: still being read, none, or what they are. */
    static String listWords(int s, boolean reading, boolean none, String todayWords) {
        switch (s) {
            case LIVE:
                return reading ? "Today's matches come up here once they are read"
                        : none ? "No football is live right now. Today's games are under Today." : "Live now";
            case TOMORROW:
                return reading ? "Finding tomorrow's games…" : none ? "No football tomorrow in the competitions followed." : "Tomorrow's matches";
            case YESTERDAY:
                return reading ? "Finding yesterday's games…" : none ? "No football yesterday in the competitions followed."
                        : "Yesterday's matches";
            default:
                return reading ? "Today's matches come up here once they are read"
                        : none ? "No football today in the competitions followed." : todayWords;
        }
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        boolean used;
        switch (zone) {
            case PILLS:
                used = pillsKey(code);
                break;
            case SIDES:
                used = sidesKey(code);
                break;
            case LEAGUES:
                used = leaguesKey(code);
                break;
            case TABLE:
                used = tableKey(code);
                break;
            case DRIVERS:
                used = driversKey(code);
                break;
            default:
                used = listKey(code);
                break;
        }
        if (used) {
            hub.invalidate();
            restSoon();
        }
        return used;
    }

    private boolean pillsKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (pill > 0) choose(pill - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (pill < SECTIONS.length - 1) choose(pill + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                zone = home();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                toTabs();
                return true;
            default:
                return false;
        }
    }

    private boolean listKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus > 0) focus--;
                else up();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < items.size() - 1) focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (football(chosen())) {
                    zone = SIDES;
                    side = 0;
                }
                return true;
            default:
                return false;
        }
        askChannelsSoon();
        return true;
    }

    private void up() {
        zone = PILLS;
        pill = section;
    }

    private boolean sidesKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (side == 1) side = 0;
                else zone = LIST;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                side = 1;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                zone = LIST;
                return listKey(code);
            default:
                return false;
        }
    }

    private boolean leaguesKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (league > 0) {
                    league--;
                    row = 0;
                    askTable();
                } else {
                    up();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (league < Espn.TABLES.length - 1) {
                    league++;
                    row = 0;
                    askTable();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!table().isEmpty()) zone = TABLE;
                return true;
            default:
                return false;
        }
    }

    private boolean tableKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) row--;
                else up();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < table().size() - 1) row++;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                zone = LEAGUES;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private boolean driversKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (driver > 0) driver--;
                else up();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (driver < drivers.size() - 1) driver++;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    @Override void ok() {
        switch (zone) {
            case PILLS:
                choose(pill);
                zone = home();
                break;
            case SIDES: {
                SportsData.Item it = chosen();
                if (football(it)) tv.push(new TeamScreen(tv, Squad.Club.of(it.game, side == 1)));
                return;
            }
            case LEAGUES:
                if (!table().isEmpty()) zone = TABLE;
                break;
            case TABLE: {
                List<String[]> t = table();
                if (row >= 0 && row < t.size()) {
                    String slug = Espn.TABLES[league][0];
                    Map<String, String[]> known = clubs.get(slug);
                    String[] r = t.get(row);
                    tv.push(new TeamScreen(tv, Squad.Club.of(slug, r, known == null ? null : known.get(r[Espn.T_ID]))));
                }
                return;
            }
            case DRIVERS:
                if (driver >= 0 && driver < drivers.size()) tv.push(new DriverScreen(tv, new ArrayList<>(drivers), driver, weekend));
                return;
            default: {
                SportsData.Item it = chosen();
                if (it != null) tv.push(new GameScreen(tv, it, channels.get(it.key)));
                return;
            }
        }
        hub.invalidate();
    }

    @Override boolean back() {
        switch (zone) {
            case PILLS:
                zone = home();
                break;
            case SIDES:
                zone = LIST;
                break;
            case TABLE:
                zone = LEAGUES;
                break;
            default:
                // At a section's root: the remote up to the tab bar (Back there goes to Home's tab).
                toTabs();
                break;
        }
        hub.invalidate();
        return true;
    }

    // ---- pure helpers -----------------------------------------------------------------------------------------------

    /** Pure: whether two sides' colours are too close to tell apart (07's colour-clash check: RGB distance under 110). */
    static boolean clash(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db) < 110;
    }

    /** Pure: a table's zone mark from ESPN's note on the place: blue for the top's, orange for a play-off's, red for the drop. */
    static int zone(String note) {
        String n = note == null ? "" : note.toLowerCase(Locale.UK);
        if (n.isEmpty()) return 0;
        if (n.contains("relegat")) return 0xFFFF453A;
        if (n.contains("playoff") || n.contains("play-off") || n.contains("play off") || n.contains("qualif") || n.contains("europa")
                || n.contains("conference") || n.contains("knockout")) return 0xFFFF8A3D;
        return 0xFF3F8CFF;
    }

    /** Pure: a competition as a row's small capitals, in at most two lines ("UEFA Nations League": NATIONS / LEAGUE). */
    static String[] tag(String comp) {
        String c = comp == null ? "" : comp.trim().toUpperCase(Locale.UK);
        if (c.contains("FRIENDL")) return new String[]{"FRIENDLY"};
        for (String lead : new String[]{"UEFA ", "FIFA ", "ENGLISH ", "SPANISH ", "ITALIAN ", "GERMAN ", "FRENCH ", "SCOTTISH "}) {
            if (c.startsWith(lead) && c.length() > lead.length()) c = c.substring(lead.length());
        }
        int sp = c.lastIndexOf(' ');
        if (c.length() <= 9 || sp < 0) return new String[]{c};
        // Two lines, broken at the space nearest the middle.
        int best = sp, mid = c.length() / 2;
        for (int i = c.indexOf(' '); i >= 0; i = c.indexOf(' ', i + 1)) if (Math.abs(i - mid) < Math.abs(best - mid)) best = i;
        return new String[]{c.substring(0, best), c.substring(best + 1)};
    }

    /** Pure: "#00359C", "00359c" or "" as a colour (0 for none). */
    static int colour(String hex) {
        return Squad.colour(hex);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** A row's look on Today, worked out when the list is read or the channels come (never each frame). */
    private static final class RowLook {
        String[] cmp;
        String home = "", away = "", kickOff = "", clock = "", homeGoals = "", awayGoals = "";
        boolean scored, live, over, coloured;
        int homeC, awayC;
        final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }

    /** A driver's row: his words and picture addresses (worked out once), his round picture's shader (made when it comes). */
    private static final class DriverLook {
        String sub = "", initials = "", photo, cardPhoto;
        Bitmap made;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    /** A table row's words as drawn (the name cut to fit, the goal difference signed), worked out once. */
    private static final class TableLook {
        String name = "", gd = "";
    }

    /** The sections and the lists, drawn in design pixels (1920 x 1080). */
    private final class Hub extends View {
        /** Today's list and the big card beside it (the mock-ups' .sh-list, .sh-big). */
        private static final float LIST_X = 90f, LIST_W = 960f, LIST_TOP = 248f, ROW = 76f, ROW_GAP = 8f, HEAD = 35.4f, HEAD_GAP = 8f;
        private static final float CARD_X = 1090f, CARD_W = 740f, CARD_TOP = 248f, CARD_H = 690f, CARD_TOP_H = 360f;
        private final Ground ground = new Ground(this);
        private final ClubLook.Pills pills = new ClubLook.Pills(SECTIONS, 48f, 19f, 20f, 0.06f);
        private final Frost big = new Frost(Look07.MAT_DARK);
        private final Lift07 rowLift = new Lift07(), leagueLift = new Lift07(), tableLift = new Lift07(), driverLift = new Lift07();
        private final Glide listScroll = new Glide(Look07.SCROLL_MS), tableScroll = new Glide(Look07.SCROLL_MS),
                driverScroll = new Glide(Look07.SCROLL_MS);
        private final TextPaint titleP = Type.paint(Type.H2, Type.BOLD, Look07.L1), subP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2),
                headP = Type.label(16f, Look07.L3), cmpP = Type.paint(12f, Type.EXTRABOLD, Look07.L2), sideP = Type.paint(22f, Type.SEMIBOLD, Look07.L1),
                goalsP = Cine.text(30f, Cine.condensed(), 0xFFFFFFFF), koP = Type.paint(21f, Type.BOLD, Look07.L1),
                clockP = Type.paint(18f, Type.BOLD, 0xFFFFB3AE), compBigP = Type.paint(16f, Type.EXTRABOLD, Look07.L1),
                teamBigP = Type.paint(24f, Type.BOLD, Look07.L1), scoreBigP = Cine.text(112f, Cine.condensed(), Look07.L1),
                clockBigP = Type.paint(22f, Type.BOLD, Look07.L1), evP = Type.paint(19f, Type.MEDIUM, Look07.L2),
                evNameP = Type.paint(19f, Type.SEMIBOLD, Look07.L1), lblP = Type.label(15f, Look07.L3),
                btnP = Type.paint(21f, Type.SEMIBOLD, Look07.L1), stepP = Type.paint(18f, Type.MEDIUM, Look07.L2),
                hintP = Type.paint(17f, Type.MEDIUM, Look07.L3), noteP = Type.paint(19f, Type.MEDIUM, Look07.L3),
                leagueP = Type.paint(21f, Type.MEDIUM, Look07.L2), tHeadP = Type.label(15f, Look07.L3),
                cellP = Type.clock(21f, Type.REGULAR, Look07.L2), rankP = Type.clock(21f, Type.REGULAR, Look07.L1),
                clubP = Type.paint(21f, Type.SEMIBOLD, Look07.L1), ptsP = Type.clock(21f, Type.EXTRABOLD, Look07.L1),
                posP = Cine.text(26f, Cine.condensed(), Look07.L1), driverP = Type.paint(23f, Type.SEMIBOLD, Look07.L1),
                driverSubP = Type.paint(17f, Type.MEDIUM, Look07.L3), pointsP = Cine.text(32f, Cine.condensed(), Look07.L1),
                ptsUnitP = Type.paint(14f, Type.BOLD, Look07.L3), bigNumP = Cine.text(120f, Cine.condensed(), 0xE6FFFFFF),
                bigNameP = Type.paint(36f, Type.BOLD, Look07.L1), sideSubP = Type.paint(21f, Type.MEDIUM, Look07.L2),
                standP = Type.paint(26f, Type.BOLD, Look07.L1), gpP = Type.paint(32f, Type.BOLD, Look07.L1),
                gpSubP = Type.paint(19f, Type.MEDIUM, Look07.L2), initialsP = Type.paint(24f, Type.BOLD, Look07.L1);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), homeTop = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                awayTop = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), stripes = new Paint(Paint.ANTI_ALIAS_FLAG),
                shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                f1Card = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final RectF r = new RectF(), box = new RectF();
        private final Path topShape = new Path(), awayShape = new Path(), card = new Path();
        private final Matrix am = new Matrix();
        private final Runnable redraw = this::invalidate;
        /** Each row's place on Today (design px from the list's top) and each section's label: {y, first item, count}. */
        private float[] rowY = new float[0];
        private final List<float[]> heads = new ArrayList<>();
        private final Map<SportsData.Item, RowLook> looks = new IdentityHashMap<>();
        private final Map<F1.Standing, DriverLook> driverLooks = new IdentityHashMap<>();
        private final Map<String[], TableLook> tableLooks = new IdentityHashMap<>();
        private final List<String> headWords = new ArrayList<>();
        /** The big card's game and what it was made for (its colours and words). */
        private SportsData.Item bigFor;
        private int bigGoals = -1, bigReds = -1;
        private String bigComp = "", bigHome = "", bigAway = "", bigScore = "", bigClock = "", homeScorers = "", awayScorers = "",
                homeReds = "", awayReds = "", stepWords = "";
        private final List<String> buttons = new ArrayList<>();
        private boolean bigRed;
        private String todayWords = "", hintWords = "", homeEvents = "", awayEvents = "", homeRedEvents = "", awayRedEvents = "";
        private long todayMinute = -1;
        private int hintZone = -1, hintSide = -1;
        private SportsData.Item hintFor;
        private float liveW;
        /** The remote is up on the tab bar (this frame). */
        private boolean onBar;
        private String cardGiven = "", cardFamily = "", cardTeam = "";
        private int f1For = 1;
        private String tableWords = "", f1Words = "", standing = "", gpDays = "", gpHead = "", gpName = "", winsWords = "";
        private int wordsLeague = -1, wordsSeason = -1;
        private String f1Round = "";
        private F1.Standing wordsFor;
        private F1.Weekend wordsWeekend;
        private F1.Season seasonFor;

        Hub() {
            super(tv);
            cmpP.setLetterSpacing(0.08f);
            compBigP.setLetterSpacing(0.08f);
            tHeadP.setLetterSpacing(0.08f);
            bigNumP.setTextSkewX(-0.22f);
            pointsP.setLetterSpacing(0f);
            bigNameP.setLetterSpacing(-0.02f);
            scoreBigP.setShadowLayer(Ui.f(20f), 0, Ui.f(6f), 0x73000000);
            dot.setColor(Look07.RED);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFFFFFFFF);
            Shadows.prepare(Look07.SH_ROW, Look07.R_CARD);
            Shadows.prepare(Look07.SH_GLASS, Look07.R_PANEL);
        }

        private float x(float d) {
            return Ui.f(d);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            pills.from(x(Look07.MARGIN) + titleP.measureText("Sports") + x(28f), x(18f), 0, SECTIONS.length - 1);
            // The big card's top: its own top corners rounded, square at its foot; the away side's part of it.
            float cw = x(CARD_W), th = x(CARD_TOP_H), rad = x(44f);
            topShape.reset();
            topShape.addRoundRect(new RectF(0, 0, cw, th), new float[]{rad, rad, rad, rad, 0, 0, 0, 0}, Path.Direction.CW);
            Path poly = new Path();
            poly.moveTo(cw * 0.56f, 0);
            poly.lineTo(cw, 0);
            poly.lineTo(cw, th);
            poly.lineTo(cw * 0.44f, th);
            poly.close();
            awayShape.reset();
            awayShape.op(topShape, poly, Path.Op.INTERSECT);
            // The fine stripes at 115 degrees (white 5 % 16 px, clear 16 px) and the shade falling down the top.
            float a = (float) Math.toRadians(115), len = x(32f);
            stripes.setShader(new LinearGradient(0, 0, (float) Math.sin(a) * len, (float) -Math.cos(a) * len,
                    new int[]{0x0DFFFFFF, 0x0DFFFFFF, 0x00FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 0.5f, 1f}, Shader.TileMode.REPEAT));
            shade.setShader(new LinearGradient(0, 0, 0, th, 0x1A000000, 0x73000000, Shader.TileMode.CLAMP));
            card.reset();
            card.addRoundRect(new RectF(0, 0, cw, x(792f)), rad, rad, Path.Direction.CW);
            TextPaint liveP = Type.paint(16f, Type.BOLD, Look07.L1);
            liveP.setLetterSpacing(0.06f);
            liveW = x(38f) + liveP.measureText("LIVE");
            tableLooks.clear();
            driverLooks.clear();
            wordsFor = null;
            bigFor = null;
            f1For = 1;
            looks.clear();
            laidOut();
        }

        /** Today's places worked out again (a new list): each section's label and each row, and each row's look. */
        void laidOut() {
            heads.clear();
            headWords.clear();
            rowY = new float[items.size()];
            float y = 0;
            String last = "";
            for (int i = 0; i < items.size(); i++) {
                String s = sectionOf(items.get(i));
                if (!s.equals(last)) {
                    if (!last.isEmpty()) y += HEAD_GAP;
                    heads.add(new float[]{y, i, 0});
                    y += HEAD;
                    last = s;
                }
                heads.get(heads.size() - 1)[2]++;
                rowY[i] = y;
                y += ROW + ROW_GAP;
            }
            for (float[] hd : heads) headWords.add(sectionOf(items.get((int) hd[1])) + " · " + (int) hd[2]);
            looks.clear();
            for (SportsData.Item it : items) looks.put(it, look(it));
            bigFor = null;
            invalidate();
        }

        void channelsKnown() {
            bigFor = null;
            invalidate();
        }

        private String sectionOf(SportsData.Item it) {
            return it.live ? "LIVE NOW" : it.over ? "FINISHED" : "LATER";
        }

        /** A row's look: its capitals, names cut to fit, score or kick-off, clock, and its sides' colours and wash. */
        private RowLook look(SportsData.Item it) {
            RowLook l = new RowLook();
            l.cmp = tag(it.comp);
            for (int i = 0; i < l.cmp.length; i++) l.cmp[i] = Ui.fit(l.cmp[i], cmpP, x(100f));
            l.home = Ui.fit(it.home, sideP, x(260f));
            l.away = Ui.fit(it.away, sideP, x(260f));
            l.live = it.live;
            l.over = it.over;
            int dash = it.score.indexOf('–');
            if (dash < 0) dash = it.score.indexOf('-');
            l.scored = !it.score.isEmpty() && dash > 0;
            if (l.scored) {
                l.homeGoals = it.score.substring(0, dash).trim();
                l.awayGoals = it.score.substring(dash + 1).trim();
            }
            l.kickOff = it.score.isEmpty() ? Ui.clock(it.start) : it.score;
            l.clock = it.live ? (it.status.isEmpty() ? "LIVE" : it.status) : it.over ? (it.status.isEmpty() ? "FT" : it.status) : "";
            Game g = it.game;
            int hc = g == null ? 0 : colour(g.homeColor), ac = g == null ? 0 : colour(g.awayColor);
            if (hc != 0 && ac != 0 && clash(hc, ac)) {
                int alt = g == null ? 0 : colour(g.awayAlt);
                if (alt != 0 && !clash(hc, alt)) ac = alt;
            }
            l.coloured = hc != 0 && ac != 0;
            l.homeC = hc != 0 ? hc : 0x33FFFFFF;
            l.awayC = ac != 0 ? ac : 0x33FFFFFF;
            if (l.coloured) {
                l.wash.setShader(new LinearGradient(0, 0, x(LIST_W), 0, new int[]{hc, hc & 0x00FFFFFF, ac & 0x00FFFFFF, ac},
                        new float[]{0.08f, 0.42f, 0.58f, 0.92f}, Shader.TileMode.CLAMP));
            }
            return l;
        }

        private Bitmap pic(String url, float size) {
            int px = Math.max(1, Math.round(x(size)));
            return Pics.get(url, px, px, Pics.FIT, redraw);
        }

        private void image(Canvas c, String url, float cx, float cy, float size, int alpha) {
            if (url == null || url.isEmpty()) return;
            Bitmap b = pic(url, size);
            if (b == null) return;
            bmp.setAlpha(alpha);
            c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, bmp);
        }

        /** The page's picture behind, for what the remote rests on (never each frame). */
        void backdrop() {
            String url = "";
            if (listSection(section)) {
                SportsData.Item it = chosen();
                if (it != null) {
                    url = programme(it);
                    if (url == null) return;
                    if (url.isEmpty()) url = it.homeLogo;
                }
            } else if (section == TABLES) {
                List<String[]> t = table();
                if (!t.isEmpty()) url = t.get(Math.max(0, Math.min(t.size() - 1, row)))[Espn.T_LOGO];
            } else if (driver >= 0 && driver < drivers.size()) {
                F1.Standing st = drivers.get(driver);
                String[] drawn = previewPictures.get(st);
                String car = drawn != null ? drawn[1] : F1.car(st.teamId, st.season, Math.max(400, Math.round(x(1000f) / 40f) * 40));
                url = car == null ? "" : car;
            }
            ground.set(url);
        }

        /** The picture of the programme on the best channel showing the game, "" for none, null while it is asked for. */
        private String programme(SportsData.Item it) {
            List<Matching.Row> rows = channels.get(it.key);
            if (rows == null || rows.isEmpty() || rows.get(0).ch == null) return "";
            Channel ch = rows.get(0).ch;
            Guide.Schedule s = Library.get(tv).schedule(ch.key);
            int at = s == null ? -1 : s.at(System.currentTimeMillis());
            if (at < 0) return "";
            return ProgrammePictures.get(tv, ch, s, at, SportsScreen.this::restSoon);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = ground.draw(c, getWidth(), getHeight());
            // The title, the sections' pills beside it, the line under them.
            c.drawText("Sports", x(Look07.MARGIN), x(176.6f), titleP);
            // While the remote is up on the tab bar, nothing on the page is lit (its section stays chosen).
            onBar = tv.tabs() != null && tv.tabs().active();
            for (int i = 0; i < SECTIONS.length; i++) pills.draw(c, i, x(160f), i == section, !onBar && zone == PILLS && i == pill, 1f);
            if (section == TABLES) moving |= drawTables(c);
            else if (section == FORMULA) moving |= drawDrivers(c);
            else moving |= drawToday(c);
            if (moving) postInvalidateOnAnimation();
        }

        private void subtitle(Canvas c, String words) {
            c.drawText(words, x(Look07.MARGIN), x(218.3f), subP);
        }

        // ---- today ----

        private boolean drawToday(Canvas c) {
            long minute = System.currentTimeMillis() / 60_000L;
            if (minute != todayMinute) {
                todayMinute = minute;
                todayWords = "Live now and today · " + Ui.clock(System.currentTimeMillis());
            }
            boolean reading = section == TOMORROW || section == YESTERDAY ? !days.containsKey(section) : loading;
            subtitle(c, listWords(section, reading, items.isEmpty(), todayWords));
            SportsData.Item on = !onBar && (zone == LIST || zone == SIDES) ? chosen() : null;
            boolean moving = rowLift.step(on);
            if (items.isEmpty() || rowY.length != items.size()) return moving;
            // Kept in sight round the remote.
            float fy = focus < rowY.length ? rowY[focus] : 0f;
            listScroll.to(Math.max(0f, fy - 330f));
            moving |= listScroll.step();
            float top = LIST_TOP - listScroll.value();
            c.save();
            c.clipRect(0, x(LIST_TOP - 18f), x(LIST_X + LIST_W + 30f), getHeight());
            for (int hi = 0; hi < heads.size(); hi++) {
                float[] hd = heads.get(hi);
                float y = top + hd[0];
                if (y > 1090f || y < LIST_TOP - 60f) continue;
                SportsData.Item first = items.get((int) hd[1]);
                String words = headWords.get(hi);
                float cy = x(y + 6f + 9.7f), tx = x(LIST_X + 10f);
                if (first.live) {
                    dot.setColor(Look07.RED);
                    dot.setAlpha(0x38);
                    c.drawCircle(tx + x(4.5f), cy, x(7f), dot);
                    dot.setAlpha(255);
                    c.drawCircle(tx + x(4.5f), cy, x(4.5f), dot);
                    tx += x(19f);
                }
                c.drawText(words, tx, Type.centred(headP, cy), headP);
            }
            // The lifted row last, over its neighbours.
            int lifted = -1;
            for (int i = 0; i < items.size(); i++) {
                float y = top + rowY[i];
                if (y > 1090f || y < LIST_TOP - ROW - 20f) continue;
                if (items.get(i) == rowLift.on()) {
                    lifted = i;
                    continue;
                }
                row(c, items.get(i), y);
            }
            if (lifted >= 0) row(c, items.get(lifted), top + rowY[lifted]);
            c.restore();
            SportsData.Item it = chosen();
            if (it != null) bigCard(c, it);
            return moving;
        }

        private void row(Canvas c, SportsData.Item it, float y) {
            RowLook l = looks.get(it);
            if (l == null) {
                l = look(it);
                looks.put(it, l);
            }
            float k = Math.max(0f, Math.min(1f, rowLift.of(it))), up = rowLift.of(it);
            float w = x(LIST_W), hh = x(ROW), grow = 1f + 0.02f * up;
            c.save();
            c.translate(x(LIST_X) + w / 2f, x(y) + hh / 2f);
            c.scale(grow, grow);
            c.translate(-w / 2f, -hh / 2f);
            r.set(0, 0, w, hh);
            float rad = x(22f);
            if (k > 0.01f) Shadows.draw(c, r, rad, Look07.SH_ROW, k);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(Math.round(0x12 + (0xF2 - 0x12) * k));
            c.drawRoundRect(r, rad, rad, fill);
            if (l.coloured) {
                l.wash.setAlpha(Math.round(255 * (0.28f - 0.14f * k)));
                c.drawRoundRect(r, rad, rad, l.wash);
            }
            if (k < 0.99f) {
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(Math.max(1f, x(1f)));
                line.setColor(0xFFFFFFFF);
                line.setAlpha(Math.round(0x12 * (1f - k)));
                c.drawRoundRect(r, rad, rad, line);
                line.setStyle(Paint.Style.FILL);
            }
            float cy = hh / 2f;
            int ink = Theme.mix(0xFFFFFFFF, Look07.INK, k);
            // The competition in small capitals.
            cmpP.setColor(Theme.mix(Look07.L2 | 0xFF000000, 0xFF000000, k));
            cmpP.setAlpha(Math.round(0xBD + (0x99 - 0xBD) * k));
            if (l.cmp.length == 1) {
                c.drawText(l.cmp[0], x(18f), Type.centred(cmpP, cy), cmpP);
            } else {
                c.drawText(l.cmp[0], x(18f), Type.centred(cmpP, cy - x(7.8f)), cmpP);
                c.drawText(l.cmp[1], x(18f), Type.centred(cmpP, cy + x(7.8f)), cmpP);
            }
            // The sides with their crests.
            image(c, it.homeLogo, x(137f), cy, 34f, 255);
            sideP.setColor(ink);
            c.drawText(l.home, x(166f), Type.centred(sideP, cy), sideP);
            image(c, it.awayLogo, x(857f), cy, 34f, 255);
            c.drawText(l.away, x(828f) - sideP.measureText(l.away), Type.centred(sideP, cy), sideP);
            // The score in the sides' colours, or the kick-off.
            if (l.scored) {
                float bx = x(453f);
                bx = goalBox(c, bx, cy, l.homeGoals, l.homeC) + x(4f);
                goalBox(c, bx, cy, l.awayGoals, l.awayC);
            } else {
                float kw = Math.max(x(88f), koP.measureText(l.kickOff) + x(24f));
                r.set(x(497f) - kw / 2f, cy - x(23f), x(497f) + kw / 2f, cy + x(23f));
                fill.setColor(k > 0.5f ? 0x1A000000 : 0x1FFFFFFF);
                c.drawRoundRect(r, x(11f), x(11f), fill);
                koP.setColor(ink);
                c.drawText(l.kickOff, r.centerX() - koP.measureText(l.kickOff) / 2f, Type.centred(koP, cy), koP);
            }
            if (!l.clock.isEmpty()) {
                clockP.setColor(l.live ? Theme.mix(0xFFFFB3AE, 0xFFC8231B, k) : Theme.mix(Look07.L3 | 0xFF000000, 0xFF8A8A8E, k));
                clockP.setAlpha(l.live ? 255 : Math.round(0x85 + (0xFF - 0x85) * k));
                c.drawText(l.clock, x(944f) - clockP.measureText(l.clock), Type.centred(clockP, cy), clockP);
            }
            c.restore();
        }

        /** A side's goals in a box of its colour (Barlow Condensed, dark figures on a light colour); its right edge. */
        private float goalBox(Canvas c, float left, float cy, String goals, int colour) {
            float bw = Math.max(x(42f), goalsP.measureText(goals) + x(16f));
            r.set(left, cy - x(23f), left + bw, cy + x(23f));
            fill.setColor(colour);
            c.drawRoundRect(r, x(11f), x(11f), fill);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1f, x(1f)));
            line.setColor(0x38FFFFFF);
            c.drawRoundRect(r, x(11f), x(11f), line);
            line.setStyle(Paint.Style.FILL);
            goalsP.setColor(ClubLook.luminance(colour) > 0.45 && (colour >>> 24) == 0xFF ? 0xFF111111 : 0xFFFFFFFF);
            c.drawText(goals, r.centerX() - goalsP.measureText(goals) / 2f, Cine.mid(goalsP, cy), goalsP);
            return r.right;
        }

        /** The big card's words for its game, worked out when it changes (the remote moved, a goal, the channels). */
        private void bigWords(SportsData.Item it) {
            Game g = it.game;
            int goals = g == null ? 0 : g.goals.size(), reds = g == null ? 0 : g.reds.size();
            if (bigFor == it && goals == bigGoals && reds == bigReds) return;
            bigFor = it;
            bigGoals = goals;
            bigReds = reds;
            RowLook l = looks.get(it);
            if (l == null) l = look(it);
            bigComp = Ui.fit(it.comp.toUpperCase(Locale.UK), compBigP, x(CARD_W - 200f));
            bigHome = Ui.fit(it.home, teamBigP, x(190f));
            bigAway = Ui.fit(it.away, teamBigP, x(190f));
            bigScore = l.scored ? l.homeGoals + "–" + l.awayGoals : l.kickOff;
            bigClock = it.live ? (it.status.isEmpty() ? "Live" : it.status) : it.over ? "Full time" : Ui.day(it.start, System.currentTimeMillis());
            bigRed = reds > 0;
            homeScorers = scorers(g, "home", g == null ? null : g.goals);
            awayScorers = scorers(g, "away", g == null ? null : g.goals);
            homeReds = scorers(g, "home", g == null ? null : g.reds);
            awayReds = scorers(g, "away", g == null ? null : g.reds);
            float half = x(CARD_W - 64f) / 2f - x(10f);
            homeEvents = Ui.fit(homeScorers, evNameP, half);
            awayEvents = Ui.fit(awayScorers, evNameP, half);
            homeRedEvents = Ui.fit(homeReds, evNameP, half - x(20f));
            awayRedEvents = Ui.fit(awayReds, evNameP, half - x(20f));
            hintFor = null;
            // The family's channels for it, English first, as buttons ("102 BBC Two · Match of the Day Wales").
            buttons.clear();
            stepWords = "";
            List<Matching.Row> rows = channels.get(it.key);
            String watched = InStep.watched(System.currentTimeMillis());
            Library lib = Library.get(tv);
            for (int i = 0; rows != null && i < rows.size() && buttons.size() < 2; i++) {
                Matching.Row row = rows.get(i);
                int n = row.ch == null ? 0 : lib.number(row.ch);
                String words = (n > 0 ? n + " " : "") + row.name + (row.uhd ? " 4K" : "") + (row.under.isEmpty() ? "" : " · " + row.under);
                buttons.add(Ui.fit(words, btnP, x(CARD_W - 64f - 18f - 64f - 14f - 24f)));
                if (row.ch != null && row.ch.key.equals(watched) && stepWords.isEmpty()) {
                    int delay = InStep.delay(tv, row.ch.key);
                    stepWords = "In step with " + (n > 0 ? String.valueOf(n) : row.name) + " · " + (delay <= 0 ? "no delay" : "held " + delay + " s behind live");
                }
            }
        }

        /** "Brooks 13', Thomas 40'": one side's goals (or red cards), as ESPN gives them so far. */
        private String scorers(Game g, String side, List<String[]> events) {
            if (g == null || events == null) return "";
            StringBuilder b = new StringBuilder();
            for (String[] e : events) {
                if (e == null || e.length < 4 || !side.equals(e[3])) continue;
                String who = e[1] == null ? "" : e[1].trim();
                int sp = who.lastIndexOf(' ');
                if (sp > 0) who = who.substring(sp + 1);
                if (b.length() > 0) b.append(", ");
                b.append(who).append(' ').append(e[2] == null ? "" : e[2].trim());
            }
            return b.toString();
        }

        /** The game the remote is on, big: its two sides' colours, the score, the scorers, its channels, what OK does. */
        private void bigCard(Canvas c, SportsData.Item it) {
            bigWords(it);
            RowLook l = looks.get(it);
            float left = x(CARD_X), top = x(CARD_TOP), bottom = CARD_TOP + CARD_H + Math.max(0, buttons.size() - 1) * 72f;
            box.set(left, top, left + x(CARD_W), x(bottom));
            big.draw(c, box, x(44f), 1f);
            c.save();
            c.translate(left, top);
            // The top in the two sides' colours, split on the slant, a fine stripe over it and a shade falling down it.
            int hc = l != null && l.coloured ? l.homeC : 0xFF3A3F4F, ac = l != null && l.coloured ? l.awayC : 0xFF262A36;
            homeTop.setColor(hc);
            awayTop.setColor(ac);
            c.drawPath(topShape, homeTop);
            c.drawPath(awayShape, awayTop);
            c.drawPath(topShape, stripes);
            c.drawPath(topShape, shade);
            // The competition and LIVE.
            c.drawText(bigComp, x(28f), Type.centred(compBigP, x(40f)), compBigP);
            if (it.live) Pills.live(c, x(CARD_W - 28f) - liveW, x(40f));
            // The two sides: crests and names; the one the remote is on ringed (Right from the list).
            for (int s = 0; s < 2; s++) {
                float cx = s == 0 ? x(36f + 95f) : x(CARD_W - 36f - 95f);
                if (!onBar && zone == SIDES && side == s) {
                    r.set(cx - x(97f), x(86f), cx + x(97f), x(254f));
                    Shadows.draw(c, r, x(26f), Look07.SH_ROW, 1f);
                    fill.setColor(0x24FFFFFF);
                    c.drawRoundRect(r, x(26f), x(26f), fill);
                    ring.setStrokeWidth(x(3f));
                    r.inset(-x(1.5f), -x(1.5f));
                    c.drawRoundRect(r, x(27.5f), x(27.5f), ring);
                }
                image(c, s == 0 ? it.homeLogo : it.awayLogo, cx, x(152f), 100f, 255);
                String nm = s == 0 ? bigHome : bigAway;
                c.drawText(nm, cx - teamBigP.measureText(nm) / 2f, Type.centred(teamBigP, x(226f)), teamBigP);
            }
            // The score huge, the clock under it (a red card when one has been shown).
            float mid = x(CARD_W / 2f);
            c.drawText(bigScore, mid - scoreBigP.measureText(bigScore) / 2f, x(197f), scoreBigP);
            float cw = clockBigP.measureText(bigClock), rw = bigRed ? x(12f + 8f) : 0f;
            float cx0 = mid - (cw + rw) / 2f;
            if (bigRed) {
                r.set(cx0, x(225f), cx0 + x(12f), x(241f));
                fill.setColor(Look07.RED_CARD);
                c.drawRoundRect(r, x(2f), x(2f), fill);
            }
            c.drawText(bigClock, cx0 + rw, Type.centred(clockBigP, x(233f)), clockBigP);
            // The body: the scorers and red cards, the channels, in step, what OK does.
            float bx = x(32f), bw = x(CARD_W - 64f), y = 384f;
            boolean anyEv = !homeScorers.isEmpty() || !awayScorers.isEmpty() || !homeReds.isEmpty() || !awayReds.isEmpty();
            if (anyEv) {
                events(c, homeEvents, bx, x(y + 11.5f), false, false);
                events(c, awayEvents, bx + bw, x(y + 11.5f), true, false);
                if (!homeReds.isEmpty() || !awayReds.isEmpty()) {
                    y += 29f;
                    events(c, homeRedEvents, bx, x(y + 11.5f), false, true);
                    events(c, awayRedEvents, bx + bw, x(y + 11.5f), true, true);
                }
                y += 23f + 20f;
            }
            c.drawText("WATCH ON YOUR CHANNELS", bx, Type.centred(lblP, x(y + 9f)), lblP);
            y += 18.2f + 10f;
            List<Matching.Row> rows = channels.get(it.key);
            if (buttons.isEmpty()) {
                String w = rows == null ? (it.over ? "Finished" : "Looking for it in your channels…") : "Not found in your channels";
                c.drawText(w, bx, Type.centred(noteP, x(y + 20f)), noteP);
                y += 40f;
            }
            for (int i = 0; i < buttons.size(); i++) {
                r.set(bx, x(y), bx + bw, x(y + 62f));
                fill.setColor(0x29FFFFFF);
                c.drawRoundRect(r, x(31f), x(31f), fill);
                Matching.Row row = rows == null || i >= rows.size() ? null : rows.get(i);
                box.set(bx + x(18f), r.centerY() - x(20f), bx + x(18f + 64f), r.centerY() + x(20f));
                if (row != null && !Logos.draw(c, row.logo, box, redraw)) Logos.empty(c, box);
                c.drawText(buttons.get(i), bx + x(18f + 64f + 14f), Type.centred(btnP, r.centerY()), btnP);
                y += 62f + 10f;
            }
            y -= 10f;
            if (!stepWords.isEmpty()) {
                y += 16f;
                float cy = x(y + 11f);
                dot.setColor(Look07.GREEN);
                dot.setAlpha(0x55);
                c.drawCircle(bx + x(5f), cy, x(9f), dot);
                dot.setAlpha(255);
                c.drawCircle(bx + x(5f), cy, x(5f), dot);
                c.drawText(stepWords, bx + x(20f), Type.centred(stepP, cy), stepP);
                y += 22f;
            }
            int hz = zone == SIDES ? SIDES : LIST;
            if (hintFor != it || hintZone != hz || hintSide != side) {
                hintFor = it;
                hintZone = hz;
                hintSide = side;
                String hint = zone == SIDES ? "OK  Open " + (side == 0 ? it.home : it.away) + "'s page: the squad and the players"
                        : football(it) ? "OK  The game's page  ·  Right  Its clubs' pages" : "OK  Open the game's page, live in step with your channel";
                hintWords = Ui.fit(hint, hintP, bw);
            }
            c.drawText(hintWords, bx, Type.centred(hintP, x(y + 14f + 10.5f)), hintP);
            c.restore();
        }

        /** One side's goals or red cards on one line (fitted already): each name bold and its minute quiet (right: from x0 leftwards). */
        private void events(Canvas c, String fit, float x0, float cy, boolean right, boolean red) {
            if (fit.isEmpty()) return;
            float w = segments(c, fit, 0f, 0f, false) + (red ? x(20f) : 0f);
            float xx = right ? x0 - w : x0;
            if (red) {
                r.set(xx, cy - x(8f), xx + x(12f), cy + x(8f));
                fill.setColor(Look07.RED_CARD);
                c.drawRoundRect(r, x(2f), x(2f), fill);
                xx += x(20f);
            }
            segments(c, fit, xx, Type.centred(evNameP, cy), true);
        }

        /** The words split at each minute ("Brooks 13', Thomas 40'"): the names in evNameP, the minutes in evP; the width. */
        private float segments(Canvas c, String fit, float x0, float base, boolean draw) {
            float xx = x0;
            int start = 0, n = fit.length();
            for (int i = 0; i <= n; i++) {
                boolean end = i == n;
                if (!end && !(fit.charAt(i) == ' ' && i + 1 < n && Character.isDigit(fit.charAt(i + 1)))) continue;
                if (draw) c.drawText(fit, start, i, xx, base, evNameP);
                xx += evNameP.measureText(fit, start, i);
                if (end) break;
                int j = i + 1;
                // The minute and the ", " after it, quiet (one run: its kerning kept).
                while (j < n && fit.charAt(j) != ',') j++;
                if (j < n) j++;
                while (j < n && fit.charAt(j) == ' ') j++;
                if (draw) c.drawText(fit, i, j, xx, base, evP);
                xx += evP.measureText(fit, i, j);
                start = j;
                i = j - 1;
            }
            return xx - x0;
        }

        // ---- tables ----

        private static final float LEAGUE_X = 90f, LEAGUE_W = 330f, LEAGUE_ROW = 58f, TABLE_X = 450f, TABLE_W = 1380f,
                TABLE_ROW = 54f, TABLE_HEAD = 44f;
        private final String[] heads7 = {"P", "W", "D", "L", "GD", "PTS"};
        private final float[] cols = {1338f, 1418f, 1498f, 1578f, 1663f, 1753f};
        private final int[] cells = {Espn.T_PLAYED, Espn.T_WON, Espn.T_DRAWN, Espn.T_LOST, Espn.T_GD, Espn.T_POINTS};

        private boolean drawTables(Canvas c) {
            String slug = Espn.TABLES[league][0];
            List<String[]> t = tables.get(slug);
            if (wordsLeague != league) {
                wordsLeague = league;
                tableWords = Espn.TABLES[league][1] + " · the table as it stands";
            }
            subtitle(c, t == null ? "Reading the table…" : t.isEmpty() ? "No table just now" : tableWords);
            boolean moving = leagueLift.step(!onBar && zone == LEAGUES ? Espn.TABLES[league] : null);
            // The leagues down the left, on glass.
            box.set(x(LEAGUE_X), x(LIST_TOP), x(LEAGUE_X + LEAGUE_W), x(LIST_TOP + 32f + Espn.TABLES.length * LEAGUE_ROW));
            Frost.glass(c, box, x(Look07.R_PANEL), 1f);
            for (int i = 0; i < Espn.TABLES.length; i++) {
                float y = LIST_TOP + 16f + i * LEAGUE_ROW;
                float k = Math.max(0f, Math.min(1f, leagueLift.of(Espn.TABLES[i])));
                r.set(x(LEAGUE_X + 16f), x(y), x(LEAGUE_X + LEAGUE_W - 16f), x(y + LEAGUE_ROW));
                if (k > 0.01f) {
                    Shadows.draw(c, r, x(16f), Look07.SH_ROW, k);
                    fill.setColor(Look07.FOCUS_FILL);
                    fill.setAlpha(Math.round(0xF2 * k));
                    c.drawRoundRect(r, x(16f), x(16f), fill);
                } else if (i == league) {
                    fill.setColor(0x2EFFFFFF);
                    c.drawRoundRect(r, x(16f), x(16f), fill);
                }
                boolean sel = i == league;
                leagueP.setTypeface(Type.face(sel ? Type.SEMIBOLD : Type.MEDIUM));
                leagueP.setColor(k > 0.5f ? Look07.INK : sel ? Look07.L1 : Look07.L2);
                c.drawText(Espn.TABLES[i][1], x(LEAGUE_X + 32f), Type.centred(leagueP, r.centerY()), leagueP);
            }
            // The table on glass: its head, then a row a club, kept in sight round the remote.
            int n = t == null ? 0 : t.size();
            float rows = Math.min(n, 14);
            float panelB = LIST_TOP + 14f + TABLE_HEAD + Math.max(1f, rows) * TABLE_ROW + 14f;
            box.set(x(TABLE_X), x(LIST_TOP), x(TABLE_X + TABLE_W), x(Math.min(1076f, panelB)));
            Frost.glass(c, box, x(Look07.R_PANEL), 1f);
            float hy = LIST_TOP + 14f + TABLE_HEAD / 2f, inner = TABLE_X + 18f + 14f;
            c.drawText("#", x(inner), Type.centred(tHeadP, x(hy)), tHeadP);
            c.drawText("CLUB", x(inner + 112f), Type.centred(tHeadP, x(hy)), tHeadP);
            for (int i = 0; i < heads7.length; i++) c.drawText(heads7[i], x(cols[i]) - tHeadP.measureText(heads7[i]) / 2f, Type.centred(tHeadP, x(hy)), tHeadP);
            String[] on = !onBar && zone == TABLE && t != null && row >= 0 && row < n ? t.get(row) : null;
            moving |= tableLift.step(on);
            if (n == 0) return moving;
            float first = LIST_TOP + 14f + TABLE_HEAD, room = Math.min(1076f, panelB) - 14f - first;
            float want = Math.max(0f, row * TABLE_ROW - 5f * TABLE_ROW), most = Math.max(0f, n * TABLE_ROW - room);
            tableScroll.to(Math.min(want, most));
            moving |= tableScroll.step();
            c.save();
            c.clipRect(x(TABLE_X), x(first - 4f), x(TABLE_X + TABLE_W), x(first + room + 4f));
            float y0 = first - tableScroll.value();
            int liftedAt = -1;
            for (int i = 0; i < n; i++) {
                float y = y0 + i * TABLE_ROW;
                if (y < first - TABLE_ROW || y > first + room) continue;
                if (t.get(i) == on) {
                    liftedAt = i;
                    continue;
                }
                tableRow(c, t.get(i), y);
            }
            if (liftedAt >= 0) tableRow(c, t.get(liftedAt), y0 + liftedAt * TABLE_ROW);
            c.restore();
            return moving;
        }

        private void tableRow(Canvas c, String[] rr, float y) {
            float up = tableLift.of(rr), k = Math.max(0f, Math.min(1f, up));
            float left = TABLE_X + 18f, w = TABLE_W - 36f, grow = 1f + 0.015f * up;
            c.save();
            c.translate(x(left + w / 2f), x(y + TABLE_ROW / 2f));
            c.scale(grow, grow);
            c.translate(-x(w / 2f), -x(TABLE_ROW / 2f));
            float cy = x(TABLE_ROW / 2f);
            if (k > 0.01f) {
                r.set(0, 0, x(w), x(TABLE_ROW));
                Shadows.draw(c, r, x(16f), Look07.SH_ROW, k);
                fill.setColor(Look07.FOCUS_FILL);
                fill.setAlpha(Math.round(0xF2 * k));
                c.drawRoundRect(r, x(16f), x(16f), fill);
            }
            boolean dark = k > 0.5f;
            int zc = zone(rr.length > Espn.T_NOTE ? rr[Espn.T_NOTE] : "");
            if (zc != 0) {
                r.set(x(14f), cy - x(15f), x(19f), cy + x(15f));
                fill.setColor(zc);
                c.drawRoundRect(r, x(3f), x(3f), fill);
            }
            TableLook tl = tableLook(rr);
            rankP.setColor(dark ? Look07.INK : Look07.L1);
            c.drawText(rr[Espn.T_RANK], x(29f), Type.centred(rankP, cy), rankP);
            image(c, rr[Espn.T_LOGO], x(74f + 16f), cy, 32f, 255);
            clubP.setColor(dark ? Look07.INK : Look07.L1);
            c.drawText(tl.name, x(126f), Type.centred(clubP, cy), clubP);
            for (int i = 0; i < cells.length; i++) {
                String v = i == 4 ? tl.gd : rr[cells[i]];
                if (v == null) continue;
                TextPaint p = i == cells.length - 1 ? ptsP : cellP;
                p.setColor(i == cells.length - 1 ? (dark ? Look07.INK : Look07.L1) : dark ? 0x99000000 : Look07.L2);
                float cx = x(cols[i] - left);
                c.drawText(v, cx - p.measureText(v) / 2f, Type.centred(p, cy), p);
            }
            c.restore();
        }

        /** A table row's words, worked out the first time it is drawn: its name cut to fit, its goal difference signed. */
        private TableLook tableLook(String[] rr) {
            TableLook l = tableLooks.get(rr);
            if (l != null) return l;
            l = new TableLook();
            l.name = Ui.fit(rr[Espn.T_NAME], clubP, x(640f));
            String v = rr[Espn.T_GD] == null ? "" : rr[Espn.T_GD].trim();
            l.gd = !v.isEmpty() && !v.startsWith("-") && !v.startsWith("+") && !"0".equals(v) ? "+" + v : v;
            tableLooks.put(rr, l);
            return l;
        }

        // ---- Formula 1 ----

        private static final float DRIVER_ROW = 92f, DRIVER_GAP = 8f;

        private boolean drawDrivers(Canvas c) {
            int season = drivers.isEmpty() ? 0 : drivers.get(0).season;
            String round = drivers.isEmpty() ? "" : drivers.get(0).round;
            if (season != wordsSeason || !round.equals(f1Round)) {
                wordsSeason = season;
                f1Round = round;
                f1Words = (season > 0 ? "Drivers' championship · " + season : "Drivers' championship")
                        + (round.isEmpty() ? "" : " · after " + round + ("1".equals(round) ? " round" : " rounds"));
            }
            subtitle(c, drivers.isEmpty() ? (f1Asking || Exec.inline ? "Reading the championship…" : "The championship isn't available just now")
                    : f1Words);
            F1.Standing on = !onBar && zone == DRIVERS && driver >= 0 && driver < drivers.size() ? drivers.get(driver) : null;
            boolean moving = driverLift.step(on);
            if (drivers.isEmpty()) return moving;
            float want = Math.max(0f, driver * (DRIVER_ROW + DRIVER_GAP) - 4f * (DRIVER_ROW + DRIVER_GAP));
            float most = Math.max(0f, drivers.size() * (DRIVER_ROW + DRIVER_GAP) - (1048f - LIST_TOP));
            driverScroll.to(Math.min(want, most));
            moving |= driverScroll.step();
            c.save();
            c.clipRect(0, x(LIST_TOP - 18f), x(LIST_X + LIST_W + 30f), getHeight());
            float y0 = LIST_TOP - driverScroll.value();
            int liftedAt = -1;
            for (int i = 0; i < drivers.size(); i++) {
                float y = y0 + i * (DRIVER_ROW + DRIVER_GAP);
                if (y < LIST_TOP - DRIVER_ROW - 20f || y > 1090f) continue;
                if (drivers.get(i) == on) {
                    liftedAt = i;
                    continue;
                }
                driverRow(c, drivers.get(i), y);
            }
            if (liftedAt >= 0) driverRow(c, drivers.get(liftedAt), y0 + liftedAt * (DRIVER_ROW + DRIVER_GAP));
            c.restore();
            if (driver >= 0 && driver < drivers.size()) driverCard(c, drivers.get(driver));
            return moving;
        }

        private void driverRow(Canvas c, F1.Standing d, float y) {
            float up = driverLift.of(d), k = Math.max(0f, Math.min(1f, up)), grow = 1f + 0.02f * up;
            float w = x(LIST_W), hh = x(DRIVER_ROW);
            c.save();
            c.translate(x(LIST_X) + w / 2f, x(y) + hh / 2f);
            c.scale(grow, grow);
            c.translate(-w / 2f, -hh / 2f);
            r.set(0, 0, w, hh);
            float rad = x(24f);
            if (k > 0.01f) Shadows.draw(c, r, rad, Look07.SH_ROW, k);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(Math.round(0x12 + (0xF2 - 0x12) * k));
            c.drawRoundRect(r, rad, rad, fill);
            if (k < 0.99f) {
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(Math.max(1f, x(1f)));
                line.setColor(0xFFFFFFFF);
                line.setAlpha(Math.round(0x12 * (1f - k)));
                c.drawRoundRect(r, rad, rad, line);
                line.setStyle(Paint.Style.FILL);
            }
            boolean dark = k > 0.5f;
            float cy = hh / 2f;
            posP.setColor(dark ? Look07.INK : Look07.L1);
            c.drawText(d.position, x(34f) - posP.measureText(d.position) / 2f, Cine.mid(posP, cy), posP);
            r.set(x(70f), cy - x(26f), x(76f), cy + x(26f));
            fill.setColor(F1.colour(d.teamId));
            c.drawRoundRect(r, x(3f), x(3f), fill);
            avatar(c, d, x(125f), cy, x(33f));
            driverP.setColor(dark ? Look07.INK : Look07.L1);
            c.drawText(d.name, x(174f), cy - x(2f), driverP);
            DriverLook dl = driverLook(d);
            driverSubP.setColor(dark ? 0x8C000000 : Look07.L3);
            c.drawText(dl.sub, x(174f), cy + x(20f), driverSubP);
            float right = w - x(22f);
            ptsUnitP.setColor(dark ? 0x8C000000 : Look07.L3);
            float uw = ptsUnitP.measureText("PTS");
            c.drawText("PTS", right - uw, Type.centred(ptsUnitP, cy) + x(5f), ptsUnitP);
            pointsP.setColor(dark ? Look07.INK : Look07.L1);
            c.drawText(d.points, right - uw - x(6f) - pointsP.measureText(d.points), Cine.mid(pointsP, cy), pointsP);
            c.restore();
        }

        /** A driver's words and picture addresses, worked out the first time he is drawn. */
        private DriverLook driverLook(F1.Standing d) {
            DriverLook l = driverLooks.get(d);
            if (l != null) return l;
            l = new DriverLook();
            l.sub = Ui.fit(d.team + (d.wins.isEmpty() ? "" : " · " + d.wins + ("1".equals(d.wins) ? " win" : " wins")), driverSubP, x(560f));
            l.initials = F1.initials(d.name);
            String[] drawn = previewPictures.get(d);
            l.photo = drawn != null ? drawn[0] : F1.photo(d, Math.max(80, Math.round(x(33f) * 3f / 40f) * 40));
            l.cardPhoto = drawn != null ? drawn[0] : F1.photo(d, Math.max(200, Math.min(600, Math.round(x(300f) / 40f) * 40)));
            driverLooks.put(d, l);
            return l;
        }

        /**
         * The driver's round picture: formula1.com's (him standing, from the head, 150 % of the frame wide), else ESPN's
         * head and shoulders, else his initials on his team's colour (never a drawn person).
         */
        private void avatar(Canvas c, F1.Standing d, float cx, float cy, float rad) {
            fill.setColor(0x1AFFFFFF);
            c.drawCircle(cx, cy, rad, fill);
            DriverLook a = driverLook(d);
            int pw = Math.max(80, Math.round(rad * 3f / 40f) * 40);
            String url = a.photo;
            boolean f1 = url != null && !Pics.failed(url);
            if (!f1) url = d.headshot == null || Pics.failed(d.headshot) ? null : d.headshot;
            Bitmap b = url == null ? null : Pics.get(url, f1 ? pw : Math.round(rad * 3f), f1 ? pw * 3 : Math.round(rad * 3f), Pics.FIT, redraw);
            if (b == null) {
                if (url == null) {
                    fill.setColor(ClubLook.at(F1.colour(d.teamId), 0.12, 0.6f));
                    c.drawCircle(cx, cy, rad, fill);
                    c.drawText(a.initials, cx - initialsP.measureText(a.initials) / 2f, Type.centred(initialsP, cy), initialsP);
                }
                return;
            }
            if (a.made != b) {
                a.made = b;
                BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                // formula1.com's: 150 % of the frame wide, its top at the frame's top; ESPN's: the head filling it.
                float dw = f1 ? rad * 3f : rad * 2.4f, sc = dw / b.getWidth();
                am.setScale(sc, sc);
                am.postTranslate(-dw / 2f, f1 ? -rad : -rad * 0.95f);
                s.setLocalMatrix(am);
                a.paint.setShader(s);
            }
            c.save();
            c.translate(cx, cy);
            c.drawCircle(0, 0, rad, a.paint);
            c.restore();
        }

        /** The driver the remote is on, beside the list: a card in his team's colour, him standing, the next Grand Prix. */
        private void driverCard(Canvas c, F1.Standing d) {
            int colour = F1.colour(d.teamId);
            if (colour != f1For) {
                f1For = colour;
                // linear-gradient(160deg, the colour at 33 %, rgba(20,20,26,.85) at 60 %) over the dark material.
                float cw = x(CARD_W), ch = x(792f), a = (float) Math.toRadians(160);
                float len = (float) (Math.abs(cw * Math.sin(a)) + Math.abs(ch * Math.cos(a)));
                float ux = (float) Math.sin(a) * len / 2f, uy = (float) -Math.cos(a) * len / 2f;
                f1Card.setShader(new LinearGradient(cw / 2f - ux, ch / 2f - uy, cw / 2f + ux, ch / 2f + uy,
                        new int[]{Palette.alpha(colour, 0x55), 0xD914141A, 0xD914141A}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            }
            float left = x(CARD_X), top = x(CARD_TOP);
            box.set(left, top, left + x(CARD_W), top + x(792f));
            big.draw(c, box, x(44f), 1f);
            c.save();
            c.translate(left, top);
            c.drawPath(card, f1Card);
            // Him standing (formula1.com's), from the head down, in the card's top left.
            int pw = Math.max(200, Math.min(600, Math.round(x(300f) / 40f) * 40));
            String url = driverLook(d).cardPhoto;
            Bitmap b = url == null || Pics.failed(url) ? null : Pics.get(url, pw, pw * 3, Pics.FIT, redraw);
            if (b != null) {
                float fw = x(300f), fh = fw * b.getHeight() / Math.max(1f, b.getWidth());
                c.save();
                c.clipRect(x(20f), x(26f), x(320f), x(546f));
                r.set(x(20f), x(26f), x(320f), x(26f) + fh);
                bmp.setAlpha(255);
                c.drawBitmap(b, null, r, bmp);
                c.restore();
            } else if (url == null || Pics.failed(url)) {
                Bitmap head = d.headshot == null || Pics.failed(d.headshot) ? null : Pics.get(d.headshot, Ui.px(300f), Ui.px(220f), Pics.FIT, redraw);
                if (head != null) {
                    float hw = x(300f), hh = hw * head.getHeight() / Math.max(1f, head.getWidth());
                    r.set(x(20f), x(546f) - hh, x(320f), x(546f));
                    c.drawBitmap(head, null, r, bmp);
                }
            }
            // His number, names, team and nationality, place and points, wins and podiums.
            if (wordsFor != d || seasonFor != F1.keptSeason(d.driverId)) {
                wordsFor = d;
                seasonFor = F1.keptSeason(d.driverId);
                standing = "P" + d.position + " · " + d.points + " points";
                StringBuilder s = new StringBuilder();
                if (!d.wins.isEmpty()) s.append(d.wins).append("1".equals(d.wins) ? " win" : " wins");
                if (seasonFor != null && seasonFor.podiums >= 0) {
                    if (s.length() > 0) s.append(" · ");
                    s.append(seasonFor.podiums).append(seasonFor.podiums == 1 ? " podium" : " podiums");
                }
                winsWords = s.toString();
                String given = d.given.isEmpty() ? "" : d.given.substring(d.given.lastIndexOf(' ') + 1);
                cardGiven = Ui.fit(given, bigNameP, x(CARD_W - 370f));
                cardFamily = Ui.fit(d.family.isEmpty() ? d.name : d.family, bigNameP, x(CARD_W - 370f));
                cardTeam = Ui.fit(d.team + (d.nationality.isEmpty() ? "" : " · " + d.nationality), sideSubP, x(CARD_W - 370f));
            }
            float tx = x(340f);
            if (!d.number.isEmpty()) c.drawText(d.number, tx, x(172f), bigNumP);
            float ny = x(226f);
            if (!cardGiven.isEmpty()) {
                c.drawText(cardGiven, tx, ny, bigNameP);
                ny += x(38f);
            }
            c.drawText(cardFamily, tx, ny, bigNameP);
            c.drawText(cardTeam, tx, ny + x(33f), sideSubP);
            c.drawText(standing, tx, ny + x(81f), standP);
            if (!winsWords.isEmpty()) c.drawText(winsWords, tx, ny + x(116f), sideSubP);
            // The next Grand Prix along its foot, under a rule.
            if (weekend != null && weekend.name != null) {
                if (wordsWeekend != weekend) {
                    wordsWeekend = weekend;
                    String when = DriverScreen.days(weekend.start, weekend.race, java.time.ZoneId.systemDefault());
                    gpDays = when + (weekend.circuit == null || weekend.circuit.isEmpty() ? "" : (when.isEmpty() ? "" : " · ") + weekend.circuit);
                    gpHead = "NEXT GRAND PRIX" + (weekend.round == null || weekend.round.isEmpty() ? "" : " · ROUND " + weekend.round);
                    gpDays = Ui.fit(gpDays, gpSubP, x(CARD_W - 64f));
                    gpName = Ui.fit(weekend.name, gpP, x(CARD_W - 64f));
                }
                line.setColor(0x24FFFFFF);
                c.drawRect(x(32f), x(608f), x(CARD_W - 32f), x(608f) + Math.max(1f, x(1f)), line);
                c.drawText(gpHead, x(32f), Type.centred(lblP, x(640f)), lblP);
                c.drawText(gpName, x(32f), x(689f), gpP);
                if (!gpDays.isEmpty()) c.drawText(gpDays, x(32f), x(721f), gpSubP);
            }
            c.drawText("OK  The driver's page", x(32f), Type.centred(hintP, x(749f)), hintP);
            c.restore();
        }
    }
}
