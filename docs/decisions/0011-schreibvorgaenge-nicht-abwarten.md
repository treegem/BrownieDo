# 0011 – Schreibvorgänge nicht auf den Server warten lassen

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Firestore liefert für jeden Schreibvorgang einen `Task`. Der übliche Kotlin-Weg ist
`await()` — die Coroutine wartet, bis der Schreibvorgang abgeschlossen ist, und meldet danach
Erfolg oder Fehler.

Bei Firestore bedeutet „abgeschlossen" jedoch **vom Server bestätigt**. Ohne Verbindung wird der
`Task` nicht abgeschlossen, sondern bleibt offen, bis die App wieder online ist — das kann Stunden
dauern. Das Dokument liegt in dieser Zeit bereits im lokalen Cache und der Snapshot-Listener meldet
es sofort.

Ein `await()` beim Hinzufügen einer Aufgabe hätte also genau im wichtigsten Szenario der App
versagt: Wer im Supermarkt ohne Empfang etwas einträgt, sähe den Eintrag zwar in der Liste, die
Oberfläche würde aber dauerhaft „wird gespeichert" anzeigen.

## Entscheidung

Schreibvorgänge werden abgesetzt, aber nicht abgewartet. Das zurückgegebene `Result` sagt nur aus,
ob Firestore den Schreibvorgang **lokal** angenommen hat. Die Zustellung an den Server erledigt
Firestore selbst.

Damit brauchen die Schreibmethoden von `TodoRepository` kein `suspend`, und die UI kennt keinen
Zustand „wird gespeichert".

## Konsequenzen

- Eine Aufgabe erscheint sofort in der Liste — online wie offline. Der Snapshot-Listener ist die
  einzige Quelle für den angezeigten Zustand, nicht die Rückmeldung des Schreibvorgangs.
- **Ein serverseitig abgelehnter Schreibvorgang bleibt unbemerkt.** Verstoßen die Daten gegen die
  Security Rules, verwirft Firestore die Änderung später still und der Eintrag verschwindet wieder
  aus der Liste. Für zwei Nutzer, die beide in `members` stehen, ist das kein realistischer Fall —
  träte er auf, wäre die Ursache ein Fehler in den Rules, kein Nutzerfehler.
- Das gemeldete `Result` deckt nur noch Fehler ab, die Firestore sofort erkennt (etwa ein nicht
  serialisierbares Objekt). Die Fehlermeldung in der UI bleibt trotzdem sinnvoll — sie ist der
  einzige Weg, einen solchen Programmfehler überhaupt zu bemerken.

## Alternativen

- **`await()` verwenden:** Meldet echte Server-Fehler zuverlässig, blockiert die Rückmeldung an den
  Nutzer aber genau dann, wenn keine Verbindung besteht. Unvereinbar mit der Projektvision.
- **Auf den `Task` hören, ohne die UI zu blockieren** (Eintrag sofort zeigen, bei späterem Fehler
  eine Meldung nachreichen): Genauer, verlangt aber einen langlebigen Scope außerhalb des
  ViewModels und eine Oberfläche für nachträgliche Fehler. Zu viel Aufwand für einen Fall, der bei
  korrekten Security Rules nicht eintritt.
