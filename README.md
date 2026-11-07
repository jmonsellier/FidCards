# FidCards

Application Android libre de cartes de fidélité : sans pub, sans compte, et qui marche
hors ligne. Aucune donnée ne quitte le téléphone : aucun serveur, aucun accès réseau.

Le chemin critique : **écran verrouillé → carte scannable en caisse.**

## Principes

- Kotlin + Jetpack Compose + Material 3 (Material You, repli statique en API 26-30) ;
- lecture des codes par `zxing-cpp` (caméra), génération par `zxing core`
  (nearest neighbour + quiet zone, verrouillés par un test round-trip JVM) ;
- base de logos de 243 enseignes aux vraies couleurs embarquée dans l'APK, catalogue de 246 enseignes
  pour l'autocomplétion ;
- seule permission : `CAMERA` (repli complet en saisie manuelle) ;
- sauvegarde par export/import ZIP — c'est **l'unique** chemin de migration entre
  téléphones, Android Auto Backup étant désactivé — avec, en option, une
  **sauvegarde automatique** qui réécrit l'archive à chaque modification vers un
  emplacement choisi une fois (dossier local, Drive/Dropbox via leur sélecteur,
  dossier Syncthing/Nextcloud…) : le cloud sans jamais donner le réseau à l'app ;
- raccourcis d'écran d'accueil vers les cartes les plus utilisées.

## Construire

Prérequis : JDK 17, SDK Android (compileSdk 37.1).

```bash
./gradlew assembleDebug              # APK debug (avec cartes de démonstration)
./gradlew test                       # tests JVM, dont le round-trip code-barres
./gradlew connectedDebugAndroidTest  # tests instrumentés (émulateur requis)
./gradlew assembleRelease            # release minifiée R8 (~10 Mo)
```

## Publier une release (Jérôme uniquement)

1. **Une seule fois** — créer le keystore et le fichier de signature :

   ```bash
   keytool -genkeypair -v -keystore fidcards-release.jks -alias fidcards \
     -keyalg RSA -keysize 4096 -validity 36500
   cp keystore.properties.example keystore.properties   # puis remplir
   ```

   ⚠ **Le keystore est l'objet le plus irréversible du projet.** Le perdre interdit
   toute mise à jour des APK installés : chaque membre du foyer devrait désinstaller
   et perdre ses cartes. À sauvegarder immédiatement hors du dépôt **et** hors de la
   machine. Sans `keystore.properties`, le build release est signé en debug —
   installable pour tester, à ne jamais distribuer.

2. À chaque version : incrémenter `versionCode`/`versionName` dans
   `app/build.gradle.kts`, puis :

   ```bash
   ./gradlew assembleRelease bundleRelease
   git tag v1.0.0 && git push --tags
   gh release create v1.0.0 app/build/outputs/apk/release/app-release.apk \
     --title "FidCards 1.0.0" --notes "…"
   ```

### Google Play

FidCards est publiée en production sur Google Play. Textes de la fiche, réponses
de la Play Console (Data Safety, classification) et visuels : `docs/play-store/`.
Politique de confidentialité : [PRIVACY.md](PRIVACY.md).

1. **Play App Signing** : la clé locale est une simple clé d'upload, remplaçable
   auprès de Google en cas de perte ;
2. chaque version : `./gradlew bundleRelease`, puis téléverser
   `app/build/outputs/bundle/release/app-release.aab` (versionCode incrémenté).

**Un seul canal par téléphone** : l'APK GitHub (clé Monselio) et le build Play (clé
Google) ont des signatures différentes — passer de l'un à l'autre impose une
désinstallation, donc un export/import ZIP.

## Installation hors Play Store (Obtainium)

1. installer [Obtainium](https://github.com/ImranR98/Obtainium) ;
2. « Ajouter une application » avec l'URL de ce dépôt ;
3. Obtainium installe l'APK de la dernière release et proposera chaque mise à jour.

## Licence

GPL-3.0 — Copyright (C) 2026 Monselio. Voir [LICENSE](LICENSE).

Les logos d'enseignes contenus dans `brands/` et `app/src/main/assets/brands/`
appartiennent à leurs propriétaires respectifs et ne sont pas couverts par la
GPL-3.0. FidCards n'est affiliée à aucune enseigne. Leur provenance est tracée
dans `brands/brands.json`.
