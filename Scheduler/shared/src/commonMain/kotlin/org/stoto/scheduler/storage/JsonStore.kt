package org.stoto.scheduler.storage

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Worker

/**
 * A dolgozói lista és a beosztások JSON (de)szerializálása.
 *
 * Tiszta, platformfüggetlen logika: csak [String] <-> objektum átalakítás,
 * a tényleges fájlművelet a [FileStorage] felelőssége.
 */
object JsonStore {

    /** Olvasható, elnéző JSON konfiguráció (ismeretlen kulcsok átugrása, alapértékek kiírása). */
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val workerListSerializer = ListSerializer(Worker.serializer())

    fun encodeWorkers(workers: List<Worker>): String =
        json.encodeToString(workerListSerializer, workers)

    fun decodeWorkers(text: String): List<Worker> =
        json.decodeFromString(workerListSerializer, text)

    fun encodeSchedule(schedule: Schedule): String =
        json.encodeToString(Schedule.serializer(), schedule)

    fun decodeSchedule(text: String): Schedule =
        json.decodeFromString(Schedule.serializer(), text)
}
