package fr.shikkanime.ktor

/** Built-in UP controller exposing `GET /health` and returning `OK` for consumers of [configureDefaultModules]. */
@RestController("/health")
class HealthController : IController {
    /** Returns `OK` when the service is UP. */
    @Operation(summary = "Health check", description = "Returns OK when the service is UP")
    @ApiResponses([ApiResponse(status = 200, description = "Service is UP", responseType = String::class)])
    @GetMapping
    fun healthCheck(): String =
        "OK"
}
