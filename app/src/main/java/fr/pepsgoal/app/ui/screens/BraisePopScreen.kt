package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.typeInfo

/** Principes de Braise Pop : emoji, titre, explication. */
private val BP_PRINCIPLES = listOf(
    Triple("👀", "Lisible en 2 secondes, vivant en 1", "La structure reste simple ; l’énergie vient de la couleur, du volume et du mouvement."),
    Triple("🎨", "Aplats, jamais d’effets", "Couleur pleine, bord et marche pleine. Pas de flou, de halo, de reflet ni d’ombre floue."),
    Triple("👆", "Tout ce qui se touche a du volume", "Un élément avec une marche se presse ; un élément plat ne se presse pas."),
    Triple("🖋️", "Encre prune sur la couleur", "Sur tout aplat vif, le texte est prune #2A1420, jamais blanc : au moins 5,1:1 de contraste."),
    Triple("🔥", "Une couleur par objectif, une flamme pour l’app", "Chaque type d’objectif a son néon ; la flamme or → braise → rose est l’emblème."),
    Triple("🏀", "Le rebond est un retour, pas un décor", "Chaque animation répond à un geste ou à un événement."),
    Triple("🎉", "La fête au bon moment", "Confettis pour une journée parfaite, un palier ou un badge, jamais à chaque coche."),
    Triple("💛", "Bienveillance", "Jokers, repos et rattrapage bien visibles, et un ton qui encourage."))

/** Étapes de l'histoire du design : emoji, version, nom, récit. */
private val BP_HISTORY = listOf(
    listOf("🍬", "7.0", "Verre bonbon", "Dégradés, halos et verre flouté sur chaque carte. Joli, mais jugé trop chargé et lourd pour le téléphone."),
    listOf("🍏", "7.1", "Sobriété « iOS 27 »", "Cartes nettes, verre réservé aux barres, animations calmes. Plus professionnel, mais trop sage."),
    listOf("🔥", "8.0", "Braise Pop", "Né d’un travail en quatre étapes : une veille des tendances et des applis concurrentes, un designer, un développeur et un auditeur. Une première ébauche « Braise » trop proche d’une appli Google a été refusée ; la version « Pop », vivante et rebondissante, a été retenue. Audit : 34 points sur 45."),
    listOf("🏞️", "8.1", "Braise Pop partout", "Toute l’interface y passe, sous-menus compris, et le fond illustré devient visible dans toute l’app. 30 composants maison remplacent les éléments Android standard. Audit : 139 points sur 141."),
    listOf("✨", "8.2 → 8.5", "Affinages", "Filtres en fenêtres, onglets à pictogrammes, libellés qui ne coupent jamais un mot, voiles de lisibilité, parallaxe continue, puis optimisations et régulateur de fluidité."))

/** Briques de l'intégration dans le code : emoji, titre, explication. */
private val BP_INTEGRATION = listOf(
    Triple("🎨", "Jetons", "Toutes les couleurs vivent dans une palette unique (fond, surfaces, bord, encres, flamme, néons). Chaque ambiance de la boutique ne fait que la teinter."),
    Triple("🧱", "Matière", "Une seule fonction dessine le volume à marche et gère l’enfoncement à l’appui ; aucun composant ne dessine sa propre ombre."),
    Triple("🌀", "Mouvement", "Quatre ressorts nommés (appui, pop, déplacement, feuille) animent l’échelle, la position ou l’opacité, sans jamais redessiner la mise en page à chaque image."),
    Triple("🧩", "Composants", "Boutons, cases, curseurs, menus, dialogues, feuilles, calendriers : tout est fait maison sur ces jetons, pour un rendu identique partout."),
    Triple("♿", "Accessibilité", "Texte à 4,5:1 au moins dans les deux thèmes, cibles de 48 dp, libellés pour TalkBack, texte jusqu’à 200 % et animations réduites respectées."),
    Triple("⚡", "Performance", "Pas de flou, des fonds en demi-résolution figés pendant le défilement, et un régulateur qui baisse la qualité si l’affichage ralentit, puis la remonte."),
    Triple("🧪", "Vérification", "Chaque version est vérifiée par une liste de contrôle, des tests de contraste, un banc d’aperçu qui rend les vrais écrans et une compilation automatique."))

private val BP_NEONS = listOf("routine", "objective", "deadline", "abstinence", "monthly", "limit", "duration", "project", "measure", "group", "duel", "challenge", "program", "timed", "period", "cap")

/**
 * Aide › Découvrir Braise Pop : présentation du design system de PepsGoal (principes, couleurs, typographie, volume à
 * marche, composants à essayer, histoire de sa conception et intégration dans le code). Les démonstrations utilisent
 * les vrais composants de l'application.
 */
@Composable
fun BraisePopScreen(ui: AppState) {
    val p = LocalPal.current
    var tab by remember { mutableIntStateOf(0) }
    ScreenFrame(ui, "Découvrir Braise Pop", "Aide", { ui.back() }, emoji = "🔥", color = p.flame, color2 = p.type("deadline").c) {
        item("tabs") {
            PTabs(listOf("Aperçu" to "🔥", "Couleurs" to "🎨", "Composants" to "🧩", "Histoire" to "📜", "Intégration" to "🛠️"), tab, { tab = it })
        }
        when (tab) {
            0 -> {
                item("intro") { BpIntro() }
                item("princ") {
                    SetGroup("Principes", emoji = "📐") {
                        BP_PRINCIPLES.forEach { (e, title, sub) -> BpLine(e, title, sub) }
                    }
                }
            }
            1 -> {
                item("flame") {
                    Glass {
                        CardHead("La flamme", emoji = "🔥"); Muted("Or, braise et rose, en dégradé diagonal. Réservée à la carte Série, au bouton +, à l’onglet actif, au bouton principal et au niveau.")
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(64.dp).chunk(p.flameBrush(), p.flameStep, 20.dp, 4.dp, 0.dp))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Swatch(Color(0xFFFFB000), "Or", "#FFB000", Modifier.weight(1f)); Swatch(Color(0xFFFF4A12), "Braise", "#FF4A12", Modifier.weight(1f)); Swatch(Color(0xFFFF3D96), "Rose", "#FF3D96", Modifier.weight(1f))
                        }
                    }
                }
                item("neutral") {
                    Glass {
                        CardHead("Les neutres", emoji = "🌗"); Muted("Crème pêche le jour, prune nuit le soir. Les cartes sont toujours opaques ; le bord et la marche partagent la même couleur.")
                        Spacer(Modifier.height(12.dp))
                        FlowRowCompat(gap = 8.dp) {
                            Swatch(p.bg, "Fond", null, Modifier.width(96.dp)); Swatch(p.surface, "Surface", null, Modifier.width(96.dp)); Swatch(p.surface2, "Creux", null, Modifier.width(96.dp))
                            Swatch(p.edge, "Bord et marche", null, Modifier.width(96.dp)); Swatch(p.ink, "Encre", null, Modifier.width(96.dp)); Swatch(p.ink2, "Secondaire", null, Modifier.width(96.dp)); Swatch(PLUM, "Prune", "#2A1420", Modifier.width(96.dp))
                        }
                    }
                }
                item("neons") {
                    Glass {
                        CardHead("16 néons d’objectif", emoji = "🌈"); Muted("Un néon par type d’objectif, identique dans les deux thèmes, avec sa marche (le néon mêlé de prune) et son encre pour le texte.")
                        Spacer(Modifier.height(12.dp))
                        BP_NEONS.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { k -> NeonTile(k, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                item("typo") {
                    Glass {
                        CardHead("Typographie", emoji = "🔤"); Muted("Unbounded pour les titres et tous les chiffres ; Manrope (jamais en Regular) pour le reste. Chiffres tabulaires partout.")
                        Spacer(Modifier.height(12.dp))
                        Text("12", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 60.sp, lineHeight = 64.sp)
                        Text("Titre d’écran", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp)
                        Text("Titre de section", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Titre de ligne", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Texte courant, en Manrope Bold", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = p.ink2)
                        Text("Légende en Manrope SemiBold", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = p.ink2)
                    }
                }
            }
            2 -> {
                item("step") {
                    Glass {
                        CardHead("Le volume « à marche »", emoji = "🧱"); Muted("Une seule technique pour tout le relief : une forme pleine, un bord de 2 dp et une marche pleine décalée vers le bas. À l’appui, l’élément descend de la hauteur de sa marche. Essaie :")
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PButton("Appuie sur moi", { Feedback.tap() }, kind = "primary")
                            IconBtn("plus", "+", { })
                        }
                    }
                }
                item("comp") { BpComponents(ui) }
            }
            3 -> {
                item("hist") {
                    SetGroup("D’une version à l’autre", emoji = "📜") {
                        BP_HISTORY.forEach { (e, v, name, story) -> BpLine(e, "$v · $name", story) }
                    }
                }
                item("process") {
                    SetGroup("Le design en quatre étapes", emoji = "🧭") {
                        BpLine("🔎", "Veille", "Tendances, meilleures applis et concurrents"); BpLine("✏️", "Designer", "Règles, jetons et ébauche visuelle validée")
                        BpLine("🛠️", "Développeur", "Mise en place dans le code, lot par lot"); BpLine("✅", "Auditeur", "Liste de contrôle, corrections et publication")
                    }
                }
            }
            else -> {
                item("integ") {
                    SetGroup("Comment Braise Pop est construit dans l’app", emoji = "🛠️") {
                        BP_INTEGRATION.forEach { (e, title, sub) -> BpLine(e, title, sub) }
                    }
                }
            }
        }
    }
}

/** Carte d'ouverture : la flamme en médaillon et le résumé du design system. */
@Composable
private fun BpIntro() {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().chunk(p.flameBrush(), p.flameStep, 28.dp, 6.dp, 0.dp).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Braise Pop", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, color = PLUM)
                Text("Braise Pop est le design system de PepsGoal depuis la version 8 : une interface qui a de l’énergie et qui reste légère.", fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, color = PLUM)
                Text("Aplats de couleurs franches, volumes « à marche » qui s’enfoncent sous le doigt, titres en Unbounded, grands emojis et animations qui rebondissent, sans aucun flou.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, color = PLUM)
            }
            Box(Modifier.size(84.dp).chunk(Color.White, p.flameStep, 42.dp, 5.dp, 0.dp), contentAlignment = Alignment.Center) { Flame(56.dp) }
        }
    }
}

/** Ligne explicative : emoji, titre en gras, texte. */
@Composable
private fun BpLine(e: String, title: String, sub: String) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Emoji(e, 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = p.ink)
            Text(sub, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, color = p.ink2)
        }
    }
}

/** Pastille de couleur avec son nom (et sa valeur). */
@Composable
private fun Swatch(c: Color, name: String, hex: String?, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(c).border(2.dp, p.edge, RoundedCornerShape(14.dp)))
        Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        if (hex != null) Text(hex, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = p.ink2)
    }
}

/** Tuile néon d'un type d'objectif : aplat à marche, nom du type en prune. */
@Composable
private fun NeonTile(k: String, modifier: Modifier) {
    val p = LocalPal.current; val c = p.type(k).c
    Box(modifier.height(58.dp).chunk(c, if (p.contrast) p.ink else c.step(), 16.dp, 3.dp, 0.dp).padding(6.dp), contentAlignment = Alignment.BottomStart) {
        FitText(typeInfo(k).label, size = 12, min = 8, color = PLUM, weight = FontWeight.ExtraBold)
    }
}

/** Composants à essayer : les vrais, avec un état local. */
@Composable
private fun BpComponents(ui: AppState) {
    val p = LocalPal.current
    var seg by remember { mutableStateOf("w") }
    var sw1 by remember { mutableStateOf(true) }; var sw2 by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(.5f) }
    var radio by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }; var count by remember { mutableIntStateOf(11) }
    var steps by remember { mutableIntStateOf(25) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted("Toutes les briques ci-dessous sont les vraies, celles qu’utilise l’application. Touche-les pour voir leurs réactions.")
        Glass {
            CardHead("Boutons", emoji = "👆")
            FlowRowCompat(gap = 10.dp) {
                PButton("Principal", { }, kind = "primary", small = true)
                PButton("Secondaire", { }, small = true)
                PButton("+250 ml", { }, icon = "plus", kind = "type", color = p.type("cap").c, small = true)
                PButton("Discret", { }, kind = "ghost", small = true)
            }
        }
        Glass {
            CardHead("Choix", emoji = "🎚️")
            Seg(listOf("d" to "Jour", "w" to "Semaine", "m" to "Mois"), seg, { seg = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            FlowRowCompat(gap = 8.dp) { FChip("Jour", seg == "d", { seg = "d" }); FChip("Semaine", seg == "w", { seg = "w" }); FChip("Mois", seg == "m", { seg = "m" }) }
        }
        Glass(pad = 0.dp) {
            Column(Modifier.padding(16.dp)) { CardHead("Interrupteur, cases et curseur", emoji = "🔘") }
            SetToggle("Animations", null, "sparkle", p.type("monthly").c, on = sw1, onChange = { sw1 = it })
            SetToggle("Rappel du soir", null, "moon", p.type("period").c, on = sw2, onChange = { sw2 = it })
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Présence du fond", fontWeight = FontWeight.ExtraBold)
                PSlider(slider, { slider = it }, steps = 2, bubble = { v -> listOf("Discrète", "Équilibrée", "Intense")[(v * 2 + .5f).toInt().coerceIn(0, 2)] })
            }
            Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                PRadioRow("Simplifiée", "L’essentiel à valider", radio == 0, { radio = 0 }, emoji = "✨")
                PRadioRow("Classique", "Toutes les cartes à la suite", radio == 1, { radio = 1 }, emoji = "📋")
            }
        }
        Glass {
            CardHead("Validation et progression", emoji = "✅"); Muted("Touche la case : elle « pop », puis le compteur roule.", small = true)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EmojiTile("🧘", p.type("abstinence").c, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text("Méditer 10 minutes", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) { RollingNumber(count, size = 20.sp, color = p.ink); Text("🔥", fontSize = 16.sp) }
                }
                CheckButton(done, done, "Méditer 10 minutes", p.type("abstinence").c) { done = !done; count += if (done) 1 else -1; if (done) Feedback.success() }
            }
            Spacer(Modifier.height(12.dp))
            Bar(if (done) 100.0 else 62.0, color = p.type("abstinence").c)
            Spacer(Modifier.height(12.dp))
            StatGrid(listOf("$count" to "jours de série", "48" to "record", "709" to "validés"), columns = 3, emojis = listOf("🔥", "🏆", "✅"), colors = listOf(p.type("limit").c, p.type("monthly").c, p.type("routine").c))
        }
        Glass {
            CardHead("Pas à pas", emoji = "➕")
            PStepper("$steps", "min", { steps = (steps - 5).coerceAtLeast(5) }, { steps = (steps + 5).coerceAtMost(90) }, minusEnabled = steps > 5, plusEnabled = steps < 90)
        }
        Glass {
            CardHead("Chargement", emoji = "⏳")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { PSpinner(); Text("Synchronisation…", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp)); PIndeterminate()
        }
        Glass {
            CardHead("Plaques et toasts", emoji = "🪧")
            MiniPlaque("Texte posé sur le fond", emoji = "📅")
            Spacer(Modifier.height(12.dp))
            PButton("Afficher un toast", { ui.toast("💧 +250 ml · 1,45 / 2 L", "drop") }, icon = "bell", small = true)
        }
    }
}
