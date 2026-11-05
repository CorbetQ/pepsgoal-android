# Résultats exécutés le 03/11/2026

Base : `d7f4b14`, branche `perf/energy-audit`. Aucun serveur PepsGoal contacté
pour les tests : HTTP synthétique sur 127.0.0.1, données et clés de test.
PEPSGOAL_IT_API absent ; ServerIT retourne sans parcours distant.

## Vérifications

| Vérification | Résultat |
| --- | --- |
| `:core:test` | **Succès : 93 cas JUnit, 0 échec, 0 erreur**, dont 20 nouveaux tests (15 réseau/énergie/E2E + 5 persistance). ServerIT est compté mais non exercé. |
| `:app:assembleGithubDebug` | **Succès**, compilation, dexing et APK généré. |
| `:wear:assembleDebug` | **Succès**, APK généré. |
| `:benchmark:compileBenchmarkKotlin` | **Succès**, PowerMetric optionnel et comparaison sans profil inclus. |
| `:baselineprofile:compileNonMinifiedReleaseKotlin` | **Succès**, aucune génération de profil prétendue. |
| `:wear:lintDebug` | **Succès**, dans la passe lint complète. |
| `:app:lintGithubDebug` | **Échec : 19 erreurs, 241 avertissements, 8 indications**. Les lignes incriminées des 19 erreurs sont présentes dans la base `d7f4b14`. Aucune baseline ou suppression ajoutée. |
| `git diff --check`, syntaxe Bash, compilation syntaxique Python | **Succès**. |
| Inspection ZIP APK debug | **Réalisée** ; aucun profil dexopt dans cette variante debug. Ne démontre rien sur les profils de la variante release distribuée. |

Dernier build, après les corrections finales, **succès en 53 s** :

```bash
PEPSGOAL_WORKERS=1 scripts/dev/gradle.sh \
  -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8' \
  -Pkotlin.daemon.jvmargs=-Xmx2g \
  :core:test :app:assembleGithubDebug :wear:assembleDebug \
  :benchmark:compileBenchmarkKotlin :baselineprofile:compileNonMinifiedReleaseKotlin
```

Workers/mémoire limités uniquement pour cet hôte ; aucun fichier de versions ou
configuration Gradle changé. Une première passe lint a perdu son daemon sous
pression mémoire ; la suivante a produit le rapport complet ci-dessus. Le
dernier build ne relance pas le lint bloqué par le code préexistant.

Erreurs lint : NewApi dans LiveUpdates (9), StartActivityAndCollapseDeprecated
dans Focus (2), SuspiciousIndentation dans ChatScreen (1),
ProduceStateDoesNotAssignValue dans Root (1), RememberReturnType dans
ChatScreen/Root/SettingsScreen (3), ByteOrderMark dans Exports (3).
Lignes comparées à `git show d7f4b14:CHEMIN` ; pas de comparaison de deux
rapports lint exécutés dans deux worktrees. Aucun lint téléphone vert revendiqué.

## Preuves comportementales ajoutées

- Relève complète : 11 requêtes au lieu des 15 d'origine, hors E2E/modération ; stable : quatre probes ; conversation seule : quatre probes + un historique.
- Fenêtre de succès/collisions ; annulation ou erreur permettant une nouvelle tentative.
- Annulation avant réponse et pendant un body partiel sans faux signal de panne.
- Résumés stables sans retéléchargement ; nouveau message conservé, ordonné et lu à l'ouverture.
- Chat stable sans écriture ; contenu chiffré connu déchiffré sans nouveau save.
- Upload E2E au coffre annulé avec son parent ; clés locales durables et upload réessayable.
- Snapshot rempli à partir de tout le schéma sérialisable, égalité des champs et isolation des structures mutables.
- Écritures hors thread appelant, ordre, déduplication, comptes, suppression, erreurs disque réessayables, sandbox sans écriture.
- Politique thermique ; démarrage local invisible et conversation de démonstration sans réseau.

Ces tests ne mesurent pas la batterie. Ils n'établissent pas un gain énergétique
proportionnel aux requêtes éliminées.

## Non exécuté / à qualifier

Aucun appareil connecté ni KVM. Aucune capture ou preview inspectée ; aucune
comparaison visuelle, interaction, TalkBack, pp/h, ODPM, FrameTimeline,
température ou durée de wake lock physique. Health Connect, SAF, localisation,
Wear/Play Services, Doze et IA ARM64 restent à tester sur matériel adapté.

Play/release non reconstruits localement dans cette campagne ; la CI existante
les construit après push. Les profils propres aux parcours, leur installation
effective et la comparaison avec/sans profil restent à mesurer. Le benchmark
ODPM et les scripts sont fournis/compilés, mais non exécutés sur matériel.
Aucune campagne énergétique périodique n'est déclarée réalisée : suivre S0–S13
dans `energy-audit.md` sur deux téléphones, au minimum pour chaque release
touchant ces chemins.

Une fermeture brutale avant achèvement d'un commit asynchrone peut perdre ce
dernier commit ; barrières et AtomicFile réduisent les risques, sans remplacer
un essai de coupure réelle. Le snapshot et le cache de lecture consomment une
mémoire proportionnelle à la base : mesurer sur un historique volumineux.
