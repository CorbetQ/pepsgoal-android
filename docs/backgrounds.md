# Fonds cartoon et ambiance locale

Les scènes gardent leurs clés, leur accès boutique/secret et leur appartenance aux
packs. Les prix, les droits synchronisés et les préférences enregistrées restent
compatibles avec 8.6.0. Aucun changement de version, de schéma ou d'API serveur.

## Direction visuelle et travail en quatre rôles

La collection illustrée est reconstruite à partir de références internet :
45 illustrations originales et sept compositions natives (six abstraits
recolorables et le ciel vivant). Le fond uni et la photo personnelle gardent
leur fonction. Les quatre rôles et leurs preuves sont documentés dans
[le dossier de conception](design/background-reboot/README.md).

La recherche réunit 67 images pour 52 fonds, avec provenance et sélection
visuelle. Le designer définit compositions, palettes, cadrage téléphone,
lumières, matières et critères. L'artiste produit des formes cartoon franches,
des faces éclairées et ombrées, et trois plans ou davantage. Le critique
compare les masters aux sources puis aux rendus Android : plusieurs versions
ont été rejetées et corrigées, notamment Kopa, Loire, Tram et les abstraits.
Les visas et révisions exactes sont dans `accepted.json`, sans approbation
automatique fondée sur une compilation.

Les ombres de contact et le modelé sont peints dans les illustrations. L'heure
module leur exposition et leur température ; le ciel, les astres, les nuages
et la météo restent des couches natives. Les extérieurs ont un vrai ciel alpha.
Les six abstraits utilisent leurs nouvelles géométries et la palette du thème,
avec faces claires, tranches et ombres pleines. L'interface Braise Pop conserve
ses composants, ses plaques opaques et ses contrastes.

## Organisation des assets

Les 54 entrées vivent dans `app/src/main/assets/backgrounds/<clé>/` :
`art/` contient l'illustration acceptée et son manifeste ; `layers/` contient
les sources Canvas ; `properties.json` définit compatibilité et cadrage ;
`animations.json` règle cycles, cadences et surcouches. Les photographies de
recherche restent hors des assets distribués.

Les fichiers `layer.kts` sont compilés au build, sans interpréteur. La tâche
`generateBackgroundAssets` valide les JSON et génère le Kotlin sous
`app/build/generated/`. Le décodage des illustrations se fait hors du fil UI,
avec cache borné à 18 Mio et sous-échantillonnage des aperçus. Les images sont
remplies sans déformation, selon `focusX`/`focusY`. Le débordement de parallaxe
des toiles peintes est limité à 8 % et la translation reste dans cette réserve,
afin de préserver les sujets et de couvrir le bas de l'écran.

Le [guide des assets](../app/src/main/assets/backgrounds/README.md) décrit
les chemins et la procédure d'import des seules révisions acceptées.

## Compatibilité explicite

`SCENE_CAPABILITIES` dans le module core couvre chaque fond du catalogue :

- **Extérieur** : paysages, villes diurnes, tram, Loire, phare, vignes, montgolfières,
  bambous, toits, canyon, savane, banquise, scènes angevines/nantaises, paysages
  saisonniers de jour, Collines braise et ciel météo. Lumière et météo compatibles.
- **Vue intérieure** : Pluie sur la vitre. Les conditions modifient la vue derrière
  la vitre ; neige et brouillard restent derrière le cadre, la pluie n'est pas
  doublée par un calque plein écran. Sans météo activée, la pluie illustrée d'origine
  reste présente ; avec un ciel dégagé ou des observations périmées, elle s'efface.
- **Ambiance artistique** : espace, fonds marins, ville/train de nuit, lave, code,
  aurores, lanternes, tunnel, scènes festives nocturnes, cœurs, secrets SCO/SRFC et
  fonds abstraits. Ils gardent leur identité et ne reçoivent pas de pluie réelle.
  Les fonds uni et photo personnelle sont également exclus.

Apparence indique la compatibilité du fond courant et la ville utilisée. Les
réglages de lumière sont conservés : heure réelle, toujours le jour, suit le thème.
La météo est indépendante du mode de lumière et reste facultative. Les aperçus
boutique/packs sont figés et n'ouvrent aucune requête réseau.

## Horloge et météo

`SceneAtmosphere.resolve` reçoit un `Instant`, une ville, les prévisions et l'âge
du cache. Il produit un `SceneEnvironment` immuable utilisé par le rendu Android.
L'heure courante ne provient jamais de l'horodatage de l'observation météo.

Le fuseau est celui de la ville, puis celui des prévisions ; à défaut, celui du
téléphone. Les données solaires sont sélectionnées pour la date locale courante.
Si elles manquent ou sont invalides, une approximation solaire NOAA utilise les
coordonnées, la date et le décalage légal, y compris en jour/nuit polaire. Sans
coordonnées, l'estimation saisonnière existante reste le repli.

Aube et crépuscule se mélangent progressivement aux couleurs du jour et de la
nuit. Le thème automatique bascule au lever/coucher, indépendamment de ces
transitions artistiques. Le ciel météo utilise le même état que les paysages.

Une seule horloge à la racine se réveille à la minute, uniquement quand l'activité
est visible. Elle se recale au retour et après changement d'heure/fuseau. Les
requêtes utilisent le cache de quinze minutes et le délai de reprise d'une minute
après erreur. Un mutex empêche les requêtes simultanées ; une réponse pour une
ancienne ville n'écrase pas la sélection courante. Les dates de cache futures ne
bloquent pas un rafraîchissement après recul de l'horloge.

La fraîcheur tient compte de l'heure de l'observation et du cache. Les effets
s'atténuent entre 105 et 120 minutes ; après deux heures, le fond retrouve un temps
neutre tout en continuant à suivre le soleil. Aucune tâche réseau en arrière-plan
n'est ajoutée.

## Sobriété et accessibilité

Les cadences plafonnées, le régulateur de résolution, les pauses de défilement,
l'économie d'énergie et les options d'accessibilité restent en place. Les
horloges décoratives et le collecteur du rendu hors écran sont suspendus lorsque
l'activité est cachée. Le pool de trois images est réutilisé entre les changements
d'heure/météo ; un changement de scène reste le seul motif de fondu global.

Les effets météo deviennent une image statique en animations réduites ou quand
les effets de performance sont désactivés. Aucun flash d'orage plein écran.
La pluie/neige réelle prend le pas sur une neige décorative pour éviter le double
rendu. Les illustrations sont décodées une fois puis réutilisées. Les mouvements
restent des surcouches natives à budgets locaux ; aucun déplacement permanent
de la toile entière ne remplace une animation de sujet. Les couleurs RGB des
couches Canvas restent mises en cache.

Aucun gain énergétique chiffré n'est revendiqué sans mesure sur appareil.

## Galerie et vérifications

```bash
scripts/dev/check.sh core
scripts/dev/check.sh ui
scripts/dev/check.sh apk
scripts/dev/check.sh wear
scripts/dev/check.sh tooling
scripts/dev/gradle.sh :app:testGithubDebugUnitTest --tests '*SceneRenderTest'
```

`SceneEnvironmentTest` vérifie les fuseaux, changements d'heure, dates locales,
données absentes/invalides/périmées, transitions solaires, cas polaires et la
couverture du catalogue. Les tests historiques du thème solaire restent couverts.

`BackgroundAssetTest` vérifie la couverture des dossiers, les valeurs compilées
depuis les JSON, l’isolation des palettes entre deux travailleurs de rendu et
les empreintes SHA-256 des illustrations effectivement acceptées.

`SceneRenderTest` utilise Robolectric 4.16, Android 35 et le rendu Skia natif. Il
dessine les vrais Canvas, sans compte et sans appel météo, pour chaque scène et
les huit fonds gratuits/ciel. Une galerie HTML hors connexion (`index.html`) permet de rechercher les fonds et de basculer jour/nuit/crépuscule au format téléphone 9:19,5. Les planches météo restent accessibles séparément.
Huit planches sont générées sous
`app/build/reports/scenes/` : jour, nuit, aube, crépuscule, pluie, neige, brouillard,
orage. Les tests vérifient aussi l'absence de précipitations par ciel dégagé,
la visibilité de la lune, les modes de lumière forcés et la stabilité des scènes
artistiques. Ces planches sont des outils de revue, pas des références approuvées
automatiquement mises à jour. `SceneArtworkTest` vérifie la transparence,
l’exposition nocturne des paysages et de la fenêtre, et la stabilité des scènes
artistiques. GitHub Actions conserve galerie, PNG et rapports en artefact.

Les planches jour/nuit et les météos permettent une revue des décors ; elles ne
remplacent pas les captures d'écrans complets. Sur téléphone/émulateur dédié :
contrôler lisibilité, clair/sombre, texte 200 %, petite largeur, TalkBack,
animations réduites, défilement, changement de ville, reprise/hors connexion et
fonctionnement du lanceur après application d'un pack. Les mesures de batterie,
FPS et jank nécessitent du matériel adapté.
