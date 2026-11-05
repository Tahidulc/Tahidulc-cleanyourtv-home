package com.gathin.tvhomephone;

import android.os.Build;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * The app's one window: five tabs along the foot (Home, Sports, Live TV, Watch, Settings), each a fragment of its own
 * part. A phone gets the bar at the foot; a tablet the same, with wider content inside each tab.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAB = "tab";
    /** An intent's extra: what to open (a notification's tap): OPEN_PLAYLISTS, Settings at Live TV's playlists. */
    public static final String EXTRA_OPEN = "open", OPEN_PLAYLISTS = "playlists";
    private BottomNavigationView tabs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.ground));
        FrameLayout content = new FrameLayout(this);
        content.setId(R.id.content_frame);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        tabs = new BottomNavigationView(this);
        tabs.inflateMenu(R.menu.tabs);
        tabs.setBackgroundColor(getColor(R.color.ground_deep));
        tabs.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        root.addView(tabs, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        tabs.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        int start = state != null ? state.getInt(TAB, R.id.tab_home) : R.id.tab_home;
        if (state == null && OPEN_PLAYLISTS.equals(getIntent().getStringExtra(EXTRA_OPEN))) {
            com.gathin.tvhomephone.settings.SettingsFragment.toPlaylists = true;
            start = R.id.tab_settings;
        }
        tabs.setSelectedItemId(start);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    /** A notification tapped while the app is open: its place shown (the playlists in Settings). */
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (OPEN_PLAYLISTS.equals(intent.getStringExtra(EXTRA_OPEN))) {
            com.gathin.tvhomephone.settings.SettingsFragment.toPlaylists = true;
            if (tabs.getSelectedItemId() == R.id.tab_settings) show(R.id.tab_settings);
            else tabs.setSelectedItemId(R.id.tab_settings);
        }
    }

    /** Home or a swipe away while Live TV's guide plays: on in picture in picture (LiveLinks.leaving). */
    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        com.gathin.tvhomephone.live.LiveLinks.leaving(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(TAB, tabs.getSelectedItemId());
    }

    /** Opens a tab from anywhere in the app (a notification's "Open in Live TV", Home's cards). */
    public void open(int tabId) {
        tabs.setSelectedItemId(tabId);
    }

    private void show(int id) {
        String tag = "tab" + id;
        Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
        if (f == null) f = make(id);
        getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, f, tag).commitAllowingStateLoss();
    }

    private static Fragment make(int id) {
        if (id == R.id.tab_watch) return new com.gathin.tvhomephone.watch.WatchFragment();
        if (id == R.id.tab_sports) return new com.gathin.tvhomephone.sports.SportsFragment();
        if (id == R.id.tab_live) return new com.gathin.tvhomephone.live.LiveTvFragment();
        if (id == R.id.tab_settings) return new com.gathin.tvhomephone.settings.SettingsFragment();
        return new com.gathin.tvhomephone.home.HomeFragment();
    }
}
