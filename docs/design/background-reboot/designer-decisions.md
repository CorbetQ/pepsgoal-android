# Décisions de design et corrections

La demande remplace les améliorations procédurales successives par une création à partir de références internet et une critique indépendante. Les premières fiches sont des intentions; les références doivent étayer les faits et les images produites doivent être réellement revues.

| Décision | Preuve/raison | Conséquence |
| --- | --- | --- |
| Séparer illustration et UI | Guide vivant BraisePopScreen.kt + demande de nouvelles lumières/ombres | Cartes opaques, marches et encre prune inchangées; modelé et ombres colorées permis dans les illustrations. |
| Produire Kopa et Loire d'abord | Difficulté du stade et référence de paysage citée par l'utilisateur | Calibration architecture + paysage avant extension des52. |
| Kopa n'est pas documenté architecturalement en2026 | Photo01=ancienne tribune2009; photo02=photo récente supporters seulement | Évocation cartoon SCO fidèle aux éléments réellement vus; aucune revendication de reconstruction contemporaine exacte. |
| Loire suit Saumur | Deux références vues: Blois et Saumur | Château sur promontoire au-dessus du front urbain; Blois sert largeur/reflets, son pont n'est pas importé. |
| V1 refusées | Masters réellement inspectés par designer et critique | Réduire microtexture, feuilles/herbes/cheveux; ombres par grandes faces, style cartoon cohérent. |
| V2 conformes en direction artistique | Deux masters réellement inspectés de nouveau | Le critique valide la direction, sans conclure au cadrage intégré; captures Android restent nécessaires. |
| Cadrage téléphone long | Master2:3 recadré vers9:19,5; château trop à droite sur Loire | Landmarks dans x20–80%, cible65%; plans de bord dispensables pour l'identité. |
| Tram de la vraie livrée angevine | Photo placeRalliement + photo pontConfluences | Tram blanc à bandes multicolores, pas tram rouge générique. |
| Nantes se fonde sur l'Éléphant | Photo réellement vue MachinesdeNantes; promesse boutique Éléphant/Titan/Anneaux | Éléphant bois/acier au quai, pas mélange géographique Erdre/château. Repères secondaires prudents si non documentés. |
| Angevine conserve son sujet acheté | Boutique vend vignesLayon/logistuffeau/moulincavier, référence initiale MaisonAdam urbaine | Demander compléments géographiques avant production; ne pas changer le paysage acheté en place urbaine. |
| Rainwindow garde une vitre sèche par beau temps | Contrat météo existant | Master sans gouttes fixes; gouttes uniquement natives conditionnelles. |
| Six abstraits gardent les couleurs du thème | Fonctionnement existant de blob/aurora/waves/stars/mesh/grid | Plans natifs palettisables ou masques; PNG opaque fixe insuffisante. |
| Citynight reste artistique | Contrat core existant | Aucune météo terrestre par défaut; fenêtres/reflets nocturnes séparés. |
| Sky reste adaptatif | Contrat ciel vivant | Ciel/astres/nuages natifs, décor éventuel avec ciel transparent. |
| Angers/Tunnel/Lanterns suivent le catalogue | Lecture Shop.kt après premier brief | Angers garde tramMaine/château/cathédrale; Tunnel reste métroRennes moderne; Lanterns garde envol au-dessus du lac. |

La validation structurelle a confirmé52fiches,52prompts et1–10URLs source par fond, avec chemins de recherche locaux existants. Elle ne vaut pas validation de52illustrations : le registre de critique suit les images réellement produites.

## Complément Angevine et critique du premier lot

Le chercheur a fourni une photographie des vignes du Layon à Pontbarré, une photographie du moulin cavier de LaHerpinière et une coupe technique. Designer et critique ont réellement inspecté ces trois images. La référence MaisonAdam a été retirée de cette fiche; le prompt impose une petite hucherolle en bois sur cône maçonné avec quatre ailes et longue échelle, plutôt qu'un moulin-tour hollandais. Le blocage documentaire Angevine est levé; cela ne préjuge pas de l'image produite.

Le premier master Tram a été refusé par le critique et réellement inspecté par le designer : pavés, façades, feuillage et reflets trop détaillés. Le prompt d'édition `correction-tram-v2.md` demande des grandes faces de couleur tout en conservant la livrée blanche/multicolore, le volume, les rails et une cabine entière dans le recadrage téléphone. La correction ne se contente pas d'augmenter la saturation.

## Révision des sept fonds natifs

Le designer a réellement inspecté les21rendus jour/nuit/crépuscule des sept fonds natifs. Stars/Grid/Waves/Sky remplissent une direction abstraite volontairement simple. Blob/Aurora/Mesh ont d'abord été refusés pour des défauts de géométrie : proche aplati sans face sombre visible, rubans parallèles sans retournement, éventail de bandes sans drapé. Les prescriptions détaillées sont dans `native-corrections-r2.md`.

Après modification par l'agent principal, les six rendus jour/nuit r2 de Blob/Aurora/Mesh ont été réellement ouverts par le designer. Les défauts ciblés sont corrigés : trois faces et bord bas visibles sur Blob, croisement et revers local sur Aurora, plis décalés et lèvre proche avec sous-face/occlusion sur Mesh. Le designer valide la direction native abstraite sans demander une complexité gratuite. Le verdict indépendant du critique et les vérifications de comportement restent distincts.

## Rooftops et Rainwindow

Deux corrections supplémentaires sont écrites après inspection effective des masters concernés :

- `correction-rooftops-v2.md` conserve l'excellente profondeur ville/toits/rue/eau mais simplifie les microdétails architecturaux, feuilles et ferronneries pour rejoindre la calibration cartoon.
- `correction-rainwindow-v2.md` conserve le beau cadre/plante/jardin existant et retire uniquement la grande zone de ciel bleu peinte dans l'ouverture. Le ciel nocturne doit rester natif; aucune goutte fixe ne doit être ajoutée.

## Dernières corrections après les masters du catalogue

- **Écran** : le master contenait des chiffres peints, incompatibles avec le score natif, ainsi qu'une matière de sièges/reflets trop réaliste. `correction-ecran-v2.md` impose une face de panneau strictement vide et la simplification cartoon des sièges, foule et reflets; la belle perspective reste intacte.
- **Snowpeak** : le profil Matterhorn et les crêtes sont conservés, mais les fissures/éclats passent à quelques grandes faces neige/roche dans `correction-snowpeak-v2.md`.
- **LoireSummer** : le pique-nique sur plage et l'îlot gardent l'identité distincte du fond. `correction-loiresummer-v2.md` simplifie la matière fine pour rejoindre Loire3 et recentre indépendamment bateau et château avant recadrage téléphone long.

Les masters corrigés Tram2, Rooftops2, Rainwindow2 et Loire3 ont été réellement ouverts par le designer. Ils corrigent le niveau de détail ou la composition demandés; seul le rendu intégré long peut conclure définitivement au recadrage. Aucun visa des autres corrections n'est déduit de leur simple génération.

## Réception des dernières corrections

Les trois masters Ecran2, Snowpeak2 et LoireSummer2 ont ensuite été réellement inspectés par le designer. Les prescriptions de leurs fiches sont remplies au niveau du master : panneau vide et matière groupée pour Écran; neige/roche à grandes faces avec corniche et crêtes préservées pour Snowpeak; matière cartoon, sujets recentrés et identité pique-nique/îlot maintenue pour LoireSummer. Le designer ne demande pas de nouvelle modification. Le critique garde son verdict indépendant, notamment sur les rendus intégrés et les recadrages longs.
