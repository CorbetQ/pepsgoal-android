package fr.pepsgoal.core

/** Android thermal status numbers, kept platform independent for policy regression tests. */
object EnergyPolicy {
    fun renderLevel(thermalStatus: Int): Int = when {
        thermalStatus >= 3 -> 3
        thermalStatus >= 2 -> 1
        else -> 0
    }
    fun allowBackgroundAi(visible: Boolean, powerSave: Boolean, batteryLow: Boolean, thermalStatus: Int): Boolean =
        visible && !powerSave && !batteryLow && thermalStatus < 2
    fun allowInteractiveAi(thermalStatus: Int): Boolean = thermalStatus < 3
}
