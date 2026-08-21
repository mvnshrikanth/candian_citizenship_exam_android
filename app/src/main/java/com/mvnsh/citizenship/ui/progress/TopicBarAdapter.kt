package com.mvnsh.citizenship.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.databinding.ItemTopicBarBinding
import com.mvnsh.citizenship.domain.Stats

/**
 * One topic's **accuracy** bar.
 *
 * The Topics screen's bar shows coverage instead. Two different measures on purpose: one
 * answers "how much have I seen", this one answers "how well do I know it".
 */
class TopicBarAdapter : ListAdapter<Stats.TopicStat, TopicBarAdapter.VH>(DIFF) {

    class VH(val binding: ItemTopicBarBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTopicBarBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        topicName.text = item.name
        topicAccuracy.text = item.accuracyText
        topicBar.setPercent(item.accuracy, animate = false)
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Stats.TopicStat>() {
            override fun areItemsTheSame(oldItem: Stats.TopicStat, newItem: Stats.TopicStat) =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: Stats.TopicStat, newItem: Stats.TopicStat) =
                oldItem == newItem
        }
    }
}
