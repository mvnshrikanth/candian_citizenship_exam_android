package com.mvnsh.citizenship.ui.lists

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
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.databinding.FragmentWeakBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The questions missed twice or more, and the two ways off the list. */
class WeakFragment : Fragment(R.layout.fragment_weak) {

    private var _binding: FragmentWeakBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = WeakAdapter(
        onBookmark = { row -> vm.toggleBookmark(row.id) },
        onKnown = { row -> vm.clearWeak(row.id) },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentWeakBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.scroll.applyBottomInset()
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }

        binding.weakList.layoutManager = LinearLayoutManager(requireContext())
        binding.weakList.adapter = adapter
        binding.weakList.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.practiseWeak.setOnClickListener { start(StudyEngine.Mode.WEAK) }

        // The empty state's one way out is a quick session, not this empty list again.
        binding.empty.emptyTitle.setText(R.string.weak_empty_title)
        binding.empty.emptyBody.setText(R.string.weak_empty_body)
        binding.empty.emptyCta.setText(R.string.weak_empty_cta)
        binding.empty.emptyIcon.setImageResource(R.drawable.ic_check)
        binding.empty.emptyDisc.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_secondary_container),
        )
        binding.empty.emptyIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_on_secondary_container_strong),
        )
        binding.empty.emptyCta.setOnClickListener { start(StudyEngine.Mode.QUICK) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) -> render(p, bank) }
            }
        }
    }

    private fun render(p: ProgressState, bank: BankRepository.Bank?) {
        val rows = StudyEngine.weakIds(p).mapNotNull { id ->
            val q = bank?.byId?.get(id) ?: return@mapNotNull null
            WeakRow(
                id = id,
                topic = Topics.name(q.topic),
                question = DateUtils.clip(q.question, QUESTION_CLIP),
                misses = p.seen[id]?.m ?: 0,
            )
        }

        val populated = rows.isNotEmpty()
        binding.weakHeadline.isVisible = populated
        binding.practiseWeak.isVisible = populated
        binding.weakList.isVisible = populated
        binding.empty.emptyState.isVisible = !populated

        if (populated) {
            binding.weakHeadline.text = getString(R.string.weak_headline, rows.size)
        }
        adapter.submitList(rows)
    }

    private fun start(mode: StudyEngine.Mode) {
        vm.startSession(mode)
        if (vm.progress.value.session != null) findNavController().navigate(R.id.quizFragment)
    }

    override fun onDestroyView() {
        binding.weakList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val QUESTION_CLIP = 82
    }
}
