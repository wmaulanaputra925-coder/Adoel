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

# Gson serializes/deserializes every model in com.jekael.adoel.data by reflecting on field names —
# the on-disk DataStore blob, JSON export/import backups, and the QR/text handover payload
# (SyncEnvelope/SyncPayload) all go through this same reflection-over-field-names path. This used
# to be a classname-pattern rule matching only "Serial*", which missed SyncEnvelope/SyncPayload:
# a release (minified) build silently renamed their fields to single letters, breaking QR/text
# sync while backup export/import (Serial*, which did match) kept working — the two paths ended
# up obfuscated inconsistently, and nothing caught it until sync failed in the field. Keeping the
# whole package instead — it's small and model-only, no controllers/ViewModels in it — means a
# future data class here is safe by default instead of needing its own keep rule remembered by
# hand every time.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.jekael.adoel.data.** { *; }
-keepclassmembers class com.jekael.adoel.data.** { *; }
-dontwarn com.google.gson.**

# MesinTipe is (de)serialized via its enum name (.name / .valueOf), not Gson's own enum handling.
# Already covered by the package-wide rule above, but pinned explicitly too — it's the one member
# whose loss would silently produce the wrong machine type instead of a crash, so it stays safe
# even if this file's package rule is ever narrowed later without re-checking this specific case.
-keepclassmembers enum com.jekael.adoel.data.MesinTipe {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
