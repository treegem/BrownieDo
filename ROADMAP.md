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
| IDE | **Android Studio** + GitHub Copilot | Volles Android-Tooling out-of-the-box. |
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

> **Legende:** `[ ]` offen · `[~]` in Arbeit · `[x]` erledigt

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
- [x] Datenmodell definieren: `Todo` (id, titel, erledigt, erstelltAm, updatedAt, ggf. erledigtVon)
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
- [ ] Offline-Szenario testen: einer offline ändern → wieder online → synct beim anderen
- [ ] Konflikt-Verhalten prüfen (Last-Write-Wins über `updatedAt`)

### Phase 6 – UI-Feinschliff
- [ ] Aufgeräumtes Layout (Material 3)
- [ ] Leerer Zustand („noch keine Aufgaben") & Ladezustand
- [ ] Optional: Aufgaben sortieren (offen oben, erledigt unten)
- [ ] Optional: App-Icon & Name anpassen
- [ ] Optional: Dark Mode prüfen

### Phase 7 – Test & Verteilung an euch zwei
- [ ] Auf beiden Galaxy-Phones testen
- [ ] Signierte APK / App Bundle bauen (Keystore NICHT committen!)
- [ ] **SHA-1 des Release-Keystores in der Firebase Console hinterlegen** — sonst schlägt der
  Google-Login in der signierten APK fehl (`google-services.json` danach neu herunterladen)
- [ ] Direkt auf beide Geräte installieren (Sideload)

### Phase 8 – Mehrere Listen (geteilt & privat)
> Die Datenstruktur dafür steht bereits seit Phase 3, es fehlt nur die Bedienung.
- [ ] Listen des angemeldeten Nutzers laden (`lists`, gefiltert über `members`)
- [ ] Zwischen Listen umschalten (zuletzt gewählte Liste merken)
- [ ] Liste anlegen (privat = nur eigene uid, geteilt = beide uids) und umbenennen
- [ ] Liste löschen inklusive ihrer `todos`-Sub-Collection (Firestore löscht nicht kaskadierend)
- [ ] Security Rules auf das Anlegen/Ändern von Listen durch die App erweitern

---

## 4. Optionale / spätere Erweiterungen (nice to have)
- [ ] Push-Benachrichtigung, wenn der andere etwas ändert (Firebase Cloud Messaging)
- [ ] Fälligkeitsdaten / Erinnerungen
- [ ] Play-Store-Veröffentlichung (Developer-Account, Store-Eintrag, Datenschutzerklärung)
- [ ] iOS-Version via Kotlin Multiplatform (Logik-Schicht wiederverwenden)

---

## 5. Projektspezifische Vorgaben

Die allgemeinen Coding-, Architektur- und Naming-Regeln stehen in `.github/instructions/`
und werden vom Coding-Agent automatisch geladen. Die Begründungen hinter den technischen
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
