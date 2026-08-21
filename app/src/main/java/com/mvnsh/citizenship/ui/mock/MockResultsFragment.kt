package com.mvnsh.citizenship.ui.mock

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.FragmentMockResultsBinding
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** How the mock went, measured against the real test's bar of 15 out of 20. */
class MockResultsFragment : Fragment(R.layout.fragment_mock_results) {

    private var _binding: FragmentMockResultsBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMockResultsBinding.bind(view)
        binding.scroll.applyTopInset()
        binding.footer.applyBottomInset()
        Motion.rise(binding.content)

        binding.reviewAll.setOnClickListener {
            findNavController().navigate(R.id.reviewFragment)
        }
        binding.practiseWeak.setOnClickListener {
            vm.startSession(StudyEngine.Mode.WEAK)
            if (vm.progress.value.session?.submitted == false) {
                findNavController().navigate(
                    R.id.quizFragment,
                    null,
                    navOptions { popUpTo(R.id.mockResultsFragment) { inclusive = true } },
                )
            }
        }
        binding.retake.setOnClickListener {
            vm.startMock()
            if (vm.progress.value.session?.submitted == false) {
                findNavController().navigate(
                    R.id.mockFragment,
                    null,
                    navOptions { popUpTo(R.id.mockResultsFragment) { inclusive = true } },
                )
            }
        }

        binding.backHome.setOnClickListener { finish() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finish()
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.session.collect { session -> session?.let(::render) }
            }
        }
    }

    private fun render(session: SessionState) {
        val total = session.ids.size
        val right = session.finalRight ?: 0
        val pct = session.finalPct ?: if (total == 0) 0 else (right * 100.0 / total).roundToInt()

        binding.score.text = right.toString()
        binding.scoreOf.text = getString(R.string.mock_result_of, total, pct)
        binding.statCorrect.text = right.toString()
        binding.statWrong.text = (total - right).toString()
        binding.statTime.text = getString(R.string.results_minutes, minutesSpent(session))
        binding.statTimeLabel.text =
            getString(R.string.mock_stat_of_thirty, StudyEngine.MOCK_SECONDS / 60)

        binding.reviewAll.text = getString(R.string.mock_review_cta, total)
        renderBanner(right >= StudyEngine.MOCK_PASS_CORRECT, right)
    }

    private fun renderBanner(passed: Boolean, right: Int) {
        val ctx = requireContext()
        if (passed) {
            binding.banner.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.md_secondary_container),
            )
            binding.bannerDisc.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_secondary))
            binding.bannerIcon.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_on_secondary))
            binding.bannerHeadline.setText(R.string.mock_pass_headline)
            binding.bannerNote.setText(R.string.mock_pass_note)
        } else {
            binding.banner.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.md_surface_container),
            )
            binding.bannerDisc.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_primary_container))
            binding.bannerIcon.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_on_primary_container))
            binding.bannerHeadline.text = getString(
                R.string.mock_fail_headline,
                StudyEngine.MOCK_PASS_CORRECT,
                StudyEngine.MOCK_SIZE,
            )
            binding.bannerNote.text = getString(
                R.string.mock_fail_note,
                max(0, StudyEngine.MOCK_PASS_CORRECT - right),
            )
        }
        Motion.pop(binding.bannerDisc)
    }

    private fun minutesSpent(session: SessionState): Int {
        val elapsed = System.currentTimeMillis() - session.startedAtEpochMs
        return max(1, (elapsed / 60_000.0).roundToInt())
    }

    private fun finish() {
        vm.discardSession()
        findNavController().navigate(
            R.id.homeFragment,
            null,
            navOptions { popUpTo(R.id.homeFragment) { inclusive = true } },
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
