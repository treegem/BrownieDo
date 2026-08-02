# Coding Rules

The full set of rules for this repository lives in `.github/instructions/`.

Copilot loads each rule automatically based on the `applyTo` glob in its frontmatter.

## How these rules are loaded

- **Always-on rules** (`applyTo: "**"`) apply to every file and are always in context.
- **Scoped rules** load only when you edit a matching file, keeping the context lean.
- `.github/instructions/` is the single source of truth for repository coding rules.

## Always-on rules

- `.github/instructions/standards.instructions.md`
- `.github/instructions/architecture.instructions.md`
- `.github/instructions/conventions.instructions.md`
- `.github/instructions/avoid-duplicate-definitions.instructions.md`
- `.github/instructions/avoid-unnecessary-wrappers.instructions.md`
- `.github/instructions/consistent-domain-terminology.instructions.md`
- `.github/instructions/mark-nullable-fields.instructions.md`
- `.github/instructions/prefer-static-imports.instructions.md`
- `.github/instructions/remove-redundant-conditionals.instructions.md`
- `.github/instructions/remove-unused-code.instructions.md`
- `.github/instructions/reuse-shared-constants.instructions.md`
- `.github/instructions/use-descriptive-names.instructions.md`
- `.github/instructions/naming.instructions.md`
- `.github/instructions/roadmap.instructions.md`

## Scoped rules (loaded by path)

- `.github/instructions/testing.instructions.md` — applies to: `**/test/**`, `**/androidTest/**`
