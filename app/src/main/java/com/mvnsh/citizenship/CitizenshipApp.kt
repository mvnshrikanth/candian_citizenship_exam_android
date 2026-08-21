package com.mvnsh.citizenship

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.mvnsh.citizenship.data.AuthRepository
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.ProgressRepository
import com.mvnsh.citizenship.data.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.progressStore by preferencesDataStore(name = "progress")

/**
 * Owns the process-scoped singletons. The app is small enough that a DI framework would
 * cost more than it saves; these four repositories are the whole object graph.
 */
class CitizenshipApp : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bankRepository: BankRepository by lazy { BankRepository(this) }

    val progressRepository: ProgressRepository by lazy {
        ProgressRepository(progressStore, appScope)
    }

    val authRepository: AuthRepository by lazy { AuthRepository(FirebaseAuth.getInstance()) }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(firestore, progressRepository, appScope)
    }

    /**
     * Offline persistence is on, which is what lets a signed-in user keep studying through
     * a dropout and have the writes reconcile later. The web app does not enable it; that
     * is noted as optional in docs/web-app-changes.md.
     */
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings {})
            }
        }
    }
}
