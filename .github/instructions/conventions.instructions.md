---
applyTo: "**"
---

# Android Conventions

## Dependency injection (MUST FIX):

- Constructor injection only for classes with dependencies.
- Avoid service-locator access from UI classes.

## Forbidden APIs (MUST FIX):

- No `println`, `System.out`, or `System.err` in app code.
- No blocking network or disk I/O on the main thread.
- Avoid `GlobalScope`; prefer lifecycle-aware scopes (`viewModelScope`, `lifecycleScope`) and structured concurrency.
- Prefer `java.time` for date/time handling.

## Data conventions (SHOULD FIX):

- Keep data/domain/UI models separated when responsibilities differ.
- Avoid redundant model copies and one-line mapping wrappers with no value.
- Keep repository contracts focused on app use cases and error states.

## UI and state (SHOULD FIX):

- Use immutable UI state models and unidirectional state updates.
- Keep composables side-effect free except in explicit side-effect APIs (`LaunchedEffect`, `DisposableEffect`, etc.).
- Keep business logic out of composables; place it in ViewModels/use-cases.
