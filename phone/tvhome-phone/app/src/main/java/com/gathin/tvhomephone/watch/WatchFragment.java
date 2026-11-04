package com.gathin.tvhomephone.watch;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Watch: Discover, Movies and TV shows (built by phone-watch). */
public class WatchFragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        TextView t = new TextView(requireContext());
        t.setText("Watch: Discover, Movies and TV shows");
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
