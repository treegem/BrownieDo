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
- [x] Offline-Szenario testen: einer offline ändern → wieder online → synct beim anderen — auf zwei
  Geräten durchgespielt, auch mit einem Handy vorübergehend im Flugmodus
- [ ] Konflikt-Verhalten prüfen (Last-Write-Wins über `updatedAt`) — beide Geräte ändern *dasselbe*
  Feld, während eines offline ist; noch nicht gezielt provoziert

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
  durchgesehen
- [x] Eigene Farbpalette statt Dynamic Color — aufgefallen war, dass ein aktiver Dialog-Knopf mit
  2,12 : 1 wie deaktiviert wirkte, weil die Farbe vom Hintergrundbild des Geräts kam. Jetzt zwei
  vollständige Schemata aus den Icon-Farben, Kontrast von `ColorSchemeContrastTest` abgesichert
  ([ADR 0021](docs/decisions/0021-eigene-farbpalette-statt-dynamic-color.md))

### Phase 7 – Test & Verteilung an euch zwei
- [x] Auf beiden Galaxy-Phones testen — signierte APK installiert, Google-Login durchgespielt und
  der Sync zwischen beiden Geräten belegt (siehe Phase 5)
- [x] Signierte APK bauen (Keystore NICHT committen!) — `signingConfig` liest die Zugangsdaten aus
  einer nicht eingecheckten `keystore.properties`, siehe
  [ADR 0017](docs/decisions/0017-signatur-zugangsdaten-aus-keystore-properties.md). Kein App
  Bundle: Ein AAB lässt sich ohne `bundletool` nicht per Sideload installieren. Keystore erzeugen
  und `keystore.properties` ausfüllen bleibt Handarbeit, Anleitung in `AGENTS.md`
- [x] **SHA-1 des Release-Keystores in der Firebase Console hinterlegen** — erledigt und auf dem
  Gerät bestätigt: `google-services.json` trägt jetzt beide Fingerabdrücke (Debug und Release),
  der Google-Login in der signierten APK funktioniert
- [x] Direkt auf beide Geräte installieren (Sideload)

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
- [x] Auf dem Gerät prüfen: umschalten und App neu starten — belegt auf einem SM-S928B mit einer
  zweiten, von Hand angelegten Liste. Die Auswahl zeigt beide Listen mit der aktuellen farblich
  hervorgehoben, und nach `am force-stop` plus Neustart steht dieselbe Liste wieder offen. Im
  DataStore der App liegt `selected_list.preferences_pb` mit `selected_list_id → second-shared`
- [x] Offline starten — belegt bei aktivem Flugmodus: Die Listen-Query wird aus Firestores lokalem
  Cache beantwortet, der Listenname steht, kein Ladefehler
- [x] Symbole und Sortierung auf dem Gerät belegt — mit drei Listen zeigt das Menü das
  Einzelperson-Symbol für die private und das Gruppen-Symbol für die beiden geteilten, und die
  Reihenfolge ist alphabetisch (Gemeinsam → Private Liste → Zweite Liste). Damit greift auch
  `LIST_ORDER` aus [ADR 0010](docs/decisions/0010-sortierung-im-client-statt-orderby.md) sichtbar

#### Phase 8b – Listen anlegen, umbenennen, löschen
> Die Reihenfolge ist hier nicht beliebig: Ohne die Regeländerung scheitert jeder Schreibvorgang.
- [x] Security Rules auf das Anlegen und Ändern von Listen erweitern — `create`/`update`/`delete`
  statt `write: if false`, wobei `members` unveränderlich bleibt, siehe
  [ADR 0019](docs/decisions/0019-schreibrechte-auf-listen-dokumente.md)
- [x] **Die neuen Rules von Hand in der Firebase Console veröffentlicht** — vorher wäre jeder
  Schreibvorgang mit `PERMISSION_DENIED` gescheitert
- [x] **Die zwei Dokumente `users/{uid}` mit `displayName` in der Console angelegt** — ohne sie ließe
  sich keine geteilte Liste anlegen, siehe
  [ADR 0020](docs/decisions/0020-partner-aus-users-collection.md). Die uids stehen unter
  Authentication → Users
- [x] Liste anlegen (privat = nur eigene uid, geteilt = beide uids) und umbenennen — die Partner-uid
  kommt aus `users` und verlässt die Datenschicht nicht
- [x] Liste löschen inklusive ihrer `todos`-Sub-Collection — alles in einem `WriteBatch`, damit die
  Regel das Listen-Dokument beim Löschen der Aufgaben noch findet (ADR 0019)
- [x] Verhalten festlegen, wenn die gerade geöffnete Liste verschwindet — der Rückfall aus 8a greift;
  war es die letzte Liste, wird ein hängender Ladefehler jetzt aufgeräumt, damit er nicht den
  Hinweis „Noch keine Liste" verdrängt
- [x] Auf dem Gerät prüfen — belegt auf einem SM-S928B: geteilte Liste angelegt (erscheint mit
  Gruppen-Symbol, die Partner-uid wurde also mitgeschrieben) · umbenannt und zurückbenannt · eine
  Liste **mit zwei Aufgaben** gelöscht, ohne Fehler, danach Rückfall auf die erste verbleibende
  Liste. Der Anlegen-Dialog zeigt „Geteilt mit <Name>" aus der `users`-Collection, die
  `exists()`-Leseregel greift also
- [x] Auch der private Pfad ist auf dem Gerät belegt — private Liste aus der App angelegt und in
  Firestore wiedergefunden; `members` trägt dort erwartungsgemäß nur die eigene uid

### Phase 9 – Priorität für Aufgaben
> Drei Stufen: niedrig · mittel · hoch. Neue Aufgaben stehen auf mittel, bestehende gelten als
> mittel. Die Security Rules bleiben unberührt — auf `todos` gilt `read, write` für alle Mitglieder
> der Liste, ohne Feldprüfung.
- [x] `Todo` um eine Priorität erweitern — `TodoPriority` in `domain/todo`, Bezeichner englisch,
  Anzeigetexte „Niedrig/Mittel/Hoch" in `strings.xml` (Sprachregel in `AGENTS.md`)
- [x] `TodoDocument` und `TodoField` um das Feld ergänzen — im Dokument steht der **Name** der
  Stufe als `String?`, kein Enum-Typ: Firestore wirft bei einem unbekannten Enum-Wert innerhalb des
  Snapshot-Listeners, wo der Fehler nicht als `Result` herauskommt, sondern die ganze
  Aktualisierung mitreißt
- [x] Bestehende Aufgaben ohne das Feld gelten beim Lesen als mittel — kein Nachziehen in der
  Console, siehe [ADR 0023](docs/decisions/0023-prioritaet-migration-und-sortierung.md)
- [x] Priorität setzen — im Bearbeiten-Dialog über eine Segment-Auswahl. Titel und Priorität gehen
  in **einem** Schreibvorgang raus (`updateTodo` statt `setTitle`), siehe
  [ADR 0025](docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md). Wischen nach
  rechts bleibt unangetastet
  ([ADR 0016](docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md))
- [x] Priorität in der Zeile anzeigen — nur Abweichungen bekommen ein Symbol, „mittel" bleibt
  unmarkiert. Pfeil hoch bzw. runter am Zeilenende, je mit `contentDescription`; die Form trägt die
  Bedeutung allein, Farbe kommt nur dazu. Die zwei neuen Farbpaare sind in
  `ColorSchemeContrastTest` aufgenommen
  ([ADR 0021](docs/decisions/0021-eigene-farbpalette-statt-dynamic-color.md))
- [x] `Todo` und `TodoDocument` um `completedAt` erweitern (nullable, analog zu `completedBy`) —
  `setDone` setzt es beim Abhaken und löscht es beim Wiederöffnen, siehe
  [ADR 0023](docs/decisions/0023-prioritaet-migration-und-sortierung.md). Bewusst **ohne**
  `@ServerTimestamp`: die Annotation greift beim Schreiben des ganzen Objekts, `addTodo` gäbe damit
  jeder frisch angelegten offenen Aufgabe einen Erledigungszeitpunkt
- [x] `TODO_ORDER` um die Priorität erweitern — offen vor erledigt; innerhalb der offenen Aufgaben
  zuerst nach Priorität (hoch → mittel → niedrig), dann wie bisher nach `createdAt`; innerhalb der
  erledigten Aufgaben neu nach `completedAt` absteigend statt nach `createdAt`, siehe
  [ADR 0023](docs/decisions/0023-prioritaet-migration-und-sortierung.md). Zwei getrennte
  Vergleiche statt einer Kette, damit die Priorität erledigte Aufgaben nicht doch als
  Gleichstand-Entscheider umsortiert
- [x] Unit-Tests: Mapper mit und ohne die neuen Felder, `TODO_ORDER` mit gemischten Prioritäten und
  gemischten `completedAt`-Zeitpunkten, ViewModel — 82 Unit-Tests grün. Der instrumentierte Test
  deckt Markierung, fehlende Markierung bei „mittel" und die Segment-Auswahl ab; er übersetzt,
  gelaufen ist er mangels Gerät noch nicht
- [ ] Auf dem Gerät prüfen — alte Aufgabe ohne die neuen Felder erscheint und sitzt am Ende des
  erledigten Blocks · eine Aufgabe auf „hoch" setzen und steigen sehen · zwei Aufgaben in bekannter
  Reihenfolge abhaken und den erledigten Block prüfen · hell und dunkel · und nachsehen, ob die
  drei Segment-Beschriftungen im Dialog abgeschnitten werden

### Phase 10 – Aufgaben zwischen Listen verschieben
> Ziel darf jede Liste sein, in deren `members` die eigene uid steht — geteilte wie private. Das
> sind genau die Listen, die die Auswahl aus 8a ohnehin schon lädt.
- [ ] `TodoRepository` um das Verschieben erweitern (Quell- und Ziel-`listId` explizit, wie alle
  anderen Methoden auch)
- [ ] Als `WriteBatch` umsetzen — Firestore kennt kein Verschieben: Dokument in der Ziel-Sub-Collection
  anlegen und das alte löschen. Ein Batch, damit die Aufgabe nie doppelt oder gar nicht existiert
  (dasselbe Muster wie beim Löschen einer Liste,
  [ADR 0019](docs/decisions/0019-schreibrechte-auf-listen-dokumente.md))
- [ ] Alle fachlichen Felder wandern unverändert mit — `createdAt`, der Erledigt-Zustand,
  `completedBy`, `priority` und `completedAt`; nur `updatedAt` und die Dokument-id entstehen neu,
  siehe [ADR 0024](docs/decisions/0024-verschieben-behaelt-zustand.md)
- [ ] Security Rules gegenprüfen — ein Batch über zwei Listen. `isListMember` wird pro Dokument
  ausgewertet, die Regel sollte also unverändert tragen; falls doch nicht, muss die Änderung vor
  dem Gerätetest von Hand veröffentlicht werden
- [ ] Bedienung im Bearbeiten-Dialog — Titel ändern, Liste ändern, löschen an einer Stelle. Zur
  Auswahl stehen die geladenen Listen ohne die aktuelle, siehe
  [ADR 0022](docs/decisions/0022-verschieben-im-bearbeiten-dialog.md)
- [-] Verschieben per Wischgeste — zurückgestellt, bis der Alltag zeigt, ob es häufig genug
  vorkommt; dann nach links für jede Aufgabe (ADR 0022)
- [ ] Ist die aktuelle Liste die einzige, ist „Verschieben" im Dialog sichtbar, aber deaktiviert
  (Standardmuster für einen Menüpunkt ohne gültiges Ziel, statt ihn ganz auszublenden)
- [ ] Unit-Tests und Gerätetest: je einmal in eine geteilte und in eine private Liste verschieben und
  auf beiden Geräten nachsehen

### Querlaufend – Werkzeuge & Doku
> Läuft neben den Phasen und gehört zu keiner.
- [x] `AGENTS.md` als gemeinsamen Einstieg für alle Coding-Agents anlegen; `CLAUDE.md` auf die
  Regel-Importe reduzieren ([ADR 0015](docs/decisions/0015-agents-md-als-gemeinsamer-einstieg.md))
- [x] Alle Regeldateien auf always-on umstellen, nachdem gemessen wurde, dass das
  JetBrains-Copilot-Plugin `applyTo` nicht auswertet
  ([ADR 0014](docs/decisions/0014-regeldateien-always-on.md))
- [x] **Die instrumentierten Tests einmal wirklich ausführen** — erledigt, alle 8 grün auf einem
  SM-S928B. Dabei kam heraus, warum sie nie etwas geprüft hatten: `TodoListScreenTest` importierte
  `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`. Mit diesem Import scheitern alle
  Compose-Tests reproduzierbar an „No compose hierarchies found"; der Standard-Import ohne `v2`
  behebt es. Nebenbei bestätigt: `swipeRight()` reißt die 85-%-Wischschwelle aus
  [ADR 0016](docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md), die Tests brauchen
  keine gesetzte Wischstrecke.
  **Achtung für den nächsten Lauf:** Sperrt der Bildschirm während des Laufs, brechen die Tests mit
  demselben „No compose hierarchies"-Fehler ab und Wireless Debugging verliert die Verbindung —
  per Kabel testen oder in den Entwickleroptionen „Aktiv lassen" einschalten.
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
