package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

/**
 * Split screen's check (TwoUp): opened by the TV's own debugging connection in a window of its own, it notes whether
 * Android really put it in one (the TCL's system switches multi-window off, every app full screen whatever is asked)
 * and closes at once, unseen (it is see-through).
 */
public class TwoUpProbe extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TwoUp.probed(this, isInMultiWindowMode());
        finish();
        overridePendingTransition(0, 0);
    }
}
