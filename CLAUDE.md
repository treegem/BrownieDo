# BrownieDo – Kontext für Claude Code

Diese Datei enthält bewusst keine Inhalte, sondern nur Importe. Der Projekt-Einstieg steht in
[`AGENTS.md`](AGENTS.md), die Regeln in [`.github/instructions/`](.github/instructions) — beide
werden von allen Werkzeugen geteilt. Hier steht nur, was Claude Code davon lädt.

**Nichts hierher kopieren.** Wächst eine Regel, wächst sie in ihrer Datei; hier kommt höchstens eine
Importzeile dazu. Warum die Aufteilung so aussieht, steht in
[ADR 0015](docs/decisions/0015-agents-md-als-gemeinsamer-einstieg.md).

## Projekt-Einstieg

@./AGENTS.md

## Verbindliche Regeln

@./.github/instructions/standards.instructions.md
@./.github/instructions/architecture.instructions.md
@./.github/instructions/conventions.instructions.md
@./.github/instructions/naming.instructions.md
@./.github/instructions/testing.instructions.md
@./.github/instructions/roadmap.instructions.md
@./.github/instructions/avoid-duplicate-definitions.instructions.md
@./.github/instructions/avoid-unnecessary-wrappers.instructions.md
@./.github/instructions/consistent-domain-terminology.instructions.md
@./.github/instructions/mark-nullable-fields.instructions.md
@./.github/instructions/prefer-static-imports.instructions.md
@./.github/instructions/remove-redundant-conditionals.instructions.md
@./.github/instructions/remove-unused-code.instructions.md
@./.github/instructions/reuse-shared-constants.instructions.md
@./.github/instructions/use-descriptive-names.instructions.md

## Produktstand und Entscheidungen

@./ROADMAP.md
@./docs/decisions/README.md

Die einzelnen ADRs werden **nicht** importiert — die importierte Übersicht in
`docs/decisions/README.md` sagt, welche es gibt; den passenden bei Bedarf gezielt öffnen.
