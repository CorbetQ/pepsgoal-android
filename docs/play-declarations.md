# Déclarations Play Console

Textes à reprendre dans la Play Console (Contenu de l'application > Autorisations sensibles) pour la variante **play** (`PepsGoal-vX.aab`).
La variante `play` retire l'auto-mise à jour GitHub (`REQUEST_INSTALL_PACKAGES`) et `USE_EXACT_ALARM` ; les mises à jour passent par Google Play (mise à jour dans l'application).

## Localisation en arrière-plan (`ACCESS_BACKGROUND_LOCATION`)

PepsGoal propose des **rappels par lieu** facultatifs : l'utilisateur associe un objectif à un emplacement enregistré (maison, salle de sport, travail…) et reçoit un rappel en y arrivant ou en le quittant (géorepérage). Cette fonction ne marche que si l'application est fermée, d'où la localisation en arrière-plan. Elle n'est demandée qu'au moment où l'utilisateur active un rappel par lieu, après un écran d'explication. La position n'est ni enregistrée ni envoyée au serveur.

Vidéo de démonstration à fournir : Objectif > Options > Rappel par lieu > choisir un emplacement > autoriser « Toujours ».

## Service de premier plan « usage spécial » (`FOREGROUND_SERVICE_SPECIAL_USE`)

Service `LinkService` : réception des messages et notifications PepsGoal. Le serveur n'envoie que du Web Push, sans Firebase Cloud Messaging ; l'application garde donc une connexion pendant une durée limitée pour relever les messages chiffrés de bout en bout. L'utilisateur peut le désactiver dans Paramètres > Notifications ; une notification permanente l'indique tant qu'il tourne.

## Alarmes exactes (`SCHEDULE_EXACT_ALARM`)

Les rappels d'objectifs se déclenchent à l'heure exacte choisie par l'utilisateur (ex. « prendre mon traitement à 8 h 00 »). L'autorisation est demandée depuis les paramètres de rappel ; sans elle, les rappels restent envoyés mais peuvent être décalés de quelques minutes.

## Health Connect (`health.READ_*`)

Lecture seule, à la demande de l'utilisateur, des pas, du sommeil, des exercices, de l'hydratation, de la distance, des calories actives, des étages et du poids, pour **valider automatiquement** les objectifs correspondants (ex. « 8 000 pas par jour »). Les données ne quittent pas l'appareil ; seule la validation de l'objectif est synchronisée. L'écran de justification (`ACTION_SHOW_PERMISSIONS_RATIONALE`) renvoie à la charte de confidentialité.

## Microphone (`RECORD_AUDIO`)

Commande vocale et dictée du coach, uniquement quand l'utilisateur appuie sur le micro.

## Agenda (`READ_CALENDAR`, `WRITE_CALENDAR`)

Ajout facultatif des créneaux d'objectifs dans l'agenda de l'utilisateur, et lecture pour éviter les conflits.

## NFC

Validation d'un objectif en touchant un tag NFC que l'utilisateur a lui-même associé à cet objectif.

## Sécurité des données (formulaire)

- Données collectées avec un compte : adresse e-mail, nom affiché, objectifs et validations (synchronisation) ; messages chiffrés de bout en bout.
- Mode sans compte : aucune donnée envoyée.
- Pas de publicité, pas de revente, pas de SDK d'analyse.
- Chiffrement en transit (HTTPS) ; suppression du compte depuis l'application (Paramètres > Compte).
