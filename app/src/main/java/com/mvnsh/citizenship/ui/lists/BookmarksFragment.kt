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
import com.mvnsh.citizenship.databinding.FragmentBookmarksBinding
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

/** Saved questions, in the order they were saved. */
class BookmarksFragment : Fragment(R.layout.fragment_bookmarks) {

    private var _binding: FragmentBookmarksBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = BookmarkAdapter { row -> vm.toggleBookmark(row.id) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentBookmarksBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.scroll.applyBottomInset()
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }

        binding.bookmarksList.layoutManager = LinearLayoutManager(requireContext())
        binding.bookmarksList.adapter = adapter
        binding.bookmarksList.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.practiseBookmarks.setOnClickListener {
            vm.startSession(StudyEngine.Mode.MARKS)
            if (vm.progress.value.session != null) findNavController().navigate(R.id.quizFragment)
        }

        binding.empty.emptyTitle.setText(R.string.bookmarks_empty_title)
        binding.empty.emptyBody.setText(R.string.bookmarks_empty_body)
        binding.empty.emptyIcon.setImageResource(R.drawable.ic_flag_outline)
        binding.empty.emptyDisc.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_surface_container),
        )
        binding.empty.emptyIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_on_surface_variant),
        )
        // Nothing to offer here: the flag lives on the questions, not on this screen.
        binding.empty.emptyCta.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) -> render(p, bank) }
            }
        }
    }

    private fun render(p: ProgressState, bank: BankRepository.Bank?) {
        // A bookmark whose question the bank no longer carries is dropped rather than
        // rendered blank, matching what a session built from the same list would do.
        val rows = p.bookmarks.mapNotNull { id ->
            val q = bank?.byId?.get(id) ?: return@mapNotNull null
            BookmarkRow(
                id = id,
                topic = Topics.name(q.topic),
                question = DateUtils.clip(q.question, QUESTION_CLIP),
            )
        }

        val populated = rows.isNotEmpty()
        binding.practiseBookmarks.isVisible = populated
        binding.bookmarksList.isVisible = populated
        binding.empty.emptyState.isVisible = !populated
        adapter.submitList(rows)
    }

    override fun onDestroyView() {
        binding.bookmarksList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val QUESTION_CLIP = 82
    }
}
