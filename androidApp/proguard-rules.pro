# Facturation, publicite et signaux d'age : les SDK livrent leurs propres regles.
-keep class com.android.vending.billing.** { *; }
# Le coeur partage est serialise par kotlinx.serialization : garder les
# serialiseurs generes.
-keepclassmembers class com.hooliapps.kilometres.core.** { *** Companion; }
-keepclasseswithmembers class com.hooliapps.kilometres.core.** { kotlinx.serialization.KSerializer serializer(...); }
