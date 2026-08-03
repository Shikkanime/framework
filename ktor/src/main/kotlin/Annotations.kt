package fr.shikkanime.ktor

import kotlin.reflect.KClass

/**
 * Marks a class as a controller discoverable by [ControllerBinder].
 *
 * The controller must be passed as an initialized instance to [ControllerBinder.register].
 *
 * @property path route prefix shared by every mapped method in the controller.
 */
@Target(AnnotationTarget.CLASS)
annotation class RestController(
    val path: String = ""
)

/**
 * Describes a mapped controller method in the generated OpenAPI document.
 *
 * This annotation enables OpenAPI processing for the method. Parameter, request-body, and response
 * annotations on a method without [Operation] are not added to its OpenAPI description.
 *
 * @property summary short operation summary.
 * @property description detailed operation description.
 * @property tags OpenAPI tags associated with the operation.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Operation(
    val summary: String,
    val description: String = "",
    val tags: Array<String> = []
)

/**
 * Groups several [ApiResponse] declarations on one controller method.
 *
 * Responses are included in OpenAPI metadata only when the same method also has [Operation].
 *
 * @property value response declarations associated with the operation.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class ApiResponses(
    val value: Array<ApiResponse>
)

/**
 * Describes one HTTP response in the generated OpenAPI operation.
 *
 * The annotation is repeatable and may also be nested in [ApiResponses]. It affects documentation
 * only and does not alter the response returned at runtime.
 *
 * @property status numeric HTTP status code.
 * @property description human-readable response description.
 * @property responseType response body class used to generate the schema.
 * @property typeArguments generic type arguments used to construct the response schema.
 */
@Target(AnnotationTarget.FUNCTION)
@Repeatable
annotation class ApiResponse(
    val status: Int,
    val description: String,
    val responseType: KClass<*>,
    val typeArguments: Array<KClass<*>> = []
)

/**
 * Maps a controller method to an HTTP GET route.
 *
 * @property path route path relative to the enclosing [RestController.path].
 */
@Target(AnnotationTarget.FUNCTION)
annotation class GetMapping(
    val path: String = ""
)

/**
 * Maps a controller method to an HTTP POST route.
 *
 * @property path route path relative to the enclosing [RestController.path].
 */
@Target(AnnotationTarget.FUNCTION)
annotation class PostMapping(
    val path: String = ""
)

/**
 * Maps a controller method to an HTTP PATCH route.
 *
 * @property path route path relative to the enclosing [RestController.path].
 */
@Target(AnnotationTarget.FUNCTION)
annotation class PatchMapping(
    val path: String = ""
)

/**
 * Resolves a controller parameter from the request query string.
 *
 * String values can be converted to `Int`, `Long`, `Double`, strict `Boolean`, enum, and
 * `kotlin.uuid.Uuid` values. When [required] is `false`, an absent value is bound to `null` only if
 * the Kotlin parameter is nullable; otherwise it remains unresolved.
 *
 * @property name query-string parameter name.
 * @property required whether the parameter is required in the generated OpenAPI schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class QueryParam(
    val name: String,
    val required: Boolean = true
)

/**
 * Resolves a controller parameter from a named Ktor route segment.
 *
 * The mapping path must declare the corresponding segment, for example `"/{id}"` for
 * `@PathParam("id")`. Textual values use the same conversions as [QueryParam]. At runtime, a missing
 * value remains unresolved. OpenAPI always marks path parameters as required.
 *
 * @property name route parameter name.
 * @property required controls schema nullability in the generated OpenAPI description.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PathParam(
    val name: String,
    val required: Boolean = true
)

/**
 * Deserializes the request payload into the annotated controller parameter using Ktor `receive`.
 *
 * Runtime deserialization always occurs when the parameter is resolved. [required] affects only the
 * generated OpenAPI request-body description.
 *
 * @property required whether the request body is required in the generated OpenAPI schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestBody(
    val required: Boolean = true
)

/**
 * Validates the resolved, non-null parameter with `fr.shikkanime.validator.Validator`.
 *
 * A validation failure is translated by [ControllerBinder] into an HTTP 400 response containing an
 * error [fr.shikkanime.ktor.dtos.MessageDto]. Null values are not validated.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Valid