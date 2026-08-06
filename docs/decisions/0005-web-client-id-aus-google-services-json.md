# 0005 – Web-Client-ID aus `google-services.json` beziehen

**Status:** akzeptiert · **Datum:** 2026-08-05

## Kontext

Der Credential Manager braucht für die Anfrage nach einem Google-ID-Token die
**Web-Client-ID** — den OAuth-Client vom Typ 3 des Firebase-Projekts. Diese ID muss zur
Laufzeit im Code verfügbar sein.

Naheliegend wäre gewesen, sie als Konstante zu hinterlegen. Das hätte sie jedoch ins Repository
gebracht, obwohl `google-services.json` bewusst in `.gitignore` steht.

## Entscheidung

Die Web-Client-ID wird nicht im Code hinterlegt. Das `google-services`-Gradle-Plugin erzeugt aus
der `google-services.json` automatisch die String-Ressource `R.string.default_web_client_id`;
`LoginScreen` liest sie von dort und reicht sie an den `GoogleIdTokenRequester` weiter.

## Konsequenzen

- Es gibt genau eine Quelle für die Client-ID. Ein Wechsel des Firebase-Projekts erfordert nur
  eine neue `google-services.json`, keine Codeänderung.
- Die Vorgabe „Secrets schützen" bleibt gewahrt: Weder Client-ID noch Projektschlüssel liegen im
  Repository.
- `R.string.default_web_client_id` existiert erst, wenn die `google-services.json` einen
  OAuth-Client vom Typ 3 enthält — also nachdem der Google-Anbieter aktiviert und der
  SHA-1-Fingerprint hinterlegt wurde. Vorher schlägt der Build fehl.
- Wer das Projekt neu auscheckt, braucht zwingend eine eigene `google-services.json`. Ohne sie
  kompiliert die App nicht.

## Alternativen

- **Konstante im Code:** Einfach, aber ein Secret im Repository und eine zweite Quelle für
  denselben Wert.
- **`local.properties` plus BuildConfig-Feld:** Hielte das Secret ebenfalls aus dem Repo, wäre
  aber eine dritte Konfigurationsdatei neben der `google-services.json`, die den Wert bereits
  enthält.
