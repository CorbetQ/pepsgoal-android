package fr.pepsgoal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateLogTest {
    @Test fun followsStepsAndDockerBuild() {
        val p = UpdateLog.parse("::started\n::step 1/4 Récupération du code\nAlready up to date.\n::step 2/4 Reconstruction de l'API\n#9 [server build 3/6] RUN npm install\n")
        assertTrue(p.running); assertEquals(2, p.step); assertEquals("Reconstruction de l'API", p.label)
        assertTrue(p.pct in 26..49); assertTrue("Already up to date." in p.lines)
    }

    @Test fun endsWithSuccessOrFailure() {
        val ok = UpdateLog.parse("::step 4/4 Vérification\n::done\n::exit 0\n")
        assertTrue(ok.done && ok.ok); assertEquals(100, ok.pct)
        val ko = UpdateLog.parse("::step 1/4 Récupération du code\nfatal: x\n::exit 1\n")
        assertTrue(ko.done); assertFalse(ko.ok); assertEquals(1, ko.exit)
    }

    @Test fun commandRunsDetachedThenFollows() {
        val c = UpdateLog.command(true)
        assertTrue("setsid nohup sh -c \"sh \$D/update.sh --web; echo \\\"::exit \\\$?\\\"\"" in c)
        assertTrue(c.endsWith("exec tail -n +1 -F \"\$L\" 2>/dev/null"))
        assertFalse("--web" in UpdateLog.command(false))
    }
}
