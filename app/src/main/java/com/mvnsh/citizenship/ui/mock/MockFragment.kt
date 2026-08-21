package com.mvnsh.citizenship.ui.mock

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
import androidx.navigation.navOptions
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.FragmentMockBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import com.mvnsh.citizenship.ui.quiz.OptionRows
import com.mvnsh.citizenship.ui.quiz.OptionViews
import com.mvnsh.citizenship.ui.settings.ExitSessionDialog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The timed run. No feedback until submit, free movement between questions, and a clock
 * driven by an absolute deadline so backgrounding the app does not pause it.
 */
class MockFragment : Fragment(R.layout.fragment_mock) {

    private var _binding: FragmentMockBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private var handedOver = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMockBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.footer.applyBottomInset()
        Motion.rise(binding.content)

        binding.exit.setOnClickListener { askToLeave() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = askToLeave()
            },
        )

        binding.navigator.setOnClickListener {
            NavigatorSheet().show(parentFragmentManager, null)
        }
        binding.mockBack.setOnClickListener {
            vm.goTo((vm.progress.value.session?.index ?: 0) - 1)
        }
        binding.mockNext.setOnClickListener {
            vm.goTo((vm.progress.value.session?.index ?: 0) + 1)
        }
        binding.mockSubmit.setOnClickListener { vm.submitMock() }

        setFragmentResultListener(NavigatorSheet.REQUEST_KEY) { _, bundle ->
            if (bundle.getBoolean(NavigatorSheet.KEY_SUBMIT)) vm.submitMock()
        }
        setFragmentResultListener(ExitSessionDialog.REQUEST_KEY) { _, bundle ->
            when (bundle.getString(ExitSessionDialog.KEY_CHOICE)) {
                ExitSessionDialog.RESULT_SAVE -> goHome()
                ExitSessionDialog.RESULT_DISCARD -> {
                    vm.discardSession()
                    goHome()
                }

                else -> Unit
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.session, vm.bank, ::Pair).collect { (session, bank) ->
                    render(session, bank)
                }
            }
        }
        // The clock is collected separately so a tick redraws the pill and nothing else;
        // rebuilding the option rows twice a second would fight every tap.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.mockSecondsLeft.collect(::renderTimer)
            }
        }
    }

    private fun render(session: SessionState?, bank: BankRepository.Bank?) {
        if (session == null || bank == null) return
        if (session.submitted) {
            handOverToResults()
            return
        }

        val question = bank.byId[session.ids.getOrNull(session.index)] ?: return
        binding.qCounter.text =
            getString(R.string.quiz_counter, session.index + 1, session.ids.size)
        binding.qTopic.text = Topics.name(question.topic)
        binding.qText.text = question.question

        OptionViews.bind(
            container = binding.options,
            rows = OptionRows.build(question, session.marks[question.id], revealed = false, mock = true),
            enabled = true,
            onPick = vm::pick,
        )

        val last = session.index + 1 >= session.ids.size
        val answered = session.ids.count { it in session.marks }
        binding.mockBack.isVisible = session.index > 0
        binding.mockNext.isVisible = !last
        binding.mockSubmit.isVisible = last
        binding.mockSubmit.text = if (answered == session.ids.size) {
            getString(R.string.mock_submit)
        } else {
            getString(R.string.mock_submit_partial, answered, session.ids.size)
        }
    }

    private fun renderTimer(secondsLeft: Int) {
        binding.timer.text = DateUtils.mmss(secondsLeft)

        val low = secondsLeft in 1 until LOW_TIME_SECONDS
        binding.timerPill.setCardBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (low) R.color.md_error_container else R.color.md_surface_container,
            ),
        )
        binding.timer.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (low) R.color.md_on_error_container else R.color.md_on_surface_variant,
            ),
        )
    }

    private fun askToLeave() {
        ExitSessionDialog().show(parentFragmentManager, null)
    }

    private fun handOverToResults() {
        if (handedOver) return
        handedOver = true
        findNavController().navigate(
            R.id.mockResultsFragment,
            null,
            navOptions { popUpTo(R.id.mockFragment) { inclusive = true } },
        )
    }

    private fun goHome() {
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

    private companion object {
        /** The design turns the pill red under five minutes. */
        const val LOW_TIME_SECONDS = 300
    }
}
