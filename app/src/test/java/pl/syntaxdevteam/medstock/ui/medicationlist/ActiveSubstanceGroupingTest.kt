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
    fun findMatchesAcrossBrandsWithExactNormalizedActiveSubstance() {
        val medications = listOf(
            medication(id = 1, name = "Bisocard", activeSubstance = "Bisoprolol fumarate"),
            medication(id = 2, name = "Ibuprofen", activeSubstance = "Ibuprofen"),
            medication(id = 3, name = "Bibloc", activeSubstance = "  bisoprolol   fumarate  "),
        )

        val matches = ActiveSubstanceGrouping.findMatches("BISOPROLOL FUMARATE", "5 mg", "tabs.", medications)

        assertEquals(listOf(1L, 3L), matches.map { it.id })
    }

    @Test
    fun findMatchesIgnoresBlankSubstances() {
        val medications = listOf(medication(id = 1, name = "Blank", activeSubstance = ""))

        assertTrue(ActiveSubstanceGrouping.findMatches("   ", "5 mg", "tabs.", medications).isEmpty())
    }

    @Test fun differentStrengthsAndUnitsNeverShareStock() {
        val bibloc = medication(1, "Bibloc", "Bisoprolol fumarate").copy(strength = "10 mg", currentStock = 5)
        val medications = listOf(bibloc)
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "2,5 mg", "tabs.", medications).isEmpty())
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "10 mg", "ml", medications).isEmpty())
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "", "tabs.", medications).isEmpty())
        assertTrue(ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "10 mg", "", medications).isEmpty())
        val decimal = bibloc.copy(strength = "2.5 mg")
        assertEquals(listOf(decimal), ActiveSubstanceGrouping.findMatches("Bisoprolol fumarate", "2,5mg", "tabs.", listOf(decimal)))
    }

    @Test
    fun tabletUnitAliasesMatchAcrossBrands() {
        val existing = medication(1, "Bisocard", "Bisoprolol fumarate").copy(unit = "tabl.")
        for (unit in listOf("tabs.", "tablets", "tabletki", "tabl.")) {
            assertEquals(listOf(existing), ActiveSubstanceGrouping.findMatches(
                "bisoprolol fumarate", "5 mg", unit, listOf(existing)
            ))
        }
    }

    @Test
    fun missingExistingStrengthOrUnitDoesNotMatch() {
        val existing = medication(1, "Bisocard", "Bisoprolol fumarate")
        for (incomplete in listOf(existing.copy(strength = ""), existing.copy(unit = ""))) {
            assertTrue(ActiveSubstanceGrouping.findMatches(
                "bisoprolol fumarate", "5 mg", "tabs.", listOf(incomplete)
            ).isEmpty())
        }
    }

    @Test
    fun combinationProductDoesNotMatchSingleSubstance() {
        val existing = medication(1, "Combination", "Ibuprofen + Paracetamol")
        assertTrue(ActiveSubstanceGrouping.findMatches(
            "Ibuprofen", "5 mg", "tabs.", listOf(existing)
        ).isEmpty())
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
