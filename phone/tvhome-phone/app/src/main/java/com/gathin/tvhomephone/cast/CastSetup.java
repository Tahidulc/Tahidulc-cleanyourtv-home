package com.gathin.tvhomephone.cast;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;

import java.util.List;

/**
 * How Cast is set up for Homefield (named in the manifest, read by Cast itself): Google's own player on the device (the
 * Default Media Receiver), stopped when casting stops, nothing resumed by itself later and no notification of Cast's own
 * (the "Casting to …" bar in Live TV is the control).
 */
public final class CastSetup implements OptionsProvider {
    @NonNull
    @Override public CastOptions getCastOptions(@NonNull Context c) {
        return new CastOptions.Builder()
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .setStopReceiverApplicationWhenEndingSession(true)
                .setResumeSavedSession(false)
                .setEnableReconnectionService(false)
                .setCastMediaOptions(new CastMediaOptions.Builder().setNotificationOptions(null).setMediaSessionEnabled(false).build())
                .build();
    }

    @Override public List<SessionProvider> getAdditionalSessionProviders(@NonNull Context c) {
        return null;
    }
}
