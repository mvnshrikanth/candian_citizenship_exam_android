package com.mvnsh.citizenship.ui.review

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemReviewBinding

/** One question from a submitted mock, with how it went. */
data class ReviewRow(
    val id: Int,
    val position: Int,
    val topic: String,
    val question: String,
    val yourAnswer: String?,
    val correctAnswer: String,
    val why: String,
    val correct: Boolean,
)

class ReviewAdapter : ListAdapter<ReviewRow, ReviewAdapter.VH>(DIFF) {

    class VH(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val ctx = root.context

        statusDisc.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (item.correct) R.color.md_secondary else R.color.md_error,
            ),
        )
        statusIcon.setImageResource(
            if (item.correct) R.drawable.ic_check else R.drawable.ic_close,
        )
        statusIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (item.correct) R.color.md_on_secondary else R.color.md_on_error,
            ),
        )

        this.position.text =
            ctx.getString(R.string.review_position, item.position, item.topic)
        question.text = item.question

        // A right answer needs no "you said" line - it would just repeat the one below.
        youSaid.isVisible = !item.correct
        if (!item.correct) {
            youSaid.text = ctx.getString(
                R.string.results_you_said,
                item.yourAnswer ?: ctx.getString(R.string.review_not_answered),
            )
        }
        correctWas.text = ctx.getString(R.string.results_correct_was, item.correctAnswer)

        why.isVisible = item.why.isNotBlank()
        why.text = item.why
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ReviewRow>() {
            override fun areItemsTheSame(oldItem: ReviewRow, newItem: ReviewRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ReviewRow, newItem: ReviewRow) =
                oldItem == newItem
        }
    }
}
