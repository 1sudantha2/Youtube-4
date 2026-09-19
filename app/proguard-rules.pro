# ─────────────────────────────────────────────────────────────────────────────
# YT4 — R8 full-mode rules.
# Philosophy: keep as little as possible. Everything below is either reached
# reflectively, loaded by class name, or a native/JS boundary.
# ─────────────────────────────────────────────────────────────────────────────

# Keep line numbers for readable crash stacks (tiny size cost, huge debug value).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations survive so @Immutable/@Stable-backed metadata and serializers stay resolvable.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ── kotlinx.serialization ────────────────────────────────────────────────────
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class com.yt4.app.**$$serializer { *; }
-keepclassmembers class com.yt4.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.yt4.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── OkHttp / Okio ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Kotlin extension functions on Request/Response used via SAM interop.
-keepclassmembers class okhttp3.** { *; }

# ── Media3 (ExoPlayer) ───────────────────────────────────────────────────────
# Media3 ships consumer rules for its reflective bits (extensions, RenderersFactory
# via DefaultRenderersFactory subclasses). We only need the service + session glue.
-keep class androidx.media3.session.** { *; }
-keep class com.yt4.app.player.PlayerService { *; }
-dontwarn androidx.media3.**

# ── NewPipeExtractor (+ Rhino, jsoup, nanojson) ──────────────────────────────
# NPE walks JSON via nanojson and evaluates player JS via Rhino; both are
# reflection-heavy, so keep them wholesale.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.mozilla.**
-dontwarn org.jsoup.**
-dontwarn com.grack.nanojson.**

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Tink (used internally by androidx.security:security-crypto) ────────────
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── WebView ──────────────────────────────────────────────────────────────────
# No JS bridges are registered; nothing to keep beyond defaults.
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String);
}

# ── Enums used in serialization / DataStore keys ─────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
