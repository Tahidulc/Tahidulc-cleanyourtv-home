package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.List;

/**
 * A card of glass with its name on the left and three posters fanned on the right, tilted and overlapping, cut off by
 * the card's rounded edge as the reference's genre cards are (Action, Animation, Comedy). Chosen, its glass brightens.
 */
public class FanCard extends FrameLayout {
    /** The posters' tilts, back to front, and how far in from the right each sits (dp). */
    private static final float[] TURN = {-14f, -4f, 9f};
    private static final int[] IN_DP = {66, 38, 10};
    private final GlassDrawable glass;
    private final TextView name;
    private final ImageView[] posters = new ImageView[3];
    private final float posterRadius;

    public FanCard(Context c) {
        super(c);
        glass = Glass.card(c, 22);
        setBackground(glass);
        Glass.round(this, Glass.dp(c, 22));
        posterRadius = Glass.dp(c, 7);
        for (int i = 0; i < 3; i++) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setRotation(TURN[i]);
            v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            v.setElevation(Glass.dp(c, 2 + i * 2));
            Glass.round(v, posterRadius);
            LayoutParams lp = new LayoutParams(Glass.dp(c, 58), Glass.dp(c, 87), Gravity.END | Gravity.CENTER_VERTICAL);
            lp.rightMargin = Glass.dp(c, IN_DP[i]);
            lp.topMargin = Glass.dp(c, i == 1 ? -6 : 8);
            addView(v, lp);
            posters[i] = v;
        }
        name = Glass.text(c, "", 17, Glass.SOFT, Glass.heading(c, 500));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams np = new LayoutParams(Glass.dp(c, 104), -2, Gravity.START | Gravity.CENTER_VERTICAL);
        np.leftMargin = Glass.dp(c, 18);
        // Above the posters (which stand out a little), so a long name is never under them.
        name.setTranslationZ(Glass.dp(c, 12));
        name.setShadowLayer(Glass.dp(c, 6), 0, 0, 0x99000000);
        addView(name, np);
        setClickable(true);
        setFocusable(true);
    }

    /** Its name and up to three posters (URLs); a missing one shows its own quiet colours. */
    public void set(String title, List<String> urls) {
        name.setText(title);
        setContentDescription(title);
        for (int i = 0; i < 3; i++) {
            // The front poster is the first: the list's best.
            String u = urls == null || urls.size() <= 2 - i ? null : urls.get(2 - i);
            ImageView v = posters[i];
            Drawable ph = Glass.placeholder(title + i, posterRadius);
            if (u == null || u.isEmpty()) {
                try {
                    Glide.with(v).clear(v);
                } catch (RuntimeException ignored) {
                    // the page went away
                }
                v.setImageDrawable(ph);
                continue;
            }
            try {
                Glide.with(v).load(u).placeholder(ph).transform(new CenterCrop(), new RoundedCorners(Math.round(posterRadius))).into(v);
            } catch (RuntimeException ignored) {
                v.setImageDrawable(ph);
            }
        }
    }

    /** Chosen (the list showing below it): brighter glass, white name. */
    public void setPicked(boolean on) {
        glass.setLift(on ? 1f : 0f);
        name.setTextColor(on ? Glass.TEXT : Glass.SOFT);
        setSelected(on);
    }

    /** For a test: the posters' views, back to front. */
    View poster(int i) {
        return posters[i];
    }
}
