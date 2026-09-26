package com.example.offlinemusic.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import com.example.offlinemusic.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Tasks
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import android.webkit.MimeTypeMap

data class AuthUiState(
    val isLoading: Boolean = false,
    val signedIn: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val accessToken: String? = null,
    /** Internal ownership key for locally stored music; it is never rendered by the UI. */
    val userId: String? = null,
    val error: String? = null
)

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val username: String = "",
    val avatarUrl: String? = null,
    val error: String? = null
)

private data class LoadedProfile(
    val username: String,
    val avatarUrl: String?
)

class SupabaseAuthManager(private val context: Context) {
    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
    )

    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()
    private val _profileState = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    suspend fun restoreSignedInState() = withContext(Dispatchers.IO) {
        _authState.value = AuthUiState(isLoading = true)
        runCatching {
            supabase.auth.awaitInitialization()
            updateStateFromSession(supabase.auth.currentSessionOrNull())
        }.onFailure { throwable ->
            Log.w("SupabaseAuthManager", "Could not restore Supabase session", throwable)
            _authState.value = AuthUiState(error = friendlyErrorMessage())
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun beginSignIn() {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
    }

    suspend fun handleSignInResult(result: ActivityResult): Result<AuthUiState> = withContext(Dispatchers.IO) {
        runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken?.takeIf { it.isNotBlank() }
                ?: error("Google ID token is missing or empty.")

            _authState.value = _authState.value.copy(isLoading = true, error = null)
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            val session = supabase.auth.currentSessionOrNull()
                ?: error("Supabase did not return an authenticated session.")
            val state = stateFromSession(session).copy(
                displayName = account.displayName,
                avatarUrl = account.photoUrl?.toString()
            )
            if (_authState.value.userId != state.userId) {
                _profileState.value = ProfileUiState()
            }
            _authState.value = state
            state
        }.onFailure { throwable ->
            val message = friendlyErrorMessage()
            Log.e("SupabaseAuthManager", "Google sign-in failed", throwable)
            _authState.value = _authState.value.copy(
                isLoading = false,
                signedIn = false,
                accessToken = null,
                error = message
            )
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
        } catch (throwable: Throwable) {
            Log.e("SupabaseAuthManager", "Supabase logout failed", throwable)
            _profileState.value = _profileState.value.copy(
                error = "Could not sign out. Please try again."
            )
            return@withContext
        }
        runCatching { Tasks.await(googleSignInClient.signOut()) }
            .onFailure { throwable -> Log.w("SupabaseAuthManager", "Google logout failed", throwable) }
        _authState.value = AuthUiState()
        _profileState.value = ProfileUiState()
    }

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        val session = supabase.auth.currentSessionOrNull()
        val userId = session?.user?.id
        if (userId.isNullOrBlank()) {
            _profileState.value = _profileState.value.copy(error = friendlyProfileError())
            return@withContext
        }

        _profileState.value = _profileState.value.copy(isSaving = true, error = null)
        try {
            val connection = (URL("${BuildConfig.SUPABASE_URL}/functions/v1/delete-account")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                doOutput = true
            }
            val statusCode = try {
                connection.outputStream.use { }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
            if (statusCode !in 200..299) error("Account deletion request failed")

            runCatching { Tasks.await(googleSignInClient.signOut()) }
                .onFailure { throwable -> Log.w("SupabaseAuthManager", "Google logout failed", throwable) }
            _authState.value = AuthUiState()
            _profileState.value = ProfileUiState()
        } catch (throwable: Throwable) {
            Log.e("SupabaseAuthManager", "Could not delete account", throwable)
            _profileState.value = _profileState.value.copy(
                isSaving = false,
                error = "Could not delete account. Please try again."
            )
        }
    }

    suspend fun loadProfile() = withContext(Dispatchers.IO) {
        val session = supabase.auth.currentSessionOrNull()
        val userId = session?.user?.id
        if (userId.isNullOrBlank()) {
            _profileState.value = ProfileUiState()
            return@withContext
        }

        _profileState.value = _profileState.value.copy(isLoading = true, error = null)
        try {
            var profile = fetchProfile(userId)
            if (profile == null) {
                try {
                    supabase.from("profiles").insert(
                        buildJsonObject {
                            put("id", userId)
                            put("username", JsonPrimitive(defaultUsername()))
                            put("avatar_url", JsonNull)
                        }
                    )
                } catch (insertError: Throwable) {
                    profile = fetchProfile(userId) ?: throw insertError
                }
                profile = profile ?: fetchProfile(userId)
                    ?: error("Profile was not created")
            }
            val avatarPath = profile?.stringValue("avatar_url")
            val loadedProfile = LoadedProfile(
                username = profile?.stringValue("username").orEmpty(),
                avatarUrl = when {
                    avatarPath.isNullOrBlank() -> null
                    avatarPath.startsWith("$userId/") -> createAvatarUrl(avatarPath)
                    avatarPath.startsWith("https://", ignoreCase = true) -> avatarPath
                    else -> null
                }
            )
            _profileState.value = ProfileUiState(
                username = loadedProfile.username,
                avatarUrl = loadedProfile.avatarUrl
            )
        } catch (throwable: Throwable) {
            Log.e("SupabaseAuthManager", "Could not load profile", throwable)
            _profileState.value = ProfileUiState(error = friendlyProfileError())
        }
    }

    suspend fun saveUsername(username: String) = withContext(Dispatchers.IO) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
        if (userId.isNullOrBlank()) {
            _profileState.value = _profileState.value.copy(error = friendlyProfileError())
            return@withContext
        }

        _profileState.value = _profileState.value.copy(isSaving = true, error = null)
        runCatching {
            supabase.from("profiles").update(
                buildJsonObject { put("username", username.trim()) }
            ) {
                filter { eq("id", userId) }
            }
            check(fetchProfile(userId)?.stringValue("username") == username.trim()) {
                "Profile username was not persisted"
            }
        }.onSuccess {
            _profileState.value = _profileState.value.copy(
                isSaving = false,
                username = username.trim(),
                error = null
            )
        }.onFailure { throwable ->
            Log.e("SupabaseAuthManager", "Could not save profile", throwable)
            _profileState.value = _profileState.value.copy(
                isSaving = false,
                error = friendlyProfileError()
            )
        }
    }

    suspend fun uploadProfilePicture(uri: Uri) = withContext(Dispatchers.IO) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
        if (userId.isNullOrBlank()) {
            _profileState.value = _profileState.value.copy(error = friendlyProfileError())
            return@withContext
        }

        _profileState.value = _profileState.value.copy(isSaving = true, error = null)
        var uploadedPath: String? = null
        runCatching {
            val mimeType = context.contentResolver.getType(uri)
                ?.takeIf { it.startsWith("image/", ignoreCase = true) }
                ?: error("Selected file is not a supported image")
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read image file")

            val bucket = supabase.storage["avatars"]
            val previousPath = fetchProfile(userId)?.stringValue("avatar_url")
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "img"
            val path = "$userId/${UUID.randomUUID()}.$extension"
            bucket.upload(path, bytes) {
                upsert = false
                contentType = ContentType.parse(mimeType)
            }
            uploadedPath = path
            val avatarUrl = bucket.createSignedUrl(path, 3600.seconds)
            supabase.from("profiles").update(
                buildJsonObject { put("avatar_url", path) }
            ) {
                filter { eq("id", userId) }
            }
            val savedPath = fetchProfile(userId)?.stringValue("avatar_url")
            check(savedPath == path) { "Profile avatar path was not persisted" }
            if (!previousPath.isNullOrBlank() && previousPath.startsWith("$userId/") && previousPath != path) {
                runCatching { bucket.delete(previousPath) }
                    .onFailure { throwable -> Log.w("SupabaseAuthManager", "Could not remove previous avatar", throwable) }
            }
            avatarUrl
        }.onSuccess { avatarUrl ->
            _profileState.value = _profileState.value.copy(
                isSaving = false,
                avatarUrl = avatarUrl,
                error = null
            )
        }.onFailure { throwable ->
            Log.e("SupabaseAuthManager", "Could not upload profile picture", throwable)
            uploadedPath?.let { path ->
                val profileResult = runCatching { fetchProfile(userId) }
                if (profileResult.isSuccess && profileResult.getOrNull()?.stringValue("avatar_url") != path) {
                    runCatching { supabase.storage["avatars"].delete(path) }
                        .onFailure { cleanupError -> Log.w("SupabaseAuthManager", "Could not clean up failed avatar upload", cleanupError) }
                }
            }
            _profileState.value = _profileState.value.copy(
                isSaving = false,
                error = "Failed to upload picture. Please try again."
            )
        }
    }

    private suspend fun fetchProfile(userId: String) =
        supabase.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeList<JsonObject>().firstOrNull()

    private suspend fun createAvatarUrl(path: String): String =
        supabase.storage["avatars"].createSignedUrl(path, 3600.seconds)

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun updateStateFromSession(session: UserSession?) {
        val state = session?.let(::stateFromSession) ?: AuthUiState()
        if (_authState.value.userId != state.userId) {
            _profileState.value = ProfileUiState()
        }
        _authState.value = state
    }

    private fun stateFromSession(session: UserSession) = AuthUiState(
        signedIn = session.user?.id != null,
        email = session.user?.email,
        accessToken = session.accessToken,
        userId = session.user?.id
    )

    private fun defaultUsername(): String =
        _authState.value.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: _authState.value.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "MUSED User"

    private fun friendlyErrorMessage() = "Authentication failed. Please try again."

    private fun friendlyProfileError() = "Profile could not be loaded. Please try again."
}
