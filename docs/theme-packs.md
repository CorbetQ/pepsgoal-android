# Packs de thème — 8.8.0

Un pack réunit l'icône du lanceur, le fond illustré, les couleurs d'accent et
l'ambiance. Le catalogue réutilise les ressources existantes : 62 packs publics
couvrent les thèmes, fonds, ambiances et icônes payantes, plus six packs secrets.
Les achats à la carte restent accessibles.

## Prix et droits existants

- Remise de 25 % sur la somme des seuls éléments permanents manquants, arrondie à la flamme supérieure : `(reste * 75 + 99) / 100`.
- Les couleurs sont celles du thème inclus, ou celles de l'ambiance quand il n'existe pas de thème séparé. Dans ce dernier cas, elles ne sont pas facturées une deuxième fois.
- Un thème illustré qui contient déjà la scène du pack ne compte jamais ce fond deux fois, même lors d'un premier achat : le prix se répartit entre scène et complément de couleurs. Un ancien achat de l'un ou l'autre est déduit de cette répartition.
- Un aperçu de fond offert pendant une saison n'est pas une acquisition permanente. Un pack acheté inclut ses droits permanents.
- Solde, disponibilité saisonnière et prix confirmé sont revérifiés avant toute mutation. Un achat attribue tous les éléments et débite une seule fois ; il ne change pas le niveau.
- Les achats répétés sont gratuits. Une confirmation ancienne ne peut pas entraîner un débit supérieur au prix affiché.
- Les prix, saisons et droits secrets des 60 icônes ont une seule source dans `ShopIcons.kt`, partagée avec Android. Les montants existants sont conservés.

Aucun nouvel identifiant `pack:` n'est enregistré. La possession d'un pack est
déduite des clés déjà comprises par la synchronisation (`icon:`, `bg:`,
`theme:`, `mode:`). Aucun modèle de données ni endpoint serveur ajouté.
L'icône active reste un choix propre au lanceur de chaque appareil ; ses droits
d'achat, comme ceux des autres composants, sont synchronisés.

## Secrets et compatibilité

SCO contient Monochrome, Stade Raymond-Kopa et Noir & Blanc, avec les couleurs
de cette ambiance. SRFC contient Nuit, Écran géant et Rouge & Noir. Ils ne
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
