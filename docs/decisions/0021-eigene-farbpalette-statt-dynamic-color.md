# 0021 – Eigene Farbpalette statt Dynamic Color

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Beim Prüfen von Phase 8b auf dem Gerät fiel auf, dass der „Anlegen"-Knopf im Dialog wie deaktiviert
aussieht, obwohl er aktiv ist. Das Auslesen der tatsächlichen Pixelfarben aus einem Screenshot hat
es beziffert:

| Zustand | Kontrast zum Dialoghintergrund |
|---|---|
| „Anlegen" **aktiv** | **2,12 : 1** |
| „Anlegen" **deaktiviert** | **1,91 : 1** |
| „Löschen" (Fehlerfarbe) | 4,87 : 1 |
| Menüeinträge | 11,07 : 1 |

Zwischen aktiv und inaktiv lagen 0,2. Das Auge liest Helligkeit stärker als Farbton, deshalb wirkte
der Knopf abgeschaltet. WCAG AA verlangt 4,5 : 1 — beide Zustände lagen darunter, und ausgerechnet
der destruktive Knopf war der einzige gut lesbare.

**Die Ursache ist strukturell.** Material 3 sichert Kontrast nur zwischen *Paaren* zu — `primary`
mit `onPrimary`, `surface` mit `onSurface`. Ein `TextButton` setzt aber `primary` als Schrift auf
`surface`, und dieses Paar ist im Token-System nicht abgesichert. In den mitgelieferten Paletten
geht das gut, weil deren `primary` im dunklen Schema hell ist. Mit Dynamic Color bestimmt das der
Gerätehersteller: Auf dem Testgerät (Samsung, One UI) lieferte die Palette `#4978F5` — ein *dunkles*
Primary in einem dunklen Schema.

Damit kontrollierte weder die App noch ihre Entwickler diese Farbe, sondern das Hintergrundbild des
jeweiligen Handys. Auf dem zweiten Gerät hätte derselbe Dialog anders ausgesehen — besser oder
schlechter, aber in keinem Fall vorhersagbar oder prüfbar.

## Entscheidung

Dynamic Color entfällt. `BrownieDoTheme` verwendet zwei vollständig ausdefinierte Schemata.

**Vollständig** ist dabei wörtlich gemeint: Bisher setzte `darkColorScheme(primary, secondary,
tertiary)` nur drei Rollen, alle übrigen rund vierzig blieben Materials Standard-Lila — darunter
`surfaceContainer`, `onSurfaceVariant` und die gesamte Fehlerfamilie, die die App tatsächlich liest.

Die Töne stammen aus dem App-Icon:

| Seed | Herkunft | Rolle |
|---|---|---|
| `#4CAF50` | der grüne Haken | `primary` |
| `#A9744B` | das Häufchen | `secondary`, und entsättigt die Neutraltöne |
| `#F7C9D9` | der Hintergrund | `tertiary` |

**`primary` wurde das Grün, nicht das Braun.** Zwei Gründe: Eine abgehakte Aufgabe bekommt damit
eine grüne Checkbox — dieselbe Geste, dieselbe Farbe wie im Icon. Und Grün liegt maximal weit von
Rot entfernt, das die destruktiven Aktionen trägt; Bestätigen und Löschen im selben Dialog lassen
sich nie verwechseln. Braun hätte bei Farbton 26° gelegen, Rot bei 3° — im dunklen Schema ein helles
Beige neben einem hellen Lachsrosa.

Die Tonleitern sind gerechnet, nicht gegriffen: Der Material-3-„Ton" ist definitionsgemäß die
CIELAB-Helligkeit L\*, also entsteht jeder Wert, indem Farbton und Chroma festgehalten und L\* auf
den Zielton gesetzt wird. Rot bleibt bei den Standardwerten von Material — erprobt, und der Abstand
zum Grün ist mit ΔE 75 (dunkel) und 100 (hell) reichlich.

**Der Kontrast ist ab jetzt eine Testbedingung, keine Beobachtung.** `ColorSchemeContrastTest`
rechnet acht Paare in beiden Schemata nach und verlangt 4,5 : 1, dazu einen Mindestabstand zwischen
`primary` und `error`. Der Test wurde gegengeprüft: Setzt man `primary` auf das gemessene `#4978F5`,
schlägt er fehl.

## Konsequenzen

- **Die App passt sich nicht mehr dem Hintergrundbild an.** Dafür sehen beide Handys gleich aus, und
  wie die App aussieht, entscheidet das Repository statt des Geräts.
- Der Kontrast im Dialog steigt von 2,12 : 1 auf **8,44 : 1**. Der Abstand zum deaktivierten Zustand
  ist damit unübersehbar — genau das war die Beschwerde.
- Die vier Compose-Vorschauen zeigen erstmals die echten Farben. Bisher zeigten sie das Baseline-Lila,
  weil sie `dynamicColor = false` setzten und die Palette dahinter nie ersetzt wurde. Wir haben also
  lange Vorschauen betrachtet, die mit dem Gerät nichts zu tun hatten.
- Wer Farbwerte ändert, muss den Test grün bekommen. Das ist Absicht: Die Zahlen sind das Ergebnis
  einer Messung, nicht eine Geschmacksfrage.
- Der Parameter `dynamicColor` ist **entfernt**, nicht auf `false` gesetzt — sonst bliebe toter Code.
  Drei Aufrufstellen verlieren dadurch ihr Argument.
- Die Schemata sind `internal` statt `private`, damit der Test sie ohne laufende Oberfläche lesen
  kann.

## Alternativen

- **Nur den Bestätigungsknopf auf `Button` umstellen:** Ein gefüllter Knopf nutzt `primary` als
  Fläche mit `onPrimary` als Schrift — ein garantiertes Paar, das Problem wäre dort gelöst. „Abbrechen"
  bliebe aber bei 2,12 : 1, ebenso jeder andere `TextButton`. Ein halber Fix, der die Ursache stehen
  lässt.
- **Dynamic Color behalten und die Kontraste nachbessern:** Nicht möglich. Die Farbe kommt vom Gerät;
  man könnte sie nur zur Laufzeit prüfen und im Zweifel verwerfen — also doch eine eigene Palette,
  nur mit mehr Apparat.
- **Bei den Vorlagenfarben `Purple40`/`Purple80` bleiben:** Die hat nie jemand gewählt, sie stammen
  aus dem Android-Studio-Assistenten und haben mit der App nichts zu tun.
- **Braun als `primary`**, näher am Namen „BrownieDo": Verworfen wegen der Nähe zu Rot, siehe oben.
  Braun bleibt als `secondary` erhalten.
