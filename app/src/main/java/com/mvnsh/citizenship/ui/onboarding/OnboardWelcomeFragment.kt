package com.mvnsh.citizenship.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.FragmentOnboardWelcomeBinding
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import kotlinx.coroutines.launch

class OnboardWelcomeFragment : Fragment(R.layout.fragment_onboard_welcome) {

    private var _binding: FragmentOnboardWelcomeBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOnboardWelcomeBinding.bind(view)
        Motion.rise(binding.root)

        binding.obWelcomeNext.setOnClickListener { onboardingHost().goToPage(1) }
        binding.obWelcomeUsedBefore.setOnClickListener { onboardingHost().finish() }

        // The bank size is read from the bank rather than hardcoded, so the claim
        // stays true if the upstream question set changes.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.bank.collect { loaded ->
                    binding.featureBank.text =
                        getString(R.string.ob_feature_bank, loaded?.size ?: 0)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
