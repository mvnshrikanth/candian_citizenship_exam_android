# kotlinx.serialization generates a companion serializer per @Serializable class.
# R8 strips them otherwise and every decode silently returns defaults.
-keepclassmembers class com.mvnsh.citizenship.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.mvnsh.citizenship.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
