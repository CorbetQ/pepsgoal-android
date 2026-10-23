package fr.pepsgoal.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** Complication du cadran : la série (et l'avancement du jour en jauge). */
class StreakComplication : SuspendingComplicationDataSourceService() {
    private fun tap() = PendingIntent.getActivity(this, 0, Intent(this, WearActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    private fun icon() = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_flame)).build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = data(request.complicationType, Today.cached(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, Day(true, 12, true, false, 3, 4, emptyList(), 0))

    private fun data(type: ComplicationType, d: Day?): ComplicationData? {
        val n = d?.streak ?: 0
        val desc = PlainComplicationText.Builder("Série de $n jours").build()
        return when (type) {
            // Texte long : la série et ce qu'il reste à valider aujourd'hui
            ComplicationType.LONG_TEXT -> { val left = ((d?.total ?: 0) - (d?.done ?: 0)).coerceAtLeast(0)
                LongTextComplicationData.Builder(PlainComplicationText.Builder(if (d == null) "PepsGoal" else if (left == 0) "$n j · tout est validé" else "$n j · encore $left").build(), desc)
                    .setTitle(PlainComplicationText.Builder("Série 🔥").build()).setMonochromaticImage(icon()).setTapAction(tap()).build() }
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder("$n").build(), desc)
                .setTitle(PlainComplicationText.Builder(if (d?.lit == true) "🔥" else "jours").build()).setMonochromaticImage(icon()).setTapAction(tap()).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder((d?.done ?: 0).toFloat(), 0f, (d?.total ?: 0).coerceAtLeast(1).toFloat(), desc)
                .setText(PlainComplicationText.Builder("$n").build()).setMonochromaticImage(icon()).setTapAction(tap()).build()
            else -> null
        }
    }
}
