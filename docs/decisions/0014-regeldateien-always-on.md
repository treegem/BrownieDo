# 0014 – Regeldateien always-on statt pfad-gebunden

**Status:** akzeptiert · **Datum:** 2026-08-10

## Kontext

Die Regeldateien in `.github/instructions/` tragen im Frontmatter einen `applyTo`-Glob. Die Idee
dahinter: Regeln, die nur für bestimmte Dateien gelten, laden auch nur dort und halten den Kontext
schlank. Zwei Dateien waren so gebaut — `naming.instructions.md` mit `applyTo: "**/*.kt"` und
`testing.instructions.md` mit `applyTo: "**/test/**, **/androidTest/**"`. Die übrigen dreizehn waren
schon immer `applyTo: "**"`.

Beim Einrichten von Claude Code als zweitem Werkzeug kam die Frage auf, ob dieses Laden auf der
Copilot-Seite überhaupt funktioniert. Gemessen wurde per Canary: In jede Regeldatei kam eine Zeile
mit einem eindeutigen Codewort, danach eine Frage in einem frischen Copilot-Chat in Android Studio,
wobei als Beleg nur die Aussage zählte, das Codewort habe *direkt im Kontext gelegen* — die bloße
Frage „welche Instructions sind geladen?" ist wertlos, weil der Chat die Dateien nachliest und sie
dann als geladen meldet.

Das Ergebnis:

- **Always-on (`applyTo: "**"`) lädt zuverlässig.**
- **Pfad-gebunden lädt nie.** `naming.instructions.md` blieb außen vor, obwohl eine `.kt`-Datei im
  Kontext lag, und auch dann, wenn die Datei dem Chat explizit angehängt wurde.

Damit waren die Namenskonventionen und sämtliche Testregeln seit Projektbeginn wirkungslos — zwei
von sechs inhaltlichen Regeldateien. Das erklärt rückwirkend, warum ausgerechnet
`naming.instructions.md` Vorgaben für Room (`*Dao`, `*Entity`, Paket `database`) und für Fragments
enthielt, die es in BrownieDo nie gab: Eine Regel, die nie geladen wird, fällt auch nie jemandem auf.

## Entscheidung

Alle Regeldateien in `.github/instructions/` bekommen `applyTo: "**"`. Neue Regeln werden
grundsätzlich always-on angelegt; der Abschnitt „Scoped rules" in
`.github/copilot-instructions.md` entfällt und wird durch die Warnung ersetzt, keine
pfad-gebundenen Regeln mehr einzuführen.

## Konsequenzen

- Die Namens- und Testregeln wirken erstmals überhaupt. Rechne damit, dass beide Werkzeuge jetzt
  Dinge anmerken, die vorher durchgingen.
- Die Testkonventionen stehen auch beim Bearbeiten von Produktivcode im Kontext. Bei rund zwei
  Dutzend Zeilen ist das der Preis, den man dafür zahlt, dass sie überhaupt gelten.
- **Die Regelmenge muss klein bleiben.** Ohne Pfad-Bindung wächst jede neue Regel den Kontext jeder
  Datei mit. Wird `.github/instructions/` einmal deutlich umfangreicher, ist das ein neuer ADR wert —
  aber dann mit einem Mechanismus, der in Android Studio nachweislich funktioniert.
- Die Messmethode gehört zum Ergebnis: Wer das Verhalten später erneut prüft, muss wieder mit
  Canaries arbeiten, nicht mit der Selbstauskunft des Chats.

## Alternativen

- **Pfad-Bindung beibehalten:** In der eingesetzten IDE schlicht wirkungslos. Die Regeln blieben
  formal vorhanden und faktisch tot — der schlechteste aller Zustände, weil er Wirkung vortäuscht.
- **Regeln in `.github/copilot-instructions.md` hineinkopieren:** Lädt zuverlässig, dupliziert aber
  die Quelle der Wahrheit und verstößt gegen die Vorgabe, jede Entscheidung genau einmal zu
  beschreiben. Die beiden Kopien driften garantiert auseinander.
- **Von Android Studio auf VS Code wechseln, wo `applyTo` ausgewertet wird:** Löst das Problem am
  falschen Ende. Android Studio ist laut `ROADMAP.md` die IDE für dieses Projekt, samt Emulator,
  Gerätebrücke und Compose-Vorschau; die Regelmenge ist zu klein, um dafür das Werkzeug zu wechseln.
