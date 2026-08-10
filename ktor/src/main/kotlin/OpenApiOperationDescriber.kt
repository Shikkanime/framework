package fr.shikkanime.ktor

import io.ktor.http.*
import io.ktor.openapi.Operation as OpenApiOperation
import kotlin.reflect.KFunction
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.*

/**
 * Adds annotation-based OpenAPI metadata for a controller function to this operation.
 *
 * Metadata is emitted only when [kFunction] has [Operation]. Parameter schemas
 * come from [QueryParam] and [PathParam], the body schema comes from [RequestBody], and response
 * schemas come from [ApiResponse] or [ApiResponses].
 *
 * @param kFunction controller function whose annotations describe the operation.
 */
internal fun OpenApiOperation.Builder.describeOperation(kFunction: KFunction<*>) {
    val operation = kFunction.findAnnotation<Operation>() ?: return
    summary = operation.summary
    description = operation.description

    operation.tags.forEach(::tag)

    parameters {
        kFunction.parameters.forEach { kParameter ->
            when {
                kParameter.hasAnnotation<QueryParam>() -> {
                    val queryParam = kParameter.findAnnotation<QueryParam>()!!

                    query(queryParam.name) {
                        required = queryParam.required
                        schema = buildSchema(kParameter.type.withNullability(!queryParam.required))
                    }
                }

                kParameter.hasAnnotation<PathParam>() -> {
                    val pathParam = kParameter.findAnnotation<PathParam>()!!

                    path(pathParam.name) {
                        required = true
                        schema = buildSchema(kParameter.type.withNullability(!pathParam.required))
                    }
                }
            }
        }
    }

    if (kFunction.parameters.any { it.hasAnnotation<RequestBody>() }) {
        val kParameter = kFunction.parameters.first { it.hasAnnotation<RequestBody>() }
        val requestBody = kParameter.findAnnotation<RequestBody>()!!

        requestBody {
            required = requestBody.required
            schema = buildSchema(kParameter.type)
        }
    }

    val responsesContainer = kFunction.findAnnotation<ApiResponses>()
    val individualResponses = kFunction.annotations.filterIsInstance<ApiResponse>()
    val allResponses = (responsesContainer?.value ?: emptyArray()) + individualResponses

    responses {
        allResponses.forEach { response ->
            val httpStatusCode = HttpStatusCode.fromValue(response.status)

            httpStatusCode {
                description = response.description

                val kType = if (response.typeArguments.isEmpty()) {
                    response.responseType.starProjectedType
                } else {
                    response.responseType.createType(
                        response.typeArguments.map {
                            KTypeProjection.invariant(it.starProjectedType)
                        }
                    )
                }

                schema = buildSchema(kType)
            }
        }
    }
}
