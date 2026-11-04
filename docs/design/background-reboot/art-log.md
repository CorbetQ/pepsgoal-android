# Journal de production artistique

## Contrat de production

Les images sont de nouvelles illustrations, produites après inspection des références de recherche et lecture des briefs du designer. Les photographies tierces restent hors des assets de production.

- Staging : `/workspace/.pepsgoal/background-reboot/art/<id>/`.
- Fonds extérieurs : portrait 1024 × 1536 visé, décor avec ciel transparent ; le moteur conserve le ciel, la météo et le temps réels.
- Fonds artistiques : illustration complète, éclairage propre au thème.
- Chaque première version est soumise à l’agent critique avant intégration. Une compilation seule n’est jamais une validation artistique.

## Références déjà inspectées par l’artiste

- `kopa/01.jpg` : ancienne tribune Saint-Léonard du stade d’Angers, toiture fine sur poteaux, gradins noirs et blancs, spectateurs et clôture de terrain. La référence 02, une couverture de livre sans lien, a été signalée et rejetée.
- `loire/01.jpg` : Blois, pont à nombreuses arches, quai de pierre, ville sur la rive, profondeur large du fleuve et reflet horizontal.
- `loire/02.jpg` : Saumur, château haut à tourelles, façades de tuffeau sur le coteau, gabarres de bois au premier plan.

## État

### Calibration v1 — produite, en attente de critique

Deux appels indépendants parallèles à `image_gen__imagegen`, durée totale observée 107 secondes. Les descriptions des références réellement inspectées ont complété les prompts initiaux du designer; pas d’affirmation d’architecture actuelle2026 à partir de l’ancienne photo de tribune.

| Fond | Master staging | Format | Poids | Statut |
|---|---|---|---|---|
| Kopa | `/workspace/.pepsgoal/background-reboot/art/kopa/v1.png` | 1024×1536 RGB | 2193278octets | Critique demandée |
| Loire | `/workspace/.pepsgoal/background-reboot/art/loire/v1.png` | 1024×1536 RGBA | 2849738octets | Critique demandée |

L’alpha de Loire va de0 à253 : vraie transparence présente, mais l’image reste légèrement semi-opaque partout. Son intégration sur ciel réel et sa tenue de nuit doivent être vérifiées avant acceptation. Le style et la perspective restent soumis au critique; aucune approbation automatique.

Les fichiers originaux de l’outil sont conservés dans `/workspace/generated_images/`. Les prompts utilisés sont archivés dans le staging `calibration-prompts-v1.json`.


### Lot1 — huit masters produits

`nebula`, `deepsea`, `mountains`, `citynight`, `tram`, `mistforest`, `dunes`, `sakura` : fichiers `art/<id>/v1.png` dans le staging. Tous transmis au critique après reprise de la cellule; aucun visa de conformité automatique. Les six planches de références finales ont été effectivement vues par l’artiste.


### Production après reprise

Kopa v2 et Loire v2 ont été acceptés artistiquement par le critique; Loire demande un recentrage ciblé v3 pour téléphone long. Tram v2 corrige le style refusé de v1, accepté artistiquement; cadrage natif encore à vérifier.

Lot2 : magma, coderain, borealis, rainwindow, planet, lagoon, lighthouse, vineyard; génération lancée avec Loire v3 en parallèle. Rainwindow v1 refusé pour une zone de ciel bleu semi-opaque réelle : édition ciblée requise.

Lot3 : balloons, jellyfish, bamboo, nighttrain, rooftops, canyon, savanna, lanterns. Lot4 : icefield, tunnel, angers, nantes, angevine, haunted, snowvillage, fireworks. Les sorties sont transmises au critique au fil de leur arrivée. Les compléments Angevine Layon et moulin cavier ont été réellement inspectés avant production.


### Fin de première production du catalogue raster

Les45fonds destinés à une illustration raster possèdent maintenant au moins un master1024×1536 réellement généré. Les7autres sont conçus en formes natives à partir de `native-art-direction.md` pour préserver la recoloration et le ciel vivant. Les variantes `plain` et `custom` restent hors remplacement illustré.

- Lot5 : snowpeak, heartsky, eggmeadow, flowerfield, summerbeach, autumnwood, xmastree, valentine, tous v1.
- Lot6 : loiresummer, ecran, pop, tous v1; rooftops v2 en correction ciblée.
- Loire v3 corrige le cadrage château/gabare indépendamment; son master et ses rendus longs jour/nuit ont été réellement inspectés puis acceptés par le critique.
- Rainwindow v2 supprime par édition imagegen le ciel bleu peint et conserve son cadre/plante; nouvelle revue demandée.
- Ecran v1 est auto-signalé par l’artiste : présence indésirable d’un score peint4/0 malgré consigne aucuntexte, et détail trop réaliste. Une correction v2 est demandée au designer et au critique. Ce master ne doit pas être considéré final.

Le staging contient `master-inventory.json` : chemins, dimensions, mode, octets et SHA256 des masters; `generated-origin.json` conserve les chemins originaux exposés par l’outil pour les sorties retrouvées dans la session. Les fichiers originaux imagegen restent présents hors dépôt. Les visas artistiques et runtime restent consignés indépendamment dans `review.md`; le nombre d’images produites ne vaut pas approbation.


### Corrections finales soumises au critique

Les54masters des45fonds raster sont archivés, originaux imagegen retrouvés par égalité SHA256 dans `generated-origin.json` et `master-inventory.json`. Aucune photographie tierce n’est devenue asset de production.

Versions proposées après les corrections réellement effectuées : `kopa/v2`, `loire/v3`, `tram/v2`, `rainwindow/v2`, `rooftops/v2`, `snowpeak/v2`, `loiresummer/v2`, `ecran/v2`; les37autres fonds ont un masterv1. Le critique conserve la décision d’acceptation de chaque version; l’artiste n’a pas auto-approuvé ses corrections.

Ecran v2 dispose d’un panneau entièrement vide, sans score peint, et de sièges/reflets plus francs. Snowpeak v2 simplifie les faces neige/roche en conservant les crêtes et la corniche. LoireSummer v2 simplifie les matières et recentre château/bateau indépendamment. Toutes ces sorties ont été transmises à l’agent critique et à l’intégrateur; aucun appel de génération ne reste en attente à cette étape.
