---
applyTo: "**"
---

# Follow the Roadmap

`ROADMAP.md` in the repository root is the single source of truth for the product vision,
the agreed technical stack and the implementation progress of BrownieDo.

## Before implementing (MUST FIX):

- Read `ROADMAP.md` before starting feature work and scope the change to its vision and guiding principles.
- Do not build capabilities the roadmap explicitly excludes (mass-market features, multi-user scaling,
  complex permission handling, CRDT-based conflict resolution, phone hardware access).
- If a request contradicts the roadmap, ask the user instead of silently deviating.

## After implementing (MUST FIX):

- Update the matching checklist item in `ROADMAP.md`: `[ ]` open, `[~]` in progress, `[x]` done.
- Add a new checklist item when work is done that the roadmap does not cover yet.

## Keeping it single-sourced (SHOULD FIX):

- `ROADMAP.md` describes the *what* and *why*; coding rules live in `.github/instructions/`.
- Do not restate architecture, naming or convention rules in `ROADMAP.md` — link to the rule files instead.
