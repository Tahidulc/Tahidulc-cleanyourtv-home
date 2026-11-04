package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

/**
 * The selected programme, as the guide shows it above the grid: its title, when, the channel, what it's about, how far
 * in it is when it's on, and what to do: Watch when it's on, Remind me when it's still to come, the favourite's star.
 * Compact on phones held upright (two lines of description, one button); fuller beside the preview and in a sheet.
 */
@SuppressLint("ViewConstructor")
final class Details extends LinearLayout {
    interface Actions {
        void watch(Channel ch);

        /** The reminder was set or cleared (to redraw anything that shows it). */
        void reminded(Channel ch, long start, boolean on);

        void favourite(Channel ch);

        /** The compact panel tapped: everything about it in a sheet. */
        void more(Channel ch, int index);
    }

    private final boolean compact;
    private final Library lib;
    private final TextView channel, title, when, desc;
    private final ImageView logo;
    private final ProgressBar progress;
    private final MaterialButton watch, remind, star;
    private Actions actions;
    private Channel ch;
    private Guide.Schedule s;
    private int i = -1;

    Details(Context c, Library lib, boolean compact, int descLines) {
        super(c);
        this.compact = compact;
        this.lib = lib;
        setOrientation(VERTICAL);
        int pad = Ui.dp(c, compact ? 14 : 18);
        setPadding(pad, Ui.dp(c, compact ? 10 : 16), pad, Ui.dp(c, compact ? 10 : 16));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout box = new FrameLayout(c);
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        if (!compact) head.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 30)));
        channel = Ui.text(c, "", 13, Ui.SOFT, true);
        channel.setSingleLine(true);
        channel.setEllipsize(TextUtils.TruncateAt.END);
        channel.setPadding(compact ? 0 : Ui.dp(c, 10), 0, 0, 0);
        head.addView(channel, new LinearLayout.LayoutParams(0, -2, 1f));

        title = Ui.text(c, "", compact ? 17 : 21, Ui.TEXT, true);
        title.setMaxLines(compact ? 1 : 2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        when = Ui.text(c, "", compact ? 13 : 14, Ui.G_SELECT, true);
        when.setSingleLine(true);
        when.setEllipsize(TextUtils.TruncateAt.END);
        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.RED));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        desc = Ui.text(c, "", compact ? 13 : 14, Ui.SOFT, false);
        desc.setMaxLines(descLines);
        // Upright phones: the panel keeps one height whatever is picked, so the grid under it doesn't jump.
        if (compact) desc.setMinLines(descLines);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setLineSpacing(0, 1.1f);

        watch = button(c, R.string.live_watch, R.drawable.live_ic_play, true);
        watch.setOnClickListener(v -> {
            if (ch != null && actions != null) actions.watch(ch);
        });
        remind = button(c, R.string.live_remind, R.drawable.live_ic_bell, false);
        remind.setOnClickListener(v -> toggleReminder());
        star = button(c, R.string.live_favourite, R.drawable.live_ic_star_outline, false);
        star.setOnClickListener(v -> {
            if (ch != null && actions != null) actions.favourite(ch);
        });

        if (compact) {
            // Title, time and channel beside one button; the description under them.
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(VERTICAL);
            texts.addView(title);
            texts.addView(when);
            top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
            bp.leftMargin = Ui.dp(c, 10);
            top.addView(watch, bp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
            rp.leftMargin = Ui.dp(c, 10);
            top.addView(remind, rp);
            addView(top);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
            pp.topMargin = Ui.dp(c, 6);
            addView(progress, pp);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = Ui.dp(c, 4);
            addView(desc, dp);
            setBackground(Ui.touch(c, false, Ui.G_PANEL, 0));
            setOnClickListener(v -> {
                if (ch != null && actions != null) actions.more(ch, i);
            });
        } else {
            addView(head);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
            tp.topMargin = Ui.dp(c, 8);
            addView(title, tp);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = Ui.dp(c, 4);
            addView(when, wp);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 4));
            pp.topMargin = Ui.dp(c, 8);
            addView(progress, pp);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = Ui.dp(c, 10);
            addView(desc, dp);
            LinearLayout buttons = new LinearLayout(c);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
            b1.rightMargin = Ui.dp(c, 8);
            buttons.addView(watch, b1);
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
            b2.rightMargin = Ui.dp(c, 8);
            buttons.addView(remind, b2);
            buttons.addView(star, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 14);
            addView(buttons, bl);
        }
    }

    private static MaterialButton button(Context c, int label, int icon, boolean primary) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setIconResource(icon);
        b.setIconSize(Ui.dp(c, 18));
        b.setIconPadding(Ui.dp(c, 6));
        b.setCornerRadius(Ui.dp(c, 24));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(Ui.dp(c, 48));
        b.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 18), 0);
        b.setTextColor(primary ? Ui.G_INK : Ui.TEXT);
        b.setIconTint(ColorStateList.valueOf(primary ? Ui.G_INK : Ui.TEXT));
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? Ui.G_SELECT : 0x29FFFFFF));
        return b;
    }

    void setActions(Actions a) { actions = a; }

    Channel channel() { return ch; }

    /** Shows a channel's programme (index into its schedule; -1 for the channel with nothing picked or no guide). */
    void bind(Channel c, int index) {
        ch = c;
        s = c == null ? null : lib.schedule(c.key);
        i = s == null || index < 0 || index >= s.start.length ? -1 : index;
        refresh();
    }

    /** Again as time has moved on (the progress, whether it has started). */
    void refresh() {
        Context c = getContext();
        // Nothing chosen (an empty group): blank, keeping its place (its visibility is the page's to set).
        setAlpha(ch == null ? 0f : 1f);
        if (ch == null) return;
        int n = lib.number(ch);
        String chan = n > 0 ? n + "  " + ch.name : ch.name;
        channel.setText(chan);
        if (ch.logo.isEmpty()) Glide.with(logo).clear(logo);
        else Glide.with(logo).load(ch.logo).into(logo);
        long now = System.currentTimeMillis();
        boolean fav = Picks.isFavourite(c, ch.key);
        star.setText(fav ? R.string.live_fav_remove_short : R.string.live_fav_add_short);
        star.setIconResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
        star.setIconTint(ColorStateList.valueOf(fav ? Ui.STAR : Ui.TEXT));
        if (i < 0) {
            title.setText(ch.name);
            String g = ch.group.isEmpty() ? "" : ch.group;
            when.setText(compact ? (n > 0 ? n + "  ·  " : "") + c.getString(R.string.live_no_guide_info) : g);
            when.setVisibility(compact || !g.isEmpty() ? View.VISIBLE : View.GONE);
            progress.setVisibility(compact ? View.INVISIBLE : View.GONE);
            desc.setText(R.string.live_no_guide_body);
            watch.setVisibility(View.VISIBLE);
            remind.setVisibility(View.GONE);
            return;
        }
        long a = s.start[i], b = s.stop[i];
        boolean on = a <= now && now < b, ahead = a > now;
        title.setText(s.title[i].isEmpty() ? ch.name : s.title[i]);
        // As Sky's guide gives it: the length, and when it started (or starts).
        String w = Ui.day(c, a) + "  " + Ui.range(a, b) + "  ·  " + Ui.length(b - a);
        if (on) w = Ui.length(b - a) + "  ·  " + c.getString(R.string.live_started_at, Ui.clock(a)) + "  ·  "
                + c.getString(R.string.live_min_left, Math.max(1, (int) ((b - now + 59_999) / 60_000)));
        else if (!ahead) w = w + "  ·  " + c.getString(R.string.live_ended);
        if (compact) w = w + "  ·  " + chan;
        when.setText(w);
        when.setVisibility(View.VISIBLE);
        progress.setVisibility(on ? View.VISIBLE : compact ? View.INVISIBLE : View.GONE);
        if (on) progress.setProgress((int) (1000 * (now - a) / Math.max(1, b - a)));
        String d = s.desc == null ? "" : s.desc[i];
        desc.setText(d);
        if (d.isEmpty() && !compact) desc.setText(R.string.live_no_description);
        // On now or over: Watch (the channel). Still to come: Remind me (and Watch the channel beside it, when wide).
        watch.setVisibility(ahead && compact ? View.GONE : View.VISIBLE);
        watch.setText(ahead ? R.string.live_watch_channel : R.string.live_watch);
        remind.setVisibility(ahead ? View.VISIBLE : View.GONE);
        if (ahead) {
            boolean set = Reminders.has(c, ch, a);
            remind.setText(set ? R.string.live_reminded : R.string.live_remind);
            remind.setIconTint(ColorStateList.valueOf(set ? Ui.G_INK : Ui.TEXT));
            remind.setTextColor(set ? Ui.G_INK : Ui.TEXT);
            remind.setBackgroundTintList(ColorStateList.valueOf(set ? Ui.G_SELECT : 0x29FFFFFF));
        }
    }

    private void toggleReminder() {
        if (ch == null || s == null || i < 0) return;
        Context c = getContext();
        long a = s.start[i];
        String t = s.title[i].isEmpty() ? ch.name : s.title[i];
        boolean on = Reminders.toggle(c, ch, t, a);
        Toast.makeText(c, on ? c.getString(R.string.live_reminder_set, t, Ui.day(c, a), Ui.clock(a))
                : c.getString(R.string.live_reminder_removed), Toast.LENGTH_SHORT).show();
        refresh();
        if (actions != null) actions.reminded(ch, a, on);
    }
}
