package fr.shikkanime.exposed

/**
 * Marks an interface or implementation method for execution inside a database transaction.
 *
 * The annotation is interpreted by [TransactionalProxy]. Calls made directly on the target object
 * are not intercepted.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Transactional
