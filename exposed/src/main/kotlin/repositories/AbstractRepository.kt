package fr.shikkanime.exposed.repositories

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.SizedIterable

/**
 * Base repository providing common operations for Exposed DAO entities.
 *
 * @param T type of the entity identifier.
 * @param ENTITY type of entity managed by this repository.
 * @property entityClass Exposed DAO class used to create and query entities.
 */
abstract class AbstractRepository<T : Any, ENTITY : Entity<T>>(protected val entityClass: EntityClass<T, ENTITY>) {
    /**
     * Applies [block] when the nullable entity exists and returns the same entity.
     *
     * @param block operation applied to the existing entity.
     * @return the updated entity, or `null` when the receiver is `null`.
     */
    protected inline fun ENTITY?.ifExists(block: ENTITY.() -> Unit): ENTITY? =
        this?.apply(block)

    /**
     * Returns the existing entity or creates a new one initialized by [block].
     *
     * The initialization block is only executed when the receiver is `null`.
     *
     * @param id optional identifier assigned to the newly created entity.
     * @param block initialization applied when a new entity is created.
     * @return the existing entity or the newly created entity.
     */
    protected fun ENTITY?.newIfNotExists(id: T? = null, block: ENTITY.() -> Unit): ENTITY =
        this ?: entityClass.new(id, block)

    /**
     * Flushes pending entity changes to the database and returns the same entity.
     *
     * @return this entity after it has been flushed.
     */
    protected fun ENTITY.applyFlush(): ENTITY =
        this.apply { flush() }

    /**
     * Finds every entity whose identifier belongs to [ids].
     *
     * @param ids identifiers to search for.
     * @return a lazily evaluated Exposed entity sequence containing matching entities.
     */
    fun findAllByIds(ids: List<T>): SizedIterable<ENTITY> =
        entityClass.find { entityClass.table.id inList ids }

    /**
     * Finds an entity by its identifier.
     *
     * @param id identifier to search for.
     * @return the matching entity, or `null` when it does not exist.
     */
    fun findById(id: T): ENTITY? =
        entityClass.findById(id)
}