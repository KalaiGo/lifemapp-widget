# kotlinx.serialization: keep generated serializers of the snapshot / cache models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.kalai73.lifemapp.widgets.**$$serializer { *; }
-keepclassmembers class com.kalai73.lifemapp.widgets.** {
    *** Companion;
}
-keepclasseswithmembers class com.kalai73.lifemapp.widgets.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
