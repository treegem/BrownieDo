# 0001 – Entscheidungen als ADR festhalten

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Die technischen Entscheidungen aus Phase 1 und 2 wurden im Dialog getroffen und danach direkt
umgesetzt. Im Repository landete jeweils nur das Ergebnis: Dependencies, Klassen, abgehakte
Checklistenpunkte. Die verworfenen Alternativen und die Abwägungen dahinter existierten nur im
Gesprächsverlauf — und damit außerhalb des Projekts.

Das ist ein reales Problem für eine App, an der unregelmäßig weitergearbeitet wird. Ohne
festgehaltene Begründung wird eine Entscheidung später entweder blind übernommen oder ohne
Kenntnis ihrer Gründe rückgängig gemacht.

Als Alternative stand das **Open Knowledge Format (OKF v0.2)** zur Diskussion: ein Markdown-Format
für LLM-gepflegte, Obsidian-kompatible Wissensdatenbanken mit kanonischem Frontmatter und
verlinkten Quellseiten.

## Entscheidung

Technische Entscheidungen werden als ADR unter `docs/decisions/` festgehalten — schlichte
Markdown-Dateien mit Status, Kontext, Entscheidung, Konsequenzen und Alternativen.

Gegen OKF sprach, dass es eine andere Problemklasse löst: das Verdichten vieler Rohquellen zu
einem verlinkten Wiki. BrownieDo braucht dagegen wenige, stabile Begründungstexte. Hinzu kommt,
dass die Spezifikation zum Entscheidungszeitpunkt bei Version 0.2 stand und im Wesentlichen von
einem einzelnen Projekt getragen wurde.

## Konsequenzen

- Jede schwer umkehrbare Entscheidung kostet zusätzlich ein paar Minuten Schreibarbeit.
- ADRs sind werkzeugneutral: reines Markdown, kein Lock-in, in jedem Editor und auf GitHub lesbar.
- `ROADMAP.md` bleibt schlank und verlinkt nur hierher, statt Begründungen zu duplizieren.
- Ein ADR wird nie nachträglich umgeschrieben. Ändert sich die Haltung, entsteht ein neuer ADR,
  der den alten ablöst — so bleibt die Historie nachvollziehbar.

## Alternativen

- **Open Knowledge Format (OKF):** Für Wissensdatenbanken gedacht, nicht für
  Entscheidungsprotokolle. Lohnt eine erneute Prüfung, falls später größere Mengen an
  Recherchematerial anfallen.
- **Abschnitt in `ROADMAP.md`:** Keine zusätzliche Datei nötig, aber die Roadmap würde mit jeder
  Phase weiter anwachsen und ihren Zweck als Fortschrittsübersicht verlieren.
- **Nur Commit-Messages:** Beschreiben eine einzelne Änderung, nicht die dauerhafte Haltung.
  Zum Nachschlagen praktisch unbrauchbar.
