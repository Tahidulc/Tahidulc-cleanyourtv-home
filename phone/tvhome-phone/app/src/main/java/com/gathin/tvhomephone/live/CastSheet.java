package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.cast.CastDevice;
import com.gathin.tvhomephone.cast.CastFinder;
import com.gathin.tvhomephone.cast.Casting;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Live TV's Cast: the TVs and devices on this Wi-Fi, as they are found, in three groups (the family's TVs with Homefield
 * TV or their own player, Chromecasts and Google TVs, and other sets that take a stream), and the one being cast to with
 * Stop. Picking one sends the channel there and stops it on the phone; channels chosen afterwards go there too, until
 * casting stops.
 */
final class CastSheet {
    private CastSheet() {}

    /** What looks for devices (a stand-in in previews). */
    static java.util.function.Supplier<CastFinder> finders = CastFinder::new;

    /** The sheet; ch is the channel to send when a device is picked (null: the next one chosen in the guide). */
    static void show(Activity a, Channel ch) {
        Context c = a;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 22));
        box.addView(Ui.text(c, c.getString(R.string.cast_title), 20, Ui.TEXT, true));
        TextView sub = Ui.text(c, ch == null ? c.getString(R.string.cast_sub_none) : c.getString(R.string.cast_sub, ch.name),
                13, Ui.DIM, false);
        sub.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        box.addView(sub);

        BottomSheetDialog[] mine = new BottomSheetDialog[1];
        if (Casting.active()) {
            CastDevice now = Casting.device();
            LinearLayout row = row(c, R.drawable.live_ic_cast, c.getString(R.string.cast_casting_to, now.name),
                    Casting.channel() == null ? "" : Casting.channel().name, true);
            TextView stop = pill(c, c.getString(R.string.cast_stop));
            stop.setOnClickListener(v -> {
                Casting.stop(c);
                if (mine[0] != null) mine[0].dismiss();
            });
            row.addView(stop);
            box.addView(row);
        }

        LinearLayout tvs = group(c, box, R.string.cast_group_tvs);
        LinearLayout casts = group(c, box, R.string.cast_group_cast);
        LinearLayout others = group(c, box, R.string.cast_group_other);

        LinearLayout status = new LinearLayout(c);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, Ui.dp(c, 16), 0, 0);
        ProgressBar spin = new ProgressBar(c);
        spin.setIndeterminateTintList(ColorStateList.valueOf(Ui.DIM));
        status.addView(spin, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
        TextView looking = Ui.text(c, c.getString(R.string.cast_looking), 13, Ui.DIM, false);
        looking.setPadding(Ui.dp(c, 10), 0, 0, 0);
        status.addView(looking, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(status);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        BottomSheetDialog d = Sheets.open(c, scroll, true);
        mine[0] = d;

        List<CastDevice> found = new ArrayList<>();
        CastFinder finder = finders.get();
        finder.start(c, dev -> {
            found.add(dev);
            LinearLayout into = dev.kind == CastDevice.CHROMECAST ? casts : dev.kind == CastDevice.DLNA ? others : tvs;
            ((View) into.getTag()).setVisibility(View.VISIBLE);
            boolean current = Casting.active() && Casting.device().id.equals(dev.id);
            LinearLayout r = row(c, dev.kind == CastDevice.CHROMECAST ? R.drawable.live_ic_cast : R.drawable.live_ic_tv,
                    dev.name, detail(c, dev), current);
            r.setOnClickListener(v -> {
                d.dismiss();
                start(a, dev, ch);
            });
            into.addView(r);
        }, () -> {
            spin.setVisibility(View.GONE);
            looking.setPadding(0, 0, 0, 0);
            looking.setText(found.isEmpty() ? R.string.cast_none : R.string.cast_more);
        });
        d.setOnDismissListener(x -> finder.stop());
    }

    /** Casting to that device starts: the phone's own stream stops (one stream at a time on most accounts). */
    static void start(Activity a, CastDevice dev, Channel ch) {
        Playback.get(a).stop();
        Casting.start(a, dev, ch == null ? null : what(a, ch));
    }

    /** A channel as it goes to a device: its names (for a TV's own player), what's on now and its logo. */
    static Casting.What what(Context c, Channel ch) {
        List<String> names = new ArrayList<>();
        names.add(ch.name);
        String plain = Names.plain(ch.name);
        if (!plain.equals(ch.name)) names.add(plain);
        String programme = null;
        Guide.Schedule s = Library.get(c).schedule(ch.key);
        int at = s == null ? -1 : s.at(System.currentTimeMillis());
        if (at >= 0) programme = s.title[at];
        return new Casting.What(ch, names, programme, Ui.logo(ch));
    }

    private static String detail(Context c, CastDevice d) {
        switch (d.kind) {
            case CastDevice.HOMEFIELD: return c.getString(R.string.cast_detail_homefield);
            case CastDevice.TV_HOME: return c.getString(R.string.cast_detail_tvhome);
            case CastDevice.CHROMECAST: return c.getString(R.string.cast_detail_chromecast);
            default: return c.getString(R.string.cast_detail_dlna);
        }
    }

    /** A group's heading and its rows (the heading shows once something is in it; kept as the rows' tag). */
    private static LinearLayout group(Context c, LinearLayout box, int title) {
        TextView head = Ui.text(c, c.getString(title).toUpperCase(java.util.Locale.UK), 12, Ui.G_SELECT, true);
        head.setLetterSpacing(0.1f);
        head.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
        head.setVisibility(View.GONE);
        box.addView(head);
        LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setTag(head);
        box.addView(rows);
        return rows;
    }

    private static LinearLayout row(Context c, int icon, String name, String detail, boolean current) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, 60));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                current ? Ui.ring(c, 0x14FFFFFF, Ui.TEXT, 1.5f, 14) : Ui.round(c, 0x0FFFFFFF, 14), Ui.round(c, Ui.TEXT, 14)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(c, 6);
        r.setLayoutParams(rp);
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(Ui.round(c, 0x26FFFFFF, 20));
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(Ui.TEXT));
        disc.addView(i, new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        r.addView(disc, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        TextView n = Ui.text(c, name, 15, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(n);
        if (detail != null && !detail.isEmpty()) {
            TextView dt = Ui.text(c, detail, 12, Ui.DIM, false);
            dt.setSingleLine(true);
            dt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(dt);
        }
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    static TextView pill(Context c, String words) {
        TextView t = Ui.text(c, words, 13, Ui.NAVY_INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Ui.dp(c, 36));
        t.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        t.setBackground(Ui.touch(c, false, Ui.TEXT, 18));
        return t;
    }
}
