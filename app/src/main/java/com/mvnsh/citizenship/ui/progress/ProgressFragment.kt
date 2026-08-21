package com.mvnsh.citizenship.ui.progress

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.databinding.FragmentProgressBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyTopInset
import com.mvnsh.citizenship.ui.mock.MockAttemptAdapter
import com.mvnsh.citizenship.ui.mock.MockAttemptRow
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.max

/** Accuracy, coverage, habit and milestones - how ready the whole thing says you are. */
class ProgressFragment : Fragment(R.layout.fragment_progress) {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val topicAdapter = TopicBarAdapter()
    private val mockAdapter = MockAttemptAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentProgressBinding.bind(view)
        binding.scroll.applyTopInset()
        Motion.rise(binding.content)

        binding.topicBars.layoutManager = LinearLayoutManager(requireContext())
        binding.topicBars.adapter = topicAdapter
        binding.topicBars.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list_wide)),
        )

        binding.mockList.layoutManager = LinearLayoutManager(requireContext())
        binding.mockList.adapter = mockAdapter
        binding.mockList.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.empty.emptyTitle.setText(R.string.progress_empty_title)
        binding.empty.emptyBody.setText(R.string.progress_empty_body)
        binding.empty.emptyCta.setText(R.string.progress_empty_cta)
        binding.empty.emptyIcon.setImageResource(R.drawable.ic_bar_chart)
        binding.empty.emptyDisc.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_surface_container),
        )
        binding.empty.emptyIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_on_surface_variant),
        )
        binding.empty.emptyCta.setOnClickListener {
            vm.startSession(StudyEngine.Mode.QUICK)
            if (vm.progress.value.session != null) findNavController().navigate(R.id.quizFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) -> render(p, bank) }
            }
        }
    }

    private fun render(p: ProgressState, bank: BankRepository.Bank?) {
        val measured = Stats.answered(p) > 0
        binding.measured.isVisible = measured
        binding.empty.emptyState.isVisible = !measured
        if (!measured) return

        renderHero(p, bank)
        binding.weekBars.setDays(Stats.weekWindow(p, LocalDate.now()))
        topicAdapter.submitList(bank?.let { Stats.topicStats(it.questions, p) }.orEmpty())
        renderMilestones(p, bank)
        renderMocks(p)
    }

    private fun renderHero(p: ProgressState, bank: BankRepository.Bank?) {
        val accuracy = Stats.accuracy(p)
        binding.accuracy.text = getString(R.string.results_percent, accuracy)
        binding.verdict.setText(
            when {
                accuracy >= READY -> R.string.progress_verdict_ready
                accuracy >= NEARLY -> R.string.progress_verdict_nearly
                accuracy > 0 -> R.string.progress_verdict_keep
                else -> R.string.progress_verdict_start
            },
        )
        binding.verdictNote.text = if (accuracy >= NEARLY) {
            getString(R.string.progress_note_good)
        } else {
            getString(
                R.string.progress_note_bar,
                StudyEngine.MOCK_PASS_CORRECT,
                StudyEngine.MOCK_SIZE,
                NEARLY,
            )
        }

        binding.statAnswered.text = Stats.answered(p).toString()
        val bankSize = bank?.questions?.size ?: 0
        // Only ids the bank still carries count as seen, or a stale record inflates it.
        val seen = bank?.questions?.count { it.id in p.seen } ?: 0
        binding.statRemaining.text = (bankSize - seen).toString()
        // Both are derived from the same day set, so the running streak can never
        // exceed the best - but max() keeps the intent obvious at the call site.
        binding.statBest.text =
            max(Stats.bestStreak(p), Stats.streak(p, LocalDate.now())).toString()
    }

    private fun renderMilestones(p: ProgressState, bank: BankRepository.Bank?) {
        val earned = Stats.achievements(bank?.questions.orEmpty(), p, LocalDate.now())
        binding.milesCount.text = getString(
            R.string.progress_miles_count, earned.size, Stats.MILESTONES.size,
        )

        binding.milestones.removeAllViews()
        Stats.MILESTONES.forEach { milestone ->
            binding.milestones.addView(buildChip(milestone, milestone.key in earned))
        }
    }

    private fun buildChip(milestone: Stats.Milestone, earned: Boolean): Chip {
        val ctx = requireContext()
        return Chip(ctx).apply {
            text = milestone.label
            textSize = CHIP_SP
            isClickable = false
            isCheckable = false
            chipStrokeWidth = 0f
            chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(
                    ctx,
                    if (earned) R.color.md_secondary_container else R.color.md_surface_container,
                ),
            )
            setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (earned) R.color.md_on_secondary_container_strong else R.color.md_text_muted,
                ),
            )
            if (earned) {
                chipIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_check)
                chipIconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.md_on_secondary_container_strong),
                )
                isChipIconVisible = true
            }
            // TalkBack would otherwise read only the label, with no sense of which are won.
            contentDescription = milestone.label
        }
    }

    private fun renderMocks(p: ProgressState) {
        val rows = p.mocks
            .mapIndexed { index, attempt ->
                MockAttemptRow(
                    number = index + 1,
                    date = DateUtils.fmtDate(attempt.date),
                    pct = attempt.pct,
                )
            }
            .reversed()

        binding.mocksCard.isVisible = rows.isNotEmpty()
        mockAdapter.submitList(rows)
    }

    override fun onDestroyView() {
        binding.topicBars.adapter = null
        binding.mockList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val READY = 85
        const val NEARLY = 75
        const val CHIP_SP = 12.5f
    }
}
