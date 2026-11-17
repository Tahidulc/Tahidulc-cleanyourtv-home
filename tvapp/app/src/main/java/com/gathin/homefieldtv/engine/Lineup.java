package com.gathin.homefieldtv.engine;

import java.util.Collections;
import java.util.List;

/**
 * One of the lists of live channels the screens show (all the channels listed, a group's, one of the guide's
 * categories, or a short list such as the favourites), with where a channel or a number is in it, found at once from
 * the lists the library made (Lists), however many channels there are.
 */
public final class Lineup {
    static final int EVERY = 0, GROUP = 1, KIND = 2, FEW = 3;
    static final Lineup NONE = new Lineup(Lists.EMPTY, FEW, "", -1, Collections.emptyList());

    public final List<Channel> channels;
    private final Lists lists;
    private final int kind, genre;
    private final String group;

    Lineup(Lists lists, int kind, String group, int genre, List<Channel> channels) {
        this.lists = lists;
        this.kind = kind;
        this.group = group;
        this.genre = genre;
        this.channels = channels;
    }

    /** Where a channel is in it (by its key), or -1. */
    public int indexOf(Channel c) {
        if (c == null) return -1;
        if (kind == FEW) {
            for (int i = 0; i < channels.size(); i++) if (channels.get(i).key.equals(c.key)) return i;
            return -1;
        }
        int e = lists.index.find(c);
        if (e < 0) return -1;
        switch (kind) {
            case EVERY: return lists.at[e];
            case GROUP: return lists.index.channels.get(e).group.equals(group) ? lists.inGroup[e] : -1;
            default: return lists.index.genre[e] == genre ? lists.inGenre[e] : -1;
        }
    }

    /** Where the first channel with this number is in it, or -1. */
    public int indexOfNumber(int number) {
        if (kind == FEW) {
            for (int i = 0; i < channels.size(); i++) if (lists.index.number(channels.get(i)) == number) return i;
            return -1;
        }
        long[] nums = lists.numbers;
        for (int k = lists.numbered(number); k >= 0 && k < nums.length && (int) (nums[k] >> 32) == number; k++) {
            int i = indexOf(lists.all.get((int) nums[k]));
            if (i >= 0) return i;
        }
        return -1;
    }
}
