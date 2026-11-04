package com.gathin.tvhomephone.home;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Home: the scores box, weather, today's games and alerts (built by phone-sports). */
public class HomeFragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        TextView t = new TextView(requireContext());
        t.setText("Home: the scores box, weather, today's games and alerts");
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
