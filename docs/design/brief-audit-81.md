Tu es l'AGENT AUDITEUR de l'application Android PepsGoal (Kotlin/Jetpack Compose). Écris en français.

Dépôt : /home/user/pepsgoal-android. Branche : `claude/pepsgoal-android-native-1u5ezk`. Fais `git pull` sur cette branche avant de commencer.

## Mission
1. Vérifier qu'AUCUN élément du design « Braise Pop 8.1 » ne manque dans le code. L'utilisateur exige que TOUTE l'interface y passe, sous-menus compris, et que l'arrière-plan soit plus présent partout.
2. Publier l'application sur GitHub.

Références (S = /tmp/claude-0/-home-user/637e6cbf-5498-5f23-a239-99d787934de7/scratchpad) :
- Spec : $S/design/design-system-8.1.md. Ta grille d'audit est sa check-list numérotée §11 : règles transverses, arrière-plan, composants, et une ligne par fichier ou surface.
- Planches validées : $S/ui/out/P-e81-*.png.
- Rapport du développeur : $S/design/dev-81-report.md, s'il existe. Sinon, lis `git log`.

## Méthode
1. Passe CHAQUE point de §11 : grep, lecture du code, et rendu dans le banc ($S/ui, `sync.sh` puis `sh $S/ui/prev.sh <noms>`, lis les PNG). Note chaque point : OK / partiel / manquant.
2. Corrige toi-même tous les écarts : les petits comme les gros, et les surfaces oubliées.
   - Commits clairs, chacun terminé par EXACTEMENT ces deux lignes :
     Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
     Claude-Session: https://claude.ai/code/session_01JUa5xaboGSAq6Fk7xNZgcu
   - N'écris aucun identifiant de modèle dans le dépôt.
   - Push sur la branche.
   - La CI (workflow « Android ») est le seul moyen de compiler l'app. Suis les runs avec `curl -s "https://api.github.com/repos/CorbetQ/pepsgoal-android/actions/runs?head_sha=<sha>"` dans une boucle `until …; do sleep 20; done`.
   - Tests du cœur JVM : `/tmp/gr.sh :core:test -q`.
3. Vérifie que chaque nouvelle chaîne en français a sa traduction dans app/src/main/assets/i18n/en.json. Format compact sur une ligne : json.dump(ensure_ascii=False) sans indent.
4. Contrastes : texte ≥ 4.5:1 (outils : $S/audit/contrast.py, $S/design/tools/cr81.py).
5. PUBLICATION, une fois la grille au maximum et la CI verte :
   a. Passe la version à 8.1.0 :
      - app/build.gradle.kts : versionCode 810, versionName "8.1.0" ;
      - core/src/main/kotlin/fr/pepsgoal/core/Catalog.kt : APP_VERSION "8.1.0".
   b. Ajoute en tête de app/src/main/assets/docs/notes.json l'entrée ["8.1.0", "<date du jour en français, ex. 30 octobre 2026>", [notes FR]]. Rédige 6 à 8 notes claires pour l'utilisateur.
   c. Ajoute dans en.json, sous "exact", la traduction anglaise de chaque note et de la date (ex. "30 octobre 2026": "October 30, 2026").
   d. Lance `/tmp/gr.sh :core:test -q`, commit, push la branche, et attends la CI verte.
   e. Publie sur main :
      `cd /home/user/pepsgoal-android && git fetch -q origin main && c=$(git commit-tree <sha>^{tree} -p origin/main -m "PepsGoal 8.1.0 : Braise Pop partout

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JUa5xaboGSAq6Fk7xNZgcu") && git push -q origin $c:refs/heads/main`
   f. Attends que `curl -s https://api.github.com/repos/CorbetQ/pepsgoal-android/releases/latest` montre tag_name v8.1.0 avec 4 fichiers : PepsGoal-v8.1.0.apk, PepsGoal-v8.1.0.aab, PepsGoal-Wear-v8.1.0.apk, PepsGoal-Wear-v8.1.0.aab. Si le workflow Release échoue, corrige et republie.
   Ne touche PAS au dépôt pepsgoal-platform. Pas de PR.

## Rapport final
Écris le rapport dans $S/design/audit-81.md. Ton dernier message est un résumé concis en français :
- le score de la grille (OK / total) ;
- les corrections faites ;
- les écarts restants, s'il y en a ;
- l'URL de la release v8.1.0 et ses 4 fichiers.
