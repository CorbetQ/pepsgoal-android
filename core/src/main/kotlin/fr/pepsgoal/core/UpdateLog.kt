package fr.pepsgoal.core

/**
 * Mise à jour du serveur depuis Modération › Serveur : commande lancée en SSH (root) et lecture du journal de
 * deploy/update.sh (« ::step n/total libellé », avancement de la construction Docker, « ::exit code »).
 * Même format que la version web (apps/server/src/lib/update.ts).
 */
object UpdateLog {
    const val SCRIPT = "/opt/pepsgoal/deploy/update.sh"

    class Progress(val running: Boolean, val done: Boolean, val ok: Boolean, val exit: Int?, val step: Int, val total: Int, val label: String, val pct: Int, val lines: List<String>)

    /**
     * Lance le script détaché (il survit à la coupure SSH et au redémarrage de l'API) puis suit son journal en direct.
     * Si une mise à jour tourne déjà, on suit simplement son journal (« ::busy »).
     */
    fun command(web: Boolean, script: String = SCRIPT): String {
        val d = script.substringBeforeLast('/'); val name = script.substringAfterLast('/')
        val q = { s: String -> "'" + s.replace("'", "'\\''") + "'" }
        return listOf(
            "D=${q(d)}; L=\"\$D/logs/update.log\"; P=\"\$D/logs/update.pid\"; mkdir -p \"\$D/logs\"",
            "if [ -f \"\$P\" ] && kill -0 \"\$(cat \"\$P\")\" 2>/dev/null; then echo \"::busy\"; else",
            ": > \"\$L\"; chmod 644 \"\$L\"",
            "setsid nohup sh -c \"sh \$D/$name${if (web) " --web" else ""}; echo \\\"::exit \\\$?\\\"\" >> \"\$L\" 2>&1 < /dev/null &",
            "echo \$! > \"\$P\"; echo \"::started\"; fi",
            "exec tail -n +1 -F \"\$L\" 2>/dev/null",
        ).joinToString("\n")
    }

    private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val STEP = Regex("^::step (\\d+)/(\\d+) (.*)$")
    private val EXIT = Regex("^::exit (\\d+)")
    private val BUILD = Regex("\\[[\\w-]+(?: [\\w-]+)? (\\d+)/(\\d+)]")

    fun parse(text: String): Progress {
        var step = 0; var total = 0; var label = ""; var sub = 0.0; var exit: Int? = null
        val lines = ArrayList<String>()
        for (raw in text.split('\n')) {
            val l = raw.replace(ANSI, "").replace("\r", "").trimEnd()
            if (l.isEmpty()) continue
            STEP.find(l)?.let { step = it.groupValues[1].toInt(); total = it.groupValues[2].toInt(); label = it.groupValues[3]; sub = 0.0; null } ?: run {
                EXIT.find(l)?.let { exit = it.groupValues[1].toInt() } ?: run {
                    if (l != "::done" && l != "::started" && l != "::busy") {
                        BUILD.find(l)?.let { m -> val n = m.groupValues[2].toInt(); if (n > 0) sub = maxOf(sub, (m.groupValues[1].toInt() - 1).toDouble() / n) }
                        lines += if (l.length > 300) l.take(300) + "…" else l
                    }
                }
            }
        }
        val done = exit != null
        val pct = when {
            done && exit == 0 -> 100
            done -> if (total > 0) Math.round((step - 1) * 100.0 / total).toInt() else 0
            total > 0 -> minOf(99, Math.round((step - 1 + minOf(sub, .95)) * 100.0 / total).toInt())
            else -> 0
        }
        return Progress(!done && step > 0, done, exit == 0, exit, step, total, label, pct.coerceAtLeast(0), lines.takeLast(40))
    }
}
