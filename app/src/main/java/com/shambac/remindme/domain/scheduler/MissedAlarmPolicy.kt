package com.shambac.remindme.domain.scheduler

import java.time.Duration

enum class MissedAlarmOutcome { RING_IMMEDIATELY, MISSED_NOTIFICATION, MISSED }

object MissedAlarmPolicy {
    val immediateWindow: Duration = Duration.ofMinutes(10)
    val notificationWindow: Duration = Duration.ofHours(2)
    fun classify(lateBy: Duration): MissedAlarmOutcome = when {
        lateBy <= immediateWindow -> MissedAlarmOutcome.RING_IMMEDIATELY
        lateBy <= notificationWindow -> MissedAlarmOutcome.MISSED_NOTIFICATION
        else -> MissedAlarmOutcome.MISSED
    }
}
