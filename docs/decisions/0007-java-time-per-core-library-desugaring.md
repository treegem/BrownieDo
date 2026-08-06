# 0007 – `java.time` per Core Library Desugaring

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Die Android-Konventionen des Projekts verlangen `java.time` für Datums- und Zeitangaben. Das
`Todo`-Modell braucht zwei Zeitpunkte (`createdAt`, `updatedAt`).

`java.time.Instant` steht nativ aber erst ab API 26 zur Verfügung, während BrownieDo als
Minimum API 24 festlegt. Ohne zusätzliche Maßnahme wäre `Instant` also nicht nutzbar.

## Entscheidung

Core Library Desugaring wird aktiviert (`isCoreLibraryDesugaringEnabled = true` plus die
Abhängigkeit `com.android.tools:desugar_jdk_libs`). Die Domäne verwendet `Instant`.

Die Firestore-Schicht arbeitet weiterhin mit `java.util.Date`, weil das SDK diesen Typ
deserialisiert. Die Umwandlung geschieht ausschließlich in `TodoMapper`.

## Konsequenzen

- `Instant` und der Rest von `java.time` sind bis hinunter zu API 24 verfügbar — auch für
  spätere Funktionen wie Fälligkeitsdaten.
- Der Build wächst um wenige hundert Kilobyte und um einen zusätzlichen Verarbeitungsschritt.
  Bei einer per Sideload verteilten App ohne Größenbeschränkung ist das irrelevant.
- `java.util.Date` bleibt auf die Data-Schicht beschränkt und leakt nicht in Domäne oder UI.

## Alternativen

- **`Long` als Epoch-Millis in der Domäne:** Keinerlei Konfiguration nötig, aber ein Zeitpunkt
  wäre nicht mehr von einer beliebigen Zahl zu unterscheiden. Widerspricht der Konvention
  „Prefer `java.time`".
- **minSdk auf 26 anheben:** Würde das Problem ebenfalls lösen, schränkt aber ohne Not die
  Gerätebasis ein.
- **ThreeTenABP:** Vor dem Desugaring die übliche Lösung, heute überholt.
