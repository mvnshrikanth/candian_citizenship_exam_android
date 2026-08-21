package com.mvnsh.citizenship.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.FragmentOnboardHowBinding
import com.mvnsh.citizenship.ui.common.Motion

class OnboardHowFragment : Fragment(R.layout.fragment_onboard_how) {

    private var _binding: FragmentOnboardHowBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOnboardHowBinding.bind(view)
        Motion.rise(binding.root)

        binding.obHowBack.setOnClickListener { onboardingHost().back() }
        binding.obHowFinish.setOnClickListener { onboardingHost().finish() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
