# JS bridge methods must keep their names
-keepclassmembers class egypt.shortdrama.top.WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
# Firestore model classes
-keep class egypt.shortdrama.top.model.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**
