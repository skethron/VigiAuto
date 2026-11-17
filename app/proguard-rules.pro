# VigiAuto - regras de ofuscação

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class br.com.vigiauto.**$$serializer { *; }
-keepclassmembers class br.com.vigiauto.** {
    *** Companion;
}
-keepclasseswithmembers class br.com.vigiauto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
