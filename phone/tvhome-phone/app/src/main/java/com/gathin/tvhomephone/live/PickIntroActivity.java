package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sync.TvUpload;

/**
 * Chooses the family's own intro video (Android's file picker), keeps a copy, and sends it to the paired TVs; says how
 * it went in a line. No screen of its own.
 */
public final class PickIntroActivity extends Activity {
    private static final int PICK = 41;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*");
        try {
            startActivityForResult(pick, PICK);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker on this phone", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            // nothing chosen: a video kept before stays chosen
            if (IntroVideo.has(this)) IntroVideo.setUseOwn(this, true);
            finish();
            return;
        }
        android.content.Context app = getApplicationContext();
        Toast.makeText(app, "Getting the video ready…", Toast.LENGTH_SHORT).show();
        App.io.execute(() -> {
            String why = IntroVideo.take(app, uri);
            App.main.post(() -> {
                if (why != null) {
                    Toast.makeText(app, why, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(app, "Intro video: " + IntroVideo.words(app) + ". Sending it to the TV…", Toast.LENGTH_LONG).show();
                TvUpload.introVideo(app, IntroVideo.file(app), IntroVideo.name(app),
                        (line, err) -> Toast.makeText(app, line, Toast.LENGTH_LONG).show());
            });
        });
        finish();
    }
}
