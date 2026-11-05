# Revue indépendante — refonte complète des fonds

État actuel : les52 fonds ont un visa visuel source+rendu Android Canvas documenté dans `accepted.json`. Les dernières révisions corrigent les refus de style, de transparence, de recadrage et de surcouches. La contre-vérification finale du surcadrage réel de parallaxe de 8 % est conforme; il ne s’agit pas de captures sur téléphone ni de mesures de batterie.

Cette revue juge les **images produites**, confrontées aux photographies/références internet et au cahier des charges du designer. Un build vert, un prompt ou une propriété JSON ne constituent pas une preuve esthétique.

## Protocole et critères

Chaque contrôle consigne le chemin de l’image, sa révision, les références effectivement vues et le brief utilisé. Les vues portrait grand format puis aperçu réduit sont inspectées; les scènes adaptatives nécessitent aussi des captures intégrées jour/nuit et météo, que des planches sources seules ne peuvent valider.

- **Identité / conformité** : lieu ou sujet reconnaissable à partir des références, aucun landmark inventé présenté comme exact; références des lieux réels distinguées des références de style.
- **Braise Pop** : silhouettes cartoon généreuses, formes lisibles, énergie chaude ponctuelle, prune dans les ombres; ni photo filtrée, ni rendu terne, ni duplication de la même composition sur plusieurs fonds.
- **Composition mobile** : foyer lisible en portrait et en miniature, espace pour l’UI, détails importants éloignés des bords de recadrage, pas de texte généré ou logos déformés.
- **Profondeur** : avant/milieu/arrière-plan distincts par échelle, contraste, occlusion et perspective; le sujet paraît intégré au sol.
- **Lumière et ombres** : surfaces éclairées et faces d’ombre explicites; direction cohérente, ombres portées/contact crédibles; reflets réservés aux matériaux qui les justifient.
- **Couleur et matière** : saturation contrôlée mais vive, contrastes de valeurs, textures de matière sélectives; aucun bruit uniforme ou détail qui fourmille à petite taille.
- **Temps et météo** : calques permettant ciel/ambiance live; pas de soleil peint fixe s’il se déplace, pas de pluie gravée dans une scène annoncée météo-compatible; fenêtres et luminaires cohérents de nuit. Les scènes artistiques fixes ont un contrat explicite distinct.
- **Intégration / sobriété** : dossier par fond et sources propres, décodage/caches/animations limités; économie d’énergie et mouvements réduits respectés. L’évaluation visuelle ne remplace pas les tests techniques de l’agent principal.

Les défauts de perspective, landmark erroné, absence de volume/lumière ou contradiction temporelle bloquent l’acceptation. Une correction est renvoyée au designer (brief) et à l’artiste (production), puis la nouvelle image est réellement inspectée. Aucun fond n’est approuvé par défaut.

## Suivi du catalogue

Statut initial : aucun nouveau visuel encore transmis au critique. Les anciens rendus Canvas ne valident pas cette refonte de zéro.

| Fond | Références vues | Brief | Image neuve inspectée | Verdict | Correction / preuve |
|---|---|---|---|---|---|
| `nebula` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/nebula/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `deepsea` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/deepsea/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `mountains` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/mountains/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `citynight` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/citynight/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `tram` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/tram/v2.png` | Accepté master+Android portrait long | Tall jour/nuit vus; focusX0.60 |
| `mistforest` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/mistforest/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `dunes` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/dunes/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `sakura` | Planche v2 (voir journal) | Fiche finale lue | `art/sakura/v1.png` | Accepté master+Android portrait long | Candidats tall-day/night réellement vus |
| `magma` | Planche v2 (voir journal) | Fiche finale lue | `art/magma/v1.png` | Accepté master+Android portrait long | Tall jour/nuit vus |
| `coderain` | Planche v2 (voir journal) | Fiche finale lue | `art/coderain/v1.png` | Accepté master+Android portrait long | Tall jour/nuit vus |
| `borealis` | Planche v2 (voir journal) | Fiche finale lue | `art/borealis/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `rainwindow` | Planche contact0–2 (voir journal) | Fiche finale/correction lue | `art/rainwindow/v2.png` | Accepté master+Android portrait long | Planches jour/nuit vues; pluie aussi vue |
| `planet` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/planet/v1.png` | Accepté master+Android portrait long | Tall jour/nuit réellement ouverts |
| `lagoon` | Planche v2 (voir journal) | Fiche finale lue | `art/lagoon/v1.png` | Accepté master+Android portrait long | Tall jour/nuit réellement ouverts |
| `loire` | `loire/01.jpg` Blois et `02.jpg` Saumur | Calibration r1 lue | `art/loire/v3.png` | Accepté master+Android portrait long | Tall jour/nuit vus |
| `lighthouse` | Planche v2 (voir journal) | Fiche finale lue | `art/lighthouse/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `vineyard` | Planche v2 (voir journal) | Fiche finale lue | `art/vineyard/v1.png` | Accepté master+Android portrait long | Tall jour/nuit vus |
| `balloons` | Final-contact (remplacement pertinent vu) | Fiche finale lue | `art/balloons/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.68 |
| `jellyfish` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/jellyfish/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `bamboo` | Planche v2 (voir journal) | Fiche finale lue | `art/bamboo/v1.png` | Accepté master+Android portrait long | Tall jour/nuit vus |
| `nighttrain` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/nighttrain/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.60 |
| `rooftops` | Planche contact0–2 (voir journal) | Fiche finale/correction lue | `art/rooftops/v2.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `canyon` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/canyon/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `savanna` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/savanna/v1.png` | Accepté master+Android portrait long | Tall jour/nuit vus |
| `lanterns` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/lanterns/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.50 |
| `icefield` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/icefield/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `tunnel` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/tunnel/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `angers` | Planche contact0–2 (voir journal) | Fiche finale/correction lue | `art/angers/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.25 |
| `nantes` | Planche contact0–2 (voir journal) | Fiche finale lue | `art/nantes/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `angevine` | `angevine/layon-01/02/03.jpg` vus | Fiche finale lue | `art/angevine/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.60 |
| `haunted` | Planche v2 (voir journal) | Fiche finale lue | `art/haunted/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.68 |
| `snowvillage` | Final-contact (remplacement pertinent vu) | Fiche finale/correction lue | `art/snowvillage/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `fireworks` | Planche contact0–2 (voir journal) | Fiche finale/correction lue | `art/fireworks/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `snowpeak` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/snowpeak/v2.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.50 |
| `heartsky` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/heartsky/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.80 |
| `eggmeadow` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/eggmeadow/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `flowerfield` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/flowerfield/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `summerbeach` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/summerbeach/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `autumnwood` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/autumnwood/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `xmastree` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/xmastree/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `valentine` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/valentine/v1.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.80 |
| `loiresummer` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/loiresummer/v2.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.50 |
| `kopa` | `kopa/01.jpg` (tribune2009), `02.png` (supporters2026) | Calibration r1 lue | `art/kopa/v1.png`, `v2.png` | v2 acceptée visuellement en portrait long | Master+Android360×780 vus; périmètre artistique fixe |
| `ecran` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/ecran/v2.png` | Accepté master+Android portrait long | Targeted jour/nuit vus; focusX0.50 |
| `pop` | Planche v2 (voir journal) | Fiche finale/correction lue | `art/pop/v1.png` | Accepté master+Android portrait long | Planches jour/nuit vues |
| `sky` | Planche v2 (voir journal) | Direction native lue | `samples/sky-day/night.png` | Natif accepté visuellement | Palette thème et volumes préservés |
| `blob` | Planche v2 (voir journal) | Correction native r2 lue | `samples/blob-day/night.png` | Natif r2 accepté visuellement | Volume/occlusion corrigés et palette préservée |
| `aurora` | Planche v2 (voir journal) | Correction native r2 lue | `samples/aurora-day/night.png` | Natif r2 accepté visuellement | Volume/occlusion corrigés et palette préservée |
| `waves` | Planche v2 (voir journal) | Direction native lue | `samples/waves-day/night.png` | Natif accepté visuellement | Palette thème et volumes préservés |
| `stars` | Planche v2 (voir journal) | Direction native lue | `samples/stars-day/night.png` | Natif accepté visuellement | Palette thème et volumes préservés |
| `mesh` | Planche v2 (voir journal) | Correction native r2 lue | `samples/mesh-day/night.png` | Natif r2 accepté visuellement | Volume/occlusion corrigés et palette préservée |
| `grid` | Final-contact (remplacement pertinent vu) | Direction native lue | `samples/grid-day/night.png` | Natif accepté visuellement | Palette thème et volumes préservés |

`plain` reste un aplat choisi par l’utilisateur; `custom` reste sa photographie. Ces deux modes ne font pas partie des52 illustrations à recréer.

## Journal de critique

Aucune production neuve inspectée à ce stade. Aucun test appareil ou jugement de contraste du contenu de l’application n’est prétendu.

### Précontrôle documentaire Kopa / Loire

Images vues : `/workspace/.pepsgoal/background-reboot/references/kopa/01.jpg`, `loire/01.jpg`, `loire/02.jpg`, et anciennes captures `coherence-before/samples/kopa-day.png`, `loire-day.png`.

- **Kopa** : photo historique de2009, tribune St-Léonard tubulaire. À utiliser pour le caractère tribune/terrain, mais elle ne documente pas seule l’architecture actuelle. Demande transmise au chercheur pour une référence récente. Le stade de l’ancienne capture est une bande de foule répétitive et un rectangle vert; cette capture ne doit pas servir de cible.
- **Loire** : `01` montre Blois; `02` Saumur et des toues. Le château de Saumur est sur un coteau, au-dessus des toits et du quai. Brief demandé sans mélange implicite de landmarks de Blois et Saumur ni château générique posé dans le fleuve. L’ancienne capture avait des plans lisibles, mais une architecture de conte approximative.

Ces précontrôles concernent les références, **pas une approbation des futures images**.

### Audit des références — planches contact0,1,2

Trois planches réellement ouvertes : `/workspace/.pepsgoal/background-reboot/references/contact-0.jpg`, `contact-1.jpg`, `contact-2.jpg`. Elles contiennent les photographies des21 fonds marqués ci-dessus, ainsi que Kopa/Loire déjà vus en grand, et deux images rejetées.

- Références pertinentes : Angers (Maine/vue urbaine), Angevine (maison à colombages/place), Canyon (Antelope et Grand Canyon), Citynight (Stockholm/Singapour), Deepsea (kelp/requin), Fireworks, Jellyfish, Lanterns (suspendues et volantes), Mistforest, Mountains, Nebula, Nighttrain (Jacobite/Glenfinnan), Planet (Saturne), Rainwindow, Rooftops, Savanna, Tram et Tunnel.
- **Dunes** : `01` montre White Sands (désert), `02` une dune herbeuse tempérée; la seconde ne prouve pas une ambiance désertique, seulement des courbes de relief.
- **Icefield** : `01` iceberg pertinent; `02` Everest représente haute montagne/glacier et ne prouve pas de banquise. Ne pas fusionner ces habitats par défaut.
- **Nantes** : `01` documente l’éléphant des Machines à l’intérieur d’une halle, pas la géométrie de toute la ville. Le brief doit expliciter son sujet.
- **Tram** : les deux vues documentent Ralliement et la livrée angevine multicolore sur pont Confluences; bon ancrage local.
- **Rejetées** : Haunted01 est un dessin de personnage, Snowvillage01 des branches devant un lampadaire. Le chercheur les retire; aucun statut référence-vue n’est attribué à ces fonds tant qu’un remplacement pertinent n’est pas inspecté.

Aucune de ces lignes ne valide une production artistique nouvelle, encore absente.

### Audit documentaire v2 et brief de calibration

Six nouvelles planches réellement ouvertes, conservées sans écrasement sous `/workspace/.pepsgoal/background-reboot/critique/references-v2/contact-0.jpg` à `contact-5.jpg`. Les anciennes planches v1 ont été remplacées par le chercheur; les fichiers copiés v2 deviennent la preuve stable de cet audit.

Les références complémentaires pour Aurora, Autumnwood, Bamboo, Blob, Borealis, Coderain, Ecran, Eggmeadow, Flowerfield, Haunted (nouvelle maison), Heartsky, Lagoon, Lighthouse, LoireSummer, Magma, Mesh, Pop, Sakura, Sky, Snowpeak, Stars, Summerbeach, Valentine, Vineyard, Waves et XmasTree sont pertinentes soit au sujet, soit à un principe explicite (palette, matière, forme) et non nécessairement à une composition entière.

- **Rejets additionnels transmis** : Balloons01 est une ville sans montgolfière identifiable; Grid01 est un DJ (Kavinsky); Design-monument01 est le portrait d’un homme. Ces trois images ne documentent pas les fonds ni l’esthétique environnementale demandée. Le chercheur confirme leur remplacement.
- **Kopa02** : supporters SCO, drapeaux noir/blanc et écharpe; bonne référence d’atmosphère2026, mais pas vue documentaire de la structure actuelle du stade.
- **Aurora/Borealis** : même photographie depuis l’espace; acceptable pour lumière du phénomène, demande du chercheur en cours d’une vue terrestre pour Borealis.
- **Mesh/Pop** : dauphin triangulé et intérieur Memphis; références de construction/palette, pas décors à reproduire littéralement.

Brief global et fiches Kopa/Loire calibration r1 lus. L’intention convient aux critères de revue. Correction documentaire demandée au designer: retirer la phrase d’en-tête « références en attente » lorsqu’une calibration inspectée existe déjà. Conformité des productions toujours **non évaluée**, aucune nouvelle image reçue.

Le moteur annoncé par l’agent principal composera le ciel natif derrière l’alpha et réservera son traitement solaire aux scènes compatibles. Une illustration outdoor à ciel opaque sera rejetée même si elle est belle. La validation finale du runtime attend la galerie Android jour/nuit/météo de l’agent principal.

### Critique de production — calibration v1

Masters1024×1536 réellement ouverts: `/workspace/.pepsgoal/background-reboot/art/kopa/v1.png` et `art/loire/v1.png`. Références Kopa01/02 et Loire01/02 vues auparavant, brief global et fiches calibration lus. L’artiste précise avoir employé les descriptions de référence et une version de brief antérieure à la révision r1; cette nuance reste dans le journal.

**Loire — refusée, demande r2 au designer et à l’artiste.** Points réussis: château sur promontoire au-dessus du front urbain, vraie largeur du fleuve, bateau posé/reflet, saule proche et plans successifs. Les faiblesses bloquantes sont le halo vert/jaune très étendu autour du feuillage dans le ciel transparent et le halo gris autour du château, ainsi qu’un rendu pictural quasi réaliste plein de microdétails de feuilles/herbes/écorce. Le ciel natif de nuit rendrait ces halos particulièrement étrangers. Correction: grandes masses cartoon propres, détails regroupés et réduits d’environ70%, faces éclairées/ombres colorées franches, lisières nettes; alpha zéro dans le ciel hors antialiasing fin et corps du décor opaques. Conserver la géographie et les quatre plans.

Vérification de données (sans modifier l’image): Loire RGBA, alpha0..253; eau à(500,1000) alpha252, herbe à(100,1300) alpha251, ciel central alpha0. Il n’y a donc pas de rectangle de ciel entièrement opaque, mais la semi-transparence générale et le halo nécessitent correction. L’alpha seul ne prouve pas la qualité des bords; un rendu Android jour/nuit a été demandé à l’agent principal.

**Kopa — prometteur, pas encore accepté en collection.** Points réussis: toiture épaisse avec sous-face, tribunes rectilignes en profondeur, foule regroupée noir/blanc, pelouse ancrée, lumières chaudes/froides lisibles; amélioration majeure par rapport aux rectangles initiaux. Le rendu personnages/cheveux/structures reste plus proche d’un film animé semi-réaliste que du cartoon à grandes formes du brief. Correction de calibration: simplifier les silhouettes humaines et le détail structurel secondaire, masses plus généreuses et ombres prune plus franches; préserver la géométrie et les ombres de contact. La photo récente ne prouve pas l’exactitude de l’architecture actuelle: aucune revendication documentaire2026 n’est acceptée.

Aucune image v1 n’est un visa pour dérouler les52 en série. Designer et artiste ont reçu les défauts précis et doivent produire une calibration r2. Aucune galerie Android de ces productions encore inspectée.

### Clôture de collecte documentaire — 52 sujets couverts

Les six planches immuables `references/final-contact-0.jpg` à `final-contact-5.jpg` ont été réellement ouvertes. Les remplacements Balloons (montgolfière rouge en vol), Grid (grilles néon/perspective) et Snowvillage (villages des Alpes enneigés) conviennent. Le portrait prétendument Monument Valley a été supprimé. Chaque fond possède désormais entre1 et10 références internet visuellement pertinentes à son sujet ou à un principe de construction, avec les limites suivantes maintenues: Kopa architecture historique, Nantes éléphant et non château, Mesh/Pop construction/palette seulement. **Collecte validée en pertinence ne signifie pas production validée.**

### Critique du cahier des charges hors calibration

Les52 compositions/sujets du fichier `briefs.json` ont été lus. Trois corrections concrètes ont été demandées au designer:

- Nantes combinait quai de l’Erdre et château des Ducs, géographie trompeuse; demander château+douves documentés ou évocation explicitement choisie. La référence d’éléphant ne valide pas un château.
- Rainwindow demandait des gouttes peintes dans le master, contradictoires avec météo sèche; conserver gouttes en couche native indépendante. Clarifier « no frame » qui ne doit pas supprimer le cadre en bois de la fenêtre.
- Tram rouge générique ne correspondait pas aux photographies angevines à livrée blanche/multicolore; reprendre la livrée documentée ou une autre référence réelle.

Angevine doit aussi distinguer référence de maison urbaine et décor de campagne; la première peut illustrer une matière, pas prouver la géographie du hameau.

### Contre-vérification des candidats dans le moteur Android

Images effectivement ouvertes: `app/build/reports/scenes/candidates/loire-v1-day.png`, `loire-v1-night.png`, `loire-v1-rain.png`, `kopa-v1-day.png`. Ce sont des sorties Canvas natives temporaires de test, pas des captures d’un appareil et pas des assets en production.

**Rectification du diagnostic de halo :** l’aperçu isolé du master sur fond noir faisait paraître des zones vertes/grises comme de grands halos. Ils ne sont pas manifestes dans les composites réels jour/nuit. Le ciel natif est bien visible à travers l’alpha, les bords se détachent proprement, la nuit conserve des plans lisibles. Le critique retire donc le blocage fondé uniquement sur un halo supposé du master et en informe les trois autres rôles. Il ne faut pas régénérer au risque de détruire le détourage pour une anomalie d’aperçu seule. La semi-opacité alpha251–253 des surfaces est mineure, pas une preuve isolée de défaut perceptible.

Le refus de calibration Loire reste fondé sur le **style trop réaliste et microdétaillé**, confirmé en portrait480×800. La correction Kopa reste nécessaire pour établir une collection cartoon homogène. Ajout: le château Loire est trop près du bord droit dans le recadrage téléphone; demander environ8% de marge supplémentaire au master pour garder tour et enceinte sur formats longs. Ces corrections ont été retransmises au designer et à l’artiste.

Météo pluie: extérieur visible, précipitations natives distinctes, pas de pluie gravée dans le master. Kopa garde son ambiance artistique propre. Ces quatre images valident le principe d’intégration, pas l’ensemble des états ni la production finale r2.

### Calibration v2 — correction reçue et réellement inspectée

Images ouvertes: `/workspace/.pepsgoal/background-reboot/art/kopa/v2.png` et `art/loire/v2.png`. Les deux masters corrigent le défaut majeur de style.

- **Kopa v2**: masses de foule simplifiées, silhouettes proches généreuses, toitures à faces lisibles, éclairage chaud/froid et ombres prune, terrain vif mat. La simplification conserve profondeur et ombres de contact. Direction artistique du master acceptée.
- **Loire v2**: grappes larges du saule, tronc à grandes faces, eau par formes/reflets groupés, fleurs/rochers modelés sans bruit, ville et château encore reconnaissables sur promontoire. Direction artistique du master acceptée. Le château reste autour dex80%; il faut vérifier le cadrage360×780 et éventuellement le recentrer vers65% avant acceptation finale.

Designer et artiste informés: cette calibration peut guider les lots suivants, sans approbation automatique des nouvelles scènes. L’agent principal produira une galerie Android v2; direction artistique acceptée n’équivaut pas à visa runtime/cadrage. Aucune revendication d’exactitude de l’architecture2026 du stade n’est faite.

### Galerie Android v2 — portrait long et météo

Images réellement ouvertes: `candidates/loire-v2-tall-day.png`, `loire-v2-tall-night.png`, `loire-v2-rain.png`, `kopa-v2-tall-day.png`.

**Kopa v2 accepté visuellement en portrait long.** Les tribunes, terrain, public proche et épaisseur de toiture restent lisibles. Le but proche est volontairement coupé par le cadre, mais le sujet stade et sa profondeur sont intacts. L’ambiance artistique fixe est respectée.

**Loire v2 refusé sur le cadrage téléphone long.** Le château est coupé à droite et la cabine de la gabare à gauche. Le focus horizontal seul ne peut pas sauver simultanément les deux sujets. Demande r3 transmise au designer et à l’artiste: recomposer le master, château versx65%, bateau versx40%, silhouettes essentielles dansx22–78%. Conserver le style cartoon et les plans réussis. Les rendus nuit et pluie montrent un ciel natif correctement séparé et des volumes lisibles; le défaut restant est la composition, pas l’alpha.

### Reprise de revue après disponibilité des outils — lot1

Les huit masters `art/{nebula,deepsea,mountains,citynight,tram,mistforest,dunes,sakura}/v1.png` ont été réellement ouverts en grand. Leur production a employé les briefs finalisés et les références des planches déjà inspectées.

Direction artistique acceptée pour sept masters: **Nebula** (nuées chaudes/froides sculptées, grand vide central), **Deepsea** (faille, voûte et kelp étagés), **Mountains** (sommets facettés, lac et sentier ancrés), **Citynight** (canal oblique, façades modelées, fenêtres et reflets groupés), **Mistforest** (troncs massifs, trois rideaux forestiers et sentier), **Dunes** (contre-pentes et arêtes claires/ombres prune, matière propre), **Sakura** (pont arqué volumétrique, arbre proche et pavillon étagés). Ces scènes restent distinctes et satisfont le niveau de volume/couleur recherché. Leur cadrage et leur comportement sont encore soumis aux rendus Android.

**Tram v1 refusé sur le style.** Véhicule presque photographique, architecture miniature trop détaillée, pavement fin et feuillage dense; la scène retombe dans le semi-réalisme corrigé lors de la calibration. Demande v2 transmise au designer et à l’artiste: grandes formes et deux/trois valeurs par matière, détails de pierre/pavés regroupés, masses végétales franches, véhicule cartoon avec livrée blanche multicolore maintenue. Le nez du tram doit rester dans la zone centrale x22–78% pour survivre au format long. Aucun rejet fondé sur le seul aperçu alpha noir; attendre les composites natifs.

### Précision sur la couverture documentaire Angevine

Le décompte52 signifie présence d’une source par clé, pas preuve documentaire suffisante de chaque composition choisie. La fiche finale Angevine prévoit campagne/hameau alors que la photographie collectée montre la Maison Adam en ville. En cohérence avec la réserve initiale, la production de ce paysage attend une photographie complémentaire des Coteaux du Layon ou d’un moulin cavier; demande renouvelée au chercheur. Le statut de cette ligne est explicitement remis « campagne non documentée ».

### Complément Angevine inspecté

Trois fichiers réellement ouverts: `references/angevine/layon-02.jpg` (paysage viticole Coteaux du Layon/Pontbarré), `layon-03.jpg` (photographie moulin cavier de la Herpinière), `layon-01.jpg` (coupe technique du même moulin). Ils documentent désormais la campagne basse, vignes, hameau et typologie du moulin. Le blocage documentaire rural est levé. Si un moulin est dessiné, sa hucherolle en bois posée sur cône en pierre et sa cave/butte doivent distinguer le cavier d’un moulin hollandais générique. Designer informé.

### Revue Android du lot1 et sélection traçable

Les rendus `candidates/{nebula,deepsea,citynight,mountains,mistforest,dunes,sakura}-v1-tall-day.png` et `-tall-night.png` ont tous été réellement ouverts. Les trois scènes artistiques gardent leur ambiance propre. Les quatre extérieurs ont un vrai ciel natif séparé et un assombrissement nocturne lisible. Le recadrage rogne des éléments décoratifs latéraux, mais ne détruit pas le sujet: faille Deepsea, chemin/lac Mountains, canal/pont Citynight, sentier Mistforest, crêtes Dunes et pont/cerisier Sakura restent identifiables. Acceptation visuelle master+rendu pour ces sept fonds.

Tram v2 master réellement ouvert: correction stylistique réussie. Grandes faces, végétation regroupée, livrée conservée. Le nez finit encore autour dex86%; verdict de cadrage long en attente, pas d’inscription à la sélection finale.

`accepted.json` devient la liste positive des seules révisions dont source et rendu ont été inspectés sans défaut bloquant. Elle ne contient pas Loire ni Tram à ce stade.

### Critique native r1

Sept rendus `samples/{blob,aurora,waves,stars,mesh,grid,sky}-day.png` réellement ouverts, puis `stars/grid/waves/sky-night.png`.

- **Refus Blob**: objet orange proche presque entièrement plat, pas de face claire/face d’ombre lisible; masse trop rognée pour comprendre le volume.
- **Refus Mesh**: bandes planes convergentes, ni retournement de tissu ni occlusion; le drapé sculpté du brief n’est pas visible.
- **Refus Aurora**: trois bandes horizontales parallèles sans pli ni croisement, trop proche de Waves et contraire aux rubans diagonaux avec occlusion du brief.
- **Stars accepté**: astres facettés avec tranches, profondeur par échelle et constellation lâche. **Grid accepté**: dalles en perspective avec épaisseur et deux dalles soulevées. **Waves accepté**: trois plans à crêtes claires/creux ombrés, rythme lisible. **Sky accepté**: ciel majoritaire, horizon bas, nuages natifs à deux faces et nuit distincte. Le niveau de simplicité de ces abstraits est intentionnel; il n’exige pas le détail d’un paysage documentaire.

Les quatre fonds natifs acceptés sont inscrits à `accepted.json` avec preuve jour/nuit. Les trois refus ont été renvoyés au designer pour prescription géométrique précise à l’agent principal, en conservant les palettes utilisateur.

### Lot2, premières productions

Masters `art/planet/v1.png`, `rainwindow/v1.png`, `lagoon/v1.png` réellement ouverts. **Planet** accepté artistiquement: globe modelé, anneaux avec occultations, roche/cratères en plans et gamme chaude/prune. **Lagoon** accepté artistiquement: ponton réellement épais et ancré, roche proche, îlot/relief distant et eau claire différenciée; les caustiques restent ciblées à la matière eau. Leur cadrage/runtime ne sont pas encore approuvés.

**Rainwindow v1 refusé techniquement avant intégration**: une large partie de l’ouverture comporte un ciel bleu peint presque opaque. Lecture sans modification de l’image: pixel(500,600)=(66,161,249,251), contre alpha3 au pixel(500,150). Ce défaut démontré par l’alpha est distinct du halo supposé Loire corrigé après rendu: il crée une plaque bleue devant le ciel natif de nuit. Demande d’édition r2 au designer/artiste: effacer tout ciel jusqu’aux silhouettes réelles du bâti/arbres, garder cadre/plante/intérieur opaques et gouttes entièrement natives. Le château générique est accepté comme évocation non nommée, pas comme précision géographique revendiquée.

### Lot2 — preuve d’intégration et refus confirmé

Rendus `candidates/{tram-v2,planet-v1,lagoon-v1,rainwindow-v1}-tall-day.png` et `-tall-night.png` réellement ouverts. Planet et Lagoon conservent sujet, profondeur et volumes au format long; Lagoon a un ciel nocturne propre et un assombrissement crédible. Ces deux révisions sont ajoutées à la sélection positive.

Tram v2 garde la quasi-totalité du nez, mais le bord droit est serré/coupé. Demande à l’agent principal d’essayer un focusX≈0,6 pour déplacer le cadrage vers la droite sans régénérer inutilement; le reste de la rame restera lisible. Pas de visa définitif avant image corrigée.

Rainwindow v1 confirme le défaut: grande plaque bleu clair devant le ciel nocturne, frontière d’alpha grossière visible. En outre l’extérieur reste éclairé de jour dans la sortie night. L’agent principal est averti de vérifier le grading de la scène window après correction r2; un ciel transparent seul ne suffira pas si le bâti/arbres extérieurs restent à pleine luminance diurne.

### Lot2 — sept nouveaux masters

`art/{coderain,magma,vineyard,borealis,rooftops,bamboo,savanna}/v1.png` réellement ouverts. Coderain (plans numériques sculptés), Magma (lave source basse et basaltes), Vineyard (ceps/rangs posés au relief, grappes proches), Borealis (draperies lumineuses et reflet lacustre), Bamboo (nœuds/cannes, racines et chemin en profondeur), Savanna (acacia en plateaux, girafes modelées, rivière et rochers) sont acceptés sur la direction artistique du master. Leur matière conserve du détail sélectif sans se substituer aux grandes silhouettes.

Rooftops v1 refusé pour rendu architectural miniature semi-réaliste: ferronneries fines, nombreuses petites lucarnes et lierre détaillé. Demande au designer et à l’artiste de préserver la très bonne perspective tout en regroupant façades lointaines, simplifiant garde-corps et végétation, avec deux/trois valeurs et ombres prune. Le cas est cohérent avec le refus Tram v1.

### Natif r2 — revue après corrections

Les six images `samples/{blob,aurora,mesh}-day.png` et `-night.png` ont été réellement ouvertes et confrontées à `native-corrections-r2.md`. Les trois corrections sont acceptées: **Blob** présente trois faces, un bord bas et un chevauchement lisible; **Aurora** possède diagonale, croisement et revers; **Mesh** possède plis décalés, vallées et lèvre retournée avec sous-face. Jour et nuit montrent des palettes distinctes tout en gardant le modelé. Les trois scènes sont ajoutées à la sélection positive sous révision `native-r2`. Leurs prédécesseurs restent des essais refusés.

### Onze nouveaux masters — revue réelle

`art/{angevine,balloons,canyon,haunted,icefield,jellyfish,lanterns,lighthouse,nantes,nighttrain,tunnel}/v1.png` ont été réellement ouverts. Tous sont acceptés en direction artistique: moulin cavier au-dessus de vignes, montgolfière à nacelle suspendue, strates du canyon et fleuve, maison hantée espiègle à volumes déformés cohérents, glace épaisse et chenal, cloches de méduses, lanternes célestes sur lac, phare côtier ancré, éléphant des Machines reconnaissable, locomotive sur arches et vue cabine de métro en tunnel moderne. Chacun présente profondeur et lumière propres. Le cadrage reste à valider, notamment ailes du moulin, enveloppe du ballon, locomotive et tête d’éléphant proches des bords.

### Corrections de cadrage et suite du lot2 — nouveaux visas

Master Loire v3 et tall jour/nuit réellement ouverts: château et bateau sont maintenant entiers dans le cadre; correction acceptée. Tram v2 tall après focusX0.60 réellement ouvert: nez complet avec petite marge; la correction de cadrage natif évite une génération inutile. Coderain/Magma/Vineyard/Bamboo/Savanna tall jour/nuit réellement ouverts et acceptés, lisibles avec ciels et grading corrects ou ambiance artistique fixe. Ces sept révisions rejoignent `accepted.json`.

Borealis tall jour/nuit montre deux grandes bandes grisbleu plates dans le ciel, absentes du master et superposées aux aurores peintes. Demande de correction à l’agent principal: retirer cet ancien effet ou le réduire à une nuance cohérente très discrète; le beau master n’a pas besoin d’être régénéré. Visa runtime suspendu jusqu’à cette correction.

### Fin des masters — catalogue entier réellement vu

Nouvelles images réellement ouvertes: Angers/Snowvillage/Fireworks v1, Rainwindow/Rooftops v2, Snowpeak/Heartsky/Eggmeadow/Flowerfield/Summerbeach/Autumnwood/XmasTree/Valentine v1, LoireSummer/Ecran/Pop v1. Les45 sujets raster ont maintenant tous reçu au moins une inspection du master par le critique.

Les corrections Rainwindow2 (ciel retiré) et Rooftops2 (grandes faces et détail regroupé) sont réussies côté source, attente du composite. Angers, Snowvillage, Fireworks, Heartsky, Eggmeadow, Flowerfield, Summerbeach, Autumnwood, XmasTree, Valentine et Pop satisfont la direction artistique: plans, lumière et matières distinctes sans ternissement global.

Trois refus supplémentaires/confirmés sont adressés au designer et à l’artiste:

- **Snowpeak1**: profil Matterhorn et corniche réussis, mais mattepainting trop réaliste à nombreux éclats et fissures. Conserver profil et plans, réduire le microdétail et sculpter neige/roche en grandes faces comme Mountains.
- **LoireSummer1**: matière picturale fine de l’ancienne Loire1 (innombrables feuilles/rides d’eau et château miniature), décalée par rapport à Loire3. Combiner en une édition la simplification cartoon et le recentrage château/bateau dans la zone sûre. Garder récit plage/pique-nique et îlot, distinct de Loire.
- **Ecran1**: score4/0 peint par le générateur et microtexture/reflets de sièges/sol quasi réalistes. Panneau doit être vide pour le score natif; regrouper reflets et matières tout en gardant l’ambiance rouge/noir. Le designer a déjà fourni une correction adéquate après inspection indépendante.

### Revue complète des45 candidats en portrait long

Les16 planches `candidates/review-day-0.png` à `review-day-7.png` et `review-night-0.png` à `review-night-7.png` ont toutes été réellement ouvertes. Les étiquettes de révision ont été vérifiées: Ecran1, Snowpeak1 et LoireSummer1 restent des essais non sélectionnés, malgré leur présence dans cette première galerie complète.

Dix-sept visas supplémentaires sans défaut bloquant: Autumnwood1, Borealis1 (bandes parasites retirées), Canyon1, Eggmeadow1, Fireworks1, Flowerfield1, Icefield1, Jellyfish1, Lighthouse1, Nantes1, Pop1, Rainwindow2, Rooftops2, Tunnel1, Summerbeach1, Snowvillage1 et XmasTree1. Rainwindow2-rain a aussi été ouvert individuellement: gouttes sur la vitre, intérieur sec, ciel natif propre; les rendus nocturnes montrent l’extérieur assombri. La sélection positive compte41 fonds.

Cadrages demandés à l’agent principal avant dernier visa: Angers focusX≈0,25 pour récupérer la cathédrale gauche; Angevine≈0,60 pour marge aile; Balloons≈0,68 pour enveloppe; Haunted≈0,68 pour tour; Heartsky≈0,80 pour cœur proche; Nighttrain≈0,60 pour marge locomotive; Valentine≈0,80 pour banc/tonnelle. Il s’agit d’ajustements de cadrage, pas de régénérations automatiques. Les arrière-plans décoratifs partiellement rognés des autres scènes ne détruisent pas leur identité et ne justifient pas de correction inutile.

**Lanterns**: trois gros rectangles orange pâle natifs flottent devant le master, sans forme de lanternes. Demande de remplacer ces anciens sprites par petites lanternes papier cohérentes (silhouette arrondie, ouverture/ellipse lumineuse) ou de les supprimer au profit d’un mouvement discret; le master lui-même est réussi.

### Dernières corrections source réellement reçues

Snowpeak2 et LoireSummer2 ouverts en grand: grandes faces neige/roche et corniche préservée pour le premier; matière cartoon et bateau/château recentrés pour le second. Ecran2 ouvert en grand: score peint supprimé, sièges/reflets regroupés et profondeur conservée. Ces trois masters sont acceptés artistiquement. Ecran doit porter son score natif sur le panneau central peint vide, pas conserver un panneau flottant haut-droit en doublon. Leur ajout à la sélection finale attend les vrais rendus Android de ces révisions.

### Dernière passe ciblée — 52 visas documentés

Les quatre planches `candidates/targeted-review-day-0.png`, `targeted-review-night-0.png`, `targeted-review-day-1.png` et `targeted-review-night-1.png` ont été réellement ouvertes. Elles portent les révisions exactes et montrent les sept focus corrigés, Lanterns avec petits sprites papier, Ecran2 avec un score natif unique dans le panneau peint, Snowpeak2 et LoireSummer2.

Les onze candidats restants sont acceptés visuellement à ce stade: Angers1, Angevine1, Balloons1, Ecran2, Haunted1, Heartsky1, Lanterns1, LoireSummer2, Nighttrain1, Snowpeak2, Valentine1. Tous les52 fonds ont donc une entrée explicite dans `accepted.json`, avec leurs révisions, preuves et focus. Plain et Custom restent hors du catalogue illustré.

**Limite de cette passe:** elle précède la reproduction explicite du surcadrage8% de parallaxe utilisé en production. L’agent principal prépare une ultime galerie fidèle à ce cadrage; des sujets proches des marges (Balloons, Haunted, Tram, LoireSummer notamment) nécessitent ce dernier contrôle. Les visas actuels ne sont pas une prétention de capture sur appareil ni de mesure énergétique. Les corrections demandées et leur réception sont traçables ci-dessus; aucun statut n’a été accordé sur la seule compilation.

### Contre-vérification finale — surcadrage de production de 8 %

Revue terminée après reprise du 4 novembre2026. Les16 planches `candidates/review-day-0.png` à `review-day-7.png` et `review-night-0.png` à `review-night-7.png`, générées le3 novembre à18:39–18:40UTC avec le surcadrage réel de production, ont toutes été réellement ouvertes de nouveau. Elles couvrent les45 révisions raster retenues en portrait long360×780, jour et nuit. Les sept fonds natifs conservent leur visa antérieur sur leurs rendus réels; ils ne deviennent pas artificiellement des rasters.

**Verdict : conforme, aucune régression de cadrage bloquante.** Balloons conserve son enveloppe principale et sa nacelle malgré une faible marge droite; la tour de Haunted reste identifiable et sa structure entière utile demeure dans la composition; le nez de Tram est complet; le château et le bateau de LoireSummer restent présents et lisibles. La cathédrale d’Angers est récupérée, le moulin d’Angevine reste reconnaissable, le banc de Valentine conserve sa lecture. Les autres scènes gardent leur sujet et leur profondeur. Les portions décoratives volontairement hors cadre ne nécessitent pas une nouvelle édition. Le grading nocturne et les ambiances artistiques fixes restent conformes; les surcouches corrigées ne réintroduisent ni bandes grises Borealis ni panneau de score doublé.

Les16 preuves sont figées hors dépôt dans `/workspace/.pepsgoal/background-reboot/critique/final-overscan-2026-11-03/`. Leur origine, date de rendu et SHA256 sont consignés dans `accepted.json`, section `productionOverscanReview`, afin qu’une future génération de galerie n’efface pas la preuve examinée. Les52 visas sont maintenus et l’attente de parallaxe est levée. Aucun nouvel asset ni code applicatif n’a été modifié lors de cette contre-vérification.

Cette validation esthétique porte sur les rendus Canvas et le cadrage illustré. Elle ne revendique ni contrôle sur appareil physique, ni mesure de batterie, ni exploration de toutes les positions possibles d’un capteur de parallaxe en vidéo. L’assemblage et les tests techniques relèvent du rapport de l’agent principal.
