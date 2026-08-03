package fr.shikkanime.validator

import fr.shikkanime.validator.exceptions.ObjectNotValidException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Validates Kotlin objects by inspecting their runtime annotations and member property values.
 *
 * Supported constraints are [NotNull], [NotBlank], [NotEmpty], and [RequireAtLeastOneValid].
 * Validation collects every error before throwing an [ObjectNotValidException], allowing callers
 * to receive all detected violations in a single message.
 */
object Validator {
    /**
     * Evaluates the [NotNull] constraint declared on [kProperty].
     *
     * @param kProperty property whose annotations are inspected.
     * @param value current property value.
     * @return the annotation message when [value] is `null`, otherwise `null`.
     */
    private fun notNullValidator(kProperty: KProperty<*>, value: Any?): String? {
        if (!kProperty.hasAnnotation<NotNull>())
            return null

        return kProperty.findAnnotation<NotNull>()!!.message
            .takeIf { value == null }
    }

    /**
     * Evaluates the [NotBlank] constraint declared on [kProperty].
     *
     * Values that do not implement [CharSequence], including `null`, are ignored.
     *
     * @param kProperty property whose annotations are inspected.
     * @param value current property value.
     * @return the annotation message when [value] is blank, otherwise `null`.
     */
    private fun notBlankValidator(kProperty: KProperty<*>, value: Any?): String? {
        if (value !is CharSequence)
            return null
        if (!kProperty.hasAnnotation<NotBlank>())
            return null

        return kProperty.findAnnotation<NotBlank>()!!.message
            .takeIf { value.isBlank() }
    }

    /**
     * Evaluates the [RequireAtLeastOneValid] constraint declared on [kClass].
     *
     * @param kClass class whose annotations are inspected.
     * @param validPropertiesCount number of non-null properties without validation errors.
     * @return the annotation message when no property is valid, otherwise `null`.
     */
    private fun requireAtLeastOneValid(kClass: KClass<*>, validPropertiesCount: Int): String? {
        if (!kClass.hasAnnotation<RequireAtLeastOneValid>())
            return null

        return kClass.findAnnotation<RequireAtLeastOneValid>()!!.message
            .takeIf { validPropertiesCount == 0 }
    }

    /**
     * Evaluates the [NotEmpty] constraint declared on [kProperty].
     *
     * Values that are not collections, including `null`, are ignored.
     *
     * @param kProperty property whose annotations are inspected.
     * @param value current property value.
     * @return the annotation message when [value] is an empty collection, otherwise `null`.
     */
    private fun notEmptyValidator(kProperty: KProperty<*>, value: Any?): String? {
        if (value !is Collection<*>)
            return null
        if (!kProperty.hasAnnotation<NotEmpty>())
            return null

        return kProperty.findAnnotation<NotEmpty>()!!.message
            .takeIf { value.isEmpty() }
    }

    /**
     * Validates every member property of [obj] and its class-level constraints.
     *
     * Property errors are collected and joined with `"; "`. A property counts toward
     * [RequireAtLeastOneValid] when its value is non-null and it produces no validation error.
     *
     * @param obj object to validate through Kotlin reflection.
     * @throws ObjectNotValidException when one or more constraints are violated.
     */
    fun validate(obj: Any) {
        val errors = mutableListOf<String>()
        val kClass = obj::class
        var validPropertiesCount = 0

        for (property in kClass.memberProperties) {
            val value = property.call(obj)
            val propertyErrors = mutableListOf<String>()
            notNullValidator(property, value)?.let(propertyErrors::add)
            notBlankValidator(property, value)?.let(propertyErrors::add)
            notEmptyValidator(property, value)?.let(propertyErrors::add)

            if (value != null && propertyErrors.isEmpty())
                validPropertiesCount++

            errors.addAll(propertyErrors)
        }

        requireAtLeastOneValid(kClass, validPropertiesCount)?.let(errors::add)

        if (errors.isNotEmpty())
            throw ObjectNotValidException(errors.joinToString("; "))
    }
}