# Testing Guide (JUnit 6 & MockK)

This guide outlines the conventions and patterns for writing tests in this framework using **JUnit 6** and **MockK**. All tests must be written in English.

## Core Libraries

- **[JUnit 6](https://junit.org/junit6/docs/current/user-guide/):** The primary testing framework. JUnit 6 provides native Kotlin `suspend` test support, unified versioning, modernized Java 17+ baseline, and enhanced `@Nested` test organization.
- **[MockK](https://mockk.io/):** The framework for mocking dependencies, stubbing behaviors, and verifying invocations.

## JUnit 6 Key Features & Conventions

### 1. Native Coroutines (`suspend` Test Functions)

JUnit 6 supports native Kotlin `suspend` test and lifecycle functions directly (`@Test`, `@BeforeEach`, `@AfterEach`). You do **not** need `runTest` or `runBlocking` wrappers.

```kotlin
@Test
suspend fun `should resolve parameters and execute controller handler asynchronously`() {
    // Given
    val handler = ControllerRequestHandler.create(...)

    // When & Then
    assertDoesNotThrow { handler(routingContext) }
}
```

### 2. Grouping Tests with `@Nested` & `@DisplayName`

Group tests logically by component method or feature within inner classes annotated with `@Nested`. Use `@DisplayName` for human-readable summaries.

```kotlin
class ValidatorTest {
    @Nested
    @DisplayName("tests for NotNull constraint")
    inner class NotNullTests {
        @Test
        fun `should pass validation when field is not null`() {
            // Given / When / Then
        }
    }
}
```

### 3. Parameterized Testing (`@ParameterizedTest`)

Leverage JUnit 6 parameterized testing with `@ValueSource`, `@CsvSource`, `@EnumSource`, or `@MethodSource` to test multiple inputs concisely.

```kotlin
@ParameterizedTest
@ValueSource(strings = ["valid1", "valid2", "valid3"])
fun `should validate multiple non-blank string inputs`(input: String) {
    // Given
    val dto = SampleNotBlankDto(code = input)

    // When & Then
    assertDoesNotThrow { Validator.validate(dto) }
}
```

### 4. Given / When / Then Pattern

Structure every test method with clear section markers:

```kotlin
@Test
fun `should return same logger instance for given class`() {
    // Given
    val clazz = LoggerFactoryTest::class.java

    // When
    val logger1 = LoggerFactory.getLogger(clazz)
    val logger2 = LoggerFactory.getLogger(clazz)

    // Then
    assertSame(logger1, logger2)
}
```

### 5. Descriptive Method Names in Backticks

Test names must be complete, readable sentences in backticks describing the expected outcome (e.g. `` `should throw exception when field is null` ``).

### 6. MockK Extension Integration

Annotate test classes with `@ExtendWith(MockKExtension::class)` when using `@MockK` and `@InjectMockKs`:

```kotlin
@ExtendWith(MockKExtension::class)
class MyComponentTest {
    @MockK private lateinit var dependency: Dependency
    @InjectMockKs private lateinit var component: MyComponent

    // ... tests
}
```
