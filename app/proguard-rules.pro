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
