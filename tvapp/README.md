# Homefield TV

An IPTV app for Android TV only, in the look of Sky Q's interface: the blue screens, the menu down the left with the
time and a mini TV, the TV guide, the channel banner over live TV and the playback bar. It plays the family's own
playlists (an M3U address or an Xtream Codes login): their live channels and guide, and their own films and TV shows,
browsed by the playlist's categories. It ships with no playlists or sources of its own, and films and series come only
from the playlists added to it. The app's name and wordmark are Homefield; it carries no Sky logo or name.

## Screens

- **Home:** the menu (Home, TV guide, Movies, TV shows, Sports, Search, Settings), the time and a mini TV playing the
  last channel; a large picture of what is highlighted, then rows: On now, Continue watching, New films, New TV shows,
  Sport on now and favourite channels.
- **TV guide:** categories down the left (All channels, Entertainment, Sport, News, Movies, Kids, Documentaries, Music,
  Favourites, then the playlist's own groups), the highlighted programme's details at the top, the channels with their
  numbers and logos, a now line. Left and Right move through time, Up and Down through the channels, OK watches, holding
  OK makes a channel a favourite, the number keys go to a channel.
- **Live TV:** full screen with the channel banner (the channel between up and down arrows, the day and time, now and
  next). Up and Down look along the channels, OK changes to one, the channel keys change at once, Back hides the banner.
  A stream that drops is tried on the same channel in another playlist, then again.
- **Films and TV shows (dark):** a slim rail down the left (Homefield's mark, the time, Home, the TV guide, Films, TV
  shows, Search, Settings). First every category at a glance: a row of posters a category (Continue watching and My
  list first, then Recently added) under a big picture of whatever the remote is on: its backdrop, title, facts (★
  rating, genre, running time, age rating), story and who is in it. Left from a row's first poster opens the side panel
  of lists: All categories, all the films (or shows), Continue watching, My list, then each category with how many it
  has; any of them but All categories shows as one grid of posters beside the panel.
- **Posters**, wherever they show (the rows, the grids, More like this), as the phone draws them: a 2:3 card with
  rounded corners whose foot fades into the poster's own colour, the title in bold over the fade with "Genre • ★ 8.2"
  under it, "New" on the top left for a title the playlist added in the last fortnight, the best picture the family's
  playlists have of it on the top right (4K, 1080p or 720p in a white chip, read from the names, with the Dolby Vision
  or Atmos mark where a name says so), and the title and year under the card. The poster the remote is on grows a little
  inside a white ring.
- **A title (dark):** its backdrop filling the screen and its title large; Play (or Resume where it was left) with round
  buttons beside it (My list's heart, from the start, the trailer in the TV's YouTube app); the playlist it is from, its
  story and facts in the middle, who is in it on the right. Down from there: a series' seasons as pills over one row of
  every episode, season after season (each with its still, "S1 E8", its title, two lines of story, its length and when
  it was first shown), the season's pill following the row and a season chosen sliding the row to its first episode;
  then the trailer, the cast in circles (initials, as the playlist gives no photos), and more from its category.
- **Player:** the playback bar. OK pauses and plays, Left and Right go back and on (faster when held), Back keeps the
  place, and a series goes on to the next episode.
- **Search:** channels, films and TV shows, typed on the on-screen keyboard.
- **Settings:** the playlists (all of them together or one, their expiry dates), adding an M3U playlist or an Xtream
  Codes login, reading them again, removing everything, and the channel logos: on black tiles (each logo trimmed and set
  in the middle of a black tile, a dark one turned light) or as the playlist gives them.

## Getting a playlist on to a TV

On the TV: Settings, then Add an Xtream Codes login (or Add an M3U playlist), typed on the on-screen keyboard. A playlist
is kept only when it gives live channels.

From a computer with ADB connected to the TV (the setup entry point needs a permission only the ADB shell has):

```
adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream \
  --bind name:s:Home --bind server:s:http://host:port --bind user:s:NAME --bind pass:s:PASSWORD
adb shell content insert --uri content://com.gathin.homefieldtv.setup/m3u --bind name:s:Home --bind url:s:'http://…'
adb shell content query --uri content://com.gathin.homefieldtv.setup/playlists
adb shell content delete --uri content://com.gathin.homefieldtv.setup/playlists/p1
```

The login stays in the app's private storage on that TV. It is never logged, and the app shows the password only as dots.

## Building

JDK 17, Android SDK 35. In this folder:

```
./gradlew --no-daemon assembleRelease testReleaseUnitTest lintRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`, signed with the debug key for sideloading.

## Versions

| Version | What changed |
|---|---|
| 1.0 | The first version: Home, the TV guide, live TV with the channel banner, Movies and TV shows from the playlists, title pages with seasons and episodes, the player with the playback bar, Search and Settings. |
| 1.1 | Channel logos on black tiles, drawn as the TV launcher and the phone draw them: in the guide (the channel column and the large logo at the top), the channel banner's cards, Home's channel cards and its large picture, and Search. Each logo from the playlist is trimmed and set in the middle of a black tile; a white JPEG box round it is taken away and a mostly dark logo is turned light. Settings, Channel logos: on black tiles, or as the playlist gives them. |
| 1.2 | Films and TV shows in a new dark look: the rail; every category at a glance under a big picture of the title the remote is on; the side panel of lists (All categories, all of them, Continue watching, My list, the categories with their counts) and a grid for each; posters drawn as the phone's (a fade into the poster's own colour, New, 4K / 1080p / 720p and Dolby marks from the playlists' names, genre and rating, title and year); a title's page with the backdrop full screen, Play and round buttons, the story, facts and cast, a series' season pills over one row of every episode, the trailer, the cast and more like it. My list (the heart). Names lose the picture and sound tags after them. Outfit (SIL Open Font License 1.1) for the dark screens. |
