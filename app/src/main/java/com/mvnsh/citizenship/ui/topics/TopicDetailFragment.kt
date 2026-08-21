package com.mvnsh.citizenship.ui.topics

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
import com.mvnsh.citizenship.databinding.FragmentTopicDetailBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.domain.Topics
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.min

/** One topic's coverage, plus a readable slice of the questions inside it. */
class TopicDetailFragment : Fragment(R.layout.fragment_topic_detail) {

    private var _binding: FragmentTopicDetailBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val topicKey: String by lazy {
        requireNotNull(arguments?.getString(ARG_TOPIC_KEY)) { "topicDetailFragment needs a topicKey" }
    }

    private val adapter = TopicQuestionAdapter { row -> vm.toggleBookmark(row.id) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTopicDetailBinding.bind(view)
        binding.appBar.applyTopInset()
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }
        binding.topicName.text = Topics.name(topicKey)
        binding.topicBlurb.text = Topics.find(topicKey)?.blurb.orEmpty()

        binding.questions.layoutManager = LinearLayoutManager(requireContext())
        binding.questions.adapter = adapter
        binding.questions.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list)),
        )

        binding.practiseCta.setOnClickListener {
            vm.startSession(StudyEngine.Mode.TOPIC, topicKey)
            if (vm.progress.value.session != null) findNavController().navigate(R.id.quizFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) -> render(p, bank) }
            }
        }
    }

    private fun render(p: ProgressState, bank: BankRepository.Bank?) {
        val questions = bank?.questions?.filter { it.topic == topicKey }.orEmpty()
        val stat = bank?.let { loaded ->
            Stats.topicStats(loaded.questions, p).firstOrNull { it.key == topicKey }
        }

        binding.catTotal.text = questions.size.toString()
        binding.catDone.text = (stat?.done ?: 0).toString()
        binding.catAccuracy.text = stat?.accuracyText ?: getString(R.string.stat_none)
        binding.coverageBar.setPercent(stat?.coveragePct ?: 0)

        // The CTA promises what it will actually deliver, so a small topic says so.
        val sessionSize = min(StudyEngine.SESSION_CAP, questions.size)
        binding.practiseCta.text = getString(R.string.topic_practise_cta, sessionSize)
        binding.practiseCta.isEnabled = sessionSize > 0

        adapter.submitList(
            questions.take(QUESTION_PREVIEW).map { q ->
                val record = p.seen[q.id]
                TopicQuestionRow(
                    id = q.id,
                    text = DateUtils.clip(q.question, QUESTION_CLIP),
                    status = when {
                        record == null -> TopicQuestionRow.Status.UNSEEN
                        record.m > 0 -> TopicQuestionRow.Status.MISSED
                        else -> TopicQuestionRow.Status.CORRECT
                    },
                    misses = record?.m ?: 0,
                    bookmarked = q.id in p.bookmarks,
                )
            },
        )
    }

    override fun onDestroyView() {
        binding.questions.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ARG_TOPIC_KEY = "topicKey"

        /** The design lists a readable slice, not the whole topic. */
        const val QUESTION_PREVIEW = 24
        const val QUESTION_CLIP = 74
    }
}
