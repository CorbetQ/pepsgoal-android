package fr.pepsgoal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Profil de référence : démarrage, entrée en mode démonstration, défilement d'Aujourd'hui, fiche d'un objectif et
 * retour. Généré par ./gradlew :app:generateBaselineProfile (appareil géré pixel6Api34), fusionné dans
 * app/src/main/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = "fr.pepsgoal.app", includeInStartupProfile = true) {
        pressHome(); startActivityAndWait()
        // Premier lancement : écran de connexion, on entre en démonstration
        val demo = By.text(java.util.regex.Pattern.compile("Mode démonstration|Demo mode"))
        device.wait(Until.hasObject(demo), 5_000)
        device.findObject(demo)?.click()
        device.wait(Until.hasObject(By.res("list")), 5_000)
        val list = device.findObject(By.res("list")) ?: return@collect
        list.setGestureMargin(device.displayWidth / 5)
        repeat(2) { list.fling(Direction.DOWN); device.waitForIdle() }
        list.fling(Direction.UP); device.waitForIdle()
        list.children.getOrNull(2)?.click(); device.waitForIdle()
        device.pressBack(); device.waitForIdle()
    }
}
