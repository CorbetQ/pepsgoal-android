# Validation de la refonte des fonds

Validation achevée le 4 novembre 2026 sur `feature/immersive-backgrounds`, version
8.6.0 inchangée. Les anciens changements de l'espace de travail sont préservés.

## Périmètre livré

52 fonds illustrés recréés : 45 illustrations originales acceptées et sept
compositions natives. 67 références internet documentées, 52 fiches designer et
54 masters produits avec les corrections. Les révisions retenues sont dans
`accepted.json` ; leurs fichiers distribués ont un manifeste et une empreinte.
Le fond uni et la photo personnelle conservent leur fonction.

Le critique a inspecté les masters puis les rendus Android jour/nuit en portrait
long. Une dernière revue reproduit les 8 % de réserve de parallaxe. Les refus,
corrections et visas sont consignés dans `review.md`.

## Commandes et résultats

```bash
PEPSGOAL_ARTWORK_REVIEW_DIR=/workspace/.pepsgoal/background-reboot/art \
  scripts/dev/gradle.sh :core:test :app:testGithubDebugUnitTest \
  :app:assembleGithubDebug :app:compilePlayReleaseKotlin :wear:assembleDebug
```

Les 122 tests core et 14 tests app ont passé sans échec. La première exécution
a été interrompue après les tests pendant l'assemblage. La reprise suivante
s'est terminée avec `BUILD SUCCESSFUL` :

```bash
scripts/dev/gradle.sh :app:assembleGithubDebug :app:compilePlayReleaseKotlin :wear:assembleDebug
```

Les 45 PNG embarqués dans l'APK ont été comptés et leurs SHA-256 vérifiés contre
les manifestes acceptés. `git diff --check` ne signale aucune erreur.

- APK de test : `app/build/outputs/apk/github/debug/app-github-debug.apk`
- Taille : 159920645 octets.
- SHA-256 : `8727ca747df85fad33d1bcb07b981a1b4cff33f9419d3c4eeb012bc739bab858`.
- Galerie : `app/build/reports/scenes/index.html`.
- Galerie transportable : `app/build/reports/scenes/pepsgoal-backgrounds-review.zip`.

## Limites de la validation

Les images proviennent des vrais Canvas Android via Robolectric/Skia. Aucun
téléphone ni émulateur connecté n'a été utilisé. La lisibilité avec l'interface,
TalkBack, texte 200 %, interactions, batterie et FPS restent à contrôler sur
appareil. Aucun gain énergétique chiffré n'est revendiqué.
