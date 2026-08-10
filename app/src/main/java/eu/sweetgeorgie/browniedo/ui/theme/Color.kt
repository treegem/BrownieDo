package eu.sweetgeorgie.browniedo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tonleitern der App-Farben, siehe
 * docs/decisions/0021-eigene-farbpalette-statt-dynamic-color.md.
 *
 * Die drei Grundtöne stammen aus dem App-Icon: das Grün des Hakens, das Braun des Häufchens und
 * das Rosa des Hintergrunds. Die Neutraltöne sind aus demselben Braun abgeleitet, fast entsättigt —
 * ein warmes Grau statt eines kalten.
 *
 * Die Zahl im Namen ist der Material-3-**Ton**, und der ist definitionsgemäß die CIELAB-Helligkeit
 * L*: `Green40` hat L* = 40. Daraus folgt die Regel, an der die Vorgängerfassung gescheitert ist —
 * im hellen Schema trägt eine Rolle den Ton 40, im dunklen den Ton 80. Wer hier Werte ändert, muss
 * `ColorSchemeContrastTest` erneut grün bekommen; der Test rechnet die Kontraste nach.
 */

// Grün — der Haken aus dem Icon. Trägt Knöpfe, Checkboxen und die aktive Liste.
val Green10 = Color(0xFF052100)
val Green20 = Color(0xFF003909)
val Green30 = Color(0xFF005312)
val Green40 = Color(0xFF006E1C)
val Green80 = Color(0xFF7ADC79)
val Green90 = Color(0xFF96F994)

// Braun — das Häufchen.
val Brown10 = Color(0xFF2C1700)
val Brown20 = Color(0xFF4D2600)
val Brown30 = Color(0xFF683C16)
val Brown40 = Color(0xFF84532C)
val Brown80 = Color(0xFFF6BA8E)
val Brown90 = Color(0xFFFFDCC3)

// Rosa — der Icon-Hintergrund.
val Rose10 = Color(0xFF31101E)
val Rose20 = Color(0xFF492533)
val Rose30 = Color(0xFF613C4A)
val Rose40 = Color(0xFF7A5362)
val Rose80 = Color(0xFFE8BACA)
val Rose90 = Color(0xFFFFD8E6)

// Warme Neutraltöne für Flächen und Schrift.
val Neutral0 = Color(0xFF000000)
val Neutral4 = Color(0xFF130D08)
val Neutral6 = Color(0xFF17120F)
val Neutral10 = Color(0xFF1F1B18)
val Neutral12 = Color(0xFF231F1C)
val Neutral17 = Color(0xFF2E2926)
val Neutral20 = Color(0xFF342F2C)
val Neutral22 = Color(0xFF383431)
val Neutral90 = Color(0xFFE7E1DD)
val Neutral92 = Color(0xFFEDE7E3)
val Neutral94 = Color(0xFFF3EDE9)
val Neutral95 = Color(0xFFF6F0EC)
val Neutral96 = Color(0xFFF9F2EE)
val Neutral98 = Color(0xFFFEF8F4)
val Neutral100 = Color(0xFFFFFFFF)

// Etwas kräftiger getönte Neutraltöne für Ränder und abgesetzte Flächen.
val NeutralVariant30 = Color(0xFF51443C)
val NeutralVariant50 = Color(0xFF82746B)
val NeutralVariant60 = Color(0xFF9C8E84)
val NeutralVariant80 = Color(0xFFD3C4B9)
val NeutralVariant90 = Color(0xFFF0E0D5)

/**
 * Rot für Fehler und destruktive Aktionen — die Standardwerte von Material 3, bewusst nicht selbst
 * erfunden: Sie sind erprobt, und ihr Abstand zum Grün ist mehr als groß genug (ΔE 75 im dunklen,
 * 100 im hellen Schema).
 */
val Red10 = Color(0xFF410E0B)
val Red20 = Color(0xFF601410)
val Red30 = Color(0xFF8C1D18)
val Red40 = Color(0xFFB3261E)
val Red80 = Color(0xFFF2B8B5)
val Red90 = Color(0xFFF9DEDC)
