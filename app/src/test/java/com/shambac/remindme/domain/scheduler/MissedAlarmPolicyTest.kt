package com.shambac.remindme.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class MissedAlarmPolicyTest {
    @Test fun boundariesAreDeterministic() {
        assertEquals(MissedAlarmOutcome.RING_IMMEDIATELY, MissedAlarmPolicy.classify(Duration.ofMinutes(10)))
        assertEquals(MissedAlarmOutcome.MISSED_NOTIFICATION, MissedAlarmPolicy.classify(Duration.ofMinutes(11)))
        assertEquals(MissedAlarmOutcome.MISSED_NOTIFICATION, MissedAlarmPolicy.classify(Duration.ofHours(2)))
        assertEquals(MissedAlarmOutcome.MISSED, MissedAlarmPolicy.classify(Duration.ofHours(2).plusMinutes(1)))
    }
}
