package com.rewise.domain

import java.util.Calendar

object Scheduler {
    // Standard spaced repetition intervals in days
    private val INTERVALS = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365)

    /**
     * Calculates the next revision date and stage.
     * @param currentStage The current stage index in the intervals list.
     * @return A Pair containing the next revision timestamp (ms) and the next stage index.
     */
//    fun scheduleNext(currentStage: Int): Pair<Long, Int> {
////        val nextStage = if (currentStage < INTERVALS.size - 1) currentStage + 1 else currentStage
////        val daysToAdd = INTERVALS[currentStage] // Use current stage interval for next date
//        // Logic: if I finish stage 0 (1 day interval), next is stage 1.
//        // Wait.
//        // Stage 0: Create topic. Next revision in 1 day.
//        // Stage 1: Revise. Next revision in 3 days.
//        // So `daysToAdd` should come from the *current* stage's interval requirement.
//
//        // Actually, let's map it simpler:
//        // Stage 0 -> Next in 1 day
//        // Stage 1 -> Next in 3 days
//        // Stage 2 -> Next in 7 days
//        // ...
//
////        val days = if (currentStage < INTERVALS.size) INTERVALS[currentStage] else 365
//
////        val calendar = Calendar.getInstance()
//        // Reset to start of day? Or keep time?
//        // For simplicity, let's just add days to current time.
//        // But better is to set to a consistent time or just +24h * days.
////        calendar.add(Calendar.DAY_OF_YEAR, days)
////
////        return Pair(calendar.timeInMillis, currentStage + 1)
//    }

    // changed
    fun scheduleNext(currentStage: Int): Pair<Long, Int> {
        // Determine the number of days to add for the next revision.
        // Use the current stage to find the interval.
        // If the current stage exceeds the list size, use the last available interval.
        val daysToAdd = if (currentStage < INTERVALS.size) {
            INTERVALS[currentStage]
        } else {
            INTERVALS.last() // Safely use the last interval (365 days)
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)

        // Increment the stage for the next revision.
        val nextStage = currentStage + 1

        return Pair(calendar.timeInMillis, nextStage)
    }

    /**
     * Reschedules a missed revision to today/nearest slot.
     * Actually, if it's missed, it just stays in the "Today" list until done.
     * "if revision task not completed than reallocate this rivision to nearest slot"
     * This implies if I miss yesterday's revision, it should be moved to today.
     * In our DB query `nextRevisionDate <= now`, so it automatically appears in "Today/Overdue".
     * So no explicit "reschedule" needed unless we want to update the DB timestamp.
     * Use this if we want to reset the clock.
     */
    fun rescheduleOverdueToNow(): Long {
         return System.currentTimeMillis()
    }
}
