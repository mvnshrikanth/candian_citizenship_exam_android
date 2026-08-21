package com.mvnsh.citizenship.ui.topics

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.FragmentTopicsBinding
import com.mvnsh.citizenship.domain.Stats
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.SpacingDecoration
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TopicsFragment : Fragment(R.layout.fragment_topics) {

    private var _binding: FragmentTopicsBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = TopicRowAdapter { topic ->
        findNavController().navigate(R.id.topicDetailFragment, bundleOf("topicKey" to topic.key))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTopicsBinding.bind(view)
        binding.scroll.applyTopInset()
        Motion.rise(binding.content)

        binding.topics.layoutManager = LinearLayoutManager(requireContext())
        binding.topics.adapter = adapter
        binding.topics.addItemDecoration(
            SpacingDecoration(resources.getDimensionPixelSize(R.dimen.gap_list_wide)),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) ->
                    // Stats.topicStats returns all seven in the design's order, including
                    // the untouched ones, so an empty topic still gets a row.
                    adapter.submitList(bank?.let { Stats.topicStats(it.questions, p) }.orEmpty())
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.topics.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
