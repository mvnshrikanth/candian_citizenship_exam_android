package com.mvnsh.citizenship.ui.quiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemMissedBinding

/** One question the session got wrong, with both answers side by side. */
data class MissedRow(
    val id: Int,
    val topic: String,
    val question: String,
    val yourAnswer: String,
    val correctAnswer: String,
)

class MissedAdapter : ListAdapter<MissedRow, MissedAdapter.VH>(DIFF) {

    class VH(val binding: ItemMissedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemMissedBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val ctx = root.context
        topic.text = item.topic
        question.text = item.question
        youSaid.text = ctx.getString(R.string.results_you_said, item.yourAnswer)
        correctWas.text = ctx.getString(R.string.results_correct_was, item.correctAnswer)
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MissedRow>() {
            override fun areItemsTheSame(oldItem: MissedRow, newItem: MissedRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MissedRow, newItem: MissedRow) =
                oldItem == newItem
        }
    }
}
