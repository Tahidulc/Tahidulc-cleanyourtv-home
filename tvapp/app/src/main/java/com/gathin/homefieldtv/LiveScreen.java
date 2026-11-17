package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.media3.common.Format;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.RemoteKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A channel full screen, as the family's TiviMate plays one, in Homefield's own channels and settings.
 *
 * <p>Plain playback shows the picture alone. Its keys, as TiviMate's remote has them (Settings → Remote control):
 * <pre>
 *   INFO               the info card at the foot (InfoCard: the channel, its programme, the date and clock, the
 *                      picture's and sound's chips, what's next), for the panels' 10 seconds
 *   CH+ / CH−          the card for the next / previous channel, without changing channel (OK then changes to it)
 *   Up / Down          the next / previous channel in the list (the card comes up on it; Settings → Remote can make
 *                      them open the live screen's rows of channels instead, as Homefield TV came)
 *   Right              the channel watched last (again: back to this one)
 *   OK                 the overlay: the card at the top, the seek line and the row of tiles (PlayerTiles: TV guide,
 *                      History, the channels watched last); Down from it, the bar (PlayerBar)
 *   OK held, MENU      the bar alone: Search … Exit, the picture and sound's pickers, Add to favourites, the scoreboard
 *                      (over a match), Channel options (from the start, lock): what OK held gave here before
 *   Left               the channel list over the picture (ChannelList.OVERLAY); held, the opaque one with the picture
 *                      in a window (ChannelListScreen)
 *   Up held            History (HistoryScreen); Right held: the programme's description; Down held: Search
 *   Back               the TV guide with this channel in its picture; held, the picture stops (Home, no mini TV)
 *   0–9                a channel by its number; the colour keys and Last as Settings → Remote say; rewind: from the
 *                      start, where the provider keeps the programme
 * </pre>
 * Pressed and held are told apart for Left, Right, Up, Down and Back in plain playback only (TvActivity), so every
 * list and row moves at once. The scoreboard over a match (BoardLayer) has the remote first while its card or menu is
 * up, and its OK at kick-off. Catch-up plays over this screen (CatchupScreen).
 */
final class LiveScreen extends Screen implements LiveTv.Listener, DialogCard.Host {
    /** TiviMate's "Panels timeout, sec": 10; the list, the description and the rows a little longer. */
    static final long PANELS_MS = 10_000L, LIST_MS = 15_000L, DESC_MS = 30_000L, BROWSE_MS = 12_000L;
    static final int NONE = 0, BANNER = 1, OVERLAY = 2, BAR = 3, LIST = 4, DESC = 5, BROWSE = 6;
    private static final float LIFT = 1.07f;

    private Channel start;
    /** From the guide: a programme to show from the archive as this screen opens (-1: none), and its end. */
    private long catchupFrom = -1, catchupEnd;
    private int mode = NONE;
    /** The card's channel on CH+ and CH− (not the one playing); null: the one playing. */
    private Channel peek;
    /** The bar came down from the overlay (Up goes back to its tiles), not by itself. */
    private boolean barWithCard;
    /** Shown before (a screen over this one closed): no card coming up for it. */
    private boolean seen;
    private String digits = "";
    private Panels panels;
    private PlayerTiles tiles;
    private PlayerBar bar;
    private ChannelList list;
    private DialogCard dialog;
    private FrameLayout browse;
    private Backdrop browseBack;
    private BrowseInfo info;
    private BrowseRows rows;
    /** The scoreboard over a match (BoardLayer): it takes the remote first while its menu or its card is up. */
    private BoardLayer board;
    /** The live screen's rows (the playlist's groups) and where the remote is in them. */
    private final List<Row> groups = new ArrayList<>();
    private final Map<String, Integer> rowOf = new HashMap<>();
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
        seen = false;
    }

    /** A channel from a list over this screen (History): played when this screen is next shown, its card up. */
    void tuneFromList(Channel c) {
        if (c == null) return;
        PinScreen.forChannel(tv, c, () -> {
            start = c;
            seen = false;
            if (tv.isTop(this)) shown();
        });
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override View build() {
        FrameLayout root = new FrameLayout(tv);
        board = new BoardLayer(tv);
        browse = new FrameLayout(tv);
        View shade = new View(tv);
        shade.setBackgroundColor(Palette.alpha(Palette.GROUND, 0xE0));
        browseBack = new Backdrop(tv, 0f, 0f, 1920f, 1080f, Pics.DARK_FULL, 0.5f);
        info = new BrowseInfo(tv);
        rows = new BrowseRows(tv);
        View left = new View(tv), foot = new View(tv);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Palette.alpha(Palette.GROUND, 0xD9),
                Palette.alpha(Palette.GROUND, 0x80), Palette.alpha(Palette.GROUND, 0)}));
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Palette.alpha(Palette.GROUND, 0),
                Palette.alpha(Palette.GROUND, 0x33), Palette.alpha(Palette.GROUND, 0xE6)}));
        browse.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(browseBack, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(left, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(foot, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(info, new FrameLayout.LayoutParams(-1, -1));
        browse.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        browse.setVisibility(View.GONE);
        tiles = new PlayerTiles(tv, tilesHost);
        tiles.setVisibility(View.GONE);
        panels = new Panels(tv);
        panels.setVisibility(View.GONE);
        list = new ChannelList(tv, ChannelList.OVERLAY, listHost);
        list.setVisibility(View.GONE);
        bar = new PlayerBar(tv, barHost);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        root.addView(board, new FrameLayout.LayoutParams(-1, -1));
        root.addView(browse, new FrameLayout.LayoutParams(-1, -1));
        root.addView(tiles, new FrameLayout.LayoutParams(-1, -1));
        root.addView(panels, new FrameLayout.LayoutParams(-1, -1));
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -1));
        root.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
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
        boolean fresh = start != null || !seen;
        if (start != null) {
            tv.live.play(start);
            start = null;
        } else if (tv.live.channel() != null) {
            tv.live.play(tv.live.channel());
        }
        seen = true;
        // The card comes up on a channel changed to (TiviMate's "Show info panel at the bottom when switching
        // channels"), not when a screen over this one closes.
        if (fresh) switched();
        else setMode(NONE);
        board.start();
    }

    @Override void hidden() {
        bar.close();
        board.stop();
        if (tv.live.subs.asker == asker) tv.live.subs.asker = null;
        tv.live.removeListener(this);
        h.removeCallbacks(hide);
        h.removeCallbacks(digitsDone);
        h.removeCallbacks(askPicture);
        mode = NONE;
        peek = null;
        Fade.show(panels, false, 0);
        tiles.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        browse.setVisibility(View.GONE);
    }

    @Override public void liveChanged() {
        panels.invalidate();
    }

    @Override void tick() {
        panels.invalidate();
        tiles.invalidate();
        list.refresh();
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
        if (mode == OVERLAY) tiles.set(Library.get(tv).recent(tv.live.channel()), tiles.focus());
        panels.invalidate();
    }

    // ---- what shows -------------------------------------------------------------------------------------------------

    /** What shows: nothing, the card at the foot, the overlay, the bar, the list, the description, or the rows. */
    private void setMode(int m) {
        int was = mode;
        mode = m;
        h.removeCallbacks(hide);
        if (m != BANNER) peek = null;
        if (m != BROWSE) h.removeCallbacks(askPicture);
        if (was == BAR && m != BAR) bar.close();
        boolean card = m == BANNER || m == OVERLAY || m == DESC || m == BAR && barWithCard;
        Fade.show(panels, card || !digits.isEmpty(), card ? 130 : 110);
        Fade.show(tiles, m == OVERLAY, m == OVERLAY ? 0 : 110);
        Fade.show(list, m == LIST, m == LIST ? 0 : 110);
        Fade.show(browse, m == BROWSE, 220);
        switch (m) {
            case BANNER:
            case OVERLAY:
            case BAR:
                h.postDelayed(hide, PANELS_MS);
                break;
            case LIST:
                h.postDelayed(hide, LIST_MS);
                break;
            case DESC:
                h.postDelayed(hide, DESC_MS);
                break;
            case BROWSE:
                h.postDelayed(hide, BROWSE_MS);
                break;
            default:
                break;
        }
        // Subtitles: above the card or the bar while they are up, gone under the lists and rows.
        float above = m == BANNER ? InfoCard.FOOT_Y - 16f : m == OVERLAY || m == BAR ? PlayerTiles.SCRIM_Y + 40f : -1f;
        tv.live.subs.room(m != LIST && m != BROWSE, above);
        panels.invalidate();
    }

    /** The panels' wait starts again (a key in them). */
    private void poke() {
        h.removeCallbacks(hide);
        h.postDelayed(hide, mode == LIST ? LIST_MS : mode == DESC ? DESC_MS : mode == BROWSE ? BROWSE_MS : PANELS_MS);
    }

    /** A channel changed to: the card at the foot for Settings → Player → Channel banner's seconds (none when Off). */
    private void switched() {
        int s = com.gathin.homefieldtv.prefs.PlayerPrefs.bannerSeconds(tv);
        if (s <= 0) {
            setMode(NONE);
            return;
        }
        setMode(BANNER);
        h.removeCallbacks(hide);
        h.postDelayed(hide, s * 1000L);
    }

    private Channel step(Channel from, int dir) {
        Library lib = Library.get(tv);
        if (from == null || lib.all().isEmpty()) return from;
        // Settings → Categories and Parental control: past hidden categories and locked channels, at once.
        return lib.step(from, dir);
    }

    /** A channel now (after its PIN where it is locked): its card comes up. */
    private void tune(Channel c) {
        if (c == null) return;
        if (com.gathin.homefieldtv.prefs.Parental.blocks(tv, c)) {
            PinScreen.forChannel(tv, c, () -> tune(c));
            return;
        }
        tv.live.play(c);
        switched();
    }

    /** The OK overlay: the card at the top, the seek line, the tiles. */
    private void openOverlay() {
        tiles.set(Library.get(tv).recent(tv.live.channel()), PlayerTiles.GUIDE);
        setMode(OVERLAY);
    }

    /** The bar: down from the overlay (with its card), or by itself (OK held, MENU). */
    private void openBar(boolean withCard) {
        barWithCard = withCard;
        bar.open();
        setMode(BAR);
    }

    private void openList() {
        list.open(tv.live.channel());
        setMode(LIST);
    }

    private void openPreviewList() {
        setMode(NONE);
        tv.push(new ChannelListScreen(tv));
    }

    private void openHistory() {
        setMode(NONE);
        tv.push(new HistoryScreen(tv));
    }

    /** CH+ or CH−: the card for the channel after or before the one shown, the one playing playing on. */
    private void peekStep(int dir) {
        Channel from = mode == BANNER && peek != null ? peek : tv.live.channel();
        Channel to = step(from, dir);
        if (to == null) return;
        Channel playing = tv.live.channel();
        peek = playing != null && playing.key.equals(to.key) ? null : to;
        Channel keep = peek;
        setMode(BANNER);
        peek = keep;
        panels.invalidate();
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
        PinScreen.forChannel(tv, c, () -> {
            setMode(NONE);
            tv.push(new CatchupScreen(tv, c, p[0], p[1]));
        });
    }

    /** Automatic subtitles where the TV has no speech recognition of its own: the speech model fetched only on a yes. */
    private void askModel() {
        if (dialog.showing() || bar.isOpen()) {
            // Something else has the remote: asked next time.
            tv.live.subs.askLater();
            return;
        }
        List<RowList.Row> answers = new ArrayList<>();
        answers.add(RowList.Row.item("get", "Download it", null));
        answers.add(RowList.Row.item("no", "Not now", null));
        h.removeCallbacks(hide);
        dialog.show("Automatic subtitles", LiveSubs.askLine(), answers, 0);
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        d.hide(null);
        if ("get".equals(answer.tag)) tv.live.subs.fetch();
        if (mode != NONE) poke();
    }

    private void tuneDigits() {
        h.removeCallbacks(digitsDone);
        String d = digits;
        digits = "";
        panels.invalidate();
        if (mode == NONE) Fade.show(panels, false, 110);
        if (d.isEmpty()) return;
        // Settings → Remote → The channel before: 0 on its own, if chosen.
        if (d.equals("0") && RemoteKeys.previous(tv) == RemoteKeys.PREV_ZERO) {
            act(RemoteKeys.PREVIOUS);
            return;
        }
        int n;
        try {
            n = Integer.parseInt(d);
        } catch (NumberFormatException e) {
            return;
        }
        Channel c = Library.get(tv).numbered(n);
        if (c != null) tune(c);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean holds(int code) {
        if (mode != NONE && mode != BANNER || dialog.showing() || board.takesKeys() || !digits.isEmpty()) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    @Override boolean keyLong(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                openPreviewList();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                openHistory();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                setMode(DESC);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                setMode(NONE);
                tv.openSearch();
                return true;
            case KeyEvent.KEYCODE_BACK:
                tv.stopPlayback();
                return true;
            default:
                return false;
        }
    }

    @Override boolean key(int code, KeyEvent e) {
        if (dialog.showing()) return dialog.key(code);
        if (mode == BAR) {
            if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
                setMode(NONE);
                return true;
            }
            poke();
            return bar.key(code);
        }
        if (mode == LIST) return list.key(code);
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            openBar(false);
            return true;
        }
        if (board.key(code)) return true;
        // Settings → Remote: the colour keys and the Last key.
        int keyAct = RemoteKeys.action(tv, code);
        if (keyAct != RemoteKeys.NOTHING && e.getRepeatCount() == 0) return act(keyAct);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits += (char) ('0' + code - KeyEvent.KEYCODE_0);
            h.removeCallbacks(digitsDone);
            if (digits.length() >= 4) tuneDigits();
            else h.postDelayed(digitsDone, RemoteKeys.waitMs(tv));
            Fade.show(panels, true, 110);
            panels.invalidate();
            return true;
        }
        boolean reversed = RemoteKeys.reversed(tv);
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                // TiviMate: the card for the next or previous channel, without changing channel.
                if (mode == BROWSE) browseStep(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
                else peekStep(RemoteKeys.zapDir(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1, reversed));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (mode == OVERLAY) {
                    poke();
                    return tiles.key(code);
                }
                if (mode == BROWSE) browseStep(dir);
                else if (mode == DESC) setMode(NONE);
                // Settings → Remote → Up and Down: change channel (as TiviMate), or the live screen's rows.
                else if (RemoteKeys.upDown(tv) == 1) tune(step(tv.live.channel(), RemoteKeys.zapDir(dir, reversed)));
                else openBrowse(dir);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mode == OVERLAY) {
                    poke();
                    return tiles.key(code);
                }
                if (mode == BROWSE) browseAlong(1);
                else if (mode == DESC) setMode(NONE);
                else act(RemoteKeys.PREVIOUS);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mode == OVERLAY) {
                    poke();
                    return tiles.key(code);
                }
                if (mode == BROWSE) browseAlong(-1);
                else if (mode == DESC) setMode(NONE);
                else openList();
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                // Homefield's: the programme on now from its start, where the provider keeps it.
                if (e.getRepeatCount() == 0) {
                    Channel c = mode == BROWSE ? browseChannel() : tv.live.channel();
                    if (fromTheStart(c) != null) startOver(c);
                    else setMode(BANNER);
                }
                return true;
            case KeyEvent.KEYCODE_INFO:
                if (mode == BANNER && peek == null) setMode(NONE);
                else setMode(BANNER);
                return true;
            default:
                return false;
        }
    }

    @Override void ok() {
        if (dialog.showing()) {
            dialog.ok();
            return;
        }
        if (mode == BAR) {
            poke();
            bar.ok();
            return;
        }
        if (mode == LIST) {
            list.ok();
            return;
        }
        if (board.ok()) return;
        if (!digits.isEmpty()) {
            tuneDigits();
            return;
        }
        switch (mode) {
            case OVERLAY:
                tiles.ok();
                return;
            case BROWSE:
                tune(browseChannel());
                return;
            case DESC:
                setMode(NONE);
                return;
            case BANNER:
                if (peek != null) {
                    // The channel shown on CH+ or CH−: changed to.
                    tune(peek);
                    return;
                }
                // fall through: as in plain playback
            default:
                if (tv.live.state() == LiveTv.FAILED) {
                    tv.live.tryAgain();
                    setMode(BANNER);
                    return;
                }
                // Settings → Remote → OK: the overlay (as TiviMate), the channel list, the guide or the bar.
                act(RemoteKeys.ok(tv));
        }
    }

    @Override void okLong() {
        if (dialog.showing() || mode == BAR || mode == LIST) return;
        // TiviMate: OK held is the bar (its menu); Settings → Remote → OK held may give another.
        int a = RemoteKeys.okLong(tv);
        if (a == RemoteKeys.CHANNEL_MENU || a == RemoteKeys.PLAYER_MENU) openBar(false);
        else act(a);
    }

    /** What Settings → Remote gave a key or OK, on the channel playing; true when it did something. */
    private boolean act(int action) {
        Channel c = tv.live.channel();
        switch (action) {
            case RemoteKeys.GUIDE:
                setMode(NONE);
                tv.toGuide();
                return true;
            case RemoteKeys.LIST:
                openList();
                return true;
            case RemoteKeys.PREVIOUS: {
                // The channel watched last: Right, as TiviMate's ("Turn on most recent channel").
                List<Channel> was = Library.get(tv).recent(c);
                if (!was.isEmpty()) tune(was.get(0));
                return true;
            }
            case RemoteKeys.FAVOURITE: {
                if (c == null) return true;
                boolean on = Library.get(tv).toggleFavourite(c);
                android.widget.Toast.makeText(tv, on ? c.name + " added to your favourites" : c.name + " taken off your favourites",
                        android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
            case RemoteKeys.PLAYER_MENU:
            case RemoteKeys.CHANNEL_MENU:
                openBar(false);
                return true;
            case RemoteKeys.SUBTITLES:
                barWithCard = false;
                bar.openPicker(PlayerBar.CAPTIONS);
                setMode(BAR);
                return true;
            case RemoteKeys.START_OVER:
                if (fromTheStart(c) != null) startOver(c);
                else setMode(BANNER);
                return true;
            case RemoteKeys.SEARCH:
                setMode(NONE);
                tv.openSearch();
                return true;
            case RemoteKeys.SETTINGS:
                setMode(NONE);
                tv.openSettings();
                return true;
            case RemoteKeys.BANNER:
                openOverlay();
                return true;
            default:
                return false;
        }
    }

    @Override boolean back() {
        if (dialog.showing()) {
            dialog.hide(null);
            return true;
        }
        if (mode == BAR) {
            bar.back();
            return true;
        }
        if (mode == LIST) return list.back();
        if (board.back()) return true;
        if (!digits.isEmpty()) {
            digits = "";
            h.removeCallbacks(digitsDone);
            setMode(mode);
            return true;
        }
        if (mode != NONE) {
            setMode(NONE);
            return true;
        }
        // TiviMate: Back from the picture is the TV guide, this channel in its picture.
        tv.toGuide();
        return true;
    }

    // ---- what the bar, the tiles and the list ask ----------------------------------------------------------------

    /** The bar's own: what its buttons do here. */
    private final PlayerBar.Host barHost = new PlayerBar.Host() {
        @Override public Channel channel() {
            return tv.live.channel();
        }

        @Override public void act(int item) {
            switch (item) {
                case PlayerBar.SEARCH:
                    tv.openSearch();
                    break;
                case PlayerBar.CHANNELS:
                    openPreviewList();
                    break;
                case PlayerBar.HISTORY:
                    openHistory();
                    break;
                case PlayerBar.MOVIES:
                    Rail.go(tv, Rail.FILMS, -1);
                    break;
                case PlayerBar.SHOWS:
                    Rail.go(tv, Rail.SHOWS, -1);
                    break;
                case PlayerBar.RECORDINGS:
                    Rail.go(tv, Rail.RECORDINGS, -1);
                    break;
                case PlayerBar.MY_LIST:
                    Rail.go(tv, Rail.MY_LIST, -1);
                    break;
                case PlayerBar.SCOREBOARD:
                    board.okLong();
                    break;
                case PlayerBar.SETTINGS:
                    tv.openSettings();
                    break;
                case PlayerBar.EXIT:
                    // TiviMate's Exit: out of the app (the picture stops as it goes).
                    tv.moveTaskToBack(true);
                    break;
                case PlayerBar.START_OVER:
                    startOver(tv.live.channel());
                    break;
                default:
                    break;
            }
        }

        @Override public boolean hasBoard() {
            return board.hasGame();
        }

        @Override public boolean canStartOver() {
            return fromTheStart(tv.live.channel()) != null;
        }

        @Override public boolean barUp() {
            if (!barWithCard) return false;
            openOverlay();
            return true;
        }

        @Override public void closed() {
            if (mode == BAR) setMode(NONE);
        }
    };

    /** The overlay's tiles: the guide, History, a channel watched last. */
    private final PlayerTiles.Host tilesHost = new PlayerTiles.Host() {
        @Override public void openGuide() {
            setMode(NONE);
            tv.toGuide();
        }

        @Override public void openHistory() {
            LiveScreen.this.openHistory();
        }

        @Override public void tune(Channel c) {
            LiveScreen.this.tune(c);
        }

        @Override public boolean edge(int dir) {
            // Down: the bar, the card staying.
            if (dir == Ui.DOWN) openBar(true);
            return true;
        }

        @Override public float[] seek() {
            Channel c = tv.live.channel();
            Guide.Schedule s = c == null ? null : Library.get(tv).schedule(c.key);
            long now = System.currentTimeMillis();
            int i = s == null ? -1 : s.at(now);
            float p = i < 0 ? 0f : LiveText.progress(now, s.start[i], s.stop[i]);
            return new float[]{p, p, 1f};
        }
    };

    /** The channel list over the picture. */
    private final ChannelList.Host listHost = new ChannelList.Host() {
        @Override public void tune(Channel c) {
            // It plays and the list stays (TiviMate's "Stay on list when switching channels").
            PinScreen.forChannel(tv, c, () -> {
                tv.live.play(c);
                list.refresh();
            });
        }

        @Override public void full() {
            setMode(NONE);
        }

        @Override public void closed() {
            setMode(NONE);
        }

        @Override public void poke() {
            LiveScreen.this.poke();
        }
    };

    /** The chips for the channel playing: the picture's quality, its frames a second, the sound (FHD, 50 FPS, STEREO). */
    static List<String> chips(LiveTv live) {
        List<String> out = new ArrayList<>(3);
        String[] f = live.format();
        if (!f[0].isEmpty()) out.add(f[0]);
        if (f.length > 2 && !f[2].isEmpty()) out.add(f[2].toUpperCase(Locale.ROOT));
        Format a = live.audioFormat();
        if (a != null) {
            String[] w = PlayerBar.audioWords(a.language, null, a.channelCount);
            if (w[1] != null) out.add(w[1].toUpperCase(Locale.ROOT));
        }
        return out;
    }

    // ---- the cards: at the foot, at the top, the description, the number typed ----------------------------------------

    private final class Panels extends View {
        private final InfoCard card;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titleP = Type.paint(36f, Type.MEDIUM, Palette.TEXT1), lineP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint descP = Type.paint(26f, Type.REGULAR, Palette.TEXT2), digitP = Type.paint(Type.HERO, Type.MEDIUM, Palette.TEXT1);
        private final TextPaint nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();
        private String descKey = "";
        private StaticLayout descTitle, descText;

        Panels(Context c) {
            super(c);
            card = new InfoCard(this);
        }

        @Override protected void onDraw(Canvas c) {
            Channel playing = tv.live.channel();
            long now = System.currentTimeMillis();
            if (mode == BANNER) {
                Channel ch = peek != null ? peek : playing;
                card.draw(c, InfoCard.FOOT_Y, ch, now, false, ch == playing ? chips(tv.live) : null);
            } else if (mode == OVERLAY || mode == BAR && barWithCard) {
                card.draw(c, InfoCard.TOP_Y, playing, now, false, chips(tv.live));
            } else if (mode == DESC && playing != null) {
                drawDescription(c, playing, now);
            }
            if (!digits.isEmpty()) drawDigits(c);
        }

        /** Right held: the programme's card at the top right, its whole description. */
        private void drawDescription(Canvas c, Channel ch, long now) {
            Guide.Schedule s = Library.get(getContext()).schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            String title = i >= 0 ? s.title[i] : ch.name, desc = i >= 0 ? s.desc[i] : "";
            String k = ch.key + "|" + (i >= 0 ? s.start[i] : 0);
            float x0 = 1152f, y0 = 48f, x1 = 1872f, inner = x1 - x0 - 64f;
            if (!k.equals(descKey)) {
                descKey = k;
                descTitle = InfoCard.lines(title, titleP, Ui.px(inner), 2, 42f / 36f);
                descText = desc.isEmpty() ? null : InfoCard.lines(desc, descP, Ui.px(inner), 5, 33f / 26f);
            }
            float th = descTitle.getHeight() / Ui.s, dh = descText == null ? 0f : descText.getHeight() / Ui.s + 16f;
            float hgt = 32f + th + 18f + (i >= 0 ? 40f : 0f) + dh + 28f;
            r.set(Ui.f(x0), Ui.f(y0), Ui.f(x1), Ui.f(y0 + hgt));
            fill.setColor(Palette.PLAYBACK);
            c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
            float tx = Ui.f(x0 + 32f), y = y0 + 32f;
            titleP.setColor(Palette.TEXT1);
            Ui.draw(c, descTitle, tx, Ui.f(y));
            y += th + 18f;
            if (i >= 0) {
                lineP.setColor(Palette.TEXT2);
                String sp = LiveText.span(s.start[i], s.stop[i]);
                float base = Ui.f(y + 28f);
                c.drawText(sp, tx, base, lineP);
                float bx = tx + lineP.measureText(sp) + Ui.f(25);
                Bars.thin(c, bx, base - Ui.f(10), Ui.f(80), Ui.f(6), LiveText.progress(now, s.start[i], s.stop[i]));
                if (s.stop[i] > now) c.drawText(InfoCard.minutes(s.stop[i] - now), bx + Ui.f(104), base, lineP);
                y += 40f + 16f;
            }
            if (descText != null) Ui.draw(c, descText, tx, Ui.f(y));
        }

        /** The number being typed, at the top right, and the channel it is so far. */
        private void drawDigits(Canvas c) {
            r.set(Ui.f(1552f), Ui.f(48f), Ui.f(1872f), Ui.f(168f));
            fill.setColor(Palette.PLAYBACK);
            c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
            String d = digits.length() >= 3 ? digits : (digits + "---").substring(0, 3);
            digitP.setColor(Palette.TEXT1);
            c.drawText(d, Ui.f(1584f), Ui.f(106f), digitP);
            int n;
            try {
                n = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return;
            }
            Channel ch = Library.get(getContext()).numbered(n);
            nameP.setColor(Palette.TEXT2);
            if (ch != null) c.drawText(Ui.fit(ch.name, nameP, Ui.f(256f)), Ui.f(1584f), Ui.f(148f), nameP);
        }
    }

    // ---- the live screen's rows (Settings → Remote → Up and Down: the rows, as Homefield TV came) ------------------------

    /**
     * The playlist's groups as rows: in the family's order and names, hidden ones left out (Settings → Categories). Each
     * group's channels as the library made them, so this goes through the groups, never through every channel.
     */
    private void buildRows() {
        groups.clear();
        rowOf.clear();
        Library lib = Library.get(tv);
        for (String g : com.gathin.homefieldtv.prefs.Categories.arranged(tv, lib.rowGroups())) {
            rowOf.put(g, groups.size());
            groups.add(new Row(com.gathin.homefieldtv.prefs.Categories.label(tv, g), lib.group(g).channels));
        }
    }

    /** The remote on a channel's place in the rows (its group's row, its place in it), found at once. */
    private void find(Channel c) {
        row = 0;
        col = 0;
        if (c == null) return;
        Integer r = rowOf.get(c.group);
        int k = r == null ? -1 : Library.get(tv).group(c.group).indexOf(c);
        if (k < 0 || r >= groups.size() || k >= groups.get(r).channels.size()) return;
        if (!groups.get(r).channels.get(k).key.equals(c.key)) return;
        row = r;
        col = k;
    }

    private Channel browseChannel() {
        if (row < 0 || row >= groups.size()) return null;
        List<Channel> l = groups.get(row).channels;
        return col >= 0 && col < l.size() ? l.get(col) : null;
    }

    /** Up or Down while watching (Settings → Remote): the live screen, on the channel before or after the one playing. */
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
            poke();
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
        Guide.Schedule s = c == null ? null : Library.get(tv).schedule(c.key);
        browseBack.set(ProgrammePictures.peek(c, s, s == null ? -1 : s.at(System.currentTimeMillis())));
        h.removeCallbacks(askPicture);
        if (Exec.inline) askPicture();
        else h.postDelayed(askPicture, ProgrammePictures.REST_MS);
        poke();
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

    /** Along the top what's on after, then the channel's logo, the programme's title, the channel, times and story. */
    private final class BrowseInfo extends View {
        private Channel ch;
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), sep = new Paint();
        private final TextPaint atP = Type.paint(Type.CHIP, Type.MEDIUM, Palette.TEXT2), laterP = Type.paint(Type.CHIP, Type.REGULAR, Palette.TEXT2);
        private final TextPaint titleP = Type.paint(56f, Type.MEDIUM, Palette.TEXT1), chanP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final TextPaint timeP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2), descP = Type.paint(26f, Type.REGULAR, Palette.TEXT2);
        private final TextPaint logoName = Type.paint(34f, Type.BOLD, Palette.TEXT1);
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
            sep.setColor(Palette.DIVIDER);
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
                c.drawRect(x, y - Ui.f(18), x + Math.max(1f, Ui.f(2f)), y + Ui.f(4), sep);
                x += Ui.f(22);
            }
            Bitmap logo = Pics.get(ch.logo, Ui.px(200), Ui.px(72), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) c.drawBitmap(logo, x0, Ui.f(100) + (Ui.f(72) - logo.getHeight()) / 2f, bmp);
            else c.drawText(Ui.fit(ch.name, logoName, Ui.f(600)), x0, Ui.f(150), logoName);
            String title = i >= 0 ? s.title[i] : ch.name;
            c.drawText(Ui.fit(title, titleP, Ui.f(1400)), x0, Ui.f(250), titleP);
            String chan = lib.number(ch) + "  " + ch.name + (ch.group.isEmpty() ? "" : "  ·  " + ch.group);
            c.drawText(Ui.fit(chan, chanP, Ui.f(1400)), x0, Ui.f(294), chanP);
            if (i >= 0) {
                String t = LiveText.span(s.start[i], s.stop[i]) + "  ·  " + InfoCard.minutes(Math.max(0, s.stop[i] - now));
                c.drawText(t, x0, Ui.f(334), timeP);
                float bx = x0 + timeP.measureText(t) + Ui.f(20);
                Bars.thin(c, bx, Ui.f(326), Ui.f(120), Ui.f(6), LiveText.progress(now, s.start[i], s.stop[i]));
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
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), tileBack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint headP = Type.paint(26f, Type.MEDIUM, Palette.TEXT1), headNext = Type.paint(Type.SMALL, Type.MEDIUM, Palette.TEXT2);
        private final TextPaint nameP = Type.paint(20f, Type.REGULAR, Palette.TEXT2), nameOn = Type.paint(20f, Type.MEDIUM, Palette.TEXT1);
        private final TextPaint tTitle = Type.paint(Type.CHIP, Type.MEDIUM, Palette.TEXT1), tDesc = Type.paint(18f, Type.REGULAR, Palette.TEXT2);
        private final TextPaint logoName = Type.paint(26f, Type.BOLD, Palette.TEXT1);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private final Runnable rest = () -> {
            settled = true;
            invalidate();
        };

        BrowseRows(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
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
            c.drawText(Ui.fit(g.name, hp, Ui.f(1500)), Ui.f(X0), Ui.f(y), hp);
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
            // Settings → Appearance → Channel tiles: Channel logos leaves the programme's picture out.
            Bitmap pic = url == null || url.isEmpty() || com.gathin.homefieldtv.prefs.Look.logos ? null
                    : Pics.get(url, Ui.px(TILE_W), Ui.px(TILE_H), Pics.CARD, redraw);
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
                tileBack.setColor(Palette.PANEL);
                c.drawRoundRect(r, Ui.f(12), Ui.f(12), tileBack);
                Bitmap logo = Pics.get(ch.logo, Ui.px(170), Ui.px(62), logoMode, redraw);
                if (logo != null) c.drawBitmap(logo, x + (w - logo.getWidth()) / 2f, y + Ui.f(58) - logo.getHeight() / 2f, bmp);
                else {
                    String nm = Ui.fit(ch.name, logoName, w - Ui.f(32));
                    c.drawText(nm, x + (w - logoName.measureText(nm)) / 2f, y + Ui.f(66), logoName);
                }
            }
            float room = w - Ui.f(32);
            String title = i >= 0 ? s.title[i] : ch.name;
            c.drawText(Ui.fit(title, tTitle, room), x + Ui.f(16), y + hgt - Ui.f(46), tTitle);
            String about = i < 0 ? "No programme information" : s.desc[i].isEmpty() ? LiveText.span(s.start[i], s.stop[i]) : s.desc[i];
            c.drawText(Ui.fit(about, tDesc, room), x + Ui.f(16), y + hgt - Ui.f(22), tDesc);
            if (i >= 0 && com.gathin.homefieldtv.prefs.Look.progress) {
                Bars.thin(c, x + Ui.f(16), y + hgt - Ui.f(9), room, Ui.f(4), LiveText.progress(now, s.start[i], s.stop[i]));
            }
            if (l > 0f) {
                // The remote's tile: its edge in the light accent, as the player's tiles.
                ring.setColor(Palette.alpha(Palette.ACCENT_LIGHT, Math.round(0xFF * l)));
                ring.setStrokeWidth(Math.max(1f, Ui.f(2f)));
                c.drawRoundRect(r, Ui.f(12), Ui.f(12), ring);
            }
            c.restore();
            TextPaint np = l > 0.5f ? nameOn : nameP;
            String nm = Ui.fit((com.gathin.homefieldtv.prefs.Look.numbers ? lib.number(ch) + "  " : "") + ch.name, np, w);
            c.drawText(nm, x, y + hgt + Ui.f(32) + (LIFT - 1f) * hgt / 2f * l, np);
        }
    }
}
