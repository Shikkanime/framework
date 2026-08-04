package fr.shikkanime.exposed

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Creates a JDK dynamic proxy that wraps methods annotated with [Transactional] in a transaction.
 *
 * The proxied type must be an interface implemented by [target]. The annotation may be declared on
 * either the interface method or its implementation. By default, the proxy resolves the
 * [DatabaseWrapper] associated with the current Exposed database. A wrapper can still be provided
 * explicitly.
 *
 * @param T interface exposed by the generated proxy.
 * @param target object receiving proxied method invocations.
 * @param interfaceType interface implemented by [target].
 */
class TransactionalProxy<T : Any> private constructor(
    private val target: T,
    private val interfaceType: Class<T>,
    private val databaseWrapper: DatabaseWrapper
) : InvocationHandler {
    /**
     * Creates a proxy using an explicit database wrapper.
     *
     * @param databaseWrapper database wrapper used to execute transactional calls.
     * @param target object receiving proxied method invocations.
     * @param interfaceType interface implemented by [target].
     */
    constructor(
        databaseWrapper: DatabaseWrapper,
        target: T,
        interfaceType: Class<T>
    ) : this(
        target,
        interfaceType,
        databaseWrapper
    )

    /**
     * Creates a proxy using the wrapper associated with the current Exposed database.
     *
     * [DatabaseWrapper.connect] must be called before creating the proxy.
     *
     * @param target object receiving proxied method invocations.
     * @param interfaceType interface implemented by [target].
     * @throws IllegalStateException when the current database is not managed by [DatabaseWrapper].
     */
    constructor(
        target: T,
        interfaceType: Class<T>
    ) : this(
        target,
        interfaceType,
        DatabaseWrapper.current()
    )

    /**
     * Creates a proxy implementing the configured interface.
     *
     * @return a proxy forwarding calls to the target and intercepting transactional methods.
     */
    @Suppress("UNCHECKED_CAST")
    fun create(): T =
        Proxy.newProxyInstance(
            interfaceType.classLoader,
            arrayOf(interfaceType),
            this
        ) as T

    /**
     * Forwards a method call to the target, opening a transaction when either method declaration is
     * annotated with [Transactional].
     *
     * @param proxy generated proxy receiving the call.
     * @param method interface method being invoked.
     * @param args invocation arguments, or `null` for a method without arguments.
     * @return the value returned by the target method.
     */
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val targetMethod = target.javaClass.getMethod(method.name, *method.parameterTypes)
        val transactional = method.isAnnotationPresent(Transactional::class.java) ||
                targetMethod.isAnnotationPresent(Transactional::class.java)

        if (!transactional)
            return method.invoke(target, *(args ?: emptyArray()))

        return databaseWrapper.inTransaction {
            method.invoke(target, *(args ?: emptyArray()))
        }
    }
}