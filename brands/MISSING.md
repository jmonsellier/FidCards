# Enseignes sans source fiable : 23 sur 246

Révision du 2026-11-07 (vraies couleurs, logo officiel actuel). Chaque enseigne du catalogue
porte son fond officiel (`color`) et le logo dessiné pour ce fond (`logoVariant`). Celles-ci
n'ont qu'une couleur ou un logo **non attestés** par une source officielle, ou aucun logo
exploitable (pastille à initiales sur la couleur retenue). Le détail est dans `note` et
`sources` de brands.json.

243 enseignes sur 246 ont un logo.

Pour corriger une enseigne : déposer le logo dans `brands/logos/<id>.svg|png`, renseigner
`color`, `logo`, `logoVariant`, `confidence`, `sources` dans brands/brands.json, puis :

    cp brands/brands.json app/src/main/assets/brands.json && tools/optimize-logos.py

| id | Enseigne | Fond | Logo | Raison |
|---|---|---|---|---|
| `alinea` | Alinéa | `#404727` | recolore | Site alinea.com bloque (403), Commons injoignable (429). Seule couleur de marque connue = le vert olive du logo (#404727) ; logo recolore en blanc par inference, version inversee officielle non verifiee. |
| `aromazone` | Aroma-Zone | `#FFFFFF` | officiel-tel-quel | Site protégé par Cloudflare (403) : seul le PNG Commons (3780 px, gris foncé sur transparent) est accessible, date du logo non vérifiable ; la marque n'a pas de fond coloré attesté, blanc par défaut. |
| `boulangerielouise` | Boulangerie Louise | `#000000` | officiel-tel-quel | Site boulangerie-louise.com injoignable (502/echec). Cartouche officiel noir + creme ; fond noir deduit du logo, non source. |
| `coccimarket` | Coccimarket | `#76746E` | officiel-tel-quel | Seul fichier trouvé : PNG 500x166 (sous 600 px) dont le fond gris #76746E fait partie de l'image ; logo orange/blanc. Date inconnue, site coccimarket.fr injoignable. À remplacer si une meilleure source apparaît. |
| `courir` | Courir | `#000000` | recolore | Aucune source de fond : identité noir/orange, fond noir, texte blanc, C orange conservé. |
| `damart` | Damart | `#767561` | recolore | bg = gris-taupe exact du logo officiel actuel du site (#767561 au lieu de #707060 approximatif) ; texte passé en blanc, éclair rouge conservé. Aucune couleur de fond attestée par carte ou app : faible. |
| `dessange` | Dessange | `#161615` | recolore | Marque noir et blanc, aucune source de fond : fond noir, logo blanc. |
| `eram` | Eram | `#14141C` | recolore | Aucune source de fond : noir du logo, texte blanc, accent jaune #FFF200 conservé. |
| `espaceculturelleclerc` | Espace Culturel Leclerc | `#0078A8` | officiel-tel-quel | Aucune source de fond : bleu du logo ; le logo est dans son cartouche blanc. Vectorisé (potrace) depuis le PNG/WebP 768 px du dépôt ; le site e.leclerc est une SPA sans logo accessible ; l'ancien logo carré de Commons est obsolète. |
| `furetdunord` | Furet du Nord | `#E30613` | aucun | La patte était le favicon, pas le logo. Le logo actuel du site est le mot 'Furet du nord' blanc sur rouge, mais seul un PNG de 115 px est disponible (pas de SVG, rien sur Commons/Play, Wikimedia en 429) ; la patte en place est le favicon (monogramme), pas le logo complet. Une fois un fichier >= 600 px trouvé, mettre le mot blanc sur #E30613. Source brute : audit/dl/furetdunord/logo.png : pastille à initiales en attendant un fichier exploitable. |
| `grandoptical` | GrandOptical | `#002F72` | officiel-inverse | Logo blanc officiel du site ; bleu #002F72 = mask-icon du gabarit GrandVision, fond reel du header non confirme. |
| `habitat` | Habitat | `#000000` | recolore | Marque noir et blanc d'apres son logo noir ; le site declare aussi un TileColor rouge #FF0033 non retenu. Logo noir du depot passe en blanc. |
| `longlerie` | L'Onglerie | `#FFFFFF` | aucun | Le mauve #A56F90 n'est pas un fond de marque attesté ; le logo officiel (picto 4 ongles + L'Onglerie, rose poudré) est posé sur fond blanc sur le site. Seul fichier : PNG 200 px (< 600 px), pas de vecteur, pas d'app (App Store / Play Store sans résultat). Pastille : bg mauve #A56F90 non sourcé -> blanc (fond du logo sur le site) ; logo : seul un PNG 200x112 existe (téléchargé dans audit/dl/a6/ong/logo.png), pas de vecteur ni raster >= 600 px, donc pastille à initiales |
| `lacuregourmande` | La Cure Gourmande | `#FDB934` | officiel-tel-quel | Or #FDB934 = bandeau du logo officiel (curegourmande.fr) ; aucune source ne donne un fond de carte ; la bannière se fond dans l'or, texte rouge et script bleu restent lisibles |
| `match` | Match | `#FFFFFF` | officiel-tel-quel | Logo officiel Supermarché Match (Commons, PNG 4149 px réduit) multicolore vert/rouge pensé pour fond clair : aucune couleur de fond attestée -> blanc |
| `maxizoo` | Maxi Zoo | `#196428` | recolore | Vert #196428 = mask-icon et fonds du site maxizoo.fr ; logo = raster du catalogue (512 px) converti en silhouette blanche et agrandi x2 ; « actuel » non vérifié : aucun SVG officiel trouvé, logo daté non confirmé |
| `natalys` | Natalys | `#000000` | recolore | Marque noir et blanc (CSS --textcolor #000000, logo noir) : fond noir, logo script en blanc ; aucune source ne donne un fond de marque explicite / actuel : logo Natalys Paris du site (catalogue), non re-daté |
| `paul` | Paul | `#FEF7EE` | officiel-tel-quel | Logo actuel « PAUL depuis 1889 » du header de paul.fr (archive 2026-01), sombre sur le crème #FEF7EE du site ; raster natif 340 px seulement, aucun vecteur trouvé. |
| `pimkie` | Pimkie | `#000000` | recolore | Logo monochrome noir (icône P) : fond noir, logo blanc ; fond de marque non attesté par une source / actuel : logo du site pimkie.fr (catalogue), non re-daté |
| `pointp` | Point.P | `#E8500E` | aucun | Orange du cartouche du logo du catalogue (raster 164 px) ; pointp.fr bloqué (403), aucun logo vectoriel actuel trouvé ; couleur non attestée par une source |
| `snipes` | Snipes | `#FFFFFF` | officiel-tel-quel | Logo Commons 'Snipes logo.svg' (gris #494B52 + carré orange #EC6408) tel quel sur blanc : le site snipes.com n'a pas pu être lu (JS) pour confirmer que c'est le logo actuel ni la couleur de fond de la carte ; blanc par défaut, aucune couleur de fond attestée. |
| `speedy` | Speedy | `#3B7AB7` | officiel-tel-quel | Bleu Speedy ~#3B7AB7 : theme-color du site + fond du logo « on blue » (#2E79BC, Wikimedia), préféré au cyan #00A8FF du CSS (couleur d'accent). Médaillon actuel du site, MAIS raster natif 172 px (aucun SVG sur speedy.fr, .be parqué) : agrandi, à remplacer. |
| `sushishop` | Sushi Shop | `#111111` | officiel-tel-quel | Fond #111111 = header du site sushishop.fr ; logo taupe #BBAD95 (roundel + SUSHISHOP) de fr.wikipedia 'Sushishop.png', seul fichier trouvé : raster de 151 px, sous le seuil de 600 px et date non vérifiée (le header actuel affiche le même roundel taupe sur fond sombre). A remplacer si un vecteur est obtenu. |
