package com.gathin.tvhomephone.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.BuildConfig;
import com.gathin.tvhomephone.R;

/**
 * Settings: one scrolling page of each part's own section (the TVs and sync, the alerts and clubs, Live TV's playlist),
 * then the version.
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
        col.addView(com.gathin.tvhomephone.sync.SyncSettings.build(this, col));
        col.addView(com.gathin.tvhomephone.sports.SportsSettings.build(this, col));
        android.view.View live = com.gathin.tvhomephone.live.LiveTvSettings.build(this, col);
        col.addView(live);
        if (toPlaylists) {
            toPlaylists = false;
            scroll.post(() -> scroll.smoothScrollTo(0, live.getTop()));
        }
        TextView v = new TextView(requireContext());
        v.setText("TV Home " + BuildConfig.VERSION_NAME);
        v.setTextColor(requireContext().getColor(R.color.text_dim));
        v.setPadding(0, pad, 0, 0);
        col.addView(v);
        return scroll;
    }
}
