# FidCards

Application Android de cartes de fidélité, à usage familial. Chacun installe l'app et
saisit ses propres cartes ; aucune donnée ne circule entre téléphones, aucun serveur,
aucun accès réseau.

Le chemin critique : **écran verrouillé → carte scannable en caisse.**

## Principes

- Kotlin + Jetpack Compose + Material 3 (Material You, repli statique en API 26-30) ;
- lecture des codes par `zxing-cpp` (caméra), génération par `zxing core`
  (nearest neighbour + quiet zone, verrouillés par un test round-trip JVM) ;
- base de logos de 245 enseignes aux vraies couleurs embarquée dans l'APK, catalogue de 248 enseignes
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

### Canal retenu : tests internes du Play Store

Plus simple qu'Obtainium pour le foyer : installation et mises à jour par le
Play Store, sans token. Dans la [Play Console](https://play.google.com/console) :

1. Créer l'app (FidCards, application, gratuite) ; déclarations triviales —
   aucune donnée collectée, aucune permission sensible (`CAMERA` seule) ;
2. accepter **Play App Signing** : la clé locale devient une simple clé
   d'upload, remplaçable auprès de Google en cas de perte ;
3. Tests internes → créer une liste de testeurs avec les adresses Gmail du
   foyer → téléverser `app/build/outputs/bundle/release/app-release.aab` ;
4. partager le lien d'activation aux testeurs ; chaque nouvelle version est
   un simple upload d'AAB sur la même piste (versionCode incrémenté).

La piste interne reste limitée à des testeurs nommés : la logique « cercle
familial, pas de redistribution publique » des logos est préservée. **Un seul
canal par téléphone** : l'APK GitHub (clé Monselio) et le build Play (clé
Google) ont des signatures différentes — passer de l'un à l'autre impose une
désinstallation, donc un export/import ZIP.

## Installation dans le foyer (Obtainium)

Le dépôt est **privé** — il doit le rester, les logos embarqués n'étant pas
redistribuables publiquement. Sur chaque téléphone :

1. installer [Obtainium](https://github.com/ImranR98/Obtainium) ;
2. dans Obtainium : Réglages → Source GitHub → renseigner le *token* GitHub en
   lecture seule fourni par Jérôme (un seul token, généré une fois sur
   github.com/settings/tokens avec la portée `repo` en lecture, suffit pour tout
   le foyer) ;
3. « Ajouter une application » avec l'URL de ce dépôt ;
4. Obtainium installe l'APK de la dernière release et proposera chaque mise à jour.

C'est la seule étape technique demandée à la famille.

## Licence

GPL-3.0 — Copyright (C) 2026 Monselio. Voir [LICENSE](LICENSE).

Les logos d'enseignes contenus dans `brands/` et `app/src/main/assets/brands/`
appartiennent à leurs propriétaires respectifs ; ils sont embarqués pour un usage
strictement personnel et familial, et ne doivent pas être redistribués publiquement.
Leur provenance et licence sont tracées dans `brands/brands.json`.
