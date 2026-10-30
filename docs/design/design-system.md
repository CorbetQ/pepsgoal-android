# PepsGoal 8 « Braise Pop » : cahier des charges et design system

> Version 2.0, 29 octobre 2026. Remplace la v1 « Braise », jugée trop sage, trop proche d'une app native Google (archivée dans `design/archive/design-system-braise-v1.md`, planches dans `ui/out/braise-v1/`).
> Sources : `design/veille.md`, code 7.1.0 (`app/src/main/java/fr/pepsgoal/app/ui/`), retours de l'utilisateur sur la 7.0 (« verre bonbon » : trop chargée et lourde), sur la 7.1 (« iOS 27 » : trop sage) et sur la v1 (« ennuyante »).
> Ébauche visuelle : `ui/src/main/kotlin/app/Ds.kt`, rendus `ui/out/P-ds-*.png` (planches clair | sombre).

---

## 0. En une phrase

**Une app qui a de l'énergie et qui reste légère.** Aplats de couleurs franches (les néons d'origine de PepsGoal et la flamme orange → rose), volumes « à marche » (chaque élément pressable repose sur une ombre pleine décalée et s'y enfonce à l'appui), Unbounded généreux pour les titres et les chiffres, emojis Fluent 3D mis en scène en grand, et mouvement à ressorts qui rebondit. Pas de flou sur le contenu, pas de halo, pas de reflet : tout se dessine en rectangles arrondis pleins, aussi rapides qu'un fond uni.

### Où se place « Braise Pop »

| | 7.0 « verre bonbon » | 7.1 « iOS 27 » / v1 « Braise » | **8.0 « Braise Pop »** |
|---|---|---|---|
| Couleur | dégradés partout, halos | neutres, une seule couleur rare | **aplats francs** : un néon par objectif, la flamme sur la carte Série, le bouton +, l'onglet actif |
| Volume | double ombre floue, reflets, sphères | ombre de 1 dp ou rien | **marche pleine** de 3 à 6 dp (une seule forme pleine, sans flou) + bord de 2 dp |
| Matière | verre flouté sur chaque carte | verre réservé à la navigation | **aucun flou** (barres opaques) |
| Typo | Unbounded ExtraBold partout | Unbounded sur 3 usages | Unbounded **ExtraBold pour les titres, sections et chiffres** ; Manrope ExtraBold et Bold pour le reste |
| Emojis | petits, dans des tuiles en pâte | 28 dp, discrets | **en grand** : 34 dp dans les lignes, 96 dp dans les fiches, en-têtes de section illustrés |
| Mouvement | rebonds, ondes, halos animés | sobre, sans rebond | **ressorts sur l'échelle et la position** (appui, pop, roulement), confettis seulement pour les paliers |
| Coût | élevé (flou × N cartes) | faible | **faible** (aplats + `graphicsLayer`) |

Références : Duolingo (boutons à marche, aplats, gros chiffres), Finch (douceur, emojis mis en scène), Headspace (formes rondes et généreuses, couleur par section), et l'identité PepsGoal d'origine (flamme, néons, Unbounded, Fluent 3D, fonds animés).

---

## 1. Principes

1. **Lisible en 2 secondes, vivant en 1.** La structure reste : barre du haut, carte Série, liste à valider groupée par moment. L'énergie vient de la couleur, du volume et du mouvement, pas de modules en plus.
2. **Aplats, jamais d'effets.** Couleur pleine, bord, marche pleine. Interdits : flou sur le contenu, halo, reflet spéculaire, double ombre, ombre floue colorée.
3. **Tout ce qui se touche a du volume.** Un élément avec une marche se presse ; un élément plat (texte, étiquette, barre) ne se presse pas. L'utilisateur sait sans réfléchir ce qui est interactif.
4. **Encre prune sur la couleur.** Sur tout aplat vif (néons, flamme, jaune), le texte et les icônes sont en prune #2A1420. Contraste mesuré ≥ 5,1:1 partout. Jamais de texte blanc sur une couleur vive.
5. **Une couleur par objectif, une flamme pour l'app.** Chaque objectif garde son néon (tuile, case faite, barre, fiche). La flamme orange → rose est l'emblème : carte Série, bouton +, onglet actif, bouton principal, niveau.
6. **Le rebond est un retour, pas un décor.** Chaque animation répond à un geste ou à un événement (appui, validation, série qui monte). Rien ne bouge en permanence, sauf la flamme qui danse doucement sur la carte Série.
7. **La fête au bon moment.** Confettis et écran plein : journée parfaite, palier de série, niveau, badge. Jamais à chaque coche.
8. **Bienveillance.** Jokers, repos, rattrapage visibles ; ton encourageant (« Tu y es presque ! », « On reprend demain »).
9. **Accessible et rapide.** AA partout, cibles de 48 dp, grands caractères, « Animations réduites » respecté, 0 image > 32 ms pendant une validation.

---

## 2. Couleurs

Contrastes calculés avec `design/tools/cr.py` (formule WCAG 2.x).

### 2.1 Neutres

| Jeton | Rôle | Clair | Sombre | Contraste vérifié |
|---|---|---|---|---|
| `bg` | Fond de l'écran (crème pêche / prune nuit) | **#FFF7F0** | **#170F1C** | — |
| `surface` | Cartes, lignes, barre d'onglets, boutons secondaires | **#FFFFFF** | **#241A2B** | — |
| `surface2` | Creux : rail du segmenté, rails des barres, pastilles de compteur, séparateurs épais | **#FFEDE1** | **#2F2337** | — |
| `edge` | Bord (2 dp) **et marche** des éléments neutres | **#F2DED1** | **#3A2D43** | décoratif |
| `control` | Contour d'une case « à faire », interrupteur éteint | **#9C8273** | **#8C7584** | 3,6:1 sur surface (clair), 4,0:1 (sombre) |
| `ink` | Texte principal | **#2A1420** (prune) | **#FFF4EC** | 16,3:1 sur bg ; 15,4:1 sur surface (sombre) |
| `ink2` | Texte secondaire, sous-lignes, icônes au repos | **#6B4F5C** | **#D9C4CF** | 7,3:1 sur blanc, 6,4:1 sur surface2 ; 10,1:1 (sombre) |
| `ink3` | Tertiaire (chevrons, légendes d'axes, placeholders) | **#7D6170** | **#A8929E** | 5,5:1 sur blanc, 4,8:1 sur surface2 ; 5,8:1 / 5,1:1 (sombre) |
| `scrim` | Voile sous une feuille | #2A1420 à 50 % | #000000 à 70 % | — |
| `deco` | Formes décoratives du fond (aplats immobiles) | #FFE2CC, #FFD6E8 | #3A1F2E, #2E1B3A | décoratif |

### 2.2 Marque, accent et états

| Jeton | Valeur | Usage |
|---|---|---|
| `flame` | **#FFB000 → #FF4A12 → #FF3D96** (dégradé linéaire diagonal, identique en clair et en sombre) | Carte Série, bouton +, onglet actif, bouton principal, barre de niveau, anneau du jour, cercle de l'avatar |
| `flameStep` | **#C2185B** | Marche des éléments en `flame` |
| `plum` (= `onColor`) | **#2A1420** | Texte et icônes sur tout aplat vif : 9,4 / 5,1 / 5,2:1 sur les trois arrêts de la flamme, ≥ 5,6:1 sur les 16 néons |
| `accentInk` | #B8350A (clair, 5,6:1 sur bg) / #FF8A55 (sombre, 7,2:1) | Liens et actions texte (« Tout voir », bouton discret) |
| `ok` | #1E7F45 / #4FD08A | Balayage Valider, messages de réussite en texte |
| `danger` | #C0392B (5,1:1 sur bg) / #FF7A84 (6,7:1) | Texte danger ; tuile danger = néon `duel` + icône prune |
| `ice` | #22D3FF (aplat, avec emoji 🧊 ou icône prune) | Jokers, jours gelés, repos |
| `gold` | #FFD23F | Bouton d'action d'un toast, avatar par défaut, récompenses |
| `pink` | #FF3D96 | Pastilles de compteur (texte prune 5,2:1) |
| `toastBg` | #2A1420 (prune) + marche #120810 | Toasts, identiques en clair et en sombre ; texte #FFF4EC (15,9:1) |

### 2.3 Couleurs des types d'objectifs : les néons d'origine, en aplats

`neon` = aplat (tuile, case faite, barre, bandeau de fiche, puce choisie). `ink` = texte coloré du type sur `surface` (sous-ligne d'une ligne faite, statistiques). `step` = marche de l'aplat = néon mélangé à 32 % avec #5A1030.

| Type | neon | ink clair | ink sombre | prune sur neon | ink sur ligne faite (clair / sombre) |
|---|---|---|---|---|---|
| routine | #B6FF2E | #3A7300 | #C6FF5C | 14,2 | 5,5 / 7,7 |
| objective | #22D3FF | #00627E | #5FE0FF | 9,7 | 6,0 / 6,7 |
| deadline | #FF4FA3 | #B0005A | #FF7DBB | 5,7 | 5,6 / 5,1 |
| abstinence | #A98BFF | #5530CF | #BFA8FF | 6,4 | 6,5 / 5,6 |
| monthly | #FFD23F | #7A4E00 | #FFE07A | 11,9 | 6,7 / 7,4 |
| limit | #FF8A3D | #A33D00 | #FFB07A | 7,3 | 5,5 / 6,2 |
| duration | #2EF2B0 | #00704F | #5FF5C4 | 11,9 | 5,5 / 7,1 |
| project | #4D9BFF | #0B4FC2 | #8CBDFF | 6,1 | 6,0 / 6,1 |
| measure | #FF7AD9 | #A3007A | #FF9FE4 | 7,4 | 6,2 / 6,0 |
| group | #7CFF6B | #1F7A10 | #9DFF90 | 13,4 | 5,1 / 7,6 |
| duel | #FF5A5A | #B3121F | #FF8A8A | 5,6 | 5,5 / 5,3 |
| challenge | #FFB000 | #8A5300 | #FFC94D | 9,4 | 5,6 / 6,8 |
| program | #7C8CFF | #2F3FBF | #A9B3FF | 5,8 | 6,7 / 5,9 |
| timed | #E3B98A | #7A4B12 | #F0CFA6 | 9,5 | 6,6 / 6,9 |
| period | #7DD3FC | #035C8A | #9DDCFF | 10,3 | 6,5 / 6,7 |
| cap | #00D1B2 | #006B5B | #4FE8CF | 8,8 | 5,5 / 7,2 |

Règles :
- **Un aplat néon ne porte jamais seul une information.** Il est toujours accompagné d'un texte ou d'une icône prune (coche, emoji, valeur). Les néons clairs (lime, jaune, cyan) ont moins de 3:1 sur le blanc : leur forme est donnée par la marche foncée et le bord, et l'information par le texte.
- Ligne faite : fond = `surface` mélangé à 20 % (clair) / 22 % (sombre) avec le néon, bord = `edge` mélangé à 55 % avec le néon.
- Daltonisme : les palettes CVD actuelles remplacent les néons ; texte toujours prune ; la forme (coche, cadenas, anneau) reste le premier signal.

### 2.4 Variantes de thème

- **Contraste élevé** : bord et marche en `ink` ; `ink2` = `ink` ; néons gardés (texte prune) ; formes décoratives supprimées.
- **OLED** : `bg` #000000, `surface` #170F1C, `edge` #2F2337.
- **Material You** : remplace `flame` dans l'onglet actif, le bouton + et le bouton principal par `primary → tertiary` de la palette dynamique (texte recalculé par `onColors`) ; la flamme de la carte Série et les néons restent.
- **Accents de la boutique** (`ACCENTS`) : remplacent `flame` (avec `flameStep` = premier arrêt mélangé à 32 % avec #5A1030).
- **Ambiances de la boutique** : teintent `bg` et `deco`, et pilotent le fond animé ; jamais les surfaces ni les néons.

---

## 3. Typographie

Polices : **Unbounded** (Bold, ExtraBold) et **Manrope** (Medium, SemiBold, Bold, ExtraBold). Chiffres tabulaires partout. Tailles en sp.

| Style | Police | Taille | Graisse | Interligne | Usage |
|---|---|---|---|---|---|
| `hero` | Unbounded | 60 | ExtraBold | 1,1 | Chiffre de série sur la carte Série |
| `celebration` | Unbounded | 64-72 | ExtraBold | 1,05 | Écrans de palier et de niveau |
| `display` | Unbounded | 48 | ExtraBold | 1,1 | Valeur principale d'une fiche (« 1,2 ») |
| `screenTitle` | Unbounded | 30 | ExtraBold | 1,12 | Titre d'écran |
| `sheetTitle` | Unbounded | 28 | ExtraBold | 1,12 | Titre de fiche (2 lignes max) |
| `stat` | Unbounded | 22 | ExtraBold | 1,12 | Tuiles de statistiques, nom du profil (24) |
| `section` | Unbounded | 18 | Bold | 1,12 | En-têtes de section (précédés d'un emoji 26 dp) |
| `cardTitle` | Unbounded | 16 | Bold | 1,15 | Titre de carte (emoji 22 dp facultatif) |
| `rowTitle` | Manrope | 16 | ExtraBold | 1,3 | Titre de ligne, élément de menu |
| `button` | Manrope | 17 (15 petit) | ExtraBold | 1,2 | Libellés de boutons |
| `body` | Manrope | 15 | Bold | 1,35 | Messages, texte de carte |
| `bodySmall` | Manrope | 14 | Bold / ExtraBold | 1,3 | Puces, valeurs à droite, date de la barre du haut |
| `caption` | Manrope | 13 | SemiBold / ExtraBold | 1,3 | Sous-lignes, libellés de tuiles, pilules |
| `label` | Manrope | 12-13 | ExtraBold | 1,2 | Étiquettes de type, onglet actif, jours de la semaine |

Règles :
- Unbounded pour : titres d'écran, de fiche, de section et de carte ; chiffres (série, stats, valeurs, anneaux). **Pas** pour les titres de ligne, les boutons, les puces ni les paragraphes.
- Manrope jamais en Regular dans l'interface (Medium au minimum) : l'app parle fort.
- Les gros chiffres sont toujours accompagnés d'une unité ou d'un libellé (« 12 jours de série »).
- Grands caractères jusqu'à 200 % : `hero` plafonné à 72 sp effectifs, titres sur 2 lignes, aucune hauteur fixe sur du texte.

---

## 4. Grille, espacements, rayons

**Grille de 4 dp.** Marges latérales 16 dp (20 à partir de 600 dp, colonne max 640 dp).

| Mesure | Valeur |
|---|---|
| Écart entre deux lignes d'objectif | 10 dp |
| Écart entre cartes | 12 dp |
| En-tête de section | 22 dp au-dessus, 10 dp en dessous |
| Marge intérieure de carte | 16 dp (18 pour la carte Série) |
| Ligne d'objectif | hauteur min 76 dp (marche comprise : 80) ; marges 12 / 6 / 10 / 10 |
| Ligne de menu ou de réglage | 60 dp min ; séparateur de 2 dp `surface2`, retrait 66 dp |
| Marge basse des listes | barre d'onglets (68 + 18) + bouton + (64) + 16 = 166 dp |

**Rayons : généreux et ronds.**

| Rayon | Éléments |
|---|---|
| **16-17 dp** (32 % de la taille) | Tuiles d'emoji (52 dp → 17), tuiles d'icône de menu (38 dp → 12) |
| **22 dp** | Lignes d'objectif, tuiles de statistiques, boutons de type |
| **24 dp** | Cartes, groupes de menus |
| **28 dp** | Carte Série |
| **32 dp** | Feuilles du bas (coins supérieurs) |
| **Capsule** | Boutons, puces, segmenté, interrupteurs, barre d'onglets (30 dp), pilules, barres de progression, bouton + (rond) |

---

## 5. Volume « à marche » (remplace élévation, ombres et verre)

Une seule technique pour tout le relief : **forme pleine + bord + marche**, dessinée par `Chunk` (voir `Ds.kt`).

```
┌──────────────────────┐  ← bord 2 dp (edge)
│   aplat (fill)       │
│                      │
└──────────────────────┘
▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀  ← marche pleine (même couleur que le bord, ou step du néon), 3-6 dp
```

| Élément | Remplissage | Bord | Marche |
|---|---|---|---|
| Carte, ligne, groupe de menus | `surface` | 2 dp `edge` | 4 dp `edge` |
| Ligne faite | `surface` + 20 % néon | 2 dp `edge` + 55 % néon | 4 dp idem |
| Bouton secondaire, bouton rond, segment choisi | `surface` | 2 dp `edge` (0 pour le segment) | 3-4 dp `edge` |
| Bouton principal, bouton +, onglet actif | `flame` | aucun | 4-5 dp `flameStep` (onglet actif : pas de marche) |
| Tuile d'emoji, case faite, puce choisie, bouton de type, tuile de stat | néon | aucun | 3-4 dp `step` du néon |
| Carte Série | `flame` | aucun | 6 dp `flameStep` |
| Toast | `plum` | aucun | 4 dp #120810 |

- **Appui** : l'élément descend de la hauteur de sa marche (`translationY = step`), la marche disparaît (ressort `press`, §9). Relâché : il remonte avec un léger dépassement.
- Éléments **plats** (non pressables) : étiquettes, pilules d'info, barres, texte, rails.
- **Aucun flou.** La barre d'onglets et la barre du haut sont opaques. Le contenu défile sous la barre d'onglets flottante, qui repose sur sa propre marche.
- Aucune ombre portée Compose (`shadow`), aucun `RenderEffect`, aucun halo : la marche suffit.

---

## 6. Iconographie, emojis et décor

- **Emojis Fluent 3D, en grand et mis en scène** :
  - 34 dp dans les tuiles de 52 dp des lignes ;
  - 26 dp devant chaque titre de section (☀️ Ce matin, 🌈 Dans la journée, 🌙 Ce soir, ✅ Fait) ;
  - 96 dp, inclinés de 8°, dans le bandeau d'une fiche ;
  - 56-80 dp dans les états vides et célébrations ;
  - 16-26 dp dans les pilules, tuiles de stat et toasts.
- **Icônes d'interface au trait** (`Pi`, trait 2) : 24 dp (onglets au repos), 22 dp (onglet actif, lignes de réglages), 20 dp (boutons ronds, tuiles de menu), 18 dp (dans une case). Couleur `ink` sur `surface`, **prune sur un aplat**.
- **Tuiles d'icône de menu** : 38 dp, rayon 12, fond néon (une couleur par entrée), icône prune 20 dp. Une entrée garde toujours la même couleur (Boutique = measure, Badges = monthly, Statistiques = objective, Réglages = program, Thème = deadline, Fond animé = period, Animations = monthly, Rappel du matin = challenge, Bilan du soir = abstinence, Confidentialité = cap, Déconnexion = duel).
- **La flamme** : 64 dp sur un médaillon blanc de 88 dp incliné de −6° (carte Série) ; 20 dp dans les jours réussis ; 26 dp au niveau du profil ; flamme éteinte en pointillés pour « aujourd'hui, à protéger ».
- **Décor du fond** : 2 grands cercles en aplat `deco` derrière l'en-tête, immobiles, sans flou. Dans les cartes vives (Série, bandeau de fiche) : 2 cercles blancs à 12-22 % d'opacité.
- **Fond animé** (option d'identité, voir §11.2) : dessiné à la place du décor, limité aux 360 dp du haut, fondu vers `bg`.

---

## 7. Composants

Dimensions en dp. Cible tactile ≥ 48 × 48 partout.

### 7.1 Carte (`Card`)
`Chunk` neutre (§5), rayon 24, marge 16, écart interne 12. Titre facultatif : emoji 22 + `cardTitle` Unbounded 16 Bold. Carte pressable : s'enfonce à l'appui.

### 7.2 Ligne d'objectif (`GoalRow`) — une seule ligne pour les 16 types
```
| 12 | tuile 52 (néon, r17, marche 3, emoji 34) | 12 | titre 16 ExtraBold              | case 44 (cible 48) | 6 |
|    |                                          |    | sous-ligne 13 SemiBold ink2      |                    |   |
|    |                                          |    | [barre 8 dp néon] (types chiffrés)|                    |   |
```
- `Chunk` neutre, rayon 22, hauteur min 76 + marche 4. Lignes séparées de 10 dp (pas de groupe).
- Sous-ligne propre au type (une ligne) : « Série de 8 jours », « 120 / 300 pages · J-12 », « 1,2 / 2 L », « 12 min / 30 », « 18 jours sans », « 2 / 3 verres », « J-12 », « S'ouvre à 21 h », « 34 / 50 km ensemble » (+ pile d'avatars 22), « Toi 12 – 9 Léa », « 3 / 8 étapes », « Semaine 2/6 », « Jour 12/30 », « Dernière : 72,4 kg ».
- **Barre de progression** de 8 dp en néon sous la sous-ligne pour les types chiffrés (cap, objective, monthly, limit, program, group, duel, project, challenge).
- **Fait** : fond teinté du néon (§2.3), sous-ligne en `ink` du type (« Fait à 7:42 · série de 9 🔥 »), case pleine. La ligne reste 1,5 s puis glisse vers la section « Fait » repliée.
- Urgent : sous-ligne en `danger` + icône drapeau.
- Gestes conservés : balayage droite (Valider, fond `ok`), gauche (actions rapides), appui (fiche), appui long (Focus pour une durée).

### 7.3 Case à valider (`CheckButton`) — 44 dp visibles, 48 tactiles
| État | Rendu |
|---|---|
| À faire | `Chunk` `surface`, bord 2,5 dp `control`, marche 3 dp `control` |
| Fait | `Chunk` néon du type, marche `step`, coche prune 26 dp, trait 3,4 dp |
| Progrès | `Chunk` `surface` (marche `edge`) + anneau 5 dp : rail `edge`, arc néon ; icône `plus` 18 dp `ink`. Tap = +1, appui long = saisie |
| Minuteur | Idem avec `play` / `pause` |
| Fermé | Cercle pointillé 2,5 dp `control`, cadenas 18 dp `ink3`, non pressable |

### 7.4 Boutons (`PButton`) — capsules à marche
| Variante | Remplissage | Bord | Marche | Libellé |
|---|---|---|---|---|
| Principal | `flame` | — | 4 dp `flameStep` | 17 ExtraBold prune, icône 20 |
| De type (« + 250 ml ») | néon du type | — | 4 dp `step` | 17 ExtraBold prune |
| Secondaire | `surface` | 2 dp `edge` | 4 dp `edge` | 17 ExtraBold `ink` |
| Danger | `surface` | 2 dp `edge` | 4 dp `edge` | `danger` (icône seule possible) |
| Discret | aucun | — | — | 17 ExtraBold `accentInk` |

Hauteur 54 + 4 (petit : 44 + 4), marges 22 (16). Un seul bouton principal par écran ou feuille. Désactivé : opacité 40 %, sans marche, non pressable.

### 7.5 Puces, étiquettes, pastilles
- **Puce de filtre** : capsule 42 (cible 48), marges 14, emoji 18 facultatif, 14 ExtraBold. Repos : `Chunk` neutre (bord 2, marche 3). **Choisie : aplat néon** (jaune `monthly` par défaut ou couleur du dossier), texte prune, marche `step`.
- **Étiquette de type** : capsule 26, aplat néon, 12 ExtraBold prune. Plate (non pressable).
- **Pilule d'info** (dans les cartes vives) : capsule 32, blanc à 42 %, emoji 16 + 13 ExtraBold prune.
- **Pastille de compteur** : 20 dp, aplat `pink`, chiffre 11 ExtraBold prune ; point de 10 dp sur un onglet.

### 7.6 Segmenté (`Seg`)
Rail capsule 50, `surface2`, marge 4. Segment choisi : `Chunk` `surface` (marche 3 `edge`), 15 ExtraBold `ink` ; autres : 15 Bold `ink2`. L'indicateur glisse avec le ressort `move` et se dépasse légèrement.

### 7.7 Interrupteur (`PSwitch`)
60 × 36, capsule. Allumé : aplat `routine` (#B6FF2E), bord 2 dp `step`, pastille blanche 28 dp à marche 2 dp, coche prune 14. Éteint : `surface2`, bord 2 dp `control`, pastille blanche à marche `control`. La pastille saute d'un côté à l'autre avec le ressort `pop` (dépassement visible).

### 7.8 Barre de progression (`Bar`)
10 dp (8 dans une ligne, 12 dans une fiche, 14 pour le niveau), capsule. Rail `surface2` (clair) / #120B16 (sombre). Remplissage néon du type (ou `flame` pour le niveau). Toujours accompagnée de la valeur en texte. Remplissage animé avec le ressort `move`.

### 7.9 Anneau (`Ring`)
Trait épais : 9 dp pour 60-64 dp. Rail `surface2`, arc en dégradé balayé `flame`, bouts ronds, chiffre central Unbounded ExtraBold 14.

### 7.10 Barre d'onglets (`NavBar`) — capsule flottante opaque
- `Chunk` neutre, rayon 30, hauteur 64 + marche 4, marges 14 dp sur les côtés, 18 dp au-dessus du bord (plus l'inset de navigation). **Opaque, aucun flou.**
- 4 onglets : Aujourd'hui (`sun`), Objectifs (`target`), Social (`users`, Feed + Amis), Profil (`user`).
- **Onglet actif** : pilule `flame` qui prend 1,7 fois la largeur d'un onglet, avec icône 22 et libellé 13 ExtraBold en prune. Onglets au repos : icône seule de 24 dp en `ink2`, libellé lu par TalkBack. En Lecture facile, tous les libellés sont visibles.
- Changement d'onglet : la pilule glisse et s'étire (ressort `move`), l'icône active fait un petit saut de 4 dp (`pop`).

### 7.11 Barre du haut (`TopBar`)
- Sur `bg` (et le décor), opaque, sans flou ni filet.
- Gauche : date 14 ExtraBold `ink2` (Aujourd'hui) puis `screenTitle` Unbounded 30 ExtraBold.
- Droite (Aujourd'hui) : bouton rond Notifications (44 à marche, pastille) puis **avatar** 40 dp (anneau `flame` 3 dp, fond `gold`, initiales prune Unbounded) avec badge de niveau prune / or. Profil : Partager + Réglages. Réglages : Rechercher. Pages poussées : bouton rond Retour à gauche.
- Au défilement, le titre se réduit à 20 sp et la barre prend un fond `bg` plein (pas de verre).

### 7.12 Bouton + (`Fab`)
Rond de 64 dp, `flame`, marche 5 dp `flameStep`, icône `plus` 30 dp prune. À 18 dp du bord droit, 104 dp du bas (au-dessus de la barre d'onglets). Se cache au défilement vers le bas (glisse de 96 dp + échelle 0,6, ressort `move`) et revient avec un rebond (`pop`).

### 7.13 Carte Série (`HomeHeader`) — l'emblème
- `Chunk` `flame`, rayon 28, marche 6 `flameStep`, marge 18 ; 2 cercles blancs à 16 % / 12 % en décor.
- Ligne 1 : « 12 » Unbounded 60 ExtraBold prune + « jours / de série » 16 ExtraBold prune ; à droite, médaillon blanc de 88 dp (marche `flameStep` à 55 %) incliné de −6°, avec la **flamme de 64 dp qui danse**.
- Message 15 Bold prune (2 lignes max), encourageant et selon l'état : « Ta flamme brûle ! Plus que 2 pour une journée parfaite. »
- Semaine : 7 pastilles de 34 dp, avec la lettre du jour (12 ExtraBold prune) au-dessus :
  - jour réussi : disque prune + flamme 20 dp ;
  - joker : disque blanc + 🧊 ;
  - aujourd'hui : pointillés prune + flamme éteinte ;
  - jour à venir : point prune à 30 %.
- Pilules : 🧊 2 jokers · 🏆 Record 48 · ✅ 3/5 faits.
- États (jamais la couleur seule) : allumée (« Ta flamme brûle ! »), à protéger (« Valide avant minuit »), gelée (dégradé `ice` → #7C8CFF + « Joker utilisé »), repos (« Jour de repos 😴 »), éteinte (`surface` + « On reprend demain »).
- Un toucher ouvre la feuille de série (calendrier, jokers, journée difficile, repos).

### 7.14 En-tête de section
Emoji Fluent 26 + `section` Unbounded 18 Bold + compteur (capsule `surface2`, 13 ExtraBold `ink2`) + action texte `accentInk` 14 ExtraBold à droite. Rôle « heading ». Section « Fait ✅ » repliée par défaut : ligne `Chunk` avec pile d'emojis et « 2 faits aujourd'hui ».

### 7.15 Feuilles du bas (`SheetHost`)
Coins 32, fond `bg` opaque, `scrim`. Pour une fiche d'objectif : **bandeau** de 176 dp en aplat du néon du type, avec :
- 2 cercles blancs translucides ;
- poignée prune à 35 % ;
- pilule (type · moment) ;
- titre Unbounded 28 ExtraBold prune (2 lignes) ;
- emoji 96 dp incliné de 8° à droite.

Autres feuilles : poignée 40 × 5 `ink3`, titre Unbounded 22, bouton fermer rond. Ouverture : montée en ressort `sheet` ; fermeture : fondu et descente de 200 ms.

### 7.16 Toasts
`Chunk` prune, rayon 20, marche 4, hauteur 56, marges 12 / 8. Emoji 26 (🎉, 🔥, ↩️), texte 15 ExtraBold #FFF4EC, action en pilule `gold` 36 dp, texte prune (cible 48). Placé 16 dp au-dessus de la barre d'onglets ; un seul à la fois. Entrée : montée de 24 dp + échelle 0,9 → 1 en ressort `pop` ; sortie : fondu de 150 ms. 4 s (6 s si action ou Lecture facile).

### 7.17 États vides
Carte : emoji 80 dp (incliné, petit flottement de 2 s en boucle, arrêté hors écran), titre Unbounded 20, texte 15 Bold `ink2`, bouton principal. Ton joyeux : « Rien de prévu aujourd'hui 🌴 Profite, ou ajoute une routine ! ».

### 7.18 Champs de saisie
Hauteur 56, rayon 18, `surface` avec bord 2 dp `edge` et marche 3 dp (creusé à l'appui), libellé 13 ExtraBold `ink2` au-dessus. Focus : bord et marche en `flame[1]` #FF4A12. Erreur : bord `danger` + message 13 Bold avec icône.

### 7.19 Tuiles de statistiques
`Chunk` néon (une couleur par stat : Série = limit, Record = monthly, Taux = duration, Validés = routine), rayon 22, marche 4 : emoji 24-26, valeur Unbounded 22 ExtraBold prune, libellé 13 ExtraBold prune.

---

## 8. Structure des écrans clés

### 8.1 Aujourd'hui — `ui/out/P-ds-today.png` (et `P-ds-today-fx.png` avec le fond animé)
1. Décor (2 cercles en aplat) ou fond animé confiné.
2. Barre du haut : date, « Aujourd'hui », notifications, avatar avec niveau.
3. **Carte Série** (§7.13).
4. Sections par moment : ☀️ Ce matin, 🌤️ Cet après-midi, 🌙 Ce soir, 🌈 Dans la journée (sections vides masquées), puis ✅ Fait (repliée). Lignes à marche (§7.2).
5. Modules secondaires (à la une, météo, objectif du mois, défi du jour…) sous la liste, en cartes.
6. Barre d'onglets flottante, bouton +.

### 8.2 Objectifs
Titre « Objectifs » ; tuiles de stat néon (en cours, aujourd'hui, favoris) ; recherche (champ §7.18) ; puces de filtre (dossiers en couleur) ; lignes §7.2 avec étiquette de type ; bouton +.

### 8.3 Profil — `ui/out/P-ds-profile.png`
Carte identité : avatar 70 dp avec badge de niveau, nom en Unbounded 24, pseudo, ligne de niveau (flamme, « Brasier II », points) et barre de niveau de 14 dp en `flame`. Ensuite : 3 tuiles de stat néon ; section 🎒 Mon espace (Boutique, Badges, Statistiques, Ligue, Amis, Réglages) en groupe à marche avec tuiles d'icône néon.

### 8.4 Réglages — `ui/out/P-ds-settings.png`
Sections illustrées (🎨 Apparence, ⏰ Rappels, 🔐 Compte…), chacune un groupe à marche ; lignes 60 dp : tuile d'icône néon 38, titre 16 ExtraBold, sous-ligne 13 SemiBold, interrupteur §7.7 ou valeur + chevron. Nouveaux réglages : **Fond animé** (Non / Haut de l'accueil) et **Animations** (Complètes / Réduites).

### 8.5 Fiche d'objectif — `ui/out/P-ds-goal.png`
Feuille haute, de haut en bas :
1. Bandeau néon (§7.15).
2. Carte d'action : valeur 48 Unbounded + unité, message encourageant, anneau, barre de 12 dp, boutons « + 250 ml » (de type) et « Saisir » (secondaire).
3. 3 tuiles de stat néon.
4. Carte 📅 30 derniers jours (barres néon de largeur égale, rayon 4, jours manqués en `surface2`).
5. Carte des réglages de l'objectif.
6. Actions Pause, Archiver, Supprimer.

### 8.6 Planches de référence
- `P-ds-kit.png` : boutons (dont l'état appuyé), puces, étiquettes, segmenté, cases, interrupteurs, anneau, barres, tuiles.
- `P-ds-motion.png` : images clés des animations.

---

## 9. Mouvement : rebondissant, mais bon marché

Toutes les animations agissent sur **l'échelle, la position, la rotation ou l'opacité** via `graphicsLayer`. Elles ne déclenchent ni recomposition ni nouvelle mise en page à chaque image. Aucune n'anime un flou ou une ombre.

| Jeton | Ressort Compose | Ressenti | Usage |
|---|---|---|---|
| `press` | `spring(dampingRatio = .6f, stiffness = 1500f)` | net | Enfoncement sur la marche (translationY = marche) + échelle 0,97 ; relâché : remonte avec dépassement de 2 dp |
| `pop` | `spring(.45f, 500f)` | rebond franc | Case validée (0,6 → 1,25 → 1), coche dessinée en 200 ms, pastille de compteur, onglet actif, interrupteur, bouton + qui revient, toast |
| `move` | `spring(.75f, 380f)` | souple, léger dépassement | Indicateur d'onglet et de segmenté, barres et anneaux qui se remplissent, ligne qui glisse vers « Fait », bouton + qui se cache |
| `sheet` | `spring(.82f, 300f)` | ample | Feuilles du bas, écrans poussés |
| `enter` | `spring(.7f, 420f)` + décalage 40 ms par élément, 6 éléments max | entrée en cascade | Apparition des lignes à l'ouverture de l'app et au changement de jour (pas à chaque onglet) |
| `fade` | `tween(150)` | — | Sorties, changement d'onglet (fondu + glissement de 12 dp) |

Moments signature :
1. **Validation** : la case « pop » (0 → 90 ms : 0,6 ; 180 ms : 1,25 ; 300 ms : 1), la coche se dessine, la tuile fait un petit saut de 6 dp (`pop`), la ligne prend sa teinte en 200 ms. Haptique `CONFIRM`. Compteur de la carte Série : les chiffres **roulent** verticalement (`move`). Pas de confettis.
2. **Flamme qui danse** : sur la carte Série seulement, rotation ±4° et étirement vertical ± 6 % autour de la base, cycle de 1,4 s, 15 i/s, arrêtée hors écran et après 20 s d'inactivité.
3. **Série +1** (première validation du jour) : le médaillon fait un saut (`pop`, échelle 1,15) et le chiffre roule.
4. **Journée parfaite** (une fois par jour) : 24 confettis en aplats (rectangles arrondis 4 × 7 dp, couleurs néon et flamme), trajectoire balistique de 1,2 s calculée dans un seul `Canvas`. Un toast 🎉 accompagne l'effet.
5. **Palier de série, niveau, badge** : écran plein, emoji ou flamme en 160 dp qui arrive en `pop`, chiffre `celebration`, 40 confettis maximum, fond animé plein écran autorisé, bouton principal « Continuer ». 2,5 s maximum avant que l'écran ne réponde.
6. **Erreur / refus** : secousse horizontale de ±6 dp, 3 oscillations en 300 ms, haptique `REJECT`.

« Animations réduites » (réglage interne ou `ANIMATOR_DURATION_SCALE = 0`) : toutes les animations deviennent des fondus de 100 ms ou des changements immédiats ; la flamme ne danse plus, pas de confettis (le toast reste).

---

## 10. Accessibilité

- **Contraste** : tout texte ≥ 4,5:1 (valeurs §2) ; texte sur aplat vif = prune (≥ 5,1:1) ; composants porteurs d'information ≥ 3:1 (case à faire, interrupteur éteint, pointillés). Les aplats néon ne portent jamais seuls une information (§2.3).
- **Cibles** : 48 × 48 dp minimum (`minimumInteractiveComponentSize()`), 8 dp entre deux cibles.
- **Grands caractères** (130 %, 160 %, 200 %) : les lignes grandissent ; le chiffre de série passe au-dessus du médaillon à partir de 160 % ; les onglets au repos restent en icônes avec libellé TalkBack ; Lecture facile affiche tous les libellés.
- **Jamais la couleur seule** : coche, cadenas, pointillés, flamme, 🧊, drapeau ; onglet actif = pilule + libellé.
- **TalkBack** : chaque ligne est un nœud « titre, sous-ligne, état » avec les actions Valider et Actions rapides ; rôles Tab, Switch, RadioButton, Heading ; toasts en région live ; la carte Série est lue « Série de 12 jours, 2 jokers, record 48, 3 objectifs sur 5 faits ».
- **Mouvement** : réglage « Animations : complètes / réduites » + respect du réglage système ; aucun clignotement au-delà de 3 Hz.
- **Haptique** : `CONFIRM`, `REJECT`, `CLOCK_TICK` pour le compteur qui roule (désactivable).

---

## 11. Performance

### 11.1 Budget
- **Aucun flou** dans l'app hors fond animé facultatif (et ce dernier ne floute rien). Aucune ombre Compose (`shadow`) : la marche est un rectangle arrondi plein.
- **Dessin** : chaque élément à marche = 2 rectangles arrondis (bord et marche, puis remplissage). Les décors (cercles) sont dessinés avec `drawBehind`, sans couche hors écran.
- **Animations** : uniquement via `graphicsLayer` (échelle, translation, rotation, alpha) ou `drawBehind` lisant un état animé (anneaux, barres). Interdit : animer `padding`, `size` ou `height` à chaque image.
- **Animations infinies** : flamme de la carte Série (15 i/s), fond animé (12 i/s), flottement de l'état vide. Toutes arrêtées hors écran, quand l'app est en arrière-plan, en économie d'énergie et en « Animations réduites ».
- **Confettis** : un seul `Canvas`, 40 particules maximum, 1,2 s, puis le nœud est retiré.
- **Objectif mesurable** (appareil modeste type Galaxy A15 / A35, build release, Baseline Profile Aujourd'hui → valider → fiche) : P90 ≤ 16 ms, P99 ≤ 24 ms, **0 image > 32 ms pendant une validation** (pop + roulement + glissade), démarrage à froid ≤ 1,2 s.

### 11.2 Fonds animés
- Option « Haut de l'accueil » : le fond (blobs, scènes, ambiance boutique) remplace les cercles de décor dans les **360 dp du haut** d'Aujourd'hui, à 70 % (clair) / 55 % (sombre) d'opacité, avec un fondu vers `bg` à partir de la moitié (masque vertical). Les cartes, opaques, passent par-dessus.
- Figé pendant le défilement, en économie d'énergie et en « Animations réduites » ; 12 i/s maximum.
- Plein écran uniquement : aperçu en boutique et écrans de célébration.

---

## 12. Check-list d'audit (à cocher par l'auditeur)

Chaque point se vérifie sur une capture, dans le code ou avec un outil (Accessibility Scanner, Layout Inspector, Macrobenchmark).

**Direction et identité**
1. Sur Aujourd'hui, on voit sans défiler : la carte Série en dégradé de la flamme, au moins 3 tuiles néon d'objectifs, la barre d'onglets avec l'onglet actif en dégradé, le bouton + en dégradé.
2. Le chiffre de série est en Unbounded ExtraBold de 60 sp, visible sans geste.
3. Les emojis Fluent 3D sont présents : 34 dp dans les tuiles, 26 dp devant chaque titre de section, 96 dp dans le bandeau de fiche.
4. Le texte n'est jamais culpabilisant ; jokers et record sont visibles sur la carte Série.

**Couleurs**
5. `bg` = #FFF7F0 / #170F1C ; `surface` = #FFFFFF / #241A2B ; `edge` = #F2DED1 / #3A2D43.
6. Les 16 types utilisent les néons du tableau §2.3 en aplat, avec l'encre du type indiquée.
7. Tout texte ou icône posé sur un aplat vif (néon, flamme, or, rose) est en prune #2A1420. Aucun texte blanc sur une couleur vive.
8. Accessibility Scanner : 0 alerte de contraste sur Aujourd'hui, Objectifs, Profil, Réglages et la fiche, en clair et en sombre.
9. Aucun aplat néon ne porte seul une information (chaque aplat est accompagné d'un texte, d'une coche, d'un emoji ou d'une icône).
10. La case « à faire » et l'interrupteur éteint ont un contour ≥ 3:1 avec la carte.

**Matière**
11. Aucun flou (`hazeEffect`, `blur`, `RenderEffect`) sur le contenu, la barre d'onglets ou la barre du haut (recherche dans le code).
12. Aucun halo, reflet, lueur ni ombre Compose (`shadow`, `glow`, `aiGlow`, `sheen`, `washOf`) dans les écrans refaits.
13. Tout élément pressable a une marche (3-6 dp) ; aucun élément non pressable n'en a (étiquettes, pilules, barres).
14. À l'appui, l'élément descend de la hauteur de sa marche (vérifié en enregistrement d'écran ou avec un test d'interface).
15. Les rayons utilisés sont ceux du §4 (17, 22, 24, 28, 32, capsule).

**Typographie**
16. Unbounded sert pour : titres d'écran (30), de fiche (28), de section (18), de carte (16), chiffres. Il n'apparaît pas sur les titres de ligne, boutons, puces ni paragraphes.
17. Manrope n'est jamais utilisé en Regular dans l'interface.
18. Les tailles appartiennent à l'échelle §3 ; chiffres tabulaires.
19. À 200 % de taille de police, rien n'est coupé ni masqué ; aucune hauteur fixe sur un texte.

**Composants et mise en page**
20. Lignes d'objectif : hauteur min 76 + marche 4, tuile 52 néon (marche 3), case 44 / cible 48, une seule commande à droite, barre néon de 8 dp pour les types chiffrés.
21. Ligne faite : fond teinté du néon, case pleine avec coche prune, puis glissade vers la section « Fait » repliée.
22. Sections avec emoji + titre Unbounded 18 + compteur.
23. Barre d'onglets : capsule flottante opaque à marche, 4 onglets, onglet actif = pilule `flame` avec libellé prune.
24. Bouton + : 64 dp, `flame`, marche 5, icône prune ; replié au défilement vers le bas.
25. Boutons : capsule de 54 (44 petit) + marche 4 ; un seul bouton principal par écran ou feuille.
26. Puce choisie = aplat néon + texte prune ; segmenté = segment à marche sur rail `surface2`.
27. Interrupteur 60 × 36, allumé en `routine` avec coche dans la pastille.
28. Toast prune à marche, action en pilule or, un seul à la fois, au-dessus de la barre d'onglets.
29. Fiche : bandeau néon de 176 dp avec emoji de 96 dp et titre Unbounded 28 prune.
30. Icônes de menu et de réglages au trait, dans des tuiles néon de 38 dp ; aucun emoji dans la barre d'onglets.
31. Marges latérales de 16 dp ; 10 dp entre lignes ; 12 dp entre cartes.

**Mouvement**
32. La validation produit le « pop » (dépassement à environ 1,25), la coche dessinée, le saut de la tuile, le roulement du compteur et l'haptique `CONFIRM` ; aucun confetti.
33. Les confettis n'apparaissent que pour : journée parfaite (une fois par jour), palier de série, niveau, badge.
34. La flamme ne danse que sur la carte Série, et s'arrête hors écran.
35. Toutes les animations passent par `graphicsLayer` ou par un dessin (aucune animation de `size`, `padding` ni `height` par image, vérifié dans le code).
36. Avec « Animations réduites » : aucun rebond, aucune animation infinie, aucun confetti.

**Accessibilité**
37. Toutes les cibles ≥ 48 × 48 dp (Accessibility Scanner : 0 alerte de taille).
38. Lignes annoncées « titre, sous-ligne, état », avec les actions Valider et Actions rapides ; carte Série lue en une phrase.
39. Aucun état signalé par la couleur seule (contrôle en niveaux de gris).
40. Rôles Tab, Switch, RadioButton et Heading présents.

**Performance**
41. Macrobenchmark sur appareil modeste, build release : P90 ≤ 16 ms, P99 ≤ 24 ms, 0 image > 32 ms en validation.
42. Fond animé désactivé par défaut ; activé, il reste limité à 360 dp, fondu, et figé au défilement.
43. Les animations infinies s'arrêtent hors écran et en arrière-plan.
44. Baseline Profile présent (Aujourd'hui → valider → fiche).
45. Les captures de l'app correspondent aux planches `ui/out/P-ds-today.png`, `P-ds-kit.png`, `P-ds-motion.png`, `P-ds-goal.png`, `P-ds-profile.png`, `P-ds-settings.png` (écarts tolérés : contenu réel).

---

## 13. Changements à faire dans le code (pour le développeur)

Chemins relatifs à `app/src/main/java/fr/pepsgoal/app/ui/`. Les valeurs de référence sont dans `ui/src/main/kotlin/app/Ds.kt` : objets `LIGHT`, `DARK`, `FLAME`, `NEON`, `TINK`, `PLUM` ; matière `Chunk` ; composables `Pop*`.

### 13.1 `theme/Theme.kt`
1. **`Pal`** : ajouter `surface`, `surface2`, `edge`, `control`, `plum` (= `onColor`), `flameStep`, `gold`, `deco: List<Color>`, `ice`. Garder `flame2/flame/pink` comme arrêts de `flame` ; `onAccent` = `plum` pour l'accent Braise.
2. **`PepsTheme`** : remplacer `bg`, `ink`, `ink2`, `ink3`, `line` (= `edge`), `ok`, `danger`, `scrim` par les valeurs §2.1-2.2 ; supprimer le calcul `glass`/`glassStrong` (garder les champs pour la compatibilité, égaux à `surface`).
3. **`TYPE_LIGHT`** : garder les néons existants (ce sont ceux du §2.3) et leurs encres ; **supprimer `desat` en sombre** (néons identiques dans les deux thèmes) ; `TYPE_DARK_INK` inchangé. Ajouter `TypeColor.step` = `lerp(c, #5A1030, .32f)`.
4. **Typographie** : `displayLarge` = Unbounded 60 ExtraBold, `displayMedium` 48, `headlineLarge` = Unbounded 30 ExtraBold, `headlineMedium` 28, `headlineSmall` 22, `titleLarge` = Unbounded 18 Bold, `titleMedium` = Unbounded 16 Bold, `titleSmall` = Manrope 16 ExtraBold, `bodyLarge` = Manrope 16 SemiBold, `bodyMedium` = 15 Bold, `bodySmall` = 13 SemiBold, `labelLarge` = 17 ExtraBold, `labelMedium` = 13 ExtraBold, `labelSmall` = 12 ExtraBold.
5. `colorScheme` : `background = bg`, `surface = surface`, `surfaceContainer = surface2`, `outline = control`, `outlineVariant = edge`, `primary = accentInk`.
6. Variantes §2.4 (contraste, OLED, Material You, accents, ambiances).

### 13.2 `Depth.kt` (matières)
1. Nouveau **`Modifier.chunk(fill: Brush, edge: Color, radius: Dp, step: Dp = 4.dp, border: Dp = 2.dp, pressed: () -> Boolean)`**. Il dessine le bord et la marche en `drawBehind` (un rectangle arrondi), puis le remplissage, et applique `graphicsLayer { translationY = if (pressed()) step.toPx() else 0f }` sans recomposition. Logique de `Chunk` dans `Ds.kt`, version modificateur.
2. Nouveau **`Modifier.pressable(inter, step)`** : lit `collectIsPressedAsState`, anime la translation avec le ressort `press`, déclenche `Feedback.tap()`.
3. `Pal.surface`, `Pal.card` = `surface` opaque ; `hairline()` = `edge`.
4. `depth()`, `glow()`, `sheen()` : renvoient `this` (à supprimer à la fin de la migration). `candy()` = aplat ou dégradé `flame` + marche, sans halo.
5. `Squircle` / `smoothShape` : garder pour les tuiles ; cartes et lignes en `RoundedCornerShape` (moins coûteux, rendu identique à ces rayons).

### 13.3 `Motion.kt`
1. Remplacer les jetons : `press()` = `spring(.6f, 1500f)`, `pop()` = `spring(.45f, 500f)`, `move()` = `spring(.75f, 380f)`, `sheet()` = `spring(.82f, 300f)`, `enter()` = `spring(.7f, 420f)` ; garder `fadeIn`/`fadeOut`.
2. Nouveau `RollingNumber(value: Int, style)` : chiffres qui roulent verticalement (`AnimatedContent` avec `slideInVertically` / `slideOutVertically` en `move`, tick haptique).
3. Nouveau `Modifier.popOnChange(key)` : échelle 1 → 1,15 → 1 (`pop`) quand `key` change.
4. Nouveau `Modifier.shake(trigger)` : ±6 dp, 300 ms.
5. Nouveau `Confetti(count = 24, onEnd)` : un `Canvas`, particules en aplats, 1,2 s.
6. `DrawnCheck` : 200 ms.

### 13.4 `Components.kt`
1. **`Glass`** → **`Card`** (alias `Glass` déprécié) : `chunk(surface, edge, 24.dp)`, marge 16 ; supprimer `wash`, `ai` et le verre ; `onClick` ⇒ `pressable`. `CardHead` : emoji 22 + Unbounded 16 Bold.
2. **`glassBg`**, **`EdgeFrost`**, **`aiGlow`**, **`washOf`** : ne plus les utiliser (barres opaques, coach IA signalé par une pilule ✨ dans l'en-tête de section).
3. **`PButton`** : variantes `primary` (`flame` + `flameStep`), `type` (nouveau paramètre `color` = néon + `step`), `secondary`, `danger`, `ghost` ; capsule de 54 (44) + marche 4 ; libellé 17 ExtraBold, prune sur les aplats ; `pressable` ; remplacer `bounce`.
4. **`IconBtn`** / **`RoundGlass`** → `RoundBtn` : 44 dp à marche 3, `surface`, icône 20.
5. **`Badge`** : 20 dp, `pink`, chiffre prune 11 ExtraBold, `popOnChange(n)`.
6. **`Chip`** / **`TypeChip`** : capsule 26, aplat néon, texte prune 12 ExtraBold.
7. **`FChip`** : capsule 42, repos `chunk` neutre, choisie = aplat néon (couleur du dossier, sinon `monthly`) + marche `step`, texte prune.
8. **`Seg`** : rail capsule 50 `surface2`, segment choisi à marche, indicateur animé en `move`.
9. **`PSwitch`** : 60 × 36, allumé `routine` + pastille blanche à marche avec coche prune, saut de pastille en `pop`.
10. **`Bar`** : 10 dp par défaut, rail `surface2`, néon ou `flame`, `move`.
11. **`Ring`** : trait 15 % de la taille, rail `surface2`, chiffre Unbounded ExtraBold.
12. **`EmojiTile`** / **`GoalIcon`** : aplat néon plein (plus 16-20 % d'alpha), rayon 32 % de la taille, marche 3 dp `step`, emoji à 66 %.
13. **`IconTile`** : tuile néon 38, rayon 12, icône prune.
14. **`StatGrid`** : tuiles `chunk` néon (couleurs §7.19), emoji, Unbounded 22, libellé prune.
15. **`Empty`** : emoji 80 qui flotte, titre Unbounded 20, bouton principal.
16. **`Field`** : 56, rayon 18, `chunk` creusé, focus en `flame[1]`.
17. **`SectionTitle`** → `SectionTitle(emoji, title, count, action)`.
18. **`Stagger`** / **`enter()`** : ressort `enter`, seulement à l'ouverture et au changement de jour.

### 13.5 `Controls.kt`
1. **`CheckButton`** : 44 / 48, états §7.3 (paramètre `progress: Float?`) ; validation = `pop` (0,6 → 1,25 → 1) + `DrawnCheck` prune + `CONFIRM`. Supprimer l'onde (`wave`).
2. **`MiniBtn`** : ne plus l'utiliser dans les lignes ; la commande Progrès de la case le remplace.
3. **`NavBar`** : capsule flottante opaque à marche (§7.10) ; pilule `flame` de 1,7 fois la largeur d'un onglet qui glisse en `move` ; icône et libellé prune ; onglets au repos en icône seule ; `pop` sur l'icône active.
4. **`Fab`** : 64 dp, `flame` + marche 5, icône prune 30 ; caché ou affiché en `move` / `pop`.
5. **`TabTitle`** : Unbounded 30 ExtraBold.
6. **`StreakPill`** : retirée de la barre du haut.
7. Nouveau **`AvatarButton`** : 40 dp, anneau `flame` 3 dp, badge de niveau prune / or.

### 13.6 `GoalUi.kt`
1. **`GoalRow`** :
   - structure §7.2 : `chunk` neutre rayon 22, ligne min 76 dp, tuile néon 52, titre 16 ExtraBold, sous-ligne 13 SemiBold, barre néon 8 dp pour les types chiffrés, case §7.3 à droite ;
   - supprimer le fond `well`, `FlowRowCompat` + `TypeChip` (sauf écran Objectifs) et les `MiniBtn` ;
   - fait : fond et bord teintés du néon, sous-ligne en `ink` du type, puis glissade (`move`) vers « Fait » après 1,5 s ;
   - garder le balayage, TalkBack, pause, sous-tâches et duo.
2. **`WeekStrip`** / **`Last7`** : pastilles 34 dp (flamme sur prune, 🧊, pointillés, point).
3. **`GoalCalendar`**, **`QtyChart`**, **`MeasureChart`** : barres néon, rayon 4, rail `surface2`, légendes 12 Bold `ink3`.

### 13.7 `screens/TodayScreen.kt` et `screens/Home61.kt`
1. **`HomeHeader`** → carte Série §7.13 (voir `PopStreakCard`) :
   - `chunk(flame, flameStep, 28.dp, 6.dp)` ;
   - `RollingNumber` en Unbounded 60 prune ;
   - médaillon blanc incliné + `Flame(64.dp)` qui danse ;
   - semaine en pastilles ; pilules jokers / record / faits ;
   - message selon l'état ; un toucher ouvre la feuille de série.
   - Retirer de l'en-tête : salutation, anneau, barre de niveau, objectif du mois et rangée de `FChip` (déplacés).
2. **`Hero`** / **`HeroFlame`** : `HeroFlame` = médaillon + danse (`graphicsLayer` rotation ±4°, scaleY ±6 %, 15 i/s, arrêt hors écran / 20 s) ; garder les chatouilles (easter egg) avec `shake`.
3. **`DueCard`** → **`DueSections`** : groupement par moment (☀️ / 🌤️ / 🌙 / 🌈), `SectionTitle(emoji, …)`, lignes espacées de 10 dp, section ✅ Fait repliée ; coach IA = pilule ✨ « Ordre conseillé » dans l'en-tête de section.
4. **`TodayScreen`** : ordre fixe (décor, carte Série, sections, modules) ; marge basse 166 dp ; `Confetti` + toast 🎉 à la journée parfaite (une fois par jour, drapeau en préférences).
5. Nouveau **`HomeDeco`** : 2 cercles `deco` en `drawBehind` derrière l'en-tête ; remplacé par `Backdrop(confined = true)` si le réglage Fond animé est actif.
6. Modules secondaires (`WeatherCard`, `CountdownCard`, `MomentsRow`, `RecapCard`, `QuickTools`, `ContextCards`) : `Card` à marche, sous la liste.

### 13.8 `Root.kt`
1. **`NAV`** : 4 onglets (today, goals, social, profile) ; nouvel écran `SocialScreen` (segmenté Feed | Amis | Ligue) ; `mod` dans le Profil pour les admins.
2. **`Shell`** :
   - fond `bg` + décor (plus de `Backdrop` plein écran par défaut) ;
   - supprimer `hazeSource` et les deux `EdgeFrost` ;
   - `NavBar` flottante (marges 14, bas 18 + inset) ; `Fab` à end 18, bottom = barre + 18 ;
   - transition d'onglet : fondu 150 ms + glissement de 12 dp.
3. **`TopBar`** : date + titre Unbounded 30 ; `RoundBtn` notifications + `AvatarButton` ; sans verre ni filet ; au défilement, titre en 20 sp sur fond `bg`.
4. **`Toasts`** : §7.16 (prune à marche, action or, `pop`), un seul à la fois ; supprimer la barre de temps et `IconTile`.
5. **`PullRefresh`** : médaillon blanc à marche + flamme qui danse pendant le chargement ; supprimer `glow`.
6. **`Sparks`** → `Confetti` (§9, moments forts uniquement).

### 13.9 `Backdrop.kt`
1. `Backdrop(…, confined: Boolean, frozen: Boolean)` : en mode confiné, 360 dp, opacité 70 / 55 %, masque vertical (voir `PopDeco` dans `Ds.kt`) ; figé pendant le défilement, en économie d'énergie et en « Animations réduites ».
2. `bgPhaseState` : 12 i/s maximum, arrêt hors écran.

### 13.10 Feuilles, réglages et autres écrans
1. **`sheets/SheetHost.kt`** : coins 32, fond `bg`, `scrim` prune ; ouverture en ressort `sheet` ; `SheetHead` en Unbounded 22 + `RoundBtn` fermer ; `SheetFoot` = bouton principal à marche pleine largeur.
2. **`sheets/GoalSheets.kt` (`GoalSheet`)** : §8.5 ; bandeau néon (voir `PopGoalSheet`), carte d'action avec `display` 48, boutons de type, `StatGrid` néon, histogramme.
3. **`SettingsKit.kt`** : `SetGroup` = `SectionTitle(emoji, titre)` + groupe à marche ; `SetItem` / `SetToggle` = ligne 60 avec `IconTile` néon 38 (couleur fixe par entrée), séparateurs 2 dp `surface2`, retrait 66 ; `SetHero` remplacé par l'en-tête de section illustré.
4. **`SettingsScreen.kt`** : ajouter Apparence › Fond animé (Non / Haut de l'accueil) et Apparence › Animations (Complètes / Réduites) ; retirer les réglages de flou et d'opacité du verre.
5. **`GoalsScreen.kt`** : tuiles de stat néon, puces néon par dossier, lignes §7.2 avec étiquette de type.
6. **`ProfileScreen.kt`** : §8.3 ; `ProfileHero` → carte identité à marche ; `ProfileTile` → `StatGrid` néon ; menus en lignes avec `IconTile` néon.
7. **`FeedScreen.kt` + `FriendsScreen.kt`** : réunis dans `SocialScreen`.
8. **`IconPaths.kt`** : ajouter `chevronright`, `undo`, `more`, `filter`.

### 13.11 Ordre d'application conseillé
1. Jetons et matière (`Theme.kt`, `Depth.kt` avec `chunk` et `pressable`, `Motion.kt`).
2. Composants (`Components.kt`, `Controls.kt`).
3. `GoalRow` et `CheckButton`.
4. Coquille (`Root.kt` : barre d'onglets, barre du haut, bouton +, toasts).
5. Aujourd'hui (carte Série, sections, confettis).
6. Fiche et feuilles.
7. Réglages, Profil, Objectifs, Social.
8. Fond animé confiné.
9. Mesures (Macrobenchmark) et passage de la check-list §12.

---

## 14. Ébauche visuelle

Maquettes autonomes : `ui/src/main/kotlin/app/Ds.kt`. Planches clair | sombre dans `ui/out/` :

- `P-ds-today.png` : écran Aujourd'hui (barre du haut, carte Série, sections avec états fait, progrès, minuteur et fermé, bouton +, barre d'onglets) ;
- `P-ds-today-fx.png` : même écran avec le fond animé confiné ;
- `P-ds-kit.png` : boutons à marche (dont l'état appuyé), puces, étiquettes, segmenté, cases, interrupteurs, anneau, barres, tuiles ;
- `P-ds-motion.png` : images clés (validation qui « pop », appui, flamme qui danse, compteur qui roule, toast) ;
- `P-ds-goal.png` : fiche d'objectif ;
- `P-ds-profile.png` : Profil ;
- `P-ds-settings.png` : Réglages.

Rendu : `sh ui/sync.sh && cd ui && gradle -q --offline --no-daemon run --args="ds-today,ds-today-fx,ds-kit,ds-motion,ds-goal,ds-profile,ds-settings"`, puis `python3 ui/mont.py` avec les mêmes noms.

Écarts connus avec la spécification : barre d'état simulée, contenu fictif, mouvements montés en images fixes. L'ancienne direction v1 est archivée dans `ui/out/braise-v1/`, `design/archive/design-system-braise-v1.md` et `design/archive/src/Ds-braise-v1.kt.txt`.
