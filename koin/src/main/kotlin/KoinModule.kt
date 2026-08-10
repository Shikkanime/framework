package fr.shikkanime.koin

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Base Koin module of the framework.
 *
 * Provides a single place to register framework-wide definitions. Consumer services can compose it
 * with their own modules when starting Koin.
 */
fun frameworkModule(): Module =
    module { }
