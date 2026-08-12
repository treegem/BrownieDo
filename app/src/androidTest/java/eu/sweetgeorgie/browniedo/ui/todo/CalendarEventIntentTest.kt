package eu.sweetgeorgie.browniedo.ui.todo

import android.content.Intent
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft den Bau des Kalender-Intents, siehe docs/decisions/0027-termine-per-kalender-intent.md.
 *
 * Steht in `androidTest` statt in `test`, obwohl weder Gerätezustand noch Compose gebraucht werden:
 * `android.content.Intent` ist im reinen JVM-Test eine Attrappe und wirft „Stub!". Der einzige
 * Ausweg wäre Robolectric — eine Test-Abhängigkeit für genau diese Datei, und damit gegen
 * „Einfachheit vor Vollständigkeit" aus `ROADMAP.md` §1.
 */
@RunWith(AndroidJUnit4::class)
class CalendarEventIntentTest {

    @Test
    fun theIntentOpensTheCalendarWithTheTitle() {
        val intent = calendarEventIntent("Werkstatt anrufen")

        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals(
            "Werkstatt anrufen",
            intent.getStringExtra(CalendarContract.Events.TITLE)
        )
    }

    @Test
    fun theTitleSurvivesUmlauts() {
        val intent = calendarEventIntent("Straßenfest — Getränke mitbringen")

        assertEquals(
            "Straßenfest — Getränke mitbringen",
            intent.getStringExtra(CalendarContract.Events.TITLE)
        )
    }

    @Test
    fun theIntentCarriesNoTime() {
        val intent = calendarEventIntent("Zahnarzt")

        // Eine Aufgabe trägt keinen Zeitpunkt; eine geratene Stunde würde häufiger korrigiert als
        // übernommen und verdeckte dabei die Voreinstellung des Kalenders (ADR 0027).
        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME))
        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_END_TIME))
        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_ALL_DAY))
    }

    @Test
    fun theIntentIsBuiltWithoutAPackage() {
        // Den Vorrang für den Google Kalender setzt erst startCalendarEventInsert, und der zweite
        // Versuch baut deshalb neu, statt das Paket wieder zu entfernen.
        assertNull(calendarEventIntent("Zahnarzt").`package`)
    }
}
