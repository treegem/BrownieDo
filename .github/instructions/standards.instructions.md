---
applyTo: "**"
---

# Language & Framework Coding Standards (Android)

Enforce these standards fully — you know them, apply them comprehensively.

## Severity mapping:

MUST FIX:
- Kotlin: naming violations, `!!` usage, `var` where `val` suffices, business logic in data classes
- Android: blocking I/O on the main thread, lifecycle-unsafe state handling, direct UI mutation outside composable state patterns
- Compose: mutable state that is not hoisted or remembered correctly, unstable state flows that break recomposition expectations

SHOULD FIX:
- Kotlin: missing named arguments, scope function misuse, expression body opportunities
- Android: resource misuse (hardcoded user-facing strings, duplicated dimensions/colors instead of resources)
- Compose: UI logic that should be moved to ViewModel/use-case layers

CONSIDER:
- Stylistic preferences within the standards that are subjective or context-dependent

## Standards:
- Kotlin: Kotlin Coding Conventions (https://kotlinlang.org/docs/coding-conventions.html)
- Android: Android app quality guidelines and architecture recommendations
- Compose: official Jetpack Compose best practices

Do NOT flag what linters/formatters already catch (whitespace, indentation, import sorting, trailing commas).
Only flag semantic and structural issues that require human judgment.
