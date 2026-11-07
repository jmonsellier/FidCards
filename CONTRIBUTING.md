# Contribuer à FidCards

> **In English:** FidCards is a French Android app, so issues, pull requests and
> commit messages are written in French. Contributions in English are welcome too,
> and we will answer in English. The rules below boil down to this: the app never
> gets network access, it only declares the `CAMERA` permission, every UI string
> lives in `strings.xml` (in French), and a pull request is merged only once the
> CI is green.

Merci de vouloir améliorer FidCards. Les contributions les plus utiles, par ordre de
fréquence :

1. **une enseigne qui manque ou qui s'affiche mal** (couleur, logo périmé, logo d'un
   autre pays) ;
2. **un bug** : une carte illisible en caisse, un plantage, un écran mal affiché ;
3. **une amélioration** qui respecte les principes ci-dessous.

Avant un travail important, ouvrez d'abord une issue pour en discuter : une PR qui va
contre un des principes ne pourra pas être fusionnée, même bien écrite.

## Les principes, non négociables

- **Aucun accès réseau.** La permission `INTERNET` n'est pas déclarée et ne le sera
  jamais. Une bibliothèque qui l'ajoute par son manifeste est refusée (vérifiez le
  manifeste fusionné : `app/build/intermediates/merged_manifests/`).
- **Une seule permission : `CAMERA`**, avec un repli complet en saisie manuelle.
- **Ni pub, ni compte, ni traceur, ni statistiques d'usage.**
- **Toutes les chaînes de l'interface dans `app/src/main/res/values/strings.xml`**,
  en français. Aucun texte en dur dans le code.
- **Architecture simple** : injection manuelle via `AppContainer`, un ViewModel par
  écran, un repository unique. Pas de Hilt, pas d'abstraction « pour plus tard ».
- **Code-barres** : la génération reste en nearest-neighbour avec quiet zone. Le test
  round-trip JVM (`BarcodeRoundTripTest`) le verrouille : il doit toujours passer.

Le cahier des charges complet, avec les décisions déjà tranchées et leurs raisons, est
dans [`docs/PROJET.md`](docs/PROJET.md).

## Préparer son environnement

- **JDK 17**, ni plus ni moins (Robolectric et la toolchain Gradle en dépendent) ;
- SDK Android avec `compileSdk` 37.1 ;
- un émulateur pour les tests instrumentés (facultatif).

```bash
./gradlew assembleDebug        # APK debug, avec des cartes de démonstration
./gradlew compileDebugKotlin   # vérification rapide des types
./gradlew :app:lintDebug       # lint
./gradlew test                 # tests JVM, dont le round-trip code-barres
```

> ⚠ **Tests instrumentés : ciblez toujours l'émulateur.**
> `connectedDebugAndroidTest` s'exécute sur *tous* les appareils branchés, puis
> **désinstalle l'app**, ce qui efface les cartes du téléphone. Lancez-le ainsi :
>
> ```bash
> ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
> ```

## Proposer une modification

1. Forkez le dépôt et créez une branche depuis `main` (`fix/carte-illisible`,
   `enseigne/picard`…).
2. Faites une modification ciblée : une PR = un sujet.
3. Avant de pousser : `./gradlew compileDebugKotlin :app:lintDebug test` doit
   passer sans nouvelle alerte de lint.
4. Si vous touchez un écran, joignez une capture avant/après à la PR. Les captures
   JVM se génèrent sans émulateur :
   `./gradlew testDebugUnitTest --tests '*CapturesEcrans*' -Pcaptures`
   (résultat dans `app/build/captures/`).
5. Ouvrez la PR en remplissant le modèle. La CI (tests, lint, build release) doit être
   verte pour que la PR soit fusionnée.

Messages de commit : en français, au présent, sur ce qui change pour l'utilisateur
(« Carte plein écran : luminosité rétablie en quittant l'app »).

## Ajouter ou corriger une enseigne

Le plus simple : ouvrez une issue **« Enseigne à ajouter ou corriger »** avec un lien
vers le logo officiel et sa couleur. Pour faire la PR vous-même :

1. **Le logo** va dans `brands/logos/<id>.svg` (ou `.png`/`.webp` faute de vecteur),
   `<id>` étant le nom en minuscules sans espace ni accent (`leroymerlin`).
2. **L'entrée** va dans `brands/brands.json`, triée par `id` :

   ```json
   {
     "id": "leroymerlin",
     "name": "Leroy Merlin",
     "sector": "Bricolage / Jardinage",
     "website": "leroymerlin.fr",
     "logo": "leroymerlin.svg",
     "color": "#78BE20",
     "logoVariant": "officiel-plaque",
     "confidence": "haute",
     "sources": ["https://… (ce que la source atteste)"],
     "license": "unknown"
   }
   ```

3. **Les assets de l'app** : `python3 tools/optimize-logos.py` copie et optimise les
   logos vers `app/src/main/assets/brands/` (il faut `npx` pour svgo et `cwebp`), puis
   copiez `brands/brands.json` dans `app/src/main/assets/brands.json`.
4. `./gradlew test` : `BrandCatalogTest` vérifie que chaque enseigne a une couleur et
   que chaque logo déclaré est bien embarqué.

Ce qu'on attend d'un logo (détails dans `docs/PROJET.md` §6.3.1) :

- **le logo actuel, tel que l'enseigne l'affiche en France** : pas une version
  mondiale ni un ancien logo (McDonald's France est sur fond vert, pas rouge) ;
- **la couleur de fond de la vraie carte**, et le logo dans la variante prévue pour ce
  fond. Le blanc ne sert que si la marque n'a pas de fond coloré ;
- **des sources vérifiables** : charte ou kit presse, site officiel, Wikipédia FR,
  icône de l'app officielle. Pas de favicon recadré ni de logo trouvé sur un site
  tiers sans source.

Les enseignes dont la couleur ou le logo restent à confirmer sont listées dans
[`brands/MISSING.md`](brands/MISSING.md).

## Signaler une faille de sécurité

Pas d'issue publique : suivez [`SECURITY.md`](SECURITY.md).

## Licence de vos contributions

FidCards est sous licence [GPL-3.0](LICENSE). En proposant une contribution, vous
acceptez qu'elle soit distribuée sous cette même licence. Aucun accord de cession
(CLA) n'est demandé : vous restez l'auteur de votre code.

## Code de conduite

La participation au projet suit le [code de conduite](CODE_OF_CONDUCT.md).
