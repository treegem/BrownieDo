# BrownieDo – Gemeinsame ToDo-App für zwei Personen

## 1. Projektvision (das WARUM & WAS)

**Worum geht es?**
BrownieDo ist eine private, geteilte ToDo-Liste für **genau zwei Personen** (ein Paar,
beide mit Samsung-Galaxy-Android-Phones). Es ist ausdrücklich **kein Produkt für den Massenmarkt**,
sondern eine maßgeschneiderte App für den gemeinsamen Alltag der beiden Nutzer.

**Welches Problem löst die App?**
Die beiden wollen Aufgaben (Einkäufe, Erledigungen, Haushalts-To-Dos …) gemeinsam
verwalten. Beide sollen jederzeit Aufgaben hinzufügen, abhaken oder ändern können –
und der jeweils andere sieht diese Änderungen zuverlässig.

**Der zentrale nicht verhandelbare Kern:**
Die Liste muss **asynchron synchronisieren**. Das bedeutet: Es darf **nicht** vorausgesetzt
werden, dass beide gleichzeitig online sind. Einer kann offline (z. B. im Supermarkt ohne
Empfang) eine Aufgabe abhaken; sobald wieder Verbindung besteht, wird der Stand automatisch
mit dem des Partners zusammengeführt. Genau deshalb gibt es eine zentrale Cloud-Instanz
(Firestore) statt einer reinen Peer-to-Peer-Lösung.

**Was die App bewusst NICHT ist / braucht:**
- Keine Telefon-Spezifika (kein Gyroskop, keine Kamera, kein Standort etc.).
- Keine Skalierung auf viele Nutzer, keine komplexe Rechteverwaltung.
- Keine ausgefeilte Konfliktlösung nötig: Bei zwei Nutzern reicht **Last-Write-Wins**
  auf Feldebene (über einen `updatedAt`-Zeitstempel). Keine CRDTs.

**Leitprinzipien für Entscheidungen:**
- **Einfachheit vor Vollständigkeit** – nur bauen, was die zwei Nutzer wirklich brauchen.
- **Sauberkeit der Architektur** trotzdem einhalten (Schichtentrennung), damit eine
  spätere Erweiterung (Play Store, iOS via Kotlin Multiplatform) möglich bleibt.
- Wenig Betriebsaufwand: Backend-as-a-Service (Firestore) statt selbst gehostetem Server.

---

## 2. Technischer Rahmen (der STACK)

| Bereich | Entscheidung | Begründung |
|---|---|---|
| Sprache | **Kotlin** | Entwickler kann serverseitig bereits sehr gut Kotlin. |
| UI | **Jetpack Compose** (Material 3) | Offizieller Android-Standard, deklarativ, ideal für Listen. |
| IDE | **Android Studio** + GitHub Copilot, dazu Claude Code | Volles Android-Tooling out-of-the-box. |
| Backend | **Firebase Firestore** | Realtime-Sync + Offline-Persistenz eingebaut, kein eigener Server. |
| Auth | **Firebase Auth** | Regelt, dass nur die beiden Accounts Zugriff haben. |
| Architektur | UI (Compose) ⇄ ViewModel ⇄ Repository ⇄ Firestore | Saubere Trennung, testbar, KMP-fähig. |
| Async | **Coroutines & Flow** | Kotlin-Standard, dem Entwickler bereits vertraut. |
| Hosting/Kosten | Firebase **Spark (Free) Tier** | Für zwei Nutzer dauerhaft kostenlos (~0 €). |
| Verteilung | Direkt-Installation (Sideload) der signierten APK | Kein Play Store nötig für den privaten Einsatz. |

**Projekt-Eckdaten:**
- App-Name: **BrownieDo**
- Package/Application ID: `eu.sweetgeorgie.browniedo` (weltweit eindeutig, kein `com.example`)
- Minimum SDK: **API 24** (deckt ~99 % der Geräte ab)
- Build: **Kotlin DSL** (`build.gradle.kts`)
- Quellcode gehostet auf **GitHub** (privates Repo)

---

## 3. Umsetzungs-Checkliste (das WIE)

> **Legende:** `[ ]` offen · `[~]` in Arbeit · `[x]` erledigt · `[-]` zurückgestellt
> (blockiert durch etwas Äußeres, der Grund steht am Punkt)

### Phase 0 – Vorbereitung & Setup
- [x] Android Studio installieren (inkl. Android SDK & Emulator)
- [x] GitHub Copilot Plugin in Android Studio installieren und einloggen
- [x] Neues Projekt anlegen: **Empty Activity** (Compose), Sprache Kotlin, Package `eu.sweetgeorgie.browniedo`
- [x] Git-Repository initialisieren & auf GitHub veröffentlichen (`Share Project on GitHub`)
- [x] Beide Galaxy-Phones als Testgeräte einrichten (Entwickleroptionen + USB-Debugging)

### Phase 1 – Firebase-Projekt aufsetzen
- [x] Firebase-Projekt in der Firebase Console erstellen (Free/Spark-Tier)
- [x] Android-App im Firebase-Projekt registrieren (Package-Name `eu.sweetgeorgie.browniedo`)
- [x] `google-services.json` herunterladen und ins `app/`-Verzeichnis legen
- [x] **`google-services.json` in `.gitignore` eintragen (Secret, nicht committen!)**
- [x] Firebase Gradle-Plugins & Dependencies einbinden (Firestore, Auth, BoM)
- [x] Firestore-Datenbank in der Console anlegen (Produktionsmodus, Region wählen)

### Phase 2 – Authentifizierung (wer darf auf die Liste?)
- [x] Firebase Auth aktivieren (Google-Login als einziger Anbieter)
- [x] Einfache Login-/Anmelde-Oberfläche bauen (Compose)
- [x] Beide Partner melden sich einmal via Google an (legt die zwei Accounts automatisch an)
- [x] Login-Zustand in der App halten (angemeldet / nicht angemeldet)

### Phase 3 – Datenmodell & Firestore-Struktur
- [x] Datenmodell definieren: `Todo` (`id`, `title`, `isDone`, `createdAt`, `updatedAt`,
  `completedBy`) — `id` ist die Dokument-id und kein gespeichertes Feld; wie die Felder in Firestore
  heißen, steht in [ADR 0009](docs/decisions/0009-listen-dokument-mit-todo-subcollection.md)
- [x] Firestore-Struktur festlegen: `lists/{listId}` mit `members`-Array und Sub-Collection
  `lists/{listId}/todos` — trägt geteilte und private Listen, siehe
  [ADR 0009](docs/decisions/0009-listen-dokument-mit-todo-subcollection.md)
- [x] **Standardliste `lists/shared` einmalig in der Firebase Console anlegen**
  (`name`, `members: [uid1, uid2]`, `createdAt`) — beide UIDs stehen unter Authentication → Users
- [x] Security Rules: Zugriff auf eine Liste und ihre Aufgaben nur für die uids in deren `members`;
  Schreiben auf Listen-Dokumente selbst verbieten (`firestore.rules`)
- [x] `firestore.rules` in der Firebase Console veröffentlichen
- [x] `updatedAt`-Feld für Last-Write-Wins-Konfliktlösung vorsehen

### Phase 4 – Kern-Funktionalität (CRUD)
> Arbeitet gegen die feste Standardliste `lists/shared`; die Listen-Auswahl kommt in Phase 8.
- [x] Aufgabe **hinzufügen**
- [x] Aufgaben **anzeigen** (LazyColumn / Liste)
- [x] Aufgabe als **erledigt / offen** markieren
- [x] Aufgabe **bearbeiten**
- [x] Aufgabe **löschen**
- [x] Repository-Schicht sauber von der Compose-UI trennen

### Phase 5 – Synchronisation & Offline (der KERN der App)
- [x] Firestore **Offline-Persistenz** aktivieren — auf Android standardmäßig aktiv, kein Zutun nötig
- [x] **Realtime-Listener** einbinden (Liste aktualisiert sich automatisch)
- [-] Offline-Szenario testen: einer offline ändern → wieder online → synct beim anderen
  — zurückgestellt, bis das zweite Handy verfügbar ist
- [-] Konflikt-Verhalten prüfen (Last-Write-Wins über `updatedAt`)
  — zurückgestellt, bis das zweite Handy verfügbar ist

### Phase 6 – UI-Feinschliff
- [x] Aufgeräumtes Layout (Material 3) — TopAppBar mit Overflow-Menü, Eingabefeld als `bottomBar`
  ([ADR 0013](docs/decisions/0013-eingabefeld-in-der-bottombar-statt-fab.md)), Zeilen als
  `ListItem`, Schreibfehler als Snackbar; Scaffold pro Bildschirm
  ([ADR 0012](docs/decisions/0012-scaffold-pro-bildschirm.md))
- [x] Leerer Zustand („noch keine Aufgaben") & Ladezustand — ein Ladefehler verdrängt den
  Leerzustand, statt beides gleichzeitig zu zeigen
- [x] Aufgaben sortieren (offen oben, erledigt unten, je die neuesten zuerst) — im Repository wie
  in [ADR 0010](docs/decisions/0010-sortierung-im-client-statt-orderby.md) vorgesehen; abgehakte
  Einträge sinken sofort und animiert nach unten
- [x] App-Icon & Name anpassen — eigenes adaptives Icon (Häufchen mit grünem Haken) plus
  einfarbige Variante für die themenbezogenen Icons ab Android 13; der Name stand bereits auf
  „BrownieDo". Offen bleiben die Raster-Icons in `mipmap-*dpi`: dort liegt noch die Vorlage, sie
  greift aber nur auf API 24/25 — erzeugen ließe sie sich mit dem Image Asset Studio
- [x] Erledigte Aufgaben durch Wischen nach rechts löschen — offene Aufgaben lassen sich bewusst
  nicht wischen, siehe [ADR 0016](docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md)
- [x] Dark Mode prüfen — der Fensterhintergrund folgt dem Dunkelmodus über
  `res/values-night/themes.xml`, das Compose-Farbschema über `BrownieDoTheme`; auf dem Gerät
  durchgesehen, keine Beanstandung

### Phase 7 – Test & Verteilung an euch zwei
- [-] Auf beiden Galaxy-Phones testen — auf dem ersten Gerät (SM-S928B) erfolgreich getestet:
  signierte APK installiert, Google-Login durchgespielt. Das zweite Handy fehlt noch
- [x] Signierte APK bauen (Keystore NICHT committen!) — `signingConfig` liest die Zugangsdaten aus
  einer nicht eingecheckten `keystore.properties`, siehe
  [ADR 0017](docs/decisions/0017-signatur-zugangsdaten-aus-keystore-properties.md). Kein App
  Bundle: Ein AAB lässt sich ohne `bundletool` nicht per Sideload installieren. Keystore erzeugen
  und `keystore.properties` ausfüllen bleibt Handarbeit, Anleitung in `AGENTS.md`
- [x] **SHA-1 des Release-Keystores in der Firebase Console hinterlegen** — erledigt und auf dem
  Gerät bestätigt: `google-services.json` trägt jetzt beide Fingerabdrücke (Debug und Release),
  der Google-Login in der signierten APK funktioniert
- [-] Direkt auf beide Geräte installieren (Sideload) — auf dem ersten Gerät erledigt, das zweite
  Handy fehlt noch

### Phase 8 – Mehrere Listen (geteilt & privat)
> Die Datenstruktur steht seit Phase 3
> ([ADR 0009](docs/decisions/0009-listen-dokument-mit-todo-subcollection.md)), es fehlt die
> Bedienung. Aufgeteilt in einen lesenden und einen schreibenden Teil: **8a** kommt ohne Änderung
> an den Security Rules aus und ist für sich allein benutzbar, **8b** braucht eine Regeländerung,
> die von Hand veröffentlicht werden muss.

#### Phase 8a – Listen lesen und wechseln
- [x] `FirestoreTodoRepository` von der festen `DEFAULT_LIST_ID` lösen — jede Methode von
  `TodoRepository` nimmt die `listId` jetzt als Parameter, damit ein Schreibvorgang während eines
  Listenwechsels nicht in der falschen Liste landet
- [x] Listen des angemeldeten Nutzers laden (`lists`, gefiltert über `members`) — der
  `whereArrayContains`-Filter ist Pflicht, nicht Optimierung: Ohne ihn lehnen die Security Rules die
  ganze Query ab
- [x] Listen-Auswahl in der TopAppBar — der Titel ist antippbar und öffnet ein Menü mit Symbol für
  geteilt/privat ([ADR 0013](docs/decisions/0013-eingabefeld-in-der-bottombar-statt-fab.md))
- [x] Zuletzt gewählte Liste über App-Neustarts hinweg merken — DataStore Preferences, pro Gerät,
  mit Rückfall auf die erste Liste
  ([ADR 0018](docs/decisions/0018-datastore-fuer-die-zuletzt-gewaehlte-liste.md))
- [ ] Auf dem Gerät prüfen: umschalten, App neu starten, offline starten — dafür muss eine zweite
  Liste von Hand in der Firebase Console angelegt werden

#### Phase 8b – Listen anlegen, umbenennen, löschen
> Die Reihenfolge ist hier nicht beliebig: Ohne die Regeländerung scheitert jeder Schreibvorgang.
- [ ] Security Rules auf das Anlegen und Ändern von Listen erweitern — `firestore.rules` verbietet
  das heute mit `write: if false` — und **von Hand in der Firebase Console veröffentlichen**
- [ ] Liste anlegen (privat = nur eigene uid, geteilt = beide uids) und umbenennen
- [ ] Liste löschen inklusive ihrer `todos`-Sub-Collection — Firestore löscht nicht kaskadierend,
  die App muss selbst aufräumen
- [ ] Verhalten festlegen, wenn die gerade geöffnete Liste verschwindet, weil der Partner sie
  gelöscht hat

### Querlaufend – Werkzeuge & Doku
> Läuft neben den Phasen und gehört zu keiner.
- [x] `AGENTS.md` als gemeinsamen Einstieg für alle Coding-Agents anlegen; `CLAUDE.md` auf die
  Regel-Importe reduzieren ([ADR 0015](docs/decisions/0015-agents-md-als-gemeinsamer-einstieg.md))
- [x] Alle Regeldateien auf always-on umstellen, nachdem gemessen wurde, dass das
  JetBrains-Copilot-Plugin `applyTo` nicht auswertet
  ([ADR 0014](docs/decisions/0014-regeldateien-always-on.md))
- [ ] **Die instrumentierten Tests einmal wirklich ausführen** (`TodoListScreenTest`, 7 Stück) —
  sie wurden bisher nur kompiliert, nie gelaufen, weil kein Gerät angeschlossen war. Grün ist also
  nichts davon belegt. Offen ist dabei besonders, ob `swipeRight()` die 85-%-Wischschwelle aus
  [ADR 0016](docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md) überhaupt reißt; falls
  nicht, gehört die Wischstrecke im Test gesetzt und **nicht** die Schwelle gesenkt — die ist eine
  Produktentscheidung
- [~] Prüfen, dass die `@`-Importe in `CLAUDE.md` und die Regeln in Android Studio tatsächlich laden
  (Canary-Methode, siehe ADR 0014)

---

## 4. Optionale / spätere Erweiterungen (nice to have)
- [ ] Push-Benachrichtigung, wenn der andere etwas ändert (Firebase Cloud Messaging)
- [ ] Fälligkeitsdaten / Erinnerungen
- [ ] Play-Store-Veröffentlichung (Developer-Account, Store-Eintrag, Datenschutzerklärung)
- [ ] iOS-Version via Kotlin Multiplatform (Logik-Schicht wiederverwenden)

---

## 5. Projektspezifische Vorgaben

Die allgemeinen Coding-, Architektur- und Naming-Regeln stehen in `.github/instructions/`
und werden von allen Coding-Agents geladen — der gemeinsame Einstieg dazu steht in
[`AGENTS.md`](AGENTS.md). Die Begründungen hinter den technischen
Entscheidungen stehen als ADRs in [`docs/decisions/`](docs/decisions/README.md).
Hier stehen nur die Entscheidungen, die speziell für BrownieDo gelten:

- **Secrets schützen:** `google-services.json` und Keystore niemals ins Repo.
- **Security Rules:** `firestore.rules` im Repo-Root ist die Quelle der Wahrheit. Veröffentlicht
  wird von Hand über die Firebase Console — für zwei Nutzer lohnt kein Firebase-CLI-Setup.
- **Konfliktstrategie:** Für zwei Nutzer reicht Last-Write-Wins auf Feldebene (`updatedAt`) — keine CRDTs.
- **Mehrere Listen:** BrownieDo trägt dauerhaft geteilte *und* private Listen. Die Struktur
  (`lists/{listId}` mit `members` plus Sub-Collection `todos`) steht seit Phase 3, die Bedienung
  folgt in Phase 8 — siehe [ADR 0009](docs/decisions/0009-listen-dokument-mit-todo-subcollection.md).
- **Zukunftssicherheit:** Business-Logik frei von Android-Framework-Abhängigkeiten halten,
  damit sie später via Kotlin Multiplatform auf iOS wiederverwendbar ist.
