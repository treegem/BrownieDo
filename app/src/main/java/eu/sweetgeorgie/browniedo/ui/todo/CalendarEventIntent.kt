package eu.sweetgeorgie.browniedo.ui.todo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * Aus einer Aufgabe einen Termin machen — der ganze Vorgang, siehe
 * docs/decisions/0027-termine-per-kalender-intent.md.
 *
 * Bewusst in der UI-Schicht: `Intent` und `CalendarContract` sind Android-Framework und bleiben
 * damit aus der Logik-Schicht heraus, die laut ROADMAP.md §5 KMP-fähig bleiben soll. Es gibt hier
 * weder Zustand noch eine Regel, die ins ViewModel gehörte.
 */

/** Der Google Kalender bekommt den Vorzug — er schreibt in die Gmail-Konten der beiden. */
internal const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"

/**
 * Der Intent, der den Anlegen-Bildschirm der Kalender-App mit vorbelegtem Titel öffnet.
 *
 * **Ohne Zeitpunkt.** Eine Aufgabe trägt keinen, und eine geratene Stunde würde häufiger korrigiert
 * als übernommen — dazu verdeckt sie die Voreinstellung des Kalenders (ADR 0027).
 */
internal fun calendarEventIntent(title: String): Intent =
    Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)

/**
 * Öffnet den Kalender mit vorbelegtem Titel.
 *
 * Erst mit [GOOGLE_CALENDAR_PACKAGE], dann ohne: Auf einem Galaxy bedienen zwei Apps diesen Intent,
 * und der Samsung Kalender kann in ein lokales Konto schreiben, das nie bei Google auftaucht. Der
 * zweite Versuch verhindert trotzdem, dass die Aktion ins Leere läuft, falls der Google Kalender
 * einmal fehlt oder deaktiviert ist — und er braucht einen **frisch gebauten** Intent, weil das
 * Paket sonst gesetzt bliebe.
 *
 * Kein `resolveActivity`-Vorabtest: Seit Android 11 liefert der ohne `<queries>`-Eintrag im Manifest
 * `null`, obwohl die Kalender-App installiert ist. `startActivity` selbst ist davon nicht betroffen,
 * `try`/`catch` ist damit der kürzere *und* der korrekte Weg (ADR 0027).
 *
 * @return false, wenn weder der Google Kalender noch eine andere App den Intent bedient.
 */
internal fun startCalendarEventInsert(context: Context, title: String): Boolean =
    try {
        context.startActivity(calendarEventIntent(title).setPackage(GOOGLE_CALENDAR_PACKAGE))
        true
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(calendarEventIntent(title))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
