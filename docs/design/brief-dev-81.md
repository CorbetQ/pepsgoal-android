Tu es l'AGENT DÉVELOPPEUR de l'application Android PepsGoal (Kotlin/Jetpack Compose). Écris en français.

Dépôt : /home/user/pepsgoal-android. Branche : `claude/pepsgoal-android-native-1u5ezk`. Fais `git pull` sur cette branche avant de commencer. Ne pousse JAMAIS sur `main` : c'est l'auditeur qui publiera. Pas de PR.

## Mission
Implémenter INTÉGRALEMENT le design « Braise Pop 8.1 », validé par l'utilisateur. L'utilisateur a été clair : « faut pas faire le travail à moitié, TOUTE l'interface doit y passer, peu importe les sous-menus, et l'arrière-plan doit être plus présent dans l'application ».

Sources de référence (S = /tmp/claude-0/-home-user/637e6cbf-5498-5f23-a239-99d787934de7/scratchpad) :
- Spec : $S/design/design-system-8.1.md. Lis-la en entier. Les sections clés sont le plan fichier par fichier (§9), l'ordre des 11 lots (§10) et la check-list d'audit (§11).
- Spec de base 8.0 : $S/design/design-system.md.
- Esquisse, déjà codée en Compose Desktop : $S/ui/src/main/kotlin/e81/Kit.kt et Screens.kt. Réutilise ce code autant que possible en l'adaptant à l'app.
- Planches validées : $S/ui/out/P-e81-*.png.

## Méthode
1. Suis les 11 lots de §10 dans l'ordre. Après chaque lot :
   - compile ;
   - commit avec un message clair qui se termine par EXACTEMENT ces deux lignes :
     Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
     Claude-Session: https://claude.ai/code/session_01JUa5xaboGSAq6Fk7xNZgcu
   - push la branche (`git push -u origin claude/pepsgoal-android-native-1u5ezk`).
   N'écris aucun nom ni identifiant de modèle dans le dépôt.
2. Compilation : l'app Android ne compile QUE dans la CI GitHub (workflow « Android » sur la branche). Pour suivre un run, interroge l'API publique :
   - liste : `curl -s "https://api.github.com/repos/CorbetQ/pepsgoal-android/actions/runs?head_sha=<sha>"` ;
   - pour attendre la fin, utilise une boucle `until ...; do sleep 20; done`.
   Si un run échoue, récupère les logs (outils mcp__github__get_job_logs via ToolSearch si disponibles, sinon l'API), puis corrige. Tests du cœur JVM en local : `/tmp/gr.sh :core:test -q`.
3. Vérifie le rendu avec le banc Compose Desktop $S/ui :
   - `sync.sh` copie les fichiers de l'app dans le banc ;
   - le rendu se lance avec `sh $S/ui/prev.sh <noms>` ;
   - lis les PNG produits pour contrôler.
4. Couvre 100 % des surfaces de l'inventaire. Ensuite, grep pour confirmer qu'il ne reste plus, dans l'UI de l'app : Checkbox, RadioButton, TextField/OutlinedTextField Material, Slider Material, CircularProgressIndicator/LinearProgressIndicator, ModalBottomSheet, AlertDialog, DatePickerDialog/TimePickerDialog, DropdownMenu, `blur`, haze, ombres Material, ni l'ancienne palette (widgets compris).
5. i18n : toute nouvelle chaîne en français doit avoir sa traduction anglaise dans app/src/main/assets/i18n/en.json, au format {"exact":{fr:en},"tmpl":[[fr,en]]}. Garde le format JSON compact sur une ligne : json.dump(…, ensure_ascii=False) sans indent. Un extracteur existe : $S/extract.py.
6. Migrations de préférences (Storage.kt) : applique-les exactement comme la spec les décrit (mode auto supprimé, flou de l'image perso converti en voile, fond `blob` remplacé par « Collines braise », etc.).
7. Ne change PAS la version (elle reste 8.0.0) et n'ajoute pas les notes de version : l'auditeur s'en charge.

## Rapport final
Termine avec la CI verte sur ton dernier commit. Ton dernier message est un rapport concis en français :
- les lots faits et leurs commits ;
- le SHA final et le run CI vert ;
- la liste HONNÊTE de ce qui n'a pas été fait ou a été fait partiellement.
Écris aussi ce rapport dans $S/design/dev-81-report.md pour l'auditeur.
