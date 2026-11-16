# The live overlays' page (overlays/assets/broadcast) calls the bridge of com.gathin.broadcast.BroadcastView by name.
-keepclassmembers class com.gathin.broadcast.** {
    @android.webkit.JavascriptInterface <methods>;
}
# Media3 makes the FFmpeg audio renderer by name (DefaultRenderersFactory, extension renderers on).
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
