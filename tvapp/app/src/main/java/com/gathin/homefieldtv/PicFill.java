package com.gathin.homefieldtv;

import com.gathin.broadcast.PicPlace;

/**
 * Where a programme's picture goes in Homefield TV's places, by the rule shared with the phone app (PicPlace: covering
 * only a place close to its own shape, else the whole picture at the place's height beside the words with the place's own
 * colour from the picture behind it; faces whole; never enlarged past 1.5 times): the free part of each place, where the
 * words aren't.
 *
 * <pre>
 *   a guide cell     its words (a title and a line of times) at its left, PicPlace.room() of it; the picture after them
 *   the info card    its words over its left CARD_ROOM, what's next from CARD_END; the picture between them
 *   the panel        (GuideBand's picture box, Pics.PANEL) its left Pics.PANEL_FADE faded into the panel; the picture after
 *   a card or tile   (FACE_MID, CARD) its words under it: the picture in the middle
 * </pre>
 */
final class PicFill {
    private PicFill() {}

    /**
     * The info card (1824 wide): its words take its left CARD_ROOM, what's next its right from CARD_END (NEXT_X less a
     * little), its faces between them.
     */
    static final float CARD_ROOM = 0.52f, CARD_END = (InfoCard.NEXT_X - InfoCard.X - 24f) / InfoCard.W;

    /** Pure: a picture sw x sh (faces box, or null) in a guide cell w x h. */
    static void row(float sw, float sh, float[] box, float w, float h, float[] out) {
        PicPlace.place(sw, sh, box, w, h, PicPlace.room(w, h), 1f, out);
    }

    /** Pure: a picture sw x sh (faces box, or null) filling the info card w x h. */
    static void card(float sw, float sh, float[] box, float w, float h, float[] out) {
        PicPlace.place(sw, sh, box, w, h, CARD_ROOM, CARD_END, out);
    }
}
