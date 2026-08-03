package fr.shikkanime.ktor

/**
 * Marker interface for framework HTTP controllers.
 *
 * [ControllerBinder] currently discovers controllers through [RestController]; implementing this
 * interface communicates intent but is not required for route registration.
 */
interface IController