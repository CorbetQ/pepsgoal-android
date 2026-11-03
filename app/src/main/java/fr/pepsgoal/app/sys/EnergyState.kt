package fr.pepsgoal.app.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import fr.pepsgoal.app.ai.LocalAi

/** Event driven thermal guard: no timer, wake lock or polling. */
object EnergyState {
    var thermalStatus by mutableIntStateOf(0); private set
    fun start(ctx: Context) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= 29) {
            fun update(status: Int) {
                thermalStatus = status
                Perf.setThermalStatus(status)
                if (status >= 3) LocalAi.stopGeneration() else if (status >= 2) LocalAi.stopBackgroundGeneration()
            }
            update(pm.currentThermalStatus)
            pm.addThermalStatusListener(ctx.mainExecutor) { update(it) }
        }
        ContextCompat.registerReceiver(ctx, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_LOW || pm.isPowerSaveMode) LocalAi.stopBackgroundGeneration()
            }
        }, IntentFilter().apply { addAction(Intent.ACTION_BATTERY_LOW); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun batteryLow(ctx: Context): Boolean {
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return level >= 0 && scale > 0 && level * 100 / scale <= 15 && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0
    }
}
