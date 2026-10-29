package e81

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.StreakMock
import app.MockRow
import app.goal
import app.ME
import app.NAVM
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.*

val E81_SCREENS: List<Pair<String, @Composable () -> Unit>> = listOf(
    "e81-scenes" to { ScenesTest() },
    "e81-home" to { E81Home() },
    "e81-stats" to { E81Stats() },
    "e81-sheet" to { E81Sheet() },
    "e81-goal" to { E81Goal() },
    "e81-chat" to { E81Chat() },
    "e81-shop" to { E81Shop() },
    "e81-settings" to { E81Settings() },
    "e81-menu" to { E81Menu() },
    "e81-dialog" to { E81Dialog() },
    "e81-profile" to { E81Profile() },
    "e81-kit" to { E81Kit() },
)

const val SCENE = "loire"

/* ------------------------------------------------------------------ coquille d'onglet */

@Composable
fun TabShell(key: String, tab: String, fab: Boolean = true, fabUp: Boolean = false, progress: Float? = null, content: @Composable ColumnScope.() -> Unit, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        AppBg(key, 1, progress)
        Column(Modifier.fillMaxSize()) { StatusBar(); content() }
        CompositionLocalProvider(LocalPal provides tintedPal(key)) {
            NavBar(NAVM, tab, mapOf("social" to 2), {}, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 18.dp))
        }
        if (fab) Fab("Nouvel objectif", true, {}, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = if (fabUp) 172.dp else 104.dp))
        overlay()
    }
}

@Composable
fun HomeBody() {
    TabHero("Aujourd’hui", "Jeudi 29 octobre", 150.dp) {
        RoundGlass("Notifications", {}, badge = 3) { Pi("bell", size = 20.dp, tint = LocalPal.current.ink) }
        AvatarButton(ME, 17, {})
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StreakMock()
        SectionPlaque("Ce matin", "☀️", 2)
        MockRow(goal("a", "Méditer 10 minutes", "🧘", "abstinence"), true, "Fait à 7:42 · série de 9 🔥")
        MockRow(goal("b", "Boire 2 L d’eau", "💧", "cap"), false, "1,45 / 2 L · appui = +250 ml", 72.0, prog = .72f)
        SectionPlaque("Dans la journée", "🌈", 3, "✨ Ordre")
        MockRow(goal("c", "Lire 20 pages", "📚", "objective"), false, "120 / 300 pages · J-12", 40.0, prog = .4f)
    }
}

@Composable
fun E81Home() = TabShell(SCENE, "today", fabUp = true, progress = .6f, content = { HomeBody() }) {
    ToastPop("💧", "+250 ml · 1,45 / 2 L", "Annuler", Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 98.dp))
}

/* ------------------------------------------------------------------ Statistiques (page poussée) */

@Composable
fun E81Stats() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg(SCENE, 1, null, heroY = 120.dp)
        Column(Modifier.fillMaxSize()) {
            StatusBar()
            PageHero("Statistiques", "📈", p.type("project").c) { RoundGlass("Partager", {}) { Pi("share", size = 20.dp, tint = p.ink) } }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PTabs(listOf("Semaine" to null, "Mois" to null, "Année" to null), 1, p.type("project").c)
                StatGrid(listOf("1 204" to "validations", "48 j" to "meilleure série", "86 %" to "réussite du mois", "+12 %" to "vs septembre"), columns = 2,
                    emojis = listOf("✅", "🏆", "🎯", "📈"), colors = listOf(p.type("routine").c, p.type("monthly").c, p.type("duration").c, p.type("objective").c))
                Glass {
                    CardHead("Ton année", emoji = "🗓️") { Count("214 jours actifs") }
                    Heat()
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Moins", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2)
                        heatLevels().forEach { c -> Box(Modifier.size(14.dp).background(c, RoundedCornerShape(4.dp))) }
                        Text("Plus", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2)
                    }
                }
                Glass {
                    CardHead("Tes objectifs (30 jours)", emoji = "🎯")
                    listOf(Triple(goal("b", "Boire 2 L d’eau", "💧", "cap"), 93.0, "28/30"), Triple(goal("a", "Méditer", "🧘", "abstinence"), 80.0, "24/30")).forEach { (g, pct, v) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GoalIcon(g, 40.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row { Text(g.title, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text(v, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = p.type(g.type).ink) }
                                Bar(pct, color = p.type(g.type).c, height = 10.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 5 niveaux en aplats (plus d'alpha) : creux, puis braise mélangée à 30 / 55 / 80 / 100 %. */
@Composable
fun heatLevels(): List<Color> { val p = LocalPal.current; val f = hx("FF4A12"); return listOf(p.surface2, lerp(p.surface, f, .3f), lerp(p.surface, f, .55f), lerp(p.surface, f, .8f), f) }

@Composable
fun Heat() {
    val lv = heatLevels()
    Canvas(Modifier.fillMaxWidth().height(13.dp * 7 + 3.dp * 6)) {
        val g = 3.dp.toPx(); val cols = 22; val c = (size.width - g * (cols - 1)) / cols
        for (x in 0 until cols) for (y in 0 until 7) {
            val v = ((x * 7 + y) * 2654435761L % 97).toInt()
            val l = when { x == cols - 1 && y > 3 -> -1; v < 18 -> 0; v < 40 -> 1; v < 62 -> 2; v < 84 -> 3; else -> 4 }
            if (l >= 0) drawRoundRect(lv[l], Offset(x * (c + g), y * (13.dp.toPx() + g)), Size(c, 13.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
    }
}

/* ------------------------------------------------------------------ feuille par-dessus le fond */

@Composable
fun SheetFrame(height: Dp, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        // Voile de feuille allégé : la scène reste visible au-dessus de la feuille
        Box(Modifier.fillMaxSize().background(if (p.dark) Color.Black.copy(alpha = .5f) else PLUM.copy(alpha = .32f)))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height).chunkTop(p).padding(horizontal = pad), content = content)
    }
}

/** Feuille : coins hauts 32, fond `bg` opaque, bord 2 dp `edge` sur le haut (la marche est remplacée par le bord : la feuille est posée). */
@Composable
fun Modifier.chunkTop(p: Pal): Modifier = this.clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(p.edge).padding(top = 2.dp, start = 2.dp, end = 2.dp)
    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(p.bg)

@Composable
fun Handle() { Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(40.dp, 5.dp).background(LocalPal.current.ink3, CircleShape)) } }

@Composable
fun E81Sheet() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        TabShell(SCENE, "today", progress = .6f, content = { HomeBody() })
        SheetFrame(560.dp) {
            Handle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nouvel objectif", Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                RoundGlass("Fermer", {}) { Pi("x", size = 18.dp, tint = p.ink) }
            }
            Text("Qu’est-ce que tu veux suivre ?", Modifier.padding(bottom = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink2)
            val types = listOf(Triple("🔁", "Routine", "routine"), Triple("💧", "Cap du jour", "cap"), Triple("🚭", "Sans", "abstinence"),
                Triple("📚", "Objectif", "objective"), Triple("⏱️", "Durée", "duration"), Triple("⏰", "Échéance", "deadline"),
                Triple("👥", "En groupe", "group"), Triple("⚔️", "Duel", "duel"), Triple("📏", "Mesure", "measure"))
            types.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (e, l, t) ->
                        Column(Modifier.weight(1f).chunk(p.type(t).c, p.type(t).c.step(), 22.dp, 4.dp, 0.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Emoji(e, 34.dp); Text(l, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PLUM)
                        }
                    }
                }
            }
            PButton("Partir d’un modèle", {}, Modifier.fillMaxWidth(), icon = "sparkle")
        }
    }
}

/* ------------------------------------------------------------------ fiche d'objectif : ajout rapide, cap +1 */

@Composable
fun E81Goal() {
    val p = LocalPal.current; val c = p.type("cap").c
    Box(Modifier.fillMaxSize()) {
        TabShell(SCENE, "today", progress = .6f, content = { HomeBody() })
        SheetFrame(845.dp, pad = 0.dp) {
            Column(Modifier.fillMaxWidth()) {
                // Bandeau néon 176, pleine largeur (coins de la feuille)
                Box(Modifier.fillMaxWidth().height(176.dp).background(c)) {
                    Box(Modifier.matchParentSize().clipToBounds()) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color.White.copy(alpha = .22f), size.height * .7f, Offset(size.width * .86f, size.height * .55f))
                            drawCircle(Color.White.copy(alpha = .16f), size.height * .22f, Offset(size.width * .55f, size.height * .1f))
                        }
                    }
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(40.dp, 5.dp).clip(CircleShape).background(PLUM.copy(alpha = .35f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Cap quotidien · Matin", Modifier.background(Color.White.copy(alpha = .5f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PLUM)
                        Text("Boire 2 L\nd’eau", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = PLUM, lineHeight = 31.sp)
                    }
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 22.dp, top = 14.dp).rotate(8f)) { Emoji("💧", 96.dp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) { Text("1,45", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 52.sp); Text(" / 2 L", Modifier.padding(bottom = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2) }
                            Text("Plus que 2 verres, tu y es presque !", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink2)
                        }
                        Ring(6, 8, 64.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Bar(72.0, color = c, height = 12.dp)
                    Spacer(Modifier.height(14.dp))
                    // Ajout rapide : le pas du cap (« + 250 ml ») en bouton de type, un multiple en secondaire, saisie libre en rond
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PButton("+ 250 ml", {}, Modifier.weight(1.25f), kind = "type", color = c, leading = { Emoji("🥛", 22.dp) })
                        PButton("+ 500 ml", {}, Modifier.weight(1f))
                        RoundGlass("Saisir une quantité", {}) { Pi("edit", size = 20.dp, tint = p.ink) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface2).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CheckButton(false, false, "", c, progress = .72f) {}
                        Text("Sur l’accueil : **un appui = + 250 ml**, appui long = saisir.".replace("**", ""), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2, lineHeight = 17.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("🔥", "8 j", "Série") to "limit", Triple("🏆", "31 j", "Record") to "monthly", Triple("📈", "86 %", "30 jours") to "duration").forEach { (v, ty) ->
                        StatTile(v.second, v.third, p.type(ty).c, v.first, Modifier.weight(1f))
                    }
                }
                Glass {
                    CardHead("Aujourd’hui", emoji = "🕘")
                    listOf("7:42" to "+ 500 ml", "10:15" to "+ 250 ml", "12:30" to "+ 500 ml", "15:02" to "+ 250 ml").forEach { (h, v) ->
                        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) { Text(h, Modifier.width(56.dp), fontWeight = FontWeight.ExtraBold, color = p.ink2, fontSize = 14.sp); Text(v, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text("Annuler", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = p.flameInk) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Messages */

@Composable
fun E81Chat() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg("balloons", 1, null, heroY = 260.dp)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                StatusBar()
                CompositionLocalProvider(LocalPal provides tintedPal("balloons")) {
                    CompactBar {
                        RoundGlass("Retour", {}) { Pi("chevronleft", size = 20.dp, tint = p.ink) }
                        AvatarFace(fr.pepsgoal.core.model.User("l", "lea", "Léa Martin"), 34.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Léa", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(8.dp).background(p.type("routine").c, CircleShape).border(1.5.dp, p.type("routine").c.step(), CircleShape)); Text("En ligne · série de 23 🔥", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2) }
                        }
                        RoundGlass("Rechercher", {}) { Pi("search", size = 20.dp, tint = p.ink) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MiniPlaque("Aujourd’hui") }
                Bubble("Tu as tenu ta série ce matin ? 😄", false, "8:02")
                Bubble("Oui ! Méditation faite à 7:42, 9 jours d’affilée 🔥", true, "8:05", react = "❤️")
                Bubble("Trop fort. On lance un duel « 10 000 pas » cette semaine ?", false, "8:07", lifted = true)
                Box(Modifier.fillMaxWidth()) {
                    PMenu(listOf(MenuItem("Répondre", "reply"), MenuItem("Copier", "copy"), MenuItem("Épingler", "pinned"), MenuItem("Supprimer pour moi", "trash", danger = true)), Modifier.padding(start = 18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("❤️", "🔥", "😂", "👏", "💪").forEach { e -> Box(Modifier.size(38.dp).background(p.surface2, CircleShape), contentAlignment = Alignment.Center) { Emoji(e, 24.dp) } }
                        }
                    }
                }
            }
            // Barre de saisie : capsule opaque à marche, un seul bouton principal (Envoyer)
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundGlass("Joindre", {}) { Pi("plus", size = 20.dp, tint = p.ink) }
                Row(Modifier.weight(1f).chunk(p.surface, p.edge, 26.dp, 3.dp, 2.dp).heightIn(min = 49.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Écris à Léa…", Modifier.weight(1f), color = p.ink3, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Emoji("😊", 24.dp)
                }
                Box(Modifier.size(52.dp, 56.dp).chunk(p.flameBrush(), p.flameStep, 26.dp, 4.dp, 0.dp), contentAlignment = Alignment.Center) { Pi("send", size = 22.dp, tint = PLUM) }
            }
        }
    }
}

/* ------------------------------------------------------------------ Boutique */

@Composable
fun E81Shop() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg(SCENE, 1, null, heroY = 120.dp)
        Column(Modifier.fillMaxSize()) {
            StatusBar()
            PageHero("Boutique", "🛍️", p.type("measure").c) { Plaque { Flame(20.dp); Text("1 240", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) } }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quêtes : carte flamme
                Row(Modifier.fillMaxWidth().chunk(p.flameBrush(), p.flameStep, 24.dp, 5.dp, 0.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(52.dp, 56.dp).chunk(Color.White, p.flameStep.copy(alpha = .55f), 26.dp, 4.dp, 0.dp), contentAlignment = Alignment.Center) { Emoji("🎯", 30.dp) }
                    Column(Modifier.weight(1f)) {
                        Text("Quête du jour", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PLUM)
                        Text("Valide 3 objectifs avant midi · +60 🔥", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PLUM)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(alpha = .45f), CircleShape)) { Box(Modifier.fillMaxWidth(.66f).fillMaxHeight().background(PLUM, CircleShape)) }
                    }
                }
                PTabs(listOf("Fonds" to "🏞️", "Thèmes" to "🎨", "Ambiances" to "🌗", "Icônes" to "📱", "Flammes" to "🔥"), 0, p.type("measure").c)
                listOf(listOf("loire" to "Loire", "mountains" to "Montagnes"), listOf("balloons" to "Montgolfières", "lighthouse" to "Phare")).forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { ci, (k, n) ->
                            val state = when { ri == 0 && ci == 0 -> "on"; ri == 0 -> "own"; ci == 0 -> "buy"; else -> "lock" }
                            Column(Modifier.weight(1f).chunk(p.surface, if (state == "on") p.type("routine").c.step() else p.edge, 24.dp, 4.dp, if (state == "on") 3.dp else 2.dp)) {
                                Box(Modifier.fillMaxWidth().height(if (ri == 0) 150.dp else 120.dp).padding(6.dp).clip(RoundedCornerShape(18.dp))) {
                                    SceneArt(k)
                                    if (state == "on") Row(Modifier.align(Alignment.TopStart).padding(8.dp).background(p.type("routine").c, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Pi("check", size = 14.dp, tint = PLUM); Text(" Actif", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = PLUM) }
                                }
                                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(n, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    when (state) {
                                        "on" -> Chip("Sur tout l’écran", "image", p.type("routine").c)
                                        "own" -> Box(Modifier.chunk(p.surface, p.edge, 18.dp, 3.dp, 2.dp).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Utiliser", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                                        "buy" -> Row(Modifier.chunk(p.gold, p.gold.step(), 18.dp, 3.dp, 0.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Flame(16.dp); Text(" 250", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PLUM) }
                                        else -> Row(verticalAlignment = Alignment.CenterVertically) { Pi("lock", size = 14.dp, tint = p.ink3); Text(" 250 · encore 310 🔥", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Réglages › Apparence › Arrière-plan */

@Composable
fun E81Settings() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg("pop", 1, null, heroY = 110.dp)
        Column(Modifier.fillMaxSize()) {
            StatusBar()
            PageHero("Apparence", "🎨", p.type("deadline").c, height = 150.dp)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass {
                    CardHead("Arrière-plan", emoji = "🏞️")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pop" to "Collines", "loire" to "Loire", "mountains" to "Montagnes").forEachIndexed { i, (k, n) ->
                            Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(78.dp, 104.dp).chunk(p.surface, if (i == 0) hx("FF4A12") else p.edge, 18.dp, 4.dp, if (i == 0) 3.dp else 2.dp).padding(2.dp).clip(RoundedCornerShape(14.dp))) { SceneArt(k) }
                                Text(n, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (i == 0) p.ink else p.ink2)
                            }
                        }
                        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(78.dp, 104.dp).chunk(p.surface2, p.edge, 18.dp, 4.dp, 2.dp), contentAlignment = Alignment.Center) { Pi("plus", size = 26.dp, tint = p.ink2) }
                            Text("Ma photo", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Où voir ton fond", Modifier.padding(start = 4.dp, bottom = 2.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                    PRadioRow("Partout", "Accueil, pages, derrière les feuilles", true, "🏞️")
                    PRadioRow("Accueil seulement", "Le haut de l’accueil, comme en 8.0", false, "🏠")
                    PRadioRow("Nulle part", "Deux formes colorées immobiles", false, "⚪")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Présence du fond", Modifier.weight(1f).padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                        Text("Équilibrée", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                    }
                    PSlider(.5f, steps = 2)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) { Text("Discrète", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3); Text("Intense", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3) }
                }
                SetGroup {
                    SetToggle("Fond animé", "Figé en économie d’énergie", "play", p.type("period").c, true, {})
                    SetToggle("Fond à la météo", "Pluie ou neige sur ton fond", "cloud", p.type("objective").c, false, {})
                }
            }
        }
    }
}

/** Même page, défilée : barre compacte, image perso avec voile (plus de flou), effets, menu déroulant ouvert. */
@Composable
fun E81Menu() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg("pop", 1, null, heroY = 110.dp, parallax = 60.dp)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                StatusBar()
                CompositionLocalProvider(LocalPal provides tintedPal("pop")) {
                    CompactBar {
                        RoundGlass("Retour", {}) { Pi("chevronleft", size = 20.dp, tint = p.ink) }
                        EmojiTile("🎨", p.type("deadline").c, 34.dp)
                        Text("Apparence", Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass {
                    CardHead("Ma photo", emoji = "🖼️") { PButton("Changer", {}, small = true, icon = "image") }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Aperçu : la photo et son voile dégradé (aucun flou)
                        Box(Modifier.size(84.dp, 120.dp).chunk(p.surface, p.edge, 18.dp, 4.dp, 2.dp).padding(2.dp).clip(RoundedCornerShape(14.dp))) {
                            SceneArt("lagoon")
                            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.bg.copy(alpha = .1f), p.bg.copy(alpha = .45f), p.bg.copy(alpha = .62f)))))
                            Box(Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth().height(22.dp).background(p.surface, RoundedCornerShape(8.dp)))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Voile", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2); Text("Auto", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                            PSlider(.45f, bubble = "45 %")
                            Text("Plus de flou : un voile dégradé garde tes cartes lisibles.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2, lineHeight = 17.sp)
                        }
                    }
                }
                Glass(pad = 0.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        CardHead("Effets du fond", emoji = "✨")
                        PSelect("Lumière de la scène", "Suit l’heure réelle", "🕰️", open = true)
                        Spacer(Modifier.height(218.dp))
                    }
                    SetToggle("Fond qui réagit", "La marée de braise monte avec tes validations", "trend", p.type("limit").c, true, {})
                }
            }
        }
        PMenu(listOf(MenuItem("Suit l’heure réelle", emoji = "🕰️", on = true, sub = "Aube, jour, crépuscule, nuit"), MenuItem("Toujours le jour", emoji = "☀️"), MenuItem("Suit le thème", emoji = "🌗", sub = "Nuit en thème sombre")),
            Modifier.offset(x = 32.dp, y = 486.dp).width(300.dp))
    }
}

/* ------------------------------------------------------------------ Compte : champs, cases, dialogue */

@Composable
fun E81Dialog() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg("pop", 1, null, heroY = 110.dp)
        Column(Modifier.fillMaxSize()) {
            StatusBar()
            PageHero("Compte", "🔐", p.type("routine").c, height = 150.dp)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass {
                    CardHead("Profil public", emoji = "🪪")
                    PTextField("Pseudo", "camille_r", focused = true, help = "✓ Disponible", icon = "user", trailing = "x", counter = "9/20")
                    Spacer(Modifier.height(8.dp))
                    PTextField("E-mail", "camille@exemple", error = "Adresse incomplète : il manque « .fr » ou « .com ».", icon = "send")
                    Spacer(Modifier.height(8.dp))
                    PTextField("Bio", "", placeholder = "Ce qui te motive en ce moment…", lines = 2, counter = "0/160")
                }
                Glass {
                    CardHead("Partage", emoji = "🤝")
                    PCheckRow("Mes séries", "Visibles par mes amis", 1)
                    PCheckRow("Mes objectifs", "2 sur 8 partagés", 2)
                    PCheckRow("Mes stories", null, 0)
                }
            }
        }
        Box(Modifier.fillMaxSize().background(if (p.dark) Color.Black.copy(alpha = .62f) else PLUM.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
            PDialog("👋", "Se déconnecter ?", "Tes objectifs restent enregistrés sur ton compte. Tu les retrouveras en te reconnectant.", "Se déconnecter", "Rester connecté", danger = true)
        }
    }
}

/* ------------------------------------------------------------------ Profil : flamme autour de l'avatar */

@Composable
fun E81Profile() {
    val p = LocalPal.current
    TabShell("pop", "profile", fab = false, content = {
        TabHero("Profil", null, 96.dp) {
            RoundGlass("Partager", {}) { Pi("share", size = 20.dp, tint = p.ink) }
            RoundGlass("Paramètres", {}) { Pi("gear", size = 20.dp, tint = p.ink) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(top = 92.dp).chunk(p.surface, p.edge, 28.dp, 4.dp, 2.dp).padding(start = 16.dp, end = 16.dp, top = 84.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camille", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("@camille · 🌱 en forme", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink2)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Flame(26.dp); Text("Brasier II", Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("12 340 / 14 120", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                    }
                    Spacer(Modifier.height(8.dp))
                    Bar(62.0, height = 14.dp)
                    Text("La couronne grandit à chaque niveau : encore 1 780 🔥 pour Brasier III.", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2, textAlign = TextAlign.Center)
                }
                Box(Modifier.align(Alignment.TopCenter)) { AvatarMedallion(96.dp, 17, .62f) { AvatarFace(ME, 88.dp) } }
            }
            StatGrid(listOf("12" to "jours de série", "48" to "record", "1 204" to "validés"), columns = 3, emojis = listOf("🔥", "🏆", "✅"),
                colors = listOf(p.type("limit").c, p.type("monthly").c, p.type("routine").c))
            SectionPlaque("Mon espace", "🎒")
            SetGroup {
                SetItem("Boutique", null, "gift", p.type("measure").c, onClick = {}) { Text("1 240 🔥", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink2) }
                SetItem("Badges", null, "medal", p.type("monthly").c, onClick = {})
            }
        }
    })
}

/* ------------------------------------------------------------------ planche des nouveaux composants */

@Composable
fun E81Kit() {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        AppBg("pop", 0, null, heroY = 80.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Plaque { Text("Composants 8.1", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            Glass(pad = 14.dp) {
                CardHead("PSlider", emoji = "🎚️")
                PSlider(.62f, bubble = "62 %")
                PSlider(.5f, color = p.type("cap").c, steps = 4)
                PSlider(.3f, enabled = false)
            }
            Glass(pad = 14.dp) {
                CardHead("PRadio · PCheckbox · PSwitch", emoji = "🔘")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    PRadio(false); PRadio(true); PRadio(true, p.type("objective").c); PCheckbox(0); PCheckbox(1); PCheckbox(2)
                    Box(Modifier.size(60.dp, 48.dp), contentAlignment = Alignment.Center) { PSwitch(true, {}) }
                }
            }
            Glass(pad = 14.dp) {
                CardHead("PTextField · PSelect", emoji = "✍️")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { PTextField("Repos", "", placeholder = "Titre") }
                    Box(Modifier.weight(1f)) { PSelect("Menu", "Matin", "☀️") }
                }
            }
            Glass(pad = 14.dp) {
                CardHead("PTabs · chargement · PStepper", emoji = "🧩")
                PTabs(listOf("Feed" to "📰", "Amis" to "👥", "Messages" to "💬"), 2, null)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PSpinner(); PIndeterminate(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                PStepper("250", "ml")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Plaque { Emoji("☀️", 22.dp); Text("Plaque", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp); Count(2) }
                MiniPlaque("Mini-plaque")
            }
        }
    }
}
