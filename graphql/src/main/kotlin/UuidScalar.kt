package fr.shikkanime.graphql

import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import java.util.Locale
import kotlin.reflect.KType
import kotlin.uuid.Uuid

/**
 * GraphQL scalar carrying a [Uuid] across the schema.
 *
 * graphql-kotlin maps only a handful of Kotlin types to built-in scalars, so a shared DTO holding
 * a [Uuid] identifier would otherwise fail schema generation and force a duplicated DTO typed on
 * strings. The scalar speaks the canonical hyphenated hexadecimal form in both directions.
 */
val uuidScalar: GraphQLScalarType =
    GraphQLScalarType.newScalar()
        .name("UUID")
        .description("kotlin.uuid.Uuid rendered as its hyphenated hexadecimal string")
        .coercing(UuidCoercing())
        .build()

/**
 * Reads and writes [Uuid] values for [uuidScalar].
 *
 * Every direction funnels through `Uuid.parseOrNull` and `Uuid.toString`, so the schema, the
 * variables and the serialized response all agree on the same textual form.
 */
class UuidCoercing : Coercing<Uuid, String> {
    /**
     * Renders a resolver result as its textual identifier.
     *
     * @param dataFetcherResult value produced by a resolver, expected to be a [Uuid].
     * @param graphQLContext context of the running operation.
     * @param locale locale of the running operation.
     * @return the hyphenated hexadecimal form of [dataFetcherResult].
     * @throws CoercingSerializeException when the result is not a [Uuid].
     */
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): String =
        (dataFetcherResult as? Uuid)?.toString()
            ?: throw CoercingSerializeException("Cannot serialize $dataFetcherResult as a UUID")

    /**
     * Parses a variable value into a [Uuid].
     *
     * @param input raw variable value, expected to be a textual identifier.
     * @param graphQLContext context of the running operation.
     * @param locale locale of the running operation.
     * @return the parsed identifier.
     * @throws CoercingParseValueException when [input] is not a valid identifier.
     */
    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Uuid =
        (input as? String)?.let(Uuid::parseOrNull)
            ?: throw CoercingParseValueException("Cannot parse $input as a UUID")

    /**
     * Parses an inline literal into a [Uuid].
     *
     * @param input literal node from the query document.
     * @param variables coerced variables of the running operation.
     * @param graphQLContext context of the running operation.
     * @param locale locale of the running operation.
     * @return the parsed identifier.
     * @throws CoercingParseLiteralException when [input] is not a valid identifier.
     */
    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): Uuid =
        (input as? StringValue)?.value?.let(Uuid::parseOrNull)
            ?: throw CoercingParseLiteralException("Cannot parse $input as a UUID")

    /**
     * Renders a [Uuid] back into a literal node.
     *
     * @param input identifier to render.
     * @param graphQLContext context of the running operation.
     * @param locale locale of the running operation.
     * @return the identifier as a string literal node.
     */
    override fun valueToLiteral(input: Any, graphQLContext: GraphQLContext, locale: Locale): Value<*> =
        StringValue.of((input as Uuid).toString())
}

/**
 * Schema hooks exposing [uuidScalar] for every [Uuid] the schema meets.
 *
 * Every other hook keeps the framework default ([FlowSubscriptionSchemaGeneratorHooks]): the
 * scalar is a widening of the generator, not a replacement of its behaviour.
 */
class UuidSchemaGeneratorHooks(
    private val delegate: SchemaGeneratorHooks = FlowSubscriptionSchemaGeneratorHooks()
) : SchemaGeneratorHooks by delegate {
    /**
     * Resolves the GraphQL type of a Kotlin type.
     *
     * @param type Kotlin type the generator is building a schema type for.
     * @return [uuidScalar] when [type] is a [Uuid], the delegate answer otherwise.
     */
    override fun willGenerateGraphQLType(type: KType): GraphQLType? =
        if (type.classifier == Uuid::class)
            uuidScalar
        else
            delegate.willGenerateGraphQLType(type)
}
