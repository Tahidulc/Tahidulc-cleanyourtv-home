package com.gathin.homefieldtv.captions;

/**
 * Something that turns speech into words as it is heard, on the device: the TV's own recogniser (OnDeviceEngine) or
 * an offline model (VoskEngine). Fed 16 kHz mono 16-bit sound from any thread; tells its listener on the main thread.
 */
interface CaptionEngine {
    interface Listener {
        /** The words of what is being said now, as far as they go (they may still change). */
        void partial(String text);

        /** What was just said, finished. */
        void line(String text);

        /** It can't go on: why, and whether the other engine should try instead. */
        void failed(String why, boolean tryOther);
    }

    void start(Listener l);

    void feed(short[] pcm, int n);

    void stop();
}
