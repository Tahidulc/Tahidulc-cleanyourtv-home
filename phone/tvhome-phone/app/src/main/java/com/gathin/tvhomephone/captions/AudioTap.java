package com.gathin.tvhomephone.captions;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

/**
 * A listener on the player's sound, put in its audio chain (DefaultAudioSink's processors): the sound goes on to the
 * speaker untouched, and while something listens a copy goes to it as 16 kHz mono (Pcm). Nothing is kept or sent
 * anywhere: the listener is the phone's own speech recognition. One player at a time uses it.
 */
@OptIn(markerClass = UnstableApi.class)
public final class AudioTap extends BaseAudioProcessor {
    private final Pcm.Resampler resampler = new Pcm.Resampler();
    private volatile Pcm.Out listener;

    /** Who hears the sound now (null: no one). */
    public void listen(Pcm.Out out) {
        listener = out;
    }

    @NonNull
    @Override protected AudioFormat onConfigure(@NonNull AudioFormat in) {
        // 16-bit or float PCM is heard; anything else (passed through to an amplifier) goes by unheard.
        if (in.encoding != C.ENCODING_PCM_16BIT && in.encoding != C.ENCODING_PCM_FLOAT) return AudioFormat.NOT_SET;
        resampler.configure(in.sampleRate, in.channelCount, in.encoding == C.ENCODING_PCM_FLOAT);
        return in;
    }

    @Override public void queueInput(@NonNull ByteBuffer input) {
        int n = input.remaining();
        if (n == 0) return;
        Pcm.Out out = listener;
        if (out != null) {
            try {
                resampler.feed(input.duplicate().order(input.order()), out);
            } catch (RuntimeException ignored) {
                // a listener's trouble never stops the sound
            }
        }
        ByteBuffer copy = replaceOutputBuffer(n);
        copy.put(input);
        copy.flip();
    }

    @Override protected void onFlush() {
        resampler.reset();
    }

    @Override protected void onReset() {
        resampler.reset();
    }
}
