package com.shambac.remindme.domain.usecase

import com.shambac.remindme.alarm.scheduling.ReminderScheduler
import javax.inject.Inject

class ReconcileAlarmScheduleUseCase @Inject constructor(private val scheduler: ReminderScheduler) {
    suspend operator fun invoke() = scheduler.reconcileAll()
}
