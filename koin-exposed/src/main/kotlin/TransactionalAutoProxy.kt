@file:OptIn(KoinInternalApi::class)

package fr.shikkanime.framework.koin

import fr.shikkanime.exposed.Transactional
import fr.shikkanime.exposed.TransactionalProxy
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Kind
import org.koin.core.definition.indexKey
import org.koin.core.instance.InstanceFactory
import org.koin.core.instance.ResolutionContext
import org.koin.core.parameter.ParametersHolder
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.StringQualifier
import org.koin.core.scope.Scope
import org.koin.mp.KoinPlatformTools
import kotlin.reflect.KClass

/**
 * Replaces eligible single definitions with [TransactionalProxy]-wrapped instances.
 *
 * A definition is eligible when its primary type is a concrete class implementing exactly one
 * interface whose methods carry [Transactional] (declared on the interface or the
 * implementation). The generated proxy keeps resolving its target lazily through the original
 * factory, so dependencies and lifecycle behave as before.
 *
 * This function relies on Koin internal registry APIs; it is tested against Koin 4.2.x and must
 * be revisited on Koin upgrades.
 *
 * @param koin started Koin instance to post-process.
 * @return number of definitions wrapped by a transactional proxy.
 */
fun applyTransactionalProxies(koin: Koin): Int {
    val registry = koin.instanceRegistry

    // The registry getter types the map read-only, but the underlying storage is a mutable
    // LinkedHashMap; the cast is the only way to replace mappings after startKoin.
    @Suppress("UNCHECKED_CAST")
    val instances = registry.instances as MutableMap<String, InstanceFactory<Any>>
    val replacements = mutableMapOf<String, InstanceFactory<Any>>()
    val removedKeys = mutableListOf<String>()

    // Scan first without touching the map, then apply removals and insertions afterwards:
    // removing entries during the entry iteration would throw ConcurrentModificationException
    // as soon as the registry holds more than one definition.
    for ((key, factory) in instances) {
        val definition = factory.beanDefinition
        val proxyInterface = transactionalInterfaceOf(definition) ?: continue

        // The registry map is keyed by InstanceFactory<Any> even though every concrete factory
        // is InstanceFactory<SomeService>; the wildcard erasure forces this single cast here.
        @Suppress("UNCHECKED_CAST")
        val proxyFactory = ProxyFactory(factory, proxyInterface) as InstanceFactory<Any>

        replacements[proxyIndexKey(definition, proxyInterface)] = proxyFactory
        removedKeys.add(key)
    }

    instances.keys.removeAll(removedKeys.toSet())
    instances.putAll(replacements)
    return replacements.size
}

/**
 * Replaces eligible single definitions on the global Koin instance.
 *
 * @return number of definitions wrapped by a transactional proxy.
 * @see applyTransactionalProxies
 */
fun applyTransactionalProxies(): Int =
    applyTransactionalProxies(KoinPlatformTools.defaultContext().get())

/**
 * Resolves the service interface a [TransactionalProxy] must expose for [definition].
 *
 * @param definition candidate bean definition.
 * @return the annotated interface, or null when the definition is not eligible.
 */
private fun transactionalInterfaceOf(definition: BeanDefinition<*>): KClass<*>? {
    if (definition.kind != Kind.Singleton)
        return null

    val interfaces = definition.primaryType.java.interfaces.toList()
    val annotated = interfaces.filter { iface ->
        iface.methods.any { method ->
            method.isAnnotationPresent(Transactional::class.java)
        }
    }

    // A single proxy exposes a single interface; several candidates would make the exposed
    // contract ambiguous, so fail loudly instead of picking one silently.
    if (annotated.size > 1)
        error("Class ${definition.primaryType.qualifiedName} implements several @Transactional interfaces: $annotated")

    return annotated.firstOrNull()?.kotlin
}

/**
 * Computes the registry key the proxy definition must be mapped at.
 *
 * Keys follow [indexKey] format `class:qualifier:scope` with the root scope qualifier, since
 * eligible definitions are root-level singles. The JVM class name is required: Koin builds its
 * keys with [org.koin.ext.getFullName], which uses `java.name` (nested classes keep their `$`),
 * not `qualifiedName`.
 *
 * @param original definition of the replaced implementation.
 * @param proxyInterface interface exposed by the generated proxy.
 * @return the index key of the proxy definition.
 */
private fun proxyIndexKey(original: BeanDefinition<*>, proxyInterface: KClass<*>): String {
    val qualifierValue = original.qualifier?.value ?: ""
    return "${proxyInterface.java.name}:$qualifierValue:_root_"
}

/**
 * Instance factory exposing a [TransactionalProxy] over the instance produced by [original].
 *
 * @param T interface exposed by the generated proxy.
 * @param original factory creating the target implementation.
 * @param proxyInterface interface exposed by the generated proxy.
 */
private class ProxyFactory<T : Any>(
    private val original: InstanceFactory<*>,
    private val proxyInterface: KClass<T>
) : InstanceFactory<T>(
    @Suppress("UNCHECKED_CAST")
    proxyBeanDefinition(original.beanDefinition, proxyInterface) as BeanDefinition<T>
) {
    @Volatile
    private var cached: Any? = null

    @Suppress("UNCHECKED_CAST")
    override fun get(context: ResolutionContext): T {
        // Fast path outside the lock: once the proxy exists, no synchronization is needed.
        cached?.let { return it as T }

        // Double-checked locking mirrors SingleInstanceFactory: Koin resolution is concurrent,
        // and two simultaneous get() calls must not produce two distinct proxies.
        val result = KoinPlatformTools.synchronized(this) {
            val existing = cached

            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                return@synchronized existing as T
            }

            // The factory produces the JDK proxy of the interface; the target cast is safe
            // because eligibility already proved the implementation realizes proxyInterface.
            val target = original.get(context) as T
            val proxy = TransactionalProxy(target, proxyInterface.java as Class<Any>).create()

            cached = proxy
            proxy
        }

        return result as T
    }

    override fun isCreated(context: ResolutionContext?): Boolean =
        cached != null

    override fun drop(scope: Scope?) {
        // Delegate to the original factory so its onClose callbacks fire and the target is
        // released; clearing only the cache would leak the underlying singleton.
        original.drop(scope)
        cached = null
    }

    override fun dropAll() {
        original.dropAll()
        cached = null
    }
}

/**
 * Builds the bean definition the proxy factory is registered under.
 *
 * @param original definition of the replaced implementation.
 * @param proxyInterface interface exposed by the generated proxy.
 * @return a singleton definition exposing [proxyInterface].
 */
private fun proxyBeanDefinition(original: BeanDefinition<*>, proxyInterface: KClass<*>): BeanDefinition<Any> {
    val kind = Kind.Singleton
    val scopeQualifier = original.scopeQualifier

    // Keep the original qualifier when present so qualified lookups still resolve; the fallback
    // only satisfies the non-null BeanDefinition contract for unqualified definitions.
    val qualifier: Qualifier = original.qualifier ?: StringQualifier("transactional-proxy")

    // Never invoked: resolution goes through ProxyFactory.get, which overrides InstanceFactory.get.
    val definition: (Scope, ParametersHolder) -> Any =
        { _, _ -> error("Proxy instance is produced by ProxyFactory") }

    @Suppress("UNCHECKED_CAST")
    val primaryType = proxyInterface as KClass<Any>
    return BeanDefinition(
        scopeQualifier,
        primaryType,
        qualifier,
        definition,
        kind,
        emptyList()
    )
}
