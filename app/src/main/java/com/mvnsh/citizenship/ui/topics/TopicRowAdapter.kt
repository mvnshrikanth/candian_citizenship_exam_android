package com.mvnsh.citizenship.ui.topics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemTopicRowBinding
import com.mvnsh.citizenship.domain.Stats

/**
 * One topic. The bar here shows **coverage** - how much of the topic has been seen.
 * The Progress screen's per-topic bar shows accuracy instead; they are deliberately
 * different measures and swapping one for the other looks right and reads wrong.
 */
class TopicRowAdapter(
    private val onClick: (Stats.TopicStat) -> Unit,
) : ListAdapter<Stats.TopicStat, TopicRowAdapter.VH>(DIFF) {

    class VH(val binding: ItemTopicRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTopicRowBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        name.text = item.name
        blurb.text = item.blurb
        accuracy.text = item.accuracyText
        counts.text = root.context.getString(R.string.topic_counts, item.done, item.total)
        // Recycled rows must not replay the growth animation.
        coverageBar.setPercent(item.coveragePct, animate = false)
        row.setOnClickListener { onClick(item) }
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
