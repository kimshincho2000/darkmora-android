# Keep Gson data models and serialized field names
-keep class uz.darkmora.app.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep WorkManager workers instantiated via reflection
-keep class uz.darkmora.app.notifications.EpisodeNotificationWorker { *; }

# Keep Media3 / OkHttp optional reflection warnings clean
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
