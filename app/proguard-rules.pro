# NeverDrop ProGuard Rules
-keepattributes *Annotation*

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Anthropic (Claude) SDK + Jackson (model (de)serialization relies on reflection)
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.anthropic.** { *; }
-dontwarn com.anthropic.**
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn okhttp3.**
-dontwarn okio.**
# The SDK ships a java.net.http backend we never instantiate (we use OkHttp);
# silence references to JDK APIs that are absent on Android.
-dontwarn java.net.http.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**

# kotlinx.serialization (Supabase DTOs) — keep generated serializers
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class com.neverdrop.** {
    *** Companion;
}
-keepclasseswithmembers class com.neverdrop.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.neverdrop.**$$serializer { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Supabase + Ktor
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.conscrypt.**
