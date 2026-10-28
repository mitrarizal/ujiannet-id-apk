# Proteksi kelas Application dan Activity utama dari obfuskasi R8
-keep class com.ujiannet.id.MyApplication { *; }
-keep class com.ujiannet.id.MainActivity { *; }
-keepclasseswithmembers class * extends android.app.Application {
    <init>();
}

# Proteksi Google Play Services & AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Proteksi Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Mempertahankan atribut runtime dan refleksi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
