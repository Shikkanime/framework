package fr.shikkanime.ktor

import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure
import kotlin.uuid.Uuid

/**
 * Resolves controller function arguments from a Ktor application call.
 */
internal object ControllerArgumentResolver {
    /**
     * Converts a textual HTTP parameter to the type expected by a controller function.
     *
     * Supported conversions are strings, integers, longs, doubles, strict booleans, enum constants,
     * and UUIDs. Unsupported target types retain their original string representation.
     *
     * @param kClass expected runtime class, or `null` when it cannot be determined.
     * @param stringValue raw parameter value.
     * @return the converted value, or `null` when a supported conversion fails.
     */
    private fun parseValue(kClass: KClass<*>?, stringValue: String): Any? =
        when {
            kClass == null -> stringValue
            kClass == String::class -> stringValue
            kClass == Int::class -> stringValue.toIntOrNull()
            kClass == Long::class -> stringValue.toLongOrNull()
            kClass == Double::class -> stringValue.toDoubleOrNull()
            kClass == Boolean::class -> stringValue.toBooleanStrictOrNull()
            kClass.java.isEnum ->
                kClass.java.enumConstants.firstOrNull {
                    (it as Enum<*>).name.equals(stringValue, ignoreCase = true)
                }

            kClass == Uuid::class -> Uuid.parseOrNull(stringValue)
            else -> stringValue
        }

    /**
     * Builds the argument map used to invoke [function] through Kotlin reflection.
     *
     * Query and path values are read from [call], request bodies are deserialized by Ktor, and the
     * controller [instance] is associated with the function's instance parameter.
     *
     * @param call current Ktor application call.
     * @param function controller function being invoked.
     * @param instance controller instance receiving the call.
     * @return parameters and resolved values suitable for `callSuspendBy`.
     */
    suspend fun resolve(
        call: ApplicationCall,
        function: KFunction<*>,
        instance: Any
    ): Map<KParameter, Any?> {
        val args = mutableMapOf<KParameter, Any?>()
        val instanceParameter = function.parameters.firstOrNull { it.kind == KParameter.Kind.INSTANCE }

        if (instanceParameter != null)
            args[instanceParameter] = instance

        function.parameters.forEach { kParameter ->
            if (kParameter.hasAnnotation<QueryParam>()) {
                val queryParamAnnotation = kParameter.findAnnotation<QueryParam>()!!
                val stringValue = call.request.queryParameters[queryParamAnnotation.name]

                if (stringValue != null) {
                    val kClass = kParameter.type.classifier as? KClass<*>
                    args[kParameter] = parseValue(kClass, stringValue)
                } else if (!queryParamAnnotation.required && kParameter.type.isMarkedNullable) {
                    args[kParameter] = null
                }
            }

            if (kParameter.hasAnnotation<PathParam>()) {
                val pathParamAnnotation = kParameter.findAnnotation<PathParam>()!!
                val stringValue = call.parameters[pathParamAnnotation.name]

                if (stringValue != null) {
                    val kClass = kParameter.type.classifier as? KClass<*>
                    args[kParameter] = parseValue(kClass, stringValue)
                }
            }

            if (kParameter.hasAnnotation<RequestBody>()) {
                args[kParameter] = call.receive(kParameter.type.jvmErasure)
            }
        }

        return args
    }
}
