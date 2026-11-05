# Revue indépendante des fonds secrets football 8.8

État actuel : les trois masters `v1` et leurs rendus Android portrait 360×780 avec surcadrage de 8 % ont été inspectés indépendamment le 04/11/2026. Les trois fonds sont acceptés dans ce périmètre, consigné dans `accepted.json`.

## Critères de sortie

Le critique confronte la photographie source, la fiche du designer, le master produit et les rendus Android 360×780 avec surcadrage réel de 8 %. La compilation et les prompts ne valident pas l’esthétique.

- Trois architectures clairement différentes, pas un stade identique recoloré : Parc des Princes couvert et compact, Camp Nou historique très haut et ouvert, Beaujoire à toiture verte ondulée.
- Cartoon Braise Pop à grandes masses et deux/trois faces par matière, couleurs franches, ombres prune/bleu et lumières chaudes/froides cohérentes. Pas de photo filtrée, microtexture semi-réaliste ou grisaille.
- Avant-plan, terrain/milieu et tribunes/lointain lisibles, épaisseur des structures, perspective crédible et contacts au sol. Identité préservée au recadrage long avec 8 % de débordement.
- Aucune lettre, logo, score, watermark ou personne réelle identifiable générés. Les éléments de marque nécessaires restent natifs et séparés.
- Scènes ARTISTIC opaques à ambiance propre, sans lumière/météo réelle injectée. Les scènes doivent rester lisibles immobiles et avec mouvement réduit.
- Les défauts bloquants sont retournés au designer et à l’artiste avec prescription concrète, puis la nouvelle image est réellement revue. Le visa final n’est consigné dans `accepted.json` qu’après source et rendu vus.

## Références réellement inspectées

- PSG : `references/psg/01.jpg` et `02.jpg` montrent le Parc des Princes de 2020 : gradins foncés/rouges, deux niveaux, couronne de toiture proche et couverte; `03.jpg` montre les contreforts/lames de béton vus depuis une rue en 2017. L’image extérieure explique la coque, mais n’autorise pas à placer ces mêmes contreforts arbitrairement au milieu du terrain.
- FCB : `references/fcb/01.jpg` est un panorama très large et étroit de 2017; `02.jpg` donne une bonne vue intérieure de 2019 avec cuvette immense, trois niveaux hauts et grande ouverture de ciel. **Camp Nou historique avant rénovation 2023** : ne pas revendiquer une restitution de l’architecture rénovée de 2026.
- FCN : `references/fcn/01.jpg` est un panorama intérieur 2016 où la toiture verte ondulée et les courbes des tribunes sont lisibles; `02.jpg` montre l’extérieur et ses nervures/supports de béton en 2022. La Beaujoire ne doit pas devenir un Camp Nou recoloré jaune/vert.

Ces 7 images ont été ouvertes individuellement via `view_image`; les URLs, dates, auteurs et licences figurent dans `references.json` et `references.md`. Les sources restent hors des assets de production.

## Suivi

| Fond | Références | Brief | Master | Android 8 % | Verdict |
|---|---|---|---|---|---|
| `psg` | 01 revue à nouveau | Brief lu | v1 vu | tall-day vu | Accepté |
| `fcb` | 02 revue à nouveau, historique | Brief lu | v1 vu | tall-day vu | Accepté |
| `fcn` | 01 revue à nouveau | Brief lu | v1 vu | tall-day vu | Accepté |

## Journal

La distinction historique Camp Nou a été signalée au designer et à l’agent principal avant production. Aucune précision documentaire contemporaine n’est revendiquée au-delà de la source.

### Cahier des charges inspecté

Le brief global et les trois prompts de `prompts/psg.txt`, `fcb.txt`, `fcn.txt` ont été réellement lus. Ils documentent trois compositions différentes, les volumes à deux/trois valeurs, l’absence de lettrage et l’espace sûr pour le recadrage. Aucune objection bloquante au cahier des charges.

La formulation initiale de la délégation évoquait « nuit », alors que le designer choisit trois lumières distinctes. Ce point a été confronté à l’agent principal, qui a confirmé : PSG de nuit, FCB au crépuscule, FCN en fin d’après-midi. Ce choix est intentionnel et toutes les scènes restent ARTISTIC fixes, sans météo réelle. Le critique ne demandera pas une régénération pour la seule heure artistique.

### Visa indépendant des illustrations v1 — 04/11/2026

Preuves ouvertes individuellement avec `view_image` pour cette revue : photographie PSG `references/psg/01.jpg`, FCB `references/fcb/02.jpg`, FCN `references/fcn/01.jpg`; les trois masters `/workspace/.pepsgoal/football-packs-8.8/art/<key>/v1.png`; les trois composites natifs `app/build/reports/scenes/candidates/<key>-v1-tall-day.png` (360×780, surcadrage de 8 %). Le brief et le manifeste des références ont été lus. La liste des sept photographies ci-dessus décrit la première revue documentaire; cette passe indépendante a rouvert les trois photographies précisées ici.

- **PSG accepté** : cuve compacte à deux niveaux, couronne couverte continue et sous-face épaisse, distinctes des deux autres stades. Nuit bleue et rouge, projecteurs froids et points chauds sous couverture cohérents. Sièges/rambarde proches, pelouse en diagonale, but et tribunes forment des plans nets; le but et la toiture restent lisibles dans le rendu long.
- **FCB accepté** : évocation du Camp Nou historique 2017/2019, cuve ouverte en trois étages avec plongée et profondeur beaucoup plus grandes. Le ciel abricot est intentionnel. Gradins blaugrana, garde-corps et écharpe à faces crème/prune donnent du volume; les trois niveaux et le but restent présents après recadrage. Ce visa ne prétend pas représenter le stade rénové de 2026.
- **FCN accepté** : couverture ondulée verte, consoles crème et tribune basse différencient nettement la Beaujoire. La courbe de toiture reste lisible au centre du portrait; but entièrement conservé. Soleil chaud, dessous vert sombre, longues ombres au sol et sièges facettés assurent profondeur et lumière de fin de journée.

Les trois illustrations gardent une lecture cartoon à masses franches et volumes colorés; les petites marques de pelouse restent secondaires à la taille finale. Aucun texte, logo, score, watermark ou visage identifiable n'a été observé. Les éléments de premier plan coupés sont périphériques et ne retirent pas l'identité du stade. Aucun défaut artistique bloquant relevé.

Limites exactes : revue statique des fichiers existants, sans build, génération, émulateur ni téléphone physique. Les images `tall-day` inspectées montrent le fond natif seul, sans cartes d'interface. Aucune vignette de boutique dédiée n'a été trouvée parmi les fichiers ciblés; sa lisibilité et les écrans avec interface superposée ne sont donc pas validés par ce visa. L'animation et les interactions ne sont pas évaluées ici.
