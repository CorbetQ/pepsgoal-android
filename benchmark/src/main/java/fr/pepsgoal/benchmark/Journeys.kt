package fr.pepsgoal.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** Paquet de l'application mesurée (variante « benchmark », sans suffixe). */
const val PACKAGE = "fr.pepsgoal.app"

/**
 * Ouvre l'application sur les données de démonstration, avec le fond « Partout » (`all`) ou « Nulle part » (`off`),
 * puis éventuellement un écran (`stats`, `chat`). Le crochet n'existe que dans la variante « benchmark ».
 */
fun MacrobenchmarkScope.startDemo(bgWhere: String = "all", open: String? = null) {
    startActivityAndWait(Intent(Intent.ACTION_MAIN).apply {
        setPackage(PACKAGE); addCategory(Intent.CATEGORY_LAUNCHER)
        putExtra("bench", bgWhere); open?.let { putExtra("open", it) }
    })
    device.wait(Until.hasObject(By.res(if (open == "chat") "chat_list" else if (open != null) "page_list" else "list")), 5_000)
}

/** Fait défiler une liste (identifiant de test) vers le bas puis vers le haut. */
fun MacrobenchmarkScope.scrollList(res: String, times: Int = 3) {
    val list = device.findObject(By.res(res)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(times) { list.fling(Direction.DOWN); device.waitForIdle() }
    repeat(times) { list.fling(Direction.UP); device.waitForIdle() }
}
