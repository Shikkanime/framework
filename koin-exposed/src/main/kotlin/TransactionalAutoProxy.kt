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

    @Suppress("UNCHECKED_CAST")
    val instances = registry.instances as MutableMap<String, InstanceFactory<Any>>

    @Suppress("UNCHECKED_CAST")
    val replacements = mutableMapOf<String, InstanceFactory<Any>>()
    val removedKeys = mutableListOf<String>()

    for ((key, factory) in instances) {
        val definition = factory.beanDefinition
        val proxyInterface = transactionalInterfaceOf(definition) ?: continue
        replacements[proxyIndexKey(definition, proxyInterface)] =
            ProxyFactory(factory, proxyInterface) as InstanceFactory<Any>
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

    if (annotated.size > 1)
        error("Class ${definition.primaryType.qualifiedName} implements several @Transactional interfaces: $annotated")

    return annotated.firstOrNull()?.kotlin
}

/**
 * Computes the registry key the proxy definition must be mapped at.
 *
 * Keys follow [indexKey] format `class:qualifier:scope` with the root scope qualifier, since
 * eligible definitions are root-level singles.
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
        cached?.let { return it as T }

        val result = KoinPlatformTools.synchronized(this) {
            val existing = cached

            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                return@synchronized existing as T
            }

            val target = original.get(context) as T
            val proxy = TransactionalProxy(target, proxyInterface.java as Class<Any>).create()

            cached = proxy
            proxy as T
        }

        return result
    }

    override fun isCreated(context: ResolutionContext?): Boolean =
        cached != null

    override fun drop(scope: Scope?) {
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
@Suppress("UNCHECKED_CAST")
private fun proxyBeanDefinition(original: BeanDefinition<*>, proxyInterface: KClass<*>): BeanDefinition<*> {
    val kind = Kind.Singleton
    val scopeQualifier = original.scopeQualifier
    val qualifier: Qualifier = original.qualifier ?: StringQualifier("transactional-proxy")
    val definition: (Scope, ParametersHolder) -> Any =
        { _, _ -> error("Proxy instance is produced by ProxyFactory") }

    return BeanDefinition(
        scopeQualifier,
        proxyInterface,
        qualifier,
        definition,
        kind,
        emptyList<KClass<*>>()
    )
}
