# CLAUDE.md

## Règles

- **Lire `docs/PROJET.md` avant tout travail de fond** : cahier des charges, décisions
  actées, pièges techniques anticipés. Ne pas rediscuter une décision déjà tranchée sans
  élément nouveau.
- **Dépôt public et open source (GPL-3.0), app en production sur Google Play** (décision de
  Jérôme, 2026-11-07, docs/PROJET.md §0.2). Les logos de marques sont embarqués et diffusés :
  Jérôme en assume le risque, le sujet est clos. Le dépôt étant public, **aucun secret** n'y
  entre jamais (keystore, mots de passe, `keystore.properties`).
- **Aucun accès réseau dans l'app** : la permission `INTERNET` n'est pas déclarée et ne
  doit jamais l'être. Seule permission : `CAMERA`.
- **Toutes les chaînes UI dans `res/values/strings.xml`**, en français, aucune en dur.
- **Vérification obligatoire** avant de déclarer une tâche terminée :
  `./gradlew compileDebugKotlin` (type-check) puis `./gradlew :app:lintDebug` (lint).
  Les tests JVM : `./gradlew test`. Les tests instrumentés exigent un émulateur :
  `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`.
- **⚠ JAMAIS de `connectedDebugAndroidTest` sans `ANDROID_SERIAL=emulator-5554`.**
  La tâche s'exécute sur TOUS les appareils adb puis DÉSINSTALLE l'app : lancée
  avec le téléphone de Jérôme branché, elle efface ses cartes (c'est arrivé le
  2026-08-28). Même prudence pour toute commande adb : toujours cibler avec `-s`.
- **JDK : ne rien changer.** `JAVA_HOME` pointe sur le JDK 17, la toolchain Gradle fixe le
  bytecode. Ne pas basculer sur un JDK plus récent (docs/PROJET.md §10.1).
- **Code-barres** : lecture via `zxing-cpp` (caméra), génération via `com.google.zxing:core`
  (l'AAR zxing-cpp 3.1.1 n'expose aucune API d'écriture — vérifié). Le rendu doit rester en
  nearest-neighbour avec quiet zone, verrouillé par le test round-trip JVM.
- **Architecture** : injection manuelle via `AppContainer`, un ViewModel par écran, un
  repository unique. Pas de Hilt, pas d'abstraction spéculative.

## Commandes

```bash
./gradlew assembleDebug            # APK debug
./gradlew compileDebugKotlin       # type-check rapide
./gradlew :app:lintDebug           # lint
./gradlew test                     # tests JVM (dont round-trip code-barres)
./gradlew connectedDebugAndroidTest  # tests instrumentés (émulateur requis)
```

## Coffre Obsidian (second cerveau)

Règles d'usage du coffre chargées via import. Autorité unique : `/Users/jerome/Obsidian/claude.md`.

@/Users/jerome/Obsidian/claude.md
