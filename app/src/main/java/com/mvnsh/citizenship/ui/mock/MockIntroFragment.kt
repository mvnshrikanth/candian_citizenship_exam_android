package com.mvnsh.citizenship.ui.mock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.databinding.FragmentMockIntroBinding
import com.mvnsh.citizenship.databinding.ItemFactBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.launch

/** The rules of the mock, and how the last few went. */
class MockIntroFragment : Fragment(R.layout.fragment_mock_intro) {

    private var _binding: FragmentMockIntroBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = MockAttemptAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMockIntroBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.footer.applyBottomInset()
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }

        binding.attempts.layoutManager = LinearLayoutManager(requireContext())
        binding.attempts.adapter = adapter
        binding.attempts.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        buildFacts()

        binding.startTest.setOnClickListener {
            vm.startMock()
            if (vm.progress.value.session != null) {
                findNavController().navigate(
                    R.id.mockFragment,
                    null,
                    navOptions { popUpTo(R.id.mockIntroFragment) { inclusive = true } },
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.progress.collect(::render)
            }
        }
    }

    /**
     * The four facts are inflated rather than written out four times in XML: duplicate
     * ids in one layout resolve to whichever came first in the binding, which would make
     * three of the four values unreachable by name.
     */
    private fun buildFacts() {
        val facts = listOf(
            getString(R.string.mock_fact_questions) to
                getString(R.string.mock_fact_questions_value, StudyEngine.MOCK_SIZE),
            getString(R.string.mock_fact_time) to
                getString(R.string.mock_fact_time_value, StudyEngine.MOCK_SECONDS / 60),
            getString(R.string.mock_fact_pass) to
                getString(R.string.mock_fact_pass_value, StudyEngine.MOCK_PASS_CORRECT),
            getString(R.string.mock_fact_answers) to
                getString(R.string.mock_fact_answers_value),
        )

        val inflater = LayoutInflater.from(requireContext())
        val gap = resources.getDimensionPixelSize(R.dimen.stroke_card)
        facts.forEachIndexed { index, (label, value) ->
            val row = ItemFactBinding.inflate(inflater, binding.facts, false)
            row.factLabel.text = label
            row.factValue.text = value
            binding.facts.addView(row.root)
            // The 1dp gap is the divider: the container's fill shows through it.
            if (index > 0) {
                row.root.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = gap }
            }
        }
    }

    private fun render(p: ProgressState) {
        // Newest first for reading, but each keeps the number it was taken as.
        val rows = p.mocks
            .mapIndexed { index, attempt ->
                MockAttemptRow(
                    number = index + 1,
                    date = DateUtils.fmtDate(attempt.date),
                    pct = attempt.pct,
                )
            }
            .reversed()

        binding.attemptsLabel.isVisible = rows.isNotEmpty()
        binding.attempts.isVisible = rows.isNotEmpty()
        adapter.submitList(rows)
    }

    override fun onDestroyView() {
        binding.attempts.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
