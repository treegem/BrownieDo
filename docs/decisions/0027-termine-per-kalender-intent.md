# 0027 – Termine per Kalender-Intent statt Calendar API

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

Aus einer Aufgabe soll sich ohne großen Aufwand ein Termin im Google Kalender machen lassen (Phase 11
der `ROADMAP.md`). Die Termine selbst sollen **nicht** in BrownieDo liegen: Eine Fälligkeit in der App
zieht Erinnerungen nach sich, Erinnerungen ziehen einen eigenen Benachrichtigungskanal nach sich, und
davon gibt es auf den beiden Geräten genug. Der Kalender kann Erinnerungen bereits — BrownieDo muss
ihm nur den Titel überreichen.

Die Zielumgebung ist eng und darf das ausnutzen: zwei Samsung-Galaxy-Phones, beide mit dem Google
Kalender aus dem jeweiligen Gmail-Konto.

Zur Wahl standen drei Wege, einen Termin anzulegen: ein `ACTION_INSERT`-Intent an die Kalender-App,
der direkte Schreibzugriff auf den `CalendarContract`-Provider, und die Google-Calendar-REST-API.

## Entscheidung

**Ein `ACTION_INSERT`-Intent auf `CalendarContract.Events.CONTENT_URI`, mit dem Aufgabentitel als
`Events.TITLE` und ohne alles Weitere.** Die Kalender-App öffnet ihren Anlegen-Bildschirm mit
vorbelegtem Titel; Zeitpunkt, Kalenderkonto, Erinnerung, Wiederholung und Gäste entscheidet der
Nutzer dort.

BrownieDo überreicht damit einen Titel und ist fertig. Es gibt keinen gespeicherten Zustand, keine
Berechtigung, keinen OAuth-Scope, keine neue Abhängigkeit und keine Änderung an `Todo`,
`TodoDocument` oder `firestore.rules`.

Konkret gilt dabei:

- **Kein Datum wird vorbelegt.** Eine Aufgabe trägt keinen Zeitpunkt, also gibt es keinen zu
  übergeben. `EXTRA_EVENT_BEGIN_TIME` mit einer geratenen Zeit (nächste volle Stunde o. ä.) würde
  häufiger korrigiert als übernommen und verdeckt dabei die Voreinstellung des Kalenders.
- **Erst `setPackage("com.google.android.calendar")`, bei `ActivityNotFoundException` erneut ohne
  Paket.** Auf einem Galaxy bedienen zwei Apps diesen Intent, und der Samsung Kalender kann in ein
  lokales Samsung-Konto schreiben, das nie bei Google auftaucht. Der Vorrang für den Google Kalender
  trifft genau die Absicht „Termine in unseren Gmail-Kalendern"; der zweite Versuch verhindert, dass
  die Aktion ins Leere läuft, falls die App einmal fehlt oder deaktiviert ist.
- **Kein `resolveActivity`-Vorabtest, sondern `try`/`catch`.** Seit Android 11 filtert Package
  Visibility die Auflösung: `resolveActivity` liefert ohne `<queries>`-Eintrag im Manifest `null`,
  obwohl die Kalender-App installiert ist. `startActivity` selbst ist davon nicht betroffen. Die
  Ausnahme abzufangen ist damit der kürzere *und* der korrekte Weg — ein Manifest-Eintrag wäre nur
  nötig, um eine Frage zu stellen, deren Antwort wir nicht brauchen.

## Konsequenzen

- **Kein Benachrichtigungskanal in BrownieDo.** Keine `POST_NOTIFICATIONS`-Berechtigung (ab API 33),
  kein WorkManager, kein Firebase Cloud Messaging. Das Erinnern bleibt vollständig beim Kalender, wo
  es schon eingerichtet ist. Genau dafür gibt es diesen ADR.
- **Kein Rückkanal, und das ist Absicht.** Die App weiß nicht, ob eine Aufgabe schon terminiert ist —
  `ACTION_INSERT` gibt keine Event-id zurück, ohne Lesezugriff auf den Provider ließe sich das auch
  nicht nachträglich feststellen. Folge: Wird die Aufgabe gelöscht oder verschoben, bleibt der Termin
  stehen; wird der Termin gelöscht, bleibt die Aufgabe stehen. Zwei Personen, die miteinander reden,
  halten das aus; die Alternative wäre ein zweiter zu synchronisierender Zustand.
- **Der Termin landet im Kalender derjenigen Person, die ihn anlegt.** Der Partner sieht ihn nur,
  wenn er im Kalender als Gast eingeladen wird oder der Kalender ohnehin geteilt ist — das entscheidet
  der Kalender, nicht BrownieDo. Einen Gast vorzubelegen wäre über `Intent.EXTRA_EMAIL` möglich,
  bräuchte aber die E-Mail-Adresse des Partners; `users/{uid}` trägt nach
  [ADR 0020](0020-partner-aus-users-collection.md) nur einen `displayName`. Bewusst nicht gebaut,
  solange nicht klar ist, dass jeder Termin beide betrifft.
- **Das Feature lebt ausschließlich in der UI-Schicht.** `Intent` und `CalendarContract` sind
  Android-Framework; die Vorgabe aus `ROADMAP.md` §5, die Logik-Schicht KMP-fähig zu halten, bleibt
  dadurch unberührt. Das ViewModel wird nicht angefasst — es gibt keinen Zustand und keine Regel,
  die dort hingehörte.
- **Der Bearbeiten-Dialog macht damit fünf Dinge** (Titel, Priorität, Liste, Löschen, Termin). Der
  bereits offene Punkt „`TodoListScreen` und den Bearbeiten-Dialog entzerren" wird damit fällig, statt
  weiter zu warten — siehe die Vorwarnung in
  [ADR 0022](0022-verschieben-im-bearbeiten-dialog.md).
- Der Aufwand ist der kleinste aller bisherigen Phasen: eine Datei in `ui`, ein String, ein Symbol.
  Es gibt nichts zu migrieren und nichts zu veröffentlichen — kein Schritt in der Firebase Console.

## Alternativen

- **Direkt in den `CalendarContract`-Provider schreiben.** Braucht die Laufzeitberechtigung
  `WRITE_CALENDAR` samt Dialog, und die App müsste selbst entscheiden, in welchen der vorhandenen
  Kalender geschrieben wird — mehr Verantwortung für weniger Nutzen. Vor allem entstünde der Termin
  dann ohne Bestätigung, mit einer von BrownieDo geratenen Uhrzeit. Da eine Aufgabe keinen Zeitpunkt
  trägt, ist genau der Bildschirm, den der Intent öffnet, der Punkt, an dem die fehlende Information
  entsteht. Verworfen.
- **Google-Calendar-REST-API mit eigenem OAuth-Scope.** Bringt den Scope `calendar.events`, einen
  Consent-Screen, Token-Erneuerung, eigene Fehlerbehandlung und einen Server-Roundtrip, der offline
  nicht funktioniert — im Gegensatz zu allem anderen in dieser App
  ([ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md)). Für „aus einer Aufgabe einen Termin machen"
  unverhältnismäßig. Verworfen.
- **Fälligkeitsdatum an der Aufgabe plus eigene Erinnerung.** Wäre der klassische Weg einer ToDo-App
  und ist der ausdrücklich unerwünschte: ein weiterer Benachrichtigungskanal auf einem Gerät, das
  genug davon hat, dazu ein Termin, der an zwei Stellen gepflegt werden müsste. Verworfen — und der
  Grund, dass dieser ADR überhaupt existiert. Das Fälligkeitsdatum bleibt in `ROADMAP.md` §4 als
  spätere Option stehen, aber nur als Anzeige, nicht als Auslöser.
