package com.gathin.tvhomephone.sports;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Settings' section: Alerts and starred clubs (built by phone-sports). */
public final class SportsSettings {
    private SportsSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        TextView t = new TextView(host.requireContext());
        t.setText("Alerts and starred clubs");
        return t;
    }
}
