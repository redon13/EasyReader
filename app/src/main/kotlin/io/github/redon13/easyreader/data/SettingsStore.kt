package io.github.redon13.easyreader.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("settings")

data class SavedLogin(
    val serverUrl: String,
    val username: String,
    /** Null when signed out (the address and username are kept to fill in the form). */
    val password: String?,
    val libraryId: String?,
    val libraryName: String?,
)

/** Settings kept on the device. The password is encrypted with a key held by Android. */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    suspend fun savedLogin(): SavedLogin? {
        val p = store.data.first()
        val url = p[SERVER_URL] ?: return null
        return SavedLogin(
            serverUrl = url,
            username = p[USERNAME].orEmpty(),
            password = p[PASSWORD]?.let { PasswordCipher.decrypt(it) },
            libraryId = p[LIBRARY_ID],
            libraryName = p[LIBRARY_NAME],
        )
    }

    suspend fun saveLogin(serverUrl: String, username: String, password: String) {
        store.edit {
            it[SERVER_URL] = serverUrl
            it[USERNAME] = username
            it[PASSWORD] = PasswordCipher.encrypt(password)
        }
    }

    suspend fun saveLibrary(id: String, name: String) {
        store.edit {
            it[LIBRARY_ID] = id
            it[LIBRARY_NAME] = name
        }
    }

    suspend fun signOut() {
        store.edit {
            it.remove(PASSWORD)
            it.remove(LAST_SYNCED)
        }
    }

    val lastSynced: Flow<Long?> = store.data.map { it[LAST_SYNCED] }

    suspend fun setLastSynced(time: Long) {
        store.edit { it[LAST_SYNCED] = time }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_encrypted")
        val LIBRARY_ID = stringPreferencesKey("library_id")
        val LIBRARY_NAME = stringPreferencesKey("library_name")
        val LAST_SYNCED = longPreferencesKey("last_synced")
    }
}

/** Encrypts the password with a key that never leaves the device's secure hardware. */
private object PasswordCipher {
    private const val KEY_ALIAS = "easyreader_password_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** Returns null if it can't be read (the owner is then asked to sign in again). */
    fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
