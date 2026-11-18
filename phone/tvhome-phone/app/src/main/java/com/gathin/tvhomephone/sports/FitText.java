package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * One line of words as large as it is set, made smaller (never below a third of that) when it is wider than its room:
 * a surname, a team's name. Worked out as it is measured, not as it is drawn.
 */
final class FitText extends AppCompatTextView {
    private float full;

    FitText(Context c, float sp) {
        super(c);
        setSingleLine(true);
        setIncludeFontPadding(false);
        setSize(sp);
    }

    /** The size it is set at (sp) when it fits. */
    void setSize(float sp) {
        full = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
        setTextSize(TypedValue.COMPLEX_UNIT_PX, full);
        requestLayout();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int room = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        if (MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED && room > 0) {
            getPaint().setTextSize(full);
            float w = getPaint().measureText(getText().toString());
            float size = w > room ? Math.max(full / 3f, full * room / w * 0.98f) : full;
            if (Math.abs(size - getTextSize()) > 0.5f) setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            else getPaint().setTextSize(getTextSize());
        }
        super.onMeasure(wSpec, hSpec);
    }
}
