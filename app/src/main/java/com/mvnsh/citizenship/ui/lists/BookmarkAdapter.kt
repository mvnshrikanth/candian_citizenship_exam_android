package com.mvnsh.citizenship.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.databinding.ItemBookmarkBinding

/** One saved question. */
data class BookmarkRow(
    val id: Int,
    val topic: String,
    val question: String,
)

class BookmarkAdapter(
    private val onRemove: (BookmarkRow) -> Unit,
) : ListAdapter<BookmarkRow, BookmarkAdapter.VH>(DIFF) {

    class VH(val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        topic.text = item.topic
        question.text = item.question
        remove.setOnClickListener { onRemove(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<BookmarkRow>() {
            override fun areItemsTheSame(oldItem: BookmarkRow, newItem: BookmarkRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BookmarkRow, newItem: BookmarkRow) =
                oldItem == newItem
        }
    }
}
