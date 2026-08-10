---
applyTo: "**"
---

# Naming Conventions

Use stable, role-revealing names that communicate Android layer responsibility.

## Common Android suffixes (MUST FIX where applicable):

- `*Activity` — Android UI entry points.
- `*ViewModel` — screen-level state + action handling.
- `*Repository` — data access abstraction.
- `*UseCase` (or `*Interactor`) — focused domain action.
- `*Document` — Firestore documents, i.e. the data-layer representation of a domain model.
- `*UiState` / `*UiEvent` — UI state and one-off event models.
- `*Mapper` — mapping classes/functions when mapping is non-trivial.

## Placement (MUST FIX):

- A class's package should match its role (for example, `ui`, `domain`, `data`, `mapper`).
- Do not leak data-layer models (`Document`, network DTOs) directly into UI state.
