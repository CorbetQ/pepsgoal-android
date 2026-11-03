# Audit énergétique : corrections et validation

Référence de l'audit fourni : Android `d7f4b14` (8.5.4), document
« PepsGoal_Audit_energetique_avec_essais_Android ». Contrats serveur vérifiés en
lecture seule dans `pepsgoal-platform@3971042`, routes messages/groups et docs/API.md.
Aucune API, migration, signature, permission ou version n'a été modifiée.

## Suivi des 13 constats

| Constat | Correction client | Preuve / limite |
| --- | --- | --- |
| F01 — Amplification sociale | Réutilisation des quatre réponses de relève ; réconciliation limitée aux conversations/groupes quand seuls ceux-ci changent ; comparaison de valeurs plutôt qu'un hash incomplet. Une erreur serveur sur une ressource facultative n'est plus interprétée comme une liste vide (sauf 404 de compatibilité). | Tests locaux : cycle complet 11 requêtes au lieu de 15, cycle stable 4, changement de conversation 5. Hors appels E2E/modération. Le push et un nouveau curseur ne sont pas inventés. |
| F02 — Planifications concurrentes | Mutex et fenêtre monotone de succès dans Cloud, communs à tous les appelants ; premier plan/alarme/service/worker passent par Poller. Boucles de premier plan sous repeatOnLifecycle. Aucun wake lock ajouté au premier plan. | Tests de collision et d'annulation ; alarmes utilisateur précises conservées. Le service instantané et son filet inexact restent disponibles. |
| F03 — HTTP/annulation | OkHttp enqueue, annulation reliée à Call.cancel jusqu'à la fin de lecture du body ; callTimeout global 25 s (120 s pour upload interactif), status 6 s, budget Poller 30 s sous verrou expirant à 40 s. Refresh social/sync et envois E2E au coffre rattachés à leur opération parente ; uploads du coffre sérialisés. | Tests réseau isolé lent, body partiel, libération de la relève et absence de faux signal de panne. Tests des jetons à usage unique. Durée de wake lock à contrôler sur téléphone. |
| F04 — Disponibilité | Pas de ping périodique indépendant quand le serveur est sain ; statut/maintenance vérifiés dans la relève au plus une fois par minute. Reprises de panne avec jitter, annulées en arrière-plan ; reprise au callback réseau. Pas de réseau de boot sur un démarrage local/worker invisible. | Politique inspectée ; campagne longue hors ligne/reconnexion à réaliser. Les métadonnées de maintenance restent actualisées. |
| F05 — Conversation | Relève seulement RESUMED, y compris ChatState et lecture locale ; résumé existant toutes les 6 s, historique uniquement sur changement, ouverture/reprise ou réconciliation de sécurité à 30 s. Pas de sauvegarde ni de déchiffrement systématique pour une liste identique. | Tests : historique stable, nouveau message, ordre, état lu et disparition de messages. Les endpoints actuels ne proposent que `before`, pas `since`. Les corrections historiques non reflétées dans un résumé peuvent prendre jusqu'à 30 s ; les nouveaux messages conservent la détection à 6 s. |
| F06 — Persistance | Snapshot profond sur le thread propriétaire, sérialisation et écriture sur IO ; file ordonnée, regroupement du travail déjà en attente, déduplication des contenus, AtomicFile Android. Barrières flush pour arrière-plan, workers, actions receiver/widget/montre. Secrets toujours synchrones et durables ; accès au cache des secrets sérialisés. | Test de tous les champs sérialisables, absence d'alias, ordre, comptes, suppression, erreurs/reprise et sandbox. Aucune sérialisation JSON de la base vivante en IO. La capture reste O(taille de la base) sur Main. Une fermeture brutale avant la fin d'un commit asynchrone peut perdre ce dernier commit ; la dernière écriture achevée reste atomique. Essai de coupure réelle encore requis. |
| F07 — Rendu | Garde thermique événementielle, réduction temporaire sous les plafonds utilisateur ; surcouches météo plafonnées davantage uniquement en chauffe. Presets normaux, palette, scène, ressorts et animations des interactions conservés. | Politique thermique testée en JVM ; rendu, récupération thermique et captures à vérifier sur appareil. Aucun gain GPU/énergie supposé. |
| F08 — FPS | Indicateur présenté comme une estimation de disponibilité du fil principal, pas comme des frames réellement présentées. FrameTimingMetric demeure l'outil de mesure. | Texte et documentation corrigés ; comparaison moniteur/FrameTimeline à réaliser. |
| F09 — IA | Installation conseillée Wi-Fi + charge avec choix explicite de continuer ; téléchargements annulables jusque dans le body. Calcul de fond interdit hors visibilité, batterie faible, économie ou chauffe ; au plus une tentative par fonction/5 min. Arrêt natif à la limite de caractères, timeout et protection thermique ; attente de fermeture avant réutilisation du moteur. | API MediaPipe 0.10.24 vérifiée : pas de setMaxOutputTokens par session ; setMaxTokens borne le contexte total, le budget de sortie est un arrêt natif sur caractères, pas une promesse de jetons exacts. Si le natif tarde à fermer (>5 s), une nouvelle génération est refusée jusqu'à sa fermeture. Validation ARM64 requise. |
| F10 — Widget/Wear | Rafales regroupées sur 200 ms sans report indéfini ; seuls les widgets installés sont sollicités ; signature métier sans révisions chat. Payload Wear comparée sans timestamp ; succès attendu avant mémorisation ; urgent réservé aux actions interactives. Un seul chemin de publication pour une validation montre. | Retour montre forcé explicite sur demande de rafraîchissement. Data Layer reçoit aussi les nouveautés pour une montre déconnectée afin de les livrer à la reconnexion : ne pas filtrer aveuglément sur connectedNodes. Essais Play Services/Wear requis. |
| F11 — Workers/backup | Rappels locaux et Health indépendants du réseau ; worker réseau CONNECTED, supprimé sans compte/en mode hors ligne. Backup quotidien flexible, batteryNotLow/storageNotLow, sans contrainte réseau injustifiée sur un dossier SAF local ; configuration absente = succès sans reprise. Payload depuis snapshot, JSON/chiffrement hors Main. | Compilations/lint ; batterie/stockage/fournisseur SAF et reprise de copie à tester sur téléphone. |
| F12 — Localisation/santé/écran | Acquisition GPS ponctuelle annulable et bornée à 20 s, précision fine conservée pour les petites zones ; métriques identiques regroupées par jour dans une synchronisation santé exclusive. Option Braise Pop pour laisser l'écran s'éteindre ; pause libère keepScreenOn. | Géorepérage système conservé, aucune boucle GPS ajoutée. Option écran allumé conservée par défaut. Signaux d'intervalles en veille/Health Connect et geofences à qualifier sur matériel. |
| F13 — Preuves | Benchmark optionnel ODPM avec vérification de support/opt-in, comparaison cold start sans profil, collecte début/fin, inspection des profils APK et matrice de campagne ci-dessous. | Modules compilés ; aucune consommation physique, mesure ODPM ou campagne appareil n'a été exécutée dans cet hôte sans appareil/KVM. Les profils de dépendances ne prouvent pas une couverture des parcours PepsGoal. |

## Commandes de validation

JDK 17 / SDK 36 / Gradle wrapper 8.14.3, mêmes versions que la base.

```bash
./gradlew :core:test
./gradlew :app:assembleGithubDebug :wear:assembleDebug \
  :benchmark:compileBenchmarkKotlin :baselineprofile:compileNonMinifiedReleaseKotlin
./gradlew :app:lintGithubDebug :wear:lintDebug
python3 scripts/energy/inspect-profile.py app/build/outputs/apk/github/debug/app-github-debug.apk
```

Dans l'environnement préparé, `scripts/dev/gradle.sh` charge les outils et le proxy
locaux ; ces scripts de configuration préexistants ne font pas partie de cette PR.
Voir les résultats exécutés dans `energy-validation.md`.

## Campagne physique à exécuter avant toute annonce d'autonomie

Deux téléphones représentatifs, même build/compilation, mêmes comptes de test,
réseau, luminosité, fréquence écran et température initiale. Au moins trois
répétitions alternées avant/après ; un seul facteur modifié à la fois. Aucun
compte de production et aucun polling ADB continu pendant les phases de veille.

| Scénario | Charge contrôlée | Durée proposée |
| --- | --- | --- |
| S0 | Témoin inactif, écran éteint | 8 h |
| S1 | Profil local, veille | 8 h |
| S2/S3 | Compte calme, instantané activé/désactivé | 8 h par condition |
| S4 | Nouveautés sociales à rythme fixé | 2 h |
| S5 | Nuit locale, sans activité récente | 6 h |
| S6/S7 | Parcours Aujourd'hui, Qualité/Économie | 1 h par condition |
| S8 | Feed à contenu fixé | 1 h |
| S9 | Chat stable puis arrivées à rythme fixé | 1 h |
| S10 | Intervalles, écran activé/désactivé et pause | 1 h par condition |
| S11 | Prompts identiques, modèles léger/avancé, installation mesurée séparément | 1 h par modèle |
| S12 | Santé, lieu, widget et montre, isolés puis combinés | 2–8 h |
| S13 | Serveur indisponible puis réseau faible, séparément | 2 h par condition |

Collecter sans écraser de preuves :

```bash
scripts/energy/collect.sh SERIAL /tmp/pepsgoal-energy/S2-1 before
# Scénario physiquement débranché, heures exactes consignées.
scripts/energy/collect.sh SERIAL /tmp/pepsgoal-energy/S2-1 after
# Sur appareil ODPM compatible uniquement (sinon test ignoré explicitement) :
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=fr.pepsgoal.benchmark.EnergyBenchmarks \
  -Pandroid.testInstrumentationRunnerArguments.pepsgoal.power=true
```

La collecte ne simule pas de débranchement et ne réinitialise pas batterystats.
Consigner manuellement alimentation physique, SOC/charge début-fin, durée,
configuration, version serveur, latence P50/P95, requêtes/octets, écritures,
incidents, exclusions et chemins de traces. Les dumps système peuvent contenir
des données privées : appareil de test et stockage contrôlé uniquement.

`pp/h = (SOC_début - SOC_fin) / durée_h`. Comparer à un témoin apparié et publier
l'incertitude. Un témoin de veille n'isole pas une charge écran allumé. Un niveau
émulateur stable ne vaut jamais 0 pp/h. Le fichier CSV fourni est vierge ;
aucune valeur physique n'a été fabriquée.

Génération du profil propre aux parcours sur hôte compatible :
`./gradlew :app:generateBaselineProfile`, puis revoir le diff des profils,
construire la variante distribuée et vérifier le profil empaqueté. Ne pas
committer un profil fictif ni mettre à jour des références visuelles sans revue.
