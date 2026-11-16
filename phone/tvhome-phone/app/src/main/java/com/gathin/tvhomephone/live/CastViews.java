package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.cast.CastDevice;
import com.gathin.tvhomephone.cast.Casting;

/**
 * What casting looks like on the phone: in Live TV a bar under the top bar ("Casting to the Bedroom TV", the channel and
 * how it's going, Stop), and in the full-screen player a cover over the picture with Stop casting and Watch on the phone.
 */
final class CastViews {
    private CastViews() {}

    /** The line under "Casting to …": the channel and how it is going. */
    static String line(Context c) {
        String ch = Casting.channel() == null ? c.getString(R.string.cast_pick_channel) : Casting.channel().name;
        switch (Casting.state()) {
            case Casting.CONNECTING: return Casting.channel() == null ? ch : c.getString(R.string.cast_state_starting, ch);
            case Casting.FAILED:
                return Casting.message().isEmpty() ? c.getString(R.string.cast_state_failed, ch) : Casting.message();
            default: return ch;
        }
    }

    static String title(Context c) {
        CastDevice d = Casting.device();
        return d == null ? "" : c.getString(R.string.cast_casting_to, d.name);
    }

    /** Live TV's bar: shown only while casting; Stop ends it. */
    static final class Bar extends LinearLayout implements Casting.Listener {
        private final TextView title, line;

        Bar(Context c) {
            super(c);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
            setBackgroundColor(0xFF15233F);
            ImageView i = new ImageView(c);
            i.setImageResource(R.drawable.live_ic_cast);
            i.setImageTintList(ColorStateList.valueOf(Ui.TEXT));
            addView(i, new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(VERTICAL);
            words.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
            title = Ui.text(c, "", 14, Ui.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line = Ui.text(c, "", 12, Ui.SOFT, false);
            line.setSingleLine(true);
            line.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(title);
            words.addView(line);
            addView(words, new LayoutParams(0, -2, 1f));
            TextView stop = CastSheet.pill(c, c.getString(R.string.cast_stop));
            stop.setOnClickListener(v -> Casting.stop(c));
            addView(stop);
            setOnClickListener(v -> {
                if (getContext() instanceof android.app.Activity) {
                    CastSheet.show((android.app.Activity) getContext(), Casting.channel());
                }
            });
            castChanged();
        }

        @Override public void castChanged() {
            setVisibility(Casting.active() ? VISIBLE : GONE);
            if (!Casting.active()) return;
            title.setText(title(getContext()));
            line.setText(line(getContext()));
            line.setTextColor(Casting.state() == Casting.FAILED ? Ui.PINK : Ui.SOFT);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Casting.listen(this);
            castChanged();
        }

        @Override protected void onDetachedFromWindow() {
            Casting.unlisten(this);
            super.onDetachedFromWindow();
        }
    }

    /**
     * The full-screen player's cover while the channel is on a device: Stop casting (the channel back on the phone),
     * another device, or back to the guide to choose another channel for it.
     */
    static final class Cover extends FrameLayout implements Casting.Listener {
        private final TextView title, line;

        Cover(Context c, Runnable change, Runnable channels) {
            super(c);
            setBackgroundColor(0xF20B0C21);
            setClickable(true);
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.drawable.live_ic_cast);
            icon.setImageTintList(ColorStateList.valueOf(Ui.TEXT));
            box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
            title = Ui.text(c, "", 18, Ui.TEXT, true);
            title.setGravity(Gravity.CENTER);
            title.setPadding(Ui.dp(c, 24), Ui.dp(c, 12), Ui.dp(c, 24), Ui.dp(c, 4));
            // Each as wide as it needs (centred), so the box is as wide as its widest: nothing wraps out of sight.
            box.addView(title, new LinearLayout.LayoutParams(-2, -2));
            line = Ui.text(c, "", 14, Ui.SOFT, false);
            line.setGravity(Gravity.CENTER);
            line.setPadding(Ui.dp(c, 24), 0, Ui.dp(c, 24), 0);
            box.addView(line, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout buttons = new LinearLayout(c);
            buttons.setGravity(Gravity.CENTER);
            buttons.setPadding(0, Ui.dp(c, 20), 0, 0);
            TextView stop = CastSheet.pill(c, c.getString(R.string.cast_stop_casting));
            stop.setOnClickListener(v -> Casting.stop(c));
            buttons.addView(stop);
            TextView other = glass(c, c.getString(R.string.cast_change));
            other.setOnClickListener(v -> change.run());
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2);
            op.leftMargin = Ui.dp(c, 10);
            buttons.addView(other, op);
            box.addView(buttons, new LinearLayout.LayoutParams(-2, -2));
            TextView pick = glass(c, c.getString(R.string.cast_choose_channel));
            pick.setOnClickListener(v -> channels.run());
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
            pp.topMargin = Ui.dp(c, 10);
            box.addView(pick, pp);
            addView(box, new LayoutParams(-2, -2, Gravity.CENTER));
        }

        private static TextView glass(Context c, String words) {
            TextView t = Ui.text(c, words, 13, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(c, 36));
            t.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
            t.setBackground(Ui.touch(c, false, 0x26FFFFFF, 18));
            return t;
        }

        @Override public void castChanged() {
            if (!Casting.active()) return;
            title.setText(title(getContext()));
            line.setText(line(getContext()));
            line.setTextColor(Casting.state() == Casting.FAILED ? Ui.PINK : Ui.SOFT);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Casting.listen(this);
            castChanged();
        }

        @Override protected void onDetachedFromWindow() {
            Casting.unlisten(this);
            super.onDetachedFromWindow();
        }
    }

}
