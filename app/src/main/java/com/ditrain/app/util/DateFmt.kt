package com.ditrain.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ISO-8601 LocalDate string, e.g. "2026-05-14". Stored as String for serialization simplicity.
 * Use [parseLocalDate] / [LocalDate.iso] to cross the boundary.
 */
typealias LocalDateIso = String

/** ISO-8601 instant string (UTC, "Z"-suffixed), e.g. "2026-05-14T16:42:00Z". */
typealias InstantIso = String

private val LOCAL_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun parseLocalDate(s: LocalDateIso): LocalDate = LocalDate.parse(s, LOCAL_DATE_FMT)
fun LocalDate.iso(): LocalDateIso = format(LOCAL_DATE_FMT)

fun parseInstant(s: InstantIso): Instant = Instant.parse(s)
fun Instant.iso(): InstantIso = toString()
