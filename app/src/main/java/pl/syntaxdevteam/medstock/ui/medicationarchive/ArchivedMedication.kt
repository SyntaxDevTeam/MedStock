package pl.syntaxdevteam.medstock.ui.medicationarchive

data class ArchivedMedication(
    val id: Long,
    val originalMedicationId: Long,
    val name: String,
    val strength: String,
    val activeSubstance: String,
    val packageSize: String,
    val unit: String,
    val currentStock: Int,
    val dosage: String,
    val alertDays: Int,
    val archivedAtUtc: String,
)
