package com.gathin.homefieldtv.captions;

import java.nio.ByteBuffer;

/**
 * The channel's sound made into what speech recognition wants: one channel (5.1 and 7.1 mostly from the centre, where
 * the voices are), 16 kHz, 16-bit, in pieces of a tenth of a second. Pure Java: no Android in here.
 */
public final class Pcm {
    private Pcm() {}

    public static final int RATE = 16_000;
    /** A piece: a tenth of a second at 16 kHz. */
    public static final int CHUNK = RATE / 10;

    /** Where the pieces go (each its own array, safe to keep). */
    public interface Out {
        void pcm(short[] samples, int n);
    }

    /** Downmixes and resamples the player's PCM as it goes by. Not thread-safe: one audio thread feeds it. */
    public static final class Resampler {
        private int inRate = 48_000, channels = 2;
        private boolean floats;
        private long phase;
        private double sum;
        private int count;
        private float last;
        private short[] buf = new short[CHUNK];
        private int filled;

        /** The player's format: its rate, its channels, and whether it is 32-bit float (else 16-bit). */
        public void configure(int rate, int channelCount, boolean isFloat) {
            inRate = Math.max(1, rate);
            channels = Math.max(1, channelCount);
            floats = isFloat;
            reset();
        }

        public void reset() {
            phase = 0;
            sum = 0;
            count = 0;
            filled = 0;
            buf = new short[CHUNK];
        }

        /** Reads every whole frame left in the buffer (its position moves to the end of them). */
        public void feed(ByteBuffer in, Out out) {
            int frame = (floats ? 4 : 2) * channels;
            while (in.remaining() >= frame) {
                float mono = mix(in);
                sum += mono;
                count++;
                phase += RATE;
                // One sample out for every inRate/RATE frames in: the average of those frames (it keeps the voices
                // and drops what 16 kHz can't hold).
                while (phase >= inRate) {
                    phase -= inRate;
                    float v = count > 0 ? (float) (sum / count) : last;
                    last = v;
                    sum = 0;
                    count = 0;
                    emit(v, out);
                }
            }
        }

        private float mix(ByteBuffer in) {
            if (channels == 6 || channels == 8) {
                // 5.1 / 7.1 in Android's order: front left, front right, centre, LFE, then the rest.
                float l = sample(in), r = sample(in), centre = sample(in);
                for (int i = 3; i < channels; i++) sample(in);
                return 0.5f * centre + 0.25f * (l + r);
            }
            float total = 0;
            for (int i = 0; i < channels; i++) total += sample(in);
            return total / channels;
        }

        private float sample(ByteBuffer in) {
            return floats ? in.getFloat() * 32767f : in.getShort();
        }

        private void emit(float v, Out out) {
            int s = Math.round(v);
            buf[filled++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            if (filled == buf.length) {
                short[] full = buf;
                buf = new short[CHUNK];
                filled = 0;
                if (out != null) out.pcm(full, full.length);
            }
        }
    }

    /** Pure: little-endian bytes of 16-bit samples (as a pipe to the TV's recogniser takes them). */
    public static byte[] bytes(short[] s, int n) {
        byte[] b = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            b[2 * i] = (byte) (s[i] & 0xFF);
            b[2 * i + 1] = (byte) ((s[i] >> 8) & 0xFF);
        }
        return b;
    }
}
