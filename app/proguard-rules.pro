# CarDeck : règles R8 de la version publiée (réduction, optimisation et obfuscation du code).

# Traces de plantage décodables avec le fichier de correspondance (mapping.txt, gardé par la CI à chaque release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Énumérations de CarDeck : leurs noms sont enregistrés (base de données, préférences, sauvegardes, découvertes de
# l'explorateur) et relus par nom. Ils ne doivent jamais changer d'une version à l'autre.
-keepclassmembers enum com.cardeck.app.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# osmdroid (cartes) : sources de tuiles et fournisseurs chargés en partie par réflexion.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
