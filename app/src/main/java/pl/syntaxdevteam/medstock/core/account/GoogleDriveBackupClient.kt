package pl.syntaxdevteam.medstock.core.account

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class GoogleDriveBackupClient(private val context: Context) {

    fun uploadSnapshot(accountEmail: String, snapshot: File): DriveUploadResult {
        val token = accessToken(accountEmail)
        val existingFileId = findLatestFile(token)?.id
        val boundary = "medstock-${UUID.randomUUID()}"
        val metadata = JSONObject().apply {
            put("name", DriveBackupSnapshotRepository.BACKUP_FILE_NAME)
            put("mimeType", BACKUP_MIME_TYPE)
            if (existingFileId == null) {
                put("parents", JSONArray().put(APP_DATA_FOLDER))
            }
        }
        val body = buildMultipartBody(boundary, metadata, snapshot.readText())
        val method = if (existingFileId == null) "POST" else "PATCH"
        val url = if (existingFileId == null) {
            "$DRIVE_UPLOAD_URL/files?uploadType=multipart&fields=id,modifiedTime"
        } else {
            "$DRIVE_UPLOAD_URL/files/$existingFileId?uploadType=multipart&fields=id,modifiedTime"
        }
        val response = executeRequest(
            method = method,
            url = url,
            token = token,
            body = body,
            contentType = "multipart/related; boundary=$boundary",
        )
        val payload = JSONObject(response)
        return DriveUploadResult(
            fileId = payload.getString("id"),
            modifiedTime = payload.optString("modifiedTime"),
        )
    }

    fun downloadLatestSnapshot(accountEmail: String, destination: File): BackupSnapshotMetadata? {
        val token = accessToken(accountEmail)
        val file = findLatestFile(token) ?: return null
        val response = executeRequest(
            method = "GET",
            url = "$DRIVE_FILES_URL/${file.id}?alt=media",
            token = token,
        )
        destination.parentFile?.mkdirs()
        destination.writeText(response)
        val payload = JSONObject(response)
        val snapshotAccountEmail = payload.optString("accountEmail")
        if (snapshotAccountEmail.isNotBlank() && !snapshotAccountEmail.equals(accountEmail, ignoreCase = true)) {
            destination.delete()
            return null
        }
        return BackupSnapshotMetadata(
            createdAtUtc = payload.optLong("createdAtUtc", destination.lastModified()),
            medicationCount = payload.optJSONArray("medications")?.length() ?: 0,
            reminderCount = payload.optJSONArray("reminders")?.length() ?: 0,
        )
    }

    private fun findLatestFile(token: String): DriveFile? {
        val query = "name = '${DriveBackupSnapshotRepository.BACKUP_FILE_NAME}' and trashed = false"
        val url = "$DRIVE_FILES_URL?spaces=$APP_DATA_FOLDER" +
            "&fields=${urlEncode("files(id,name,modifiedTime)")}" +
            "&orderBy=${urlEncode("modifiedTime desc")}" +
            "&pageSize=1" +
            "&q=${urlEncode(query)}"
        val response = executeRequest("GET", url, token)
        val files = JSONObject(response).optJSONArray("files") ?: return null
        val file = files.optJSONObject(0) ?: return null
        return DriveFile(
            id = file.getString("id"),
            modifiedTime = file.optString("modifiedTime"),
        )
    }

    private fun accessToken(accountEmail: String): String {
        val account = Account(accountEmail, GOOGLE_ACCOUNT_TYPE)
        try {
            return GoogleAuthUtil.getToken(context, account, DRIVE_APPDATA_SCOPE)
        } catch (exception: UserRecoverableAuthException) {
            throw DriveBackupAuthorizationRequiredException(exception.intent)
        } catch (exception: GoogleAuthException) {
            throw IOException("Google account authorization failed", exception)
        }
    }

    private fun executeRequest(
        method: String,
        url: String,
        token: String,
        body: String? = null,
        contentType: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECTION_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
                outputStream.use { output ->
                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    runCatching { GoogleAuthUtil.clearToken(context, token) }
                }
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Drive API $method failed with HTTP $responseCode: ${errorBody.take(MAX_ERROR_BODY_CHARS)}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildMultipartBody(boundary: String, metadata: JSONObject, snapshotJson: String): String {
        return buildString {
            append("--").append(boundary).append(CRLF)
            append("Content-Type: application/json; charset=UTF-8").append(CRLF).append(CRLF)
            append(metadata).append(CRLF)
            append("--").append(boundary).append(CRLF)
            append("Content-Type: ").append(BACKUP_MIME_TYPE).append(CRLF).append(CRLF)
            append(snapshotJson).append(CRLF)
            append("--").append(boundary).append("--").append(CRLF)
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class DriveFile(
        val id: String,
        val modifiedTime: String,
    )

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val APP_DATA_FOLDER = "appDataFolder"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3"
        private const val BACKUP_MIME_TYPE = "application/json"
        private const val CRLF = "\r\n"
        private const val CONNECTION_TIMEOUT_MILLIS = 20_000
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val MAX_ERROR_BODY_CHARS = 500
    }
}

class DriveBackupAuthorizationRequiredException(val authorizationIntent: Intent?) : IOException("Google Drive authorization required")

data class DriveUploadResult(
    val fileId: String,
    val modifiedTime: String,
)
