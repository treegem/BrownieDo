---
applyTo: "**"
---

# Android Testing Conventions

Keep tests readable, deterministic, and aligned with Android test layers.

## Test structure (MUST FIX):

- Unit tests use `*Test` under `test`.
- Instrumented/UI tests use `*Test` under `androidTest`.
- Name test methods by expected behavior/outcome, not implementation details.

## Scope and isolation (SHOULD FIX):

- Unit tests should isolate business/state logic from Android framework dependencies.
- Use fakes/mocks for repository and data-source boundaries where needed.
- Prefer deterministic coroutine testing patterns over real delays/timers.

## Assertions (CONSIDER):

- Keep each test focused on one behavior with clear assertions.
- Prefer assertion/mocking styles already used in the repository.
