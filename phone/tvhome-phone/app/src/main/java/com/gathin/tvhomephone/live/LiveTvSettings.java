package com.gathin.tvhomephone.live;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Settings' section: Live TV: M3U playlist or Xtream Codes (built by phone-live). */
public final class LiveTvSettings {
    private LiveTvSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        TextView t = new TextView(host.requireContext());
        t.setText("Live TV: M3U playlist or Xtream Codes");
        return t;
    }
}
