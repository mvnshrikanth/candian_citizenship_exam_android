package com.mvnsh.citizenship.ui.quiz

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.FragmentResultsBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** What a finished practice session came to, and the one thing worth doing next. */
class ResultsFragment : Fragment(R.layout.fragment_results) {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = MissedAdapter()

    /** The ids to re-practise, captured on render so the CTA does not re-derive them. */
    private var missedIds: List<Int> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentResultsBinding.bind(view)
        binding.scroll.applyTopInset()
        binding.footer.applyBottomInset()
        Motion.rise(binding.content)

        binding.missed.layoutManager = LinearLayoutManager(requireContext())
        binding.missed.adapter = adapter
        binding.missed.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.practiseMissed.setOnClickListener {
            vm.practiceMissed(missedIds)
            if (vm.progress.value.session?.submitted == false) {
                findNavController().navigate(
                    R.id.quizFragment,
                    null,
                    navOptions { popUpTo(R.id.resultsFragment) { inclusive = true } },
                )
            }
        }

        binding.backHome.setOnClickListener { finish() }
        // Back means the same thing here: the session is over either way.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finish()
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.session, vm.bank, ::Triple).collect { (p, s, bank) ->
                    render(p, s, bank)
                }
            }
        }
    }

    private fun render(p: ProgressState, session: SessionState?, bank: BankRepository.Bank?) {
        if (session == null || bank == null) return

        val total = session.ids.size
        val right = session.finalRight ?: session.right
        binding.sessionLabel.text = getString(R.string.results_label, session.label)
        binding.score.text = right.toString()
        binding.scoreOf.text = getString(R.string.results_of_correct, total)

        val accuracy = if (total == 0) 0 else (right * 100.0 / total).roundToInt()
        binding.statAccuracy.text = getString(R.string.results_percent, accuracy)
        binding.statMissed.text = (total - right).toString()
        binding.statTime.text = getString(R.string.results_minutes, minutesSpent(session))

        renderGoal(p)
        renderMissed(session, bank)
    }

    private fun renderGoal(p: ProgressState) {
        // A goal counted on an earlier day is not today's goal, so it is not called out.
        val metToday = p.goalDate == DateUtils.today() && p.goalDone >= p.goalTarget
        binding.goalMetCard.isVisible = metToday
        if (metToday) {
            binding.goalMetText.text = getString(R.string.results_goal_met, p.streak)
        }
    }

    private fun renderMissed(session: SessionState, bank: BankRepository.Bank) {
        // Everything not answered correctly, so the list, the "missed" tile and the CTA
        // all count the same thing. A skipped question says so rather than being dropped.
        val rows = session.ids.mapNotNull { id ->
            val q = bank.byId[id] ?: return@mapNotNull null
            val mark = session.marks[id]
            if (mark == q.answer) return@mapNotNull null

            MissedRow(
                id = id,
                topic = Topics.name(q.topic),
                question = DateUtils.clip(q.question, QUESTION_CLIP),
                yourAnswer = mark?.let { q.options.getOrNull(it) }
                    ?: getString(R.string.results_skipped),
                correctAnswer = q.options.getOrElse(q.answer) { "" },
            )
        }

        missedIds = rows.map { it.id }
        adapter.submitList(rows)

        val clean = rows.isEmpty()
        binding.missedLabel.isVisible = !clean
        binding.missed.isVisible = !clean
        binding.cleanSweepCard.isVisible = clean
        binding.practiseMissed.isVisible = !clean
        if (!clean) {
            binding.practiseMissed.text =
                getString(R.string.results_practise_missed, rows.size)
        }
    }

    /** The design's "spent" tile, never rounded down to a bare zero. */
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
        binding.missed.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val QUESTION_CLIP = 88
    }
}
