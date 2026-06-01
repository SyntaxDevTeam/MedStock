package pl.syntaxdevteam.medstock.core.account

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GoogleAccountProfileClient(private val context: Context) {

    fun fetchProfile(accountEmail: String): GoogleAccountProfile {
        val token = accessToken(accountEmail)
        val response = executeRequest(token)
        val payload = JSONObject(response)
        return GoogleAccountProfile(
            avatarUrl = payload.optString("picture").takeIf { it.isNotBlank() },
        )
    }

    private fun accessToken(accountEmail: String): String {
        val account = Account(accountEmail, GOOGLE_ACCOUNT_TYPE)
        try {
            return GoogleAuthUtil.getToken(context, account, PROFILE_SCOPE)
        } catch (exception: UserRecoverableAuthException) {
            throw GoogleProfileAuthorizationRequiredException(exception.intent)
        } catch (exception: GoogleAuthException) {
            throw IOException("Google profile authorization failed", exception)
        }
    }

    private fun executeRequest(token: String): String {
        val connection = (URL(USERINFO_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECTION_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
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
                throw IOException("Google profile API failed with HTTP $responseCode: ${errorBody.take(MAX_ERROR_BODY_CHARS)}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val PROFILE_SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.profile"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val CONNECTION_TIMEOUT_MILLIS = 20_000
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val MAX_ERROR_BODY_CHARS = 500
    }
}

class GoogleProfileAuthorizationRequiredException(val authorizationIntent: Intent?) : IOException("Google profile authorization required")

data class GoogleAccountProfile(
    val avatarUrl: String?,
)
