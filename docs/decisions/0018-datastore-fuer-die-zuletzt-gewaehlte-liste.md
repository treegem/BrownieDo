# 0018 – DataStore für die zuletzt gewählte Liste

**Status:** akzeptiert · **Datum:** 2026-08-10

## Kontext

Mit Phase 8a lässt sich zwischen mehreren Listen wechseln. Die Wahl soll einen App-Neustart
überleben — sonst landet man jedes Mal wieder in derselben Liste und muss neu umschalten.

Das Projekt hatte bis dahin **keinerlei lokale Persistenz**: keine SharedPreferences, kein DataStore,
kein Room. Alles lag in Firestore. Es gab also keinen bestehenden Mechanismus, an den man sich
hängen konnte, und die Wahl war offen.

Erschwerend kommt eine Regel des Projekts dazu: `conventions.instructions.md` führt blockierende I/O
im Hauptthread als **MUST FIX**. Das schließt den naheliegendsten Weg nicht aus, macht ihn aber
teurer, als er aussieht.

## Entscheidung

Die zuletzt gewählte Liste steht in einem **Preferences-DataStore** unter einem einzigen
String-Schlüssel `selected_list_id`, gekapselt hinter `SelectedListRepository` im Domänen-Paket.

Gespeichert wird **pro Gerät**, nicht pro Nutzer. Welche Liste gerade offen ist, ist eine
Ansichtseinstellung und keine geteilte Information — es gibt keinen Grund, warum das Handy des
Partners derselben Wahl folgen sollte.

Die gespeicherte Id wird **ungeprüft** abgelegt. Ob die Liste noch existiert, entscheidet erst das
ViewModel, das die aktuellen Listen ohnehin zur Hand hat: Es fällt auf die erste Liste nach Namen
zurück. Diese eine Regel deckt beide Fälle ab — es wurde noch nie etwas gewählt, und die gemerkte
Liste ist inzwischen weg.

## Konsequenzen

- Eine neue Abhängigkeit: `androidx.datastore:datastore-preferences`.
- Der `Flow<String?>` fügt sich ohne Umweg in den `flatMapLatest`-Aufbau des ViewModels — die
  Auswahl ist damit dieselbe Art Datenquelle wie die Listen selbst.
- Kein Zugriff im Hauptthread, die MUST-FIX-Regel bleibt eingehalten, ohne dass jemand daran denken
  muss.
- **Die Wahl gilt pro Gerät.** Wer auf zwei Geräten arbeitet, findet dort unterschiedliche Listen
  offen. Für zwei Personen mit je einem Handy ist das folgenlos.
- Ein Preferences-DataStore ist typlos (`String`-Schlüssel auf `String`-Wert). Sobald mehr als diese
  eine Einstellung dazukommt, lohnt die Frage nach einem typisierten DataStore erneut.

## Alternativen

- **SharedPreferences:** Kommt ohne neue Abhängigkeit aus und ist in rund 15 Zeilen geschrieben. Die
  API ist aber synchron, und der erste Zugriff liest von der Platte — im Hauptthread wäre das genau
  der MUST-FIX-Verstoß. Sauber gelöst bräuchte es eine Coroutine auf `Dispatchers.IO` plus eine
  Flow-Hülle, also einen Nachbau dessen, was DataStore mitbringt. Die eingesparte Abhängigkeit wird
  mit selbstgeschriebener Nebenläufigkeit bezahlt.
- **Ein Feld an `users/{uid}` in Firestore:** Bräuchte ebenfalls keine neue Abhängigkeit und würde
  die Wahl zwischen den Geräten eines Nutzers synchronisieren. Kostet aber eine neue Top-Level-
  Collection samt eigener Security Rule — und zieht damit die Regeländerung, die bewusst nach Phase
  8b geschoben wurde, in 8a zurück. Für eine Ansichtseinstellung ist das der falsche Ort: Sie wäre
  dann Teil der geteilten Daten.
- **Gar nicht merken:** Der geringste Aufwand. Bei einer Liste, die man mehrmals täglich öffnet,
  wäre das jedes Mal ein zusätzlicher Griff.
- **Die Gültigkeit der gemerkten Id beim Speichern prüfen:** Würde nichts helfen. Die Liste kann
  zwischen Speichern und Lesen verschwinden — der Partner löscht sie ab Phase 8b aus der App. Die
  Prüfung gehört dorthin, wo die aktuellen Listen bekannt sind.
