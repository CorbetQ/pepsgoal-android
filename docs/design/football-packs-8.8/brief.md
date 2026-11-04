# Trois stades secrets — PepsGoal8.8.0

## Direction et limites documentaires

Extension de la collection8.7, avec ses grandes formes cartoon, lumière par faces, ombres prune/bleu colorées, contacts au sol et au moins trois plans. Les cartes, composants et règles d'accessibilité Braise Pop restent inchangés. Les éclairages ci-dessous appartiennent à des illustrations `ARTISTIC` : scène complète opaque à ambiance fixe, indépendante de l'heure ou de la météo réelles.

Le designer a réellement ouvert les sept photographies du manifeste `references.json` : PSG01/02 intérieurs2020 et03 extérieur2017; FCB01 panorama2017 et02 match2019; FCN01 panorama2016 et02 extérieur2022. Ces références fixent des faits de structure et de couleurs; elles ne sont pas copiées dans l'application. Les photos tierces restent dans le cache de recherche hors dépôt.

**FCB représente le Camp Nou historique avant les travaux de2023.** Sa cuve profonde ouverte en trois étages est documentée par les sources2017/2019. Cette évocation ne prétend pas reconstituer le chantier ou l'état architectural2026. La rue extérieure du Parc des Princes sert seulement à confirmer ses contreforts de béton, pas à composer une fausse vue intérieure/extérieure simultanée.

## Grammaire commune

- Cartoon franc et propre : grandes masses intentionnelles, silhouettes légèrement arrondies, deux ou trois valeurs principales par matière. Pas de rendu photo, de miniature d'architecture3D, de petits cheveux ou de milliers de pixels d'herbe/sièges.
- Des volumes réellement modelés : face de lumière crème chaude, couleur locale, face d'ombre prune ou bleu froid. Toitures avec épaisseur et sous-face, gradins avec changements de niveau, sièges proches avec contacts et ombres portées. L'arrière-plan perd du contraste avec la distance.
- Matières par quelques marques sélectives : cinq à huit grandes bandes de tonte, trois à cinq nervures structurelles lisibles, quelques facettes de béton. Foule en groupes irréguliers, sans pointillage uniforme ni visages détaillés.
- Source lumineuse cohérente, le fond immobile est déjà immersif. Aucun effet ne doit servir à masquer un manque de profondeur.
- Aucun texte, logo, badge, chiffre, score, publicité, watermark ou pseudo-interface peint. Les références comportent des inscriptions de sièges et sponsors : elles sont remplacées par des groupes de couleur sans lettres. Éviter même un panneau de score si rien ne doit y être affiché.

## Format et recadrage

MasterPNG complet opaque1024×1536. Test final sur portrait9:19,5 avec surcadrage de8% utilisé par le moteur. Les signes d'identité importants sont contenus dans x20–80%; viser x25–75% pour le but, les ruptures de toiture et les grands repères de gradins. La zone centrale haute x25–75%, y8–43% reste calme pour l'interface. Les bords latéraux et le bas portent les sièges, garde-corps et textiles qui peuvent être volontairement coupés.

L'architecture distinctive doit être lisible au milieu du cadre, pas uniquement au bord. Un même template de stade recoloré pour les trois clubs ne remplit pas ce brief. Les perspectives, silhouettes de toit et rapports d'échelle sont différents.

## Trois compositions distinctes

| Fond | Sujet documenté et vue | Palette et lumière | Plans et identité |
| --- | --- | --- | --- |
| `psg` | Parc des Princes, vue basse depuis une tribune d'angle vers la cuve compacte et son large débord de toiture continu. | Bleu profond et rouge franc, quelques accents crème; match nocturne aux projecteurs froids, lumières sous toit légèrement chaudes. | Sièges bleu/rouge et rambarde proches; terrain en diagonale et but au milieu; gradins compacts à niveaux et rim de toiture courbe. Les contreforts extérieurs ne deviennent pas de faux piliers sur le terrain. |
| `fcb` | Camp Nou historique2017/2019, vue sensiblement plus haute depuis le bord d'un virage vers la cuve ouverte très profonde. | Blaugrana : bleu royal et grenat, petits accents or; crépuscule abricot calme et lumière de match blanche. | Rang de sièges et écharpe proche; grand terrain en plongée; trois étages continus et raides qui montent vers le ciel ouvert. Pas de toit intégral futuriste ni de toiture continue semblable au Parc. |
| `fcn` | Stade de la Beaujoire, vue basse depuis la présidentielle ou légèrement en angle vers la tribune ondulée opposée. | Jaune vif et vert profond, béton crème/vert grisé; fin de journée claire, lumière chaude rasante et ombres vert/prune. | Sièges et garde-corps proches; pelouse et but; courbe de couverture ondulée, côtes et consoles concrètes, tribunes moins hautes et moins profondes que Camp Nou. Aucune cuve monumentale à trois étages inventée. |

## Animations et livraison

Les masters sont fixes et opaques. Aucun ciel transparent n'est requis. Le rendu conserve les réglages mouvement réduit, fond figé et économie. Une surcouche native très légère peut ajouter deux ou trois scintillements de projecteur ou une ondulation discrète de petit fanion; elle ne contient aucun score ou glyphe. Pas de confettis permanents, de pluie uniforme, de flash ou de reconstruction du master à chaque image.

Source de production hors dépôt sous `.pepsgoal/football-packs-8.8/art/<key>/`; asset validé dans `assets/backgrounds/<key>/art/artwork.png`. Propriétés, couches et animations restent propres au dossier de chaque fond. Les sources photo ne sont jamais ajoutées aux assets.

## Revue indépendante attendue

Le critique inspecte le master, la vignette et le vrai composite natif9:19,5 avec surcadrage8%. Il vérifie identité des toitures/gradins, perspective, au moins trois plans, faces de lumière/ombre, matière cartoon et absence d'inscriptions. Tout sujet coupé, glissement semi-réaliste, score inventé ou stade générique bloque la validation. La compilation ne valide aucun critère artistique. Les corrections sont formulées au designer puis appliquées par l'artiste avec un prompt d'édition ciblé.

## Arbitrage d'ambiance confirmé

L'agent principal a confirmé les trois lumières distinctes ci-dessus : aucune contrainte utilisateur n'impose la nuit aux trois stades. Le ciel de crépuscule FCB et la fin de journée claire FCN sont donc des choix intentionnels, pas des défauts de suivi d'heure. Les trois ambiances UI peuvent rester sombres avec accents bleu/rouge, blaugrana/or et jaune/vert; leurs cartes opaques assurent la lisibilité sans devoir assombrir artificiellement les illustrations.
