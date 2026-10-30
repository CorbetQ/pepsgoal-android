# PepsGoal 8.1 « Braise Pop, fond partout » : extension du design system

> Version 1.0, 29 octobre 2026. **Complète** `design/design-system.md` (Braise Pop 2.0, version 8.0.0) : tout ce qui n'est pas redéfini ici reste valable (couleurs §2, typo §3, rayons §4, marche §5, mouvement §9, accessibilité §10).
> Demande : « TOUTE l'interface doit y passer, peu importe les sous-menus, et l'arrière-plan doit être plus présent dans l'application. »
> Sources : code 8.0.0 (`2b572d1`, `app/src/main/java/fr/pepsgoal/app/ui/`), audit `design/audit.md` (34/45), veille `design/veille.md`.
> Esquisse : `ui/src/main/kotlin/e81/Kit.kt` (prototypes des composants) et `ui/src/main/kotlin/e81/Screens.kt` (maquettes). Planches clair | sombre : `ui/out/P-e81-*.png` (§12).
> Contrastes : `design/tools/cr81.py` (tous les textes des nouveaux composants ≥ 4,5:1 ; contours ≥ 3:1).

---

## 0. En bref

| | 8.0.0 | **8.1** |
|---|---|---|
| Arrière-plan | confiné aux 360 dp du haut d'Aujourd'hui, **coupé par défaut**, fond uni ailleurs | **une seule couche plein écran sous toute l'app** (onglets, pages, derrière les feuilles), **activée par défaut**, avec voile dégradé, héros de scène en haut de chaque page et parallaxe |
| Texte posé sur le fond | titres et sections directement sur `bg` | **plaques** : tout texte hors carte est posé sur une capsule opaque `surface` (contraste garanti quelle que soit la scène) |
| Barre du haut | titre réduit à l'échelle | **héros** (scène visible, titre en plaque) qui part avec le contenu → **barre compacte** opaque de 56 dp qui apparaît |
| Contrôles | Material résiduels (Checkbox, RadioButton, CircularProgressIndicator, TextField, ModalBottomSheet, DatePickerDialog/TimePickerDialog, Dialog) | **PSlider, PRadio, PCheckbox, PTextField, PSelect + PMenu, PDialog, PDatePicker / PTimePicker, PTabs, PSpinner, PStepper, PSheet** |
| Écrans secondaires | verre, `smoothShape`, `shadow`, rayons libres, couleurs codées en dur | 100 % Braise Pop (plan fichier par fichier §9) |
| Fond réactif | lueur radiale (halo) dessinée seulement dans le fond confiné | **marée de braise** en aplats, visible partout, même fond coupé |
| Image perso | floutée (`Modifier.blur`) | **jamais floutée** : voile dégradé réglable |
| Profil | couronne de flammes supprimée | **couronne de flammes Braise Pop** (aplats) autour d'un médaillon qui s'enfonce |
| Fiche d'objectif | « + 250 ml » absent, case = saisie | **ajout rapide** (bouton de type « + 250 ml », « + 500 ml », saisie) et **cap : appui = +1 pas** |

---

## 1. Principes ajoutés

10. **Le fond est un lieu, pas un motif.** La scène choisie (ou « Collines braise », le nouveau fond par défaut) est le décor permanent de l'app : on la voit en grand dans le héros de chaque page, et elle reste présente, voilée, derrière les listes. Changer de page, c'est rester au même endroit.
11. **Rien de lisible ne repose sur la scène.** Le texte vit dans des cartes, des plaques ou des barres opaques. La scène ne porte jamais d'information. Conséquence : le contraste ne dépend jamais de la scène.
12. **Une seule couche de fond.** Elle est dessinée une fois, à la racine, sous tout le reste. Aucun écran ne peint son propre fond opaque (sauf exceptions §2.10).
13. **Aucun Material visible.** Chaque contrôle a sa version à marche. Les dialogues Android (date, heure) sont remplacés par des feuilles Braise Pop.

---

## 2. Arrière-plan « fond partout »

Planches : `P-e81-home.png` (accueil), `P-e81-stats.png` (sous-page), `P-e81-sheet.png` (derrière une feuille), `P-e81-chat.png` (fond de conversation), `P-e81-profile.png` / `P-e81-settings.png` (fond par défaut « Collines braise »).

### 2.1 Couches (de bas en haut)

| # | Couche | Où | Coût |
|---|---|---|---|
| 0 | `bg` uni (#FFF7F0 / #170F1C, #000 en OLED) | racine | 1 rectangle |
| 1 | **Scène** : scène illustrée (`Scenes.kt`), fond gratuit (`FreeBg`), ciel Météo (`SkyCanvas`), ambiance de boutique, image perso ou « Collines braise » | racine, plein écran + 30 % de hauteur en réserve (parallaxe, point focal) | 1 bitmap (rendu hors fil principal, 50 % de résolution, ≤ 12 i/s) |
| 2 | **Voile** : dégradé vertical de `bg` (§2.3) | racine | 1 rectangle dégradé |
| 3 | **Marée** du fond réactif (§2.7) | racine | 2 chemins pleins, statiques |
| 4 | **Voile de barre d'état** : `bg` 80 % → 0 sur 44 dp | racine | 1 rectangle dégradé |
| 5 | Contenu : héros, plaques, cartes opaques à marche, barres opaques | écrans | inchangé |

Le composable racine s'appelle **`AppBg`** (remplace `AppBackdrop` + `ConfinedBackdrop` + `PageBackdrop`). Il est posé dans `PepsRoot` **hors** du calque masqué (`hideLayer`) : quand une page est poussée, l'accueil dessous est masqué et arrêté comme aujourd'hui, mais la scène reste visible sous la page.

### 2.2 Point focal et héros

- Chaque scène déclare un **point focal** vertical `focus` (fraction de la hauteur, nouveau champ `ShopBg.focus`, défaut 0,30 ; ex. Loire 0,30, Montagnes 0,62, Phare 0,55). La scène est décalée (`offset`, une fois par mise en page) pour que ce point tombe dans le **héros** : `y = héros − focus × hauteurScène`, borné à [−30 %, 0].
- **Héros d'onglet** (`TabHero`) : 150 dp sur Aujourd'hui (date + titre), 120 dp sur Objectifs, Social, Profil, Modération. La scène y est presque nue (voile ≤ 12 %).
- **Héros de page** (`PageHero`) : 176 dp (150 dp pour les réglages) sur toutes les pages poussées (`ScreenFrame`) : bouton Retour à gauche, actions à droite, **tuile emoji 48 + titre Unbounded 26 dans une plaque** en bas à gauche.
- Le héros est le **premier élément de la liste** : il défile avec le contenu. La scène suit à **0,35 × le défilement** (parallaxe), plafonnée à 96 dp, par `graphicsLayer { translationY }` lu dans la phase de dessin (aucune recomposition).

### 2.3 Voile (lisibilité de la scène sous les listes)

Dégradé vertical de `bg` : opacité **a** de 0 à 26 % de la hauteur, **b** à 50 %, **c** en bas. Réglage « Présence du fond » (`prefs.bgLevel`, PSlider à 3 crans) :

| Présence | Clair (a / b / c) | Sombre (a / b / c) |
|---|---|---|
| Discrète | 34 / 70 / 82 % | 40 / 72 / 82 % |
| **Équilibrée (défaut)** | **6 / 38 / 55 %** | **12 / 40 / 55 %** |
| Intense | 0 / 18 / 32 % | 4 / 22 / 34 % |

Le voile est plus dense en bas (listes, barre d'onglets). Il ne sert pas au contraste du texte (§1, principe 11) mais au calme visuel.

### 2.4 Plaques : texte posé sur le fond

Tout texte qui n'est pas dans une carte, une feuille ou une barre est posé sur une **plaque** (§4.1) : titre d'écran, date, en-têtes de section, compteur, séparateurs de jour (Messages), lignes d'aide entre deux cartes (`Muted` hors carte), états vides hors carte, statut hors ligne / synchronisé. **Interdit : `Text` dont le parent direct est la scène.** Le texte de la barre d'état système est protégé par le voile de barre d'état (couche 4).

### 2.5 Barres

- **Barre d'onglets** : remplissage `surface` (opaque), bord et marche teintés par la scène : `edge' = lerp(edge, sceneTint, .45)` (clair) / `.55` (sombre). `sceneTint` = couleur de la scène (nouveau champ `ShopBg.tint`, sinon `sky[1]` ; image perso : `bgPalette[0]` ; fonds gratuits : `deco[0]`). Onglet actif inchangé (flamme).
- **Barre compacte** (`CompactBar`) : apparaît quand le héros est passé ; surface opaque, coins 24, bord 2 + marche 4 teintés comme la barre d'onglets ; retour, tuile emoji 34, titre Unbounded 20, actions. Entrée : fondu + glissement de 8 dp (`graphicsLayer`), pas d'animation de hauteur.
- Le bouton + reste flamme.

### 2.6 Derrière les feuilles

- Voile de feuille allégé : **prune 32 %** (clair) / **noir 50 %** (sombre) au lieu de 50 / 70 % : la scène reste lisible au-dessus de la feuille.
- La feuille reste opaque (`bg`, coins 32, bord 2 dp `edge` sur le haut). Hauteur maximale : 92 % ; une feuille pleine hauteur laisse toujours voir 8 % de scène en haut.
- L'horloge de la scène est **figée** tant qu'une feuille est ouverte.

### 2.7 Fond réactif : « marée de braise »

- Remplace la lueur radiale (`ReactiveBg`, halo interdit). Deux vagues en aplats translucides montent du bas de l'écran avec l'avancement du jour : hauteur `8 % + 26 % × progrès`. Vague arrière #FF8A3D à 16 % (clair) / 20 % (sombre), vague avant #FF3D96 à 13 % / 18 %, amplitudes 16 et 12 dp.
- La hauteur change avec un ressort `move` (1,2 s) à chaque validation ; **aucune animation infinie**.
- Journée parfaite : confettis du fond **une fois** (24 particules, 1,2 s), pas de boucle.
- Dessinée dans `AppBg` : **visible même si la scène est coupée** (« Nulle part » : marée sur `bg` + cercles de décor). Corrige l'écart de l'audit.
- Défaut : activé pour les nouvelles installations ; valeur existante conservée.

### 2.8 Image personnalisée

- **Plus aucun flou** : `Modifier.blur` et le réglage « Flou de l'image » sont supprimés.
- **Voile** (PSlider continu 0 → 80 %, bulle de valeur, « Auto » par défaut) : Auto = voile de la présence choisie ; sinon `c = valeur`, `b = 0,7 × valeur`, `a = 0,2 × valeur`.
- Migration : `bgBlur > 0` et `bgDim = −1` ⇒ `bgDim = 0,5` (le flou adoucissait l'image ; le voile compense). La clé `bgBlur` est effacée.
- Point focal : centre de l'image (0,5) ; couleur de barre : `bgPalette[0]`.
- Planche : `P-e81-menu.png`.

### 2.9 Réglages (Apparence › Arrière-plan) et valeurs par défaut

| Réglage | Contrôle | Défaut (nouvelle installation) | Migration (installations 8.0) |
|---|---|---|---|
| Fond (galerie : Collines braise, fonds gratuits, scènes possédées, Ma photo, Boutique) | tuiles à marche 78 × 104 | **« Collines braise »** (`pop`) | `blob` → `pop` ; autre choix conservé |
| **Où voir ton fond** (`prefs.bgWhere`) | 3 × PRadioRow : **Partout** / Accueil seulement / Nulle part | **Partout** | `bgTop = "off"` (choisi explicitement) ⇒ « Nulle part » ; `"on"` ou `"auto"` ⇒ « Partout ». Le mode « auto » disparaît (corrige « auto s'active pour les existants »). Toast unique : « 🏞️ Ton fond s'affiche maintenant partout · Réglages » |
| Présence du fond (`prefs.bgLevel`) | PSlider 3 crans | Équilibrée | — |
| Fond animé (`prefs.bgAnim`) | SetToggle | Oui (figé en économie d'énergie) | Appareil d'entrée de gamme (`Perf` « économie ») : Non |
| Lumière de la scène (`prefs.bgClock` étendu) | PSelect + PMenu : Suit l'heure réelle / Toujours le jour / **Suit le thème** (nuit en sombre) | Suit le thème | `bgClock = true` ⇒ Suit l'heure |
| Fond qui réagit | SetToggle | Oui | conservé |
| Fond à la météo | SetToggle | Non | conservé |
| Ma photo › Voile | PSlider 0-80 % | Auto | §2.8 |

« Accueil seulement » = comportement 8.0 (héros d'Aujourd'hui uniquement) ; « Nulle part » = `bg` + 2 cercles `deco` + marée.

### 2.10 Exceptions (fond propre autorisé)

- Visionneuses de photos et stories (`StoriesFs`, `PhotoViewer`, `ProofImage`, galerie plein écran) : fond **#000** (média), commandes sur boutons ronds à marche sombres.
- Écrans de fête (`Celebrate`, `Recap`, `BadgeFs`) : scène à voile 0, plaques, confettis ≤ 40.
- Contraste élevé : `bg` uni, aucune scène, aucune marée ; bord des plaques en `ink`.
- « Réduire la transparence » (`p.transp`) : scène seulement dans les héros (le reste en `bg` plein).
- Thème Météo : la scène est le ciel (`SkyCanvas`), point focal 0,3.

### 2.11 Performance (budget du fond)

- **Un seul `AppBg`** dans l'arbre. Interdit : `SceneBg`, `Backdrop` ou `FreeBg` plein écran ailleurs (seules exceptions : miniatures figées `still = true` et aperçus de la boutique / d'Apparence).
- Scène rendue dans un bitmap hors fil principal (`OffscreenCanvas`, `RenderCache.kt`) à **50 % de la résolution**, puis mise à l'échelle ; parallaxe et décalage du point focal par `graphicsLayer` (recomposition nulle pendant le défilement).
- **Horloge figée** : pendant un défilement (et 500 ms après), feuille ou dialogue ouverts, écran de fête, appli en arrière-plan, économie d'énergie (`PowerManager.isPowerSaveMode`), « Animations réduites » ou échelle d'animation système 0, `Perf.flags.bgAnim = false`. Figée = dernière image du bitmap (aucun redessin).
- Cadence : 6 à 12 i/s selon la scène (`SCENE_FPS`), jamais plus.
- Aucun flou, aucun `RenderEffect`, aucune ombre. Voile et marée = 3 formes pleines.
- Mémoire : 2 bitmaps à 50 % (≈ 2,6 Mo chacun sur un écran 1080 × 2400).
- Cible : la scène « Partout » ne doit pas dégrader le P90 de plus de 1 ms par rapport à « Nulle part » sur Aujourd'hui (Macrobenchmark §8).

---

## 3. Jetons ajoutés

| Jeton | Valeur | Usage |
|---|---|---|
| `veil(level)` | tableau §2.3 | voile du fond |
| `sheetScrim` | #2A1420 à 32 % / #000 à 50 % | voile des feuilles (remplace `scrim` pour les feuilles) |
| `dialogScrim` | #2A1420 à 45 % / #000 à 62 % | voile des dialogues |
| `sceneTint` | `ShopBg.tint` / `sky[1]` / `bgPalette[0]` / `deco[0]` | bord et marche des barres |
| `barEdge` | `lerp(edge, sceneTint, .45 / .55)` | barre d'onglets, barre compacte |
| `plaque` | `surface` + bord 2 dp `edge`, capsule, marges 12 / 5 (6 / 3 pour la mini-plaque) | texte sur la scène |
| `tide` | #FF8A3D 16/20 %, #FF3D96 13/18 % | marée |
| `focusEdge` | #FF4A12 (`flame[1]`), bord 2,5 dp | champ, sélecteur ouvert, bulle choisie |
| `heat0..4` | `surface2`, puis `lerp(surface, #FF4A12, .30 / .55 / .80 / 1)` | cartes de chaleur (fini l'alpha) |
| `mediaBg` | #000000 | visionneuses (§2.10) |
| `selTint` | `lerp(surface, néon, .14)` (clair) / `.16` (sombre) | ligne radio choisie, option de menu choisie (routine 18 / 14 %) |

---

## 4. Nouveaux composants

Tous dans `Components.kt` / `Controls.kt` (ou un nouveau `Kit81.kt`), prototypes de référence dans `ui/src/main/kotlin/e81/Kit.kt`. Cible tactile ≥ 48 × 48 dp pour tous. Rôles TalkBack indiqués.

### 4.1 Plaques (`Plaque`, `MiniPlaque`, `SectionPlaque`)
- `Plaque` : capsule `surface`, bord 2 dp `edge`, **sans marche** (non pressable), marges 12 / 5, écart 8. Contenu libre.
- `MiniPlaque` : idem, marges 10 / 3, emoji 16 facultatif, texte 13 ExtraBold `ink2` (date, « Aujourd'hui », aide).
- `SectionPlaque(text, emoji, count, action)` : remplace `SectionTitle` partout où il est posé sur le fond : plaque [emoji 24 + Unbounded 18 Bold + `Count`] ; action = **petite puce à marche** (`chunk(surface, edge, 20, 3, 2)`, 14 ExtraBold `accentInk`, 48 dp). Rôle Heading. Espacement : 14 dp au-dessus, 10 en dessous.
- `TitlePlaque` (dans `TabHero`) : capsule, Unbounded 28-30 ExtraBold, 2 lignes max.

### 4.2 Héros et barres (`TabHero`, `PageHero`, `CompactBar`, `AppBg`)
Voir §2.2 et §2.5. `TabHero(title, over?, height, right)` ; `PageHero(title, emoji, color, height, right)` ; `CompactBar { … }`. `ScreenFrame` les intègre (§9). La barre du haut d'onglet (`Root.TopBar`) devient : héros en tête de liste + barre compacte superposée.

### 4.3 `PSlider`
- Rail capsule 12 dp `surface2` entouré de 2 dp `control` (≥ 3:1) ; remplissage flamme (défaut) ou néon du contexte ; pouce blanc 30 dp à marche 3 dp (`flameStep` ou `step` du néon) qui s'enfonce pendant le glissement.
- Crans (`steps`) : pastilles de 6 dp (prune 55 % sous le remplissage, `control` au-delà) ; aimantation avec `pop`.
- Bulle de valeur prune (#FFF4EC 13 ExtraBold) au-dessus du pouce pendant le glissement, et en permanence si `bubble` est donné.
- Libellés d'extrémité 12 Bold `ink3` sous le rail ; valeur courante à droite du titre (14 ExtraBold).
- Rôle : `progressBarRangeInfo` + `setProgress` (TalkBack ajuste par crans). Hauteur 48 (78 avec bulle). Désactivé 45 %.
- Usages : présence du fond, voile de l'image, taille du texte (A11y), particules (Performance), volume des sons, durée par défaut du minuteur, objectif chiffré dans le formulaire (en plus de la saisie).

### 4.4 `PRadio` et `PRadioRow`
- Rond 28 dp dans une cible 48. Éteint : `surface`, contour 2,5 dp `control`. Allumé : aplat néon (défaut `challenge` #FFB000), pastille prune 10 dp, marche 2 dp `step` ; apparition de la pastille en `pop`.
- `PRadioRow(title, sub, on, emoji?)` : 60 dp min, emoji 26, titre 16 ExtraBold, sous-ligne 13 SemiBold `ink2`, PRadio à droite ; ligne choisie = fond `selTint`, rayon 18. Rôle RadioButton, groupe `selectableGroup`.
- Remplace `material3.RadioButton` (`SheetHost.FriendPicker`) et les `Seg` de plus de 3 options longues (§5.6).

### 4.5 `PCheckbox` et `PCheckRow`
- Carré 28 dp, rayon 9, cible 48. Vide : `surface`, contour 2,5 `control`. Coché : aplat néon (défaut `routine`), coche prune 18 dp dessinée en 200 ms (`DrawnCheck`), marche 2 dp. Partiel : tiret prune 12 × 3.
- `PCheckRow` : 56 dp, case à gauche, titre 16 ExtraBold, sous-ligne. Rôle Checkbox (`toggleableState`).
- Remplace `material3.Checkbox` (`GoalSheets` tâches de projet, `GoalExtras` étapes, `GoalPacks`, `SheetHost.FriendPicker`).

### 4.6 `PTextField`, `PTextArea`, `PSearchField`
- Évolution de `Field` : libellé 13 ExtraBold `ink2` au-dessus ; boîte 56 (rayon 18, bord 2 + marche 3, « creusée » à l'appui) ; **focus** : bord 2,5 + marche `focusEdge` ; **erreur** : bord `danger` + ligne « ✕ message » 13 Bold `danger` ; **aide** 13 Bold `ok` ; **compteur** 12 ExtraBold `ink3` à droite sous la boîte ; désactivé : `surface2` à 45 %.
- Boutons de champ (effacer, afficher le mot de passe, micro) : **cible 48**, pastille visible 32 `surface2`.
- `PTextArea` : 2 à 12 lignes, compteur dès 70 %.
- `PSearchField` : capsule (rayon 28), icône loupe, effacer.
- Remplace `material3.TextField` (`ChatScreen` Composer) et tous les `BasicTextField` locaux.

### 4.7 `PSelect` et `PMenu`
- `PSelect(label, value, emoji?)` : boîte de champ pressable (marche 4), valeur 16 ExtraBold, chevron qui pivote de 180° à l'ouverture ; ouvert = bord `focusEdge`.
- `PMenu(items, header?)` : carte flottante `surface`, rayon 22, bord 2 + marche 4, largeur 248-300 ; lignes 50 dp (emoji 22 ou icône 20, libellé 16 ExtraBold, sous-ligne 13) ; option choisie : fond `selTint` routine + pastille `routine` avec coche prune ; ligne danger en `danger` séparée par un trait 2 dp `surface2` ; en-tête facultatif (rangée de réactions dans Messages).
- Placement : sous l'ancre, **au-dessus si la place manque** ; entrée échelle 0,92 → 1 depuis l'ancre (`pop`), sortie fondu 120 ms ; voile transparent (toucher dehors = fermer). Rôle : `Popup` avec `paneTitle`, éléments `Role.DropdownList` / `selected`.
- Remplace tous les menus contextuels (Messages, Feed, Amis, Modération) et les listes déroulantes faites de `FlowRow` de puces quand il y a plus de 6 choix.

### 4.8 `PDialog` et `PConfirm`
- Carte centrée `surface`, rayon 28, bord 2 + **marche 6**, largeur 328 (max 90 %) ; emoji 56 incliné de −6° qui arrive en `pop` ; titre Unbounded 22 ; texte 15 Bold `ink2` ; contenu libre ; **boutons empilés pleine largeur** : un seul principal (flamme) ou, pour une action destructrice, bouton de type `duel` + icône + texte prune ; « Annuler » en secondaire.
- Voile `dialogScrim`. Entrée : échelle 0,9 → 1 + fondu (`pop`), sortie 150 ms. Retour = Annuler.
- Remplace : `ConfirmSheet` (quand la question est courte), `androidx.compose.ui.window.Dialog` (Sheets41, Screens56, Screens57, FeedScreen : voir les visionneuses §2.10), et toute confirmation en ligne (`askDel` de Messages).

### 4.9 `PDatePicker` et `PTimePicker` (feuilles)
- **Date** : feuille 480 dp : en-tête mois Unbounded 18 + boutons ronds ‹ › ; grille 7 × 6 de jours 44 dp (`chunk` neutre, marche 3) ; aujourd'hui = bord `focusEdge` ; jour choisi = aplat flamme + prune ; jours hors bornes 40 %. Raccourcis en puces (`FChip`) : « Aujourd'hui », « Demain », « Dans 1 semaine ». Pied : bouton principal « Choisir le 12 nov. ».
- **Heure** : feuille 420 dp : deux colonnes défilantes (heures 0-23, minutes par 5) en capsules 56 dp, valeur choisie en aplat flamme ; puces « 7:00 », « 12:30 », « 20:00 » ; bouton principal.
- Remplacent `android.app.DatePickerDialog` (`SheetHost.DateField`, `Home61` compte à rebours, `Screens60` anniversaire, `ModerationScreen`) et `TimePickerDialog` (`SettingsScreen.TimeButton`, `ModerationScreen`).

### 4.10 `PTabs`
- Onglets de page : rail capsule `surface` + bord 2 (posé sur la scène, il sert aussi de plaque) ; onglet choisi = pastille à marche 3 en **néon de la page** (couleur du `PageHero`) ou flamme, texte prune 14 ExtraBold, emoji 18 facultatif ; au repos texte `ink2`.
- ≤ 3 options : largeurs égales ; ≥ 4 : **défilement horizontal**, largeur au contenu, le choisi est ramené à l'écran. Hauteur 48 min ; 2 lignes à 200 %. Rôle Tab.
- Remplace : onglets maison de la Boutique (`glassStrong`), segmenté Feed | Amis | Messages (Social), sélecteurs de période des Statistiques, onglets de Modération.

### 4.11 Chargement : `PSpinner`, `PIndeterminate`, `PSkeleton`
- `PSpinner` : trois pastilles (#FFB000, #FF4A12, #FF3D96) qui sautent tour à tour (`graphicsLayer`, 15 i/s), tailles 6 / 10 / 14 dp. Animations réduites : pastilles fixes qui pulsent en opacité 2 Hz max.
- `PIndeterminate` : rail `rail` 10 dp + segment flamme de 30 % qui glisse (une translation).
- `PSkeleton` : `Skeleton` existant, aplats `surface2` sans scintillement.
- Remplace `CircularProgressIndicator` (`ServerUpdateCard`, `ChatScreen` envoi d'image, `SettingsScreen` vérification) et `LinearProgressIndicator` des widgets.

### 4.12 `PStepper`
Bouton rond « − », valeur Unbounded 22 + unité 14 ExtraBold `ink2`, bouton rond « + » ; appui long = répétition accélérée (tick haptique). Remplace le `Stepper` local de `Sheets56` et les « − / + » des formulaires.

### 4.13 `PSheet` (hôte des feuilles, remplace `ModalBottomSheet`)
- Feuille opaque `bg`, coins 32, bord 2 dp `edge` en haut, poignée 40 × 5 `ink3` (cible 48), voile `sheetScrim`.
- **Ouverture rebondissante** : translation de 100 % → 0 en ressort `spring(.72, 380)` (dépassement d'environ 12 dp vers le haut puis retour), la poignée fait un `pop` à l'arrivée ; le contenu entre en cascade (`enter`, 4 éléments max).
- Fermeture : glisser vers le bas (seuil 30 % ou vitesse), bouton Fermer, Retour (retour prédictif : la feuille suit le geste, échelle 0,96) ; sortie 200 ms + fondu.
- Imbrication avec le défilement interne (`nestedScroll`) ; accessibilité : `paneTitle`, action « Fermer ».
- Animations réduites : fondu 120 ms, pas de rebond.

### 4.14 Messages (`Bubble`, `Composer`)
- Bulle **à moi** : aplat de la couleur de la conversation (dégradé flamme par défaut), texte et heure **prune** (pas d'alpha : 5,1:1 minimum), marche 3 dp (`step` ou `flameStep`), rayon 22, coin de la queue 6.
- Bulle **reçue** : `surface`, bord 2 + marche 3 `edge`. Bulle sélectionnée : bord `focusEdge`.
- Réactions : plaque sous la bulle (emoji 16 + nombre 12 ExtraBold).
- Séparateur de jour, « en train d'écrire », messages système : `MiniPlaque`.
- **Composer** : bouton rond « + », champ capsule `chunk(surface, edge, 26, 3, 2)` avec emoji, bouton Envoyer **flamme à marche** (seul principal de l'écran) ; enregistrement vocal : barre `surface` à marche + `PSpinner`.
- En-tête : `CompactBar` (retour, avatar 34 dans l'anneau flamme, nom Unbounded 18, statut 13 Bold avec pastille `routine`, actions). Mode sélection : même barre, actions en boutons ronds.
- Fond : fond de conversation choisi (`ChatBgPicker`) ou `AppBg` ; voile Équilibré.

### 4.15 Couronne de flammes (`flameCrown`, `AvatarMedallion`)
- Reprise de `flameAura` (6.3.0) **en aplats** : 9 à 13 langues sur trois plans pleins (#FF3D96, #FF4A12 à 72 %, #FFB000 à 44 % de la hauteur), sans dégradé vers le transparent ni halo ; les langues montent (hautes en haut du cercle, presque nulles en bas), pointe recourbée.
- Hauteur `18 + 40 × k` dp, `k` = progression du palier (0 au niveau 1, 1 en Supernova) ; nombre `9 + 4k`. Couleurs du palier (`tierColors`) au-delà du palier 6.
- Vacille à 15 i/s (dessin seulement), uniquement sur le Profil et la feuille d'un ami (`UserSheet`), arrêtée hors écran et après 20 s ; Animations réduites : figée.
- **Médaillon** : disque blanc à marche 5 dp `flameStep` 55 %, avatar 88-96 dp dedans, badge de niveau prune / or à marche 3 ; **pressable : s'enfonce** (ouvre la feuille Avatar). Même traitement pour `AvatarButton` (40 dp, marche 3) et `StreakMedal` (médaillon de la carte Série).
- Planche : `P-e81-profile.png`.

### 4.16 Ajout rapide et cap « appui = +1 »
- **Case de progrès** (`CheckButton` mode progrès, objectifs `cap`, `objective` chiffrés, `limit`, `measure` par pas) : **appui = + 1 pas** (`goal.step`, ex. 250 ml, 1 verre, 1 page), **appui long = saisie** (feuille Progrès). Retour : pop de la case, chiffre `+250 ml` qui monte de 16 dp et s'efface (graphicsLayer), roulement de la sous-ligne, toast prune « 💧 +250 ml · 1,45 / 2 L [Annuler] ». TalkBack : actions « Ajouter 250 ml » et « Saisir une quantité ».
- **Fiche** : carte d'action = valeur `display` 48 + unité, message, anneau, barre 12, puis rangée **[bouton de type « + 250 ml » avec emoji 22] [secondaire « + 500 ml »] [rond « Saisir »]**, puis encart `surface2` qui rappelle le geste de l'accueil ; carte « Aujourd'hui » (journal des ajouts avec « Annuler » en `accentInk`).
- Planches : `P-e81-goal.png`, `P-e81-home.png` (toast).
- Le bouton + remonte au-dessus d'un toast (bas = barre + 18 + toast 56 + 12).

---

## 5. Écarts de l'audit 8.0 : décisions

| # | Écart | Décision 8.1 |
|---|---|---|
| 1 | Fond réactif sans effet si fond animé coupé | Marée dessinée dans `AppBg`, indépendante de la scène (§2.7) |
| 2 | Image perso floutée (`Backdrop.kt:194`) | Flou supprimé, voile (§2.8) |
| 3 | « Auto » active le fond chez les existants | Mode auto supprimé, migration explicite + toast (§2.9) |
| 4 | Couronne de flammes supprimée | `flameCrown` Braise Pop (§4.15) |
| 5 | Médaillon flamme / avatar sans enfoncement | `chunk` + `rememberPress` sur `StreakMedal`, `AvatarButton`, `AvatarMedallion` |
| 6 | Cibles < 48 dp | FChip : capsule 44 + marche 4 = **48** ; Seg : rail 56 (segments 48) ; PSwitch isolé : boîte 60 × 48 (`minimumInteractiveComponentSize`) ; boutons de champ 48 ; toutes les `MiniPlaque` non pressables |
| 7 | Plusieurs boutons principaux sur Aujourd'hui | « Oui » du plan de la semaine → bouton de type (néon de l'objectif) ; « Journée parfaite » → bouton de type `monthly` ; « Confirmer » → secondaire ; règle générale §6.3 |
| 8 | 10 dp entre lignes et cartes | Listes : `spacedBy(10)` ; éléments « carte » : `padding(top = 2.dp)` ⇒ 12 ; en-têtes de section 14 / 10 |
| 9 | Seg / Chip tronqués à 200 % | Seg : `maxLines = 2`, hauteur min 48, plus d'ellipse ; si `fontScale ≥ 1,5` et plus de 3 options → liste de `PRadioRow`. Chip et FChip : 2 lignes, pas d'ellipse |
| 10 | Rangée « Moments » animée en taille | Fondu + glissement (`graphicsLayer`) ; la place est réservée (pas d'`expandVertically`) |
| 11 | Barre du haut sans changement de hauteur | Héros en tête de liste + `CompactBar` superposée (§2.2, §2.5) |
| 12 | Ouverture des feuilles Material | `PSheet` rebondissante (§4.13) |
| 13 | Pas de « + 250 ml », case = saisie | §4.16 |
| 14 | `ok` sur `surface2` 4,42:1 | Interdit (règle d'audit 8.1 n° R13) ; aide de champ en `ok` sur `surface` (5,0 / 8,5:1) |
| 15 | Pointillé du jour plus fin que la maquette | Trait 2 dp, tirets 4 / 3 dp |
| 16 | Macrobenchmark et Baseline Profile absents | Modules prévus §8 |
| 17 | `smoothShape`, `shadow(0 dp)` dans ~40 fichiers | Supprimés partout (§9, règles R1-R3) |

---

## 6. Règles globales de migration (s'appliquent à chaque fichier)

### 6.1 Remplacements mécaniques
| Code | Règle | Remplacement |
|---|---|---|
| G1 | `smoothShape(r)` / `Squircle` | `RoundedCornerShape` au rayon §4 (17 tuile, 22 ligne / tuile, 24 carte, 28 carte vive, 32 feuille, capsule) ; `Squircle` réservé à `EmojiTile` |
| G2 | `p.glass`, `p.glassStrong`, `p.glassEdge`, `glassBg` | `chunk(surface, edge, r, 4, 2)` (pressable) ou `Plaque` / fond `surface` + bord (plat) |
| G3 | `.shadow(…)` | supprimé (la marche suffit) |
| G4 | `Color(0x…)` dans un écran | jeton (`ink`, `PLUM`, néon du type, `gold`…) ; autorisé seulement dans les **illustrations** (`Scenes.kt`, dessin du compagnon `PetView`, œufs `EasterEggs`, palettes de confettis) |
| G5 | `Color.White/Black.copy(alpha)` en surimpression | `Plaque`, `sheetScrim`, `dialogScrim`, `mediaBg` ; pilules d'info sur aplat vif = blanc 42 % (déjà au §7.5 de la 8.0) |
| G6 | `clickable` / `springClick` sans marche | `chunk(…, pressed = rememberPress(inter))` ; lien texte = `PButton(kind = "ghost")` |
| G7 | tailles hors échelle (`fontSize` 9, 10, 19, 21, 26…) | échelle §3 (11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 28, 30, 48, 60) |
| G8 | rayons hors §4 (8, 9, 10, 14, 16, 26…) | rayon §4 ; petits éléments ≤ 20 dp : 6 |
| G9 | `Text` / `Muted` / `SectionTitle` posés sur le fond | `MiniPlaque` / `SectionPlaque` |
| G10 | `PageBackdrop`, `background(p.bg)` plein écran, `decoCircles` | supprimés (fond = `AppBg`) ; sauf exceptions §2.10 |
| G11 | Material / Android : `Checkbox`, `RadioButton`, `TextField`, `CircularProgressIndicator`, `ModalBottomSheet`, `DatePickerDialog`, `TimePickerDialog`, `ui.window.Dialog` | `PCheckbox`, `PRadio`, `PTextField`, `PSpinner`, `PSheet`, `PDatePicker`, `PTimePicker`, `PDialog` / visionneuse |
| G12 | `bounce(inter)` (échelle) | `rememberPress` (enfoncement) |
| G13 | `LocalHaze`, `EdgeFrost`, `aiGlow`, `washOf`, `depth`, `glow`, `sheen`, `candy`, `ifShadows` | appels supprimés, puis fonctions supprimées (fin de migration) |

### 6.2 Contenus sur aplats
Tout texte sur un aplat vif est **prune** (aucune exception dans les cartes cadeau, anniversaire, récap : `Color(0xFF2A1408)` / `0xFF3A2010` → `PLUM`).

### 6.3 Un seul bouton principal
Par écran, feuille ou dialogue : au plus **un** `kind = "primary"` visible à la fois. Les actions répétées dans une liste (« Oui », « Plan B fait », « Ajouter ») sont des **boutons de type** (néon de l'objectif) ou secondaires. Le bouton + et la carte Série ne comptent pas. Contrôle : `grep 'kind = "primary"'` par fichier (§11, R10).

### 6.4 Espacements
Marges 16 ; lignes 10 ; cartes 12 ; sections 14 / 10 ; héros → première carte 10 ; feuille : marges 16, bas = `navigationBars` + 16.

---

## 7. Écrans clés 8.1 (structure)

- **Aujourd'hui** (`P-e81-home.png`) : `TabHero` 150 (date en mini-plaque, titre en plaque, cloche + avatar à marche) → carte Série → `SectionPlaque` par moment → lignes → modules en cartes. Marée en bas. Toast d'ajout rapide.
- **Statistiques** (`P-e81-stats.png`) : `PageHero` (📈, néon `project`, Partager) → `PTabs` Semaine | Mois | Année → `StatGrid` néon 2 × 2 → « Ton année » en 5 niveaux pleins (`heat0..4`) → « Tes objectifs » (tuile 40 + barre néon 10).
- **Feuille par-dessus** (`P-e81-sheet.png`) : voile allégé, scène visible au-dessus, feuille « Nouvel objectif » : grille 3 × 3 de tuiles de type à marche (emoji 34, libellé prune), « Partir d'un modèle » secondaire.
- **Fiche d'objectif** (`P-e81-goal.png`) : bandeau néon pleine largeur 176, carte d'action avec ajout rapide (§4.16), tuiles de stat, journal du jour.
- **Messages** (`P-e81-chat.png`) : fond de conversation, `CompactBar`, bulles §4.14, `PMenu` avec rangée de réactions, Composer.
- **Boutique** (`P-e81-shop.png`) : `PageHero` (🛍️, solde en plaque à droite) → carte « Quête du jour » en flamme → `PTabs` défilants (Fonds, Thèmes, Ambiances, Icônes, Flammes) → grille 2 colonnes de cartes à marche : aperçu de la scène (rayon 18), nom, état : **Actif** (bord `routine` 3 dp + pilule « ✓ Actif », étiquette « Sur tout l'écran »), **Utiliser** (secondaire petit), **Prix** (puce or à marche + flamme), **Verrouillé** (cadenas + « encore 310 🔥 »).
- **Apparence** (`P-e81-settings.png`, `P-e81-menu.png`) : galerie de fonds, « Où voir ton fond » (PRadioRow), « Présence du fond » (PSlider à crans), interrupteurs ; page défilée = `CompactBar`, « Ma photo » avec voile (PSlider + bulle), « Lumière de la scène » (PSelect + PMenu).
- **Compte** (`P-e81-dialog.png`) : PTextField (focus + aide + compteur, erreur, zone de texte), PCheckRow (coché, partiel, vide), PDialog destructif.
- **Profil** (`P-e81-profile.png`) : `TabHero` 96 → carte identité avec `AvatarMedallion` + couronne débordant au-dessus (92 dp), nom, niveau, barre 14, phrase de progression → `StatGrid` → `SectionPlaque` « Mon espace » → groupe.
- **Composants** (`P-e81-kit.png`).

---

## 8. Performance : Macrobenchmark et Baseline Profile

1. Nouveau module **`:benchmark`** (`com.android.test`, `androidx.benchmark:benchmark-macro-junit4` 1.3.x), cible `:app` en variante `benchmark` (release signée debug, `profileable`).
2. Nouveau module **`:baselineprofile`** (plugin `androidx.baselineprofile`), dépendance `androidx.profileinstaller` dans `:app`.
3. Parcours (UiAutomator, identifiants `testTag` à ajouter) :
   - `coldStartup` : `StartupTimingMetric`, 10 itérations, `CompilationMode.Partial(BaselineProfile)`.
   - `todayScroll` : défilement d'Aujourd'hui, **fond Partout** puis **Nulle part** (paramètre), `FrameTimingMetric`.
   - `validate` : valider 3 objectifs (pop, roulement, glissade), `FrameTimingMetric`.
   - `capPlusOne` : 5 appuis sur la case d'un cap.
   - `openGoalSheet` : ouverture rebondissante de la fiche, défilement.
   - `statsPage` : ouvrir Statistiques (héros + parallaxe), défiler.
   - `chatScroll` : conversation de 200 messages.
4. Seuils (appareil modeste type Galaxy A15 / A35) : P90 ≤ 16 ms, P99 ≤ 24 ms, 0 image > 32 ms en validation, démarrage à froid ≤ 1,2 s ; écart P90 Partout − Nulle part ≤ 1 ms.
5. Génération du profil : `./gradlew :app:generateBaselineProfile` sur appareil géré (`pixel6Api34`, `aosp-atd`) dans le CI ; profil versionné (`app/src/main/baseline-prof.txt`). Mesures chiffrées sur appareil réel (Firebase Test Lab si disponible), résultats joints au rapport d'audit.

---

## 9. Plan fichier par fichier (100 % des surfaces)

Chemins relatifs à `app/src/main/java/fr/pepsgoal/app/ui/` sauf mention. Colonnes de l'inventaire : **L** lignes ; **sm** `smoothShape` ; **r** rayons hors §4 ; **sh** `shadow` ; **gl** verre ; **M** Material / dialogues Android ; **cx** `Color(0x…)` ; **α** blanc / noir translucides ; **ck** cliquables ; **mk** éléments à marche ; **fs** tailles hors échelle. État : 🟢 conforme 8.0 (adaptation au fond seulement), 🟡 partiel, 🔴 non refait, ⚪ non visuel.

### 9.1 Inventaire

| Fichier | L | sm | r | sh | gl | M | cx | α | ck | mk | fs | État | Surfaces |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| theme/Theme.kt | 313 | 0 | 0 | 0 | 3 | 0 | 12 | 0 | 0 | 0 | 1 | 🟡 | jetons, typo, thèmes, ambiances |
| Depth.kt | 184 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 4 | 0 | 🟡 | chunk, pressable, reliquats depth/glow/sheen/candy |
| Motion.kt | 158 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🟢 | ressorts, RollingNumber, Confetti |
| Components.kt | 1002 | 1 | 1 | 0 | 2 | 0 | 5 | 0 | 13 | 17 | 0 | 🟡 | Card, PButton, IconBtn, Chip, FChip, Seg, PSwitch, Field, SectionTitle, Bar, Ring, Av, LevelRing, flameAura, Empty, Skeleton, Note, PollOption, PageBackdrop, decoCircles |
| Controls.kt | 265 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 6 | 9 | 0 | 🟡 | GoalIcon, CheckButton, NavBar, Fab, TabTitle, StreakPill, RoundGlass, AvatarButton, AvatarFace |
| SettingsKit.kt | 139 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 🟢 | SetHero, SetGroup, SetItem, SetToggle, SetBlock |
| GoalUi.kt | 498 | 1 | 7 | 0 | 1 | 0 | 0 | 1 | 3 | 2 | 1 | 🟡 | GoalRow, QuickSheet, Last7, GoalCard, GoalCalendar, ProgLine, MeasureChart, WeekStrip, QtyChart |
| Swipe.kt | 167 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🟢 | balayage des lignes (fonds Valider / actions) |
| Backdrop.kt | 426 | 0 | 0 | 0 | 0 | 0 | 2 | 11 | 0 | 0 | 0 | 🔴 | Backdrop, ConfinedBackdrop, CustomBg (flou), ThemeFx, SnowFx, WeatherFx, SkyCanvas, ReactiveBg (halo) |
| Scenes.kt | 3315 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0 | 0 | 0 | 🟡 | 39 scènes + 6 fonds gratuits (illustrations : couleurs libres) |
| RenderCache.kt | 101 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🟡 | OffscreenCanvas, StillCanvas |
| Root.kt | 559 | 0 | 1 | 0 | 0 | 0 | 3 | 0 | 4 | 7 | 0 | 🟡 | PepsRoot, Shell, TopBar, NavBar, Fab, PullRefresh, Toasts, Sparks, Reachable, volet latéral, transitions |
| EasterEggs.kt | 119 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🟡 | EggOverlay, Drifters, DailyEggs, EggsCard |
| EmojiPicker.kt | 46 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 3 | 0 | 0 | 🔴 | grille d'emojis |
| EmojiText.kt | 124 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🟢 | texte avec emojis Fluent |
| ThemePreview.kt | 94 | 2 | 1 | 0 | 0 | 0 | 0 | 3 | 2 | 0 | 1 | 🔴 | aperçus de thème (boutique, Apparence) |
| IconPaths.kt | 91 | — | — | — | — | — | — | — | — | — | — | 🟡 | pictos (ajouter `mail`, `at`, `calendarplus`, `sliders`, `chevronup`) |
| Feedback, Helpers, I18n, Media, Voice, VoiceCmd | — | — | — | — | — | — | — | — | — | — | — | ⚪ | non visuels (aucun changement) |
| screens/TodayScreen.kt | 590 | 1 | 0 | 0 | 0 | 0 | 0 | 3 | 7 | 24 | 2 | 🟡 | ScreenList, TodayScreen, HeroFlame, Starters, Board, RecapCard, WeatherCard, DueRow, CoachPill, DoneHead, QuickTools, ContextCards, WrappedCard, DayCount |
| screens/Home61.kt | 443 | 1 | 1 | 0 | 0 | 1 | 3 | 3 | 7 | 25 | 0 | 🟡 | HomeHeader, StreakMedal, StreakSheet, MonthFocusCard, MomentsRow, HomeCardSheet, HomeSectionHead, CountdownCard, QuickCmdSheet, QuotesSheet, WalletSheet |
| screens/Today56.kt | 147 | 1 | 1 | 0 | 1 | 0 | 0 | 0 | 3 | 6 | 1 | 🔴 | TimelineCard, CatchUpCard, EveningQuickCard |
| screens/AiCoachCard.kt | 130 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 🟡 | AiPromoCard, AiTipCard, AiCreateCard |
| screens/ServerUpdateCard.kt | 116 | 2 | 3 | 0 | 0 | 1 | 0 | 0 | 2 | 4 | 0 | 🔴 | ServerUpdateCard, UpdateProgress, HostKeyBox |
| screens/ChallengesScreen.kt | 153 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 🟡 | ChallengesCard, ChallengesScreen, NewChallenge |
| screens/GoalsScreen.kt | 187 | 1 | 1 | 0 | 0 | 0 | 1 | 0 | 3 | 15 | 0 | 🟡 | ChipsRow, GoalsScreen, OverTile |
| screens/SocialScreen.kt | 36 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🟡 | segmenté Feed / Amis / Messages |
| screens/FeedScreen.kt | 385 | 5 | 8 | 1 | 5 | 3 | 2 | 3 | 14 | 10 | 1 | 🔴 | FeedScreen, BanBanner, GelCard, StoriesBar, StoryBubble, GoalAttach, PostCard, ActionPill, CheerButton, PollView, PhotoViewer (Dialog), CommentBubble, CommentsScreen |
| screens/FriendsScreen.kt | 351 | 7 | 4 | 0 | 3 | 0 | 0 | 0 | 8 | 12 | 0 | 🔴 | FriendsScreen, RequestsCard, FriendsTodayCard, PodiumCard, FriendRow, InviteCard, PersonRow, Verified, MessagesCard, GroupAv, SuggestCard, SearchCard |
| screens/ChatScreen.kt | 801 | 17 | 19 | 5 | 13 | 3 | 4 | 2 | 15 | 36 | 0 | 🔴 | ChatScreen, MessageRow, MsgText, VoicePlayer, ChatPoll, Composer (TextField), ChatPollForm, VoiceRecorderBar, DuoInvite, barre de sélection, confirmation de suppression |
| screens/Chat59.kt | 247 | 3 | 1 | 0 | 3 | 0 | 2 | 0 | 2 | 8 | 0 | 🔴 | StickerView, StickerPicker, JumboEmojis, EmojiBurst, ShotNotice, ChatSearchBar, SchedulePanel, ScheduledStrip, VoiceTranscript |
| screens/Screens58.kt | 54 | 1 | 2 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 2 | 🔴 | ChatBgPicker, Tile |
| screens/CoachChatScreen.kt | 199 | 3 | 4 | 0 | 3 | 0 | 0 | 0 | 0 | 13 | 0 | 🔴 | CoachAvatar, CoachChatScreen, Welcome, Writing, Bubble |
| screens/ProfileScreen.kt | 486 | 8 | 6 | 1 | 0 | 0 | 0 | 2 | 8 | 8 | 0 | 🔴 | ProfileScreen, ProfileHero, MySpace, LookCard, NearBadgesCard, BadgeGridCard, StoryArchiveCard, StoryRow, StoryHistoryScreen, StoryThumb, BadgeMedal, Medal, BadgesCard, BadgeScreen, FlameSkinsCard |
| screens/Levels63.kt | 59 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 🔴 | LevelsSheet |
| screens/ModerationScreen.kt | 416 | 2 | 3 | 0 | 0 | 2 | 0 | 0 | 4 | 23 | 0 | 🔴 | ModerationScreen, StatusChips, Tiles, ModAccounts, ModPostCard, ModReports, ModAppeals, ModMaint (Date/TimePickerDialog), ModNews, ModServer, ModServerStats, Meter, SparkBars |
| screens/SearchScreen.kt | 176 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 4 | 0 | 🟡 | SearchScreen, Result |
| screens/StatsScreen.kt | 324 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 1 | 3 | 1 | 🟡 | StatsScreen, HealthStatsCard, InsightsCard, YearPixels, carte de chaleur, objectifs 30 j |
| screens/WeekScreen.kt | 110 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 2 | 2 | 1 | 🔴 | WeekScreen, DayCard |
| screens/JournalScreen.kt | 149 | 1 | 5 | 0 | 0 | 0 | 0 | 0 | 1 | 6 | 0 | 🔴 | MoodPicker, MoodCard, JournalScreen, MoodCalendar |
| screens/LeagueScreen.kt | 121 | 3 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 🔴 | LeagueScreen, LeagueCard |
| screens/ShopScreen.kt | 295 | 0 | 0 | 0 | 2 | 0 | 0 | 4 | 4 | 5 | 0 | 🔴 | AppIconPreview, ShopScreen (onglets maison), ModeCard, GiftBtn, PriceTag, SeasonTag, FlameQuestsCard |
| screens/Gift70.kt | 190 | 4 | 2 | 0 | 0 | 0 | 5 | 1 | 3 | 3 | 0 | 🔴 | ShopItemPreview, ShopItemPicker, GiftItemSheet, GiftItemCard |
| screens/Screens56.kt | 381 | 6 | 7 | 0 | 2 | 1 | 12 | 5 | 5 | 6 | 0 | 🔴 | GalleryScreen, GalleryTile (Dialog), PetView (illustration), PetCard, PetScreen, QuestCard, QuestScreen, SecretsCard, SeasonCard |
| screens/Screens57.kt | 268 | 7 | 7 | 0 | 4 | 3 | 0 | 3 | 3 | 15 | 1 | 🔴 | FriendCheersCard, StatusRow, StatusSheet, BriefingCard, WeekImageCard, MomentsCard, BeforeAfterDialog, TimelapseDialog |
| screens/Screens60.kt | 326 | 1 | 2 | 0 | 0 | 1 | 8 | 0 | 2 | 14 | 2 | 🔴 | GiftSheet, GiftCard, FriendPrivateCard, BirthdayCard (DatePickerDialog), MemoriesCard, QuoteCard, ChallengeCard, AdventCard, RecordCard, WeekWeatherCard, BreathSheet |
| screens/SettingsScreen.kt | 914 | 2 | 1 | 0 | 0 | 2 | 0 | 0 | 3 | 23 | 0 | 🟡 | ScreenFrame, SettingsScreen, TimeButton (TimePickerDialog), CatHero, Reminders, Account, GuestUpgrade, PasswordCard, VaultCard, VaultForm, Weather, Privacy, Data, A11yRows, Help, UpdateCard (CircularProgressIndicator), ExportRow, BackupCard |
| screens/AppearanceScreen.kt | 524 | 7 | 10 | 0 | 6 | 0 | 0 | 2 | 7 | 7 | 4 | 🔴 | AppearancePage, LivePreview, PreviewLine, Head, SegFill, Carousel, Tile, ThemeSection, ShopModesTile, ColorSection, Swatch, BackgroundSection (flou), MiniBackdrop, IconSection, EffectsSection, LanguageSection, AdvancedSection |
| screens/HomeSettings.kt | 234 | 4 | 4 | 0 | 2 | 0 | 0 | 0 | 3 | 3 | 0 | 🔴 | HomeSettings (glisser-déposer), ComfortSettings, StylePicker, LayoutPicker |
| screens/WidgetsSettings.kt | 254 | 4 | 3 | 0 | 0 | 0 | 14 | 0 | 0 | 2 | 6 | 🔴 | WidgetsSettings, Frame, PBar, 9 aperçus (ancienne palette marine), QuickTilesCard |
| screens/PlacesSettings.kt | 247 | 3 | 2 | 2 | 3 | 0 | 0 | 1 | 1 | 8 | 1 | 🔴 | PlacesSettings, PlaceEditor, MapBtn, StyledMap |
| screens/AiSettings.kt | 164 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1 | 6 | 0 | 🟡 | AiSettings, PersonaGroup, MemoryCard |
| screens/HealthSettings.kt | 101 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 5 | 0 | 🟡 | HealthSettings |
| screens/PerformanceScreen.kt | 159 | 2 | 1 | 0 | 0 | 0 | 2 | 2 | 2 | 2 | 0 | 🔴 | PerformancePage, ScoreRing, Bench |
| screens/AuthScreen.kt | 173 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 8 | 2 | 🟡 | AuthScreen, LegalLine, LegalLinks, MaintenanceScreen, AuthHero (halo radial) |
| sheets/SheetHost.kt | 248 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 9 | 0 | 🔴 | SheetHost (ModalBottomSheet), SheetHead, SheetFoot, DateField (DatePickerDialog), DayChips, FriendPicker (RadioButton/Checkbox), ConfirmSheet, RestSheet, VacationBlock |
| sheets/GoalSheets.kt | 847 | 5 | 3 | 0 | 0 | 1 | 1 | 3 | 4 | 54 | 3 | 🟡 | GoalFormSheet, StepBar, ModeCard, GoalSummary, TypeTile, GoalSheet, GoalBanner, GoalSheetBody, Info, GoalActions, GoalDetail, DuelSide, ProjectTasks (Checkbox), ProgressSheet, GeoRow |
| sheets/GoalExtras.kt | 372 | 10 | 2 | 0 | 2 | 1 | 0 | 1 | 5 | 20 | 0 | 🔴 | StepsBlock (Checkbox), ChainRow, ProofsBlock, ProofTile, DuoRow, AgendaRow, OptionsCard, OptionRow, SavingsCard, SavingsRow, GoalStatsBlock |
| sheets/GoalPacks.kt | 113 | 2 | 1 | 0 | 0 | 2 | 0 | 0 | 1 | 3 | 0 | 🔴 | PacksStep (Checkbox) |
| sheets/DaySheets.kt | 111 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 7 | 0 | 🟡 | MiniGoal, EveningSheet, HardDaySheet |
| sheets/MiscSheets.kt | 187 | 3 | 6 | 0 | 3 | 0 | 0 | 0 | 8 | 9 | 0 | 🔴 | AvatarSheet, LegalSheet, NotesSheet, ExportSheet, GoalGroupsSheet, IconPick |
| sheets/MoreSheets.kt | 268 | 4 | 1 | 0 | 0 | 0 | 1 | 0 | 2 | 22 | 1 | 🟡 | RemindRow, AutoPlaceRow, OutdoorRow, MissSheet, RewardsCard, RewardSheet, MiniRow, GoalQrSheet, ImportGoalSheet, NfcRow |
| sheets/Sheets41.kt | 278 | 3 | 4 | 0 | 1 | 3 | 0 | 1 | 6 | 9 | 3 | 🔴 | LikersSheet, HourHeatCard, TrialRow, TrialCalendar, FriendsRankCard, ProofStory, ProofImage (Dialog), OfflineChip, SyncedChip, PendingSheet |
| sheets/Sheets56.kt | 95 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 7 | 0 | 🟡 | PauseBanner, PauseRow, LadderRow, Stepper, PinRow |
| sheets/Sheets57.kt | 33 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 🟢 | PlanBRow |
| sheets/SocialSheets.kt | 564 | 7 | 8 | 0 | 3 | 0 | 10 | 2 | 4 | 49 | 1 | 🔴 | UserSheet, NicknameEditor, NotifsSheet, ShareHubSheet, Opt, Quote, ShareTarget, ShareFeedSheet, ShareTextSheet, ComposeSheet, StoryNewSheet, StoryCard, ReportSheet, AppealSheet, ModUserSheet, Block, GroupNewSheet, GroupInfoSheet, DeleteAccountSheet |
| fullscreen/FullscreenHost.kt | 519 | 10 | 7 | 2 | 2 | 0 | 1 | 16 | 3 | 14 | 1 | 🔴 | FullscreenHost, Confetti, FsFrame, Pop, PerfectFs, CountUp, BadgeFs, RecapFs, RecapDecor, HeroText, HeroCount, CoachWord, Floaters, PartView, StoriesFs |
| fullscreen/FocusFs.kt | 90 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 6 | 1 | 🟡 | FocusFs |
| fullscreen/IntervalsFs.kt | 115 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 7 | 1 | 🟡 | IntervalsFs |
| fullscreen/Tutorial.kt | 227 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 9 | 1 | 🟡 | TutorialFs, tutorialPages |
| `sys/TodayWidget.kt` + `res/xml/widget_*.xml` + `res/drawable/widget_*` | 528 | — | — | — | — | Glance `LinearProgressIndicator` | 20+ | — | — | — | — | 🔴 | 9 widgets : Today, Streak, Flame, Quote, Week, Timeline, Cap, Progress, Quick (ancienne palette marine #0D0E2B) |
| `res/layout/notif_row.xml`, `notif_morning.xml` | — | — | — | — | — | — | — | — | — | — | — | 🔴 | notifications personnalisées (RemoteViews) |
| `wear/…/WearActivity.kt`, `Today.kt`, `StreakComplication.kt` | 187 | — | — | — | — | Wear `Chip` | 5 | — | — | — | — | 🔴 | appli montre (liste du jour), tuile, complication |

**Total : 72 entrées** couvrant les **74 fichiers d'interface** (68 fichiers visuels + 1 ligne pour les 6 fichiers non visuels) et 3 entrées hors application (9 widgets, 2 notifications, Wear), soit **≈ 450 surfaces** (composables visibles listés). **71 entrées à modifier** : **37 🔴 non refaites**, **29 🟡 partielles**, **5 🟢 à adapter au fond seulement** (plaques, transparence) ; 1 entrée ⚪ (6 fichiers) sans changement. Aucune entrée n'est laissée en Material brut.

### 9.2 Socle

**theme/Theme.kt**
1. Ajouter les jetons §3 (`sheetScrim`, `dialogScrim`, `heat`, `selTint`, `focusEdge`, `mediaBg`) dans `Pal` ; `glass`, `glassStrong`, `glassEdge` = `surface` / `edge` puis suppression en fin de lot.
2. `ShopBg` (core `Shop.kt`) : champs `focus: Float = .3f` et `tint: String?` (Loire #5E93CF / #2E4670, Montagnes #F59E8B / #3B2F5E…).
3. Remplacer les `Color(0x…)` restants par des constantes nommées (ambiances), sans changer les valeurs.

**Depth.kt** — supprimer `depth`, `glow`, `sheen`, `candy`, `candyOf`, `smoothShape` (après G1), `Pal.shade` ; garder `chunk`, `rememberPress`, `pressable`, `vivid`, `lift`, `deepen`.

**Motion.kt** — ajouter `Motion.sheetIn = spring(.72f, 380f)` (ouverture rebondissante), `Modifier.floatUp(trigger)` (+250 ml qui monte), `Modifier.popIn()` pour PMenu / PDialog.

**Components.kt**
1. Ajouter `Plaque`, `MiniPlaque`, `SectionPlaque`, `PSlider`, `PRadio`, `PRadioRow`, `PCheckbox`, `PCheckRow`, `PTextField` (évolution de `Field`, mêmes paramètres + `error`, `help`, `counter`), `PTextArea`, `PSearchField`, `PSelect`, `PMenu`, `PDialog`, `PTabs`, `PSpinner`, `PIndeterminate`, `PStepper`, `flameCrown` (remplace `flameAura`).
2. `FChip` : hauteur visible 44 + marche 4 (48), 2 lignes. `Chip` : 2 lignes. `Seg` : rail 56, segments 48, `maxLines = 2`, bascule en `PRadioRow` à 200 % (> 3 options).
3. `Field` : boutons de champ 48 ; devient un alias de `PTextField`.
4. `PSwitch` isolé : `minimumInteractiveComponentSize()` (60 × 48).
5. `SectionTitle` : variante `onScene = true` → `SectionPlaque` (défaut `true` hors carte).
6. `Empty` hors carte : contenu dans une carte (jamais sur la scène).
7. `PageBackdrop`, `decoCircles` : utilisés seulement par `AppBg` (« Nulle part ») ; `LocalHaze`, `EdgeFrost`, `aiGlow`, `washOf`, `glassBg` supprimés.
8. `PollOption` : barre de résultat en néon plein (pas d'alpha), choix = PRadio / PCheckbox.
9. `LevelRing` : anneau plein en `tierColors` (balayage autorisé, pas d'alpha) ; `Av` : `smoothShape` → cercle.

**Controls.kt**
1. `CheckButton` mode progrès : appui = `onStep()` (+1 pas), appui long = `onInput()` ; `floatUp` du libellé « +250 ml ».
2. `AvatarButton` : `chunk` circulaire marche 3 + `rememberPress` (remplace `bounce`).
3. `NavBar` : paramètre `edge` (= `barEdge`).
4. `Fab` : paramètre `lift` (remonte au-dessus d'un toast).
5. `TabTitle` → utilisé dans `TabHero` (plaque) ; `StreakPill` supprimé (inutilisé).

**SettingsKit.kt** — `SetGroup` : titre en `SectionPlaque` ; `SetHero` remplacé par `PageHero` ; ajouter `SetSlider(title, sub, value, steps)` (PSlider dans une ligne), `SetSelect` (PSelect), `SetRadioGroup` (PRadioRow).

**GoalUi.kt**
1. `GoalRow` : case de progrès « appui = +1 » (§4.16) ; rayons 7 hors §4 → 22 / 17 / 6.
2. `GoalCalendar`, `QtyChart`, `MeasureChart`, `WeekStrip` : rayons 4 → 6 ; jours manqués `surface2` ; aucune couleur translucide (blanc 1 occurrence → plaque).
3. `QuickSheet` (actions rapides) : `PSheet` + lignes de menu à marche.
4. `GoalCard` (grille Objectifs) : `chunk` 24.

**Swipe.kt** — fonds de balayage : Valider = aplat `ok` + coche prune ; Actions = aplat `monthly` ; texte prune.

**Backdrop.kt**
1. Nouveau `AppBg(where, level, key, progress)` (§2.1) ; supprimer `ConfinedBackdrop` ; `Backdrop` devient la couche 1 (sans `background(p.bg)` propre).
2. `CustomBg` : supprimer `Modifier.blur` et la lecture de `bgBlur` ; voile §2.8 ; point focal 0,5.
3. `ReactiveBg` → `Tide` (§2.7) ; confettis de journée parfaite une seule fois.
4. Horloge : `LocalBgPaused` étendu (défilement, feuille, économie d'énergie) ; `bgPhaseState` lit `PowerManager.isPowerSaveMode`.
5. Nouveau fond gratuit `pop` (« Collines braise », `popHills`, voir `e81/Kit.kt`) : défaut.
6. `WeatherFx` brouillard : bandes en aplats (plus de dégradés radiaux).

**Scenes.kt** — `SceneBg` reçoit `mode` de lumière (heure réelle / jour / suit le thème : 15 h en clair, 21 h 30 en sombre) ; rendu à 50 % (`OffscreenCanvas(scale = .5f)`) ; aucun autre changement (illustrations : couleurs et dégradés internes autorisés, ce sont des images).

**RenderCache.kt** — paramètre `scale` (défaut 1, 0,5 pour la scène plein écran) ; arrêt du rendu quand `LocalBgPaused`.

**Root.kt**
1. `PepsRoot` : `AppBg` sous tout, hors `hideLayer` ; le `Box(background(p.bg))` disparaît.
2. `Shell` : supprimer la boîte 360 dp (fond confiné / `decoCircles`) ; héros de chaque onglet en tête de liste ; `CompactBar` superposée (apparition quand `Fx.scroll > héros − 56`) ; `NavBar(edge = barEdge)`.
3. `TopBar` → `TabHero` (date en `MiniPlaque`, titre en plaque, `OfflineChip` / `SyncedChip` en mini-plaques) + `CompactBar` ; notes de maintenance / démonstration = `Note` dans une carte.
4. `Toasts` : position au-dessus de la barre ; le `Fab` remonte (`lift`).
5. `PullRefresh` : inchangé (médaillon à marche) ; `PullToRefreshBox` Material gardé pour le geste seulement (indicateur maison).
6. Volet latéral (≥ 900 dp) : `chunk(bg…)` déjà conforme ; scène visible autour.
7. Transitions de pages : la scène ne bouge pas (elle est sous les pages) ; pages sans fond.
8. Migration des préférences §2.9 au démarrage (une fois, drapeau `ui81`).

**EasterEggs.kt** — `EggsCard` : `Card` à marche ; overlay : particules en aplats ; aucun changement de fond.

**EmojiPicker.kt** — onglets de catégories en `PTabs` ; cases 44 `chunk` neutres ; `glass`/`smoothShape` supprimés ; recherche `PSearchField`.

**ThemePreview.kt** — cartes d'aperçu `chunk` 24 ; pastilles de couleur sur plaque ; mini-scène figée (`still`) avec voile Équilibré ; texte sur aplat prune.

**IconPaths.kt** — ajouter `mail`, `at`, `chevronup`, `sliders`, `calendarplus`.

### 9.3 Onglets

**screens/TodayScreen.kt** — héros 150 en tête de `ScreenList` ; `SectionTitle` → `SectionPlaque` ; espacement 10 / 12 (§6.4) ; `Board` « Le plan de la semaine » : « Oui » → bouton de type (néon de l'objectif) ; « Journée parfaite » → type `monthly` ; « Confirmer » → secondaire ; « Plan B fait » → type ; état vide « Ajouter une routine » reste le seul principal ; `RecapCard`, `WeatherCard`, `QuickTools`, `ContextCards`, `WrappedCard` : cartes à marche, aucun blanc translucide (3) ; `HeroFlame` : médaillon à marche qui s'enfonce ; `DayCount` : plaque.

**screens/Home61.kt** — `StreakMedal` : `chunk` + press ; `MomentsRow` : apparition fondu + glissement, place réservée ; `CountdownCard` : date via `PDatePicker` ; `HomeSectionHead` → `SectionPlaque` ; `StreakSheet`, `HomeCardSheet`, `QuickCmdSheet` (champ `PTextField`), `QuotesSheet`, `WalletSheet` : `PSheet`, un seul principal ; 3 blancs translucides → pilules d'info réglementaires ou plaques ; `Color(0x…)` → jetons.

**screens/Today56.kt** — `TimelineCard` (frise : rail `rail`, pastilles néon, heure courante = ligne flamme 2 dp), `CatchUpCard` (« Oui, fait » → type), `EveningQuickCard` (seul principal) : cartes à marche, verre supprimé.

**screens/AiCoachCard.kt** — pilule ✨ dans l'en-tête de section au lieu d'une carte lumineuse ; `AiTipCard` : carte à marche, bordure `program` 2 dp ; « en train de réfléchir » = `PSpinner`.

**screens/ServerUpdateCard.kt** — `CircularProgressIndicator` → `PSpinner` ; `UpdateProgress` → `Bar` / `PIndeterminate` ; `HostKeyBox` : boîte `surface2` rayon 18, texte monospace 13 ; `smoothShape` ×2 et rayons 3 → §4.

**screens/ChallengesScreen.kt** — `PageHero` (🏆, `challenge`) ; cartes de défi à marche avec tuile emoji 52 ; `NewChallenge` : `PSheet` + `PTextField` + `PDatePicker`.

**screens/GoalsScreen.kt** — héros 120 ; `StatGrid` néon ; `PSearchField` ; puces 48 ; `OverTile` : tuile à marche ; 1 `Color(0x…)` → jeton.

**screens/SocialScreen.kt** — héros 120 + `PTabs` Feed | Amis | Messages (badges en pastilles roses sur l'onglet).

**screens/FeedScreen.kt** — `PostCard` : carte à marche (verre ×5, `shadow`, rayons ×8 supprimés) ; `StoriesBar` : bulles 64 dans l'anneau flamme sur plaque ; `ActionPill`, `CheerButton` : capsules à marche (les « bounce » → press) ; `PollView` : `PollOption` refait ; `BanBanner`, `GelCard` : cartes `duel` / `ice` à marche ; `PhotoViewer` : visionneuse `mediaBg` (Dialog plein écran autorisé, boutons ronds sombres) ; `CommentsScreen` : `ScreenFrame` + `PageHero` (💬) + `Composer` ; `CommentBubble` = `Bubble` reçue ; menus contextuels → `PMenu`.

**screens/FriendsScreen.kt** — `RequestsCard`, `FriendsTodayCard`, `PodiumCard` (marches de podium en aplats néon), `FriendRow`, `PersonRow`, `InviteCard`, `SuggestCard`, `SearchCard` (`PSearchField`), `MessagesCard` : lignes 60 dp dans des groupes à marche ; avatars cercles ; `Verified` : pastille `objective` + coche prune ; verre ×3, `smoothShape` ×7 supprimés.

**screens/ProfileScreen.kt** — `ProfileHero` → carte identité + `AvatarMedallion` + `flameCrown` (§4.15) ; `LookCard`, `FlameSkinsCard` (tuiles de flamme à marche, choisie = bord `routine`), `NearBadgesCard`, `BadgeGridCard`, `BadgesCard` (médailles en aplats, `BadgeMedal` sans dégradé translucide), `StoryArchiveCard` / `StoryRow` / `StoryThumb` (vignettes rayon 18) ; `StoryHistoryScreen`, `BadgeScreen` : `PageHero` ; `shadow`, `smoothShape` ×8, blancs translucides ×2, `bounce` ×3 → §6.

**screens/Levels63.kt** — `LevelsSheet` : `PSheet` ; paliers = lignes à marche avec flamme du palier, palier courant = carte flamme, à venir = cadenas.

**screens/ModerationScreen.kt** — héros 120 + `PTabs` (Signalements, Comptes, Appels, Serveur…) ; `StatusChips` = `FChip` ; `Tiles` = `StatGrid` ; `ModPostCard` : carte à marche ; `ModMaint` : `PDatePicker` + `PTimePicker` (plus de dialogues Android) ; `Meter`, `SparkBars` : barres néon pleines ; 5 principaux → 1 par onglet.

### 9.4 Pages poussées

**screens/SettingsScreen.kt — `ScreenFrame`** (sert à 20 pages) : fond transparent (plus de `PageBackdrop`), `PageHero` en premier élément, `CompactBar` à la place de la barre `drawRect(bg)`, mode une main = barre compacte en bas ; espacement 12. **Paramètres** : `PSearchField` ; catégories en groupes avec `SectionPlaque`. **Rappels** : `TimeButton` → `PSelect` + `PTimePicker`. **Compte** : `PTextField` (pseudo, e-mail, mot de passe avec bouton 48), `PasswordCard`, `VaultCard`/`VaultForm`, `GuestUpgrade` ; déconnexion / suppression → `PDialog` destructif. **Météo** : recherche `PSearchField`, résultats en lignes. **Confidentialité** : visibilité par défaut `Seg` → `PRadioRow` (4 options). **Données** : `ExportRow`, `BackupCard`. **A11y** : taille du texte `PSlider` (85-200 %, crans de 15 %), densité `Seg`, `A11yRows`. **Aide** : liens en lignes. `UpdateCard` : `PSpinner`. 10 principaux → 1 par page.

**screens/AppearanceScreen.kt** — `LivePreview` : téléphone miniature `chunk` avec mini-scène figée + voile ; `SegFill` → `Seg` / `PRadioRow` ; `Carousel` / `Tile` : tuiles à marche 78 × 104 (choisie = bord `focusEdge` 3 dp) ; `ThemeSection`, `ShopModesTile`, `ColorSection` / `Swatch` (pastilles 44 à marche, choisie = coche prune) ; **`BackgroundSection`** : galerie + « Où voir ton fond » + « Présence du fond » + « Ma photo » (voile, plus de flou) (§2.8-2.9) ; `MiniBackdrop` figé ; `IconSection` ; `EffectsSection` : Fond animé, Lumière de la scène (`PSelect`), Fond qui réagit, Fond à la météo, effet animé (`FChip`) ; `LanguageSection` → `PSelect` + `PMenu` ; verre ×6, `smoothShape` ×7, rayons ×10 → §6.

**screens/HomeSettings.kt** — liste réordonnable : lignes à marche avec poignée `grip` 48 ; ligne saisie = soulevée (marche 6, échelle 1,02, `graphicsLayer`) ; `StylePicker`, `LayoutPicker` : tuiles à marche ; `ComfortSettings` : `SetToggle`.

**screens/WidgetsSettings.kt** — aperçus redessinés avec la palette Braise Pop des widgets (§9.7) : fond crème / prune nuit, cartes blanches / #241A2B à marche, flamme + prune ; plus aucune couleur marine ; 6 tailles hors échelle.

**screens/PlacesSettings.kt** — `StyledMap` dans une carte rayon 24 (bord 2, pas d'ombre : `shadow` ×2 supprimés) ; `MapBtn` = `RoundBtn` ; `PlaceEditor` : `PSheet` + `PTextField` + rayon en `PSlider` (100 m → 2 km) ; verre ×3.

**screens/AiSettings.kt** — `PersonaGroup` : `PRadioRow` avec emoji ; `MemoryCard` : lignes + suppression `PDialog` ; installation : `Bar` + `PSpinner`.

**screens/HealthSettings.kt** — permissions en lignes `SetItem` ; 3 principaux → 1 (« Connecter Health Connect ») ; rayon hors §4.

**screens/PerformanceScreen.kt** — `ScoreRing` : `Ring` ; `Bench` : barres néon ; particules en `PSlider` ; 2 `Color(0x…)` et 2 translucides → jetons.

**screens/ChatScreen.kt** — §4.14 : `CompactBar` (verre ×13, `shadow` ×5, `smoothShape` ×17, rayons ×19 supprimés) ; `MessageRow` → `Bubble` ; `MsgText` ; `VoicePlayer` (bouton rond flamme, forme d'onde en barres `rail` / prune) ; `ChatPoll` / `ChatPollForm` (`PTextField`, `PCheckbox` « choix multiple ») ; `Composer` : **`TextField` Material → `PTextField` capsule** ; `VoiceRecorderBar` ; `DuoInvite` (carte néon `duel`) ; menu de message → `PMenu` avec réactions ; suppression → `PDialog` ; envoi d'image : `PSpinner` ; fond : fond de conversation ou `AppBg` (plus de `PageBackdrop`).

**screens/Chat59.kt** — `StickerPicker` : `PTabs` + grille de cases à marche ; `ChatSearchBar` : `PSearchField` + boutons ronds ‹ › ; `SchedulePanel` : `PDatePicker` / `PTimePicker` ; `ScheduledStrip`, `ShotNotice`, `VoiceTranscript` : `MiniPlaque` / cartes ; `EmojiBurst`, `JumboEmojis` : inchangés (emojis).

**screens/Screens58.kt** — `ChatBgPicker` : `PSheet`, tuiles 78 × 104 à marche (identiques à Apparence) ; tailles hors échelle ×2.

**screens/CoachChatScreen.kt** — `ScreenFrame` + `CompactBar` ; `CoachAvatar` : médaillon `program` ; `Bubble` coach = reçue avec pastille ✨, utilisateur = à moi ; `Writing` = `PSpinner` ; `Welcome` : carte + suggestions en `FChip` ; verre ×3.

**screens/SearchScreen.kt** — `PageHero` (🔍) + `PSearchField` collé sous le héros (passe dans la `CompactBar` au défilement) ; résultats groupés (`SectionPlaque` + groupes à marche).

**screens/StatsScreen.kt** — §7 : `PTabs` de période ; carte de chaleur `heat0..4` (plus `flame.copy(alpha)` ni `ink.copy(.07)`) ; `HealthStatsCard`, `InsightsCard` (barres néon), `YearPixels` (5 niveaux pleins), `HourHeatCard` ; « Pourquoi ça coince » : `ProgLine` néon ; rayons 3 → 6.

**screens/WeekScreen.kt** — `PageHero` (📅) ; `DayCard` : carte à marche, jour courant = bord `focusEdge`, pastilles du jour (flamme / 🧊 / pointillés) comme la carte Série.

**screens/JournalScreen.kt** — `MoodPicker` : 5 boutons emoji 56 à marche, choisi = aplat néon ; `MoodCard`, `MoodCalendar` (cases 40 en aplats néon par humeur) ; saisie `PTextArea` ; rayons ×5.

**screens/LeagueScreen.kt** — `PageHero` (🏆) ; `LeagueCard` : classement en lignes, zone de montée = fond `selTint routine`, zone de descente = `selTint duel`, moi = bord `focusEdge` ; podium en aplats ; `smoothShape` ×3.

**screens/ShopScreen.kt** — §7 Boutique : onglets maison (`glassStrong`) → `PTabs` ; `AppIconPreview`, `ModeCard`, cartes de thème (aperçu de scène rayon 18 + pastilles de couleur sur plaque, libellé de scène sur `MiniPlaque` au lieu de `Color.Black.copy(.25)`), `GiftBtn` (rond à marche), `PriceTag` (puce or à marche / « Actif » / « Utiliser » / cadenas), `SeasonTag` (étiquette néon), `FlameQuestsCard` (carte flamme) ; blancs translucides ×4.

**screens/Gift70.kt** — `GiftItemCard` : carte aplat `gold` à marche, texte **prune** (plus `0xFF2A1408` / `0xFF3A2010`), ruban en aplat `deadline` ; `ShopItemPicker` : grille de tuiles ; `GiftItemSheet` : `PSheet` ; `smoothShape` ×4.

**screens/Screens56.kt** — `GalleryScreen` : `PageHero` (🖼️), vignettes rayon 18 à marche ; grand format = visionneuse `mediaBg` ; `PetScreen` / `PetCard` : `PageHero` (🐣), le compagnon (`PetView`) garde ses couleurs d'illustration, cartes autour à marche ; `QuestScreen` / `QuestCard` : étapes en lignes cochables `PCheckbox` ; `SecretsCard`, `SeasonCard` : cartes ; dégradé noir 55 % (ligne 276) → plaque ; 12 `Color(0x…)` (dessin du compagnon) autorisés.

**screens/Screens57.kt** — `FriendCheersCard`, `BriefingCard`, `WeekImageCard`, `MomentsCard` : cartes à marche ; `StatusSheet` / `StatusRow` : `PSheet`, choix d'emoji en grille à marche ; `BeforeAfterDialog` / `TimelapseDialog` / aperçu : visionneuses `mediaBg` avec curseur `PSlider` (avant / après) et boutons ronds ; verre ×4, `smoothShape` ×7.

**screens/Screens60.kt** — `GiftSheet`, `BreathSheet` (cercle de respiration en aplats néon `period`, phases en plaque) : `PSheet` ; `GiftCard`, `BirthdayCard` (date via `PDatePicker`), `MemoriesCard`, `QuoteCard`, `ChallengeCard`, `AdventCard` (cases 44 à marche ; aujourd'hui = bord `gold` 3 dp ; ouvertes = aplat `ok` + coche prune ; plus de `0xFFE8412C` / `0xFFFFD25A`), `RecordCard`, `WeekWeatherCard` : cartes ; texte prune sur aplats ; 7 principaux → 1 par carte au plus.

**screens/AuthScreen.kt** — `AppBg` (« Collines braise ») ; `AuthHero` : **halo radial supprimé**, flamme 112 dans un médaillon blanc à marche, tuiles d'emoji à marche autour ; formulaire `PTextField` ; `LegalLinks` en mini-plaques ; `MaintenanceScreen` : carte + `PSpinner` ; tailles hors échelle ×2.

### 9.5 Feuilles

**sheets/SheetHost.kt** — `ModalBottomSheet` → **`PSheet`** (§4.13) ; `SheetHead` : poignée 48, titre Unbounded 22, `RoundBtn` fermer ; `SheetFoot` : bouton principal pleine largeur, collé en bas avec fond `bg` + filet 2 dp `edge` ; `DateField` → `PSelect` ouvrant `PDatePicker` ; `DayChips` : puces 48 ; `FriendPicker` : `PRadioRow` / `PCheckRow` avec avatars ; `ConfirmSheet` : `PDialog` si la question tient en 2 lignes, sinon feuille ; `RestSheet`, `VacationBlock` : `PDatePicker`.

**sheets/GoalSheets.kt** — `GoalFormSheet` : étapes (`StepBar` = barre flamme 8 + pastilles), `TypeTile` (grille 3 × 3 à marche, planche `P-e81-sheet.png`), `ModeCard`, `GoalSummary` ; cible chiffrée : `PTextField` + `PSlider` ; un seul principal (`SheetFoot`) ; **`GoalSheet`** : `GoalBanner` pleine largeur, carte d'action avec ajout rapide (§4.16), `Info`, `GoalActions` (Pause / Archiver secondaires, Supprimer danger → `PDialog`), `GoalDetail`, `DuelSide` (deux colonnes néon), `ProjectTasks` : **`Checkbox` → `PCheckRow`** ; `ProgressSheet` : `PStepper` + `PTextField` + raccourcis de type ; `GeoRow` ; 17 principaux → 1 par feuille ; 3 translucides → pilules réglementaires.

**sheets/GoalExtras.kt** — `StepsBlock` : **`Checkbox` → `PCheckRow`** ; `ChainRow`, `DuoRow`, `AgendaRow`, `OptionRow`, `SavingsRow` : lignes 60 à marche dans un groupe ; `ProofsBlock` / `ProofTile` : vignettes rayon 18 ; `OptionsCard`, `SavingsCard`, `GoalStatsBlock` : cartes ; `smoothShape` ×10, verre ×2.

**sheets/GoalPacks.kt** — `PacksStep` : **`Checkbox` → `PCheckRow`** (couleur du type au lieu de `checkedColor = flame`) ; packs en cartes à marche avec emoji 34.

**sheets/DaySheets.kt** — `EveningSheet`, `HardDaySheet` : `PSheet`, `MiniGoal` = ligne à marche ; 2 principaux → 1.

**sheets/MiscSheets.kt** — `AvatarSheet` (photo, emoji, picto, couleurs : `PTabs` + grilles à marche ; aperçu dans `AvatarMedallion`) ; `LegalSheet`, `NotesSheet` (texte long : titres Unbounded 18, paragraphes 15 Bold), `ExportSheet`, `GoalGroupsSheet` (dossiers : pastille néon + `PTextField`), `IconPick` ; verre ×3, rayons ×6.

**sheets/MoreSheets.kt** — `RemindRow` (heure : `PTimePicker`), `AutoPlaceRow`, `OutdoorRow`, `NfcRow` : lignes de réglage ; `MissSheet` (raisons en `PRadioRow` avec emoji), `RewardsCard`, `RewardSheet`, `GoalQrSheet` (QR sur carte blanche à marche), `ImportGoalSheet` ; 7 principaux → 1 par feuille.

**sheets/Sheets41.kt** — `LikersSheet`, `PendingSheet` : listes de personnes ; `HourHeatCard` : `heat0..4` ; `TrialRow` / `TrialCalendar` : cases à marche ; `FriendsRankCard` ; `ProofStory` / `ProofImage` (Dialog) : visionneuse `mediaBg` ; `OfflineChip`, `SyncedChip` : `MiniPlaque` (dans le héros) ; tailles ×3, rayons ×4.

**sheets/Sheets56.kt** — `Stepper` local → `PStepper` ; `PauseBanner` (carte `ice` à marche, emoji ⏸️), `PauseRow`, `LadderRow`, `PinRow`.

**sheets/Sheets57.kt** — `PlanBRow` : ligne à marche (conforme) ; vérifier le bouton « Plan B fait » = type.

**sheets/SocialSheets.kt** — `UserSheet` (profil d'un ami : `AvatarMedallion` + couronne à son niveau, `StatGrid`, actions : 1 principal) ; `NicknameEditor` (`PTextField`) ; `NotifsSheet` (lignes avec pastille non lu `pink`) ; `ShareHubSheet` / `ShareTarget` / `Opt` (grille de cibles 64 à marche) ; `ShareFeedSheet`, `ShareTextSheet`, `Quote` ; `ComposeSheet` (`PTextArea`, pièces jointes en vignettes, visibilité `PSelect`) ; `StoryNewSheet` / `StoryCard` (fonds de story = aplats néon, texte prune ; récap « RÉCAP PEPSGOAL » en prune) ; `ReportSheet` / `AppealSheet` (`PRadioRow` motifs + `PTextArea`) ; `ModUserSheet` (`PMenu` d'actions, durée en `PRadioRow`) ; `Block` ; `GroupNewSheet` / `GroupInfoSheet` (`PTextField`, `FriendPicker`) ; `DeleteAccountSheet` (`PDialog` destructif + saisie de confirmation) ; 10 `Color(0x…)` → jetons ; 9 principaux → 1 par feuille.

### 9.6 Plein écran

**fullscreen/FullscreenHost.kt** — `FsFrame` : scène à voile 0 (`AppBg` présence Intense) au lieu des fonds propres ; `PerfectFs`, `BadgeFs`, `CountUp`, `HeroText`, `HeroCount`, `Pop` : emoji / flamme 160 en `pop`, chiffre `celebration` sur plaque, bouton principal « Continuer » ; `RecapFs` / `RecapDecor` / `PartView` / `CoachWord` / `Floaters` : pages de récap sur aplats néon pleine page (texte prune), décor en aplats (plus de dégradés radiaux translucides : 16 blancs / noirs translucides) ; `StoriesFs` : `mediaBg`, barres de progression `rail` / blanc plein, en-tête sur plaque sombre ; `shadow` ×2, `smoothShape` ×10, rayons ×7.

**fullscreen/FocusFs.kt** — `AppBg` présence Intense ; minuteur dans un anneau `Ring` 240 ; commandes : un principal (Pause) + secondaires ronds ; taille hors échelle.

**fullscreen/IntervalsFs.kt** — idem ; phase courante = carte néon (travail = `duel`, repos = `ice`) avec texte prune.

**fullscreen/Tutorial.kt** — fond `AppBg` (« Collines braise ») ; chaque page : emoji 96 dans un médaillon à marche, titre Unbounded 28 sur plaque, texte dans une carte ; pastilles de page ; un principal (« Suivant ») ; `PageBackdrop` supprimé.

### 9.7 Hors application

**sys/TodayWidget.kt + res/drawable/widget_* + res/xml/widget_*.xml** — palette Braise Pop : fond `#FFF7F0` / `#170F1C`, cartes `#FFFFFF` / `#241A2B` avec **marche dessinée en `layer-list`** (rectangle `edge` décalé de 3 dp sous la carte), bouton flamme `widget_flame_btn` (dégradé #FFB000 → #FF4A12 → #FF3D96 + marche #C2185B) avec « + » **prune** (plus de blanc), cases faites en néon du type + « ✓ » prune, encres `PLUM` / `#FFF4EC`, `ink2` #6B4F5C / #D9C4CF (plus de #0D0E2B / #C9CCF2 / #34375F) ; `LinearProgressIndicator` Glance : rail `surface2` + néon (pas d'alpha) ; rayons 24 (fond) / 18 (cartes) ; Material You (`you`) conservé en option. 9 widgets : Aujourd'hui, Série, Flamme, Citation, Semaine, Frise, Cap, Progrès, Raccourcis.

**res/layout/notif_row.xml, notif_morning.xml** — fond `surface`, texte `ink` / `ink2`, pastille néon du type à gauche, titre en gras ; couleur d'accent de la notification = #FF4A12.

**wear/** — `WearActivity` : thème `MaterialTheme(colors = Colors(primary = #FF4A12, secondary = #FFB000, background = #170F1C, surface = #241A2B, onPrimary = PLUM))` ; `Chip` : à faire = `surface` + bord `control` 2 dp, fait = aplat néon du type + texte prune (plus d'alpha .45) ; série en « 🔥 12 » Unbounded si la police est embarquée, sinon gras système ; `Today` (tuile) et `StreakComplication` : mêmes couleurs.

---

## 10. Ordre d'application (lots pour le développeur)

1. **Socle** : jetons, `AppBg` + migration des préférences, `Plaque`s, `TabHero` / `PageHero` / `CompactBar`, nouveaux contrôles (`Components.kt`), `PSheet`, `PDialog`, `PMenu`, `PDatePicker` / `PTimePicker`.
2. **Coquille** : `Root.kt` (fond racine, héros, barre compacte, barre teintée, toasts), `ScreenFrame`.
3. **Accueil et fiche** : `TodayScreen`, `Home61`, `Today56`, `GoalUi`, `GoalSheets`, `GoalExtras` (ajout rapide, cap +1).
4. **Social** : `FeedScreen`, `FriendsScreen`, `ChatScreen`, `Chat59`, `Screens58`, `CoachChatScreen`, `SocialSheets`.
5. **Profil et boutique** : `ProfileScreen` (couronne), `Levels63`, `ShopScreen`, `Gift70`, `Screens56/57/60`.
6. **Réglages** : `SettingsScreen` et ses 14 pages, `AppearanceScreen`, `HomeSettings`, `WidgetsSettings`, `PlacesSettings`, `AiSettings`, `HealthSettings`, `PerformanceScreen`.
7. **Feuilles restantes** : `SheetHost`, `MiscSheets`, `MoreSheets`, `Sheets41/56/57`, `DaySheets`, `GoalPacks`.
8. **Plein écran et divers** : `FullscreenHost`, `FocusFs`, `IntervalsFs`, `Tutorial`, `AuthScreen`, `EasterEggs`, `EmojiPicker`, `ThemePreview`, `ModerationScreen`, `Stats`, `Week`, `Journal`, `League`, `Search`, `Challenges`.
9. **Hors app** : widgets, notifications, Wear.
10. **Nettoyage** : suppression des fonctions mortes (G13), `smoothShape`, `glass*`.
11. **Mesures** : modules `:benchmark` et `:baselineprofile` (§8), puis check-list §11.

---

## 11. Check-list d'audit 8.1 (numérotée)

Chaque ligne se vérifie dans le code (commande indiquée), sur capture, ou à l'outil. Les points de la check-list 8.0 (§12 de `design-system.md`) restent à repasser.

### 11.1 Règles transverses (R)

| # | Vérification | Méthode |
|---|---|---|
| R1 | Aucun `smoothShape` hors `EmojiTile` / `Depth.kt` | `grep -rn smoothShape ui/ \| grep -v EmojiTile` = 0 |
| R2 | Aucun `.shadow(` | `grep -rn "\.shadow(" ui/` = 0 |
| R3 | Aucun `glassStrong`, `glassEdge`, `p.glass`, `glassBg`, `LocalHaze`, `EdgeFrost`, `aiGlow`, `washOf`, `sheen(`, `glow(`, `depth(`, `candy(` | grep = 0 |
| R4 | Aucun `blur`, `RenderEffect`, `haze` | `grep -rnE "blur\|RenderEffect\|haze" ui/` = 0 (hors noms de variables de `Scenes.kt`) |
| R5 | Aucun composant Material visible : `material3.(Checkbox\|RadioButton\|TextField\|OutlinedTextField\|CircularProgressIndicator\|LinearProgressIndicator\|ModalBottomSheet\|AlertDialog\|DropdownMenu\|Slider\|TabRow\|Card\|Surface\|TextButton\|Button)` | grep = 0 (seuls `MaterialTheme`, `Typography`, `ColorScheme`, `LocalTextStyle`, `LocalContentColor`, `PullToRefreshBox` autorisés) |
| R6 | Aucun dialogue Android : `DatePickerDialog`, `TimePickerDialog`, `android.app.AlertDialog` | grep = 0 |
| R7 | `ui.window.Dialog` seulement dans `PDialog` et les visionneuses (§2.10) | grep : 2 à 5 occurrences, toutes listées |
| R8 | `Color(0x` seulement dans `theme/Theme.kt`, `Scenes.kt`, `Backdrop.kt` (effets), `Screens56.kt` (PetView), `EasterEggs.kt`, confettis | grep par fichier |
| R9 | Aucune taille de police hors échelle §3 / §6.1 G7 | `grep -oE "fontSize = [0-9.]+\.sp"` filtré = 0 |
| R10 | Au plus un `kind = "primary"` visible par écran / feuille / dialogue | revue fichier par fichier (liste §6.3) |
| R11 | Aucun `Text` / `Muted` / `SectionTitle` posé directement sur la scène | captures des 30 écrans (fond « Loire », Présence Intense) : tout texte est dans une carte, une plaque ou une barre |
| R12 | Tout élément pressable a une marche ; aucun `bounce(` | `grep -rn "bounce(" ui/` = 0 ; captures |
| R13 | Contrastes : textes ≥ 4,5:1, contours ≥ 3:1 (`design/tools/cr81.py` + `audit/contrast.py`) ; jamais `ok` sur `surface2` ; heure des bulles en prune plein | script, clair et sombre |
| R14 | Cibles ≥ 48 dp : FChip, Seg, PSwitch isolé, boutons de champ, poignée de feuille, PRadio, PCheckbox, PSlider, actions des plaques | Accessibility Scanner : 0 alerte de taille sur Aujourd'hui, Objectifs, Social, Profil, Réglages › Apparence, fiche |
| R15 | 200 % : `Seg`, `Chip`, `FChip`, `PTabs` sur 2 lignes sans « … » ; `Seg` > 3 options devient une liste radio | capture à 200 % (Apparence, Confidentialité, Boutique) |
| R16 | Rayons §4 uniquement (17, 22, 24, 28, 32, capsule ; 6 pour ≤ 20 dp ; 18 champs / vignettes) | grep des rayons, liste des exceptions |
| R17 | Texte prune sur tout aplat vif (cartes cadeau, anniversaire, calendrier de l'Avent, récap, stories, widgets, montre) | captures + grep `0xFF3A2010`, `0xFF2A1408` = 0 |
| R18 | Espacements : lignes 10, cartes 12, sections 14 / 10 | Layout Inspector sur Aujourd'hui et Statistiques |

### 11.2 Arrière-plan (B)

| # | Vérification |
|---|---|
| B1 | Un seul `AppBg` dans l'arbre (Layout Inspector) ; aucun `PageBackdrop`, `ConfinedBackdrop`, `SceneBg` plein écran ailleurs (`grep`) |
| B2 | Nouvelle installation : fond « Collines braise », « Partout », Présence Équilibrée, Fond animé Oui, Fond qui réagit Oui |
| B3 | Migration : `bgTop = off` ⇒ Nulle part ; `on` / `auto` ⇒ Partout ; toast unique affiché une fois ; `blob` ⇒ `pop` ; plus aucune valeur « auto » |
| B4 | La scène est visible sous les 4 onglets, sous toutes les pages poussées (`ScreenFrame`, Messages, Coach) et au-dessus de chaque feuille (captures `P-e81-home`, `-stats`, `-sheet`) |
| B5 | Héros : 150 dp sur Aujourd'hui, 120 sur les autres onglets, 176 / 150 sur les pages ; titre en plaque ; point focal de la scène dans le héros |
| B6 | Parallaxe 0,35 plafonnée à 96 dp par `graphicsLayer` ; aucune recomposition pendant le défilement (Layout Inspector, compteur de recompositions) |
| B7 | Barre compacte opaque de 56 dp qui apparaît après le héros, sans animation de hauteur |
| B8 | Barre d'onglets et barre compacte : bord et marche teintés par la scène (`barEdge`) |
| B9 | Voile conforme au tableau §2.3 pour les 3 présences, clair et sombre |
| B10 | Voile de feuille 32 % / 50 % ; scène figée feuille ouverte |
| B11 | Marée : visible avec « Nulle part » ; hauteur 8 % → 34 % ; aucune animation infinie ; aucun dégradé radial |
| B12 | Image perso : aucun flou (`grep blur` = 0), voile réglable 0-80 %, clé `bgBlur` supprimée et migrée |
| B13 | Horloge figée : défilement, feuille, arrière-plan, économie d'énergie, animations réduites, échelle système 0 (log de `slowPhaseState`) |
| B14 | Scène rendue à 50 % de résolution, ≤ 12 i/s |
| B15 | Contraste élevé : aucune scène ; Réduire la transparence : scène seulement dans les héros ; OLED : voile noir |
| B16 | Exceptions §2.10 respectées (visionneuses en #000, fêtes à voile 0) |
| B17 | Macrobenchmark `todayScroll` : écart P90 Partout − Nulle part ≤ 1 ms |

### 11.3 Composants (C)

| # | Vérification |
|---|---|
| C1 | `Plaque`, `MiniPlaque`, `SectionPlaque` conformes §4.1 (sans marche, bord 2) |
| C2 | `PSlider` : rail 12 + contour `control`, pouce 30 à marche, crans, bulle, rôle progression, 48 dp |
| C3 | `PRadio` / `PRadioRow` : 28 dp, contour 2,5, aplat néon + pastille prune, rôle RadioButton, `selectableGroup` |
| C4 | `PCheckbox` / `PCheckRow` : 28 dp rayon 9, coche dessinée, état partiel, rôle Checkbox |
| C5 | `PTextField` : libellé, focus `focusEdge`, erreur avec icône, aide, compteur, boutons 48 |
| C6 | `PSelect` + `PMenu` : ouverture vers le haut si nécessaire, entrée `pop`, option choisie cochée, danger séparé |
| C7 | `PDialog` : marche 6, emoji 56, un principal ou bouton `duel` destructif, voile `dialogScrim` |
| C8 | `PDatePicker` / `PTimePicker` : feuilles Braise Pop, raccourcis, aucun dialogue Android |
| C9 | `PTabs` : néon de la page, défilement au-delà de 3, rôle Tab |
| C10 | `PSpinner` / `PIndeterminate` : aplats, figés en Animations réduites |
| C11 | `PStepper` : ronds à marche, répétition à l'appui long |
| C12 | `PSheet` : ouverture rebondissante (dépassement ≈ 12 dp), poignée `pop`, glisser pour fermer, retour prédictif, fondu en Animations réduites |
| C13 | Bulles : à moi aplat + prune + marche, reçue `surface` à marche, réactions en plaque, Composer avec un seul principal |
| C14 | Couronne de flammes : aplats sans transparence dégradée, taille selon le niveau, 15 i/s, arrêt hors écran / 20 s, figée en Animations réduites |
| C15 | Médaillon de la flamme, avatar de la barre et du Profil : s'enfoncent sur leur marche |
| C16 | Cap : appui = +1 pas, appui long = saisie, « +250 ml » qui monte, toast avec Annuler, actions TalkBack |
| C17 | Fiche : bouton de type « + pas », secondaire « + 2 pas », rond « Saisir », journal du jour avec Annuler |
| C18 | Bouton + : remonte au-dessus d'un toast |
| C19 | `FChip` 48 dp total ; `Seg` segments 48 dp |
| C20 | Rangée « Moments » : plus d'`AnimatedVisibility` en taille |

### 11.4 Surfaces, une ligne par fichier (S)

Pour chaque fichier : 0 occurrence des règles R1-R9 (sauf exceptions listées), changements du §9 faits, capture clair + sombre sur la scène « Loire ».

| # | Fichier / surface | Points précis à vérifier |
|---|---|---|
| S1 | theme/Theme.kt | jetons §3 ; `ShopBg.focus` / `tint` |
| S2 | Depth.kt | fonctions mortes supprimées |
| S3 | Motion.kt | `sheetIn`, `floatUp`, `popIn` |
| S4 | Components.kt | 17 nouveaux composants ; FChip / Seg / Field / PSwitch 48 ; `flameAura` remplacée |
| S5 | Controls.kt | CheckButton progrès +1 ; AvatarButton à marche ; NavBar `edge` ; Fab `lift` |
| S6 | SettingsKit.kt | `SetSlider`, `SetSelect`, `SetRadioGroup` ; `SetGroup` en plaque |
| S7 | GoalUi.kt | GoalRow +1 ; rayons ; QuickSheet en PSheet |
| S8 | Swipe.kt | fonds de balayage en aplats + prune |
| S9 | Backdrop.kt | `AppBg`, `Tide`, `popHills`, plus de blur ni de halo |
| S10 | Scenes.kt | lumière « suit le thème » ; rendu 50 % |
| S11 | RenderCache.kt | paramètre `scale` ; pause |
| S12 | Root.kt | fond racine ; héros + barre compacte ; barre teintée ; migration ; toasts |
| S13 | EasterEggs.kt | EggsCard à marche |
| S14 | EmojiPicker.kt | PTabs, cases à marche, PSearchField |
| S15 | ThemePreview.kt | cartes à marche, mini-scène voilée |
| S16 | IconPaths.kt | 5 pictos ajoutés |
| S17 | Aujourd'hui (TodayScreen.kt) | héros 150 ; SectionPlaque ; un seul principal ; 10 / 12 |
| S18 | Home61.kt | StreakMedal à marche ; Moments sans animation de taille ; PDatePicker ; feuilles PSheet |
| S19 | Today56.kt | 3 cartes refaites, verre supprimé |
| S20 | AiCoachCard.kt | pilule ✨ ; PSpinner |
| S21 | ServerUpdateCard.kt | PSpinner, rayons |
| S22 | ChallengesScreen.kt | PageHero ; NewChallenge en PSheet |
| S23 | Objectifs (GoalsScreen.kt) | héros 120 ; PSearchField ; puces 48 |
| S24 | Social (SocialScreen.kt) | PTabs avec badges |
| S25 | Feed (FeedScreen.kt) | PostCard ; StoriesBar ; PollView ; PhotoViewer #000 ; CommentsScreen ; PMenu |
| S26 | Amis (FriendsScreen.kt) | groupes à marche ; podium ; PSearchField |
| S27 | Messages (ChatScreen.kt) | CompactBar ; Bubble ; Composer PTextField ; PMenu ; PDialog ; PSpinner |
| S28 | Chat59.kt | StickerPicker ; ChatSearchBar ; SchedulePanel |
| S29 | Screens58.kt (fonds de conversation) | tuiles 78 × 104 |
| S30 | Coach IA (CoachChatScreen.kt) | CompactBar ; bulles ; PSpinner |
| S31 | Profil (ProfileScreen.kt) | couronne ; médaillon ; StatGrid ; Mon espace ; StoryHistory / Badge en PageHero |
| S32 | Levels63.kt | paliers à marche |
| S33 | Modération (ModerationScreen.kt) | PTabs ; PDatePicker / PTimePicker ; 1 principal par onglet |
| S34 | Recherche (SearchScreen.kt) | PageHero + PSearchField |
| S35 | Statistiques (StatsScreen.kt) | PTabs ; heat0..4 ; HourHeat |
| S36 | Ma semaine (WeekScreen.kt) | DayCard |
| S37 | Journal (JournalScreen.kt) | MoodPicker à marche ; PTextArea |
| S38 | Ligue (LeagueScreen.kt) | zones de montée / descente |
| S39 | Boutique (ShopScreen.kt) | PTabs ; cartes ; PriceTag ; plus de noir 25 % |
| S40 | Gift70.kt | carte cadeau prune sur or |
| S41 | Galerie / Compagnon / Quête (Screens56.kt) | PageHero ; visionneuse ; PCheckbox de quête |
| S42 | Screens57.kt | cartes ; StatusSheet ; visionneuses avant / après avec PSlider |
| S43 | Screens60.kt | Avent ; anniversaire PDatePicker ; BreathSheet ; texte prune |
| S44 | Paramètres + ScreenFrame (SettingsScreen.kt) | ScreenFrame transparent ; PageHero ; CompactBar ; PTimePicker ; PSlider taille du texte ; PDialog |
| S45 | Réglages › Rappels | PSelect heure |
| S46 | Réglages › Compte | PTextField ; PDialog destructif |
| S47 | Réglages › Météo | PSearchField |
| S48 | Réglages › Confidentialité | PRadioRow visibilité |
| S49 | Réglages › Données | lignes ; un principal |
| S50 | Réglages › Accessibilité | PSlider texte ; Seg 48 |
| S51 | Réglages › Aide | lignes |
| S52 | Apparence (AppearanceScreen.kt) | galerie ; Où voir ton fond ; Présence ; Ma photo voile ; Lumière (PSelect) ; Langue (PMenu) |
| S53 | Accueil (HomeSettings.kt) | réordonnancement à marche |
| S54 | Widgets (WidgetsSettings.kt) | aperçus Braise Pop |
| S55 | Emplacements (PlacesSettings.kt) | carte sans ombre ; rayon en PSlider |
| S56 | Assistant IA (AiSettings.kt) | PRadioRow persona ; PDialog mémoire |
| S57 | Santé (HealthSettings.kt) | 1 principal |
| S58 | Performance (PerformanceScreen.kt) | Ring ; PSlider particules |
| S59 | Connexion / maintenance (AuthScreen.kt) | plus de halo ; PTextField ; AppBg |
| S60 | SheetHost.kt | PSheet ; DateField → PDatePicker ; FriendPicker PRadio / PCheck ; ConfirmSheet |
| S61 | Fiche et formulaire (GoalSheets.kt) | TypeTile ; ajout rapide ; ProjectTasks PCheckRow ; ProgressSheet PStepper ; 1 principal |
| S62 | GoalExtras.kt | StepsBlock PCheckRow ; lignes à marche |
| S63 | GoalPacks.kt | PCheckRow |
| S64 | DaySheets.kt | soir / journée difficile |
| S65 | MiscSheets.kt | Avatar, Mentions, Notes de version, Export, Dossiers |
| S66 | MoreSheets.kt | rappel PTimePicker ; MissSheet PRadioRow ; QR |
| S67 | Sheets41.kt | ProofImage #000 ; OfflineChip / SyncedChip MiniPlaque ; HourHeat |
| S68 | Sheets56.kt | PStepper |
| S69 | Sheets57.kt | Plan B en type |
| S70 | SocialSheets.kt | UserSheet couronne ; Compose PTextArea ; Report / Appeal PRadioRow ; DeleteAccount PDialog ; stories prune |
| S71 | Fêtes et récap (FullscreenHost.kt) | scène voile 0 ; récap en aplats ; stories #000 |
| S72 | Focus (FocusFs.kt) | AppBg intense ; Ring |
| S73 | Fractionné (IntervalsFs.kt) | cartes de phase néon |
| S74 | Tutoriel (Tutorial.kt) | médaillon ; plaques ; 1 principal |
| S75 | Barre d'onglets (Root/Controls) | barEdge ; 48 dp |
| S76 | Barre du haut (Root) | héros + compacte, aucune animation de hauteur |
| S77 | Toasts (Root) | prune à marche ; le bouton + remonte |
| S78 | Tirer pour actualiser (Root) | médaillon à marche |
| S79 | Mode une main (Root.Reachable) | invite en MiniPlaque |
| S80 | Volet latéral grand écran (Root) | scène visible autour |
| S81 | Widgets (TodayWidget.kt + drawables) | 9 widgets en palette Braise Pop, « + » prune, marche en layer-list |
| S82 | Notifications (notif_row.xml, notif_morning.xml) | couleurs Braise Pop |
| S83 | Wear (WearActivity, Today, StreakComplication) | thème prune / flamme, chips néon + prune |
| S84 | Fichiers non visuels (Feedback, Helpers, I18n, Media, Voice, VoiceCmd) | aucun changement requis (vérifier qu'aucun n'émet d'UI) |
| S85 | Traductions | toutes les nouvelles chaînes (§2.9 toast, réglages, PDatePicker, PTimePicker, PDialog) dans `assets/i18n/en.json` |
| S86 | Benchmarks | modules `:benchmark` et `:baselineprofile` présents, profil versionné, résultats §8 joints |

---

## 12. Esquisse visuelle

Prototypes : `ui/src/main/kotlin/e81/Kit.kt` (fond racine `AppBg`, `popHills`, `Tide`, plaques, héros, `CompactBar`, `PSlider`, `PRadio`, `PCheckbox`, `PTextField`, `PSelect`, `PMenu`, `PDialog`, `PTabs`, `PSpinner`, `PIndeterminate`, `PStepper`, `flameCrown`, `AvatarMedallion`, `Bubble`, `ToastPop`) ; maquettes : `ui/src/main/kotlin/e81/Screens.kt` (avec les vrais `NavBar`, `Fab`, `Glass`, `StatGrid`, `CheckButton`, `PButton`, `PSwitch`, `SetGroup`…).

Planches clair | sombre (`ui/out/`) :

| Planche | Contenu |
|---|---|
| `P-e81-home.png` | Aujourd'hui, scène Loire partout (jour en clair, nuit en sombre), héros, plaques, marée, barre teintée, toast « +250 ml » et bouton + remonté |
| `P-e81-stats.png` | Statistiques (sous-page) : héros de page, PTabs, tuiles néon, carte de chaleur pleine, objectifs |
| `P-e81-sheet.png` | Feuille « Nouvel objectif » par-dessus l'accueil : voile allégé, scène visible au-dessus |
| `P-e81-goal.png` | Fiche d'objectif : bandeau pleine largeur, ajout rapide, rappel du geste +1, journal du jour |
| `P-e81-chat.png` | Messages : fond de conversation (Montgolfières), barre compacte, bulles, PMenu avec réactions, Composer |
| `P-e81-shop.png` | Boutique : héros + solde, quête, PTabs défilants, cartes de fonds (actif, utiliser, prix, verrouillé) |
| `P-e81-settings.png` | Apparence : fond par défaut « Collines braise », galerie, PRadioRow, PSlider à crans, interrupteurs |
| `P-e81-menu.png` | Apparence défilée : barre compacte, Ma photo avec voile (PSlider + bulle, plus de flou), PSelect + PMenu ouvert |
| `P-e81-dialog.png` | Compte : PTextField (focus, aide, erreur, zone de texte), PCheckRow, PDialog destructif |
| `P-e81-profile.png` | Profil : couronne de flammes en aplats autour du médaillon qui s'enfonce |
| `P-e81-kit.png` | Planche des nouveaux composants |
| `P-e81-scenes.png` | Choix des scènes (jour / nuit) pour le point focal |

Rendu : `sh ui/sync.sh && cd ui && gradle -q --offline --no-daemon run --args="e81-home,e81-stats,e81-sheet,e81-goal,e81-chat,e81-shop,e81-settings,e81-menu,e81-dialog,e81-profile,e81-kit"` puis `python3 ui/mont.py <mêmes noms>` (ou `sh ui/prev.sh <noms>`).

Écarts connus de l'esquisse : barre d'état simulée ; mouvements (parallaxe, rebond des feuilles, couronne qui vacille, « +250 ml » qui monte) montrés en images fixes ; la planche Messages place le PMenu sous la bulle choisie sans le voile transparent ; contenu fictif.
