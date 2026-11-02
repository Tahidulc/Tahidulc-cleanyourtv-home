package com.gathin.tvhome;

import android.service.dreams.DreamService;

/**
 * Ambient mode as the TV's screensaver (AmbientScene): it starts when the TV has been idle for its screensaver time,
 * over any app that lets the screen rest. Pick it in Settings, or `settings put secure screensaver_components
 * com.gathin.tvhome/.AmbientDream` over adb.
 */
public class AmbientDream extends DreamService {

    private AmbientScene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        scene = new AmbientScene(this);
        setContentView(scene.view());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.close();
        super.onDetachedFromWindow();
    }
}
