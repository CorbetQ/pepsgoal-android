# Corrections des fonds natifs — r2

Designer :21 vrais rendus Canvas inspectés, `samples/{blob,aurora,waves,stars,mesh,grid,sky}-{day,night,dusk}.png`. Les versions jour/nuit changent bien les couleurs utilisateur. L'absence de changement horaire des six abstraits est conforme à leur contrat artistique. Les prescriptions ci-dessous modifient la géométrie et les rapports de faces, sans toucher à la palette utilisateur ni ajouter d'image opaque.

Critique indépendante : Blob, Aurora et Mesh refusés; Stars, Grid, Waves et Sky acceptables dans une direction volontairement simple. On concentre la correction sur les trois premiers.

Les coordonnées sont des fractions de largeurW/hauteurH. Les masses proches restent lisibles en480×800 et en360×780. Utiliser les couleurs du thème pour chaque objet, une face éclaircie avec crème et une face ombrée avec prune; aucune teinte de collection codée en dur. Trois valeurs principales suffisent; on ne demande ni dégradé photographique ni blur.

## Blob — trois sculptures, et non trois pièces de monnaie

### Défaut vu

Les deux premiers objets sont des ovales aplatis à tranche mince. La grande forme orange du bas est presque entièrement une face sans modelé : son bas et sa tranche sortent du cadre. L'ombre de contact est un mince liseré; on ne lit ni vraie silhouette organique ni volume gonflé.

### Géométrie corrective

1. Petit objet éloigné : boîte x=[.61,.88], y=[.16,.30], contour asymétrique en haricot, pas ellipse. Deux lobes de taille différente reliés par une inflexion douce; face supérieure ~65% de la masse, face ombrée inférieure ~25%, fin bord de lumière sur le lobe supérieur gauche. La face sombre doit avoir une largeur visible d'au moins10pixels à480W.
2. Objet moyen : boîte x=[-.10,.48], y=[.39,.68], forme en S potelée, avec un renflement supérieur droit et un renflement inférieur gauche. Le contour droit rentre légèrement au milieu pour casser le galet régulier. La face de lumière occupe le tiers supérieur gauche; le ventre principal garde la couleur de thème; un croissant inférieur droit épais occupe25–30% du volume. Le contour sombre suit le même dessin organique, pas une ellipse déplacée.
3. Objet proche : boîte x=[.24,1.10], y=[.65,.97] (ne pas descendre le centre à.95H). Silhouette de coussin/haricot incliné, son grand lobe gauche commence vers(.30,.78), un petit lobe haut vers(.66,.65), puis il s'évase à droite. Sa bordure inférieure autour de.94H reste VISIBLE dans le cadre. Face claire x~.35–.65,y~.69–.81; face moyenne jusqu'à.88H; large face prune teintée à partir d'une courbe(.38,.84)→(.72,.89)→(1.07,.79), jusqu'au contour inférieur. Une petite face réfléchie plus claire peut suivre le bord bas sur15% de longueur, sans halo.
4. L'objet proche recouvre le coin inférieur droit du moyen sur une zone réelle d'environ.08W×.06H. Dessiner le moyen entièrement d'abord, puis une ombre courte et pleine à l'endroit où le proche le couvre, puis le proche. Cela produit une relation spatiale, pas trois stickers indépendants.

### Critère de sortie

La sculpture proche montre immédiatement AU MOINS trois faces et un bord inférieur, sans avoir besoin d'animer. On distingue une forme organique et pas un disque. Le fond demeure très calme derrière les cartes; l'objet moyen et le proche se recouvrent réellement.

## Aurora — rubans qui tournent dans l'espace

### Défaut vu

Trois bandes horizontales parallèles, de même courbure et sans croisement. Elles ressemblent à Waves et n'ont ni revers ni pli. Une tranche foncée sous chaque bande ne suffit pas à créer une torsion.

### Géométrie corrective

Deux grands rubans seulement, un petit lointain optionnel. Déplacer la narration dans une diagonale qui commence vers.35H et se développe dans les deux tiers bas; préserver le haut central.

- Ruban arrièreB, axe : partir(-.10,.35); première Bézier contrôles(.20,.25),(.30,.58), arrivée(.60,.58); deuxième Bézier contrôles(.90,.58),(.75,.82), arrivée(1.20,.85). Largeur visuelle variable ~.07H au départ et.11H en bas droit. Dessiner toutB avantA.
- Ruban avantA, axe : partir(-.15,.62); première Bézier contrôles(.18,.47),(.51,.80), arrivée(.64,.60); deuxième Bézier contrôles(.77,.40),(.84,.42), arrivée(1.18,.25). Largeur ~.10H au milieu, ~.06H vers la sortie haut droite. Son passage proche de(.62,.59) DOIT recouvrirB. Une ombre pleine courte projetée surB de.01–.02H sous le bordA explique cette occlusion.
- Construire pour chaque ruban une vraie bande fermée à deux bords non parallèles. La face principale utilise une couleur du thème; le revers plus sombre apparaît lors d'un changement d'orientation sur un tronçon de longueur.20W, et non tout le long du bord inférieur. SurA, la zone de torsion est autour de(.64,.60): le bord clair du côté haut gauche se resserre puis passe sur le bord droit de la montée; un triangle/croissant de revers prune y occupe30% de l'épaisseur.
- Le revers est caché par la face avant à l'autre extrémité du pli. Au croisement, rien du rubanB ne transparaît à traversA. Deux grandes couleurs utilisateur, plus leur version lumière/prune, suffisent. Aucun trait lumineux fin comme identité principale.

### Critère de sortie

On voit un croisement et un retournement de matière immédiatement. La silhouette générale est oblique et distincte de Waves. Les différentes faces restent compréhensibles avec une autre palette utilisateur, y compris mode sombre.

## Mesh — drapé avec plis et bord retourné

### Défaut vu

Un éventail de bandes plates converge hors cadre en haut droit. Les zones prune pourraient être d'autres bandes de couleur; aucun bord ne se retourne, aucune partie ne recouvre une autre. La composition évoque un graphique en aires plutôt qu'un tissu épais.

### Géométrie corrective

Remplacer la convergence commune par une surface continue drapée avec trois plis décalés. Leur extrémité haute n'est PAS le même point. La matière occupe surtout les deux tiers bas; sa limite haute reste courbe et irrégulière.

1. Surface support : commencer à(-.10,.54), suivre une large courbe passant vers(.20,.38),(.60,.52),(1.10,.27), puis fermer vers le bas horscadre. Une seule couleur de thème principale, calme; cette surface connecte les plis, évitant des rubans indépendants.
2. Pli gauche : naissance près(.12,.42), crête courbe qui passe(.08,.60),(.22,.86),(.10,1.10). Corps large .18–.28W; sa face sombre n'est visible qu'à droite de la crête. La face claire s'élargit puis disparaît sous le grand pli central versy=.77.
3. Grand pli central : naissance près(.32,.30), crête en S douce passant(.28,.55),(.72,.60),(.70,1.10). Bande de matière large .26W au milieu et.38W en bas. Construire une crête éclairée étroite de.03–.05W, une grande face médiane, puis une vallée prune dont la largeur varie .06–.12W; cette vallée est une région fermée de surface, pas un trait. Le côté gauche recouvre le pli gauche vers(.31,.74), avec ombre de contact courte sur le dessous.
4. Pli droit : naissance près(.75,.38), suit(.80,.60),(.70,.90),(1.10,.98). Il se lit par une face moyenne et un creux plus sombre, partiellement caché par le pli central. Ses coordonnées et sa largeur différentes empêchent la répétition d'éventail.
5. **Retournement proche obligatoire** : une large lèvre recourbée traverse le bas droit. Son bord supérieur part(.38,.86), courbe vers(.58,.80),(.98,.79),(1.10,.96). Le dessous revient par(1.10,1.10),(.63,1.04),(.50,.96). Face externe claire, sous-face prune pleine, ombre projetée étroite sur le drapé sousjacent. Le bord doit produire une épaisseur évidente de.025–.04H, non une rayure supplémentaire. Cette lèvre recouvre les fins des plis, créant l'avantplan.
6. Deux ou trois couleurs utilisateur sont suffisantes; leurs faces dérivées font le volume. Ne pas alterner une nouvelle couleur saturée sur chaque minuscule bande.

### Critère de sortie

Le retournement de bord et l'occlusion des plis se lisent sans explication. Aucun point unique ne réunit toutes les bandes; les plis ont des naissances et largeurs distinctes. Au moins une grande face reçoit la lumière, une autre tourne dans l'ombre. Le haut central garde son calme.

## Révision et preuves attendues

Rendre ces trois scènes jour et nuit après modification, plus le portrait long. La première galerie était un essai de construction; un test vert n'en faisait pas une validation de qualité. Le critique réévalue les images après correction. Stars/Grid/Waves/Sky n'ont pas de demande de complexité supplémentaire à ce stade.
