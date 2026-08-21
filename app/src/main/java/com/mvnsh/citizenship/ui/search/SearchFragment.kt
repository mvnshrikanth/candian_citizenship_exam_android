package com.mvnsh.citizenship.ui.search

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.databinding.FragmentSearchBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Free-text search over the whole bank, by question wording or by answer. */
@OptIn(FlowPreview::class)
class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val query = MutableStateFlow("")

    private val adapter = SearchAdapter { hit -> vm.toggleBookmark(hit.id) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.screen.applyBottomInset()  // see SettingsFragment: the scroll container must end above the navigation bar
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }
        binding.clear.setOnClickListener { binding.query.setText("") }
        binding.query.doAfterTextChanged { query.value = it?.toString().orEmpty() }

        binding.hits.layoutManager = LinearLayoutManager(requireContext())
        binding.hits.adapter = adapter
        binding.hits.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.empty.emptyTitle.setText(R.string.search_empty_title)
        binding.empty.emptyBody.setText(R.string.search_empty_body)
        binding.empty.emptyCta.setText(R.string.search_browse_topics)
        binding.empty.emptyIcon.setImageResource(R.drawable.ic_search)
        binding.empty.emptyDisc.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_surface_container),
        )
        binding.empty.emptyIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.md_on_surface_variant),
        )
        binding.empty.emptyCta.setOnClickListener {
            findNavController().navigate(R.id.topicsFragment)
        }

        // The result state is debounced, so without this the empty state and the list
        // would both be on screen for the first frame or two.
        binding.hits.isVisible = false
        binding.empty.emptyState.isVisible = false

        // The hint counts the bank, not the query, so it is not part of the debounced
        // result state - it would otherwise stay blank until the first keystroke settles.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.bank.collect { bank ->
                    binding.query.hint =
                        getString(R.string.search_hint, bank?.questions?.size ?: 0)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The scan is 501 questions x 5 strings. Cheap, but not free on every
                // keystroke, so it is debounced and run off the main thread.
                val results = query
                    .debounce(QUERY_DEBOUNCE_MS)
                    .combine(vm.bank) { text, bank -> text to bank }
                    .map { (text, bank) -> text to search(text, bank) }
                    .flowOn(Dispatchers.Default)

                combine(results, vm.progress) { (text, matches), p ->
                    Triple(text, matches, p)
                }.collect { (text, matches, p) -> render(text, matches, p) }
            }
        }
    }

    /** Case-insensitive contains over the question and every option, capped. */
    private fun search(text: String, bank: BankRepository.Bank?): List<Question> {
        val needle = text.trim().lowercase()
        if (needle.length < MIN_QUERY || bank == null) return emptyList()
        return bank.questions
            .asSequence()
            .filter { q ->
                q.question.contains(needle, ignoreCase = true) ||
                    q.options.any { it.contains(needle, ignoreCase = true) }
            }
            .take(MAX_HITS)
            .toList()
    }

    private fun render(text: String, matches: List<Question>, p: ProgressState) {
        binding.clear.isVisible = text.isNotEmpty()

        val idle = text.trim().length < MIN_QUERY
        binding.idleHint.isVisible = idle
        binding.hitCount.isVisible = !idle && matches.isNotEmpty()
        binding.hits.isVisible = !idle && matches.isNotEmpty()
        binding.empty.emptyState.isVisible = !idle && matches.isEmpty()

        if (idle) {
            adapter.submitList(emptyList())
            return
        }

        // The cap means a full page is "30+", never a number that claims to be exact.
        binding.hitCount.text = if (matches.size == MAX_HITS) {
            getString(R.string.search_results_capped, MAX_HITS)
        } else {
            getString(R.string.search_results, matches.size)
        }

        adapter.submitList(
            matches.map { q ->
                SearchHit(
                    id = q.id,
                    topic = Topics.name(q.topic),
                    question = DateUtils.clip(q.question, QUESTION_CLIP),
                    answer = q.options.getOrElse(q.answer) { "" },
                    bookmarked = q.id in p.bookmarks,
                )
            },
        )
    }

    override fun onDestroyView() {
        binding.hits.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MIN_QUERY = 2
        const val MAX_HITS = 30
        const val QUERY_DEBOUNCE_MS = 150L
        const val QUESTION_CLIP = 86
    }
}
