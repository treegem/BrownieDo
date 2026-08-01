---
applyTo: "**"
---

## Mark Nullable Fields

**Severity: MUST FIX**

Explicitly model nullability in Kotlin types (`?`) for entities, models, DTOs, and UI state when a value can truly be absent. Keep nullability consistent across database, network, domain, and UI layers so required/optional semantics remain correct and safe.
