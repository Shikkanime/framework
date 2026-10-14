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
abstract class AbstractRepository<T : Any, ENTITY : Entity<T>>(
    entityClass: EntityClass<T, ENTITY>
) : UpsertRepository<T, ENTITY>(entityClass) {
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