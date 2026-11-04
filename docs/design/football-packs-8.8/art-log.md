# Production artistique — secrets football 8.8

Créations originales en staging hors dépôt, sans remplacement des fonds existants. Les photographies de recherche servent à comprendre les structures; elles ne deviennent pas des assets de production. Toute intégration attend un visa du critique sur les images produites.

## Références effectivement inspectées par l’artiste

- PSG : intérieur du Parc des Princes2020, enceinte compacte à couverture continue, bandeaux de gradins rouge/bleu, vues proches du terrain; extérieur2017 avec contreforts de béton et quartier dense. Les inscriptions des photographies ne doivent pas être peintes dans l’illustration.
- FC Barcelone : Camp Nou2019 en vue plongeante, cuve profonde à trois grandes strates de gradins, partie opposée largement ouverte au ciel et projecteurs en crête; panorama2017 montrant une couverture limitée à la tribune principale. Cette direction illustre explicitement le Camp Nou historique avant rénovation2023, sans revendiquer le stade actuel.
- FC Nantes : Beaujoire2016, grande toiture couverte ondulée et gradins vert/jaune autour d’un terrain bas; extérieur2022 montrant les nervures et portées en béton. Ces courbes doivent distinguer la scène d’une simple recoloration des autres stades.

## Contrat

Master portrait1024×1536, composition artistique complète opaque. Grandes masses cartoon et deux ou trois faces de lumière/ombre par matière, ombres prune, au moins trois plans. Identité essentielle dans la zone centrale x20–80% pour recadrage téléphone. Aucun texte, logo, score, symbole d’affichage ou watermark peint. Les détails fins et reflets photographiques sont exclus.

## État

Les trois prompts du designer `prompts/{psg,fcb,fcn}.txt` ont été lus et archivés dans le staging `art/prompts-v1.json`. Trois appels indépendants parallèles à `image_gen__imagegen` ont démarré. Chaque sujet possède une composition et une structure propre; aucune recoloration d’un stade existant n’est utilisée comme livraison. Résultats encore en attente à cette étape.


## Première production — v1

Les trois appels ont produit de véritables images 1024×1536, copiées depuis les originaux de `/workspace/generated_images/` vers le staging `art/{psg,fcb,fcn}/v1.png`. Le manifeste `art/master-inventory.json` associe chaque copie à son original par SHA256, avec dimensions, mode et poids. Les originaux restent conservés.

Inspection de l’artiste : PSG présente une couronne couverte continue et deux niveaux compacts dans une nuit bleu profond; FCB montre une vue plus haute dans trois niveaux ouverts avec ciel crépusculaire et masses blaugrana; FCN offre une vue basse, une toiture ondulée nervurée et une ambiance jaune/vert claire. Les trois compositions ne sont pas des recolorations de la même arène. Aucun texte, logo ou score visible dans ces masters.

Les masters et les prompts exacts ont été transmis à l’agent critique pour confrontation indépendante aux références. Aucun visa final ni copie de production à cette étape; cadrage portrait long et débordement restent à juger dans le moteur Android.
