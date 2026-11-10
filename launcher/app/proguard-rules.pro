# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# The live overlays' page (overlays/assets/broadcast) calls the bridge of com.gathin.broadcast.BroadcastView by name.
-keepclassmembers class com.gathin.broadcast.** {
    @android.webkit.JavascriptInterface <methods>;
}
