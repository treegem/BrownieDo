# 0008 – Compose-BOM in zwei Konfigurationen deklarieren

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Die Compose-BOM (`androidx.compose:compose-bom`) steht in `app/build.gradle.kts` zweimal: einmal
unter `implementation` und einmal unter `androidTestImplementation`. Android Studio meldet dazu
„Dependency is declared multiple times".

Die zugehörige Inspektion heißt `AvoidDuplicateDependencies` und beschreibt ihr Verhalten selbst so:

> `api("com.google.guava:guava")` — will also report this line since it's available through
> another configuration

Für `api` stimmt das, denn diese Konfiguration erbt von `implementation`. Auf
`androidTestImplementation` trifft es nicht zu — sie ist eigenständig und erbt nichts.
Die Inspektion berücksichtigt die Vererbungsrichtung nicht und meldet deshalb einen Fall,
der keiner ist.

Nachweis über `gradlew :app:dependencies --configuration debugAndroidTestCompileClasspath`:

```
+--- androidx.compose:compose-bom:2026.06.01
+--- androidx.compose.ui:ui-test-junit4 -> 1.11.4
```

Der Pfeil zeigt, dass die Version aus der BOM aufgelöst wird. Ohne die zweite Deklaration bliebe
`ui-test-junit4` ohne Version und der androidTest-Klassenpfad ließe sich nicht auflösen.

## Entscheidung

Beide Deklarationen bleiben bestehen. Die BOM wird einmal an ein `val composeBom` gebunden und
zweimal verwendet — die Form aus Googles Compose-Dokumentation.

Die Warnung wird gezielt an der betroffenen Zeile mit
`@Suppress("AvoidDuplicateDependencies")` unterdrückt, zusammen mit einem Kommentar, der den
Grund nennt. Das Kotlin-DSL braucht die Annotation — das aus Java bekannte `//noinspection`
wirkt hier nicht.

## Konsequenzen

- Der androidTest-Klassenpfad bleibt funktionsfähig.
- Die Datei ist wieder warnungsfrei, sodass künftige echte Warnungen auffallen.
- Unterdrückung und Begründung stehen direkt an der Zeile. Wer hier aufräumen will, sieht sofort,
  warum die scheinbare Doppelung Absicht ist.
- Sollte die Inspektion später Konfigurationsvererbung berücksichtigen, kann die
  `@Suppress`-Zeile ersatzlos entfallen.

## Alternativen

- **Zweite Deklaration löschen:** Bricht den androidTest-Build. Genau der Fehler, den dieser ADR
  verhindern soll.
- **Feste Version für `ui-test-junit4` in `libs.versions.toml`:** Machte die BOM dort entbehrlich,
  schüfe aber eine zweite Versionsquelle, die mit der BOM synchron gehalten werden müsste —
  Verstoß gegen „Reuse shared constants".
- **Inspektion projektweit abschalten:** Die Einstellung läge unter `/.idea`, das in `.gitignore`
  steht — sie würde also nicht geteilt. Zudem wäre die Inspektion dann auch für echte Duplikate
  blind.
- **Warnung stehen lassen:** Funktional unbedenklich, gewöhnt aber daran, Warnungen zu ignorieren.
