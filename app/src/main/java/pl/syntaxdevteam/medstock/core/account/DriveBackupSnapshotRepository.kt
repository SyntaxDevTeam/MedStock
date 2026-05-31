package pl.syntaxdevteam.medstock.core.account

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.syntaxdevteam.medstock.core.download.UserMedicationRepository
import java.io.File

class DriveBackupSnapshotRepository(context: Context) {

    private val appContext = context.applicationContext
    private val medicationRepository = UserMedicationRepository(appContext)

    fun createSnapshotFile(accountEmail: String): File {
        val medications = medicationRepository.getAll()
        val payload = JSONObject().apply {
            put("format", SNAPSHOT_FORMAT)
            put("accountEmail", accountEmail)
            put("createdAtUtc", System.currentTimeMillis())
            put("medications", JSONArray().apply {
                medications.forEach { medication ->
                    put(JSONObject().apply {
                        put("id", medication.id)
                        put("name", medication.name)
                        put("strength", medication.strength)
                        put("activeSubstance", medication.activeSubstance)
                        put("packageSize", medication.packageSize)
                        put("unit", medication.unit)
                        put("currentStock", medication.currentStock)
                        put("dosage", medication.dosage)
                        put("alertDays", medication.alertDays)
                    })
                }
            })
        }

        val directory = File(appContext.filesDir, BACKUP_DIRECTORY).apply { mkdirs() }
        return File(directory, BACKUP_FILE_NAME).apply {
            writeText(payload.toString(2))
        }
    }

    companion object {
        const val SNAPSHOT_FORMAT = "medstock-user-medications-v1"
        private const val BACKUP_DIRECTORY = "drive_backup"
        private const val BACKUP_FILE_NAME = "medstock_medications_backup.json"
    }
}
