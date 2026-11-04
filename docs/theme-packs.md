# Packs de thème — 9.0.0

Un pack réunit l'icône du lanceur, le fond illustré, les couleurs d'accent et
l'ambiance. Le catalogue réutilise les ressources existantes : 63 packs publics
couvrent les thèmes, fonds, ambiances et icônes payantes, plus six packs secrets.
Les achats à la carte restent accessibles.

## Prix et droits existants

- Remise de 25 % sur la somme des seuls éléments permanents manquants, arrondie à la flamme supérieure : `(reste * 75 + 99) / 100`.
- Les couleurs sont celles du thème inclus, ou celles de l'ambiance quand il n'existe pas de thème séparé. Dans ce dernier cas, elles ne sont pas facturées une deuxième fois.
- Un thème illustré qui contient déjà la scène du pack ne compte jamais ce fond deux fois, même lors d'un premier achat : le prix se répartit entre scène et complément de couleurs. Un ancien achat de l'un ou l'autre est déduit de cette répartition.
- Un aperçu de fond offert pendant une saison n'est pas une acquisition permanente. Un pack acheté inclut ses droits permanents.
- Solde, disponibilité saisonnière et prix confirmé sont revérifiés avant toute mutation. Un achat attribue tous les éléments et débite une seule fois ; il ne change pas le niveau.
- Les achats répétés sont gratuits. Une confirmation ancienne ne peut pas entraîner un débit supérieur au prix affiché.
- Les prix, saisons et droits secrets des 62 icônes ont une seule source dans `ShopIcons.kt`, partagée avec Android. Les montants existants sont conservés.

Aucun nouvel identifiant `pack:` n'est enregistré. La possession d'un pack est
déduite des clés déjà comprises par la synchronisation (`icon:`, `bg:`,
`theme:`, `mode:`). Aucun modèle de données ni endpoint serveur ajouté.
L'icône active reste un choix propre au lanceur de chaque appareil ; ses droits
d'achat, comme ceux des autres composants, sont synchronisés.

## Secrets et compatibilité

SCO contient l’icône SCO, Stade Raymond-Kopa et Noir & Blanc, avec les couleurs
de cette ambiance. SRFC contient l’icône SRFC, Roazhon Park et Rouge & Noir. Ils ne
s'achètent pas avant découverte et sont complets sans frais après leur surprise.

PSG, FC Barcelone (FCB) et FC Nantes (FCN) se découvrent dans la recherche avec
`psg`, `fcb` ou `fcn` (casse et espaces ignorés). Chaque découverte offre
l’icône exclusive, le stade et l’ambiance avec ses couleurs, sans achat. Le bonus
de surprise n’est attribué qu’une fois. La découverte ne change pas le thème
actif : le bouton « Appliquer le pack » permet de choisir quand l’utiliser.
Ces éléments restent cachés et ne peuvent être achetés ou offerts avant découverte.

Les illustrations restent dans leurs dossiers `backgrounds/{psg,fcb,fcn}/`.
Ce sont des scènes artistiques fixes : Parc de nuit, Camp Nou historique
avant rénovation 2023 au crépuscule, Beaujoire en fin de journée. Leurs
références, briefs et revues sont archivés dans `design/football-packs-8.8/`.

Anjou secret contient l'ambiance secrète déjà offerte ; l'icône et le fond
publics correspondants restent à compléter au tarif réduit s'ils ne sont pas
possédés. La découverte ne donne donc pas gratuitement des articles publics
auparavant payants.

Les découvertes et anciennes possessions sont reconnues, même si un ancien
journal de surprise a été tronqué. La réconciliation restaure seulement les
récompenses secrètes originales manquantes, sans rejouer le bonus de flammes.
Les noms/contenus des packs secrets non découverts sont masqués dans la boutique.

## Application et présentation

L'achat est confirmé par « Acheter et appliquer ». Un pack possédé s'applique
gratuitement. L'application règle la scène, les couleurs et l'ambiance, puis
l'icône via les alias existants. En cas d'échec du changement d'icône, une
restauration est tentée ; les droits acquis sont conservés pour réessayer.

Le pack remplace les surcharges Material You, couleurs/scènes saisonnières et
réglages locaux de visibilité/lumière du fond pour rendre ses quatre choix
visibles. Ce comportement est indiqué avant achat et application. Accessibilité,
contraste, monochrome, animations réduites et performances ne sont pas modifiés.

Les cartes utilisent les composants Braise Pop existants ; les aperçus de scène
sont statiques. Le catalogue est une liste paresseuse ; l'icône courante est lue
une fois dans la boutique et non à chaque carte. Accès depuis Boutique, Apparence,
la recherche des surprises et Profil › Collection.

## Validation

`ThemePacksTest` couvre le catalogue complet, la remise, la propriété partielle,
les anciens thèmes avec scène, le solde insuffisant, les confirmations obsolètes,
les saisons, les droits temporaires, les secrets/anciens déblocages, la
sérialisation existante et l'application des choix sans nouveau débit.

```bash
./gradlew :core:test
./gradlew :app:assembleGithubDebug :app:assembleGithubRelease :app:bundlePlayRelease \
  :wear:assembleRelease :wear:bundleRelease
./gradlew :benchmark:assemble :baselineprofile:compileNonMinifiedReleaseKotlin
```

`FootballPacksTest` couvre aussi la recherche, les droits exclusifs, le bonus
unique, la réconciliation, la persistance et le contraste des nouveaux packs.
Les rendus natifs Android/Skia et les couches vectorielles des icônes sont
inspectés localement. Ils ne remplacent pas un test du lanceur sur appareil. À vérifier sur téléphone : retour du lanceur, clair/sombre,
texte 200 %, TalkBack, scènes, comptes synchronisés et restauration après erreur.

## Variantes jour/nuit — 8.8.5

Les ambiances hors clubs, publiques et secrètes, possèdent une déclinaison claire et
sombre sous le même identifiant acheté. Le téléphone décide du mode, ou le soleil
de la ville météo lorsque « Suivre le soleil » est activé. Ce réglage reste
accessible avec une ambiance choisie. Aucun achat supplémentaire nécessaire.
Les fonds artistiques de stade conservent leur éclairage peint.

Les icônes SCO et SRFC reprennent les tracés de la flamme originale et des bandes
noir/blanc ou rouge/noir. La réconciliation les attribue aux anciens déblocages
sans rejouer le bonus de surprise ni retirer les icônes déjà possédées.

## Couleurs fixes des clubs

Exception au suivi jour/nuit : SCO, SRFC, PSG, FCB et FCN conservent leur
ambiance, indépendamment du téléphone, du soleil et de Material You. Les autres
ambiances gardent leurs variantes. Les réglages d’accessibilité restent prioritaires.

- FCN : actions jaunes et surfaces vertes.
- PSG : actions rouges, surfaces bleues et contours blancs.
- FCB : actions rouges, surfaces bleues et contours jaunes.

La sélection de ces ambiances utilise directement leur palette actuelle, y compris
lorsqu’un ancien pack avait enregistré une palette différente.

## Pack PepsGoal gratuit

Le pack PepsGoal réunit l’icône Braise, les collines braise, la palette Braise
et l’ambiance Auto native. Il est disponible à tous sans achat. Son application
rétablit `system`, `pop`, l’accent Braise natif et aucun effet additionnel. Elle
conserve le choix de suivi automatique téléphone/soleil et les réglages
d’accessibilité. Comme pour les autres packs, Material You et les surcharges
saisonnières sont désactivés pour rendre le style choisi visible. Aucun droit
acheté n’est retiré et aucun nouvel article gratuit n’est inscrit au compte.

## Boutique par catégories — 9.0.0

Les cinq rayons (packs, fonds, thèmes, ambiances, icônes) utilisent la taxonomie
partagée `ShopCategories.kt` : PepsGoal, Stades, Villes et voyages, Nature et
paysages, Espace, Abstraits et couleurs, Saisons et fêtes, Douceurs, Symboles et
objets. Les catégories vides sont omises après filtrage des secrets.

Chaque catégorie dispose d’un titre, d’un compteur et d’un carrousel horizontal
paresseux. Les clés stables de catégorie et d’article conservent leur identité
pendant les recompositions. La largeur des cartes suit la largeur disponible,
avec un aperçu de la suite du carrousel sur téléphone. La hauteur reste libre
pour accueillir les textes et contrôles. Les offres de saison passent devant les
autres articles de leur catégorie. Les aperçus restent statiques.

Les cartes et leurs actions existantes sont réutilisées : achat, application,
essai d’ambiance et cadeau. Les mêmes filtres protègent les éléments secrets.
`ShopCategoriesTest` vérifie le regroupement des clubs, les grandes familles et
la conservation de tous les articles dans le classement. Tests core et compilation
Kotlin téléphone/Wear exécutés avec succès. Sans appareil connecté, le défilement,
TalkBack et les grands textes restent à vérifier sur téléphone.
