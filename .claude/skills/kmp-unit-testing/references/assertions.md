# Assertions

## Arrow Either

```kotlin
// Check which side
assertTrue(result.isRight())
assertTrue(result.isLeft())

// Extract and assert value
assertEquals(expected, result.getOrNull())       // Right value or null
assertEquals(expected, result.leftOrNull())       // Left value or null

// Assert and extract in one step (fails test if wrong side)
val value = result.getOrElse { fail("Expected Right but got Left: $it") }
val error = result.fold({ it }, { fail("Expected Left but got Right: $it") })

// Assert specific error type
assertEquals(TransactionError.InvalidAmount, result.leftOrNull())
assertIs<TransactionError.InvalidAmount>(result.leftOrNull())

// Chained
result
    .onRight { assertEquals(expected, it) }
    .onLeft { fail("Unexpected error: $it") }
```

## Kotlin Test Assertions (kotlin.test)

Prefer `kotlin.test` over JUnit assertions — it's multiplatform.

```kotlin
import kotlin.test.*

assertTrue(condition)
assertFalse(condition)
assertEquals(expected, actual)
assertNotEquals(unexpected, actual)
assertNull(value)
assertNotNull(value)
assertIs<ExpectedType>(value)           // also asserts type and smart-casts
assertIsNot<UnexpectedType>(value)
assertContains(collection, element)
assertFails { /* block that should throw */ }
assertFailsWith<SpecificException> { /* block */ }
```

## Turbine Assertions (Flows)

```kotlin
flow.test {
    awaitItem()                          // assert next emission
    skipItems(n)                         // skip N emissions
    awaitComplete()                      // assert flow completed
    awaitError()                         // assert flow threw
    expectNoEvents()                     // assert no pending events
    cancelAndIgnoreRemainingEvents()     // cancel without failing on leftovers
    cancelAndConsumeRemainingEvents()    // cancel and return remaining events list
}
```

## Asserting Collections

```kotlin
// Size
assertEquals(3, list.size)
assertTrue(list.isEmpty())
assertTrue(list.isNotEmpty())

// Content
assertContains(list, item)
assertTrue(list.all { it.amount > 0 })
assertTrue(list.none { it.amount < 0 })
assertTrue(list.any { it.type == INCOME })

// Order
assertEquals(listOf(a, b, c), list)    // exact order
assertEquals(setOf(a, b, c), list.toSet()) // any order
```

## Asserting Domain Objects

Prefer `data class` equality over field-by-field assertions:

```kotlin
// GOOD — single assertion, readable failure message
assertEquals(
    buildTransaction(amount = 100.0, description = "Coffee"),
    result.getOrNull(),
)

// OK when only one field matters
assertEquals(100.0, result.getOrNull()?.amount)

// BAD — verbose, harder to maintain
assertEquals(100.0, result.amount)
assertEquals("Coffee", result.description)
assertEquals(1L, result.accountId)
// ...
```

## Test Naming

Use backtick names with the pattern `given X when Y then Z`:

```kotlin
@Test
fun `given valid amount when validated then returns Right`() { }

@Test
fun `given negative amount when validated then returns InvalidAmount`() { }

@Test
fun `given account not found when transfer requested then returns AccountNotFound`() { }

// Shorter forms are fine when context is obvious
@Test
fun `returns empty list when no transactions exist`() { }

@Test
fun `emits loading then data on init`() { }
```

## Asserting No Side Effects on Failure

Always verify that failed operations do not persist partial state:

```kotlin
@Test
fun `given invalid input when save attempted then nothing is persisted`() = runTest {
    val result = useCase(invalidInput)

    assertTrue(result.isLeft())
    assertTrue(fakeRepository.saved.isEmpty())    // no partial writes
    assertTrue(fakeRepository.deleted.isEmpty())  // no deletions
}
```
