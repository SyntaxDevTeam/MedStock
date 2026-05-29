package pl.syntaxdevteam.medstock.ui.medicationlist

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationCatalogSuggestionRepositoryTest {

    @Test
    fun buildDisplayNameUsesNameStrengthAndParsedPackage() {
        val packageInfo = MedicationCatalogSuggestionRepository.extractPackageInfo("30 tabl. | Rp | 20504")

        assertEquals("30", packageInfo.size)
        assertEquals("tabl", packageInfo.unit)
        assertEquals("30 tabl", packageInfo.displayPackage)
        assertEquals(
            "Atoris (20 mg 30 tabl)",
            MedicationCatalogSuggestionRepository.buildDisplayName("Atoris", "20 mg", packageInfo.displayPackage)
        )
    }
}
