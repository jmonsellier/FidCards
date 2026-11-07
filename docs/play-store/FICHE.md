# Fiche Play Store — FidCards

Textes et réponses à reporter dans la Play Console. Visuels dans ce dossier, rendus
depuis l'UI réelle par le test `VisuelsPlayStore` (`./gradlew testDebugUnitTest --tests
'*VisuelsPlayStore*' -Pcaptures`, sortie dans `app/build/captures/play/`). Les cinq
captures montrent chacune un écran différent (plein écran, carte, liste, ajout, réglages),
découpées dans un même **panorama** : seul le code-barres du fond passe d'une capture à
l'autre ; les téléverser dans l'ordre 1 → 5.

| Élément | Fichier | Format exigé |
|---|---|---|
| Icône de l'application | `icone-512.png` | 512 × 512 PNG |
| Image de présentation | `banniere-1024x500.png` | 1024 × 500 |
| Captures téléphone (panorama, dans l'ordre) | `capture-1-plein-ecran.png`, `capture-2-hors-ligne.png`, `capture-3-sans-pub.png`, `capture-4-ajout.png`, `capture-5-privee.png` | 1080 × 1920 (9:16) |

## Fiche principale (français)

**Nom** (30 car. max) : FidCards

**Description courte** (80 car. max) :

> Cartes de fidélité hors ligne, gratuites, sans pub ni compte.

**Description complète** :

> FidCards range toutes vos cartes de fidélité dans votre téléphone. Ouvrez l'app, touchez la carte, présentez-la en caisse : c'est tout.
>
> SANS PUB, SANS COMPTE
> Aucune publicité, aucun compte à créer, aucun abonnement. L'app est gratuite.
>
> 100 % HORS LIGNE
> FidCards n'utilise jamais Internet : vos cartes s'affichent même sans 4G ni Wi-Fi, au fond du magasin comme à l'étranger.
>
> RAPIDE EN CAISSE
> Vos cartes les plus utilisées remontent en tête. Un toucher affiche le code en grand, luminosité au maximum. Le numéro reste écrit sous le code si le lecteur refuse l'écran.
>
> AJOUT EN QUELQUES SECONDES
> Scannez le code-barres de la carte avec l'appareil photo, ou saisissez-le à la main. Plus de 240 enseignes françaises sont reconnues, avec leur couleur et leur logo. Photo du recto et du verso en option.
>
> TOUS LES CODES
> EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, Codabar, ITF, QR, Data Matrix, PDF417, Aztec.
>
> VOS DONNÉES RESTENT CHEZ VOUS
> Aucun traceur, aucune mesure d'audience, et pas même la permission d'accéder à Internet : vos cartes ne quittent jamais le téléphone. Sauvegarde automatique facultative dans le dossier de votre choix, sur le téléphone ou dans le cloud (Drive, Dropbox…), pour changer de téléphone sans rien perdre.
>
> Application libre et open source (GPL-3.0).
>
> Les marques et logos des enseignes appartiennent à leurs propriétaires respectifs. FidCards n'est affiliée à aucune enseigne.

## Catégorisation

- Type : Application · Catégorie : **Shopping** · Tags : cartes de fidélité, portefeuille
- Coordonnées : e-mail jmonsellier@gmail.com
- Politique de confidentialité : `https://github.com/jmonsellier/FidCards/blob/main/PRIVACY.md`

## Contenu de l'application

| Rubrique | Réponse |
|---|---|
| Accès aux applications | Toutes les fonctionnalités sont disponibles sans accès particulier |
| Annonces | Non, l'application ne contient pas d'annonces |
| Classification du contenu | Questionnaire : catégorie « Utilitaire, productivité, communication ou autre », réponse **Non** partout → PEGI 3 |
| Public cible | 18 ans et plus (évite les obligations « Familles », sans contrainte pour l'usage) |
| Application d'actualités | Non |
| Applis de santé, financières, gouvernementales | Non |
| Sécurité des données | voir ci-dessous |

### Sécurité des données (Data safety)

- Votre application collecte-t-elle ou partage-t-elle l'un des types de données utilisateur requis ? **Non.**
  - Justification : pas de permission `INTERNET`, aucun SDK tiers ; les données restent sur l'appareil (les données traitées uniquement sur l'appareil ne sont pas des « données collectées » au sens de Google).
- Les données sont-elles chiffrées en transit ? Sans objet (aucune transmission).
- Les utilisateurs peuvent-ils demander la suppression de leurs données ? Oui : suppression d'une carte dans l'app, désinstallation.

## Avant la première version de production

1. **Validation des développeurs Android** (Play Console → Validation des développeurs Android → Noms des packages) : enregistrer `com.monselio.fidcards` avec l'empreinte SHA-256 de la clé d'**upload** (`keytool -list -v -keystore fidcards-release.jks`), en plus de la clé Play App Signing.
2. Compte personnel créé après le 13 novembre 2023 : un **test fermé de 12 testeurs pendant 14 jours** est exigé avant l'accès à la production. Compte plus ancien ou organisation : exempté.
3. Téléverser `app/build/outputs/bundle/release/app-release.aab` (versionCode incrémenté à chaque envoi).
