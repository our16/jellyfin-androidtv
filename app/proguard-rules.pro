-dontwarn org.commonmark.ext.gfm.strikethrough.Strikethrough

# Keep AboutLibraries data
-keep class com.mikepenz.aboutlibraries.** { *; }
-keepclassmembers class * extends com.mikepenz.aboutlibraries.model.** { *; }

# Keep aboutlibraries.json raw resource (loaded at runtime via context)
-keep class org.jellyfin.androidtv.R$raw {
    <fields>;
}
-keep class org.jellyfin.androidtv.R$raw$aboutlibraries { *; }
