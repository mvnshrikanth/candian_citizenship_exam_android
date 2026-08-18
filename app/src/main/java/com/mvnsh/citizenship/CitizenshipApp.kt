package com.mvnsh.citizenship

import android.app.Application

/**
 * Owns the process-scoped singletons. Task 4 attaches the DataStore-backed
 * ProgressRepository here and Task 15 creates the notification channel.
 */
class CitizenshipApp : Application()
