---
applyTo: "**"
---

# Android Architecture Rules

Keep dependencies and responsibilities explicit so screens stay testable and maintainable.

## Layer boundaries (MUST FIX):

- Enforce one-way dependency direction: `ui -> domain -> data`.
- UI layer must not call database/network clients directly.
- Domain logic should not depend on Android framework types when avoidable.

## UI architecture (MUST FIX):

- ViewModels own screen state and user-intent handling.
- Composables render state and emit events; they should not host business rules.
- Keep state immutable and update it through explicit reducer-like transitions.

## Data architecture (SHOULD FIX):

- Repositories are the single entry point for data sources used by a feature.
- Isolate mapping between network/database/domain models to avoid cross-layer leakage.

## Error handling (SHOULD FIX):

- Surface user-meaningful errors in UI state instead of swallowing exceptions.
- Keep retry/fallback behavior explicit in repository or use-case logic.
