package com.gathin.tvhomephone.live;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.PosterCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' Films and TV shows section: the posters on Home and Watch as portrait posters or as landscape cards (each
 * title's picture at 16:9 with its logo). Home and Watch draw the choice as they next open.
 */
public final class PosterSettings {
    private PosterSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 28), 0, Ui.dp(c, 8));
        col.addView(Ui.text(c, c.getString(R.string.posters_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.posters_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        col.addView(body);
        col.addView(LiveTvSettings.label(c, c.getString(R.string.posters_shape)));
        LinearLayout row = new LinearLayout(c);
        String[] names = {c.getString(R.string.posters_portrait), c.getString(R.string.posters_landscape)};
        int chosen = PosterCard.wide(c) ? 1 : 0;
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            boolean wide = i == 1;
            TextView t = LiveTvSettings.pill(c, names[i], i == chosen);
            t.setOnClickListener(v -> {
                PosterCard.setWide(c, wide);
                for (TextView x : chips) LiveTvSettings.restyle(c, x, x == v);
            });
            chips.add(t);
            row.addView(t, LiveTvSettings.chipParams(c));
        }
        col.addView(row);
        return col;
    }
}
