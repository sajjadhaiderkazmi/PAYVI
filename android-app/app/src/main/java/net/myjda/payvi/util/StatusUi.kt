package net.myjda.payvi.util

import android.content.Context
import androidx.core.content.ContextCompat
import net.myjda.payvi.R
import net.myjda.payvi.matching.PayviStatus

/** Maps a [PayviStatus] to the label/color shown in the UI. Effective
 * status prefers whatever this device computed locally (localStatus) over
 * the last value the server acknowledged (payviStatus), since the local
 * value is always at least as fresh. */
object StatusUi {

    fun effectiveStatus(localStatus: String?, serverStatus: String): PayviStatus =
        PayviStatus.fromWireValue(localStatus ?: serverStatus)

    fun label(context: Context, status: PayviStatus): String = when (status) {
        PayviStatus.COMPLETED -> context.getString(R.string.status_completed)
        PayviStatus.NOT_SURE -> context.getString(R.string.status_not_sure)
        PayviStatus.NOT_RECEIVED -> context.getString(R.string.status_not_received)
        PayviStatus.DUPLICATE -> context.getString(R.string.status_duplicate)
        PayviStatus.PENDING -> context.getString(R.string.status_pending)
    }

    fun colorRes(status: PayviStatus): Int = when (status) {
        PayviStatus.COMPLETED -> R.color.status_completed
        PayviStatus.NOT_SURE -> R.color.status_not_sure
        PayviStatus.NOT_RECEIVED -> R.color.status_not_received
        PayviStatus.DUPLICATE -> R.color.status_duplicate
        PayviStatus.PENDING -> R.color.status_pending
    }

    fun color(context: Context, status: PayviStatus): Int =
        ContextCompat.getColor(context, colorRes(status))
}
