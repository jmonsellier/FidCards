# FidCards — cahier des charges et plan d'implémentation

**Portée : application familiale, hors stores.** Usage personnel et partage avec le cercle
familial proche, via un dépôt GitHub privé. Aucune publication sur les stores pour le
moment ; le §15 recense ce qu'on garde ouvert pour ne pas fermer cette porte.

**Dernière mise à jour** : 2026-08-27 · **Statut** : phases 0 à 8 implémentées et
vérifiées sur AVD (Pixel 8, API 35). Restent, sur téléphone physique : le scan caméra
réel, le test en caisse (§10.2), et côté Jérôme : la création du keystore, le premier
tag et la release signée (voir README).

**Écarts au cadrage, constatés à l'implémentation** :

| Sujet | Prévu | Réel |
|---|---|---|
| Génération des codes | zxing-cpp | `com.google.zxing:core` : l'AAR zxing-cpp 3.1.1 n'expose **aucune** API d'écriture (vérifié dans classes.jar). Java pur, sans Play Services, round-trip testable en JVM |
| compileSdk | 36 | **37.1** : le BOM Compose 2026.08.00 (Compose 1.12) l'exige |
| Gradle / plugin Kotlin | — | Gradle 9.7.1 (AGP 9.3.2 refuse 9.3.1) ; AGP 9 intègre Kotlin : `org.jetbrains.kotlin.android` interdit |
| UPC-E scanné | tel quel | zxing-cpp rapporte le texte étendu EAN-13 : recompression vers la forme courte à 8 chiffres au scan (`UpcE`), verrouillée par test instrumenté |
| Poids des logos | < 1 Mo | 2,4 Mo d'assets pour 245 logos (SVG minifiés, rasters 768 px → WebP, `tools/optimize-logos.py`) ; les SVG texte se compressent encore dans l'APK — release complète : ~10 Mo |
| Plein écran (§5.2) | bascule paysage forcée pour les formats 1D | Demande utilisateur (2026-08-28) : écran carte en portrait aux couleurs de l'enseigne, code sur cartouche blanc ; un toucher agrandit le code (ouverture directe possible via un réglage), les formats linéaires pivotant de 90° dans l'image (rotation bitmap sans perte) — lu téléphone tourné : nom en haut, numéro sous le code, croix de fermeture en haut à droite |
| Sauvegarde (§8) | export manuel uniquement | Ajout utilisateur (2026-08-28) : **sauvegarde automatique** optionnelle — destination SAF choisie une fois (permission persistante), archive réécrite à chaque modification des cartes (différé 3 s, pas d'écriture au simple lancement). L'app reste sans réseau : une destination Drive/Dropbox/dossier synchronisé fait porter l'upload par l'app tierce |
| Groupes de cartes (§6.4) | l'autocomplétion signale le groupe | Demande utilisateur (2026-08-28) : simplifié **à la source** — les membres non-têtes sont retirés de brands.json (171 → 159 entrées : une seule entrée Carrefour, Super U, Auchan, Casino ; 170 après l'ajout des 11 enseignes des cartes réelles du foyer le 2026-08-28), la notion de groupe disparaît du code et de l'UI. Au passage, correction du piège org.json : `optString` rend la chaîne « null » pour un null JSON, ce qui créait un groupe fantôme réunissant les 155 enseignes sans groupe |
| Vignette d'accueil (§5.1) | logo + nom sous la vignette | Demande utilisateur (2026-08-28) : face de carte seule, sans bandeau de nom. **Révisé le 2026-11-07 : les vraies couleurs de l'enseigne, sans pastel.** Fond plein à la couleur officielle de la carte (`color` dans brands.json), logo officiel actuel dans la variante dessinée pour ce fond (`logoVariant` : logo blanc sur rouge, par exemple), identique en thème clair et sombre. La pastille des cartes sans logo garde le nom, à l'intérieur, sur la couleur officielle quand l'enseigne est au catalogue, sinon sur une teinte franche tirée du nom (§6.3.1) |

---

## 0. Contexte

Développeur solo (Jérôme, société Monselio, éditeur de MiluTV). FidCards est une
application Android de cartes de fidélité écrite pour son usage et celui de son cercle
familial proche — quatre à cinq personnes.

Chacun installe l'app et saisit ses propres cartes : **aucun partage de données entre
téléphones**. La distribution passe par un dépôt GitHub privé et Obtainium.

L'utilisateur ouvre l'app, voit ses cartes, en touche une, la montre en caisse. Rien
d'autre.

### 0.1 Ce que la portée personnelle change

Un premier cadrage visait F-Droid, Google Play, GitHub Releases et Obtainium. Cette
ambition est abandonnée, et avec elle une part considérable du travail :

| Abandonné | Pourquoi |
|---|---|
| Licences libres obligatoires pour les logos | Sans redistribution, la contrainte tombe |
| Pipeline d'approvisionnement des logos | ~20 cartes réelles : la main est plus rapide et plus propre qu'un script |
| Build reproductible vérifié en CI | Ne sert qu'à aligner la signature entre canaux |
| fastlane, métadonnées de stores | Aucun store |
| Politique de confidentialité, formulaire Data Safety | Aucun store |
| Compte Google Play, D-U-N-S, 12 testeurs | Aucun store |
| Liens de dons, GitHub Sponsors, Ko-fi | Pas de public |
| Traductions communautaires, Weblate | Cercle francophone |
| Justification face à Catima | Sans objet : « j'ai envie de le construire » suffit |

Le projet passe d'environ dix phases à huit, et la question juridique qui occupait le plus
de place dans les versions précédentes se réduit à une seule règle : **le dépôt reste
privé**. Diffuser des logos de marques dans un cercle familial n'est pas une redistribution
publique ; rendre le dépôt public ferait tout resurgir. C'est le seul geste à ne pas faire
par inadvertance.

## 1. Principe directeur

Le chemin critique est : **écran verrouillé → carte scannable en caisse**. Tout ce qui
allonge ce chemin est un défaut.

### 1.1 Contrainte physique à ne jamais oublier

Les lecteurs laser rouges de caisse ne lisent pas un écran de téléphone : un laser lit une
modulation réfléchie, qu'un écran émissif sous verre brillant ne fournit pas. Seuls les
imageurs 2D/CCD y parviennent. La part de caisses équipées en imageurs augmente, mais une
fraction ne scannera jamais l'écran.

C'est pourquoi la vue plein écran affiche **toujours le numéro en gros caractères sous le
code**. Ce n'est pas un ornement, c'est le plan B du cas d'usage principal.

**Limite connue** : sur certaines cartes, le numéro imprimé diffère du contenu encodé. Le
caissier tape le numéro imprimé ; afficher `barcodeValue` brut échoue alors. Résolu par la
colonne `displayValue`, optionnelle et vide dans la quasi-totalité des cas.

## 2. Ce que l'app ne fera pas

- pas de publicité, pas de télémétrie, pas d'analytics ;
- pas de compte, pas de synchronisation cloud, pas de serveur ;
- **aucun accès réseau** (la permission `INTERNET` n'est pas déclarée) ;
- pas de cartes de paiement, d'embarquement ni de billetterie ;
- pas de programme de points, de solde, de suivi de dépenses ;
- pas de géolocalisation, de notifications, de rappels ;
- pas de widget d'écran d'accueil ;
- pas de réordonnancement manuel par glisser-déposer.

## 3. Décisions actées

| Sujet | Décision |
|---|---|
| Nom | FidCards |
| Package / applicationId | `com.monselio.fidcards` |
| Plateforme | Android uniquement |
| Langage / UI | Kotlin + Jetpack Compose + Material 3 |
| Licence | GPL-3.0, `Copyright (C) 2026 Monselio` |
| Codes-barres | `zxing-cpp`, lecture et génération |
| Logos | Base couvrant les **248 enseignes** (171 à l'origine, 78 ajouts le 2026-11-07), embarquée dans l'APK |
| Constitution de la base | ~40 % par script, le reste à la main, une seule fois |
| Catalogue d'enseignes | `seed_brands_1.csv`, 171 entrées, noms et groupes de cartes |
| Dépôt | GitHub **privé** |
| Livraison | APK signé par tag + Obtainium sur les téléphones du foyer |
| Sauvegarde | Export / import ZIP |
| Langue | Français ; chaînes externalisées, anglais possible plus tard |
| Thème | Material You (API 31+), palette statique de repli en 26-30 |
| Tri | Score de récence pondérée (défaut), A→Z, Z→A |
| Base de connaissance | Coffre Obsidian `Jerome`, notes préfixées `FidCards — ` |

### 3.1 Pourquoi ZXing malgré l'abandon de F-Droid

Le choix de `zxing-cpp` avait été dicté par F-Droid, qui refuse ML Kit (brique propriétaire
liée aux Play Services). La contrainte tombe, la décision reste : ZXing évite la dépendance
aux Play Services, tient dans une seule bibliothèque, et suffit largement pour lire un code
net, immobile, à 15 cm. Conserver ce choix maintient aussi F-Droid possible plus tard, à
coût nul.

### 3.2 Base de connaissance

Le savoir durable va dans le coffre Obsidian **`Jerome`** existant, non dans un second
coffre : Obsidian ne sait pas lier deux coffres, et celui-ci contient déjà des notes
Android réutilisables.

Créer en phase 0 un `CLAUDE.md` racine important le `claude.md` du coffre, sans quoi le
protocole de consultation reste inactif sur ce projet.

## 4. Modèle de données

Table `cards` (Room / SQLite) :

| Champ | Type | Rôle |
|---|---|---|
| `id` | Long, auto | clé primaire |
| `name` | String | nom affiché |
| `barcodeValue` | String | contenu brut du code, tel que scanné |
| `displayValue` | String? | numéro imprimé s'il diffère de l'encodé (§1.1) |
| `barcodeFormat` | enum | EAN_13, EAN_8, UPC_A, UPC_E, CODE_128, CODE_39, ITF, CODABAR, QR_CODE, DATA_MATRIX, PDF_417, AZTEC |
| `brandId` | String? | clé dans `brands.json`, nul si enseigne absente du catalogue |
| `colorArgb` | Int? | couleur de repli, dérivée du nom si nulle |
| `logoPath` | String? | logo local, ajouté à la main |
| `frontImagePath` | String? | photo recto |
| `backImagePath` | String? | photo verso |
| `createdAt` | Long | horodatage de création |
| `lastUsedAt` | Long? | dernière ouverture en plein écran |
| `useCount` | Int | compteur brut, informatif |
| `score` | Real | score de récence pondérée, **base du tri par défaut** |

### 4.1 Le score de récence

`useCount` seul est monotone : une carte très utilisée il y a un an resterait en tête
indéfiniment, alors que le besoin est « les cartes que j'utilise en ce moment ».

Formule retenue, appliquée paresseusement à chaque ouverture plein écran :

```
score = score × 0,5 ^ (Δjours / 30) + 1
```

`Δjours` = écart depuis `lastUsedAt`. Décroissance exponentielle, demi-vie 30 jours. Tri
décroissant, égalités départagées par `lastUsedAt`. Aucun job périodique, aucune table
d'événements, une seule colonne.

**Anti-rebond** : consulter le verso puis revenir, ou rouvrir la même carte dans la minute,
ne compte pas comme deux usages.

Les réglages (thème, tri courant) vont dans DataStore Preferences, pas dans Room.

### 4.2 Ce que le catalogue apporte, hors base de données

`brands.json` est un asset en lecture seule, pas une table Room. Chaque entrée porte :

| Champ | Rôle |
|---|---|
| `id`, `name`, `sector`, `website` | identité de l'enseigne |
| `cardGroup` | regroupement : cinq enseignes Carrefour, une seule carte |
| `logo` | fichier dans `assets/brands/`, nul si aucun logo |
| `color` | fond officiel de la carte de l'enseigne, `#RRGGBB` (§6.3.1) |
| `logoVariant` | `officiel-inverse`, `officiel-tel-quel`, `recolore` ou `aucun` : comment le logo a été obtenu pour ce fond |
| `confidence`, `sources`, `note` | fiabilité de la couleur et du logo, URLs, sur quoi repose le « logo actuel » |
| `license` | traçabilité, indispensable si le projet devenait public |
| `groupLogoFrom` | l'enseigne dont ce membre hérite son logo |

Une carte référence une enseigne par `brandId` ; tout le reste vient du catalogue.

## 5. Écrans

### 5.1 Accueil

- grille de 2 colonnes ;
- vignette : logo si disponible, sinon photo recto, sinon pastille de couleur avec
  initiales. **La pastille sera fréquente** — elle doit être soignée, pas subie ;
- barre de recherche insensible à la casse et aux accents (« leclerc » trouve
  « E.Leclerc ») ;
- menu de tri : score de récence (défaut), A→Z, Z→A ;
- bouton flottant `+` ;
- état vide explicite.

### 5.2 Carte en plein écran

Le cœur de l'app. À l'ouverture, simultanément :

1. luminosité de la fenêtre à 100 % (`screenBrightness`, sans permission), restaurée à la
   sortie ;
2. écran maintenu allumé (`FLAG_KEEP_SCREEN_ON`) ;
3. **bascule en paysage pour les formats 1D uniquement** — un code linéaire y gagne le
   double de largeur ; QR, DataMatrix et Aztec sont carrés, la rotation n'y apporte rien ;
4. code-barres **régénéré** depuis `barcodeValue`, jamais la photo ;
5. numéro en gros sous le code — `displayValue` s'il est renseigné, sinon `barcodeValue` ;
6. `score` recalculé, `lastUsedAt` et `useCount` mis à jour, sous anti-rebond.

Accès au verso par un geste, si une photo verso existe.

### 5.3 Ajout / édition

- entrée principale : scan par la caméra, détection automatique du format ;
- entrée secondaire : saisie manuelle avec choix du format ;
- champ enseigne avec autocomplétion sur le catalogue, préremplissant nom et couleur ;
- **logo** : choisi depuis la galerie ou photographié, au moment de l'ajout ;
- photos recto/verso facultatives via le Photo Picker (donc sans permission de stockage) ;
- choix manuel de la couleur si l'enseigne est absente du catalogue.

### 5.4 Réglages

Thème, export, import, à propos.

## 6. Catalogue d'enseignes et base de logos

### 6.1 Pourquoi la couverture doit être large

L'app est partagée avec le foyer. On ne sait pas à l'avance quelles enseignes fréquentent la
mère ou la sœur de Jérôme, et on ne peut pas leur demander d'aller chercher un logo sur un
site web : elles resteraient sur des pastilles.

La base doit donc viser **les 171 enseignes du catalogue**, et non les seules cartes déjà
détenues. C'est le retour assumé à la « base complète » — justifié ici non par une ambition
de diffusion, mais par l'impossibilité de prévoir les besoins des autres utilisateurs.

### 6.2 Le catalogue et la base sont deux objets distincts

**Le catalogue** — `seed_brands_1.csv`, 171 enseignes avec secteur, site officiel et
regroupement de cartes — alimente l'autocomplétion. Converti une fois en `brands.json`, il
évite de retaper les noms et fournit les couleurs de repli.

**La base de logos** est l'ensemble des images correspondantes, embarquées dans l'APK.

Toute enseigne absente reste utilisable : l'utilisateur choisit une image depuis sa galerie,
ou reste sur la pastille — qui doit donc rester soignée.

### 6.3 Constitution : ce qui a été fait

**Sourcing clos le 2026-08-27 : 167 logos sur 171 enseignes (98 %).** Repris entièrement le
2026-11-07 (§6.3.1) : 245 logos sur 248 enseignes, chacun avec sa couleur officielle. Les
tableaux ci-dessous décrivent la passe d'août.

| Source | Logos |
|---|---|
| site | 71 |
| wikidata | 35 |
| manuel | 28 |
| fr-wikipedia | 22 |
| simple-icons | 11 |

| Licence | Logos |
|---|---|
| unknown | 96 |
| Public domain | 34 |
| non-libre | 22 |
| CC0-1.0 | 11 |
| CC BY-SA 4.0 | 1 |

Quatre sources automatiques ont été mesurées avant d'arrêter la stratégie :

| Méthode | Rendement | Obstacle |
|---|---|---|
| Simple Icons (CC0) | 15 % | Périmètre international ; rate le commerce de proximité français |
| Scraping `apple-touch-icon` / `og:image` | 27 % | 403 anti-bot, timeouts, balises absentes |
| Service favicon, ≥ 128 px | 41 % | 42 % des sites ne publient qu'un favicon < 64 px |
| Wikidata → Commons (P154) | — | Ne couvre que les logos libres |

**Deux enseignements, tous deux issus du tri humain et contraires à l'intuition :**

- **Un favicon n'est pas un logo.** Les sources carrées — favicon, `link[rel=icon]`,
  `apple-touch-icon` — renvoient un monogramme dessiné pour 16 px. Un « B » bleu pour
  Bricomarché n'apporte rien de plus que la pastille « B » affichée par défaut. Le **ratio
  d'aspect** est le meilleur discriminant : un logo de marque est horizontal, un monogramme
  est carré.
- **Wikipédia FR couvre ce que Commons refuse.** Les logos figuratifs non libres sont
  uploadés localement sur fr.wikipedia sous exception de courte citation — précisément
  parce que Commons les rejette. C'est ce qui a débloqué les enseignes de proximité.

**4 enseignes restent sans logo**, et c'est un choix assumé : Maxi Toys, La Panetière, Sophie Lebreuilly, L'Onglerie.
Elles afficheront la pastille à initiales.

### 6.3.1 Vraies couleurs (révision du 2026-11-07)

Décision de Jérôme : chaque carte s'affiche **comme sa vraie carte**. Fond plein à la couleur
officielle de l'enseigne (`color`), logo officiel **actuel** dans la variante prévue pour ce fond.
Plus de pastel ni de cartouche clair, et le même rendu en thème clair et sombre : une carte
Carrefour est bleue partout. Cela remplace le champ `background` (`light`/`dark`) de 2026-08.

- **Couleur** : charte ou kit presse, sinon fond du logo officiel, CSS du site, icône d'app.
  Le blanc n'est retenu que si la marque n'a aucun fond coloré (Aldi, Alain Afflelou, C&A…).
- **Logo** : `officiel-inverse` (variante blanche publiée), `officiel-tel-quel` (cartouche
  propre, comme Ikea ou Lidl), `recolore` (vecteur officiel passé en monochrome, seulement si
  la version inversée officielle est une silhouette unie), `aucun` (pastille à initiales).
- **Logo actuel** : vérifié contre l'en-tête actuel du site (web.archive.org quand le site
  bloque les robots), l'icône de l'app officielle sur l'App Store FR et Wikipédia FR. La
  comparaison avec les icônes d'app a révélé des logos périmés (Monoprix 2026, Bershka 2023,
  Kiabi, Primark 2024, Go Sport, Gibert, Léon, Vival 2025, La Halle) et des faux (favicon du
  Furet du Nord, Cora, O'Tacos). Une icône d'app reste un monogramme : elle date une identité,
  elle ne remplace pas le logo de la carte. Méfiance aussi sur les homonymes (Netto Allemagne,
  Royal Match…).
- Les enseignes dont la couleur ou le logo ne sont pas attestés sont listées dans
  `brands/MISSING.md`.

Dans l'app : `cardFaceColor` (couleur de la carte saisie > couleur du catalogue > teinte tirée
du nom), `contentColorOn` pour le texte posé dessus (blanc sous une luminance de 0,4).

### 6.3.2 Outillage, dans `tools/`

| Script | Rôle |
|---|---|
| `optimize-logos.py` | `brands/logos/` → `app/src/main/assets/brands/` : SVG minifiés (svgo), raster extrait des SVG qui n'emballent qu'une image, rasters ramenés à 768 px et convertis en WebP |

Les scripts de scraping de 2026-08 (favicons, Wikidata, Playwright, import manuel) ont été
retirés le 2026-11-07 : ils écrivaient l'ancien schéma (`background`, `validated`) et ne
vérifiaient ni la couleur ni l'actualité du logo. Le sourcing de 2026-11 s'est fait à la main,
enseigne par enseigne, avec relecture contre les icônes d'app. Le test JVM `BrandCatalogTest`
verrouille la cohérence du catalogue : identifiants et noms uniques, une couleur par enseigne,
chaque logo déclaré présent dans les assets.

Pour ajouter ou corriger une enseigne : voir `brands/MISSING.md`.

### 6.4 Regroupement de cartes

Le CSV porte une notion que le modèle initial ignorait : `card_group`. Cinq enseignes
Carrefour partagent une seule carte, cinq enseignes U également, quatre Casino, deux
Auchan — seize enseignes pour quatre cartes.

Conséquence : l'autocomplétion doit proposer l'enseigne **et** signaler le groupe, pour
éviter de créer cinq cartes Carrefour identiques.

## 7. Permissions et données

`CAMERA` uniquement, demandée au premier scan, avec repli complet en saisie manuelle.

Ni `INTERNET`, ni permission de stockage (Photo Picker et SAF s'en passent), ni
`WRITE_SETTINGS`.

**Auto Backup** : à désactiver explicitement (`dataExtractionRules`). Par défaut Android
sauvegarde les données vers Google Drive ; ne rien décider revient à laisser ce
comportement actif. Conséquence assumée : le ZIP devient l'unique chemin de migration d'un
téléphone à l'autre.

## 8. Sauvegarde

Export vers une archive ZIP choisie via le Storage Access Framework :

```
fidcards-backup-AAAA-MM-JJ.zip
├── cards.json      (schemaVersion + toutes les cartes)
└── images/         (logos et photos référencés)
```

Import avec choix fusionner / remplacer, refus d'une `schemaVersion` inconnue. Clé de
déduplication : `barcodeValue` + `barcodeFormat`.

C'est le seul chemin de migration entre téléphones, Auto Backup étant désactivé. À traiter
comme une fonctionnalité de premier plan, pas comme un extra — d'où sa place dans le
jalon 1.

## 9. Chaînes de caractères

Français uniquement, mais **toutes les chaînes dans `values/strings.xml`**, aucune en dur.
Coût nul aujourd'hui, et c'est ce qui rendrait une version anglaise possible plus tard sans
reprendre chaque écran.

## 10. Environnement de développement

| Élément | Constat |
|---|---|
| Android Studio | 2026.1 (build 261.26222.65) — JBR JDK 25 |
| JDK de la ligne de commande | OpenJDK 17.0.15 via `JAVA_HOME` |
| SDK platforms | android-31, 33, 34, 35, 36 |
| build-tools | jusqu'à 36.0.0 |
| AVD | tablettes + TV uniquement, hérités de MiluTV |
| Appareil physique | **aucun** détecté par `adb` |

### 10.1 JDK : ne rien changer

Vérifié sur le jar d'AGP 9.3.2 : ses classes portent la *major version* 61, soit du
bytecode **Java 17**. AGP 9.3.2 tourne donc sur JDK 17, plancher également de Gradle 9.

L'IDE compile avec son JBR 25, la ligne de commande avec le JDK 17 : sans conséquence dès
lors que le build déclare une *toolchain* Java, c'est elle qui fixe le bytecode produit.

Basculer `JAVA_HOME` sur le JDK 25 serait un risque net : chaque version de Gradle refuse de
démarrer au-delà d'un JDK maximum, et les projets de la machine s'étalent de Gradle 6.5 à
9.3.1. Seul MiluTV (Gradle 9.3.1) tolérerait le 25 ; les six autres casseraient, pour un
gain nul.

### 10.2 Appareil de test

Créer un AVD téléphone (Pixel, API 36) pour le développement courant. Mais l'émulateur **ne
validera jamais le jalon du plein écran** : ni le scan caméra réel, ni la luminosité, ni le
test décisif consistant à présenter l'écran à une vraie caisse. Un téléphone physique est
indispensable, et il n'y en a pas.

### 10.3 Amorçage du plein écran

Le plein écran se développe **avant** l'écran d'ajout : aucun moyen d'entrer une carte
n'existe encore. Prévoir un **seed de debug** avec les cartes réelles, numéros saisis depuis
le plastique. Trivial, mais bloquant si omis.

## 11. Versions figées

Relevées sur Google Maven et Maven Central le 2026-08-25, hors préversions.

| Dépendance | Version |
|---|---|
| Android Gradle Plugin | 9.3.2 |
| Kotlin | 2.4.10 |
| KSP | 2.3.11 |
| Compose BOM | 2026.08.00 |
| activity-compose | 1.13.0 |
| navigation-compose | 2.9.8 |
| lifecycle-viewmodel-compose | 2.11.0 |
| Room | 2.8.4 |
| DataStore Preferences | 1.2.1 |
| CameraX | 1.6.1 |
| core-ktx | 1.19.0 |
| zxing-cpp (android) | 3.1.1 |
| Coil 3 | 3.5.0 |

`minSdk` **26** (≈ 96,3 % du parc actif) · `compileSdk` 36 · `targetSdk` 36.

Toutes centralisées dans `gradle/libs.versions.toml`.

Architecture : injection manuelle via un `AppContainer`, un ViewModel par écran, un
repository unique, aucune abstraction spéculative.

## 12. Arborescence cible

```
FidCards/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/brands.json         catalogue d'enseignes (sans logos)
│       │   ├── kotlin/com/monselio/fidcards/
│       │   │   ├── FidCardsApplication.kt  AppContainer.kt  MainActivity.kt
│       │   │   ├── data/      Room : entity, dao, db, repository
│       │   │   ├── brands/    catalogue, autocomplétion, groupes de cartes
│       │   │   ├── barcode/   génération + analyse CameraX
│       │   │   ├── backup/    export / import ZIP
│       │   │   ├── settings/  DataStore
│       │   │   └── ui/        theme/ home/ detail/ edit/ scan/ settings/
│       │   └── res/values/strings.xml
│       ├── test/              tests JVM, dont le round-trip code-barres
│       └── androidTest/       tests instrumentés (DAO, migrations)
├── brands/                    CATALOGUE CONSTITUÉ, prêt à intégrer
│   ├── brands.json            248 enseignes : couleur, logo, variante, sources
│   ├── logos/                 245 fichiers
│   └── MISSING.md             les enseignes sans source fiable
├── docs/PROJET.md
├── tools/                     optimisation des logos, voir §6.3.2
├── gradle/libs.versions.toml
├── CLAUDE.md                  importe le claude.md du coffre Obsidian
├── LICENSE                    GPL-3.0
└── README.md
```

## 13. Phases

### Jalon 1 — utilisable au quotidien

| Phase | Contenu | Vérification |
|---|---|---|
| 0 | Squelette Gradle, thème Material You (+ repli 26-30), navigation, `CLAUDE.md`, LICENSE | l'app se lance sur l'AVD |
| 1 | Room : entité, DAO, base (`exportSchema = true`), repository, `AppContainer` | tests instrumentés du DAO |
| 2 | Accueil : grille, recherche sans accents, tri par score, état vide | navigation vers le détail |
| 3 | **Plein écran** : génération du code, luminosité, veille, paysage conditionnel | round-trip en CI ; **scan réel en magasin** |
| 4 | Ajout / édition + scan CameraX, permission et repli, catalogue d'enseignes | cycle scan → affichage → re-scan |
| 5 | Export / import ZIP | aller-retour sans perte |

À l'issue du jalon 1, l'app est utilisable au quotidien et les données peuvent en sortir.

### Jalon 2 — partageable avec la famille

| Phase | Contenu | Vérification |
|---|---|---|
| 6 | Intégration du catalogue : `brands/` vers `assets/`, optimisation du poids, sélecteur d'image par carte | une enseigne du catalogue affiche son logo dès l'installation |
| 7 | Photos recto/verso, réglages, à propos, raccourcis d'écran d'accueil | survit au redémarrage |
| 8 | Keystore, build de release signé, dépôt privé, premier tag, notice Obtainium | installation **et mise à jour** réussies sur un téléphone tiers |

La phase 8 est courte mais ne doit pas être bâclée : c'est elle qui décide si les mises à
jour parviendront au foyer sans intervention.

Chaque phase se termine par une compilation propre et un lint sans erreur.

## 14. Pièges techniques anticipés

**Génération du code-barres — le piège principal.** Un code agrandi par interpolation
bilinéaire devient flou et illisible par les scanners. Le générer à la taille exacte en
modules, puis l'agrandir en *nearest neighbour*, filtrage désactivé.

**Verrouiller ce piège par un test.** Un test JVM de *round-trip* — générer le bitmap puis
le **redécoder avec zxing-cpp**, y compris après mise à l'échelle — rend la régression
impossible, sans appareil. À écrire en phase 3.

**Quiet zone.** La marge blanche autour du code fait partie de la norme. Sans elle,
beaucoup de lecteurs échouent.

**Fond blanc en thème sombre.** Le code reste noir sur blanc même en thème sombre ; un code
inversé n'est pas lu par la plupart des lecteurs.

**Restauration de la luminosité.** `screenBrightness` doit revenir à
`BRIGHTNESS_OVERRIDE_NONE` à la sortie, y compris si l'activité est détruite brutalement.

**Rotation forcée.** `SCREEN_ORIENTATION_LANDSCAPE` s'impose même si la rotation est
verrouillée au niveau système. Comportement voulu, et réservé aux formats 1D.

**Checksum EAN-13.** On stocke et réaffiche `barcodeValue` tel que scanné, sans jamais le
« corriger ».

**Couleurs dynamiques Material You** : API 31+ seulement. Palette statique de repli
obligatoire pour 26-30.

**Le keystore est l'objet le plus irréversible du projet.** Le perdre interdit toute mise à
jour des APK déjà installés : chaque membre du foyer devrait désinstaller et **perdre ses
cartes**. À sauvegarder hors du dépôt et hors de la machine, dès la phase 8.

**Obtainium sur dépôt privé** exige un token d'accès GitHub configuré sur chaque téléphone.
Un token en lecture seule, généré une fois, suffit pour tout le foyer. À documenter dans le
README : c'est la seule étape technique demandée à la famille.

**Releases manuelles d'abord.** `./gradlew assembleRelease` puis `gh release create`
suffisent ; automatiser trois releases par an via GitHub Actions ne se rentabilise pas.

## 15. Ce qu'on garde ouvert pour une diffusion future

« En tout cas pour le moment » : ces décisions coûtent presque rien aujourd'hui et
éviteraient une reprise complète si le projet était publié un jour.

| Décision | Coût aujourd'hui | Ce qu'elle préserve |
|---|---|---|
| Dépôt privé | nul | Tant qu'il l'est, les logos restent dans le cercle familial |
| Licence GPL-3.0 et `com.monselio.fidcards` | nul | Identité et licence définitives dès le premier APK |
| `zxing-cpp` plutôt que ML Kit | nul | F-Droid reste possible : aucune dépendance propriétaire |
| Chaînes dans `strings.xml` | nul | Version anglaise sans reprendre les écrans |
| `source` et `license` par logo dans les métadonnées | négligeable | Purge des logos non redistribuables en une passe |
| Format d'export versionné | déjà prévu | Migration et interopérabilité |

Le point le plus important est l'avant-dernier : les logos récupérés pour un usage
personnel ne sont pas redistribuables. Noter leur provenance au moment de l'ajout coûte un
champ ; ne pas le faire obligerait à tout réauditer avant publication.

## 16. Décisions restant à prendre

1. **Appareil physique de test** — conditionne le jalon du plein écran (§10.2).
2. **Rien d'autre.** Le périmètre de la base est tranché : les 248 enseignes du catalogue (élargi le 2026-11-07 aux grandes enseignes manquantes, Gifi en tête, et aux enseignes des classements de fidélité ; les enseignes fermées ou sans programme de fidélité ont été écartées).
