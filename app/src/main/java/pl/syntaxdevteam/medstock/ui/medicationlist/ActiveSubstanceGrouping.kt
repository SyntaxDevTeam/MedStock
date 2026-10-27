package pl.syntaxdevteam.medstock.ui.medicationlist

object ActiveSubstanceGrouping {

    // Only offer stock addition for the same active substance, strength and stock unit, regardless of brand.
    // Unknown strength or unit must never be treated as proof of compatibility.
    fun findMatches(rawActiveSubstance: String, rawStrength: String,
                    rawUnit: String, medications: List<UserMedication>): List<UserMedication> {
        val normalizedActiveSubstance = normalize(rawActiveSubstance)
        val strength = normalize(rawStrength).replace(',', '.').replace(Regex("\\s+"), "")
        val unit = normalizeStockUnit(rawUnit)
        if (normalizedActiveSubstance.isBlank() || strength.isBlank() || unit.isBlank()) return emptyList()
        return medications.filter { medication ->
            normalize(medication.activeSubstance) == normalizedActiveSubstance &&
                normalize(medication.strength).replace(',', '.').replace(Regex("\\s+"), "") == strength &&
                normalizeStockUnit(medication.unit) == unit
        }
    }

    private fun normalizeStockUnit(rawUnit: String): String {
        return when (val unit = normalize(MedicationUnitFormatter.abbreviate(rawUnit))) {
            "tabl.", "tabs." -> "tablet"
            else -> unit
        }
    }

    fun normalize(rawActiveSubstance: String): String {
        return rawActiveSubstance
            .trim()
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
    }
}
