package com.gathin.tvhomephone.sync;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Settings' section: Your TVs: pairing and sync (built by phone-watch). */
public final class SyncSettings {
    private SyncSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        TextView t = new TextView(host.requireContext());
        t.setText("Your TVs: pairing and sync");
        return t;
    }
}
