package org.stoto.scheduler.domain

import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Worker

/**
 * Összesítő statisztika egy beosztáshoz: dolgozónként a műszakok és hétvégi műszakok száma.
 * Tiszta, állapotmentes számítás a UI összesítő nézetéhez.
 */
object ScheduleStatistics {

    /** Egy dolgozó havi terhelése. */
    data class WorkerLoad(
        val workerId: String,
        val workerName: String,
        val totalShifts: Int,
        val weekendShifts: Int,
        val maxShifts: Int
    ) {
        /** Igaz, ha a dolgozó túllépte a havi maximumát. */
        val isOverloaded: Boolean get() = totalShifts > maxShifts
    }

    /**
     * Minden dolgozóra kiszámolja a terhelést (a be nem osztott dolgozók is szerepelnek 0-val),
     * név szerint rendezve.
     */
    fun workerLoads(schedule: Schedule, workers: List<Worker>): List<WorkerLoad> {
        val shiftsByWorker = schedule.shifts
            .filter { it.assignedWorkerId != null }
            .groupBy { it.assignedWorkerId }

        return workers
            .map { worker ->
                val shifts = shiftsByWorker[worker.id].orEmpty()
                WorkerLoad(
                    workerId = worker.id,
                    workerName = worker.name,
                    totalShifts = shifts.size,
                    weekendShifts = shifts.count { it.isWeekend },
                    maxShifts = worker.maxShifts
                )
            }
            .sortedBy { it.workerName.lowercase() }
    }

    /** A be nem osztott (üres) napok száma. */
    fun unassignedDays(schedule: Schedule): Int =
        schedule.shifts.count { it.assignedWorkerId == null }
}
