# Reboot des illustrations — direction Braise Pop

Statut : cahier des charges de production, à confronter aux références téléchargées et aux images produites. Ce document ne vaut pas approbation des visuels. Les décisions de correction sont consignées dans les revues de l'agent critique.

## Intention

Chaque fond devient un petit monde illustré qui a une géographie, une matière et une source de lumière lisibles. L'identité vient des grandes silhouettes, des rapports chaud/froid et de l'emboîtement de trois à cinq plans ; elle ne dépend ni d'une pluie de détails minuscules ni d'un filtre coloré appliqué à une scène plate. On repart des références de lieu ou de phénomène, sans tracer leurs pixels et sans reprendre les compositions des versions précédentes.

L'interface existante conserve ses cartes opaques, marches pleines, encre prune #2A1420, typographies Unbounded/Manrope, contraste et mouvement réduit. Pour les illustrations, la demande récente de lumière et d'ombres autorise une lumière picturale colorée et des ombres portées lisibles. Elle ne justifie pas de remplacer les composants Braise Pop par du verre ou d'ajouter un flou UI coûteux.

## Grammaire commune

- Cartoon contemporain chaleureux : volumes légèrement arrondis, silhouettes volontaires, proportions plausibles simplifiées, plans de couleur francs et arêtes de lumière sélectives. Aspect peint propre, sans grain sale ni faux rendu photographique.
- Couleur locale vive, ombres froides prune/bleu, lumière chaude pêche/or/braise. Deux couleurs dominantes et une couleur d'accent, adaptées au sujet ; pas d'orange forcé sur toutes les scènes.
- Une source dominante cohérente. Chaque volume principal présente au moins une face éclairée et une face ombrée. Contacts au sol, occlusion aux intersections et ombres portées rendent les objets posés.
- Au moins trois plans : avant-plan découpé et sombre/chromatique, sujet de milieu de plan, lointain plus clair et moins contrasté. Les fonds abstraits remplacent la perspective par superposition et occlusion.
- Texture à l'échelle de l'objet : grands coups de couleur pour herbe/roche/bois, reflets groupés pour eau, joints simplifiés pour pierre. Aucun bruit uniformément réparti.
- Le point d'intérêt reste reconnaissable dans une vignette de 96×160. Les détails de 1–2 pixels ne portent jamais l'identité du fond.
- Aucun texte généré, logo, watermark, pseudo-interface ni personnes identifiables. Les labels SCO et le score historique OL 4 / SRFC 0 sont des éléments natifs séparés si conservés dans les fonds secrets.

## Gabarit et plans

Master vertical 1024×1536, plein cadre sans bord, composition extensible au recadrage 9:19,5. Zone centrale x=20–80 %, y=10–57 % plus calme pour les cartes et titres ; des masses latérales et la partie basse portent la narration. Les sujets ne doivent pas tous être repoussés dans les dix derniers pourcents : leur silhouette débute vers y=45–65 % et reste perceptible en écran réel.

Pour les scènes `outdoor`, produire un décor transparent au-dessus de la ligne d'horizon : ciel réellement alpha, aucune grande masse noire remplaçant la transparence, aucun soleil/lune/coucher orange peint. Nuages, astre, teinte du ciel et précipitations restent animés et pilotés par l'heure/météo natives. Les bâtiments, végétaux et reliefs conservent des ombres légères de modelé et une lumière neutre chaude de fin de matinée ; le moteur module la température et la luminance des plans sans transformer le ciel en rectangle teinté. Si une unique image aplatie ne permet pas une nuit crédible, produire des plans séparés ou une variante nocturne avant acceptation.

Pour `window`, le cadre et la pièce sont fixes ; l'extérieur et les gouttes restent compatibles avec l'environnement natif. Pour `artistic`, une scène complète avec lumière propre est permise ; ne pas y injecter une météo terrestre arbitraire. Les effets animés restent des couches natives peu nombreuses ; le fond immobile doit déjà être réussi.

Les assets de chaque fond restent dans `assets/backgrounds/<key>/`, avec `art/` pour les images produites, `layers/` pour composition/animations et propriétés locales. Les photographies de recherche restent hors des assets de production.

## Référence et transformation

Une fiche associe 1 à 10 références internet vérifiables au fond. La référence apporte des faits : forme d'un toit, couleurs de tuffeau, structure d'une dune, anatomie d'une méduse. Elle ne donne pas licence de copier une illustration. Le prompt décrit la transformation cartoon et les éléments retenus. Une référence téléchargée mais non inspectée reste marquée comme telle.

## Contrôle du critique

Chaque critère est évalué sur le master, sur le rendu intégré téléphone et sur la miniature :

1. Identité : les éléments distinctifs définis par la fiche sont présents et les références géographiques respectées.
2. Profondeur : au moins trois plans se distinguent immédiatement ; perspective et occlusions ne se contredisent pas.
3. Lumière : source lisible, volumes modelés, ombres de contact et portées cohérentes ; aucune silhouette principale entièrement bouchée.
4. Couleur/matière : couleurs franches mais hiérarchisées, textures propres et spécifiques ; pas de grisaille globale ni de plastique générique.
5. Composition : point focal lisible, zone des cartes calme, pas de recadrage détruisant le sujet.
6. Cohérence de collection : même degré de finition que les meilleures scènes, sans recycler le même arbre/montagne/nuage partout.
7. Comportement : jour/nuit, aube/crépuscule et météo lisibles sur les scènes compatibles ; artistiques stables ; mouvement réduit et fond figé préservés.

Tout défaut majeur d'identité, de lumière, de perspective ou de compatibilité bloque l'acceptation. Une simple compilation ne prouve aucun de ces critères. Le critique demande une correction précise au designer, qui révise la fiche/prompt ; l'artiste régénère la scène ou la couche concernée. Les références et les itérations restent traçables.

## Calibration

Commencer par `kopa` (architecture sportive et profondeur difficile) et `loire` (paysage/eau/architecture locale). Une fois ces deux directions examinées indépendamment, dérouler le catalogue par familles. Ne pas utiliser Loire comme simple gabarit copié pour tous les lieux : chaque fiche garde une composition et une histoire propres.

## Veille design réellement inspectée

- [Alba: A Wildlife Adventure, couverture](https://en.wikipedia.org/wiki/Alba:_A_Wildlife_Adventure) : grandes silhouettes naturelles, tons chauds/froids francs, paysage stratifié très lisible. Retenir ces principes; ne pas reproduire personnages, arbres ou composition.
- [A Short Hike, capture de jeu](https://en.wikipedia.org/wiki/A_Short_Hike) : relief compris par faces de couleur et occlusions, chemins comme guides de profondeur, végétation par masses. Écarter le rendu pixelisé et la caméra du jeu; produire illustration verticale propre.
- La première image obtenue pour MonumentValley représente un développeur en portrait. Elle est rejetée et ne constitue pas une référence design.

## Palettes personnalisées et animation

`blob`, `aurora`, `waves`, `stars`, `mesh`, `grid` gardent le recoloriage par thème utilisateur. Une PNG opaque à couleurs fixes ne remplit pas ce contrat. Les nouveaux masters de ces fonds servent de direction artistique pour des plans vectoriels natifs palettisables ou de masques alpha/luminance séparés. Les couches portent volumes, faces/ombres et perspective; le moteur fournit les couleurs.

Chaque fiche JSON définit `nativeOverlay`; le master raster immobile ne remplace pas à lui seul les méduses, code, lanternes ou gerbes déjà animés. Les effets restent des couches légères, figées si mouvement réduit/économie. Le chemin de livraison accepté est `assets/backgrounds/<key>/art/artwork.png`, distinct de `layers/` réservé à la composition et aux effets.

## Correction de style et de cadrage après les premières preuves

Les premières images avaient trop de micro-détails semi-réalistes. La prescription commune passe à des **masses cartoon franches et propres**, deux ou trois valeurs par matière, faces de lumière chaudes et ombres prune/bleu nettes, texture locale minimale. Le volume et la profondeur doivent rester riches malgré la simplification.

Le master1024×1536 est recadré pour un téléphone9:19,5. Tous les landmarks nécessaires à l'identité restent dans x=20–80 % du master, point focal vers x=65 % maximum. Les grandes formes de bord peuvent être coupées. Sur Loire, le château doit se rapprocher du centre : x≈85 % du premier master se fait couper. Le critique vérifie un rendu natif360×780 et ne déduit pas cette conformité du seul master.
