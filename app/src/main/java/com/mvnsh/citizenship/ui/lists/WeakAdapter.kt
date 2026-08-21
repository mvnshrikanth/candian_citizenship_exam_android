package com.mvnsh.citizenship.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemWeakBinding

/** One question that keeps coming back wrong. */
data class WeakRow(
    val id: Int,
    val topic: String,
    val question: String,
    val misses: Int,
)

class WeakAdapter(
    private val onBookmark: (WeakRow) -> Unit,
    private val onKnown: (WeakRow) -> Unit,
) : ListAdapter<WeakRow, WeakAdapter.VH>(DIFF) {

    class VH(val binding: ItemWeakBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemWeakBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        topic.text = item.topic
        question.text = item.question
        missed.text = root.context.getString(R.string.weak_missed, item.misses)
        bookmark.setOnClickListener { onBookmark(item) }
        known.setOnClickListener { onKnown(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WeakRow>() {
            override fun areItemsTheSame(oldItem: WeakRow, newItem: WeakRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: WeakRow, newItem: WeakRow) =
                oldItem == newItem
        }
    }
}
