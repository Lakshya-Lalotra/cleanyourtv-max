# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# The Marvel screen's player reports back through this from the page's script; R8 cannot see that call.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
