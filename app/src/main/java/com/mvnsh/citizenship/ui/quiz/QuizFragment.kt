package com.mvnsh.citizenship.ui.quiz

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.FragmentQuizBinding
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import com.mvnsh.citizenship.ui.settings.ExitSessionDialog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One practice question at a time, graded the moment an option is picked. */
class QuizFragment : Fragment(R.layout.fragment_quiz) {

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    /** The question on screen, so the listeners set once do not need the render state. */
    private var current: Question? = null

    /** True once the session has run out and the screen has handed over to results. */
    private var handedOver = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentQuizBinding.bind(view)
        binding.appBar.applyTopInset()
        // The bottom nav is hidden here, so this screen reaches the bottom of the window
        // and has to place the navigation bar itself. Without this the footer's buttons
        // sit inside the system gesture region, where taps never reach them.
        binding.footer.applyBottomInset()
        Motion.rise(binding.content)

        binding.exit.setOnClickListener { askToLeave() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                // System back is the same intent as the exit arrow, so it asks too rather
                // than dropping the user on home with a session they did not choose to keep.
                override fun handleOnBackPressed() = askToLeave()
            },
        )

        binding.bookmark.setOnClickListener {
            current?.let { vm.toggleBookmark(it.id) }
        }
        binding.skip.setOnClickListener { vm.next() }
        binding.nextCta.setOnClickListener { vm.next() }

        setFragmentResultListener(ExitSessionDialog.REQUEST_KEY) { _, bundle ->
            when (bundle.getString(ExitSessionDialog.KEY_CHOICE)) {
                ExitSessionDialog.RESULT_SAVE -> {
                    vm.keepSessionAndExit()
                    goHome()
                }

                ExitSessionDialog.RESULT_DISCARD -> {
                    vm.discardSession()
                    goHome()
                }

                else -> Unit // "Keep studying" just closes the dialog.
            }
        }

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
        if (session.submitted) {
            handOverToResults()
            return
        }

        val question = bank.byId[session.ids.getOrNull(session.index)]
        if (question == null) {
            // The id is not in the bank any more; skipping is better than a blank screen.
            vm.next()
            return
        }
        current = question

        val revealed = session.picked != null
        renderHeader(p, session, question, revealed)

        binding.qTopic.text = Topics.name(question.topic)
        binding.qText.text = question.question

        OptionViews.bind(
            container = binding.options,
            rows = OptionRows.build(question, session.picked, revealed, mock = false),
            enabled = !revealed,
            onPick = vm::pick,
        )

        renderExplanation(p, bank, question, revealed)
        renderFooter(p, session, question, revealed)
    }

    private fun renderHeader(
        p: ProgressState,
        session: SessionState,
        question: Question,
        revealed: Boolean,
    ) {
        val total = session.ids.size.coerceAtLeast(1)
        binding.qCounter.text =
            getString(R.string.quiz_counter, session.index + 1, session.ids.size)
        // A revealed question counts as done, so the bar moves on the answer, not the tap
        // that follows it.
        val answered = session.index + if (revealed) 1 else 0
        binding.qProgress.setPercent(
            (answered * 100.0 / total).roundToInt(), durationMs = Motion.BAR_QUIZ_MS,
        )

        val bookmarked = question.id in p.bookmarks
        binding.bookmark.setImageResource(
            if (bookmarked) R.drawable.ic_flag_filled else R.drawable.ic_flag_outline,
        )
        binding.bookmark.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (bookmarked) R.color.md_primary else R.color.md_outline,
            ),
        )
        binding.bookmark.contentDescription = getString(
            if (bookmarked) R.string.cd_bookmark_remove else R.string.cd_bookmark_add,
        )
    }

    private fun renderExplanation(
        p: ProgressState,
        bank: BankRepository.Bank,
        question: Question,
        revealed: Boolean,
    ) {
        val wasVisible = binding.explainCard.isVisible
        binding.explainCard.isVisible = revealed
        if (!revealed) return

        val explanation = bank.explanations[question.id]
        if (explanation != null && explanation.why.isNotBlank()) {
            binding.explainLabel.setText(R.string.quiz_why)
            binding.explainBody.text = explanation.why
            val hasTip = explanation.tip.isNotBlank()
            binding.explainDivider.isVisible = hasTip
            binding.explainTip.isVisible = hasTip
            binding.explainTip.text = explanation.tip
        } else {
            // Only 11 of the 501 questions are annotated, so a missing entry is normal:
            // show the answer itself instead of an empty panel.
            binding.explainLabel.setText(R.string.quiz_answer)
            binding.explainBody.text = question.options.getOrElse(question.answer) { "" }
            binding.explainDivider.isVisible = true
            binding.explainTip.isVisible = true
            binding.explainTip.text =
                getString(R.string.quiz_no_explanation, seenLine(p, question))
        }
        if (!wasVisible) Motion.rise(binding.explainCard)
    }

    private fun renderFooter(
        p: ProgressState,
        session: SessionState,
        question: Question,
        revealed: Boolean,
    ) {
        binding.nextCta.isVisible = revealed
        binding.skip.isVisible = !revealed
        binding.seenLine.isVisible = !revealed

        if (revealed) {
            val last = session.index + 1 >= session.ids.size
            binding.nextCta.setText(if (last) R.string.quiz_results else R.string.quiz_next)
        } else {
            binding.seenLine.text = seenLine(p, question)
        }
    }

    /** The design's status line: how often this question has come up, and how it went. */
    private fun seenLine(p: ProgressState, question: Question): String {
        val record = p.seen[question.id] ?: return getString(R.string.quiz_first_time)
        return if (record.m > 0) {
            getString(R.string.quiz_seen_missed, record.s, record.m)
        } else {
            getString(R.string.quiz_seen, record.s)
        }
    }

    /**
     * Shown on the parent manager, not the child one: both setFragmentResult and
     * setFragmentResultListener work through parentFragmentManager, so hosting the dialog
     * anywhere else posts the choice to a manager nobody is listening on.
     */
    private fun askToLeave() {
        ExitSessionDialog().show(parentFragmentManager, null)
    }

    private fun handOverToResults() {
        if (handedOver) return
        handedOver = true
        findNavController().navigate(
            R.id.resultsFragment,
            null,
            androidx.navigation.navOptions {
                popUpTo(R.id.quizFragment) { inclusive = true }
            },
        )
    }

    /**
     * Pops the existing home entry too, so leaving a session started from Practice does
     * not leave two home screens stacked behind each other.
     */
    private fun goHome() {
        findNavController().navigate(
            R.id.homeFragment,
            null,
            androidx.navigation.navOptions {
                popUpTo(R.id.homeFragment) { inclusive = true }
            },
        )
    }

    override fun onDestroyView() {
        current = null
        _binding = null
        super.onDestroyView()
    }
}
