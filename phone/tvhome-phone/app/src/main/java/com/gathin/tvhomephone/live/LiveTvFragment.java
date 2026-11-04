package com.gathin.tvhomephone.live;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Live TV: your own playlist's channels and guide (built by phone-live). */
public class LiveTvFragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        TextView t = new TextView(requireContext());
        t.setText("Live TV: your own playlist's channels and guide");
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
