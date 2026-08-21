package com.mvnsh.citizenship.ui.mock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemMockAttemptBinding
import com.mvnsh.citizenship.domain.StudyEngine

/**
 * One past attempt.
 *
 * [number] is the attempt's position in the order it was taken, not its position in this
 * list: the newest is shown first but is still "Mock test 3" of three.
 */
data class MockAttemptRow(
    val number: Int,
    val date: String,
    val pct: Int,
)

class MockAttemptAdapter : ListAdapter<MockAttemptRow, MockAttemptAdapter.VH>(DIFF) {

    class VH(val binding: ItemMockAttemptBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemMockAttemptBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val ctx = root.context
        attemptName.text = ctx.getString(R.string.mock_attempt_name, item.number)
        attemptDate.text = item.date
        pct.text = ctx.getString(R.string.results_percent, item.pct)
        pct.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (item.pct >= PASS_PCT) R.color.md_on_secondary_container else R.color.md_error,
            ),
        )
    }

    private companion object {
        /** 15 of 20, expressed as the percentage the attempt is stored as. */
        val PASS_PCT = StudyEngine.MOCK_PASS_CORRECT * 100 / StudyEngine.MOCK_SIZE

        val DIFF = object : DiffUtil.ItemCallback<MockAttemptRow>() {
            override fun areItemsTheSame(oldItem: MockAttemptRow, newItem: MockAttemptRow) =
                oldItem.number == newItem.number

            override fun areContentsTheSame(oldItem: MockAttemptRow, newItem: MockAttemptRow) =
                oldItem == newItem
        }
    }
}
