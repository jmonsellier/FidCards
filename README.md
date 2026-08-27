# FidCards

Application Android de cartes de fidélité, à usage familial. Chacun installe l'app et
saisit ses propres cartes ; aucune donnée ne circule entre téléphones, aucun serveur,
aucun accès réseau.

Le chemin critique : **écran verrouillé → carte scannable en caisse.**

## Principes

- Kotlin + Jetpack Compose + Material 3 (Material You, repli statique en API 26-30) ;
- lecture des codes par `zxing-cpp`, génération par `zxing core` ;
- base de logos de 167 enseignes embarquée dans l'APK ;
- seule permission : `CAMERA` (repli complet en saisie manuelle) ;
- sauvegarde par export/import ZIP — c'est **l'unique** chemin de migration entre
  téléphones, Android Auto Backup étant désactivé.

## Construire

Prérequis : JDK 17, SDK Android (compileSdk 36).

```bash
./gradlew assembleDebug
```

## Installation dans le foyer (Obtainium)

Le dépôt est **privé** — il doit le rester, les logos embarqués n'étant pas
redistribuables publiquement. Sur chaque téléphone :

1. installer [Obtainium](https://github.com/ImranR98/Obtainium) ;
2. ajouter l'app avec l'URL de ce dépôt GitHub ;
3. renseigner le *token* GitHub en lecture seule (fourni par Jérôme) dans les réglages de
   la source GitHub d'Obtainium ;
4. Obtainium installera l'APK de la dernière release et proposera les mises à jour.

C'est la seule étape technique demandée à la famille.

## Licence

GPL-3.0 — Copyright (C) 2026 Monselio. Voir [LICENSE](LICENSE).

Les logos d'enseignes contenus dans `brands/` appartiennent à leurs propriétaires
respectifs ; ils sont embarqués pour un usage strictement personnel et familial, et ne
doivent pas être redistribués publiquement.
