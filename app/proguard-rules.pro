# MyFit Tracker — R8 rules. Libraries ship their own consumer rules; these cover reflection/JNI that R8 can't see.

# Our own code is small: keep it whole (Room, WorkManager workers, Glance receivers, JSON field names, Compose).
# Shrinking still removes unused library code, which is where the size is.
-keep class com.myfit.tracker.** { *; }

# Native (JNI) engines: Java classes are looked up from C++ by name
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.google.ai.edge.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# Firebase / Play services / ML Kit use reflection for some components
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# commons-compress / zxing optional deps not on Android
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.**
-dontwarn org.tukaani.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.**
