package com.gathin.tvhomephone.live;

import java.util.List;

/**
 * The Live TV tab as it was left: the category chosen last is opened again while the playlist still has it, and the
 * channel played last plays again (LiveTvFragment). Pure, for the tests.
 */
final class LastWatch {
    private LastWatch() { }

    /** Whether a kept category can be opened again: one of the tab's own, or a group the playlist still has. */
    static boolean known(String group, List<String> playlistGroups) {
        // All is the empty name (Library.ALL), so only null is no category.
        if (group == null) return false;
        if (Library.FAVOURITES.equals(group) || Library.ALL.equals(group) || Library.RECENT.equals(group) || SportGuide.GROUP.equals(group)) return true;
        return playlistGroups != null && playlistGroups.contains(group);
    }
}
