package com.gathin.homefieldtv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * The app: Home first, with the screens opened from it stacked over it. Three layers: the screens, the live picture
 * over them (the mini TV, or full screen), and what goes over the live picture (the channel banner).
 */
public final class TvActivity extends Activity {
    FrameLayout screens, video, over;
    LiveTv live;
    /** 07's shell: Homefield's mark, the tab bar or a screen's back line, the clock (over the pages, under overlays). */
    private TabBar tabs;
    private final List<Screen> stack = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean resumed, okDown, okLong;

    private final Runnable minute = new Runnable() {
        @Override public void run() {
            for (Screen s : new ArrayList<>(stack)) s.tick();
            nextMinute();
            // The memory log's line for this minute (MemLog: written off the main thread).
            Screen t = top();
            MemLog.write(TvActivity.this, live.buffered(), t == null ? "-" : t.getClass().getSimpleName());
        }
    };
    private final Runnable channels = () -> {
        for (Screen s : new ArrayList<>(stack)) s.channelsChanged();
        watchReminder();
    };
    /** A reminder's card answered with OK before the playlists were in: its channel, played once they are. */
    private String reminderKey;
    private final Runnable catalog = () -> {
        for (Screen s : new ArrayList<>(stack)) s.catalogChanged();
        // Read again (a refresh) while only live TV is on screen: let go again in a while.
        catalogueFollows();
    };
    /** An update found, gone or under way: the dots on Settings drawn again, Settings told. */
    private final Runnable updates = () -> {
        for (Screen s : new ArrayList<>(stack)) {
            if (!s.built()) continue;
            redraw(s.view());
            s.updatesChanged();
        }
    };
    /** The channels' logos changed (a new list of logos, a channel's own choice, Settings › Logos): drawn again. */
    private final Runnable logos = () -> {
        for (Screen s : new ArrayList<>(stack)) {
            if (!s.built()) continue;
            redraw(s.view());
            if (s instanceof SettingsScreen) ((SettingsScreen) s).logosChanged();
        }
    };

    private static void redraw(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) redraw(g.getChildAt(i));
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        // Settings → Appearance, read before any screen draws; and whether this start is a fresh one (General → Start on).
        com.gathin.homefieldtv.prefs.Look.load(this);
        boolean fresh = state == null && reopenSettings < 0;
        // The TV launcher's channel (PlayChannel): the picture first. Only what playing needs is made now; Home, the
        // films' catalogue and the update check once the channel is on (afterChannel), What's new and a problem's note
        // at the next ordinary start, the playlists' refresh once the picture has started (Library.holdRefresh).
        channelFirst = reopenSettings < 0 && PlayChannel.asks(getIntent());
        // The theme's colours before any screen is made, its dark ground behind them all.
        Theme.apply(this);
        // Settings → Appearance → Animations: Full or Lite (Automatic: Lite on a slower TV), before any screen draws.
        Motion.load(this);
        getWindow().setBackgroundDrawable(windowGround = new WindowGround(Dark.GROUND));
        Dark.init(this);
        PosterArt.init(this);
        Pics.init(this);
        // The family's own edits to their channels, and the new logos (their list read, asked for again weekly).
        com.gathin.homefieldtv.prefs.ChannelEdits.load(this);
        com.gathin.homefieldtv.engine.LogoPack.failed = Pics::failed;
        com.gathin.homefieldtv.engine.LogoPack.init(this);
        com.gathin.homefieldtv.engine.LogoPack.addListener(logos);
        Cast.hello(this);
        // The family's playlists, favourites, Continue watching and settings, through the launcher on this TV.
        com.gathin.homefieldtv.sync.TvSync.start(this);
        FrameLayout root = new FrameLayout(this);
        screens = new FrameLayout(this);
        video = new FrameLayout(this);
        over = new FrameLayout(this);
        root.addView(screens, full());
        root.addView(video, full());
        root.addView(over, full());
        setContentView(root);
        // Each screen's frame times, for the memory log and for Automatic's Lite (FrameStats).
        FrameStats.start(this);
        live = new LiveTv(this, video);
        live.addListener(awake);
        Library lib = Library.get(this);
        if (channelFirst) lib.holdRefresh(PlayChannel.QUIET_MS);
        lib.addListener(channels);
        lib.ensure();
        Catalog cat = Catalog.get(this);
        cat.addListener(catalog);
        if (!channelFirst) cat.ensure();
        // The shell first, so every page is put under it (push raises it); the app starts with the remote on its Home.
        tabs = new TabBar(this);
        screens.addView(tabs, full());
        // A channel from the launcher: Home in its place under it, made only when it is first needed (or afterChannel).
        if (channelFirst) homeUnbuilt(new HomeScreen(this));
        else push(new HomeScreen(this));
        tabs.enter();
        // Homefield TV's own updates: the folder made (for one to be put in), a quiet look, What's new once after one.
        Updates.folder(this);
        Updates.addListener(updates);
        if (!channelFirst) {
            Updates.check(this, null);
            // Automatic updates: the update address looked at as the app opens (Settings › About › Updates).
            AutoUpdate.opened(this, true);
            // If something goes wrong: a line once when the app had a problem last time (its note in Settings › About).
            Exec.io(() -> {
                if (com.gathin.homefieldtv.engine.Problems.takeUntold() == null) return;
                Exec.main(() -> {
                    if (!isFinishing()) {
                        android.widget.Toast.makeText(this, "Homefield TV had a problem last time. Settings › About has a note of it.",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            });
            String[] news = Updates.whatsNew(this);
            if (news != null && reopenSettings < 0) push(new WhatsNewScreen(this, news[0], news[1]));
        }
        // Started again for a new theme: back on Settings where it was chosen, its panel there at once.
        if (reopenSettings >= 0) {
            push(new SettingsScreen(this, reopenSettings));
            reopenSettings = -1;
        }
        handleCast(getIntent());
        handleReminder(getIntent());
        // The TV launcher's channel (PlayChannel): straight on to that channel, Home kept out of sight meanwhile.
        boolean asked = PlayChannel.handle(this, getIntent(), true);
        if (channelFirst) {
            h.removeCallbacks(afterChannel);
            h.postDelayed(afterChannel, AFTER_CHANNEL_MS);
        }
        // The cards the launcher shows for this app (ChannelCards, served by ChannelCardsProvider) kept up to date.
        ChannelCards.start(this);
        // Reminders' alarm made again (Android forgets it when the app is updated or stopped).
        ReminderAlarm.schedule(this);
        // Settings → General → Start on: Live TV, the guide, films or TV shows over Home (not over What's new, a cast or
        // the channel a reminder's card or the launcher's card was for, still waiting for the channels to be read).
        // (Not in the JVM tests, which drive the screens from Home themselves; StartUp is tested on its own.)
        if (fresh && !asked && top() instanceof HomeScreen && reminderKey == null && !Guard.underTest()) StartUp.open(this);
    }

    // ---- the launcher's channel first (PlayChannel) -----------------------------------------------------------------

    /** What the launcher's channel put off is done this long after it was asked for (the picture up by then). */
    static final long AFTER_CHANNEL_MS = 6_000L;
    /** A channel from the launcher is on its way (from onCreate or onNewIntent until onResume): what can wait, waits. */
    private boolean channelFirst;
    /** Started afresh for the launcher's channel: Updates' quiet look still to come (afterChannel). */
    private boolean checkLater;
    private final Runnable afterChannel = this::afterChannel;

    /**
     * The launcher's channel is on: what it put off, done now. The films' catalogue read (and refreshed when due), the
     * update folder looked in, Home made out of sight under the picture so Back to it is at once. What's new and a note of
     * a problem last time wait for the next ordinary start (a page over the channel would cover it).
     */
    private void afterChannel() {
        h.removeCallbacks(afterChannel);
        if (isFinishing() || isDestroyed()) return;
        // The films and series once a screen shows them (not while the launcher's channel plays: Catalog.sleep).
        catalogueFollows();
        if (checkLater) {
            checkLater = false;
            Updates.check(this, null);
        }
        homeView();
    }

    /** Home put in its place at the foot of the stack without its page made (homeView makes it when first needed). */
    private void homeUnbuilt(HomeScreen home) {
        stack.add(home);
        checkLater = true;
        if (tabs != null) tabs.pushed(home);
    }

    /**
     * Home's page, made and put in its place under every other page the first time it is needed (a start for the
     * launcher's channel leaves it till then): shown when it is on top, else out of sight.
     */
    View homeView() {
        Screen home = stack.get(0);
        if (home.built()) return home.view();
        View v = home.view();
        v.setVisibility(top() == home ? View.VISIBLE : View.GONE);
        screens.addView(v, 0, full());
        return v;
    }

    /** The phone stopped what it sent: Home comes back without its mini TV playing, once. */
    private boolean miniTvHeld;

    /** Whether Home should leave its mini TV off this time (the phone's Stop); asked once. */
    boolean takeMiniTvHeld() {
        boolean held = miniTvHeld;
        miniTvHeld = false;
        return held;
    }

    /** Settings' section to open again after the screens start again for a theme (-1: none). */
    private static int reopenSettings = -1;

    /** A new theme: every screen made again in its colours, Settings open where it was. */
    void restartLook(int section) {
        reopenSettings = section;
        recreate();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Opened again from the TV's Home (Start on: Last channel plays it as the app comes back: onResume).
        // A reminder's card or the phone's channel brings its own channel instead.
        boolean own = intent == null || intent.hasExtra(ReminderCard.EXTRA_KEY) || intent.hasExtra(Cast.EXTRA_KEY)
                || intent.hasExtra(Cast.EXTRA_STOP);
        if (!own && Intent.ACTION_MAIN.equals(intent.getAction())) reopened = true;
        else leftForOwn = true;
        handleCast(intent);
        handleReminder(intent);
        // The launcher's channel: changed in place when the picture is up; what can wait (onResume's catalogue and
        // update look) waits until it is on (afterChannel).
        if (PlayChannel.handle(this, intent, false)) {
            channelFirst = true;
            h.removeCallbacks(afterChannel);
            h.postDelayed(afterChannel, AFTER_CHANNEL_MS);
        }
    }

    /** OK on a reminder's card: its channel full screen (once the playlists are in); an old one is let go. */
    private void handleReminder(Intent i) {
        if (i == null || !i.hasExtra(ReminderCard.EXTRA_KEY)) return;
        String key = i.getStringExtra(ReminderCard.EXTRA_KEY);
        long at = i.getLongExtra(ReminderCard.EXTRA_AT, 0L);
        i.removeExtra(ReminderCard.EXTRA_KEY);
        if (key == null || key.isEmpty() || Math.abs(System.currentTimeMillis() - at) > 10 * 60_000L) return;
        reminderKey = key;
        watchReminder();
    }

    private void watchReminder() {
        if (reminderKey == null) return;
        Channel c = Library.get(this).byKey(reminderKey);
        if (c == null) return;
        reminderKey = null;
        watch(c);
    }

    /** A channel sent from the family's phone (Cast): watched at once, a note of where it came from. */
    private void handleCast(Intent i) {
        if (i != null && i.hasExtra(Cast.EXTRA_STOP)) {
            // The phone's Stop: only the channel it sent, and only while that is what plays.
            String key = i.getStringExtra(Cast.EXTRA_STOP);
            long at = i.getLongExtra(Cast.EXTRA_AT, 0L);
            i.removeExtra(Cast.EXTRA_STOP);
            Channel playing = live.channel();
            if (Math.abs(System.currentTimeMillis() - at) <= Cast.FRESH_MS && playing != null && playing.key.equals(key)
                    && top() instanceof LiveScreen) {
                miniTvHeld = true;
                live.stop();
                home();
            }
            return;
        }
        if (i == null || !i.hasExtra(Cast.EXTRA_KEY)) return;
        String key = i.getStringExtra(Cast.EXTRA_KEY), from = i.getStringExtra(Cast.EXTRA_FROM);
        long at = i.getLongExtra(Cast.EXTRA_AT, 0L);
        i.removeExtra(Cast.EXTRA_KEY);
        if (Math.abs(System.currentTimeMillis() - at) > Cast.FRESH_MS) return;
        Channel c = Library.get(this).byKey(key);
        if (c == null) return;
        watch(c);
        android.widget.Toast.makeText(this, "Playing " + ChannelEdits.name(c) + (from == null || from.isEmpty() ? "" : " from " + from),
                android.widget.Toast.LENGTH_LONG).show();
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /**
     * While a channel or a programme from the archive plays (or is starting, or reconnecting), the screen is kept on, so
     * the TV's screensaver never comes over what is being watched; stopped, failed or paused, the TV may sleep as usual.
     */
    private final LiveTv.Listener awake = this::keepAwake;
    /** Whether the screen is kept on now: the window's flags are changed only when this changes (each change is a call to Android's window manager). */
    private Boolean awakeOn;

    private void keepAwake() {
        // The picture coming or going: whether the window's own ground is covered.
        covered();
        int s = live.state();
        boolean on = !live.held() && (s == LiveTv.LOADING || s == LiveTv.PLAYING || s == LiveTv.RETRYING);
        if (awakeOn != null && awakeOn == on) return;
        awakeOn = on;
        if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Android is short of memory: the pictures not on screen let go (all of them once the app is out of sight), the
     * guide kept only around now (read back from its disk copy when the app is used again: Library.trim), Sky's
     * programme lists and the text measured for the screens forgotten.
     */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Pics.trim(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            Library.get(this).trim(System.currentTimeMillis());
            com.gathin.homefieldtv.engine.SkyGuide.trim();
            // Only live TV on screen: the films and series let go now, not in a while.
            if (onlyLiveTv()) Catalog.get(this).sleep();
        }
    }

    // ---- the films and series while only live TV is on screen (Catalog.sleep) ---------------------------------

    /** How long only live TV is on screen before the films and series are let go (read back when a screen shows them). */
    static final long CATALOGUE_SLEEP_MS = 30_000L;
    private final Runnable catalogueSleep = () -> {
        if (onlyLiveTv()) Catalog.get(this).sleep();
    };

    /**
     * Whether only live TV is on screen: the screen that decides is live TV's, and no screen that shows the films and
     * series is open under it but Home (Movies under the guide would show nothing while they were read back).
     */
    private boolean onlyLiveTv() {
        if (!liveOnly(beneath(top()))) return false;
        for (int i = 1; i < stack.size(); i++) if (showsCatalogue(stack.get(i))) return false;
        return true;
    }

    /**
     * Whether a screen shows only live TV (the picture full screen, a programme from the archive, the guide, the channel
     * lists, what was watched): nothing on it needs the films and series.
     */
    static boolean liveOnly(Screen s) {
        return s instanceof LiveScreen || s instanceof CatchupScreen || s instanceof GuideScreen || s instanceof ChannelListScreen
                || s instanceof HistoryScreen;
    }

    /** Whether a screen shows the films and series: Home, Movies and TV shows, a title, Search, My list, Settings. */
    static boolean showsCatalogue(Screen s) {
        return s instanceof HomeScreen || s instanceof BrowseScreen || s instanceof TitleScreen || s instanceof SearchScreen
                || s instanceof MyListScreen || s instanceof ListScreen || s instanceof SettingsScreen;
    }

    /** The screen that decides: an overlay's (a menu over the guide) is the screen under it, unless it shows them itself. */
    private Screen beneath(Screen s) {
        while (s != null && s.overlay() && !showsCatalogue(s) && below(s) != null) s = below(s);
        return s;
    }

    /**
     * The top screen changed (or the app came back): the films and series read back for a screen that shows them, or let
     * go once only live TV has been on screen CATALOGUE_SLEEP_MS (the TCL's 200,000 titles are some 70 MB of its 192: room
     * for the player, and far fewer collections while a channel plays).
     */
    private void catalogueFollows() {
        h.removeCallbacks(catalogueSleep);
        Screen t = beneath(top());
        if (onlyLiveTv()) {
            if (!Exec.inline) h.postDelayed(catalogueSleep, CATALOGUE_SLEEP_MS);
        } else if (showsCatalogue(t)) {
            Catalog.get(this).wake();
        }
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        onTrimMemory(TRIM_MEMORY_COMPLETE);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        Cast.hold(this);
        Updates.resumed(this);
        // Coming back for the launcher's channel: no update looked for (its installer would come over the picture), the
        // catalogue once the channel is on (afterChannel).
        boolean channel = channelFirst;
        channelFirst = false;
        if (!channel) AutoUpdate.opened(this, false);
        // A reminder due now comes as a panel inside the app (ReminderScreen).
        ReminderCard.front = new java.lang.ref.WeakReference<>(this);
        nextMinute();
        Library.get(this).ensure();
        if (!channel) {
            // The films and series read again when a day old, but not read back while only live TV is on screen.
            if (!onlyLiveTv()) Catalog.get(this).ensure();
            catalogueFollows();
        }
        // Coming back (Settings → General → Start on: Last channel): the channel watched last, full screen with its card.
        boolean back = stoppedAt > 0 && StartUp.playOnReturn(com.gathin.homefieldtv.prefs.General.start(this), reopened,
                android.os.SystemClock.elapsedRealtime() - stoppedAt, leftForOwn);
        reopened = false;
        leftForOwn = false;
        stoppedAt = 0;
        if (back && StartUp.cameBack(this)) return;
        Screen t = top();
        if (t != null) t.shown();
    }

    /** Coming back: opened again from the TV's Home, when it went out of sight, whether to one of its own screens. */
    private boolean reopened, leftForOwn;
    private long stoppedAt;

    /** One of its own screens over it (a film, Android's installer or file chooser, voice search): not a coming back. */
    @Override public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        leftForOwn = true;
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        ReminderCard.front = null;
        Cast.release();
        h.removeCallbacks(minute);
        Screen t = top();
        if (t != null) t.hidden();
        // The stream let go whenever the app isn't on screen: the provider allows one at a time.
        live.stop();
    }

    @Override protected void onStop() {
        super.onStop();
        stoppedAt = android.os.SystemClock.elapsedRealtime();
        // Settings → Parental control: whatever the PIN opened is locked again once Homefield TV is left.
        if (!isChangingConfigurations()) com.gathin.homefieldtv.prefs.Parental.relock();
    }

    /** Settings → Backup's file chooser answered: the file read, and Settings on it to confirm. */
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        // Search's microphone: Android's voice input answered.
        if (SearchScreen.heard(this, request, result, data)) return;
        String said = com.gathin.homefieldtv.backups.BackupStore.picked(this, request, result, data);
        if (said != null) ExtraSettings.picked(this, said);
    }

    @Override protected void onDestroy() {
        h.removeCallbacks(afterChannel);
        h.removeCallbacks(catalogueSleep);
        FrameStats.stop(this);
        Library.get(this).removeListener(channels);
        Catalog.get(this).removeListener(catalog);
        Updates.removeListener(updates);
        com.gathin.homefieldtv.engine.LogoPack.removeListener(logos);
        live.stop();
        super.onDestroy();
    }

    private void nextMinute() {
        h.removeCallbacks(minute);
        long now = System.currentTimeMillis();
        h.postDelayed(minute, 60_000L - now % 60_000L + 50);
    }

    Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    boolean isTop(Screen s) {
        return top() == s;
    }

    /** Whether s is on top and on the screen now (not while another app is in front). */
    boolean showing(Screen s) {
        return resumed && top() == s;
    }

    /** The screen under s (null: none, or s isn't open). */
    Screen below(Screen s) {
        int i = stack.indexOf(s);
        return i > 0 ? stack.get(i - 1) : null;
    }

    /**
     * A screen opened over the one on top: it fades in over it, and the one under goes when it has. An overlay (Settings'
     * panel) is put over it instead, the one under staying on screen, and takes itself in.
     */
    void push(Screen s) {
        Screen was = top();
        if (was != null && resumed) was.hidden();
        stack.add(s);
        View v = s.view();
        (s.overVideo() ? over : screens).addView(v, full());
        if (tabs != null) tabs.pushed(s);
        FrameStats.on(s);
        if (s.overlay()) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setVisibility(View.VISIBLE);
            s.opening();
        } else {
            crossFade(v, was == null || !was.built() ? null : was.view(), false);
        }
        covered();
        if (resumed) s.shown();
        // Its shown() may have put the live picture up (or taken it away).
        covered();
        catalogueFollows();
    }

    /**
     * The top screen closed (at Home: the app goes to the background): it fades away over the one under it, or (an
     * overlay) takes itself out.
     */
    void pop() {
        if (stack.size() <= 1) {
            moveTaskToBack(true);
            return;
        }
        Screen s = stack.remove(stack.size() - 1);
        if (resumed) s.hidden();
        Screen t = top();
        if (tabs != null) tabs.follow(t);
        // Home's page made now when the launcher's channel started the app without it.
        View under = t == stack.get(0) ? homeView() : t.view();
        FrameStats.on(t);
        under.animate().cancel();
        under.setAlpha(1f);
        under.setVisibility(View.VISIBLE);
        View gone = s.view();
        gone.animate().cancel();
        if (Exec.inline) {
            detach(gone);
        } else if (s.overlay() && s.closing(() -> detach(gone))) {
            // The overlay goes its own way (its panel sliding out), then is taken away.
            covered();
            if (resumed) t.shown();
            catalogueFollows();
            return;
        } else {
            gone.animate().alpha(0f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer()
                    .withEndAction(() -> {
                        detach(gone);
                        gone.setAlpha(1f);
                        covered();
                    }).start();
        }
        covered();
        if (resumed) t.shown();
        covered();
        catalogueFollows();
    }

    /** Back to Home, then s over it, fading in over the screen that was on top. */
    void fromHome(Screen s) {
        Screen was = top();
        if (was != null && resumed) was.hidden();
        // (Home not made yet, for the launcher's channel: nothing of it to fade or hide.)
        View showing = was == null || !was.built() ? null : was.view();
        boolean homeShowing = was == stack.get(0);
        while (stack.size() > 1) {
            Screen x = stack.remove(stack.size() - 1);
            if (x.view() != showing) detach(x.view());
        }
        if (!homeShowing && stack.get(0).built()) stack.get(0).view().setVisibility(View.GONE);
        stack.add(s);
        (s.overVideo() ? over : screens).addView(s.view(), full());
        if (tabs != null) tabs.pushed(s);
        FrameStats.on(s);
        crossFade(s.view(), showing, !homeShowing);
        covered();
        if (resumed) s.shown();
        // Its shown() may have put the live picture up (or taken it away).
        covered();
        catalogueFollows();
    }

    /** 07's shell (TabBar): a page sends the remote up to it with tabs().enter(). */
    TabBar tabs() {
        return tabs;
    }

    private static final long PAGE_MS = 220;

    /**
     * in fades up as out fades away (whichever layer each is in); then out is removed (gone) or hidden. Coming back to
     * out before then cancels its going (pop).
     */
    private void crossFade(View in, View out, boolean gone) {
        in.animate().cancel();
        in.setVisibility(View.VISIBLE);
        if (Exec.inline || out == null) {
            in.setAlpha(1f);
            if (out != null) {
                if (gone) detach(out);
                else out.setVisibility(View.GONE);
            }
            return;
        }
        in.setAlpha(0f);
        out.animate().cancel();
        Runnable done = () -> {
            out.setAlpha(1f);
            if (gone) detach(out);
            else out.setVisibility(View.GONE);
            covered();
        };
        if (above(in, out)) {
            // The new page fades in over the one it leaves, which stays whole under it until then: the same cross-fade
            // (in at k over out), with one page drawn through a layer, not two.
            out.setAlpha(1f);
            in.animate().alpha(1f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer().withEndAction(done).start();
        } else {
            in.animate().alpha(1f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer().start();
            out.animate().alpha(0f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer().withEndAction(done).start();
        }
    }

    /** Whether a is drawn over b (the layers: the screens, the live picture, what goes over it; then their order). */
    private boolean above(View a, View b) {
        int la = layerOf(a), lb = layerOf(b);
        if (la != lb) return la > lb;
        ViewGroup p = (ViewGroup) a.getParent();
        return p != null && p == b.getParent() && p.indexOfChild(a) > p.indexOfChild(b);
    }

    private int layerOf(View v) {
        Object p = v.getParent();
        return p == over ? 2 : p == video ? 1 : 0;
    }

    // ---- the window's own ground --------------------------------------------------------------------------------

    /**
     * The window's ground, drawn only where no page covers the screen: under a page that draws its own opaque ground
     * (Screen.opaque) it would be a full screen of fill wasted on every frame. Still there (as before) whenever a page
     * fades or nothing opaque is up (the live picture over a hidden Home).
     */
    private static final class WindowGround extends android.graphics.drawable.ColorDrawable {
        boolean covered;

        WindowGround(int colour) {
            super(colour);
        }

        @Override public void draw(android.graphics.Canvas c) {
            if (!covered) super.draw(c);
        }
    }

    private WindowGround windowGround;

    /** Whether an opaque page is whole on screen now (in the screens' layer, shown, not fading): then no window ground. */
    private void covered() {
        if (windowGround == null) return;
        boolean c = false;
        for (Screen s : stack) {
            if (s.overlay() || s.overVideo() || !s.opaque() || !s.built()) continue;
            View v = s.view();
            if (v.getParent() == screens && v.getVisibility() == View.VISIBLE && v.getAlpha() >= 0.999f) c = true;
        }
        // A channel full screen: the live picture covers the window (its own ground would be drawn only to be cut away).
        Screen t = top();
        if (!c && live != null && t != null && t.overVideo() && video.getVisibility() == View.VISIBLE && live.covering()) c = true;
        if (c != windowGround.covered) {
            windowGround.covered = c;
            windowGround.invalidateSelf();
        }
    }

    private static void detach(View v) {
        ViewGroup parent = (ViewGroup) v.getParent();
        if (parent != null) parent.removeView(v);
    }

    HomeScreen homeScreen() {
        return (HomeScreen) stack.get(0);
    }

    /** Back to Home, every screen over it closed. */
    void home() {
        while (stack.size() > 1) pop();
    }

    /** The channel for the mini TV: the one playing while it is still in the playlists, else the last one watched. */
    Channel channelToPlay() {
        Library lib = Library.get(this);
        Channel c = live.channel();
        c = c != null && lib.byKey(c.key) != null ? lib.byKey(c.key) : lib.lastChannel();
        // Settings → Categories and Parental control: never a hidden or locked channel started by itself (at once).
        return lib.playable(c);
    }

    // ---- where the screens go ----------------------------------------------------------------------------------

    /** A channel full screen, with its banner. */
    void watch(Channel c) {
        if (c == null) return;
        // Settings → Parental control: a locked channel after its PIN.
        PinScreen.forChannel(this, c, () -> fromHome(new LiveScreen(this, c)));
    }

    /** A programme on a channel from its start (catch-up, CatchupScreen), over the channel's live screen. */
    void watchFrom(Channel c, long start, long end) {
        if (c == null) return;
        PinScreen.forChannel(this, c, () -> fromHome(new LiveScreen(this, c, start, end)));
    }

    void openGuide(String category) {
        push(new GuideScreen(this, category));
    }

    void openBrowse(boolean series) {
        push(new BrowseScreen(this, series));
    }

    void openTitle(Catalog.Title t) {
        if (t != null) push(new TitleScreen(this, t));
    }

    void openSearch() {
        push(new SearchScreen(this));
    }

    void openSettings() {
        // Settings → Parental control → Lock Settings: after the PIN.
        PinScreen.forSettings(this, () -> push(new SettingsScreen(this)));
    }

    /**
     * A film (episode null) or an episode, full screen, from start ms in: on its cover first, every source on it (choose:
     * Play on a title's page, an episode chosen), or straight away from the copy it was started on (Resume, Continue
     * watching).
     */
    void play(Catalog.Title t, Catalog.Episode e, long startMs, boolean choose) {
        if (t == null) return;
        live.stop();
        Intent i = new Intent(this, VodActivity.class)
                .putExtra(VodActivity.TITLE, t.key)
                .putExtra(VodActivity.EPISODE, e == null ? "" : e.id)
                .putExtra(VodActivity.START, startMs)
                .putExtra(VodActivity.CHOOSE, choose);
        startActivity(i);
    }

    /** The whole screen, for the live picture. */
    Rect fullScreen() {
        return new Rect(0, 0, getWindow().getDecorView().getWidth() > 0 ? getWindow().getDecorView().getWidth()
                : getResources().getDisplayMetrics().widthPixels,
                getWindow().getDecorView().getHeight() > 0 ? getWindow().getDecorView().getHeight()
                        : getResources().getDisplayMetrics().heightPixels);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    /** A key held this long is held (OK's too). */
    static final long LONG_MS = 600;
    /** The key down now that the screen tells apart pressed and held, whether its hold has been given, to whom. */
    private int holdCode;
    private boolean holdFired;
    private Screen holdScreen;
    private KeyEvent holdDown;

    /** A press of a key held apart (holds): Back as Back, any other as a key. */
    private void press(Screen s, KeyEvent down) {
        int code = down.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
            if (!s.back()) pop();
        } else {
            s.key(code, down);
        }
    }

    // ---- the player's sleep timer, and leaving the player ------------------------------------------------------

    /** When the sleep timer stops the picture (elapsedRealtime; 0: off), and the minutes chosen. */
    private long sleepAt;
    private int sleepChosen;
    private final Runnable sleep = this::sleepNow;

    /** The player's Sleep timer: the picture stops after minutes (0: off). */
    void sleep(int minutes) {
        h.removeCallbacks(sleep);
        sleepChosen = Math.max(0, minutes);
        sleepAt = sleepChosen == 0 ? 0 : android.os.SystemClock.elapsedRealtime() + sleepChosen * 60_000L;
        if (sleepChosen > 0) h.postDelayed(sleep, sleepChosen * 60_000L);
    }

    /** The minutes chosen on the sleep timer (0: off). */
    int sleepChosen() {
        return sleepAt == 0 ? 0 : sleepChosen;
    }

    /** What is left on the sleep timer in ms (0: off). */
    long sleepLeft() {
        return sleepAt == 0 ? 0 : Math.max(0, sleepAt - android.os.SystemClock.elapsedRealtime());
    }

    private void sleepNow() {
        sleepAt = 0;
        sleepChosen = 0;
        if (live.channel() == null) return;
        stopPlayback();
        android.widget.Toast.makeText(this, "Sleep timer: the picture has stopped", android.widget.Toast.LENGTH_LONG).show();
    }

    /** Back from the player, as TiviMate's: the TV guide, the channel in its picture (the guide decides what plays). */
    void toGuide() {
        fromHome(new GuideScreen(this, null));
    }

    /** The picture stopped (Back held, Exit, the sleep timer): Home, its mini TV not started again. */
    void stopPlayback() {
        miniTvHeld = true;
        live.stop();
        home();
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        Screen s = top();
        int code = e.getKeyCode();
        if (s == null) return super.dispatchKeyEvent(e);
        if (isOk(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    okDown = true;
                    okLong = false;
                } else if (okDown && !okLong && e.getEventTime() - e.getDownTime() > 600) {
                    okLong = true;
                    if (tabs.active()) tabs.ok();
                    else s.okLong();
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (okDown && !okLong) {
                    if (tabs.active()) tabs.ok();
                    else s.ok();
                }
                okDown = false;
            }
            return true;
        }
        // The remote on 07's tab bar: its keys (Left, Right, Down, Up, MENU) and Back are the bar's; the guide and search
        // keys open the guide and Search (below); the others (the channel keys, the numbers) go on as from the page.
        if (tabs.active()) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !tabs.back()) pop();
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_DOWN && tabs.key(code, e)) return true;
            if (e.getAction() == KeyEvent.ACTION_UP && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                return true;
            }
        }
        // A key the screen tells apart pressed and held (the player's, as TiviMate's remote has them): a press when it is
        // let go, held once LONG_MS have gone by with it down.
        if (code == holdCode || e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && s.holds(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    holdCode = code;
                    holdFired = false;
                    holdScreen = s;
                    holdDown = new KeyEvent(e);
                } else if (!holdFired && (e.getEventTime() - e.getDownTime() >= LONG_MS
                        || (e.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0)) {
                    holdFired = true;
                    if (holdScreen == top() && !holdScreen.keyLong(code)) press(holdScreen, holdDown);
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (!holdFired && holdScreen == top() && holdDown != null) press(holdScreen, holdDown);
                holdCode = 0;
                holdScreen = null;
                holdDown = null;
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !s.back()) pop();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            // On 07's tab bar the guide and search keys are the shortcuts below (the page under it hasn't the remote).
            boolean onBar = tabs.active() && (code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_SEARCH);
            if (!onBar && s.key(code, e)) return true;
            // The remote's own shortcuts, from any screen.
            if (code == KeyEvent.KEYCODE_GUIDE) {
                if (!(s instanceof GuideScreen)) fromHome(new GuideScreen(this, null));
                return true;
            }
            if (code == KeyEvent.KEYCODE_SEARCH) {
                if (!(s instanceof SearchScreen)) openSearch();
                return true;
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP && (code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_SEARCH)) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
