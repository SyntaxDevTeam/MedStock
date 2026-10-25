package pl.syntaxdevteam.medstock.ui.medicationlist

object ActiveSubstanceGrouping {

    // Only offer stock addition for the same product, strength and stock unit.
    // Unknown strength or unit must never be treated as proof of compatibility.
    fun findMatches(rawActiveSubstance: String, rawName: String, rawStrength: String,
                    rawUnit: String, medications: List<UserMedication>): List<UserMedication> {
        val normalizedActiveSubstance = normalize(rawActiveSubstance)
        val strength = normalize(rawStrength).replace(',', '.').replace(Regex("\\s+"), "")
        val unit = normalize(MedicationUnitFormatter.abbreviate(rawUnit))
        if (normalizedActiveSubstance.isBlank() || rawName.isBlank() || strength.isBlank() || unit.isBlank()) return emptyList()
        return medications.filter { medication ->
            normalize(medication.activeSubstance) == normalizedActiveSubstance &&
                normalize(medication.name) == normalize(rawName) &&
                normalize(medication.strength).replace(',', '.').replace(Regex("\\s+"), "") == strength &&
                normalize(MedicationUnitFormatter.abbreviate(medication.unit)) == unit
        }
    }

    fun normalize(rawActiveSubstance: String): String {
        return rawActiveSubstance
            .trim()
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
    }
}
