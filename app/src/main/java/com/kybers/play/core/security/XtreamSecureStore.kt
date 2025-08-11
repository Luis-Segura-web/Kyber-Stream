package com.kybers.play.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacenamiento seguro para credenciales de Xtream Codes usando encriptación
 */
@Singleton
class XtreamSecureStore @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "XtreamSecureStore"
        private const val PREFS_NAME = "secrets_xtream"
        private const val KEY_BASE_URL = "baseUrl"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences, falling back to regular", e)
            // Fallback a SharedPreferences regulares si falla la encriptación
            context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Guarda las credenciales de Xtream Codes de manera segura
     */
    fun save(credentials: XtreamCredentials) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_BASE_URL, credentials.baseUrl.trimEnd('/') + "/")
                .putString(KEY_USERNAME, credentials.username)
                .putString(KEY_PASSWORD, credentials.password)
                .apply()
            
            Log.d(TAG, "Xtream credentials saved securely")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Xtream credentials", e)
            throw SecurityException("Error al guardar credenciales", e)
        }
    }

    /**
     * Carga las credenciales guardadas
     */
    fun load(): XtreamCredentials? {
        return try {
            val baseUrl = encryptedPrefs.getString(KEY_BASE_URL, null)
            val username = encryptedPrefs.getString(KEY_USERNAME, null)
            val password = encryptedPrefs.getString(KEY_PASSWORD, null)

            if (baseUrl != null && username != null && password != null) {
                XtreamCredentials(baseUrl, username, password)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Xtream credentials", e)
            null
        }
    }

    /**
     * Verifica si hay credenciales guardadas
     */
    fun hasCredentials(): Boolean {
        return try {
            encryptedPrefs.contains(KEY_USERNAME) && 
            encryptedPrefs.contains(KEY_PASSWORD) && 
            encryptedPrefs.contains(KEY_BASE_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check credentials", e)
            false
        }
    }

    /**
     * Limpia todas las credenciales guardadas
     */
    fun clear() {
        try {
            encryptedPrefs.edit().clear().apply()
            Log.d(TAG, "Xtream credentials cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials", e)
        }
    }

    /**
     * Obtiene información básica de la cuenta sin exponer la contraseña
     */
    fun getAccountInfo(): XtreamAccountInfo? {
        return try {
            val baseUrl = encryptedPrefs.getString(KEY_BASE_URL, null)
            val username = encryptedPrefs.getString(KEY_USERNAME, null)
            
            if (baseUrl != null && username != null) {
                XtreamAccountInfo(
                    baseUrl = redactUrl(baseUrl),
                    username = username
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get account info", e)
            null
        }
    }

    /**
     * Redacta la URL para logging seguro
     */
    private fun redactUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}:${uri.port}/***"
        } catch (e: Exception) {
            "***"
        }
    }
}

/**
 * Credenciales completas de Xtream Codes
 */
data class XtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String
) {
    /**
     * Genera la URL base para API requests
     */
    fun getApiBaseUrl(): String = baseUrl.trimEnd('/') + "/"
    
    /**
     * Genera la URL para stream con los parámetros básicos
     */
    fun getStreamBaseUrl(): String = "${getApiBaseUrl()}live/$username/$password/"
    
    /**
     * Para logging seguro - nunca exponer la contraseña
     */
    override fun toString(): String {
        return "XtreamCredentials(baseUrl='${redactUrl(baseUrl)}', username='$username', password='***')"
    }
    
    private fun redactUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}:${uri.port}/***"
        } catch (e: Exception) {
            "***"
        }
    }
}

/**
 * Información básica de la cuenta sin datos sensibles
 */
data class XtreamAccountInfo(
    val baseUrl: String, // URL ya redactada
    val username: String
)