package fr.shikkanime.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Provides utilities for timestamps.
 */
@Suppress("unused") // Public utility consumed by downstream projects.
object TimeUtils {
    /**
     * Returns the current date and time used to track row creation and updates.
     *
     * @return the current date and time in UTC.
     */
    @Suppress("unused") // Public utility consumed by downstream projects.
    fun now(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.UTC)
}
