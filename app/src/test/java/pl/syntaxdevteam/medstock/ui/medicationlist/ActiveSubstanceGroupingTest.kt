package pl.syntaxdevteam.medstock.ui.medicationlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSubstanceGroupingTest {

    @Test
    fun normalizeCollapsesWhitespaceAndIgnoresCase() {
        assertEquals(
            "bisoprolol fumarate",
            ActiveSubstanceGrouping.normalize("  Bisoprolol\n\tFUMARATE  ")
        )
    }

    @Test
    fun findMatchesRequiresExactNormalizedActiveSubstance() {
        val medications = listOf(
            medication(id = 1, name = "Bisocard", activeSubstance = "Bisoprolol fumarate"),
            medication(id = 2, name = "Ibuprofen", activeSubstance = "Ibuprofen"),
            medication(id = 3, name = "Bibloc", activeSubstance = "  bisoprolol   fumarate  "),
        )

        val matches = ActiveSubstanceGrouping.findMatches("BISOPROLOL FUMARATE", "Bibloc", "5 mg", "tabs.", medications)

        assertEquals(listOf(3L), matches.map { it.id })
    }

    @Test
    fun findMatchesIgnoresBlankSubstances() {
        val medications = listOf(medication(id = 1, name = "Blank", activeSubstance = ""))

        assertTrue(ActiveSubstanceGrouping.findMatches("   ", "Blank", "5 mg", "tabs.", medications).isEmpty())
    }

    @Test fun differentStrengthsAndUnitsNeverShareStock() {
        val bibloc = medication(1, "Bibloc", "Bisoprolol fumarate").copy(strength = "10 mg", currentStock = 5)
        val medications = listOf(bibloc)
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "Bibloc", "2,5 mg", "tabs.", medications).isEmpty())
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "Bibloc", "10 mg", "ml", medications).isEmpty())
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "Bibloc", "", "tabs.", medications).isEmpty())
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "Bibloc", "10 mg", "", medications).isEmpty())
        val decimal = bibloc.copy(strength = "2.5 mg")
        assertEquals(listOf(decimal), ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "Bibloc", "2,5mg", "tabs.", listOf(decimal)))
    }

    private fun medication(
        id: Long,
        name: String,
        activeSubstance: String,
    ): UserMedication {
        return UserMedication(
            id = id,
            name = name,
            strength = "5 mg",
            activeSubstance = activeSubstance,
            packageSize = "30",
            unit = "tabs.",
            currentStock = 10,
            dosage = "1||",
            alertDays = 7,
        )
    }
}
