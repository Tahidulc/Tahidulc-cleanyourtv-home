package com.gathin.tvhomephone.live;

import com.gathin.broadcast.PicPlace;

/**
 * Where a programme's picture goes in the phone's places, by the rule shared with Homefield TV (PicPlace: covering only
 * a place close to its own shape, else the whole picture at the place's height beside the words with the place's own
 * colour from the picture continuing behind it; faces whole; never enlarged past 1.5 times), and the words' shade:
 *
 * <pre>
 *   a guide cell   its words (the name or time, and the title) at its left, PicPlace.room() of it; the picture after
 *   the panel      upright: its words and buttons over its left PANEL_ROOM, the picture after them
 *   the header     the pane's top: the picture in the middle, fading at its foot
 *   the band       (PinBackdrop) its words over a share of its left, the picture after them
 * </pre>
 */
final class PicFill {
    private PicFill() {}

    /** The programme's panel upright: its words and buttons take its left PANEL_ROOM, its picture the rest. */
    static final float PANEL_ROOM = 0.6f;

    /** Pure: a picture sw x sh (faces box, or null) in a guide cell w x h. */
    static void row(float sw, float sh, float[] box, float w, float h, float[] out) {
        PicPlace.place(sw, sh, box, w, h, PicPlace.room(w, h), 1f, out);
    }

    /** Pure: the share of a guide cell w x h its words take at its left. */
    static float room(float w, float h) {
        return PicPlace.room(w, h);
    }

    /**
     * Pure: where the words' shade ends its fade (cell px from the cell's left): a little past the words (fade), shorter
     * to stop before the faces, never under least.
     */
    static float shadeFade(float wordsEnd, float facesLeft, float fade, float least, float gap) {
        float room = facesLeft > wordsEnd ? facesLeft - wordsEnd - gap : fade;
        return Math.max(least, Math.min(fade, room));
    }
}
