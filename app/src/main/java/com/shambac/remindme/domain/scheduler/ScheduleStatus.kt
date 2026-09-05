package com.shambac.remindme.domain.scheduler

sealed interface ScheduleStatus {
    data object Armed : ScheduleStatus
    data class Problem(val message: String, val recoveryAction: String) : ScheduleStatus
}
