package com.gathin.homefieldtv.prefs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * The player as Settings say to make it: its buffer (live TV's or films'), its decoders, its sound (passed through or
 * decoded, mixed down to two speakers, delayed or brought forward against the picture), the sound's languages and
 * tunnelled playback. The live TV and the film player both start theirs here.
 *
 * Other parts of the app that need to hear the sound (the automatic subtitles) hand their AudioProcessor to builder()
 * as an extra: it goes into the same sink, after the mixing down.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerSetup {
    private PlayerSetup() {}

    /** The sound's delay against the picture as the players use it now (µs; Settings and the player's menu change it). */
    static volatile long delayUs = Long.MIN_VALUE;

    /** A player made as Settings say: live TV's or a film's buffer, and any extra hearers of the sound. */
    public static ExoPlayer.Builder builder(Context c, boolean live, AudioProcessor... extra) {
        Context app = c.getApplicationContext();
        if (delayUs == Long.MIN_VALUE) delayUs = PlayerPrefs.delayMs(app) * 1000L;
        // A stream with several pictures adapts to the connection: down as soon as it slows (even with up to 30 s kept),
        // up only once 15 s are kept, so the picture doesn't hop about.
        AdaptiveTrackSelection.Factory adaptive = new AdaptiveTrackSelection.Factory(15_000, 30_000, 25_000, 0.7f);
        DefaultTrackSelector selector = new DefaultTrackSelector(app, adaptive);
        List<String> langs = PlayerPrefs.languages(app);
        DefaultTrackSelector.Parameters.Builder p = selector.buildUponParameters()
                .setTunnelingEnabled(PlayerPrefs.tunnelled(app))
                .setPreferredAudioLanguages(langs.toArray(new String[0]));
        quality(p, PlayerPrefs.quality(app));
        selector.setParameters(p);
        return new ExoPlayer.Builder(app, renderers(app, extra)).setLoadControl(load(app, live)).setTrackSelector(selector);
    }

    /** Settings → Player → Picture quality on the track selector: Auto adapts, Highest keeps the best, Data saver caps it. */
    static void quality(DefaultTrackSelector.Parameters.Builder p, int quality) {
        if (quality == PlayerPrefs.QUALITY_HIGHEST) {
            p.setForceHighestSupportedBitrate(true);
        } else if (quality == PlayerPrefs.QUALITY_SAVER) {
            p.setMaxVideoSize(1024, 576).setMaxVideoBitrate(2_000_000);
        }
    }

    /** The buffer Settings chose for live TV or for films and episodes. */
    public static DefaultLoadControl load(Context c, boolean live) {
        int[] b = PlayerPrefs.bufferMs(live, PlayerPrefs.buffer(c, live));
        return new DefaultLoadControl.Builder().setBufferDurationsMs(b[0], b[1], b[2], b[3]).build();
    }

    /** The decoders and the sound's way out as Settings chose. */
    public static DefaultRenderersFactory renderers(Context c, AudioProcessor... extra) {
        Context app = c.getApplicationContext();
        int decoder = PlayerPrefs.decoder(app);
        boolean downmix = PlayerPrefs.downmix(app);
        boolean passthrough = PlayerPrefs.passthrough(app) && !downmix;
        DefaultRenderersFactory f = new DefaultRenderersFactory(app) {
            @Override protected AudioSink buildAudioSink(@NonNull Context ctx, boolean floatOut, boolean playbackParams) {
                List<AudioProcessor> processors = new ArrayList<>();
                if (downmix) processors.add(downmixer());
                if (extra != null) for (AudioProcessor p : extra) if (p != null) processors.add(p);
                DefaultAudioSink.Builder b = passthrough ? new DefaultAudioSink.Builder(ctx) : pcmOnly();
                b.setEnableFloatOutput(floatOut).setEnableAudioTrackPlaybackParams(playbackParams);
                if (!processors.isEmpty()) b.setAudioProcessors(processors.toArray(new AudioProcessor[0]));
                return new DelaySink(b.build());
            }
        };
        // The app's FFmpeg for sound the TV can't decode (AC-3, E-AC-3, MP2 on UK channels); first when Software.
        f.setExtensionRendererMode(decoder == PlayerPrefs.DECODER_SOFTWARE
                ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        f.setEnableDecoderFallback(decoder != PlayerPrefs.DECODER_HARDWARE);
        if (decoder != PlayerPrefs.DECODER_AUTO) f.setMediaCodecSelector(codecs(decoder == PlayerPrefs.DECODER_SOFTWARE));
        return f;
    }

    /** A sink that knows no encoded sound: everything decoded here, nothing passed through. */
    @SuppressWarnings("deprecation")
    private static DefaultAudioSink.Builder pcmOnly() {
        return new DefaultAudioSink.Builder().setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES);
    }

    /** The TV's decoders: its hardware ones only (all, when it has none for a kind), or the software ones first. */
    static MediaCodecSelector codecs(boolean softwareFirst) {
        return (mime, secure, tunnelling) -> {
            List<MediaCodecInfo> all = MediaCodecUtil.getDecoderInfos(mime, secure, tunnelling);
            List<MediaCodecInfo> hw = new ArrayList<>(), sw = new ArrayList<>();
            for (MediaCodecInfo i : all) (i.softwareOnly ? sw : hw).add(i);
            if (softwareFirst) {
                List<MediaCodecInfo> out = new ArrayList<>(sw);
                out.addAll(hw);
                return out;
            }
            return hw.isEmpty() ? all : hw;
        };
    }

    // ---- two speakers ---------------------------------------------------------------------------------------------

    private static final float HALF = 0.7071f;

    /** Surround mixed to two speakers, from mono up to 7.1, as Android orders the channels. */
    static ChannelMixingAudioProcessor downmixer() {
        ChannelMixingAudioProcessor p = new ChannelMixingAudioProcessor();
        for (int n = 1; n <= 8; n++) p.putChannelMixingMatrix(toStereo(n));
        return p;
    }

    /**
     * Pure: n channels to two, each speaker's sum kept at most 1 (no clipping). Android's order: front left, front
     * right, centre, low frequency (left out), then back left and right (5.1), back centre (6.1) or side left and right
     * (7.1); quad is front and back pairs; three is left, right and centre.
     */
    static ChannelMixingMatrix toStereo(int n) {
        float[][] m = new float[n][2];
        switch (n) {
            case 1:
                m[0] = new float[]{1f, 1f};
                break;
            case 2:
                m[0] = new float[]{1f, 0f};
                m[1] = new float[]{0f, 1f};
                break;
            case 3:
                m[0] = new float[]{1f, 0f};
                m[1] = new float[]{0f, 1f};
                m[2] = new float[]{HALF, HALF};
                break;
            case 4:
                m[0] = new float[]{1f, 0f};
                m[1] = new float[]{0f, 1f};
                m[2] = new float[]{HALF, 0f};
                m[3] = new float[]{0f, HALF};
                break;
            case 5:
                m[0] = new float[]{1f, 0f};
                m[1] = new float[]{0f, 1f};
                m[2] = new float[]{HALF, HALF};
                m[3] = new float[]{HALF, 0f};
                m[4] = new float[]{0f, HALF};
                break;
            default:
                m[0] = new float[]{1f, 0f};
                m[1] = new float[]{0f, 1f};
                m[2] = new float[]{HALF, HALF};
                m[3] = new float[]{0f, 0f};
                if (n >= 6) {
                    m[4] = new float[]{HALF, 0f};
                    m[5] = new float[]{0f, HALF};
                }
                if (n == 7) m[6] = new float[]{0.5f, 0.5f};
                if (n == 8) {
                    m[6] = new float[]{HALF, 0f};
                    m[7] = new float[]{0f, HALF};
                }
                for (int i = 8; i < n; i++) m[i] = new float[]{0f, 0f};
        }
        // Each speaker's sum at most 1.
        for (int out = 0; out < 2; out++) {
            float sum = 0f;
            for (int in = 0; in < n; in++) sum += m[in][out];
            if (sum > 1f) for (int in = 0; in < n; in++) m[in][out] /= sum;
        }
        float[] flat = new float[n * 2];
        for (int in = 0; in < n; in++) for (int out = 0; out < 2; out++) flat[in * 2 + out] = m[in][out];
        return new ChannelMixingMatrix(n, 2, flat);
    }

    // ---- the sound's delay ----------------------------------------------------------------------------------------

    /**
     * The sound moved against the picture: the picture follows the sound's clock, so the clock read ahead (the sound
     * later) or behind (the sound earlier) by the delay. Read on each call, so a change in the player's menu shows at
     * once. Tunnelled playback keeps its own timing: there the delay does nothing.
     */
    static final class DelaySink extends ForwardingAudioSink {
        DelaySink(AudioSink sink) {
            super(sink);
        }

        @Override public long getCurrentPositionUs(boolean sourceEnded) {
            long p = super.getCurrentPositionUs(sourceEnded);
            if (p == AudioSink.CURRENT_POSITION_NOT_SET) return p;
            long d = delayUs == Long.MIN_VALUE ? 0 : delayUs;
            return Math.max(0, p + d);
        }
    }
}
