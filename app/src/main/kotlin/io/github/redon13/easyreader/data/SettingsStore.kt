package io.github.redon13.easyreader.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.redon13.easyreader.data.browse.SortField
import io.github.redon13.easyreader.data.browse.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("settings")

/** Reader backgrounds from docs/DESIGN.md. */
enum class ReaderTheme(val label: String, val background: Long, val text: Long, val secondary: Long) {
    WHITE("White", 0xFFFFFFFF, 0xFF1C1C1C, 0xFF5E5E5E),
    CAPPUCCINO("Light cappuccino", 0xFFEFE2CF, 0xFF3B2A1C, 0xFF6E5844),
    DARK("Dark", 0xFF121212, 0xFFD8D2C8, 0xFF9C958B),
}

enum class ReaderTypeface(val label: String) {
    ALEGREYA("Alegreya"),
    ALEGREYA_SANS("Alegreya Sans"),
    ORIGINAL("Book's own"),
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.WHITE,
    /** 1.0 is the normal size; the design's default (20 sp) is a little larger. */
    val fontSize: Double = 1.2,
    val typeface: ReaderTypeface = ReaderTypeface.ALEGREYA,
)

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

    /** The chosen sort order (date added, newest first, until changed). */
    val sortOrder: Flow<SortOrder> = store.data.map { p ->
        SortOrder(
            field = p[SORT_FIELD]?.let { name -> SortField.entries.firstOrNull { it.name == name } } ?: SortField.DATE_ADDED,
            descending = p[SORT_DESCENDING] ?: true,
        )
    }

    suspend fun saveSortOrder(order: SortOrder) {
        store.edit {
            it[SORT_FIELD] = order.field.name
            it[SORT_DESCENDING] = order.descending
        }
    }

    /** Whether book lists are shown as a list (true) or a cover grid (false). */
    val listView: Flow<Boolean> = store.data.map { it[LIST_VIEW] ?: false }

    suspend fun saveListView(list: Boolean) {
        store.edit { it[LIST_VIEW] = list }
    }

    val readerSettings: Flow<ReaderSettings> = store.data.map { p ->
        ReaderSettings(
            theme = p[READER_THEME]?.let { n -> ReaderTheme.entries.firstOrNull { it.name == n } } ?: ReaderTheme.WHITE,
            fontSize = p[READER_FONT_SIZE] ?: 1.2,
            typeface = p[READER_TYPEFACE]?.let { n -> ReaderTypeface.entries.firstOrNull { it.name == n } } ?: ReaderTypeface.ALEGREYA,
        )
    }

    suspend fun saveReaderSettings(settings: ReaderSettings) {
        store.edit {
            it[READER_THEME] = settings.theme.name
            it[READER_FONT_SIZE] = settings.fontSize
            it[READER_TYPEFACE] = settings.typeface.name
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_encrypted")
        val LIBRARY_ID = stringPreferencesKey("library_id")
        val LIBRARY_NAME = stringPreferencesKey("library_name")
        val LAST_SYNCED = longPreferencesKey("last_synced")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_DESCENDING = booleanPreferencesKey("sort_descending")
        val LIST_VIEW = booleanPreferencesKey("list_view")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT_SIZE = doublePreferencesKey("reader_font_size")
        val READER_TYPEFACE = stringPreferencesKey("reader_typeface")
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
