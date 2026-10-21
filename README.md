# PepsGoal pour Android

Application Android **100 % native** de PepsGoal (Kotlin, Jetpack Compose, Material 3). Elle remplace la coque Expo qui
embarquait l'interface web : chaque écran est dessiné en Compose, la logique tourne en Kotlin sur l'appareil, et
l'application parle directement au serveur de [pepsgoal-platform](https://github.com/CorbetQ/pepsgoal-platform)
(mêmes comptes, même synchronisation, même chiffrement de bout en bout que la version web).

```
pepsgoal-android/
├── core/   Kotlin pur (JVM) : modèle, logique des 15 types d'objectifs, séries, gels, récaps, badges,
│           chiffrement NaCl compatible tweetnacl, coffre de clés, synchronisation, API — testé sans Android
└── app/    Application Android : interface Compose, thème (dont Material You), notifications, Live Updates,
            rappels, synchronisation en arrière-plan, widget, raccourcis
```

## Fonctions natives

- **Material You** : Paramètres › Apparence › « Couleurs Material You ». Toute l'interface (panneaux, boutons, flammes,
  navigation, widget) prend les couleurs du fond d'écran ; les couleurs des types d'objectifs sont harmonisées.
  Réglage propre à l'appareil (Android 12+), la couleur d'accent du compte reste conservée.
- **Activités en cours (Live Updates, Android 16)** : minuteur d'un objectif de durée, période en cours et compte à
  rebours de fin de journée quand la série est en jeu, mis en avant dans la barre d'état, l'écran de verrouillage et la
  Now Bar (`Notification.ProgressStyle`, texte critique court, segments colorés par type). Avant Android 16 : notification
  permanente avec progression et chronomètre. Réglage : Paramètres › Rappels et notifications.
- **Notifications enrichies** : canaux par catégorie ; « Valider » depuis un rappel ou un début de période ;
  « Confirmer / Refuser » pour un binôme ; conversations en `MessagingStyle` avec raccourcis de conversation, réponse
  directe et « Marquer comme lu ».
- **Rappels à l'heure exacte** (AlarmManager, autorisation « Alarmes et rappels »), reprogrammés au démarrage du
  téléphone et aux changements d'heure.
- **Synchronisation en arrière-plan** (WorkManager, toutes les 15 min environ) : le serveur n'envoie ses notifications
  qu'en Web Push ; l'application relève donc notifications et messages à intervalles réguliers.
- **Widget « Ta journée »** (Glance) : série et validations du jour, validation d'un geste.
- Écran de démarrage, icône adaptative et **icône thématique**, raccourcis d'application, retour prédictif,
  affichage bord à bord, secrets (jetons, clés de chiffrement) protégés par le **Keystore Android**.

## Compiler

Prérequis : JDK 17 et le SDK Android (compileSdk 36).

```bash
./gradlew :core:test
```

```bash
./gradlew :app:assembleDebug
```

Serveur de test : `./gradlew :app:assembleDebug -Ppepsgoal.api=http://192.168.1.10:8080`.
Signature de publication : variables `PEPSGOAL_KEYSTORE`, `PEPSGOAL_KEYSTORE_PASSWORD`, `PEPSGOAL_KEY_ALIAS`, `PEPSGOAL_KEY_PASSWORD`.

Le module `core` se compile sans le SDK Android ; `ServerIT` rejoue un parcours complet contre un serveur si
`PEPSGOAL_IT_API` est défini. La CI GitHub (`.github/workflows/android.yml`) lance les tests et publie l'APK de débogage
en artefact.

## Licence

MIT, comme le reste de PepsGoal.
