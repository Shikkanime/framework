package fr.shikkanime.core

/**
 * Contract for test builders producing fully-constructed or mocked instances of [T].
 *
 * A builder exposes a configurable state via its own properties and materializes it into a [T]
 * through [build], keeping test data construction centralized and reusable across projects.
 *
 * @param T type of the object produced by the builder.
 */
interface TestBuilder<T> {
    /**
     * Produces the configured instance.
     *
     * @return the built [T].
     */
    fun build(): T
}
