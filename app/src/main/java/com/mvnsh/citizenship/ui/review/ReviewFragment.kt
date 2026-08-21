package com.mvnsh.citizenship.ui.review

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
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.FragmentReviewBinding
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Every question from the submitted mock, in the order it was asked. */
class ReviewFragment : Fragment(R.layout.fragment_review) {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = ReviewAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentReviewBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.reviewList.applyBottomInset()
        Motion.rise(binding.reviewList)

        binding.back.setOnClickListener { findNavController().navigateUp() }

        binding.reviewList.layoutManager = LinearLayoutManager(requireContext())
        binding.reviewList.adapter = adapter
        binding.reviewList.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.session, vm.bank, ::Pair).collect { (session, bank) ->
                    if (session != null && bank != null) render(session, bank)
                }
            }
        }
    }

    private fun render(session: SessionState, bank: BankRepository.Bank) {
        val rows = session.ids.mapIndexedNotNull { index, id ->
            val q = bank.byId[id] ?: return@mapIndexedNotNull null
            val mark = session.marks[id]
            ReviewRow(
                id = id,
                position = index + 1,
                topic = Topics.name(q.topic),
                question = q.question,
                yourAnswer = mark?.let { q.options.getOrNull(it) },
                correctAnswer = q.options.getOrElse(q.answer) { "" },
                why = bank.explanations[id]?.why.orEmpty(),
                correct = mark == q.answer,
            )
        }

        binding.reviewTitle.text = getString(
            R.string.review_title,
            rows.count { it.correct },
            rows.size,
        )
        adapter.submitList(rows)
    }

    override fun onDestroyView() {
        binding.reviewList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
