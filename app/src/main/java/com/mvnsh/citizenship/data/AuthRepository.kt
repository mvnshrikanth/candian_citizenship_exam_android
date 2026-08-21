package com.mvnsh.citizenship.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException

/** Who is signed in, if anyone. */
sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val uid: String, val email: String) : AccountState
}

/**
 * Email and password sign-in, matching the web app exactly - it offers no other provider,
 * so an account created on either platform works on both.
 *
 * Signing in is optional. Nothing here is on the path of studying: the bank is a bundled
 * asset and progress is written locally whether or not anyone is signed in.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    /** Emits on every sign-in and sign-out, starting with the current state. */
    val account: Flow<AccountState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toState()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val current: AccountState get() = auth.currentUser.toState()

    /**
     * Creates the account. The caller is responsible for creating the user's document -
     * [SyncRepository] does it on the first sync, in the same shape `js/auth.js` writes.
     */
    suspend fun signUp(email: String, password: String): Result<AccountState> =
        run { auth.createUserWithEmailAndPassword(email.trim(), password) }

    suspend fun signIn(email: String, password: String): Result<AccountState> =
        run { auth.signInWithEmailAndPassword(email.trim(), password) }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email.trim()).await(); Unit }
            .recoverCatching { throw AuthError(messageFor(it)) }

    /** Local progress is untouched; this only stops it being mirrored to the account. */
    fun signOut() = auth.signOut()

    private suspend fun run(
        block: () -> com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>,
    ): Result<AccountState> = runCatching { block().await().user.toState() }
        .recoverCatching { throw AuthError(messageFor(it)) }

    private fun com.google.firebase.auth.FirebaseUser?.toState(): AccountState =
        this?.let { AccountState.SignedIn(it.uid, it.email.orEmpty()) } ?: AccountState.SignedOut

    private companion object {
        /**
         * Firebase's own messages read like error codes. These are the four things that
         * actually go wrong, in words that tell someone what to do next.
         */
        fun messageFor(cause: Throwable): String = when (cause) {
            is FirebaseAuthWeakPasswordException ->
                "That password is too short. Use at least six characters."
            is FirebaseAuthInvalidCredentialsException ->
                "That email and password do not match an account."
            is FirebaseAuthInvalidUserException ->
                "There is no account for that email address."
            is FirebaseAuthUserCollisionException ->
                "An account already exists for that email. Sign in instead."
            is IOException ->
                "No connection. Your progress is safe on this device - try again later."
            else -> "Something went wrong signing in. Please try again."
        }
    }
}

/** Carries a message already fit to show a user. */
class AuthError(override val message: String) : Exception(message)
