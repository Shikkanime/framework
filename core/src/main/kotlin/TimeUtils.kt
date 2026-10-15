package fr.shikkanime.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Provides utilities for timestamps.
 */
object TimeUtils {
    /**
     * Returns the current date and time used to track row creation and updates.
     *
     * @return the current date and time in UTC.
     */
    fun now(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.UTC)
}
