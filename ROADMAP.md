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

**Wie die beiden damit arbeiten (der Rhythmus):**
Die App strukturiert den Alltag der beiden. Alles, was nicht alltäglich ist, sammelt sich zuerst in
einem **gemeinsamen Backlog**; in einer **wöchentlichen Besprechung** wandern daraus die Punkte, die
in dieser Woche wirklich dran sind, in die **Wochenliste der jeweiligen Person**. Jede Person weiß
danach, was sie sich für die Woche vorgenommen hat. Daraus folgen zwei Dinge für Entscheidungen:
Verschieben zwischen Listen ist keine Randfunktion, sondern der wöchentliche Kernvorgang — und ein
Backlog-Eintrag lebt Wochen, muss sich also nach Wochen noch selbst erklären. Was das für die Art der
Wochenlisten heißt, steht unten unter „Projektspezifische Vorgaben".

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
- [x] **`versionCode` gehoben** — auf `2` (`versionName` „1.1"), zusammen mit dem Release, das Phase 11
  und 12 auf das zweite Handy bringt. Damit dieser Punkt nicht wiederkehrt, ist die Regel
  **verschärft und in die Erwartungen an *jede* Änderung gewandert**: Der `versionCode` steigt bei
  jeder mittleren oder größeren Änderung, nicht erst beim Verteilen — samt Abgrenzung, was zählt
  (Feature, Fehlerbehebung, Oberfläche, Ressourcen, Manifest, Gradle) und was nicht (Doku, ADRs,
  Regeldateien, Kommentare, Tests). Steht in `AGENTS.md`, hier nur der Verweis

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
  deckt Markierung, fehlende Markierung bei „mittel" und die Segment-Auswahl ab; **am 2026-08-12 auf
  einem SM-S928B gelaufen und grün**
- [ ] Auf dem Gerät prüfen — alte Aufgabe ohne die neuen Felder erscheint und sitzt am Ende des
  erledigten Blocks · eine Aufgabe auf „hoch" setzen und steigen sehen · zwei Aufgaben in bekannter
  Reihenfolge abhaken und den erledigten Block prüfen · hell und dunkel · und nachsehen, ob die
  drei Segment-Beschriftungen im Dialog abgeschnitten werden

### Phase 10 – Aufgaben zwischen Listen verschieben
> Ziel darf jede Liste sein, in deren `members` die eigene uid steht — geteilte wie private. Das
> sind genau die Listen, die die Auswahl aus 8a ohnehin schon lädt.
- [x] `TodoRepository` um das Verschieben erweitern — `moveTodo` nimmt Quell- und Ziel-`listId`
  explizit und dazu die ganze Aufgabe. **Nicht suspend:** Anders als `deleteList` gibt es nichts
  nachzuschlagen, die Ziel-id vergibt `document()` lokal. Damit bleibt das Verschieben bei
  [ADR 0011](docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md) und funktioniert offline
- [x] Als `WriteBatch` umsetzen — Firestore kennt kein Verschieben: Dokument in der Ziel-Sub-Collection
  anlegen und das alte löschen. Ein Batch, damit die Aufgabe nie doppelt oder gar nicht existiert
  (dasselbe Muster wie beim Löschen einer Liste,
  [ADR 0019](docs/decisions/0019-schreibrechte-auf-listen-dokumente.md)) — anders als dort hängt
  hier aber nichts an der Reihenfolge, beide Listen-Dokumente überstehen den Batch unberührt
- [x] Alle fachlichen Felder wandern unverändert mit — `createdAt`, der Erledigt-Zustand,
  `completedBy`, `priority` und `completedAt`; nur `updatedAt` und die Dokument-id entstehen neu,
  siehe [ADR 0024](docs/decisions/0024-verschieben-behaelt-zustand.md). Trägt die erste
  Domäne→Dokument-Abbildung des Projekts, abgesichert über einen Hin-und-zurück-Test
- [x] Security Rules gegenprüfen — **keine Änderung nötig, kein Konsolen-Schritt.** `allow read,
  write: if isListMember(listId)` wird pro Operation mit der `listId` aus dem jeweiligen Pfad
  ausgewertet; beide Listen-Dokumente bleiben unberührt und existieren beim `get()` noch.
  Mitgliedschaft in beiden ist bauartbedingt gegeben, weil die Auswahl nur Listen aus der
  `whereArrayContains`-Query zeigt
- [x] Bedienung im Bearbeiten-Dialog — Titel ändern, Liste ändern, löschen an einer Stelle. Die
  Zielliste ist ein **Feld** wie Titel und Priorität: „Speichern" schreibt entweder an Ort und
  Stelle oder verschiebt, in einem Schreibvorgang. Gewählt wird über ein Aufklappmenü, das
  vorhandene `ListMenuItem` aus der TopAppBar wird wiederverwendet, siehe
  [ADR 0022](docs/decisions/0022-verschieben-im-bearbeiten-dialog.md)
- [x] Die **aktuelle Liste steht mit im Menü und ist vorausgewählt** — hier stand vorher „ohne die
  aktuelle". Das war für das ältere Bild geschrieben, in dem Verschieben eine eigene Aktion war;
  als Feld gäbe es sonst nach einem Fehlgriff keinen Weg zurück zu „bleibt, wo sie ist", außer den
  Dialog abzubrechen
- [x] Rückmeldung nach dem Verschieben — Snackbar „Nach <Liste> verschoben". Ohne sie verschwindet
  der Eintrag einfach und sähe wie ein Löschen aus. Zweiter Meldungskanal im UiState neben dem
  Fehler; zwei Effekte am selben `SnackbarHostState` reihen sich an, statt sich zu verdrängen
- [-] Verschieben per Wischgeste — zurückgestellt, bis der Alltag zeigt, ob es häufig genug
  vorkommt; dann nach links für jede Aufgabe (ADR 0022)
- [x] Ist die aktuelle Liste die einzige, ist das Listen-Feld im Dialog sichtbar, aber deaktiviert
  (Standardmuster statt es ganz auszublenden). `clickable(enabled = false)` nimmt auch die
  Klick-Semantik weg, TalkBack bietet die Aktion also gar nicht erst an
- [x] Unit-Tests — 97 grün. Mapper in beide Richtungen inklusive Hin-und-zurück-Vergleich, der
  ViewModel-Zweig für Verschieben gegen Ändern, und die Fälle, in denen Eintrag oder Zielliste
  während des offenen Dialogs verschwinden. Die vier neuen instrumentierten Tests sind **am
  2026-08-12 auf einem SM-S928B gelaufen**, alle 16 grün — einer davon musste dafür korrigiert
  werden, siehe „Querlaufend"
- [x] **Das `Popup`-im-`Dialog`-Risiko ist damit vom Tisch** — `pickingAnotherListInTheEditDialogReportsIt`
  öffnet das Aufklappmenü im Dialog und tippt einen Eintrag an, auf dem Gerät grün. Der Ausweg über
  `ExposedDropdownMenuBox` wird nicht gebraucht. Was der Test *nicht* zeigt, ist wie das Menü aussieht
  — das bleibt beim Punkt unten
- [ ] Auf dem Gerät prüfen — in eine geteilte und in eine private Liste verschieben, auf beiden
  Geräten nachsehen · eine **erledigte** Aufgabe verschieben (muss abgehakt ankommen, mit
  `completedBy` und `completedAt`, an derselben Stelle im erledigten Block) · eine alte Aufgabe
  verschieben und prüfen, dass sie in der Zielliste **nicht** nach oben springt · der Fall „nur eine
  Liste" · Snackbar-Text, hell und dunkel · offline verschieben und wieder verbinden · und ein Blick
  darauf, wie das Aufklappmenü im Dialog *aussieht* (dass es aufgeht, steht seit dem Testlauf fest,
  siehe den Punkt darüber). Das bleibt Handarbeit: Es geht um zwei Geräte, echte Firestore-Daten und
  darum, wie es aussieht — nichts davon prüft ein instrumentierter Test

### Phase 11 – Aus einer Aufgabe einen Termin machen
> Der Termin gehört in den Google Kalender, **nicht** in BrownieDo: Eine Fälligkeit in der App zieht
> Erinnerungen nach sich und damit einen weiteren Benachrichtigungskanal auf Geräten, die genug davon
> haben. Der Weg ist ein `ACTION_INSERT`-Intent — kein OAuth-Scope, keine Berechtigung, kein
> gespeicherter Zustand, keine Änderung an `firestore.rules` und **kein Schritt in der Firebase
> Console**, siehe [ADR 0027](docs/decisions/0027-termine-per-kalender-intent.md). Damit die kleinste
> Phase des Projekts.
- [x] Kalender-Intent in der UI-Schicht — `ACTION_INSERT` auf `CalendarContract.Events.CONTENT_URI`
  mit dem Aufgabentitel als `Events.TITLE`, in `ui/todo/CalendarEventIntent.kt`. Gehört in `ui`,
  nicht ins ViewModel: Es gibt keinen Zustand und keine Regel, und `Intent`/`CalendarContract` sind
  Android-Framework, das aus der Logik-Schicht fernbleibt (siehe „Projektspezifische Vorgaben")
- [x] **Kein Datum vorbelegen** — eine Aufgabe trägt keinen Zeitpunkt, eine geratene Stunde würde
  häufiger korrigiert als übernommen (ADR 0027). `theIntentCarriesNoTime` hält das fest
- [x] Erst `setPackage("com.google.android.calendar")`, bei `ActivityNotFoundException` ohne Paket
  erneut — auf einem Galaxy bedienen zwei Apps diesen Intent, und der Samsung Kalender kann in ein
  lokales Konto schreiben, das nie bei Google auftaucht. Der zweite Versuch baut einen **frischen**
  Intent, statt das Paket wieder zu entfernen
- [x] **Kein `resolveActivity`**, sondern `try`/`catch`: Seit Android 11 liefert das ohne
  `<queries>`-Eintrag im Manifest `null`, obwohl die Kalender-App da ist. Scheitert auch der zweite
  Versuch, kommt eine Snackbar — `CALENDAR_APP_MISSING` in `TodoListError`. Der Fehler läuft dabei
  doch über das ViewModel, anders als ADR 0027 schrieb: `error` wird nur dort geschrieben, und ein
  eigener Zustand im Bildschirm wäre ohne Gerät nicht prüfbar, siehe
  [ADR 0029](docs/decisions/0029-kalender-fehler-ueber-todolisterror.md)
- [x] Bedienung im Bearbeiten-Dialog, wie Verschieben
  ([ADR 0022](docs/decisions/0022-verschieben-im-bearbeiten-dialog.md)). Der Dialog macht damit fünf
  Dinge — die Entzerrung war dafür vorgezogen, der Rückruf blieb wie versprochen ein Eintrag in
  `TodoEditActions` plus eine Zeile in `MainActivity`, siehe
  [ADR 0028](docs/decisions/0028-rueckrufe-in-actions-haltern.md). Als Aktionszeile **unter** den
  Feldern statt als dritter Knopf neben Löschen und Abbrechen; der Titel kommt aus dem Feld, und
  der Dialog bleibt offen — es wird dabei nichts nach Firestore geschrieben. (Seit
  [ADR 0032](docs/decisions/0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md) steht Löschen
  ebenfalls dort, und die Knopfzeile trägt nur noch Abbrechen und Speichern — aus dem Sonderfall ist
  damit die Regel geworden)
- [x] Test auf den Intent-Bau (Action, Data-Uri, Titel-Extra, kein Paket, kein Zeitpunkt) —
  `CalendarEventIntentTest`. **Steht in `androidTest`, nicht in `test`**, obwohl er weder Compose
  noch Gerätezustand braucht: `android.content.Intent` ist im reinen JVM-Test eine Attrappe und
  wirft „Stub!". Der einzige Ausweg wäre Robolectric — eine Test-Abhängigkeit für genau eine Datei,
  und damit gegen „Einfachheit vor Vollständigkeit". Der Fehlerweg selbst ist dagegen sehr wohl ohne
  Gerät geprüft (zwei Tests im `TodoListViewModelTest`, 99 Unit-Tests grün)
- [x] Instrumentierte Tests **am 2026-08-12 auf einem SM-S928B gelaufen, alle 21 grün** (vorher 16:
  vier neue in `CalendarEventIntentTest`, einer im `TodoListScreenTest`). Die Canary-Gegenprobe ist
  mitgemacht: `theCalendarButtonInTheEditDialogReportsTheTitle` auf einen erfundenen Text gedreht
  scheitert mit „could not find any node that satisfies: (Text … contains …)" — der Matcher wird
  also gegen einen echten Baum ausgewertet, nicht an „No compose hierarchies found" vorbei.
  **Nebenbei gelernt:** Ein zweites, `offline` gemeldetes Gerät in `adb devices` überspringt Gradle
  von allein („Skipping device … Device is OFFLINE"); `ANDROID_SERIAL` zu setzen half dagegen
  nicht, sondern brach den Lauf mit „Connected device with serial … not found" ab
- [x] Auf dem Gerät geprüft (2026-08-12, SM-S928B) — Termin aus einer Aufgabe angelegt und im
  Gmail-Kalender wiedergefunden, der Intent landet also im richtigen Konto (das war der eigentliche
  Fallstrick, weil auf einem Galaxy zwei Apps ihn bedienen) · zurück in der App steht der Dialog
  noch offen und die Aufgabe unverändert da · Titel mit Umlauten kommen vollständig an.
  **Nicht angesehen: hell und dunkel.** Auf Wunsch trotzdem abgehakt — die Aktionszeile nimmt ihre
  Farben aus `TextButton` und `ColorSchemeContrastTest` deckt die Schemata ab, ein Blick darauf
  bleibt aber nachzuholen

### Phase 12 – Notiz an einer Aufgabe
> Ein Backlog-Eintrag lebt Wochen. Ein Titel allein hat dann oft verloren, was eigentlich gemeint
> war („Fenster abdichten" — welche, und was war der Plan?). Die Security Rules bleiben unberührt: auf
> `todos` gilt `read, write` für alle Mitglieder der Liste, ohne Feldprüfung.
- [x] `Todo` und `TodoDocument` um `notes: String?` erweitern, dazu `TodoField` — nullable, weil
  „keine Notiz" der Normalfall ist und bestehende Aufgaben das Feld nicht haben
- [x] **Bestehende Aufgaben ohne das Feld haben keine Notiz — kein Nachziehen in der Console.**
  `TodoDocument.notes` steht auf `null`, und Firestores `toObject` lässt ein fehlendes Feld genau
  darauf stehen. Anders als bei der Priorität braucht es **keinen Rückfallwert**: Dort war
  `TodoPriority` nicht-nullable, „fehlt" musste also zu „mittel" werden
  ([ADR 0023](docs/decisions/0023-prioritaet-migration-und-sortierung.md)) — hier ist null bereits
  die richtige Antwort. Ein `update()` legt das fehlende Feld beim ersten Speichern an; es scheitert
  nur an einem fehlenden *Dokument*. **Auf die Sortierung wirkt das Feld nicht**, `TODO_ORDER` fasst
  es nicht an
- [x] Eine gelöschte Notiz wird als `null` geschrieben, **nicht** mit `FieldValue.delete()` entfernt
  — beides liest sich gleich, aber so zerfällt der Bestand nicht in „Feld fehlt" und „Feld ist
  null". Der leere Puffer des Textfelds wird dafür im ViewModel zu null, an einer Stelle und vor der
  Verzweigung zwischen Ändern und Verschieben
- [x] Mehrzeiliges Feld im Bearbeiten-Dialog (`minLines = 3`, `maxLines = 5`); Titel, Priorität und
  Notiz gehen in **einem** Schreibvorgang raus, wie in
  [ADR 0025](docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md) entschieden. Der
  Dialog trägt damit fünf Dinge und bekam ein `verticalScroll`: Der `text`-Slot von `AlertDialog`
  begrenzt seine Höhe zwar, scrollt aber nicht von allein
- [x] In der Zeile nur angedeutet — **gekürzte zweite Zeile** (`supportingContent`, einzeilig mit
  Auslassungspunkten), kein Symbol. Warum, steht in
  [ADR 0030](docs/decisions/0030-notiz-als-zweite-zeile.md): Ein Symbol sagt nur, *dass* es etwas
  gibt, und müsste sich den `trailingContent` mit dem Prioritäts-Pfeil teilen. Erledigte Aufgaben
  zeigen die Notiz mit, das spart eine Regel
- [x] Beim Verschieben mitwandern lassen — das verlangt
  [ADR 0024](docs/decisions/0024-verschieben-behaelt-zustand.md) ausdrücklich für jedes neue Feld,
  das die Aufgabe beschreibt. Es hängt an **zwei** Stellen, und nur eine ist offensichtlich:
  `toDocument()` nimmt das Feld mit (das sichert der Hin-und-zurück-Test), **und `onEditConfirm`
  muss die Notiz aus dem Dialog auf den Snapshot überschreiben** — sonst reiste beim gleichzeitigen
  Verschieben und Ändern die *alte* Notiz mit und die getippte wäre verloren. Der Fall fällt durch
  keinen Mapper-Test auf und hat deshalb einen eigenen ViewModel-Test
- [x] Unit-Tests — **108 grün** (vorher 99). Mapper mit Notiz, **ohne das Feld** (der
  Migrationsfall) und mit leerem Feld, dazu ein Hin-und-zurück ohne das Feld für das Verschieben
  einer alten Aufgabe; im ViewModel die Vorbelegung, das Trimmen, das Leeren zu `null` und drei
  Verschieben-Fälle (Notiz mitgeführt · Notiz gleichzeitig geändert · Notiz gleichzeitig geleert)
- [x] Instrumentierte Tests **am 2026-08-12 auf einem SM-S928B gelaufen, alle 24 grün** (vorher 21:
  drei neue im `TodoListScreenTest` — Zeile mit Notiz · Zeile ohne Notiz hat keine zweite ·
  Notizfeld im Dialog meldet die Eingabe). Canary-Gegenprobe mitgemacht: `aRowShowsTheNotesBelowTheTitle`
  auf einen erfundenen Text gedreht scheitert mit „is not displayed" und aufgelöstem Matcher, nicht
  an „No compose hierarchies found".
  **Nebenbei gelernt:** Wird das Kabel während eines Laufs gezogen, bleibt die App halb installiert
  zurück und der nächste Versuch scheitert mit „Failed to install APK(s)" plus
  `DELETE_FAILED_INTERNAL_ERROR` beim Test-APK. Einfach erneut starten, sobald das Gerät wieder
  stabil hängt — es braucht kein Deinstallieren von Hand
- [x] Auf dem Gerät geprüft (2026-08-12, SM-S928B) — lange Notiz und eine mit Zeilenumbrüchen ·
  Dialog mit offener Tastatur, das `verticalScroll` greift · Zeile mit und ohne Notiz nebeneinander,
  offen und erledigt, hell und dunkel · alte Aufgabe ohne das Feld bearbeitet und ihr eine Notiz
  gegeben · Notiz gelöscht und beim Partner nachgesehen · Aufgabe mit Notiz verschoben, ohne und mit
  gleichzeitiger Änderung. **Damit ist auch die aus Phase 11 offene Hell/Dunkel-Sicht nachgeholt**

### Phase 13 – Nacharbeit aus dem Best-Practice-Durchgang (2026-08-12)
> Ergebnis eines Durchgangs durch Oberfläche und Code nach Phase 12, ohne Anlass in einem konkreten
> Fehler. Bewusst **nicht** kleinteilig: Was Linter und Formatierer ohnehin finden, steht hier nicht.
> Die Reihenfolge ist die nach Gewicht, nicht die der Umsetzung. Die ersten Punkte hängen zusammen,
> sind aber bewusst in mehrere Züge zerfallen: erst das Rückgängig
> ([ADR 0031](docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md)), dann die
> Knopfzeile ([ADR 0032](docs/decisions/0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md)) —
> die Wischschwelle bleibt offen, weil sie auf dem Gerät entschieden wird und nicht hier.

**Was der Durchgang ausdrücklich nicht beanstandet hat**, damit hier niemand nachträglich
„aufräumt": Die Schichtentrennung hält durchgehend, die `LazyColumn` hat `key = Todo::id` samt
`animateItem`, die Insets der Eingabeleiste sind sauber gelöst, jedes bedeutungstragende Symbol hat
eine `contentDescription`, Fehler laufen als `Result` und das ViewModel kennt keinen Android-Typ.

#### Löschen und die Knopfzeile des Bearbeiten-Dialogs
- [x] **Löschen war der am schwächsten geschützte Weg — ein Widerspruch in der eigenen Logik.**
  Wischen löscht nur *erledigte* Aufgaben und verlangt 85 % der Zeilenbreite, ausdrücklich weil
  „gelöscht ist endgültig" ([ADR 0016](docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md));
  eine *Liste* zu löschen hat einen Bestätigungsdialog mit Anzahl. Eine *Aufgabe* aus dem
  Bearbeiten-Dialog zu löschen kostete dagegen **einen Tipp ohne Rückfrage, direkt neben Speichern**,
  und funktionierte auch für offene Aufgaben. Der bequemste Weg war der ungeschützteste.
  **Umgesetzt ist (a): Snackbar „Aufgabe gelöscht" mit „Rückgängig"**, siehe
  [ADR 0031](docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md) — auf **beiden**
  Löschwegen, Dialog wie Wischgeste. Damit ist ADR 0016 in einem Punkt überholt („Kein Rückgängig.
  Dafür die hohe Schwelle."); es gibt jetzt beides.
  **Der befürchtete Preis fiel weg:** „Rückgängig" heißt bei Firestore neu anlegen — aber `set()`
  schreibt auf **dieselbe Dokument-id**, die im aufbewahrten `Todo` steht, und `Todo.toDocument()`
  gab es schon fürs Verschieben. Die Aufgabe kehrt mit `createdAt`, `completedAt`, Priorität und
  Notiz an ihre alte Stelle zurück; neu ist nur `updatedAt`, das vom Server kommt. Die Ausnahme aus
  [ADR 0026](docs/decisions/0026-verschieben-schreibt-createdat-selbst.md) ist damit nicht mehr auf
  das Verschieben beschränkt.
  Bewusst so und nicht anders: sofort schreiben statt verzögert (kein Timer, keine schwebende
  Löschung — die Maschinerie, die ADR 0016 verworfen hat) · zurückgelegt wird der **Snapshot**, nicht
  der Inhalt des offenen Dialogs · **Einzelslot**, nur die letzte Löschung ist umkehrbar · das
  Angebot **überlebt keinen Listenwechsel**, sonst schriebe es in die falsche Liste.
  Nebenbei: Die Snackbar-Rückrufe sind zum fünften Actions-Halter `SnackbarActions` geworden, statt
  als vier einzelne Parameter am Bildschirm zu stehen (ADR 0028 bleibt damit bei acht)
- [x] Unit-Tests zum Rückgängig — **117 grün** (vorher 108): das Angebot nach dem Löschen im Dialog
  und nach dem Wischen · dass es den **Snapshot** trägt und nicht den ungespeicherten Dialog-Inhalt ·
  Wiederherstellen unter der alten id · der Fehlschlag (`RESTORE_FAILED`, Angebot verfällt) · kein
  Angebot nach einem gescheiterten Löschen · kein Angebot, wenn der Partner den Eintrag schon
  entfernt hatte · und dass ein Listenwechsel das Angebot verwirft
- [x] Instrumentierte Tests **am 2026-08-12 auf einem SM-S928B gelaufen, alle 25 grün** (vorher 24:
  einer neu, `theUndoActionOfTheDeleteSnackbarReportsTheTap` — Snackbar da, der Tipp kommt als
  „Rückgängig" an, und der andere Rückruf läuft dabei *nicht*). Canary-Gegenprobe mitgemacht: auf
  einen erfundenen Text gedreht scheitert der Test mit „Assert failed: The component with Text +
  InputText + EditableText contains 'CANARY …' is not displayed!" — ein aufgelöster Matcher gegen
  einen echten Baum, nicht „No compose hierarchies found". Danach zurückgedreht und erneut 25 grün
- [x] Auf dem Gerät geprüft (2026-08-12, SM-S928B) — löschen und zurückholen, im Dialog **und** per
  Wischgeste · beim Partner nachgesehen, dass die Aufgabe an derselben Stelle wieder auftaucht (nicht
  oben) · eine **erledigte** Aufgabe zurückgeholt · zwei Löschungen schnell hintereinander · Liste
  gewechselt, während die Snackbar stand · offline gelöscht und zurückgeholt · Anzeigedauer,
  hell und dunkel.
  **Die vorsorgliche Rücksetzung des Wischzustands in `TodoRow` hat sich damit bewährt:** Eine
  weggewischte und zurückgeholte Zeile steht an ihrem Platz, keine leere Fläche. Ob es die Vorsorge
  wirklich gebraucht hätte, sagt der Lauf nicht — sie bleibt, weil der Fehlerfall unsichtbar wäre
- [x] **Das Verschwinden sichtbar machen** — aufgefallen beim Durchspielen: Die Zeile war *instantan*
  weg, was zu einem umkehrbaren Löschen nicht passt; wer nichts verschwinden sieht, greift auch nicht
  nach dem „Rückgängig". Jetzt blendet sie in 800 ms **linear** aus
  (`animateItem(fadeOutSpec = tween(...))`, eigene Dauer nur fürs Ausblenden — Einblenden und
  Verschieben behalten ihre Vorgabe-Federn).
  **Die Kurve war dabei wichtiger als die Zahl:** Mit 400 ms und der Vorgabe `FastOutSlowInEasing`
  blieb es „kaum sichtbar", weil diese Kurve am Anfang beschleunigt — nach einem Fünftel der Zeit
  stand die Deckkraft schon bei etwa der Hälfte, der Rest verstrich an einer unsichtbaren Zeile. Bei
  `LinearEasing` sagt die Dauer, wie lange man wirklich etwas sieht.
  **Kein Rot-Aufblitzen**, obwohl das die erste Idee war: Die verschwindende Zeile wird von der
  `LazyColumn` nur noch *gezeichnet*, nicht neu zusammengesetzt — eine Farbe ließe sich also nur
  ändern, indem die Aufgabe künstlich in der Liste gehalten und erst verzögert gelöscht wird. Das ist
  genau die schwebende Löschung samt Timer, die
  [ADR 0031](docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md) und
  [ADR 0016](docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md) verworfen haben. Rot gibt
  es beim Wischen ohnehin schon, dort trägt der Hintergrund die Farbe
- [ ] Die **85-%-Wischschwelle** aus ADR 0016 auf 50 % der Zeilenbreite senken — jetzt möglich, weil
  das Rückgängig das Netz spannt, das die hohe Schwelle ersetzen sollte. Absichtlich **nicht** in
  derselben Änderung: Das ist eine Entscheidung über das Gefühl der Geste und will auf dem Gerät
  erprobt werden, nicht am Schreibtisch entschieden. Bleibt vorerst offen; wenn sie kommt, bekommt
  sie einen eigenen ADR — [ADR 0031](docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md)
  hat ihr den ausdrücklich versprochen.
  **Hier stand „auf den Material-Standard von 50 %" — das war falsch und ist der Fallstrick des
  Punktes:** `SwipeToDismissBoxDefaults.positionalThreshold` ist in Material 3 1.4.0 **kein Anteil,
  sondern feste 56 dp**, auf einer 360 dp breiten Zeile also rund 15 %. Wer die eigene Lambda in
  `TodoRow` einfach weglässt, macht die Geste nicht standardkonform, sondern dreimal empfindlicher
  als hier gewollt. Der eigene Wert bleibt, nur die Zahl darin sinkt
- [x] **„Speichern" landete auf einer eigenen Zeile, und zwar aus einem benennbaren Grund.**
  `AlertDialog` legt `dismissButton` und `confirmButton` in eine gemeinsame `AlertDialogFlowRow`.
  Weil Löschen und Abbrechen in **einem** `Row` steckten, war das dort ein *unteilbares* Element:
  Passt `[Löschen Abbrechen] [Speichern]` nicht in eine Zeile, bricht der FlowRow vor Speichern um.
  Erledigt durch den Punkt darunter — **Löschen hat die Knopfzeile verlassen, damit löste es sich von
  selbst**, es bleibt die Standardanordnung `[Abbrechen] [Speichern]`, siehe
  [ADR 0032](docs/decisions/0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md). Die Erklärung
  bleibt hier stehen, damit niemand wieder zwei Knöpfe in einen Slot legt
- [x] **Löschen steht jetzt im Inhalt des Bearbeiten-Dialogs**, als zweite Aktionszeile unter „Termin
  anlegen" — gestapelt, nicht daneben (nebeneinander passt nicht: zwei Beschriftungen mit Symbol
  brauchen rund 270 dp, ein `AlertDialog` gibt auf einem 360-dp-Gerät etwa 256 dp Inhalt her, und
  `Row` bricht nicht um). Damit gilt allgemein, was der Kalender-Knopf bisher nur für sich
  beanspruchte: Die Knopfzeile trägt nur, was den Dialog beendet. Das schließt den Faden aus
  [ADR 0031](docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md) ab — der Fehlgriff
  neben „Speichern" ist nicht mehr möglich, und Löschen kostet trotzdem keinen Tipp mehr. Der Knopf
  bleibt, weil ADR 0016 den Dialog als den mit TalkBack bedienbaren Löschweg verlangt
- [x] **„Speichern" sah aus wie „Abbrechen"** — beide waren `TextButton`, nur Löschen hob sich über die
  Farbe ab. Jetzt ist **Bestätigen in allen vier Dialogen ein gefüllter `Button`**, Abbrechen bleibt
  `TextButton`. Die Gabel aus diesem Punkt ist entschieden: **`Button`, nicht `FilledTonalButton`** —
  der tonale färbt `secondaryContainer`, also genau die Rollen der *gewählten* Segment-Stufe im
  selben Dialog, „Speichern" und die gewählte Priorität sähen gleich aus.
  „Liste löschen?" bestätigt **gefüllt in `error`/`onError`**: Dort ist das Löschen die Hauptaktion,
  die Bremse ist der Dialog mit seiner Anzahl, und ein Listen-Löschen hat kein Rückgängig. Löschen im
  Bearbeiten-Dialog bleibt dagegen ein Textknopf in Fehlerfarbe — ein zweiter gefüllter Knopf würde
  mit „Speichern" um die Hauptaktion streiten. Alles in ADR 0032 begründet.
  **Nebenbei:** Die Fehlerfarbe sitzt jetzt über `textButtonColors(contentColor = …)` am Knopf statt
  am `Text` — nur so trägt das Symbol daneben dieselbe Farbe; der Druckeffekt ist damit ebenfalls rot
- [x] Tests — **117 Unit-Tests grün** (unverändert: das neue Farbpaar `onError` auf `error` reitet in
  den zwei bestehenden Schema-Tests von `ColorSchemeContrastTest` mit, es ist kein eigener Test) und
  **28 instrumentierte am 2026-08-12 auf einem SM-S928B, alle grün** (vorher 25). Die drei neuen
  füllen eine Lücke, die vorher keinem aufgefallen war: **kein einziger Test prüfte irgendeinen
  Bestätigen-, Abbrechen- oder Löschen-Knopf** — jetzt melden Löschen und Speichern ihren Tipp, und
  ein leerer Titel lässt „Speichern" abgeblendet und ungeklickt. Der Löschen-Test ist auf den Dialog
  eingegrenzt, weil „Löschen" auch die Bestätigung von `DeleteListDialog` beschriftet.
  Canary-Gegenprobe mitgemacht: auf einen erfundenen Text gedreht scheitert der Test mit „could not
  find any node that satisfies: ((hasAnyAncestorThat(IsDialog is defined)) && (Text … contains
  'CANARY …'))" — aufgelöster Matcher gegen einen echten Baum, nicht „No compose hierarchies found"
- [x] Auf dem Gerät prüfen — **der Umbruch ist weg** (`[Abbrechen] [Speichern]` in einer Zeile, auch
  bei großer Schrift; ein gefüllter Knopf ist rund 24 dp breiter als ein Textknopf) · alle vier
  Dialoge hell und dunkel, besonders die gefüllte Fläche im dunklen Schema — dort ist das Grün hell
  und könnte als Block wirken · „Liste löschen?" in `error`/`onError` · Bearbeiten-Dialog **mit
  offener Tastatur**: Löschen liegt jetzt im scrollenden Inhalt und kann aus dem Bild rutschen (für
  TalkBack unkritisch, der Fokus scrollt mit — für Sehende ist das die eigentliche Frage) · einmal
  TalkBack über den Löschen-Knopf

#### Zuschnitt des Bearbeiten-Dialogs
- [x] **Bearbeiten als eigener Bildschirm — entschieden: bleibt ein Dialog**, mit benannten Auslösern,
  bei denen gebaut wird. Die Abwägung steht in
  [ADR 0033](docs/decisions/0033-bearbeiten-bleibt-ein-dialog.md) und wird hier nicht wiederholt; kurz:
  ~133 Zeilen ViewModel-Umzug, drei neu zu entwerfende bildschirmübergreifende Kopplungen, **38
  angefasste Tests** und zwei im Repo präzedenzlose Mechanismen (`BackHandler`, `rememberSaveable`) —
  gegen ein Scrollen weniger. Die vier Mängel dieses Unterabschnitts lagen ohnehin *im* Dialog und
  kosteten rund 15 Zeilen
- [-] Den Bearbeiten-Bildschirm **bauen** — zurückgestellt, vorab genehmigt bei jedem einzelnen dieser
  Auslöser (Begründung je Punkt in ADR 0033): sechstes Eingabefeld · dritte Aktion im Inhalt ·
  `maxLines` an der Notiz muss steigen · der Geräteblick scheitert bei Schriftskalierung ≥ 1,3 mit
  offener Tastatur · ein zweiter Weg in den Editor (Deep-Link, Benachrichtigung, Widget) ·
  `TodoListViewModel` über ~600 Zeilen oder ein viertes Thema · die App bekommt aus anderem Grund
  Navigation
- [x] **`fillMaxWidth()` an allen vier Dialog-Textfeldern** — es waren vier über drei Dialoge, nicht
  „die beiden im Dialog": Titel und Notiz im Bearbeiten-Dialog, dazu die Namensfelder von
  `NewListDialog` und `RenameListDialog`. Der Vergleichspunkt „das Zielliste-Feld schon" ist übrigens
  entfallen — das war das selbstgebaute `Row`, das es nicht mehr gibt
- [x] **Die Beschriftungen sind vereinheitlicht — und die Diagnose hier war teilweise falsch.**
  Nachgemessen in den Material-Quellen 1.4.0: `AlertDialog` liefert im `text`-Slot bereits
  `bodyMedium` + `onSurfaceVariant` (`DialogTokens.SupportingTextFont`/`SupportingTextColor`). Das
  `style = bodyMedium` an „Priorität", „Liste" und den Radio-Zeilen war also **redundant**, und die
  Farbe hat sich *nie* unterschieden — es waren **zwei Größen, nicht drei Stile** (Kopf-Text 14 sp
  gegen verkleinerte Feld-Beschriftung 12 sp).
  Umgesetzt: Die **Zielliste ist jetzt ein echtes M3-Feld** (`ExposedDropdownMenuBox` mit
  schreibgeschütztem `OutlinedTextField` und `label`), damit bleibt „Priorität" der einzige Kopf-Text —
  und der trägt jetzt `bodySmall` samt 4-dp-Einzug, also die Geometrie, die Material selbst für eine
  Beschriftung über einem Feld benutzt. Die Gruppe Kopf + Steuerung ist ein `Column` mit 4 dp, die
  8 dp des äußeren `Column` trennen die Gruppen: Das löst den 4-gegen-8-dp-Vorwurf, ohne ein neues
  `dp`-Literal.
  **Nebenbei mitgenommen:** Das Feld bringt `Role.DropdownList`, eine 56-dp-Trefferfläche und einen
  stärkeren Riegel im Ein-Listen-Fall mit — `menuAnchor(enabled = false)` hängt gar keine
  Menü-Semantik an. Der Pfeil verliert seine `contentDescription` (die Steuerung ist das Feld), damit
  entfällt der String `todo_list_choose_target_list`
- [x] **`keyboardOptions` an allen vier Dialogfeldern**, nach einer Regel: `Sentences` überall
  (**nicht** `Words` — im Deutschen würde das Präpositionen großschreiben: „Zeug Für Oma"),
  `ImeAction.Done` an den drei einzeiligen Feldern, **keines** an der Notiz (dort verdrängt eine
  erzwungene Aktion die Zeilenumbruch-Taste). **Kein `keyboardActions`:** Die Enter-Taste schließt nur
  die Tastatur und speichert nicht — den Dialog beendet allein die Knopfzeile (ADR 0032). Die
  Eingabeleiste bleibt die bewusste Ausnahme, ihr Kommentar begründet das
- [x] Tests — **117 Unit-Tests grün** (unverändert, keiner betrifft Dialoglayout) und **29
  instrumentierte am 2026-08-12 auf einem SM-S928B, alle grün** (vorher 28: einer neu,
  `theTargetListFieldCarriesItsLabelAndTheCurrentList`; zwei umgezielt, weil sie das Feld jetzt über
  seine Beschriftung anfassen statt über den Pfeil, und `theListCannotBePickedWhileItIsTheOnlyOne`
  zusichert zusätzlich `assertIsNotEnabled` — **nicht** `assertHasNoClickAction`: Ein abgeblendetes
  Textfeld behält seinen `onClick` absichtlich und markiert ihn als `disabled`).
  **Die Canary-Gegenprobe hat hier mehr gezeigt als eine Gegenprobe:** Sie druckt die Semantik des
  Knotens, und die belegt den Entwurf auf dem Gerät — `Text = '[Liste]'` und `EditableText =
  'Einkauf'` in **einem** verschmolzenen Knoten, `IsEditable = 'false'`, und `Role = 'DropdownList'`.
  Damit ist die Barrierefreiheits-Verbesserung nachgewiesen und nicht bloß behauptet
- [x] **Nebenbefund beim Nachmessen, gleich mit behoben:** `OutlinedTextField` erbt seinen `textStyle`
  von `LocalTextStyle`, und im `text`-Slot eines Dialogs ist das `bodyMedium` — man tippte in den
  Dialogen also in **14 sp**, während dasselbe Feld in der Eingabeleiste 16 sp hat; im leeren Feld war
  die Beschriftung damit *größer* als der Text, den man dann tippt. Alle vier Felder setzen jetzt
  `bodyLarge`
- [ ] Auf dem Gerät prüfen — **das Aufklappmenü der Zielliste** ist das Neue: öffnet über dem Dialog,
  nicht abgeschnitten, auf Feldbreite, mit offener und geschlossener Tastatur. Besonders: **ein Tipp
  auf „Speichern" bei offenem Menü** — das `ExposedDropdownMenu` ist anders als das bisherige
  `DropdownMenu` **nicht berührungsmodal**, schließt der Tipp also nur das Menü oder speichert er
  zugleich? (Ausweg wäre `properties = PopupProperties(focusable = true)`) · der Fall „nur eine Liste"
  im **dunklen** Schema, wo ein abgeblendetes M3-Feld mit 38 % Alpha arbeitet, während das Projekt
  sonst deckendes `onSurfaceVariant` benutzt · Tastatur: erster Buchstabe groß in Titel und Notiz, die
  Enter-Taste im Titel heißt „Fertig" und **speichert nicht**, und **Enter in der Notiz erzeugt
  weiterhin einen Zeilenumbruch** — das ist die Regression, die diese Änderung einführen könnte · alle
  vier Dialoge bei Schriftskalierung 1,0 und größter, hell und dunkel.
  **Der letzte Punkt ist gleichzeitig Auslöser 4 aus ADR 0033** — hier fällt das Urteil, ob der Dialog
  weiter trägt

#### Kleinere Oberflächen-Punkte
- [ ] **Klick-Semantik fehlt am TopAppBar-Titel:** Er benutzt `Modifier.clickable` ohne `Role.Button`
  und ohne `onClickLabel`. Für TalkBack ist er damit „Text, antippbar" ohne Rolle. Die Klickfläche ist
  zudem nur so hoch wie der Text — in der TopAppBar praktisch groß genug, aber nicht durch
  `minimumInteractiveComponentSize` abgesichert.
  **Hier stand einmal „an zwei antippbaren Texten":** Das Zielliste-Feld ist der zweite gewesen und
  erledigt — als `ExposedDropdownMenuBox`-Feld bringt es Rolle, Klick-Semantik und 56 dp mit
  ([ADR 0033](docs/decisions/0033-bearbeiten-bleibt-ein-dialog.md))

#### Code
- [ ] **`TodoListUiState.error` ist ein Einzelslot.** Treffen zwei Fehler kurz hintereinander ein,
  überschreibt der zweite den ersten, bevor die Snackbar ihn gezeigt hat. Bei zwei Nutzern selten,
  aber echt — eine Warteschlange wäre die saubere Antwort, ein bewusstes „reicht uns" der billigere
  Weg. Dann aber als Kommentar am Feld, nicht als Zufall
- [ ] **`TodoListViewModel` ist bei 471 Zeilen und trägt drei Themen** (Listenwahl: ~211 Zeilen,
  Aufgaben-CRUD: ~56, Dialogzustände: ~133). Der Bildschirm wurde in Phase 11 entzerrt, das ViewModel
  nicht. Kein Fehler, aber der nächste Ort, an dem Wachstum wehtut.
  **Das Tor ist offen:** Hier stand „vor einem eigenen Umbau erst den Bildschirm-Punkt entscheiden" —
  der ist mit [ADR 0033](docs/decisions/0033-bearbeiten-bleibt-ein-dialog.md) entschieden. Der Punkt
  ist damit bearbeitbar, nimmt aber **kein** Drittel mit: Sortiert wird innerhalb eines ViewModels,
  nicht auf zwei verteilt. Umgekehrt ist „über ~600 Zeilen" jetzt selbst ein Auslöser für den
  Bildschirm
- [ ] `updateTodo` hat mit der Notiz **fünf Parameter, zwei davon `String`**. Die Aufrufstellen
  arbeiten benannt, das trägt. Kommt ein weiteres Feld dazu, ist ein Wertobjekt fällig statt eines
  sechsten Parameters
- [ ] **Abstände stehen als Literale** (`8.dp`, `16.dp`, `24.dp`, `32.dp`) über mehrere Dateien
  verteilt. `standards.instructions.md` nennt „duplicated dimensions instead of resources"
  ausdrücklich als SHOULD FIX. In Compose ist ein eigenes `Spacing`-Objekt im Theme üblicher als
  `dimens.xml`

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
- [x] **Zweiter Lauf am 2026-08-12, alle 16 grün** (SM-S928B per Kabel, `stay_on_while_plugged_in`
  war gesetzt) — dazu 97 Unit-Tests. Einer der acht neuen Tests aus Phase 9 und 10 fiel dabei durch,
  und zwar zu Recht: `theEditDialogShowsTheListTheEntryIsIn` prüfte mit `onNodeWithText(LIST.name)`,
  aber der Name der aktuellen Liste steht **zweimal** auf dem Bildschirm — im TopAppBar-Titel und im
  Zielliste-Feld des Dialogs. `onNodeWithText` scheitert bei zwei Treffern, statt sich einen
  auszusuchen. Jetzt auf den Dialog eingegrenzt (`hasAnyAncestor(isDialog()) and hasText(...)`), was
  auch die eigentliche Aussage des Tests ist. Der Nachbartest warnte übrigens schon vor genau dieser
  Mehrdeutigkeit — nur für den Fall mit offenem Menü, der TopAppBar-Titel war übersehen.
  **Lehre:** Ein Name, der in dieser App irgendwo im Dialog steht, steht oft auch in der TopAppBar.
  Bei Listennamen gehört die Eingrenzung auf den Dialog dazu, nicht die Suche nach dem bloßen Text
- [~] Prüfen, dass die `@`-Importe in `CLAUDE.md` und die Regeln in Android Studio tatsächlich laden
  (Canary-Methode, siehe ADR 0014)
- [x] `createAndroidComposeRule` auf `androidx.compose.ui.test.junit4.v2` umgestellt — am 2026-08-12
  erledigt, alle 16 Tests auf einem SM-S928B grün. Die Verfallswarnung ist damit weg und
  `@Suppress("DEPRECATION")` aus `TodoListScreenTest` verschwunden.
  **Ein reiner Import-Austausch hat gereicht** — die befürchtete Nacharbeit blieb aus: `v2` tauscht
  zwar die ganze `AndroidComposeUiTestEnvironment` und reiht die Komposition ein, statt sie sofort
  auszuführen, aber die Finder (`onNodeWithText` und Verwandte) synchronisieren von sich aus, bevor
  sie den Baum durchsuchen. Kein zusätzliches `waitForIdle()` nach `setContent` nötig; die beiden
  vorhandenen `waitForIdle()`-Aufrufe nach den Wischgesten bleiben, wo sie sind.
  **Der grüne Lauf allein wurde nicht als Beleg akzeptiert**, weil derselbe Import hier schon einmal
  alle Tests an „No compose hierarchies found" scheitern ließ: Zur Gegenprobe wurde eine Zusicherung
  absichtlich auf einen erfundenen Text gedreht. Sie scheiterte mit „is not displayed" und mit
  aufgelöstem `hasAnyAncestorThat(IsDialog is defined)` — die Komposition entsteht also und wird
  wirklich durchsucht. Diese Gegenprobe gehört bei jeder Änderung an der Regel wiederholt; der
  Hinweis steht jetzt am `@get:Rule` selbst
- [ ] `ExampleUnitTest` und `ExampleInstrumentedTest` löschen — unveränderte Projektvorlagen, die
  `2 + 2 == 4` und den Package-Namen prüfen. `remove-unused-code` ist MUST FIX, und sie zählen bei
  jedem „alle Tests grün" mit, ohne etwas abzusichern
- [x] `TodoListScreen` und den Bearbeiten-Dialog entzerren — am 2026-08-12 erledigt, vor Phase 11
  statt erst vor Phase 12. **27 Parameter → 8**, eine Datei mit 1067 Zeilen → fünf. Die Rückrufe
  reisen jetzt in vier `@Immutable data class`-Haltern, gruppiert nach ihrem Ort in der Oberfläche,
  siehe [ADR 0028](docs/decisions/0028-rueckrufe-in-actions-haltern.md). Damit kostet ein neues Feld
  am Dialog einen Eintrag im Halter statt drei Stellen — genau das, worauf
  [ADR 0022](docs/decisions/0022-verschieben-im-bearbeiten-dialog.md) und
  [ADR 0027](docs/decisions/0027-termine-per-kalender-intent.md) gezeigt haben.
  **Beim Anfassen zu beachten:** `@Immutable` und das `remember` in `MainActivity` sind kein Beiwerk.
  Vorher war der Bildschirm überspringbar, weil Methodenreferenzen sich strukturell vergleichen; ein
  Halter ohne `equals` hätte ihn bei jeder Rekomposition neu gezeichnet, ohne dass ein Test rot wird
  (Begründung im ADR). Standardwerte auf den Haltern gibt es aus demselben Grund nicht.
  Reiner Umbau: **97 Unit-Tests und 16 instrumentierte grün, ohne eine einzige angepasste
  Zusicherung**, dazu die Canary-Gegenprobe. `TodoListViewModel` und `TodoListUiState` blieben
  unangetastet.
  **Auf dem Gerät gegengeprüft:** Liste wechseln, Aufgabe anlegen, abhaken, wischen, Dialog öffnen
  und speichern — alles unverändert. Das war der Punkt, den kein Test abdeckt: Ein falsch gebauter
  Halter fällt nicht als roter Test auf, sondern als Ruckeln
- [x] Geteilte Run-Configuration für `:app:assembleRelease` — `.run/assembleRelease.run.xml`, damit
  der Release-Build in Android Studio ein Klick ist statt einer Kommandozeile. **Kein eigener
  Gradle-Task:** `assembleRelease` gibt es schon, ein `dependsOn`-Wrapper darauf wäre genau der
  Fall, den `avoid-unnecessary-wrappers` verbietet. Der Ordner heißt `.run/` und nicht
  `.idea/runConfigurations/`, weil `/.idea` in `.gitignore` steht — dort wäre die Konfiguration nur
  lokal vorhanden. Die Signatur-Zugangsdaten kommen unverändert aus `keystore.properties`
  ([ADR 0017](docs/decisions/0017-signatur-zugangsdaten-aus-keystore-properties.md)); fehlt die
  Datei, liefert auch dieser Klick eine unsignierte APK
- [x] Link-Ziele auf `.github/instructions` ohne Schrägstrich am Ende — mit ihm ist das letzte
  Pfadsegment leer, und Android Studio warnte „Cannot resolve file ''". Betraf `AGENTS.md`,
  `CLAUDE.md` und `README.md`; die Beschriftung behält den Schrägstrich, weil sie einen Ordner meint

---

## 4. Optionale / spätere Erweiterungen (nice to have)
- [ ] Push-Benachrichtigung, wenn der andere etwas ändert (Firebase Cloud Messaging)
- [ ] Fälligkeitsdaten — **nur als Anzeige, nicht als Auslöser.** Das Erinnern bleibt beim Google
  Kalender (Phase 11), siehe [ADR 0027](docs/decisions/0027-termine-per-kalender-intent.md): Ein
  eigener Erinnerungsweg wäre ein weiterer Benachrichtigungskanal auf Geräten, die genug davon haben
- [ ] Mehrere Aufgaben auf einmal verschieben — Auswahlmodus mit Häkchen und *einem* Ziel-Tipp. Die
  wöchentliche Besprechung ist heute vier Tipper pro Punkt; ein `WriteBatch` trägt bis 250 Aufgaben.
  Erst entscheiden, wenn ein paar Besprechungen zeigen, wie viele Punkte es wirklich pro Woche sind
- [ ] „Erledigte löschen" als eine Aktion im Überlauf-Menü — nach einer Woche steht unten ein Haufen
  abgehakter Zeilen, und einzeln wegwischen passt nicht zu einem wöchentlichen Ritual
- [ ] Wiederkehrende Aufgaben — **bewusst offen gelassen.** Sie brauchen einen Auslöser
  (WorkManager) und führen damit geradewegs zu dem Benachrichtigungsweg, den ADR 0027 vermeidet
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
- **Die Wochenlisten sind geteilte Listen, keine privaten.** Eine private Liste trägt nur die eigene
  uid in `members` und ist damit für den Partner unsichtbar *und* unbeschreibbar: Sie taucht in dessen
  `whereArrayContains`-Query nicht auf, und `isListMember` in `firestore.rules` lehnt den
  Schreibvorgang ab. In der wöchentlichen Besprechung ließe sich ein Backlog-Punkt also nur auf dem
  Gerät der Person selbst in ihre Woche übertragen. Als geteilte Listen („Woche <Name>") geht die
  ganze Besprechung von einem Gerät aus, und beide sehen, was der andere sich vorgenommen hat. Das ist
  eine **Vereinbarung über die Nutzung, keine Lücke** — die Abschottung privater Listen ist gewollt
  ([ADR 0019](docs/decisions/0019-schreibrechte-auf-listen-dokumente.md)) und bleibt für alles, was
  wirklich privat ist.
- **Zukunftssicherheit:** Business-Logik frei von Android-Framework-Abhängigkeiten halten,
  damit sie später via Kotlin Multiplatform auf iOS wiederverwendbar ist.
