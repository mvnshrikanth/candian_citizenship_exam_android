package com.mvnsh.citizenship

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.progressStore by preferencesDataStore(name = "progress")

/**
 * Owns the process-scoped singletons. The app is small enough that a DI framework would
 * cost more than it saves; these two repositories are the whole object graph.
 */
class CitizenshipApp : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bankRepository: BankRepository by lazy { BankRepository(this) }

    val progressRepository: ProgressRepository by lazy {
        ProgressRepository(progressStore, appScope)
    }
}
