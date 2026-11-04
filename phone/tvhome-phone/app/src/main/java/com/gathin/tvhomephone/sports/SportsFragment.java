package com.gathin.tvhomephone.sports;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Sports: Match Centre, tables, F1, This weekend (built by phone-sports). */
public class SportsFragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        TextView t = new TextView(requireContext());
        t.setText("Sports: Match Centre, tables, F1, This weekend");
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
