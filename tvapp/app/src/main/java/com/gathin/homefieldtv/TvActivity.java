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

import java.util.ArrayList;
import java.util.List;

/**
 * The app: Home first, with the screens opened from it stacked over it. Three layers: the screens, the live picture
 * over them (the mini TV, or full screen), and what goes over the live picture (the channel banner).
 */
public final class TvActivity extends Activity {
    FrameLayout screens, video, over;
    LiveTv live;
    private final List<Screen> stack = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean resumed, okDown, okLong;

    private final Runnable minute = new Runnable() {
        @Override public void run() {
            for (Screen s : new ArrayList<>(stack)) s.tick();
            nextMinute();
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
    };
    /** An update found, gone or under way: the dots on Settings drawn again, Settings told. */
    private final Runnable updates = () -> {
        for (Screen s : new ArrayList<>(stack)) {
            if (!s.built()) continue;
            redraw(s.view());
            s.updatesChanged();
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
        // The theme's colours before any screen is made; the blue screens' ground tinted to match.
        Theme.apply(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{Ui.tint(0xFF0C4FAE), Ui.tint(0xFF06347C)}));
        Dark.init(this);
        PosterArt.init(this);
        Pics.init(this);
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
        live = new LiveTv(this, video);
        Library lib = Library.get(this);
        lib.addListener(channels);
        lib.ensure();
        Catalog cat = Catalog.get(this);
        cat.addListener(catalog);
        cat.ensure();
        push(new HomeScreen(this));
        // Homefield TV's own updates: the folder made (for one to be put in), a quiet look, What's new once after one.
        Updates.folder(this);
        Updates.addListener(updates);
        Updates.check(this, null);
        String[] news = Updates.whatsNew(this);
        if (news != null && reopenSettings < 0) push(new WhatsNewScreen(this, news[0], news[1]));
        // Started again for a new theme: back on Settings where it was chosen.
        if (reopenSettings >= 0) {
            SettingsScreen s = new SettingsScreen(this);
            push(s);
            s.openAt(reopenSettings);
            reopenSettings = -1;
        }
        handleCast(getIntent());
        handleReminder(getIntent());
        // Reminders' alarm made again (Android forgets it when the app is updated or stopped).
        ReminderAlarm.schedule(this);
        // Settings → General → Start on: Live TV, the guide, films or TV shows over Home (not over What's new, a cast or
        // the channel a reminder's card was for, still waiting for the channels to be read).
        if (fresh && top() instanceof HomeScreen && reminderKey == null) StartUp.open(this);
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
        handleCast(intent);
        handleReminder(intent);
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
        android.widget.Toast.makeText(this, "Playing " + c.name + (from == null || from.isEmpty() ? "" : " from " + from),
                android.widget.Toast.LENGTH_LONG).show();
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        Cast.hold(this);
        Updates.resumed(this);
        nextMinute();
        Library.get(this).ensure();
        Catalog.get(this).ensure();
        Screen t = top();
        if (t != null) t.shown();
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        Cast.release();
        h.removeCallbacks(minute);
        Screen t = top();
        if (t != null) t.hidden();
        // The stream let go whenever the app isn't on screen: the provider allows one at a time.
        live.stop();
    }

    @Override protected void onStop() {
        super.onStop();
        // Settings → Parental control: whatever the PIN opened is locked again once Homefield TV is left.
        if (!isChangingConfigurations()) com.gathin.homefieldtv.prefs.Parental.relock();
    }

    /** Settings → Backup's file chooser answered: the file read, and Settings on it to confirm. */
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        String said = com.gathin.homefieldtv.backups.BackupStore.picked(this, request, result, data);
        if (said != null) ExtraSettings.picked(this, said);
    }

    @Override protected void onDestroy() {
        Library.get(this).removeListener(channels);
        Catalog.get(this).removeListener(catalog);
        Updates.removeListener(updates);
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

    /** A screen opened over the one on top: it fades in over it, and the one under goes when it has. */
    void push(Screen s) {
        Screen was = top();
        if (was != null && resumed) was.hidden();
        stack.add(s);
        View v = s.view();
        (s.overVideo() ? over : screens).addView(v, full());
        crossFade(v, was == null ? null : was.view(), false);
        if (resumed) s.shown();
    }

    /** The top screen closed (at Home: the app goes to the background): it fades away over the one under it. */
    void pop() {
        if (stack.size() <= 1) {
            moveTaskToBack(true);
            return;
        }
        Screen s = stack.remove(stack.size() - 1);
        if (resumed) s.hidden();
        Screen t = top();
        View under = t.view();
        under.animate().cancel();
        under.setAlpha(1f);
        under.setVisibility(View.VISIBLE);
        View gone = s.view();
        gone.animate().cancel();
        if (Exec.inline) {
            detach(gone);
        } else {
            gone.animate().alpha(0f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer()
                    .withEndAction(() -> {
                        detach(gone);
                        gone.setAlpha(1f);
                    }).start();
        }
        if (resumed) t.shown();
    }

    /** Back to Home, then s over it, fading in over the screen that was on top. */
    void fromHome(Screen s) {
        Screen was = top();
        if (was != null && resumed) was.hidden();
        View showing = was == null ? null : was.view();
        boolean homeShowing = was == stack.get(0);
        while (stack.size() > 1) {
            Screen x = stack.remove(stack.size() - 1);
            if (x.view() != showing) detach(x.view());
        }
        if (!homeShowing) stack.get(0).view().setVisibility(View.GONE);
        stack.add(s);
        (s.overVideo() ? over : screens).addView(s.view(), full());
        crossFade(s.view(), showing, !homeShowing);
        if (resumed) s.shown();
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
        in.animate().alpha(1f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer().start();
        out.animate().cancel();
        out.animate().alpha(0f).setStartDelay(0).setDuration(PAGE_MS).setInterpolator(Fade.EASE).withLayer().withEndAction(() -> {
            out.setAlpha(1f);
            if (gone) detach(out);
            else out.setVisibility(View.GONE);
        }).start();
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

    /** A film (episode null) or an episode, full screen, from start ms in. */
    void play(Catalog.Title t, Catalog.Episode e, long startMs) {
        if (t == null) return;
        live.stop();
        Intent i = new Intent(this, VodActivity.class)
                .putExtra(VodActivity.TITLE, t.key)
                .putExtra(VodActivity.EPISODE, e == null ? "" : e.id)
                .putExtra(VodActivity.START, startMs);
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
                    s.okLong();
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (okDown && !okLong) s.ok();
                okDown = false;
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !s.back()) pop();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (s.key(code, e)) return true;
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
