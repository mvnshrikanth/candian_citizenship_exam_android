package com.mvnsh.citizenship.ui.mock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemNavChipBinding

/** One square in the navigator grid. */
data class NavChip(
    val index: Int,
    val answered: Boolean,
    val current: Boolean,
)

class NavChipAdapter(
    private val onClick: (Int) -> Unit,
) : ListAdapter<NavChip, NavChipAdapter.VH>(DIFF) {

    class VH(val binding: ItemNavChipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemNavChipBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val ctx = root.context
        chipNumber.text = (item.index + 1).toString()

        // Three states, in priority order: where you are, where you have been, the rest.
        val (fill, text) = when {
            item.current -> R.color.md_primary to R.color.md_on_primary
            item.answered -> R.color.md_primary_container to R.color.md_on_primary_container
            else -> R.color.md_surface_container_lowest to R.color.md_on_surface_variant
        }
        chip.setCardBackgroundColor(ContextCompat.getColor(ctx, fill))
        chip.strokeColor = ContextCompat.getColor(ctx, R.color.md_outline_variant)
        chip.strokeWidth = if (item.current || item.answered) {
            0
        } else {
            ctx.resources.getDimensionPixelSize(R.dimen.stroke_card)
        }
        chipNumber.setTextColor(ContextCompat.getColor(ctx, text))
        chipNumber.fontVariationSettings =
            if (item.current || item.answered) "'wght' 600" else "'wght' 400"

        chip.setOnClickListener { onClick(item.index) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NavChip>() {
            override fun areItemsTheSame(oldItem: NavChip, newItem: NavChip) =
                oldItem.index == newItem.index

            override fun areContentsTheSame(oldItem: NavChip, newItem: NavChip) =
                oldItem == newItem
        }
    }
}
