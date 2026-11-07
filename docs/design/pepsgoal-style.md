# Norme de rendu « PepsGoal Style »

Référence visuelle des fonds illustrés de PepsGoal, et cahier des charges de l'IA « PepsGoal Style » qui redessine une
photo. Établie à partir des 48 fonds de l'application (`app/src/main/assets/backgrounds/*/art/artwork.png`) ; les
fonds « Bords de Loire (Saumur) » (`loire`) et « Loire en été » (`loiresummer`) servent d'étalon.

Un rendu est conforme quand il respecte les règles **obligatoires** (O) ; les règles **recommandées** (R) font la
différence entre un fond correct et un fond réussi.

## 1. Format

- (O) Portrait **2:3**, livré en 1024 × 1536 px (les fonds de l'app ont exactement cette taille).
- (O) PNG avec transparence : **le ciel est transparent**. C'est l'application qui dessine le ciel (heure réelle,
  météo, nuit, nuages) puis le fond par-dessus. 26 fonds sur 48 ont plus de 10 % de ciel transparent ; la médiane est
  de 21 %.
- (R) Exceptions : scènes nocturnes ou fantastiques dont le ciel fait partie du sujet (aurores, nébuleuse, feux
  d'artifice, fonds marins) ; leur ciel est alors peint et opaque.
- (O) Aucun texte, logo, filigrane ni signature.

## 2. Trait et formes

- (O) **Illustration vectorielle à aplats** : formes pleines, bords nets, aucun grain, aucune texture photo, aucun flou
  de profondeur de champ.
- (O) **Pas de contour noir.** Les formes se détachent par la couleur ; un liseré, s'il existe, est une teinte plus
  sombre de la forme elle-même.
- (O) **Formes simplifiées et arrondies** : feuillages en grappes de lobes ronds, nuages en boules, rochers en blocs à
  facettes douces, vagues en bandes ondulées. Un détail plus petit que 1 % de la largeur disparaît ou devient une
  simple tache.
- (R) Architecture lisible et un peu caricaturée : toits pentus accentués, fenêtres en rectangles réguliers,
  cheminées et clochers légèrement étirés.

## 3. Lumière et ombrage

- (O) **Cel shading à deux ou trois tons par objet** : lumière, ton moyen, ombre, en aplats francs (pas de dégradé
  continu sur un objet, sauf l'eau et le ciel).
- (O) Lumière **chaude et dorée** (soleil haut, de trois quarts) ; **ombres teintées bleu-violet**, jamais grises
  ni noires.
- (R) Reflets en touches claires : feuilles brillantes, arêtes des toits, éclats horizontaux sur l'eau.

## 4. Couleur

Mesures sur les 48 fonds (pixels opaques) :

| Indicateur | Médiane | Plage tolérée (10e – 90e centile) |
| --- | --- | --- |
| Saturation moyenne (TSL) | 0,57 | 0,43 – 0,66 |
| Luminosité moyenne (TSL) | 0,41 | 0,27 – 0,53 |

- (O) Couleurs **vibrantes et saturées** : la saturation moyenne d'un rendu doit tomber dans la plage ci-dessus.
  Une saturation moyenne sous 0,40 est refusée (rendu terne, « photo retouchée »).
- (O) Palettes franches et lisibles : verts jaunes à émeraude pour la végétation, bleus turquoise à cobalt pour
  l'eau, crèmes et ocres pour la pierre, toits ardoise bleu-violet ou tuiles orangées, fleurs en accents
  violets, roses ou jaunes.
- (R) Contraste chaud/froid : premier plan chaud et saturé, arrière-plan plus clair et plus bleuté (perspective
  atmosphérique douce).

## 5. Composition

- (O) **Trois plans** :
  1. **premier plan de cadrage** sur un ou deux bords : branche ou arbre penché en haut d'un côté, herbes, fleurs,
     rochers en bas ;
  2. **plan moyen** : le sujet (rivière, rue, stade, plage…) ;
  3. **arrière-plan** : un repère reconnaissable (château, clocher, montagne, skyline) sur la ligne d'horizon.
- (O) Ligne d'horizon entre 30 % et 55 % de la hauteur : le haut de l'écran reste dégagé (le ciel de l'app et les
  cartes de l'interface s'y posent).
- (R) Le sujet principal est légèrement décentré ; une diagonale (berge, route, rail, pont) guide le regard.

## 6. Ambiance

- (O) Scène **gaie et accueillante** : beau temps par défaut. La météo réelle est ajoutée par l'application, pas
  par l'image.
- (R) Personnages rares, de dos ou en silhouette simple, jamais de visage détaillé.

## 7. Application à « PepsGoal Style » (IA du serveur)

L’IA redessine la photo de l’utilisateur pour qu’elle respecte cette norme. Mise en œuvre prévue (démo en local, en attente de validation, pas encore dans l’API) :

| Règle | Mise en œuvre |
| --- | --- |
| Redessin complet (§2, §3) | Stable Diffusion 1.5 (DreamShaper 8) en image → image, force 0,8 ; la composition de la photo est tenue par ses contours (ControlNet Canny). |
| Style des fonds PepsGoal (§2 à §4) | IP-Adapter : un fond PepsGoal sert de référence de style (`loire` par défaut), posé sur le ciel de l'app pour que ses zones transparentes ne deviennent pas blanches. |
| Vitesse sur processeur | LCM-LoRA : 8 étapes au lieu de 30. |
| Couleurs vibrantes (§4) | Finition : saturation +25 %, contraste +8 %, lissage des aplats ; contrôle de la saturation moyenne (0,43 à 0,66). |
| Format 2:3 (§1) | Recadrage centré de la photo en portrait, puis agrandissement à 1024 × 1536. |
| Ciel transparent (§1) | Segmentation sémantique (UperNet ConvNeXt, ADE20K, classe « ciel »), restreinte au ciel relié au bord haut. |

Tous les modèles sont sous licence permissive (CreativeML OpenRAIL-M, OpenRAIL++, Apache 2.0, MIT) : l'usage commercial
est autorisé.

### Contrôle qualité automatique

Un rendu est rejeté (et recalculé avec une autre graine) si :
- la saturation moyenne est hors de la plage 0,43 – 0,66 ;
- la part de ciel transparent dépasse 65 % (le sujet a été pris pour du ciel) ;
- du ciel transparent apparaît dans le tiers bas de l'image.
