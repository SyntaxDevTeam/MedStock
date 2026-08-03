package pl.syntaxdevteam.medstock.core.download

import android.content.ContentValues
import android.content.Context
import kotlin.math.floor
import pl.syntaxdevteam.medstock.core.stock.MedicationStockCalculator
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication
import pl.syntaxdevteam.medstock.ui.medicationarchive.ArchivedMedication

class UserMedicationRepository(context: Context) {

    private val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)

    fun getAll(): List<UserMedication> {
        reconcileStockDepletion()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT id, name, strength, active_substance, package_size, unit, current_stock, dosage, alert_days, last_stock_update_utc
            FROM user_medication
            ORDER BY updated_at_utc DESC, id DESC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            val medications = mutableListOf<UserMedication>()
            while (cursor.moveToNext()) {
                medications += UserMedication(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    strength = cursor.getString(2).orEmpty(),
                    activeSubstance = cursor.getString(3).orEmpty(),
                    packageSize = cursor.getString(4).orEmpty(),
                    unit = cursor.getString(5).orEmpty(),
                    currentStock = cursor.getInt(6),
                    dosage = cursor.getString(7).orEmpty(),
                    alertDays = cursor.getInt(8),
                    lastStockUpdateUtc = cursor.getString(9).orEmpty()
                )
            }
            return medications
        }
    }

    fun insert(
        name: String,
        strength: String,
        activeSubstance: String,
        packageSize: String,
        unit: String,
        currentStock: Int,
        dosage: String,
        alertDays: Int
    ): Long {
        val db = dbHelper.writableDatabase
        val now = currentUtcDateTime()
        val values = ContentValues().apply {
            put("name", name)
            put("strength", strength)
            put("active_substance", activeSubstance)
            put("package_size", packageSize)
            put("unit", unit)
            put("current_stock", currentStock)
            put("dosage", dosage)
            put("alert_days", alertDays)
            put("last_stock_update_utc", now)
            put("updated_at_utc", now)
        }
        return db.insertOrThrow("user_medication", null, values)
    }

    fun update(
        id: Long,
        name: String,
        strength: String,
        activeSubstance: String,
        packageSize: String,
        unit: String,
        currentStock: Int,
        dosage: String,
        alertDays: Int
    ): Int {
        val db = dbHelper.writableDatabase
        val now = currentUtcDateTime()
        val values = ContentValues().apply {
            put("name", name)
            put("strength", strength)
            put("active_substance", activeSubstance)
            put("package_size", packageSize)
            put("unit", unit)
            put("current_stock", currentStock)
            put("dosage", dosage)
            put("alert_days", alertDays)
            put("last_stock_update_utc", now)
            put("updated_at_utc", now)
        }
        return db.update("user_medication", values, "id = ?", arrayOf(id.toString()))
    }

    fun addToStock(id: Long, addedStock: Int): Int {
        val db = dbHelper.writableDatabase
        val now = currentUtcDateTime()
        val statement = """
            UPDATE user_medication
            SET current_stock = current_stock + ?,
                last_stock_update_utc = ?,
                updated_at_utc = ?
            WHERE id = ?
        """.trimIndent()
        return db.compileStatement(statement).use { compiled ->
            compiled.bindLong(1, addedStock.toLong())
            compiled.bindString(2, now)
            compiled.bindString(3, now)
            compiled.bindLong(4, id)
            compiled.executeUpdateDelete()
        }
    }

    fun delete(id: Long): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val archivedRows = db.compileStatement(
                """
                INSERT INTO archived_user_medication(
                    original_medication_id, name, strength, active_substance, package_size, unit,
                    current_stock, dosage, alert_days, last_stock_update_utc, archived_at_utc
                )
                SELECT id, name, strength, active_substance, package_size, unit,
                       current_stock, dosage, alert_days, last_stock_update_utc, datetime('now')
                FROM user_medication WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.bindLong(1, id)
                statement.executeInsert()
            }
            val deletedRows = if (archivedRows != -1L) {
                db.delete("user_medication", "id = ?", arrayOf(id.toString()))
            } else {
                0
            }
            db.setTransactionSuccessful()
            deletedRows
        } finally {
            db.endTransaction()
        }
    }

    fun getAllArchived(): List<ArchivedMedication> {
        val query = """
            SELECT id, original_medication_id, name, strength, active_substance, package_size, unit,
                   current_stock, dosage, alert_days, archived_at_utc
            FROM archived_user_medication
            ORDER BY archived_at_utc DESC, id DESC
        """.trimIndent()
        return dbHelper.readableDatabase.rawQuery(query, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(ArchivedMedication(
                        id = cursor.getLong(0), originalMedicationId = cursor.getLong(1), name = cursor.getString(2),
                        strength = cursor.getString(3).orEmpty(), activeSubstance = cursor.getString(4).orEmpty(),
                        packageSize = cursor.getString(5).orEmpty(), unit = cursor.getString(6).orEmpty(),
                        currentStock = cursor.getInt(7), dosage = cursor.getString(8).orEmpty(),
                        alertDays = cursor.getInt(9), archivedAtUtc = cursor.getString(10).orEmpty()
                    ))
                }
            }
        }
    }

    fun reconcileStockDepletion(): Int {
        val db = dbHelper.writableDatabase
        val now = currentUtcDateTime()
        val query = """
            SELECT id, current_stock, dosage, last_stock_update_utc
            FROM user_medication
        """.trimIndent()

        var updatedRows = 0
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val currentStock = cursor.getInt(1)
                val dosage = cursor.getString(2).orEmpty()
                val lastStockUpdateUtc = cursor.getString(3).orEmpty()
                val daysElapsed = fullDaysBetween(lastStockUpdateUtc, now)
                if (daysElapsed <= 0) continue
                val dosagePerDay = MedicationStockCalculator.parseDailyUsage(dosage)
                if (dosagePerDay <= 0.0) continue
                val depletion = floor(dosagePerDay * daysElapsed).toInt()
                val updatedStock = (currentStock - depletion).coerceAtLeast(0)
                val values = ContentValues().apply {
                    put("current_stock", updatedStock)
                    put("last_stock_update_utc", now)
                    put("updated_at_utc", now)
                }
                updatedRows += db.update("user_medication", values, "id = ?", arrayOf(id.toString()))
            }
        }
        return updatedRows
    }

    private fun fullDaysBetween(lastStockUpdateUtc: String, currentUtc: String): Long {
        if (lastStockUpdateUtc.isBlank() || currentUtc.isBlank()) return 0
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT CAST((julianday(?) - julianday(?)) AS INTEGER)",
            arrayOf(currentUtc, lastStockUpdateUtc)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0).coerceAtLeast(0)
            }
        }
        return 0
    }

    private fun currentUtcDateTime(): String {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT datetime('now')", null).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return ""
    }
}
