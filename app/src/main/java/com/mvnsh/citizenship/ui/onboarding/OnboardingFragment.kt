package com.mvnsh.citizenship.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.FragmentOnboardingBinding
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.applyInsets

/**
 * Hosts the three onboarding pages. Swiping is disabled because the design advances
 * only through its own buttons, and page 2 is a form rather than a slide.
 */
class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOnboardingBinding.bind(view)
        // One call, both edges: two calls would leave only the last listener installed.
        binding.root.applyInsets(top = true, bottom = true)

        binding.pager.isUserInputEnabled = false
        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = PAGES

            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> OnboardWelcomeFragment()
                1 -> OnboardPaceFragment()
                else -> OnboardHowFragment()
            }
        }
    }

    fun goToPage(index: Int) {
        // No smooth scroll: the design swaps the panel and plays its own rise
        // animation per page, it never slides horizontally.
        binding.pager.setCurrentItem(index.coerceIn(0, PAGES - 1), false)
    }

    fun back() {
        goToPage(binding.pager.currentItem - 1)
    }

    /** Marks onboarding done and hands over to home, leaving nothing to come back to. */
    fun finish() {
        vm.finishOnboarding()
        findNavController().navigate(
            R.id.homeFragment,
            null,
            androidx.navigation.navOptions {
                popUpTo(R.id.onboardingFragment) { inclusive = true }
            },
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val PAGES = 3
    }
}

/**
 * Pages reach their host for navigation rather than knowing the nav graph themselves.
 * Non-null on purpose: a page that cannot find its host is a wiring bug, and returning
 * null here would turn every button on that page into a silent no-op.
 */
internal fun Fragment.onboardingHost(): OnboardingFragment =
    requireNotNull(parentFragment as? OnboardingFragment) {
        "onboarding page ${javaClass.simpleName} has parent ${parentFragment?.javaClass?.simpleName}"
    }
