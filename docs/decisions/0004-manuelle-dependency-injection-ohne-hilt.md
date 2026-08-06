# 0004 – Manuelle Dependency Injection ohne Hilt

**Status:** akzeptiert · **Datum:** 2026-08-05

## Kontext

Die Coding-Regeln schreiben Constructor Injection vor und verbieten Service-Locator-Zugriffe aus
UI-Klassen. Mit dem `AuthRepository` gab es in Phase 2 erstmals eine Abhängigkeit, die ein
ViewModel benötigt — die Frage nach dem Verdrahtungsmechanismus stand also an.

Hilt ist der Android-Standard und nimmt einem bei wachsenden Objektgraphen viel Arbeit ab.
Er bringt aber KSP-Codegen mit, was Build-Zeit und eine zusätzliche Fehlerquelle bedeutet.

Der absehbare Umfang von BrownieDo ist gering: laut Roadmap etwa zwei Repositories
(Auth, Todos), zwei bis drei Screens und keine Feature-Module. Der Objektgraph passt auf
wenige Zeilen.

## Entscheidung

Die Abhängigkeiten werden von Hand verdrahtet: `AppContainer` baut den Graphen auf,
`BrownieDoApplication` hält ihn, und ViewModels erhalten ihre Abhängigkeiten über eine
`ViewModelProvider.Factory`.

Auf Hilt wird verzichtet, solange der Objektgraph überschaubar bleibt.

## Konsequenzen

- Kein KSP, kein Codegen, spürbar schnellere Builds. Der gesamte Objektgraph ist in einer
  einzigen, lesbaren Datei sichtbar.
- Die Regel „Constructor Injection only" ist trotzdem erfüllt: Weder `FirebaseAuthRepository`
  noch `LoginViewModel` beschaffen sich selbst etwas.
- Neue ViewModels müssen manuell in `AppContainer.viewModelFactory` eingetragen werden. Wird das
  vergessen, scheitert es zur Laufzeit statt beim Kompilieren — der wesentliche Nachteil
  gegenüber Hilt.
- Ein späterer Umstieg bleibt jederzeit möglich, weil alle Abhängigkeiten bereits über
  Konstruktoren laufen. Nachzurüsten wären dann nur Annotationen und Module.

## Alternativen

- **Hilt:** Kompilierzeit-Prüfung und Skalierbarkeit, aber Codegen-Overhead für einen Graphen aus
  einer Handvoll Objekten.
- **Koin:** Kein Codegen, dafür Auflösung zur Laufzeit — löst das Vergessens-Problem also nicht
  und fügt trotzdem eine Abhängigkeit hinzu.

## Wiedervorlage

Sobald mehr als etwa fünf ViewModels oder verschachtelte Abhängigkeiten entstehen, sollte Hilt
erneut geprüft werden. Bis dahin wäre es Overhead.
