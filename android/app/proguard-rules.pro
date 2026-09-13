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

# Gson serializes/deserializes DoffRepository's Serial* classes by reflecting on their field
# names (both the on-disk DataStore blob and the JSON export/import backup use this). Without
# these keep rules, R8 renaming those fields would make every existing user's saved data and any
# previously exported backup silently fail to parse after updating to a minified build.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.jekael.adoel.data.Serial* { *; }
-keepclassmembers class com.jekael.adoel.data.Serial* { *; }
-keep class com.jekael.adoel.data.Sync* { *; }
-keepclassmembers class com.jekael.adoel.data.Sync* { *; }
-dontwarn com.google.gson.**

# MesinTipe is (de)serialized via its enum name (.name / .valueOf), not Gson's own enum handling,
# but keep the standard values()/valueOf() members regardless so that lookup can't break.
-keepclassmembers enum com.jekael.adoel.data.MesinTipe {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
