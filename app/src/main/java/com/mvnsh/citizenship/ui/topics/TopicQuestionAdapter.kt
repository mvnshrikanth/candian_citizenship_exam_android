package com.mvnsh.citizenship.ui.topics

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemTopicQuestionBinding

/** One question inside a topic, with how it has gone so far. */
data class TopicQuestionRow(
    val id: Int,
    val text: String,
    val status: Status,
    val misses: Int,
    val bookmarked: Boolean,
) {
    enum class Status { UNSEEN, CORRECT, MISSED }
}

class TopicQuestionAdapter(
    private val onFlagClick: (TopicQuestionRow) -> Unit,
) : ListAdapter<TopicQuestionRow, TopicQuestionAdapter.VH>(DIFF) {

    class VH(val binding: ItemTopicQuestionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTopicQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val ctx = root.context
        question.text = item.text

        when (item.status) {
            TopicQuestionRow.Status.UNSEEN -> {
                dot.setBackgroundResource(R.drawable.bg_dot_ring)
                dot.backgroundTintList = null
                status.setText(R.string.q_status_new)
            }

            TopicQuestionRow.Status.CORRECT -> {
                dot.setBackgroundResource(R.drawable.bg_dot_filled)
                dot.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_secondary))
                status.setText(R.string.q_status_correct)
            }

            TopicQuestionRow.Status.MISSED -> {
                dot.setBackgroundResource(R.drawable.bg_dot_filled)
                dot.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_error))
                status.text = ctx.getString(R.string.q_status_missed, item.misses)
            }
        }

        flag.setImageResource(
            if (item.bookmarked) R.drawable.ic_flag_filled else R.drawable.ic_flag_outline,
        )
        flag.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (item.bookmarked) R.color.md_primary else R.color.md_outline,
            ),
        )
        // State-dependent, so TalkBack says what the tap will do rather than what it is.
        flag.contentDescription = ctx.getString(
            if (item.bookmarked) R.string.cd_bookmark_remove else R.string.cd_bookmark_add,
        )
        flag.setOnClickListener { onFlagClick(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TopicQuestionRow>() {
            override fun areItemsTheSame(oldItem: TopicQuestionRow, newItem: TopicQuestionRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TopicQuestionRow, newItem: TopicQuestionRow) =
                oldItem == newItem
        }
    }
}
