package com.mvnsh.citizenship.ui.practice

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.databinding.FragmentPracticeBinding
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The six ways into the bank, each showing how much of it the mode currently covers. */
class PracticeFragment : Fragment(R.layout.fragment_practice) {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = PracticeModeAdapter(::onModeClicked)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPracticeBinding.bind(view)
        binding.scroll.applyTopInset()
        Motion.rise(binding.content)

        binding.modes.layoutManager = LinearLayoutManager(requireContext())
        binding.modes.adapter = adapter
        binding.modes.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.searchEntry.setOnClickListener { findNavController().navigate(R.id.searchFragment) }
        binding.mockEntry.setOnClickListener { findNavController().navigate(R.id.mockIntroFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) -> render(p, bank) }
            }
        }
    }

    private fun render(p: ProgressState, bank: BankRepository.Bank?) {
        val questions = bank?.questions.orEmpty()
        val unseen = questions.count { it.id !in p.seen }
        // Only ids the bank still carries can be practised, so both lists are filtered.
        val ids = questions.mapTo(HashSet()) { it.id }

        adapter.submitList(
            listOf(
                PracticeMode(
                    id = QUICK,
                    title = getString(R.string.pm_quick_title),
                    subtitle = getString(R.string.pm_quick_sub),
                    meta = StudyEngine.QUICK_SIZE.toString(),
                ),
                PracticeMode(
                    id = ALL,
                    title = getString(R.string.pm_all_title),
                    subtitle = getString(R.string.pm_all_sub),
                    meta = questions.size.toString(),
                ),
                PracticeMode(
                    id = WEAK,
                    title = getString(R.string.pm_weak_title),
                    subtitle = getString(R.string.pm_weak_sub),
                    meta = StudyEngine.weakIds(p).count { it in ids }.toString(),
                ),
                PracticeMode(
                    id = MARKS,
                    title = getString(R.string.pm_marks_title),
                    subtitle = getString(R.string.pm_marks_sub),
                    meta = p.bookmarks.count { it in ids }.toString(),
                ),
                PracticeMode(
                    id = UNSEEN,
                    title = getString(R.string.pm_unseen_title),
                    subtitle = getString(R.string.pm_unseen_sub),
                    meta = unseen.toString(),
                ),
                PracticeMode(
                    id = TOPIC,
                    title = getString(R.string.pm_topic_title),
                    subtitle = getString(R.string.pm_topic_sub),
                    meta = Topics.all.size.toString(),
                ),
            ),
        )
    }

    /**
     * Weak and bookmarked open their lists rather than starting a session: the design
     * lets you read and prune those before committing to practising them.
     */
    private fun onModeClicked(mode: PracticeMode) = when (mode.id) {
        WEAK -> findNavController().navigate(R.id.weakFragment)
        MARKS -> findNavController().navigate(R.id.bookmarksFragment)
        TOPIC -> findNavController().navigate(R.id.topicsFragment)
        QUICK -> startSession(StudyEngine.Mode.QUICK)
        ALL -> startSession(StudyEngine.Mode.ALL)
        UNSEEN -> startSession(StudyEngine.Mode.UNSEEN)
        else -> Unit
    }

    private fun startSession(mode: StudyEngine.Mode) {
        vm.startSession(mode)
        // startSession refuses an empty source and says so, leaving no session behind.
        if (vm.progress.value.session != null) findNavController().navigate(R.id.quizFragment)
    }

    override fun onDestroyView() {
        binding.modes.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val QUICK = "quick"
        const val ALL = "all"
        const val WEAK = "weak"
        const val MARKS = "marks"
        const val UNSEEN = "unseen"
        const val TOPIC = "topic"
    }
}
