# Bibliothèque de fonds PepsGoal

Chaque fond garde sa clé stable et son propre dossier. Les achats, secrets et packs
utilisent ces clés ; la refonte visuelle conserve leurs droits et leurs prix.

```text
backgrounds/
  catalog.json
  _shared/                      # primitives Canvas, ciel et mouvements communs
  loire/
    properties.json             # compatibilité, cadrage, réglages des couches natives
    animations.json             # cycle, FPS, phase, surcouche et budget de particules
    art/
      artwork.png               # illustration originale acceptée, ciel alpha si extérieur
      manifest.json             # révision, provenance de recherche, SHA-256 et visa visuel
    layers/
      illustration.layer.kts    # composition Canvas et repli historique
      sky.layer.kts             # fonctions de ciel
      details.layer.kts          # détails spécifiques, par ex. score natif pour ecran
      foreground.layer.kts
      lighting.layer.kts
```

Les 45 décors peints utilisent une illustration originale. Les six abstraits
(`blob`, `aurora`, `waves`, `stars`, `mesh`, `grid`) et `sky` sont
recréés en Canvas pour garder leurs palettes et leur ciel vivants. Ils n'exigent
pas de PNG opaque. `plain` utilise `layers/surface.json` ; `custom` lit la
photo choisie sur l'appareil, qui reste hors des assets.

Les `layer.kts` sont des sources compilées par `generateBackgroundAssets`.
Éditer ces sources, jamais le fichier généré sous `app/build/generated/`.
Aucun interpréteur ni téléchargement d'art n'est utilisé dans l'application.

## Modifier un décor peint

1. Lire son brief dans `docs/design/background-reboot/briefs/<clé>.md` et ses
   références traçables. Les photos de recherche sont conservées hors dépôt.
2. Produire une nouvelle illustration cartoon : silhouettes nettes, plans
   distincts, faces de lumière/ombre et matière sélective. Les sujets importants
   restent dans les 20–80 % centraux pour le téléphone 9:19,5.
3. Soumettre le master et les rendus Android jour/nuit au critique. Les extérieurs
   ont un vrai ciel alpha ; la fenêtre adapte aussi son exposition. Les ambiances
   artistiques gardent leur lumière propre.
4. Consigner la révision acceptée dans
   `docs/design/background-reboot/accepted.json`, puis exécuter
   `python3 scripts/dev/import-background-art.py`. Le script copie exactement le
   master accepté et écrit son manifeste avec empreinte SHA-256.
5. Ajuster `framing.focusX` (0 à 1, défaut 0,5) et `focusY` dans
   `properties.json`, puis revoir le cadrage. Les couleurs de l'illustration sont
   celles du master ; les paramètres pigments/matière concernent les couches Canvas.
6. Régler les mouvements dans `animations.json` : `overlay` et
   `overlayCount`, cycle et cadence. Les règles de pause, mouvement réduit et
   économie restent obligatoires. Les glyphes, gouttes, lanternes ou score sont
   dessinés nativement au-dessus du décor ; le score d'Ecran suit son panneau peint.

## Modifier un fond natif

Éditer ses formes dans `layers/`, puis rendre la palette claire et sombre.
Le volume vient des faces, plis, tranches et occlusions. Les six abstraits gardent
les couleurs du thème utilisateur. Le soleil, la lune et les nuages de `sky`
restent pilotés par l'état météo/solaire commun.

## Vérifier

```bash
scripts/dev/gradle.sh :app:testGithubDebugUnitTest
scripts/dev/check.sh apk

# Revue temporaire des candidats, sans les distribuer :
PEPSGOAL_ARTWORK_REVIEW_DIR=/workspace/.pepsgoal/background-reboot/art \
  scripts/dev/gradle.sh :app:testGithubDebugUnitTest \
  --tests '*SceneRenderTest.renderArtworkCandidatesWhenRequested'
```

La galerie hors connexion est dans `app/build/reports/scenes/index.html`.
Les détails jour/nuit/crépuscule sont au format 360×780 ; huit planches couvrent
aussi aube, pluie, neige, brouillard et orage. `candidates/` contient les masters
de staging recomposés par Android et les planches de critique en portrait long.

Les tests vérifient catalogue, propriétés, isolation des palettes, intégrité des
révisions et contrats de rendu. Les captures Canvas ne remplacent pas la revue
sur téléphone avec l'interface, TalkBack, texte 200 %, mouvements réduits et batterie.
