package com.mvnsh.citizenship.ui.practice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.databinding.ItemPracticeModeBinding

/**
 * One practice entry. The count is a formatted string rather than an Int so the adapter
 * never has to know which mode counts questions and which counts topics.
 */
data class PracticeMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String,
)

/**
 * The list pattern the rest of the app follows: a ListAdapter over an immutable row with
 * a stable identity field, submitted from the fragment's render. Rows are never mutated
 * in place and hold no logic - the click is handed straight back out.
 */
class PracticeModeAdapter(
    private val onClick: (PracticeMode) -> Unit,
) : ListAdapter<PracticeMode, PracticeModeAdapter.VH>(DIFF) {

    class VH(val binding: ItemPracticeModeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemPracticeModeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        title.text = item.title
        subtitle.text = item.subtitle
        meta.text = item.meta
        row.setOnClickListener { onClick(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PracticeMode>() {
            override fun areItemsTheSame(oldItem: PracticeMode, newItem: PracticeMode) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PracticeMode, newItem: PracticeMode) =
                oldItem == newItem
        }
    }
}
