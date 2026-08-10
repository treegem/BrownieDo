# 0015 – `AGENTS.md` als gemeinsamer Agenten-Einstieg

**Status:** akzeptiert · **Datum:** 2026-08-10

## Kontext

BrownieDo wurde bis dahin mit GitHub Copilot in Android Studio gebaut. Mit Claude Code kam ein
zweites Werkzeug dazu, und damit die Frage, wo der Projekt-Einstieg lebt — also Sprache, Build- und
Testbefehle, Secrets und die Erwartungen an eine Änderung.

Die drei Werkzeuge laden unterschiedliche Dateien:

| Werkzeug | Liest |
|---|---|
| Copilot in Android Studio | `.github/copilot-instructions.md` und `.github/instructions/` |
| Copilot CLI und Coding Agent | `AGENTS.md` |
| Claude Code | `CLAUDE.md`, inklusive der darin aufgelösten `@`-Importe |

Der Einstieg stand komplett in `CLAUDE.md`. Copilot sah ihn damit nie — weder in der IDE noch auf
der CLI. Der Inhalt ist aber an keiner Stelle Claude-spezifisch; nur der Lademechanismus ist es.

Dazu kam ein zweites Problem: `CLAUDE.md` bat den Agenten, zu Beginn jeder Aufgabe selbst alle
Dateien in `.github/instructions/` zu lesen, weil Claude Code die `applyTo`-Globs nicht kennt. Das
ist ein Höflichkeitsvertrag — er kostet jede Sitzung Werkzeugaufrufe und wird bei kurzen Aufgaben
plausibel übersprungen.

## Entscheidung

`AGENTS.md` im Repo-Root wird der gemeinsame, werkzeugneutrale Einstieg und trägt alles, was für
jedes Werkzeug gilt.

`CLAUDE.md` enthält keine Inhalte mehr, sondern nur noch Importe: `@./AGENTS.md`, je eine Zeile pro
Regeldatei, dazu `ROADMAP.md` und die ADR-Übersicht. Claude Code löst diese Importe beim
Sitzungsstart auf — aus der Bitte wird eine Garantie.

`.github/copilot-instructions.md` verweist mit einem Satz auf `AGENTS.md`, weil das JetBrains-Plugin
`AGENTS.md` nicht von sich aus liest.

Die einzelnen ADRs werden bewusst **nicht** importiert. Die Übersichtstabelle in
`docs/decisions/README.md` reicht, damit ein Agent weiß, dass es zu einer Frage einen ADR gibt, und
ihn gezielt öffnet.

## Konsequenzen

- Der Einstieg wird an genau einer Stelle gepflegt, und alle drei Werkzeuge sehen denselben.
- Ein neues Werkzeug braucht künftig nur einen Zeiger auf `AGENTS.md`, keinen weiteren Textblock.
- `CLAUDE.md` ist keine Datei mehr, in die man etwas schreibt. Wächst eine Regel, wächst sie in
  ihrer eigenen Datei; hier kommt höchstens eine Importzeile dazu.
- Dauerhaft im Kontext stehen rund 520 Zeilen (Einstieg, Regeln, ROADMAP, ADR-Übersicht). Für ein
  Projekt dieser Größe unkritisch, aber es ist die Obergrenze, die man im Blick behält.
- Neuere Claude-Code-Versionen laden `AGENTS.md` teils zusätzlich von sich aus. Dann steht der
  Inhalt doppelt im Kontext — harmlos, aber beim Prüfen mit `/context` nicht erschrecken.

## Alternativen

- **Alles in `CLAUDE.md` lassen und Copilot ignorieren:** Der bisherige Zustand. Copilot CLI und der
  Coding Agent sähen die Sprachregel, die Build-Befehle und die Secret-Vorgaben nie — ausgerechnet
  die Regeln, deren Verletzung am teuersten ist.
- **Die Regeldateien nach `docs/rules/` verschieben, damit die Importpfade nicht in einem
  versteckten Verzeichnis liegen:** Zerstört Copilots automatisches Laden über
  `.github/instructions/`, um einen Importpfad zu retten, der auch so funktioniert. Falscher Tausch.
- **Den Einstieg in jede Werkzeugdatei kopieren:** Lädt überall zuverlässig, verstößt aber gegen die
  Vorgabe, jede Entscheidung genau einmal zu beschreiben. Drei Kopien driften auseinander, und man
  merkt es erst, wenn zwei Werkzeuge Unterschiedliches behaupten.
