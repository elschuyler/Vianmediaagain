# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# Keep VianBoard components and services
-keep class com.example.ime.** { *; }
-keep class com.example.logger.** { *; }
-keep class com.android.inputmethod.latin.** { *; }
-keep class helium314.keyboard.latin.BinaryDictionary { *; }
-keep class helium314.keyboard.latin.DictionaryFacilitatorImpl { *; }
-keep class helium314.keyboard.latin.Suggest { *; }
-keep class helium314.keyboard.latin.SuggestedWords { *; }
-keep class helium314.keyboard.latin.WordComposer { *; }

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate
