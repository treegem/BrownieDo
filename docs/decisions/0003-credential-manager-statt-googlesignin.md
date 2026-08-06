# 0003 – Credential Manager statt GoogleSignIn-SDK

**Status:** akzeptiert · **Datum:** 2026-08-05

## Kontext

Für den Google-Login (siehe [0002](0002-google-login-als-einziger-anbieter.md)) gibt es auf
Android zwei Wege, an ein ID-Token zu kommen:

- das ältere `GoogleSignIn`-SDK aus den Play Services, das seit einiger Zeit als deprecated gilt
- den `androidx.credentials`-**Credential Manager** zusammen mit der `googleid`-Bibliothek,
  den Google als Nachfolger empfiehlt

Der Credential Manager ist die offizielle Empfehlung und bindet Passkeys sowie gespeicherte
Anmeldedaten mit ein. Er bringt allerdings mehr Dependencies mit und hat eine sperrigere API.

## Entscheidung

Der Login läuft über den Credential Manager. Der Aufruf ist in `ui/auth/GoogleIdTokenRequester`
gekapselt, weil er zwingend einen Activity-Context braucht.

Jede Anfrage verwendet einen frischen, SHA-256-gehashten Nonce aus `SecureRandom`, um
Replay-Angriffe auszuschließen.

Der Requester übersetzt die Credential-Manager-Exceptions in ein `GoogleIdTokenResult`. Wichtig
ist dabei die Unterscheidung zwischen einem Abbruch durch den Nutzer, einem fehlenden Google-Konto
und einem echten Fehler: Ein Abbruch ist Absicht und darf keine Fehlermeldung erzeugen.

## Konsequenzen

- Drei zusätzliche Dependencies: `androidx.credentials:credentials`,
  `credentials-play-services-auth` und `com.google.android.libraries.identity.googleid:googleid`.
- Die Android-spezifische Anmeldemechanik bleibt in der UI-Schicht. `AuthRepository` nimmt nur
  ein fertiges ID-Token entgegen und bleibt damit frei von Android-Typen — Voraussetzung für die
  spätere Wiederverwendung via Kotlin Multiplatform.
- Weil `GoogleIdTokenResult` ein reiner Rückgabewert ist, lässt sich das ViewModel ohne
  Android-Framework und ohne Mocking-Bibliothek testen: Die Testfälle liefern das gewünschte
  Ergebnis einfach direkt.
- Der Login setzt aktuelle Play Services auf dem Gerät voraus.

## Alternativen

- **`GoogleSignIn`-SDK:** Einfachere API und weniger Dependencies, aber deprecated. Für eine App,
  die über Jahre laufen soll, wäre das absehbar Wartungsschuld.
- **Firebase UI:** Fertige Login-Oberfläche, dafür ein weiteres Framework mit eigenem
  Navigationsmodell und wenig Kontrolle über das Erscheinungsbild.
