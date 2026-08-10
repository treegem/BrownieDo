# Coding Rules

The project onboarding — language policy, build and test commands, secrets, and what is expected of
a change — lives in [`AGENTS.md`](../AGENTS.md). Read it before starting.

The full set of rules for this repository lives in `.github/instructions/`.

## How these rules are loaded

- **Every rule file is always-on** (`applyTo: "**"`) and applies to every file in the repository.
- **Do not add path-scoped rules.** Measured in Android Studio: the JetBrains Copilot plugin does
  not evaluate `applyTo` globs — a scoped rule file never loads, not even when a matching file is
  explicitly attached to the chat. `naming` and `testing` were scoped this way and were silently
  inactive. See [ADR 0014](../docs/decisions/0014-regeldateien-always-on.md).
- `.github/instructions/` is the single source of truth for repository coding rules.

## Rules

- `.github/instructions/standards.instructions.md`
- `.github/instructions/architecture.instructions.md`
- `.github/instructions/conventions.instructions.md`
- `.github/instructions/naming.instructions.md`
- `.github/instructions/testing.instructions.md`
- `.github/instructions/roadmap.instructions.md`
- `.github/instructions/avoid-duplicate-definitions.instructions.md`
- `.github/instructions/avoid-unnecessary-wrappers.instructions.md`
- `.github/instructions/consistent-domain-terminology.instructions.md`
- `.github/instructions/mark-nullable-fields.instructions.md`
- `.github/instructions/prefer-static-imports.instructions.md`
- `.github/instructions/remove-redundant-conditionals.instructions.md`
- `.github/instructions/remove-unused-code.instructions.md`
- `.github/instructions/reuse-shared-constants.instructions.md`
- `.github/instructions/use-descriptive-names.instructions.md`
