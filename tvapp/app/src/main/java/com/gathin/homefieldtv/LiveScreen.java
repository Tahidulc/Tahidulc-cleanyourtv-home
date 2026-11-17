package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A channel full screen, with two things over it in Homefield's dark look.
 *
 * OK brings up the channel banner over a dark scrim: the channel's group and small logo at the top; its large logo,
 * what's on (its times, how far through, what's left, the channel and the picture the player has: its size and frames
 * a second), what's next, a thin amber line across for how far through it is, and a row of the channels last watched
 * on this TV, each with what's on and how far through. Left and Right go along the row; OK changes to one at once.
 *
 * Up and Down go through the channels without leaving this one, in the live screen's look: the picture of what's on
 * the channel the remote is on fills the screen behind, darkened; along the top what's on after it, then its logo, the
 * programme's title, the channel, the times and three lines of what it's about; under that its category's row of
 * channels, each tile its logo over a picture of what's on with the title, a line of what it's about and how far
 * through it is, and the next category's row below. Left and Right go along the row too; OK changes to the channel.
 *
 * The channel keys and the number keys change at once. Back, or a few seconds with no key, hides it all.
 *
 * Catch-up: where the provider keeps the programme on now in its archive (or the stream's own window reaches back to
 * its start), the banner has "Watch from the start" over the logo (Left goes to it; the rewind key too), and the live
 * screen tags it. OK held brings up a small menu for the channel: watch it, from the start, add it to the favourites;
 * and, for the channel playing, Subtitles: Off, From the channel or Automatic (LiveSubs), which ask before fetching the
 * speech model where the TV needs it.
 */
final class LiveScreen extends Screen implements LiveTv.Listener {
    private static final long BANNER_MS = 6000, CHOOSING_MS = 9000, BROWSE_MS = 12000;
    private static final int NONE = 0, BANNER = 1, BROWSE = 2;
    /** The banner's "Watch from the start", where the remote can be as on a recent channel. */
    private static final int START_OVER = -2;
    private static final float LIFT = 1.07f;

    private Channel start;
    /** From the guide: a programme to show from the archive as this screen opens (-1: none), and its end. */
    private long catchupFrom = -1, catchupEnd;
    private int mode = BANNER;
    /** The recent channel the remote is on in the banner's row, -1 for none, START_OVER for "Watch from the start". */
    private int pick = -1;
    private PopMenu menu;
    private List<Channel> recent = new ArrayList<>();
    private String digits = "";
    private FrameLayout browse;
    private Banner banner;
    private Backdrop browseBack;
    private BrowseInfo info;
    private BrowseRows rows;
    /** The scoreboard over a match (BoardLayer): it takes the remote first while its menu or its card is up. */
    private BoardLayer board;
    /** The live screen's rows (the playlist's groups) and where the remote is in them. */
    private final List<Row> groups = new ArrayList<>();
    private int row, col;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> setMode(NONE);
    private final Runnable digitsDone = this::tuneDigits;
    private final Runnable askPicture = this::askPicture;
    /** Automatic subtitles want the speech model: the family asked here first (LiveSubs). */
    private final LiveSubs.Asker asker = this::askModel;

    /** A category's row on the live screen. */
    static final class Row {
        final String name;
        final List<Channel> channels;

        Row(String name, List<Channel> channels) {
            this.name = name;
            this.channels = channels;
        }
    }

    LiveScreen(TvActivity tv, Channel c) {
        super(tv);
        this.start = c;
    }

    /** A channel's programme from its archive, from start (CatchupScreen over this screen); live when that closes. */
    LiveScreen(TvActivity tv, Channel c, long from, long end) {
        this(tv, c);
        this.catchupFrom = from;
        this.catchupEnd = end;
    }

    /** The channel before (-1) or after (1) the one playing, played live when this screen is next shown. */
    void next(int dir) {
        start = step(tv.live.channel(), dir);
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override View build() {
        FrameLayout root = new FrameLayout(tv);
        browse = new FrameLayout(tv);
        View shade = new View(tv);
        shade.setBackgroundColor(Dark.alpha(Dark.GROUND, 0xE0));
        browseBack = new Backdrop(tv, 0f, 0f, 1920f, 1080f, Pics.DARK_FULL, 0.5f);
        info = new BrowseInfo(tv);
        rows = new BrowseRows(tv);
        // Over the picture, darker at the left (for the words) and at the foot (for the rows).
        View left = new View(tv), foot = new View(tv);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Dark.alpha(Dark.GROUND, 0xD9), Dark.alpha(Dark.GROUND, 0x80), Dark.alpha(Dark.GROUND, 0)}));
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Dark.alpha(Dark.GROUND, 0), Dark.alpha(Dark.GROUND, 0x33), Dark.alpha(Dark.GROUND, 0xE6)}));
        browse.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(browseBack, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(left, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(foot, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(info, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        browse.setVisibility(View.GONE);
        banner = new Banner(tv);
        menu = new PopMenu(tv);
        board = new BoardLayer(tv);
        root.addView(board, new FrameLayout.LayoutParams(-1, -1));
        root.addView(browse, new FrameLayout.LayoutParams(-1, -1));
        root.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    @Override void shown() {
        if (catchupFrom >= 0) {
            // From the guide: the programme from the archive over this screen, which plays live once that closes.
            long from = catchupFrom;
            catchupFrom = -1;
            Channel c = start;
            start = null;
            tv.push(new CatchupScreen(tv, c, from, catchupEnd));
            return;
        }
        tv.live.addListener(this);
        tv.live.subs.asker = asker;
        tv.live.place(tv.fullScreen());
        if (start != null) {
            tv.live.play(start);
            start = null;
        } else if (tv.live.channel() != null) {
            tv.live.play(tv.live.channel());
        }
        setMode(BANNER);
        board.start();
    }

    @Override void hidden() {
        board.stop();
        if (tv.live.subs.asker == asker) tv.live.subs.asker = null;
        tv.live.removeListener(this);
        h.removeCallbacks(hide);
        h.removeCallbacks(digitsDone);
        h.removeCallbacks(askPicture);
        if (menu != null && menu.isOpen()) menu.close();
    }

    @Override public void liveChanged() {
        banner.invalidate();
    }

    @Override void tick() {
        banner.invalidate();
        info.invalidate();
        rows.invalidate();
    }

    @Override void channelsChanged() {
        if (mode == BROWSE) {
            Channel on = browseChannel();
            buildRows();
            find(on);
            rows.moved(-1, 0);
            info.show(browseChannel());
        }
        recent = Library.get(tv).recent(tv.live.channel());
        if (pick >= recent.size()) pick = recent.size() - 1;
        banner.invalidate();
    }

    /** What shows: nothing, the banner, or the live screen's rows; each fades in and out. */
    private void setMode(int m) {
        mode = m;
        h.removeCallbacks(hide);
        if (m == BANNER) {
            recent = Library.get(tv).recent(tv.live.channel());
            if (pick >= recent.size()) pick = recent.size() - 1;
            if (pick == START_OVER && fromTheStart(tv.live.channel()) == null) pick = -1;
            h.postDelayed(hide, pick != -1 ? CHOOSING_MS : BANNER_MS);
        } else {
            pick = -1;
        }
        if (m == BROWSE) h.postDelayed(hide, BROWSE_MS);
        else h.removeCallbacks(askPicture);
        Fade.show(banner, m == BANNER, 180);
        Fade.show(browse, m == BROWSE, 220);
        // Subtitles: above the banner while it is up (its row of recent channels, or not), gone under the rows.
        tv.live.subs.room(m != BROWSE, m == BANNER ? (recent.isEmpty() ? 690f : 500f) : -1f);
        banner.invalidate();
    }

    private Channel step(Channel from, int dir) {
        Library lib = Library.get(tv);
        List<Channel> all = lib.all();
        if (all.isEmpty() || from == null) return from;
        int i = lib.position(from.key);
        if (i < 0) i = 0;
        return all.get(((i + dir) % all.size() + all.size()) % all.size());
    }

    /** A channel now: the banner comes up on it. */
    private void tune(Channel c) {
        if (c == null) return;
        pick = -1;
        tv.live.play(c);
        setMode(BANNER);
    }

    /**
     * The programme on a channel now as {start, end} when it can be watched from its start (its archive keeps it, or
     * the stream's own window reaches back to it, and it began more than a minute ago); null when it can't.
     */
    private long[] fromTheStart(Channel c) {
        if (c == null) return null;
        long now = System.currentTimeMillis();
        Guide.Schedule s = Library.get(tv).schedule(c.key);
        int i = s == null ? -1 : s.at(now);
        if (i < 0 || now - s.start[i] < LiveTv.LIVE_EDGE_MS || !tv.live.canShow(c, s.start[i])) return null;
        return new long[]{s.start[i], s.stop[i]};
    }

    /** The programme on now on a channel from its start (CatchupScreen over this screen). */
    private void startOver(Channel c) {
        long[] p = fromTheStart(c);
        if (p == null) return;
        pick = -1;
        h.removeCallbacks(hide);
        mode = NONE;
        Fade.show(banner, false, 120);
        Fade.show(browse, false, 120);
        tv.push(new CatchupScreen(tv, c, p[0], p[1]));
    }

    /** OK held: a small menu for a channel (watch it, from the start, the favourites). */
    private void openMenu(Channel c) {
        if (c == null || menu == null) return;
        boolean playing = tv.live.channel() != null && tv.live.channel().key.equals(c.key);
        List<String[]> items = new ArrayList<>();
        if (!playing) items.add(new String[]{"watch", "Watch " + c.name});
        if (fromTheStart(c) != null) items.add(new String[]{"start", "Watch from the start"});
        boolean fav = Library.get(tv).isFavourite(c);
        items.add(new String[]{"fav", fav ? "Remove from favourites" : "Add to favourites"});
        if (playing) items.add(new String[]{"subs", "Subtitles: " + LiveSubs.MODES[LiveSubs.mode(tv)]});
        long now = System.currentTimeMillis();
        Guide.Schedule s = Library.get(tv).schedule(c.key);
        int i = s == null ? -1 : s.at(now);
        String line = i >= 0 ? LiveText.span(s.start[i], s.stop[i]) + "  " + s.title[i] : "";
        h.removeCallbacks(hide);
        menu.open(c.name, line, items, 680f, 360f, key -> {
            switch (key) {
                case "watch":
                    tune(c);
                    break;
                case "start":
                    startOver(c);
                    break;
                case "subs":
                    openSubs();
                    break;
                default: {
                    boolean on = Library.get(tv).toggleFavourite(c);
                    android.widget.Toast.makeText(tv, on ? c.name + " added to your favourites" : c.name + " taken off your favourites",
                            android.widget.Toast.LENGTH_SHORT).show();
                    if (mode != NONE) setMode(mode);
                    break;
                }
            }
        });
    }

    /** The menu's Subtitles: Off, From the channel or Automatic, for every channel; the one chosen ticked. */
    private void openSubs() {
        int now = LiveSubs.mode(tv);
        List<String[]> items = new ArrayList<>();
        for (int i = 0; i < LiveSubs.MODES.length; i++) {
            items.add(new String[]{"mode:" + i, LiveSubs.MODES[i], i == now ? PopMenu.CHOSEN : ""});
        }
        h.removeCallbacks(hide);
        menu.open("Subtitles", tv.live.subs.about(), items, 680f, 360f, key -> {
            tv.live.subs.setMode(key.charAt(key.length() - 1) - '0');
            if (mode != NONE && !menu.isOpen()) setMode(mode);
        });
    }

    /** Automatic subtitles where the TV has no speech recognition of its own: the speech model fetched only on a yes. */
    private void askModel() {
        if (menu == null || menu.isOpen()) {
            // Something else is up: asked next time.
            tv.live.subs.askLater();
            return;
        }
        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"get", "Download it"});
        items.add(new String[]{"no", "Not now"});
        h.removeCallbacks(hide);
        menu.ask("Automatic subtitles", LiveSubs.askLine(), items, key -> {
            if (key.equals("get")) tv.live.subs.fetch();
            if (mode != NONE) setMode(mode);
        });
    }

    private void tuneDigits() {
        h.removeCallbacks(digitsDone);
        String d = digits;
        digits = "";
        if (d.isEmpty()) return;
        Library lib = Library.get(tv);
        int n;
        try {
            n = Integer.parseInt(d);
        } catch (NumberFormatException e) {
            return;
        }
        for (Channel c : lib.all()) {
            if (lib.number(c) == n) {
                tune(c);
                return;
            }
        }
        banner.invalidate();
    }

    // ---- the live screen's rows -------------------------------------------------------------------------------------

    /** The playlist's groups as rows, in the playlist's order. */
    private void buildRows() {
        groups.clear();
        Map<String, List<Channel>> by = new LinkedHashMap<>();
        for (Channel c : Library.get(tv).all()) {
            List<Channel> l = by.get(c.group);
            if (l == null) by.put(c.group, l = new ArrayList<>());
            l.add(c);
        }
        for (Map.Entry<String, List<Channel>> e : by.entrySet()) {
            groups.add(new Row(e.getKey().isEmpty() ? "Channels" : e.getKey(), e.getValue()));
        }
    }

    private void find(Channel c) {
        row = 0;
        col = 0;
        if (c == null) return;
        for (int r = 0; r < groups.size(); r++) {
            List<Channel> l = groups.get(r).channels;
            for (int k = 0; k < l.size(); k++) {
                if (l.get(k).key.equals(c.key)) {
                    row = r;
                    col = k;
                    return;
                }
            }
        }
    }

    private Channel browseChannel() {
        if (row < 0 || row >= groups.size()) return null;
        List<Channel> l = groups.get(row).channels;
        return col >= 0 && col < l.size() ? l.get(col) : null;
    }

    /** Up or Down while watching: the live screen, on the channel before or after the one playing. */
    private void openBrowse(int dir) {
        buildRows();
        if (groups.isEmpty()) return;
        find(tv.live.channel());
        rows.moved(-1, 0);
        setMode(BROWSE);
        browseStep(dir);
    }

    /** To the channel before or after, on into the row before or after at either end. */
    private void browseStep(int dir) {
        if (groups.isEmpty()) return;
        int r = row, k = col + dir;
        if (k < 0) {
            r = (r - 1 + groups.size()) % groups.size();
            k = groups.get(r).channels.size() - 1;
        } else if (k >= groups.get(r).channels.size()) {
            r = (r + 1) % groups.size();
            k = 0;
        }
        moveTo(r, k, r == row ? 0 : dir);
    }

    /** Along the row, stopping at its ends. */
    private void browseAlong(int dir) {
        if (groups.isEmpty()) return;
        int k = col + dir;
        if (k < 0 || k >= groups.get(row).channels.size()) {
            h.removeCallbacks(hide);
            h.postDelayed(hide, BROWSE_MS);
            return;
        }
        moveTo(row, k, 0);
    }

    private void moveTo(int r, int k, int rowDir) {
        int was = row;
        row = r;
        col = k;
        rows.moved(was, rowDir);
        Channel c = browseChannel();
        info.show(c);
        // The picture behind: the one known now, else none until the remote rests and it is found.
        Guide.Schedule s = c == null ? null : Library.get(tv).schedule(c.key);
        browseBack.set(ProgrammePictures.peek(c, s, s == null ? -1 : s.at(System.currentTimeMillis())));
        h.removeCallbacks(askPicture);
        if (Exec.inline) askPicture();
        else h.postDelayed(askPicture, ProgrammePictures.REST_MS);
        h.removeCallbacks(hide);
        h.postDelayed(hide, BROWSE_MS);
    }

    private void askPicture() {
        if (mode != BROWSE) return;
        Channel c = browseChannel();
        Guide.Schedule s = c == null ? null : Library.get(tv).schedule(c.key);
        String url = ProgrammePictures.get(tv, c, s, s == null ? -1 : s.at(System.currentTimeMillis()), this::pictureCame);
        if (url != null) browseBack.set(url);
    }

    private void pictureCame() {
        if (mode != BROWSE) return;
        Channel c = browseChannel();
        Guide.Schedule s = c == null ? null : Library.get(tv).schedule(c.key);
        String url = ProgrammePictures.peek(c, s, s == null ? -1 : s.at(System.currentTimeMillis()));
        if (url != null) browseBack.set(url);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (menu != null && menu.isOpen()) return menu.key(code);
        if (board.key(code)) return true;
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits += (char) ('0' + code - KeyEvent.KEYCODE_0);
            h.removeCallbacks(digitsDone);
            if (digits.length() >= 4) tuneDigits();
            else h.postDelayed(digitsDone, 1600);
            setMode(BANNER);
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
                tune(step(tv.live.channel(), -1));
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                tune(step(tv.live.channel(), 1));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (mode == BROWSE) browseStep(dir);
                else openBrowse(dir);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mode == BROWSE) {
                    browseAlong(1);
                    return true;
                }
                if (mode == BANNER && pick == START_OVER) pick = -1;
                else if (mode == BANNER && pick < recent.size() - 1) pick++;
                setMode(BANNER);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mode == BROWSE) {
                    browseAlong(-1);
                    return true;
                }
                if (mode == NONE) {
                    // "‹ Menu", as before.
                    tv.pop();
                    return true;
                }
                if (pick >= 0) pick--;
                // Left of the row: "Watch from the start", where the programme on now can be.
                else if (pick == -1 && fromTheStart(tv.live.channel()) != null) pick = START_OVER;
                setMode(BANNER);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (e.getRepeatCount() == 0) {
                    Channel c = mode == BROWSE ? browseChannel() : tv.live.channel();
                    if (fromTheStart(c) != null) startOver(c);
                    else setMode(BANNER);
                }
                return true;
            case KeyEvent.KEYCODE_INFO:
                setMode(mode == BANNER ? NONE : BANNER);
                return true;
            default:
                return false;
        }
    }

    @Override void ok() {
        if (menu != null && menu.isOpen()) {
            menu.ok();
            return;
        }
        if (board.ok()) return;
        switch (mode) {
            case BROWSE:
                tune(browseChannel());
                return;
            case BANNER:
                if (pick == START_OVER) {
                    startOver(tv.live.channel());
                    return;
                }
                if (pick >= 0 && pick < recent.size()) {
                    tune(recent.get(pick));
                    return;
                }
                if (tv.live.state() == LiveTv.FAILED) {
                    tv.live.tryAgain();
                    setMode(BANNER);
                    return;
                }
                setMode(NONE);
                return;
            default:
                setMode(BANNER);
        }
    }

    @Override void okLong() {
        if (menu != null && menu.isOpen()) return;
        // Over a match with nothing else up: the scoreboard's menu, as OK held over TiviMate on the launcher.
        if (mode == NONE && board.okLong()) return;
        Channel c = mode == BROWSE ? browseChannel() : mode == BANNER && pick >= 0 && pick < recent.size() ? recent.get(pick) : tv.live.channel();
        openMenu(c);
    }

    @Override boolean back() {
        if (menu != null && menu.isOpen()) {
            menu.close();
            if (mode != NONE) setMode(mode);
            return true;
        }
        if (board.back()) return true;
        if (mode != NONE) {
            setMode(NONE);
            return true;
        }
        return false;
    }

    // ---- the banner -------------------------------------------------------------------------------------------------

    /** The channel banner, and the channel number being typed. */
    private final class Banner extends View {
        private final Fade fade = new Fade();
        private final Lift lift = new Lift();
        private final Paint top = new Paint(), foot = new Paint(), bmp = new Paint(Paint.FILTER_BITMAP_FLAG), logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint track = Dark.fill(0x4DFFFFFF), white = Dark.fill(0xFFFFFFFF), line = Dark.fill(0x40FFFFFF), lineAccent = Dark.fill(Dark.ACCENT);
        private final Paint chip = Dark.line(0x8CFFFFFF, 1.6f), thin = Dark.fill(0x40FFFFFF), accent = Dark.fill(Dark.ACCENT);
        private final Paint tileBack = Dark.fill(0xFF000000), ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 3f), pillBack = Dark.fill(Dark.alpha(Dark.RAIL, 0xE6));
        private final TextPaint groupP = Dark.text(26, 600, Dark.SOFT), titleP = Dark.text(58, 600, Dark.TEXT);
        private final TextPaint lineP = Dark.text(30, 500, Dark.SOFT), numP = Dark.text(30, 400, Dark.MUTED), nameP = Dark.text(30, 700, Dark.TEXT);
        private final TextPaint chipP = Dark.text(22, 600, Dark.SOFT), nextLabel = Dark.text(24, 700, Dark.MUTED), nextP = Dark.text(28, 500, Dark.SOFT);
        private final TextPaint logoName = Dark.text(40, 700, Dark.TEXT), noneP = Dark.text(28, 500, Dark.MUTED);
        private final TextPaint tileP = Dark.text(24, 500, Dark.SOFT), tileOn = Dark.text(24, 600, Dark.TEXT), tileName = Dark.text(24, 700, Dark.TEXT);
        private final TextPaint hintP = Dark.text(26, 600, Dark.TEXT), digitP = Dark.text(64, 600, Dark.TEXT);
        private final Paint startLit = Dark.fill(Dark.ACCENT), startBack = Dark.fill(Dark.alpha(Dark.RAIL, 0xD9)), startEdge = Dark.line(0x66FFFFFF, 1.5f);
        private final Paint startRing = Dark.line(Dark.alpha(Dark.RING, 0xE6), 3f), iconLine = Dark.line(Dark.TEXT, 2.4f), iconFill = Dark.fill(Dark.TEXT);
        private final Paint inkLine = Dark.line(Dark.ACCENT_INK, 2.4f), inkFill = Dark.fill(Dark.ACCENT_INK);
        private final TextPaint startP = Dark.text(26, 600, Dark.TEXT), startInk = Dark.text(26, 700, Dark.ACCENT_INK);
        private final RectF r = new RectF();
        private float topFor = -1f, footFor = -1f, scroll;
        private long last;
        private final Runnable redraw = this::invalidate;

        Banner(Context c) {
            super(c);
            groupP.setLetterSpacing(0.06f);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            boolean moving = false;
            Channel ch = tv.live.channel();
            if (ch != null && mode == BANNER) moving = drawBanner(c, ch, w, hh);
            if (!digits.isEmpty()) drawDigits(c, w);
            if (moving) postInvalidateOnAnimation();
        }

        /** The banner; true while anything in it still moves. */
        private boolean drawBanner(Canvas c, Channel ch, int w, int hh) {
            Library lib = Library.get(tv);
            long now = System.currentTimeMillis();
            boolean row = !recent.isEmpty();
            float base = row ? 0f : 190f;
            shades(hh, base);
            c.drawRect(0, 0, w, Ui.f(260), top);
            c.drawRect(0, Ui.f(470 + base), w, hh, foot);

            // The channel's words fade and rise in again when it changes.
            fade.focus(ch.key);
            int a = Math.round(255 * fade.text());
            float rise = fade.rise();
            alpha(a);

            // At the top: its group, its small logo under it.
            String group = ch.group.toUpperCase(Locale.ROOT);
            if (!group.isEmpty()) c.drawText(Ui.fit(group, groupP, Ui.f(900)), Ui.f(96), Ui.f(92) + rise, groupP);
            int logoMode = Pics.tiles ? Pics.READY : Pics.FIT;
            Bitmap small = Pics.get(ch.logo, Ui.px(150), Ui.px(60), logoMode, redraw);
            if (small != null) c.drawBitmap(small, Ui.f(96), Ui.f(group.isEmpty() ? 60 : 110) + rise, logoPaint);

            // Its large logo, what's on, the channel and the picture the player has.
            float lx = Ui.f(96), ly = Ui.f(600 + base) + rise, lw = Ui.f(280), lh = Ui.f(150);
            Bitmap big = Pics.get(ch.logo, Ui.px(280), Ui.px(150), logoMode, redraw);
            if (big != null) c.drawBitmap(big, lx, ly + (lh - big.getHeight()) / 2f, logoPaint);
            else c.drawText(Ui.fit(ch.name, logoName, lw), lx, ly + lh / 2f + Ui.f(14), logoName);

            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            int n = s == null ? -1 : i >= 0 ? (i + 1 < s.start.length ? i + 1 : -1) : s.after(now);
            float x0 = Ui.f(420), right = w - Ui.f(96);
            String title = i >= 0 ? s.title[i] : ch.name;
            c.drawText(Ui.fit(title, titleP, right - x0), x0, Ui.f(662 + base) + rise, titleP);
            float y = Ui.f(718 + base) + rise, x = x0;
            if (i >= 0) {
                String span = LiveText.span(s.start[i], s.stop[i]);
                c.drawText(span, x, y, lineP);
                x += lineP.measureText(span) + Ui.f(22);
                float p = LiveText.progress(now, s.start[i], s.stop[i]), bw = Ui.f(120), by = y - Ui.f(11);
                r.set(x, by, x + bw, by + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), track);
                r.set(x, by, x + bw * p, by + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), white);
                x += bw + Ui.f(18);
                String left = LiveText.left(now, s.stop[i]);
                c.drawText(left, x, y, lineP);
                x += lineP.measureText(left) + Ui.f(30);
            } else {
                String none = s == null ? "No programme information" : "Nothing in the guide now";
                c.drawText(none, x, y, noneP);
                x += noneP.measureText(none) + Ui.f(30);
            }
            String num = String.valueOf(lib.number(ch));
            c.drawText(num, x, y, numP);
            x += numP.measureText(num) + Ui.f(10);
            String name = Ui.fit(ch.name, nameP, Math.max(Ui.f(120), right - x - Ui.f(280)));
            c.drawText(name, x, y, nameP);
            x += nameP.measureText(name) + Ui.f(24);
            // The chips: only what the player itself knows of the picture.
            for (String f : tv.live.format()) {
                if (f.isEmpty()) continue;
                float cw = chipP.measureText(f) + Ui.f(24);
                if (x + cw > right) break;
                r.set(x, y - Ui.f(28), x + cw, y + Ui.f(8));
                c.drawRoundRect(r, Ui.f(8), Ui.f(8), chip);
                c.drawText(f, x + Ui.f(12), y - Ui.f(2), chipP);
                x += cw + Ui.f(12);
            }
            if (n >= 0) {
                float ny = Ui.f(770 + base) + rise;
                c.drawText("Next", x0, ny, nextLabel);
                float nx = x0 + nextLabel.measureText("Next") + Ui.f(16);
                String sp = LiveText.span(s.start[n], s.stop[n]);
                c.drawText(sp, nx, ny, nextP);
                nx += nextP.measureText(sp) + Ui.f(18);
                c.drawText(Ui.fit(s.title[n], nextP, right - nx), nx, ny, nextP);
            }
            // A thin line across for how far through it is.
            if (i >= 0) {
                float ax0 = Ui.f(96), ax1 = w - Ui.f(96), ay = Ui.f(816 + base);
                float px = ax0 + (ax1 - ax0) * LiveText.progress(now, s.start[i], s.stop[i]);
                c.drawRect(ax0, ay - Ui.f(1.5f), ax1, ay + Ui.f(1.5f), line);
                c.drawRect(ax0, ay - Ui.f(2), px, ay + Ui.f(2), lineAccent);
                c.drawCircle(px, ay, Ui.f(9), lineAccent);
            }
            if (tv.live.state() == LiveTv.FAILED) hint(c, "OK to try again", right, Ui.f(560 + base));
            else if (fromTheStart(ch) != null) startPill(c, Ui.f(96), Ui.f(560 + base), pick == START_OVER);
            alpha(255);
            boolean moving = fade.busy();
            if (row) moving |= drawRecent(c, w, now, lib);
            return moving;
        }

        /** The channel's words' opacity (the recent row keeps its own). */
        private void alpha(int a) {
            groupP.setAlpha(a);
            titleP.setAlpha(a);
            lineP.setAlpha(a);
            numP.setAlpha(a);
            nameP.setAlpha(a);
            chipP.setAlpha(a);
            nextLabel.setAlpha(a);
            nextP.setAlpha(a);
            logoName.setAlpha(a);
            noneP.setAlpha(a);
            logoPaint.setAlpha(a);
            track.setAlpha(a * 0x4D / 255);
            white.setAlpha(a);
            chip.setAlpha(a * 0x8C / 255);
            line.setAlpha(a * 0x40 / 255);
            lineAccent.setAlpha(a);
        }

        private void shades(int hh, float base) {
            if (topFor != hh) {
                top.setShader(new LinearGradient(0, 0, 0, Ui.f(260), 0xB3000000, 0x00000000, Shader.TileMode.CLAMP));
                topFor = hh;
            }
            float from = Ui.f(470 + base);
            if (footFor != from + hh) {
                foot.setShader(new LinearGradient(0, from, 0, hh, new int[]{0x00000000, 0xC7000000, 0xF2000000},
                        new float[]{0f, 0.38f, 1f}, Shader.TileMode.CLAMP));
                footFor = from + hh;
            }
        }

        /** The channels last watched: each its logo on its tile, what's on and how far through; true while moving. */
        private boolean drawRecent(Canvas c, int w, long now, Library lib) {
            long t = SystemClock.uptimeMillis();
            float dt = last == 0 ? 16f : Math.min(48f, t - last);
            last = t;
            float tw = Ui.f(232), th = Ui.f(130), gap = Ui.f(28), y0 = Ui.f(852), x0 = Ui.f(96), pitch = tw + gap;
            int fit = Math.max(1, (int) ((w - Ui.f(192) + gap) / pitch));
            float to = pick < fit ? 0f : (pick - fit + 1) * pitch;
            boolean moving = false;
            if (Math.abs(to - scroll) > 0.5f) {
                scroll += (to - scroll) * (Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0));
                moving = true;
            } else {
                scroll = to;
            }
            moving |= lift.step(pick >= 0 && pick < recent.size() ? recent.get(pick) : null);
            if (!moving) last = 0;
            for (int k = 0; k < recent.size(); k++) {
                float tx = x0 + k * pitch - scroll;
                if (tx > w) break;
                if (tx + tw < 0) continue;
                Channel rc = recent.get(k);
                float l = lift.of(rc);
                c.save();
                if (l > 0f) {
                    float sc = 1f + (LIFT - 1f) * l;
                    c.scale(sc, sc, tx + tw / 2f, y0 + th / 2f);
                }
                r.set(tx, y0, tx + tw, y0 + th);
                Bitmap tile = Pics.tiles ? Pics.get(rc.logo, Ui.px(232), Ui.px(130), Pics.TILE, redraw) : null;
                if (tile != null) {
                    c.drawBitmap(tile, tx, y0, bmp);
                } else {
                    c.drawRoundRect(r, Ui.f(16), Ui.f(16), tileBack);
                    Bitmap f = Pics.tiles ? null : Pics.get(rc.logo, Ui.px(172), Ui.px(80), Pics.FIT, redraw);
                    if (f != null) c.drawBitmap(f, tx + (tw - f.getWidth()) / 2f, y0 + (th - f.getHeight()) / 2f, bmp);
                    else {
                        String nm = Ui.fit(rc.name, tileName, tw - Ui.f(24));
                        c.drawText(nm, tx + (tw - tileName.measureText(nm)) / 2f, y0 + th / 2f + Ui.f(9), tileName);
                    }
                }
                if (l > 0f) {
                    ring.setAlpha(Math.round(0xE6 * l));
                    Dark.ring(c, r, Ui.f(16), ring);
                }
                c.restore();
                // What's on, and how far through.
                Guide.Schedule s = lib.schedule(rc.key);
                int i = s == null ? -1 : s.at(now);
                float under = (LIFT - 1f) * th / 2f * l;
                TextPaint p = l > 0.5f ? tileOn : tileP;
                c.drawText(Ui.fit(i >= 0 ? s.title[i] : rc.name, p, tw), tx, y0 + th + Ui.f(36) + under, p);
                float by = y0 + th + Ui.f(50) + under;
                c.drawRect(tx, by, tx + tw, by + Ui.f(3), thin);
                if (i >= 0) c.drawRect(tx, by, tx + tw * LiveText.progress(now, s.start[i], s.stop[i]), by + Ui.f(3), l > 0.5f ? accent : white);
            }
            return moving;
        }

        /** "Watch from the start" over the logo: amber inside a ring where the remote is (Left from the row goes to it). */
        private void startPill(Canvas c, float x, float base, boolean lit) {
            String t = "Watch from the start";
            TextPaint p = lit ? startInk : startP;
            float icon = Ui.f(36), ph = Ui.f(54), pw = Ui.f(20) + icon + p.measureText(t) + Ui.f(26);
            r.set(x, base - Ui.f(38), x + pw, base - Ui.f(38) + ph);
            c.drawRoundRect(r, ph / 2f, ph / 2f, lit ? startLit : startBack);
            if (lit) Dark.ring(c, r, ph / 2f, startRing);
            else c.drawRoundRect(r, ph / 2f, ph / 2f, startEdge);
            Dark.replay(c, x + Ui.f(20) + icon / 2f - Ui.f(4), r.centerY(), Ui.f(10), lit ? inkLine : iconLine, lit ? inkFill : iconFill);
            c.drawText(t, x + Ui.f(20) + icon, r.centerY() + Ui.f(9), p);
        }

        private void hint(Canvas c, String t, float right, float y) {
            float tw = hintP.measureText(t);
            r.set(right - tw - Ui.f(44), y - Ui.f(40), right, y + Ui.f(10));
            c.drawRoundRect(r, Ui.f(25), Ui.f(25), pillBack);
            c.drawText(t, right - tw - Ui.f(22), y - Ui.f(6), hintP);
        }

        private void drawDigits(Canvas c, int w) {
            r.set(w - Ui.f(340), Ui.f(60), w - Ui.f(96), Ui.f(170));
            c.drawRoundRect(r, Ui.f(18), Ui.f(18), pillBack);
            String d = digits.length() >= 3 ? digits : (digits + "---").substring(0, 3);
            c.drawText(d, r.centerX() - digitP.measureText(d) / 2f, r.centerY() + Ui.f(22), digitP);
        }
    }

    // ---- the live screen: what's on the channel the remote is on ----------------------------------------------------

    /** Along the top what's on after, then the channel's logo, the programme's title, the channel, times and story. */
    private final class BrowseInfo extends View {
        private Channel ch;
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), sep = Dark.fill(0x4DFFFFFF);
        private final Paint track = Dark.fill(0x40FFFFFF), accent = Dark.fill(Dark.ACCENT);
        private final TextPaint atP = Dark.text(22, 600, Dark.MUTED), laterP = Dark.text(22, 500, Dark.SOFT);
        private final TextPaint titleP = Dark.text(60, 600, Dark.TEXT), chanP = Dark.text(24, 500, Dark.MUTED);
        private final TextPaint timeP = Dark.text(24, 500, Dark.SOFT), descP = Dark.text(26, 400, Dark.SOFT);
        private final TextPaint logoName = Dark.text(34, 700, Dark.TEXT), tagP = Dark.text(20, 600, Dark.SOFT);
        private final Paint tagLine = Dark.line(0x80FFFFFF, 1.5f), iconLine = Dark.line(Dark.SOFT, 2f), iconFill = Dark.fill(Dark.SOFT);
        private final RectF r = new RectF();
        private String descKey = "";
        private StaticLayout descL;
        private final Runnable redraw = this::invalidate;

        BrowseInfo(Context c) {
            super(c);
        }

        void show(Channel c) {
            boolean other = c == null ? ch != null : ch == null || !c.key.equals(ch.key);
            ch = c;
            if (other && c != null) Fade.in(this);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (ch == null) return;
            Library lib = Library.get(tv);
            long now = System.currentTimeMillis();
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            float x0 = Ui.f(96), right = getWidth() - Ui.f(96);

            // Along the top: what's on after it.
            int from = s == null ? -1 : i >= 0 ? i + 1 : s.after(now);
            float x = x0, y = Ui.f(64);
            for (int k = from; s != null && k >= 0 && k < s.start.length && k < from + 6; k++) {
                String at = LiveText.hm(s.start[k]);
                float aw = atP.measureText(at);
                if (x + aw + Ui.f(140) > right) break;
                c.drawText(at, x, y, atP);
                x += aw + Ui.f(12);
                String t = Ui.fit(s.title[k], laterP, Math.min(Ui.f(330), right - x));
                c.drawText(t, x, y, laterP);
                x += laterP.measureText(t) + Ui.f(22);
                c.drawRect(x, y - Ui.f(18), x + Math.max(1f, Ui.f(1.5f)), y + Ui.f(4), sep);
                x += Ui.f(22);
            }

            // The channel's logo, the programme's title, the channel, the times and the story.
            Bitmap logo = Pics.get(ch.logo, Ui.px(200), Ui.px(72), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) c.drawBitmap(logo, x0, Ui.f(100) + (Ui.f(72) - logo.getHeight()) / 2f, bmp);
            else c.drawText(Ui.fit(ch.name, logoName, Ui.f(600)), x0, Ui.f(150), logoName);
            String title = i >= 0 ? s.title[i] : ch.name;
            c.drawText(Ui.fit(title, titleP, Ui.f(1400)), x0, Ui.f(250), titleP);
            String chan = lib.number(ch) + "  " + ch.name + (ch.group.isEmpty() ? "" : "  ·  " + ch.group);
            c.drawText(Ui.fit(chan, chanP, Ui.f(1400)), x0, Ui.f(294), chanP);
            if (i >= 0) {
                String t = LiveText.span(s.start[i], s.stop[i]) + "  ·  " + LiveText.left(now, s.stop[i]);
                c.drawText(t, x0, Ui.f(334), timeP);
                float bx = x0 + timeP.measureText(t) + Ui.f(20), by = Ui.f(326), bw = Ui.f(120);
                r.set(bx, by, bx + bw, by + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), track);
                r.set(bx, by, bx + bw * LiveText.progress(now, s.start[i], s.stop[i]), by + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), accent);
                if (fromTheStart(ch) != null) {
                    // It can be watched from its start: how.
                    String how = "From the start: hold OK";
                    float tx = bx + bw + Ui.f(24), tw = Ui.f(40) + tagP.measureText(how) + Ui.f(14), th = Ui.f(34);
                    r.set(tx, Ui.f(334) - Ui.f(25), tx + tw, Ui.f(334) - Ui.f(25) + th);
                    c.drawRoundRect(r, th / 2f, th / 2f, tagLine);
                    Dark.replay(c, tx + Ui.f(20), r.centerY(), Ui.f(8), iconLine, iconFill);
                    c.drawText(how, tx + Ui.f(38), Ui.f(334) - Ui.f(1), tagP);
                }
                if (!s.desc[i].isEmpty()) {
                    String key = ch.key + "|" + s.start[i];
                    if (!key.equals(descKey) || descL == null) {
                        descL = Ui.layout(s.desc[i], descP, Ui.px(1000), 3);
                        descKey = key;
                    }
                    Ui.draw(c, descL, x0, Ui.f(358));
                }
            } else {
                c.drawText(s == null ? "No programme information" : "Nothing in the guide now", x0, Ui.f(334), timeP);
            }
        }
    }

    /** The category's row of channels, its name over it, and the next category's row below. */
    private final class BrowseRows extends View {
        private static final float X0 = 96f, TILE_W = 320f, TILE_H = 180f, GAP_X = 24f, HEAD_Y = 610f, PITCH = 300f;
        private final Lift lift = new Lift();
        private float scroll, scrollTo, shift, leftScroll;
        private int first, leftRow = -1;
        private boolean settled;
        private long last;
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), tileBack = Dark.fill(0xFF000000);
        private final Paint track = Dark.fill(0x40FFFFFF), bar = Dark.fill(Dark.ACCENT), ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 3f);
        private final TextPaint headP = Dark.text(26, 700, Dark.TEXT), headNext = Dark.text(24, 700, Dark.MUTED);
        private final TextPaint nameP = Dark.text(20, 500, Dark.MUTED), nameOn = Dark.text(20, 600, Dark.SOFT);
        private final TextPaint tTitle = Dark.text(23, 600, Dark.TEXT), tDesc = Dark.text(18, 400, Dark.SOFT);
        private final TextPaint logoName = Dark.text(26, 700, Dark.TEXT);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private final Runnable rest = () -> {
            settled = true;
            invalidate();
        };

        BrowseRows(Context c) {
            super(c);
            headP.setLetterSpacing(0.04f);
            headNext.setLetterSpacing(0.04f);
        }

        /** The remote moved (from row was; rowDir +1 down into the next row, -1 up, 0 along the same one). */
        void moved(int was, int rowDir) {
            int n = Math.max(1, (int) ((1920f - X0 - 40f + GAP_X) / (TILE_W + GAP_X)));
            int size = row < groups.size() ? groups.get(row).channels.size() : 0;
            if (rowDir != 0 || was < 0) {
                leftRow = was;
                leftScroll = scroll;
                first = 0;
            }
            if (col < first + 1) first = Math.max(0, col - 1);
            if (col > first + n - 2) first = col - n + 2;
            first = Math.max(0, Math.min(first, Math.max(0, size - n)));
            scrollTo = first * (TILE_W + GAP_X);
            if (rowDir != 0 || was < 0 || Exec.inline) scroll = scrollTo;
            shift = rowDir != 0 && !Exec.inline ? rowDir * PITCH : 0f;
            settled = Exec.inline;
            removeCallbacks(rest);
            if (!Exec.inline) postDelayed(rest, ProgrammePictures.REST_MS);
            invalidate();
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(rest);
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas c) {
            if (groups.isEmpty()) return;
            long t = SystemClock.uptimeMillis();
            float dt = last == 0 ? 16f : Math.min(48f, t - last);
            last = t;
            float k = Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0);
            boolean moving = false;
            if (Math.abs(scrollTo - scroll) > 0.5f) {
                scroll += (scrollTo - scroll) * k;
                moving = true;
            } else {
                scroll = scrollTo;
            }
            if (Math.abs(shift) > 0.5f) {
                shift -= shift * k;
                moving = true;
            } else {
                shift = 0f;
            }
            moving |= lift.step(browseChannel());
            if (!moving) last = 0;
            Library lib = Library.get(tv);
            long now = System.currentTimeMillis();
            c.save();
            c.clipRect(0, Ui.f(HEAD_Y - 46), getWidth(), getHeight());
            for (int d = -1; d <= 1; d++) {
                int idx = row + d;
                if (idx < 0 || idx >= groups.size()) continue;
                float y = HEAD_Y + d * PITCH + shift;
                if (y > 1080f + 40f || y + PITCH < HEAD_Y - 46f) continue;
                float sc = d == 0 ? scroll : idx == leftRow ? leftScroll : 0f;
                drawRow(c, groups.get(idx), y, sc, d == 0, now, lib);
            }
            c.restore();
            if (moving) postInvalidateOnAnimation();
        }

        private void drawRow(Canvas c, Row g, float y, float sc, boolean mine, long now, Library lib) {
            TextPaint hp = mine ? headP : headNext;
            c.drawText(Ui.fit(g.name.toUpperCase(Locale.ROOT), hp, Ui.f(1500)), Ui.f(X0), Ui.f(y), hp);
            float pitch = TILE_W + GAP_X;
            int from = Math.max(0, (int) Math.floor(sc / pitch));
            for (int k = from; k < g.channels.size(); k++) {
                float x = X0 + k * pitch - sc;
                if (x > 1920f) break;
                drawTile(c, g.channels.get(k), Ui.f(x), Ui.f(y + 22f), mine, now, lib);
            }
        }

        /** A channel's tile: its logo over a picture of what's on, the title, a line of what it's about, how far through. */
        private void drawTile(Canvas c, Channel ch, float x, float y, boolean mine, long now, Library lib) {
            float w = Ui.f(TILE_W), hgt = Ui.f(TILE_H), l = lift.of(ch);
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            String url = i < 0 ? "" : mine && settled ? ProgrammePictures.get(getContext(), ch, s, i, redraw) : ProgrammePictures.peek(ch, s, i);
            Bitmap pic = url == null || url.isEmpty() ? null : Pics.get(url, Ui.px(TILE_W), Ui.px(TILE_H), Pics.CARD, redraw);
            int logoMode = Pics.tiles ? Pics.READY : Pics.FIT;
            c.save();
            if (l > 0f) {
                float k = 1f + (LIFT - 1f) * l;
                c.scale(k, k, x + w / 2f, y + hgt / 2f);
            }
            r.set(x, y, x + w, y + hgt);
            if (pic != null) {
                c.drawBitmap(pic, x, y, bmp);
                Bitmap logo = Pics.get(ch.logo, Ui.px(120), Ui.px(46), logoMode, redraw);
                if (logo != null) c.drawBitmap(logo, x + Ui.f(16), y + Ui.f(14), bmp);
            } else {
                c.drawRoundRect(r, Ui.f(16), Ui.f(16), tileBack);
                Bitmap logo = Pics.get(ch.logo, Ui.px(170), Ui.px(62), logoMode, redraw);
                if (logo != null) c.drawBitmap(logo, x + (w - logo.getWidth()) / 2f, y + Ui.f(58) - logo.getHeight() / 2f, bmp);
                else {
                    String nm = Ui.fit(ch.name, logoName, w - Ui.f(32));
                    c.drawText(nm, x + (w - logoName.measureText(nm)) / 2f, y + Ui.f(66), logoName);
                }
            }
            // On the tile: what's on, a line of what it's about, how far through.
            float room = w - Ui.f(32);
            String title = i >= 0 ? s.title[i] : ch.name;
            c.drawText(Ui.fit(title, tTitle, room), x + Ui.f(16), y + hgt - Ui.f(46), tTitle);
            String about = i < 0 ? "No programme information" : s.desc[i].isEmpty() ? LiveText.span(s.start[i], s.stop[i]) : s.desc[i];
            c.drawText(Ui.fit(about, tDesc, room), x + Ui.f(16), y + hgt - Ui.f(22), tDesc);
            if (i >= 0) {
                float bx = x + Ui.f(16), by = y + hgt - Ui.f(10);
                c.drawRect(bx, by, bx + room, by + Ui.f(3), track);
                c.drawRect(bx, by, bx + room * LiveText.progress(now, s.start[i], s.stop[i]), by + Ui.f(3), bar);
            }
            if (l > 0f) {
                ring.setAlpha(Math.round(0xE6 * l));
                Dark.ring(c, r, Ui.f(16), ring);
            }
            c.restore();
            // Under it: the channel's number and name.
            TextPaint np = l > 0.5f ? nameOn : nameP;
            String nm = Ui.fit(lib.number(ch) + "  " + ch.name, np, w);
            c.drawText(nm, x, y + hgt + Ui.f(32) + (LIFT - 1f) * hgt / 2f * l, np);
        }
    }
}
