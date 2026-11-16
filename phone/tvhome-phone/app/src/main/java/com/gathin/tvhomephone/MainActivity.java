package com.gathin.tvhomephone;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.glass.LensBar;

/**
 * The app's one window: five tabs (Home, Sports, Live TV, Watch, Settings), each a fragment of its own part, under a
 * floating bar of liquid glass at the foot. Slide a finger along the bar and its lens follows to the tab let go on;
 * the page slides in from that side. Home and Watch are glass pages, drawn up under the status bar and down under the
 * bar, which blurs them as they scroll; Live TV fills the window too (its guide's black up under the status bar and its
 * rows on under the bar); Sports and Settings sit between the bars as before. A tablet the same, wider inside.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAB = "tab", SAVED_AT = "savedAt";
    /** Away from the screen this long, coming back counts as opening it again: Live TV (as Settings has it). */
    static final long BACK_AFTER_MS = 60_000L;
    /** When it last left the screen (not turned on its side), elapsed-time ms; 0 while it is here. */
    private long awayAt;
    /** An intent's extra: what to open (a notification's tap): OPEN_PLAYLISTS, Settings at Live TV's playlists. */
    public static final String EXTRA_OPEN = "open", OPEN_PLAYLISTS = "playlists", OPEN_LIVE = "live", OPEN_UPDATE = "update";
    /** The tabs in their order along the bar: a page slides in from the side its tab lies. */
    static final int[] ORDER = {R.id.tab_home, R.id.tab_sports, R.id.tab_live, R.id.tab_watch, R.id.tab_settings};
    private LensBar tabs;
    private FrameLayout root, content;
    private FrameLayout.LayoutParams barPlace;
    /** The tab whose page shows (0 before the first); the status bar's, the navigation bar's and the keyboard's heights. */
    private int shownId, insetTop, insetBottom, insetIme;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Glass.GROUND);
        content = new FrameLayout(this);
        content.setId(R.id.content_frame);
        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        tabs = new LensBar(this, LensBar.TABS);
        tabs.tab(R.id.tab_home, Icons.HOME, getString(R.string.tab_home))
                .tab(R.id.tab_sports, Icons.SPORTS, getString(R.string.tab_sports))
                .tab(R.id.tab_live, Icons.LIVE, getString(R.string.tab_live))
                .tab(R.id.tab_watch, Icons.WATCH, getString(R.string.tab_watch))
                .tab(R.id.tab_settings, Icons.SETTINGS, getString(R.string.tab_settings));
        tabs.setSources(content);
        tabs.setElevation(Glass.dp(this, 10));
        int side = Glass.dp(this, 14);
        barPlace = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        barPlace.setMargins(side, 0, side, Glass.dp(this, Glass.BAR_GAP_DP));
        root.addView(tabs, barPlace);
        setContentView(root);
        // Edge to edge: the glass pages and Live TV draw under the status bar; the others are kept between the bars (fitPage).
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.systemBars());
            insetTop = s.top;
            insetBottom = s.bottom;
            insetIme = ins.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            barPlace.bottomMargin = s.bottom + Glass.dp(this, Glass.BAR_GAP_DP);
            barPlace.leftMargin = side + s.left;
            barPlace.rightMargin = side + s.right;
            tabs.setLayoutParams(barPlace);
            fitPage(false);
            return ins;
        });
        // A page that fills the window gets the insets to fit itself; the others are padded here and see none.
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, ins) -> fillsWindow(shownId) ? ins : WindowInsetsCompat.CONSUMED);
        // Closed unexpectedly last time: said once, the details to copy.
        CrashNote.offer(this);
        tabs.setOnPick(this::show);
        int start = state != null ? state.getInt(TAB, R.id.tab_home) : R.id.tab_home;
        // Opened afresh from its icon: Live TV (it goes on with the channel last watched), unless Settings says Home.
        if (state == null && opening(getIntent()) && com.gathin.tvhomephone.live.LiveTvSettings.openOnLive(this)) start = R.id.tab_live;
        // Made again after Android let it go while it was away a while (not only turned on its side): the same.
        if (state != null && opening(getIntent()) && backToLive(System.currentTimeMillis() - state.getLong(SAVED_AT, System.currentTimeMillis()))
                && com.gathin.tvhomephone.live.LiveTvSettings.openOnLive(this)) start = R.id.tab_live;
        if (state == null && OPEN_PLAYLISTS.equals(getIntent().getStringExtra(EXTRA_OPEN))) {
            com.gathin.tvhomephone.settings.SettingsFragment.toPlaylists = true;
            start = R.id.tab_settings;
        }
        if (OPEN_LIVE.equals(getIntent().getStringExtra(EXTRA_OPEN))) start = R.id.tab_live;
        tabs.select(start, false);
        show(start);
        // The "Homefield X is ready" note tapped: its question (Update or Later).
        if (state == null && OPEN_UPDATE.equals(getIntent().getStringExtra(EXTRA_OPEN))) com.gathin.tvhomephone.update.AppUpdate.ask(this, true);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        // Turned on its side with rotation on (the page made again): a picture playing small goes full screen.
        if (state != null) com.gathin.tvhomephone.live.SideFull.remade(this, state.getBoolean(UPRIGHT, true));
    }

    private static final String UPRIGHT = "upright";

    /** With rotation locked: the phone turned on its side while a picture plays small, wherever in the app: full screen. */
    @Override protected void onResume() {
        super.onResume();
        com.gathin.tvhomephone.live.SideFull.watch(this, true);
        // Back from letting Homefield install its own updates: the update it was starting goes on.
        com.gathin.tvhomephone.update.AppUpdate.resumed(this);
    }

    @Override protected void onPause() {
        com.gathin.tvhomephone.live.SideFull.watch(this, false);
        super.onPause();
    }

    /** Back on the screen after a while away (its icon, the app switcher): Live TV, as when it opens afresh. */
    @Override protected void onStart() {
        super.onStart();
        // Homefield's own updates: a word if it just updated; the TVs looked at for a newer version (AppUpdate).
        com.gathin.tvhomephone.update.AppUpdate.opened(this);
        long away = awayAt;
        awayAt = 0;
        if (away > 0 && backToLive(android.os.SystemClock.elapsedRealtime() - away) && tabs != null
                && com.gathin.tvhomephone.live.LiveTvSettings.openOnLive(this) && tabs.selected() != R.id.tab_live) {
            open(R.id.tab_live);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        com.gathin.tvhomephone.update.AppUpdate.closed();
        if (!isChangingConfigurations()) awayAt = android.os.SystemClock.elapsedRealtime();
    }

    /** Pure: away this long, coming back is opening the app again. */
    static boolean backToLive(long awayMs) {
        return awayMs >= BACK_AFTER_MS;
    }

    /** Pure-ish: an intent that just opens the app (its icon, or nothing asked for), not one pointing somewhere. */
    static boolean opening(android.content.Intent i) {
        if (i == null || i.getStringExtra(EXTRA_OPEN) != null) return false;
        boolean icon = android.content.Intent.ACTION_MAIN.equals(i.getAction()) && i.hasCategory(android.content.Intent.CATEGORY_LAUNCHER);
        return icon || i.getAction() == null && (i.getExtras() == null || i.getExtras().isEmpty());
    }

    /** A notification tapped while the app is open: its place shown (the playlists in Settings). */
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // A notification or a link says where to go: that, not Live TV for having been away (onStart comes after this).
        awayAt = 0;
        // The "Homefield X is ready" note tapped while the app was open: its question, over whatever is on.
        if (intent != null && OPEN_UPDATE.equals(intent.getStringExtra(EXTRA_OPEN))) {
            com.gathin.tvhomephone.update.AppUpdate.ask(this, true);
            return;
        }
        // Its icon tapped while it was open in the background: Live TV again, as when it opens afresh.
        if (intent != null && android.content.Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)
                && intent.getStringExtra(EXTRA_OPEN) == null && com.gathin.tvhomephone.live.LiveTvSettings.openOnLive(this)) {
            if (tabs.selected() != R.id.tab_live) open(R.id.tab_live);
            return;
        }
        // The full screen's "TV guide": the Live TV tab, the channel going on in its picture.
        if (OPEN_LIVE.equals(intent.getStringExtra(EXTRA_OPEN))) {
            if (tabs.selected() != R.id.tab_live) open(R.id.tab_live);
            return;
        }
        if (OPEN_PLAYLISTS.equals(intent.getStringExtra(EXTRA_OPEN))) {
            com.gathin.tvhomephone.settings.SettingsFragment.toPlaylists = true;
            if (tabs.selected() == R.id.tab_settings) show(R.id.tab_settings);
            else open(R.id.tab_settings);
        }
    }

    /** Home or a swipe away while Live TV's guide plays: on in picture in picture (LiveLinks.leaving). */
    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        com.gathin.tvhomephone.live.LiveLinks.leaving(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(TAB, tabs.selected());
        out.putLong(SAVED_AT, System.currentTimeMillis());
        out.putBoolean(UPRIGHT, com.gathin.tvhomephone.live.SideFull.upright(this));
    }

    /** Opens a tab from anywhere in the app (a notification's "Open in Live TV", Home's cards): the lens slides there. */
    public void open(int tabId) {
        tabs.select(tabId, true);
        if (tabId != shownId) show(tabId);
    }

    private void show(int id) {
        String tag = "tab" + id;
        Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
        if (f == null) f = make(id);
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        int from = place(shownId), to = place(id);
        if (shownId != 0 && from >= 0 && to >= 0 && from != to) {
            if (to > from) tx.setCustomAnimations(R.anim.page_in_right, R.anim.page_out_left);
            else tx.setCustomAnimations(R.anim.page_in_left, R.anim.page_out_right);
        }
        tx.replace(R.id.content_frame, f, tag).commitAllowingStateLoss();
        shownId = id;
        // Live TV's picture can't be drawn twice: the bar's tint alone over it.
        tabs.setBlurOn(id != R.id.tab_live);
        fitPage(true);
    }

    /** Pure: where a tab sits along the bar (-1 for none). */
    static int place(int id) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == id) return i;
        return -1;
    }

    /** Pure: the pages drawn in glass, under the status bar and the tab bar (Home and Watch). */
    static boolean glassPage(int id) {
        return id == R.id.tab_home || id == R.id.tab_watch;
    }

    /** Pure: the pages that fill the whole window, fitting themselves to the bars: the glass ones and Live TV. */
    static boolean fillsWindow(int id) {
        return glassPage(id) || id == R.id.tab_live;
    }

    /**
     * A glass page or Live TV fills the window (it fits itself); any other sits between the status bar and the tab bar.
     * Asked (a new page), the insets are sent round again for it; never from the insets' own listener, which would loop.
     */
    private void fitPage(boolean ask) {
        if (content == null) return;
        boolean fills = fillsWindow(shownId);
        int room = Glass.dp(this, Glass.BAR_DP + Glass.BAR_GAP_DP + 6);
        // The keyboard, when up, lifts the page's foot above it (Settings' playlist fields).
        content.setPadding(0, fills ? 0 : insetTop, 0, fills ? 0 : Math.max(insetBottom + room, insetIme));
        // Under the page as it slides in or out: the glass's ground, the guide's black (GuideLook) or the navy.
        root.setBackgroundColor(glassPage(shownId) ? Glass.GROUND : shownId == R.id.tab_live ? Color.BLACK : getColor(R.color.ground));
        if (ask) ViewCompat.requestApplyInsets(content);
    }

    private static Fragment make(int id) {
        if (id == R.id.tab_watch) return new com.gathin.tvhomephone.watch.WatchFragment();
        if (id == R.id.tab_sports) return new com.gathin.tvhomephone.sports.SportsFragment();
        if (id == R.id.tab_live) return new com.gathin.tvhomephone.live.LiveTvFragment();
        if (id == R.id.tab_settings) return new com.gathin.tvhomephone.settings.SettingsFragment();
        return new com.gathin.tvhomephone.home.HomeFragment();
    }
}
