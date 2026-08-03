package fr.shikkanime.validator

/**
 * Requires at least one property of the annotated class to be valid.
 *
 * [Validator] considers a property valid when its value is not `null` and none of the supported
 * property annotations produces an error. Unannotated non-null properties therefore also count as
 * valid. Validation fails with [message] only when no member property meets these conditions.
 *
 * Apply this annotation to classes representing requests or inputs where several optional
 * properties are accepted but at least one value must be supplied.
 *
 * @property message error message reported when the object has no valid property.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAtLeastOneValid(val message: String = "At least one field must be valid")

/**
 * Requires the annotated property value to be non-null.
 *
 * [Validator] reports [message] when the property's runtime value is `null`. This annotation can be
 * combined with [NotBlank] or [NotEmpty] to reject both `null` and an invalid non-null value.
 *
 * @property message error message reported when the property is `null`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotNull(val message: String = "This field cannot be null")

/**
 * Requires the annotated character sequence to contain at least one non-whitespace character.
 *
 * [Validator] applies this constraint only when the runtime value implements [CharSequence].
 * `null` and values of other types are ignored. Add [NotNull] to the same property when `null`
 * should also be rejected.
 *
 * @property message error message reported when the character sequence is empty or blank.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotBlank(val message: String = "This field cannot be blank")

/**
 * Requires the annotated collection to contain at least one element.
 *
 * [Validator] applies this constraint only when the runtime value is a [Collection]. `null` and
 * values of other types are ignored. Add [NotNull] to the same property when `null` should also be
 * rejected.
 *
 * @property message error message reported when the collection is empty.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotEmpty(val message: String = "This field cannot be empty")