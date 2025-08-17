# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Bunny Stream TV ProGuard Rules

# Keep TV Activity and its public methods
-keep class net.bunny.tv.ui.BunnyTVPlayerActivity {
    public static void start(android.content.Context, java.lang.String, long, java.lang.String);
    public <init>(...);
}

# Keep TV Controls
-keep class net.bunny.tv.ui.controls.TVPlayerControlsView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void setBunnyPlayer(net.bunny.bunnystreamplayer.common.BunnyPlayer);
    public void setVideoTitle(java.lang.String);
    public void show();
    public void hide();
}

# Keep TV Key Event Handler
-keep class net.bunny.tv.navigation.TVKeyEventHandler {
    public <init>(**);
    public boolean handleKeyEvent(android.view.KeyEvent);
}

# Keep TV Settings Dialog
-keep class net.bunny.tv.ui.dialogs.TVSettingsDialog {
    public <init>(android.content.Context, net.bunny.bunnystreamplayer.common.BunnyPlayer);
}

# Keep device utils if they exist in the api module
-keep class net.bunny.api.utils.** { *; }

# Preserve TV-specific annotations
-keepattributes *Annotation*

# ExoPlayer and Media3 TV-specific rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Android TV Leanback support
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# TV Provider
-keep class androidx.tvprovider.** { *; }
-dontwarn androidx.tvprovider.**

# Prevent obfuscation of TV resources
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep view constructors for XML inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep bundle arguments for intents
-keepclassmembers class * {
    static final java.lang.String EXTRA_*;
}