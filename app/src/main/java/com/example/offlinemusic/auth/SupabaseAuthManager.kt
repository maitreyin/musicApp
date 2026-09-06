package com.example.offlinemusic.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import com.example.offlinemusic.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.from
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AuthUiState(
    val isLoading: Boolean = false,
    val signedIn: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val accessToken: String? = null,
    val error: String? = null
)

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val username: String = "",
    val avatarUrl: String? = null,
    val error: String? = null
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
                email = account.email,
                avatarUrl = account.photoUrl?.toString()
            )
            _authState.value = state
            loadOrCreateProfile(
                userId = session.user?.id ?: error("Supabase user ID is missing."),
                displayName = account.displayName,
                avatarUrl = account.photoUrl?.toString()
            )
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
        runCatching { supabase.auth.signOut() }
            .onFailure { throwable -> Log.w("SupabaseAuthManager", "Supabase logout failed", throwable) }
        googleSignInClient.signOut()
        _authState.value = AuthUiState()
        _profileState.value = ProfileUiState()
    }

    suspend fun loadProfile() = withContext(Dispatchers.IO) {
        val session = supabase.auth.currentSessionOrNull()
        val userId = session?.user?.id
        if (userId.isNullOrBlank()) {
            _profileState.value = ProfileUiState()
            return@withContext
        }

        _profileState.value = _profileState.value.copy(isLoading = true, error = null)
        runCatching { fetchProfile(userId) }
            .onSuccess { profile ->
                _profileState.value = ProfileUiState(
                    username = profile?.get("username")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    avatarUrl = profile?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                )
            }
            .onFailure { throwable ->
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
                eq("id", userId)
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

    private suspend fun loadOrCreateProfile(userId: String, displayName: String?, avatarUrl: String?) {
        val existing = fetchProfile(userId)
        if (existing == null) {
            supabase.from("profiles").insert(
                buildJsonObject {
                    put("id", userId)
                    put("username", displayName ?: JsonNull)
                    put("avatar_url", avatarUrl ?: JsonNull)
                }
            )
        }
        loadProfile()
    }

    private suspend fun fetchProfile(userId: String) =
        supabase.from("profiles").select {
            eq("id", userId)
        }.decodeList<kotlinx.serialization.json.JsonObject>().firstOrNull()

    private fun updateStateFromSession(session: UserSession?) {
        _authState.value = session?.let(::stateFromSession) ?: AuthUiState()
    }

    private fun stateFromSession(session: UserSession) = AuthUiState(
        signedIn = true,
        email = session.user?.email,
        accessToken = session.accessToken
    )

    private fun friendlyErrorMessage() = "Authentication failed. Please try again."

    private fun friendlyProfileError() = "Profile could not be loaded. Please try again."
}
