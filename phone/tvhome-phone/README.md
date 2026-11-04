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

The scores box is the broadcasters' bar: each club's code on a slanted wedge in its own colour, the scores either side
of the competition's logo in the dark middle, and the clock in a tab under it in minutes and seconds (45:00 and +2 in
added time). A goal plays out as the broadcasters' bar does, in the scoring club's colours: its colour sweeps across
from its end with a flash, its code at the left, its crest popping in on a chevron of its second colour where the
competition's logo was, GOAL dropping in letter by letter at the right (its O a spinning ball), the clock carrying on
under it; then it pulls back and the new number rolls up.
Full screen, the picture goes into a small window as you leave the app (from the guide too; the player's options turn
that off), and a phone on its side stretches the picture to the screen. Upright, the info tile stays in the room under
the picture. A game's tile and its page have "Also showing on": every channel with the same game, with its logo.

A game's alerts come as cards: folded, what happened and who with the scoring side's crest; opened out, both crests on
white discs, the score with the scoring side's number lit, the minute, and the 90 minutes with each goal on them. Under
each: Watch (the channel in Live TV, when the playlist has it), Match and Mute game. "Following live" draws the live
games as the scores box's rows. A game's page has the header card (crests, score or kick-off, the clock, the scorers,
where it is on, Remind me), then Timeline (the game on a pitch, each shot, goal and card where it happened, the newest
pulsing, then every moment by its minute), Line-ups (both elevens on one pitch by their formations, the substitutes
under it, with the players' photos) and Stats in each side's colours. The line-ups come as both elevens on a pitch,
an F1 result as its podium, and "Following live" as the scoreboards with the first game's clock going on.

Titles open in the licensed streaming app that carries them in the UK (or its store), on the phone or, with Play on TV,
on a TV. Live TV plays only the playlist you give it; the app ships with no channels or sources.

Build: JDK 17, Android SDK 34. `./gradlew assembleRelease testReleaseUnitTest lintRelease`; the APK is in
`app/build/outputs/apk/release/`, signed with the debug key for sideloading.
