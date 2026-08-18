package com.mvnsh.citizenship

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {

    @Test
    fun activity_launches_and_inflates_host() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.nav_host))
                assertNotNull(activity.findViewById<View>(R.id.bottom_nav))
            }
        }
    }
}
