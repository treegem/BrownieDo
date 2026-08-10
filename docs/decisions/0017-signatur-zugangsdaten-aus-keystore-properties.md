# 0017 – Signatur-Zugangsdaten aus `keystore.properties`

**Status:** akzeptiert · **Datum:** 2026-08-10

## Kontext

Für Phase 7 muss eine signierte Release-APK entstehen. Dafür braucht der Build den Pfad zum Keystore
und zwei Passwörter — Daten, die niemals ins Repo dürfen.

Der Ausgangszustand bot dafür nichts: Es gab weder einen `signingConfigs`-Block noch irgendeinen
Mechanismus, Geheimnisse aus dem Build zu halten. Eine Suche über `signingConfig`, `storeFile`,
`storePassword`, `keyAlias`, `System.getenv` und `Properties()` lieferte im ganzen Repo keinen
Treffer. `assembleRelease` erzeugte damit eine `app-release-unsigned.apk`, die sich nicht
installieren lässt; signieren ging nur über den Assistenten in Android Studio.

Dazu kam eine Lücke, die genau an dieser Stelle gefährlich wird: `.gitignore` deckte `*.jks` und
`*.keystore` ab, aber **nicht** `keystore.properties` — also ausgerechnet die Datei, in der die
Passwörter landen sollen.

## Entscheidung

Die Zugangsdaten stehen in `keystore.properties` im Repo-Root, mit den Schlüsseln `storeFile`,
`storePassword`, `keyAlias` und `keyPassword`. Die Datei steht in `.gitignore`; daneben liegt eine
eingecheckte `keystore.properties.example` mit denselben Schlüsseln und leeren Werten. Der Keystore
selbst liegt außerhalb des Repos, sein Pfad kommt aus der Properties-Datei.

Drei Eigenschaften macht die Umsetzung aus:

- **Die Signatur ist optional.** Fehlt `keystore.properties`, wird kein `signingConfig` angelegt, und
  `buildTypes.release` bekommt über `signingConfigs.findByName("release")` schlicht `null`. Ein
  frischer Klon baut und testet damit ohne jede Einrichtung.
- **Das Einlesen ist Configuration-Cache-tauglich.** `gradle.properties` setzt
  `org.gradle.configuration-cache=true`; gelesen wird deshalb über `providers.fileContents(...)`,
  damit Gradle die Datei als Eingabe kennt, statt sie am Cache vorbei zu öffnen.
- **Der Build prüft die Datei, bevor er sie benutzt.** Fehlt ein Schlüssel oder zeigt `storeFile` ins
  Leere, bricht die Konfiguration mit einer Meldung ab, die sagt, was zu tun ist. Das fängt vor allem
  eine Falle ab, die sonst schwer zu erkennen ist: In `.properties`-Dateien leitet der Backslash eine
  Escape-Sequenz ein, aus `C:\Users\georg\keys\browniedo-release.jks` wird beim Einlesen
  `C:Usersgeorgkeysrowniedo-release.jks` — das `\b` verschluckt sogar ein Zeichen.

R8 bleibt aus (`optimization { enable = false }`). Bei einer per Sideload verteilten App ist die
APK-Größe irrelevant, siehe [ADR 0007](0007-java-time-per-core-library-desugaring.md); ohne R8 kann
außerdem keine Reflection in Firebase, Credential Manager oder googleid wegoptimiert werden.

Verteilt wird eine **APK**, kein App Bundle: Ein AAB lässt sich ohne `bundletool` nicht per Sideload
installieren. Die `ROADMAP.md` nannte bisher beides.

## Konsequenzen

- Ein frischer Klon baut Debug ohne jede Einrichtung; nur das signierte Release braucht Vorarbeit.
- **`assembleRelease` liefert ohne `keystore.properties` still eine unsignierte APK.** Das ist der
  Preis dafür, dass der Build nicht bricht — und ein Stolperstein, wenn man es nicht weiß. Deshalb
  steht es in `AGENTS.md` und hier.
- Der Keystore ist unersetzlich: Android behandelt eine anders signierte APK als andere App. Geht er
  verloren, muss die App auf beiden Geräten deinstalliert und neu eingerichtet werden.
- Jede weitere verteilte Fassung braucht einen höheren `versionCode`.
- Der SHA-1 des Release-Keystores muss in der Firebase Console hinterlegt und
  `google-services.json` danach neu heruntergeladen werden, sonst scheitert der Google-Login, siehe
  [ADR 0002](0002-google-login-als-einziger-anbieter.md).

## Alternativen

- **`~/.gradle/gradle.properties`:** Liegt außerhalb des Repos und kann prinzipiell nicht committet
  werden — auch nicht bei einem Fehler in `.gitignore`. Dafür ist die Einrichtung für jemanden, der
  das Projekt neu auscheckt, unsichtbar: Es gibt keine Datei im Projekt, die verrät, dass es sie
  gibt. Die eingecheckte `.example`-Vorlage löst genau das.
- **Umgebungsvariablen:** Der richtige Weg für CI. Lokal unbequem, weil sie in jeder Shell gesetzt
  sein müssen, und Android Studio erbt die Shell-Umgebung nicht zuverlässig.
- **Passwörter in `local.properties`:** Schon gitignoriert, spart also den `.gitignore`-Eintrag.
  Aber Android Studio schreibt die Datei selbstständig um, und sie ist konzeptuell die Datei für den
  SDK-Pfad — Geheimnisse haben dort nichts zu suchen.
- **Nur der Assistent „Generate Signed App Bundle / APK" in Android Studio:** Kommt ganz ohne
  Konfiguration aus, ist aber weder reproduzierbar noch skriptbar und funktioniert nicht auf der
  Kommandozeile.
- **Den Build hart abbrechen lassen, wenn `keystore.properties` fehlt:** Wäre eindeutiger als eine
  stille unsignierte APK, würde aber jeden frischen Klon am Bauen hindern — auch für Debug-Arbeit,
  die mit Signaturen nichts zu tun hat.
