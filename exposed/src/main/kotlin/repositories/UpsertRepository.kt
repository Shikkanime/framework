package fr.shikkanime.exposed.repositories

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

/**
 * Base repository providing the entity upsert helpers shared by every repository shape.
 *
 * Concrete repositories chain these helpers to persist rows: look the entity up, then apply
 * [ifExists] to refresh it, [newIfNotExists] to create it, and [applyFlush] to write it.
 *
 * @param T type of the entity identifier.
 * @param ENTITY type of entity managed by this repository.
 * @property entityClass Exposed DAO class used to create and query entities.
 */
abstract class UpsertRepository<T : Any, ENTITY : Entity<T>>(
    protected val entityClass: EntityClass<T, ENTITY>
) {
    /**
     * Builds the entity reference of [id] for this repository table.
     *
     * Use it to assign foreign keys to this repository's entity without querying the database.
     *
     * @param id identifier of the referenced entity.
     * @return the typed entity reference of [id].
     */
    fun getReference(id: T): EntityID<T> =
        EntityID(id, entityClass.table)

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
}

