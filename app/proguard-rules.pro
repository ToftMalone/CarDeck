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

# Sauvegardes sur un serveur SMB (smbj) : le bus d'événements (mbassador, méthodes @Handler) et la lecture des messages
# ASN.1 / SMB passent par la réflexion ; on garde ces bibliothèques entières (le code de BouncyCastle, lui, est appelé
# directement et reste réduit). Ce que Android n'a pas (Kerberos, expressions) n'est jamais utilisé : NTLM seulement.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.kerberos.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn com.hierynomus.**
