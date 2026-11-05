# TV Home for Android phones and tablets

The launcher's own parts on a phone: Home with the scores box and weather, Watch (Discover, Movies and TV shows, a
title's page, the Episode map, Coming up, Because you watched, Film night, trailers), Sports (Match Centre, tables, F1,
This weekend), alerts as phone notifications with the Control Centre's switches, and Live TV for the family's own M3U
playlist or Xtream Codes login. It pairs with the TVs as Delivery to TV does and keeps My list, ratings, the Film
night plan, Coming up's mutes, reminders and starred clubs the same everywhere (SYNC.md).

Full screen, a tap brings up an info tile that suits what's on (the Full-screen TV Info Tiles canvas): Match night for a
game (the scores box with both crests and the competition's logo, the fixture, kick-off or the score, each club's
form or the game so far), Cinematic for a film, Card for a series' episode and Glass ribbon for the rest, each with
the channel's logo, and facts in turn while a game is on (its numbers, the table, each side's run, earlier meetings,
top scorers). The player's options can set one style for everything, or the guide panel as before.

The scores box is the TV's, as at first: the competition's logo on its navy block, each club's crest on its own white
tile, and the blue middle with the score (or the kick-off time) and under it the clock live in minutes and seconds,
going on second by second between ESPN's reads (67:23; 90:00 with +4 on a red chip in added time), or HT, FT or
KICK-OFF.
Full screen, the picture goes into a small window as you leave the app (from the guide too; the player's options turn
that off), and a phone on its side stretches the picture to the screen. The info tile is Small at first (on its side a
card at the foot's left, a little over half the width, so the game shows; upright, in the room under the picture),
Medium or Large in the player's options, which also set how long it stays (4, 8, 15 or 30 seconds, or until a swipe
down takes it away). A game's tile and its page have "Also showing on", from the family's own playlist: the channels
whose guide has the game and the playlist's channels of the broadcasters it is on, each with its logo, the likeliest
first and marked Best match; a channel's 4K/UHD version is listed too, with a "4K" mark on its logo (4K channels in Live
TV wear the same mark, and one with no logo of its own shows its channel's, Sky's, matched past "UK:" and "UHD").
Broadcasters the playlist has none of are named only when it has nothing for the game.

Players have live ratings out of ten (Ratings), worked out from ESPN's figures for them in the game: everyone on starts
at 6.4, goals, assists, shots on target and fouls won lift it, fouls, offsides, cards and own goals bring it down, a
keeper's saves and a clean sheet count, and the score moves it a touch. They show by each name on the line-ups pitch
(the best with a star) and the substitutes, and the full-screen tile's facts name each side's top rated.

A game's alerts come as cards: folded, what happened and who with the scoring side's crest; opened out, both crests on
white discs, the score with the scoring side's number lit, the minute, and the 90 minutes with each goal on them. Under
each, and under the kick-off and line-ups alerts too: Watch match (the game in Live TV, on the playlist channel that
"Also showing on" puts first), Match and Mute game. "Following live" draws the live
games as the scores box's rows. A game's page has the header card (crests, score or kick-off, the clock, the scorers,
where it is on, Remind me), then Timeline (the game on a pitch, each shot, goal and card where it happened, the newest
pulsing, then every moment by its minute), Line-ups (both elevens on one pitch by their formations, the substitutes
under it, with the players' photos) and Stats in each side's colours. The line-ups come as both elevens on a pitch,
an F1 result as its podium, and "Following live" as the scoreboards with the first game's clock going on.

Titles open in the licensed streaming app that carries them in the UK (or its store), on the phone or, with Play on TV,
on a TV. A title on one of the phone's streaming apps plays from your own playlist when it has it (its films and
series, Vod: matched by TMDB's id when the provider gives one, else by name and year; a series asks for its season
and episode) in a full-screen player that remembers where you stopped, and only otherwise through Debrid Stream or the
app. Live TV and those films and series play only the playlist you give it; the app ships with no channels or
sources. The tabs are Home, Sports, Live TV, Watch and Settings.

Build: JDK 17, Android SDK 34. `./gradlew assembleRelease testReleaseUnitTest lintRelease`; the APK is in
`app/build/outputs/apk/release/`, signed with the debug key for sideloading.
