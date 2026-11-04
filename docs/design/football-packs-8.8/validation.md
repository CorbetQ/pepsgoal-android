# Validation 8.8.0 — 4 novembre 2026

## Livraison locale

Trois packs secrets PSG, FC Barcelone et FC Nantes : icône adaptative et
monochrome, illustration originale de stade, couleurs et ambiance. Découverte
par recherche exacte, bonus unique, application explicite, aucun achat ni cadeau
permettant de contourner la découverte. Version téléphone 880 / 8.8.0 ; Wear
reprend automatiquement cette version. Notes ajoutées sans altérer l’historique.

## Contrôles exécutés

Via `scripts/dev/gradle.sh`, avec `PEPSGOAL_WORKERS=1` :

- `:core:test` : 129 tests réussis, dont 7 tests spécifiques football.
- `:app:compileGithubDebugKotlin` : réussite.
- `:app:testGithubDebugUnitTest` : 14 tests réussis, 1 test optionnel de staging
  ignoré normalement. Le test de staging a été exécuté séparément avec succès
  pour les trois nouveaux masters avant leur import.
- `:app:assembleGithubDebug` : réussite, APK `8.8.0-debug`, code 880.
- `:wear:compileReleaseKotlin` : réussite.
- `git diff --check` : réussite.

L’APK a été inspecté avec aapt et zipfile : version correcte, trois PNG avec
SHA256 conformes aux manifestes acceptés, propriétés/animations et notes 8.8.0
présentes. Les sources photo ne sont pas embarquées.

## Revue visuelle

Le visa indépendant `accepted.json` porte sur les trois masters, les références
et les rendus natifs Android/Skia 360×780 avec surcadrage de 8 %. Le principal a
aussi ouvert les trois rendus longs et les six icônes couleur/monochrome rendues
sur Canvas natif. Aucun nouveau recours à la génération d’image n’a été nécessaire
pour cette reprise. Les illustrations et leurs références existantes ont été
réutilisées.

Ces scènes ARTISTIC n’injectent ni météo ni heure réelles. Le test vérifie que
leur rendu reste identique jour/nuit. Aucune surcouche animée permanente ajoutée.

## Limites

Sans appareil Android connecté : interactions du lanceur, interface superposée,
TalkBack et texte 200 % restent à vérifier sur téléphone. Pas de mesure de
batterie ou de fluidité revendiquée. Les APK/AAB release et la publication GitHub
ne font pas partie de cette validation locale.

## Coût des agents

Tests métier ciblés : `gpt-6-luna`, contexte borné sans historique. Revue visuelle
indépendante : `gpt-6.1-sol`, contexte borné sans historique. Les anciennes
recherches et images sont conservées. La préférence de routage est inscrite dans
les consignes locales `AGENTS.md`. Aucun montant ou pourcentage d’économie n’est
revendiqué : les crédits facturés ne sont pas exposés ici.
