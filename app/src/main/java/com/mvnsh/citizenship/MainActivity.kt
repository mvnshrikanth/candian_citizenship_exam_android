package com.mvnsh.citizenship

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.mvnsh.citizenship.databinding.ActivityMainBinding
import com.mvnsh.citizenship.ui.AppViewModel
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.showDesignSnackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The only Activity. Hosts the nav graph, owns the bottom navigation, applies the
 * persisted theme and shows snackbars raised by the shared ViewModel.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = (supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.bottomNav.setupWithNavController(navController)
        binding.bottomNav.applyBottomInset()

        // The design shows the bar on exactly these four screens.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevel = destination.id in TOP_LEVEL
            binding.bottomNav.isVisible = topLevel
            binding.navDivider.isVisible = topLevel
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                // Gate once, on the first value actually read from disk. Observing the
                // flow instead would bounce the user back into onboarding the moment
                // they finished it.
                val restored = (application as CitizenshipApp).progressRepository.awaitLoaded()
                // The graph inflates asynchronously; navigating before it is set throws.
                if (!restored.onboarded && navController.currentDestination != null) {
                    navController.navigate(R.id.onboardingFragment)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.progress.map { it.theme }.distinctUntilChanged().collect(::applyTheme)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.snacks.collect { showDesignSnackbar(binding.root, it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Make sure anything still inside the write debounce reaches disk.
        lifecycleScope.launch { (application as CitizenshipApp).progressRepository.flush() }
    }

    private fun applyTheme(name: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (name) {
                "Light" -> AppCompatDelegate.MODE_NIGHT_NO
                "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

    companion object {
        private val TOP_LEVEL = setOf(
            R.id.homeFragment,
            R.id.practiceFragment,
            R.id.topicsFragment,
            R.id.progressFragment,
        )
    }
}
