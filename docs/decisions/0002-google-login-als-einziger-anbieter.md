# 0002 – Google-Login als einziger Anbieter

**Status:** akzeptiert · **Datum:** 2026-08-05

## Kontext

Phase 2 verlangt eine Anmeldung, damit nur die beiden Partner-Accounts auf die geteilte
`todos`-Collection zugreifen können. Firebase Auth bietet dafür mehrere Anbieter; realistisch
standen E-Mail/Passwort und Google-Login zur Wahl.

E-Mail/Passwort ist billiger einzurichten: kein SHA-1-Fingerprint, keine zusätzlichen
Dependencies. Dafür fällt Passwortverwaltung an, und es braucht ein Eingabeformular mit
Validierung. Google-Login kostet einmalig Konfigurationsaufwand, spart danach aber jede
Passwortpflege — und beide Nutzer haben als Android-Nutzer ohnehin ein Google-Konto.

## Entscheidung

Google-Login ist der einzige aktivierte Anbieter. Auf einen E-Mail/Passwort-Fallback wird
bewusst verzichtet.

Die beiden Accounts werden nicht vorab angelegt, sondern entstehen automatisch, wenn sich jeder
Partner das erste Mal anmeldet.

## Konsequenzen

- Der Login-Screen besteht aus einem einzigen Button. Kein Formular, keine Validierung,
  keine „Passwort vergessen"-Behandlung.
- Für jeden Signing-Keystore muss der SHA-1-Fingerprint in der Firebase Console hinterlegt
  werden. Beim Debug-Keystore ist das erledigt; für den Release-Keystore steht ein eigener
  Punkt in Phase 7 der Roadmap. Wird er vergessen, schlägt der Login in der signierten APK mit
  einer nichtssagenden Meldung fehl.
- Ohne eingerichtetes Google-Konto ist keine Anmeldung möglich — auf einem Emulator ohne Konto
  meldet die App „kein Google-Konto eingerichtet". Das ist eine Konfigurationsfrage, kein Fehler.
- Die Firestore Security Rules in Phase 3 arbeiten mit den beiden Firebase-`uid`s. Diese sind
  vom Anbieter unabhängig — ein späterer Wechsel würde die Rules also nicht brechen, wohl aber
  neue uids erzeugen.

## Alternativen

- **E-Mail/Passwort:** Weniger Konfiguration, aber dauerhafte Passwortverwaltung für zwei
  Personen, die ihre Google-Konten ohnehin täglich nutzen.
- **Beide Anbieter parallel:** Böte einen Fallback, verdoppelt aber UI und Fehlerbehandlung.
  Widerspricht dem Leitprinzip „Einfachheit vor Vollständigkeit".
