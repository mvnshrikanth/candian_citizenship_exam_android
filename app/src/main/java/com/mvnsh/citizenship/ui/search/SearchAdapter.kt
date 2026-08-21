package com.mvnsh.citizenship.ui.search

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemSearchHitBinding

/** One search hit, showing the answer so the fact can be read without opening it. */
data class SearchHit(
    val id: Int,
    val topic: String,
    val question: String,
    val answer: String,
    val bookmarked: Boolean,
)

class SearchAdapter(
    private val onFlag: (SearchHit) -> Unit,
) : ListAdapter<SearchHit, SearchAdapter.VH>(DIFF) {

    class VH(val binding: ItemSearchHitBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSearchHitBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val ctx = root.context
        topic.text = item.topic
        question.text = item.question
        answer.text = item.answer

        flag.setImageResource(
            if (item.bookmarked) R.drawable.ic_flag_filled else R.drawable.ic_flag_outline,
        )
        flag.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (item.bookmarked) R.color.md_primary else R.color.md_outline,
            ),
        )
        flag.contentDescription = ctx.getString(
            if (item.bookmarked) R.string.cd_bookmark_remove else R.string.cd_bookmark_add,
        )
        flag.setOnClickListener { onFlag(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchHit>() {
            override fun areItemsTheSame(oldItem: SearchHit, newItem: SearchHit) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SearchHit, newItem: SearchHit) =
                oldItem == newItem
        }
    }
}
