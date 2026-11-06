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
    };
    private final Runnable catalog = () -> {
        for (Screen s : new ArrayList<>(stack)) s.catalogChanged();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        Pics.init(this);
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
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        nextMinute();
        Library.get(this).ensure();
        Catalog.get(this).ensure();
        Screen t = top();
        if (t != null) t.shown();
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        h.removeCallbacks(minute);
        Screen t = top();
        if (t != null) t.hidden();
        // The stream let go whenever the app isn't on screen: the provider allows one at a time.
        live.stop();
    }

    @Override protected void onDestroy() {
        Library.get(this).removeListener(channels);
        Catalog.get(this).removeListener(catalog);
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

    /** A screen opened over the one on top. */
    void push(Screen s) {
        Screen was = top();
        if (was != null) {
            if (resumed) was.hidden();
            was.view().setVisibility(View.GONE);
        }
        stack.add(s);
        View v = s.view();
        (s.overVideo() ? over : screens).addView(v, full());
        if (resumed) s.shown();
    }

    /** The top screen closed (at Home: the app goes to the background). */
    void pop() {
        if (stack.size() <= 1) {
            moveTaskToBack(true);
            return;
        }
        Screen s = stack.remove(stack.size() - 1);
        if (resumed) s.hidden();
        ViewGroup parent = (ViewGroup) s.view().getParent();
        if (parent != null) parent.removeView(s.view());
        Screen t = top();
        t.view().setVisibility(View.VISIBLE);
        if (resumed) t.shown();
    }

    /** Back to Home, then s over it. */
    void fromHome(Screen s) {
        Screen was = top();
        if (was != null && resumed) was.hidden();
        while (stack.size() > 1) {
            Screen x = stack.remove(stack.size() - 1);
            ViewGroup parent = (ViewGroup) x.view().getParent();
            if (parent != null) parent.removeView(x.view());
        }
        stack.get(0).view().setVisibility(View.GONE);
        stack.add(s);
        (s.overVideo() ? over : screens).addView(s.view(), full());
        if (resumed) s.shown();
    }

    HomeScreen home() {
        return (HomeScreen) stack.get(0);
    }

    /** The channel for the mini TV: the one playing while it is still in the playlists, else the last one watched. */
    Channel channelToPlay() {
        Library lib = Library.get(this);
        Channel c = live.channel();
        if (c != null && lib.byKey(c.key) != null) return lib.byKey(c.key);
        return lib.lastChannel();
    }

    // ---- where the screens go ----------------------------------------------------------------------------------

    /** A channel full screen, with its banner. */
    void watch(Channel c) {
        if (c == null) return;
        fromHome(new LiveScreen(this, c));
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
        push(new SettingsScreen(this));
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
