# Enseignes sans logo — 4 sur 171

Sourcing clos le 2026-08-27. Ces enseignes restent volontairement en **pastille à initiales** :
elles ne publient pas de logo exploitable, et la pastille fait aussi bien qu_un logo mal cadré
pour un commerce de proximité.

Pour en ajouter un plus tard :

    node tools/import-logos-manuels.mjs "id=https://…/logo.svg"
    node tools/import-logos-manuels.mjs "id=/chemin/vers/logo.png"

Le script reconnaît le format par les octets de tête, refuse les pages HTML déguisées en image,
et ajoute le viewBox manquant aux SVG.

| id | Enseigne | Secteur | Site |
|---|---|---|---|
| `longlerie` | L'Onglerie | Institut / Onglerie | https://onglerie.com |
| `lapanetiere` | La Panetière | Boulangerie | — |
| `maxitoys` | Maxi Toys | Jouets | https://maxitoys.fr |
| `sophielebreuilly` | Sophie Lebreuilly | Boulangerie | — |
