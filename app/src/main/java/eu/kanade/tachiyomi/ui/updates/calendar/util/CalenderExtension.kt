package eu.kanade.tachiyomi.ui.updates.calendar.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val TOTAL_DAYS_IN_WEEK = 7

internal fun LocalDate.getNext7Dates() =
    List(TOTAL_DAYS_IN_WEEK) { day -> this.plus(day.toLong(), ChronoUnit.DAYS) }
