# Menjaga seluruh package proyek agar tidak terhapus oleh R8
-keep class com.numerasiapp.** { *; }
-keep class com.bilangannumerasiapp.** { *; }
-keep class com.ujiannet.id.** { *; }
-keep class com.game.sos.** { *; }

# Menjaga JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Menjaga AdMob, UMP Consent & Play Integrity
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**
-keep class com.pairip.** { *; }
-keepclassmembers class com.pairip.** { *; }
-dontwarn com.pairip.**

# Menjaga Google Mobile Ads Native Templates
-keep class com.google.android.ads.nativetemplates.** { *; }
-dontwarn com.google.android.ads.nativetemplates.**

# Menjaga Anotasi, Signature, Inner Classes, dan nomor baris log Play Console
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Mencegah obfuscation pada View kustom yang dipakai Native Ad
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
