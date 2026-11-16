package com.gathin.tvhomephone.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.fragment.app.Fragment;

/**
 * Settings: one scrolling page of each part's own section (the TVs and sync, the alerts and clubs, Live TV's playlist,
 * the posters' shape, the films' subtitles), then the app's own updates (its version, and the newer one the TVs have).
 */
public class SettingsFragment extends Fragment {
    /** Set before Settings opens from a playlist's notification: scrolled down to Live TV's playlists once. */
    public static boolean toPlaylists;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        // The matchday intro first: its own video is the thing most often changed here.
        col.addView(com.gathin.tvhomephone.live.IntroSettings.build(requireContext()));
        col.addView(com.gathin.tvhomephone.sync.SyncSettings.build(this, col));
        col.addView(com.gathin.tvhomephone.sports.SportsSettings.build(this, col));
        android.view.View live = com.gathin.tvhomephone.live.LiveTvSettings.build(this, col);
        col.addView(live);
        // Films and TV shows: portrait or landscape posters on Home and Watch.
        col.addView(com.gathin.tvhomephone.live.PosterSettings.build(this, col));
        // The family's own TorBox files, a source for films and series beside the playlists.
        col.addView(com.gathin.tvhomephone.live.TorBoxSettings.build(this, col));
        col.addView(com.gathin.tvhomephone.live.SubtitleSettings.build(this, col));
        if (toPlaylists) {
            toPlaylists = false;
            scroll.post(() -> scroll.smoothScrollTo(0, live.getTop()));
        }
        // Last, in place of the version line: this version and the newer one the TVs have, to update to here.
        col.addView(com.gathin.tvhomephone.update.UpdateSettings.build(this));
        return scroll;
    }
}
