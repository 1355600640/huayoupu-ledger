# WebView invokes these methods by their JavaScript names. Keep only the annotated API,
# rather than disabling obfuscation for the entire application or UI package.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Enum names form persisted SQLite/backup values and the WebView JSON protocol.
# Keep application enum constants while allowing unrelated implementation classes to shrink.
-keepclassmembers enum net.ankio.ledger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static <fields>;
}

# Retain line mappings for private crash diagnosis; mapping.txt stays outside the APK.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optional V6 JNI entry points are looked up by native symbol/class names.
-keep class ai.onnxruntime.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
