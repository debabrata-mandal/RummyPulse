# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Firebase rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep all model classes
-keep class com.example.rummypulse.data.** { *; }
-keep class com.example.rummypulse.ui.home.GameItem { *; }

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# Keep RecyclerView adapters
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter {
    public void onBindViewHolder(*, int);
    public * onCreateViewHolder(android.view.ViewGroup, int);
    public int getItemCount();
}

# Keep ViewHolders
-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    public <init>(android.view.View);
}

# Keep LiveData and ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class * extends androidx.lifecycle.LiveData {
    public <init>(...);
}

# Keep Fragment classes
-keep class * extends androidx.fragment.app.Fragment {
    public <init>(...);
}

# Keep Activity classes
-keep class * extends androidx.appcompat.app.AppCompatActivity {
    public <init>(...);
}

# Keep all classes with @Keep annotation
-keep @androidx.annotation.Keep class * { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep R class
-keep class com.example.rummypulse.R$* {
    *;
}

# Keep all public classes in your package
-keep public class com.example.rummypulse.** { *; }