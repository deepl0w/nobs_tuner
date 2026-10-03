package io.github.deeplow.nobstuner.model

import io.github.deeplow.nobstuner.model.InstrumentFamily.BANJO
import io.github.deeplow.nobstuner.model.InstrumentFamily.BASS
import io.github.deeplow.nobstuner.model.InstrumentFamily.GUITAR
import io.github.deeplow.nobstuner.model.InstrumentFamily.MANDOLIN
import io.github.deeplow.nobstuner.model.InstrumentFamily.ORCHESTRAL
import io.github.deeplow.nobstuner.model.InstrumentFamily.OTHER
import io.github.deeplow.nobstuner.model.InstrumentFamily.UKULELE

/**
 * The built-in tuning presets.
 *
 * Ids are stable identifiers that end up persisted in the favourites set, so
 * they must never be renamed once shipped — change the [Tuning.name] instead.
 * Strings are listed low-numbered-string-first as a player counts them, which
 * for re-entrant tunings (ukulele, 5-string banjo) is not the same as ascending
 * pitch order.
 */
object TuningCatalog {

    private fun t(id: String, name: String, family: InstrumentFamily, vararg strings: Int) =
        Tuning(id = id, name = name, family = family, strings = strings.toList())

    val presets: List<Tuning> = listOf(
        // ---- Guitar ------------------------------------------------------
        t("guitar_standard", "Standard", GUITAR, 40, 45, 50, 55, 59, 64),
        t("guitar_drop_d", "Drop D", GUITAR, 38, 45, 50, 55, 59, 64),
        t("guitar_double_drop_d", "Double Drop D", GUITAR, 38, 45, 50, 55, 59, 62),
        t("guitar_half_step_down", "Half Step Down (E♭)", GUITAR, 39, 44, 49, 54, 58, 63),
        t("guitar_whole_step_down", "Whole Step Down (D)", GUITAR, 38, 43, 48, 53, 57, 62),
        t("guitar_drop_c_sharp", "Drop C♯", GUITAR, 37, 44, 49, 54, 58, 63),
        t("guitar_drop_c", "Drop C", GUITAR, 36, 43, 48, 53, 57, 62),
        t("guitar_drop_b", "Drop B", GUITAR, 35, 42, 47, 52, 56, 61),
        t("guitar_drop_a", "Drop A", GUITAR, 33, 40, 45, 50, 54, 59),
        t("guitar_dadgad", "DADGAD", GUITAR, 38, 45, 50, 55, 57, 62),
        t("guitar_open_d", "Open D", GUITAR, 38, 45, 50, 54, 57, 62),
        t("guitar_open_d_minor", "Open D Minor", GUITAR, 38, 45, 50, 53, 57, 62),
        t("guitar_open_g", "Open G", GUITAR, 38, 43, 50, 55, 59, 62),
        t("guitar_open_g_minor", "Open G Minor", GUITAR, 38, 43, 50, 55, 58, 62),
        t("guitar_open_c", "Open C", GUITAR, 36, 43, 48, 55, 60, 64),
        t("guitar_open_e", "Open E", GUITAR, 40, 47, 52, 56, 59, 64),
        t("guitar_open_a", "Open A", GUITAR, 40, 45, 52, 57, 61, 64),
        t("guitar_all_fourths", "All Fourths", GUITAR, 40, 45, 50, 55, 60, 65),
        t("guitar_nst", "New Standard Tuning", GUITAR, 36, 43, 50, 57, 64, 67),
        t("guitar_guitalele", "Guitalele (A D G C E A)", GUITAR, 45, 50, 55, 60, 64, 69),
        t("guitar_7_standard", "7-String Standard", GUITAR, 35, 40, 45, 50, 55, 59, 64),
        t("guitar_7_drop_a", "7-String Drop A", GUITAR, 33, 40, 45, 50, 55, 59, 64),
        t("guitar_8_standard", "8-String Standard", GUITAR, 30, 35, 40, 45, 50, 55, 59, 64),

        // ---- Bass --------------------------------------------------------
        t("bass_standard", "4-String Standard", BASS, 28, 33, 38, 43),
        t("bass_drop_d", "4-String Drop D", BASS, 26, 33, 38, 43),
        t("bass_half_step_down", "Half Step Down (E♭)", BASS, 27, 32, 37, 42),
        t("bass_whole_step_down", "Whole Step Down (D)", BASS, 26, 31, 36, 41),
        t("bass_5_standard", "5-String (Low B)", BASS, 23, 28, 33, 38, 43),
        t("bass_5_tenor", "5-String (High C)", BASS, 28, 33, 38, 43, 48),
        t("bass_6_standard", "6-String Standard", BASS, 23, 28, 33, 38, 43, 48),
        t("bass_tenor", "Tenor Bass (A D G C)", BASS, 33, 38, 43, 48),

        // ---- Ukulele -----------------------------------------------------
        t("uke_standard", "Standard (High G)", UKULELE, 67, 60, 64, 69),
        t("uke_low_g", "Low G", UKULELE, 55, 60, 64, 69),
        t("uke_baritone", "Baritone (D G B E)", UKULELE, 50, 55, 59, 64),
        t("uke_soprano_d", "Soprano D (A D F♯ B)", UKULELE, 69, 62, 66, 71),
        t("uke_bass", "Bass Ukulele", UKULELE, 28, 33, 38, 43),

        // ---- Banjo -------------------------------------------------------
        t("banjo_open_g", "5-String Open G", BANJO, 67, 50, 55, 59, 62),
        t("banjo_double_c", "Double C", BANJO, 67, 48, 55, 60, 62),
        t("banjo_sawmill", "Sawmill / Mountain Modal", BANJO, 67, 50, 55, 60, 62),
        t("banjo_open_d", "5-String Open D", BANJO, 66, 50, 57, 62, 66),
        t("banjo_tenor", "Tenor (C G D A)", BANJO, 48, 55, 62, 69),
        t("banjo_irish_tenor", "Irish Tenor (G D A E)", BANJO, 43, 50, 57, 64),
        t("banjo_plectrum", "Plectrum (C G B D)", BANJO, 48, 55, 59, 62),

        // ---- Mandolin family ---------------------------------------------
        t("mandolin_standard", "Mandolin (G D A E)", MANDOLIN, 55, 62, 69, 76),
        t("mandola_standard", "Mandola (C G D A)", MANDOLIN, 48, 55, 62, 69),
        t("octave_mandolin", "Octave Mandolin (G D A E)", MANDOLIN, 43, 50, 57, 64),
        t("mandocello", "Mandocello (C G D A)", MANDOLIN, 36, 43, 50, 57),

        // ---- Orchestral strings ------------------------------------------
        t("violin_standard", "Violin (G D A E)", ORCHESTRAL, 55, 62, 69, 76),
        t("viola_standard", "Viola (C G D A)", ORCHESTRAL, 48, 55, 62, 69),
        t("cello_standard", "Cello (C G D A)", ORCHESTRAL, 36, 43, 50, 57),
        t("double_bass_standard", "Double Bass (E A D G)", ORCHESTRAL, 28, 33, 38, 43),
        t("double_bass_5", "Double Bass 5-String (Low B)", ORCHESTRAL, 23, 28, 33, 38, 43),

        // ---- Everything else ---------------------------------------------
        t("bouzouki_gdad", "Irish Bouzouki (G D A D)", OTHER, 43, 50, 57, 62),
        t("bouzouki_gdae", "Irish Bouzouki (G D A E)", OTHER, 43, 50, 57, 64),
        t("dobro_open_g", "Resonator / Dobro Open G", OTHER, 43, 47, 50, 55, 59, 62),
        t("dobro_open_d", "Resonator / Dobro Open D", OTHER, 38, 45, 50, 54, 57, 62),
        t("lap_steel_c6", "Lap Steel C6", OTHER, 48, 52, 55, 57, 60, 64),
        t("cigar_box_gdg", "Cigar Box (G D G)", OTHER, 43, 50, 55),
        t("cigar_box_dad", "Cigar Box (D A D)", OTHER, 50, 57, 62),
        t("balalaika_prima", "Balalaika Prima (E E A)", OTHER, 64, 64, 69),
        t("cuatro_venezuelan", "Venezuelan Cuatro (A D F♯ B)", OTHER, 69, 62, 66, 71),
    )

    private val byId: Map<String, Tuning> = presets.associateBy { it.id }

    fun findById(id: String): Tuning? = byId[id]

    /** The tuning selected the very first time the app is opened. */
    val default: Tuning get() = byId.getValue("guitar_standard")

    fun byFamily(family: InstrumentFamily): List<Tuning> = presets.filter { it.family == family }
}
