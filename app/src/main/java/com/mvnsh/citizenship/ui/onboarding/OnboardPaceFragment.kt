package com.mvnsh.citizenship.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.databinding.FragmentOnboardPaceBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import kotlinx.coroutines.launch

/** Test date and daily goal. Both optional, both changeable later in settings. */
class OnboardPaceFragment : Fragment(R.layout.fragment_onboard_pace) {

    private var _binding: FragmentOnboardPaceBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOnboardPaceBinding.bind(view)
        Motion.rise(binding.root)

        binding.obPaceBack.setOnClickListener { onboardingHost().back() }
        binding.obPaceNext.setOnClickListener { onboardingHost().goToPage(2) }
        binding.obPaceSkip.setOnClickListener { onboardingHost().finish() }

        binding.dateMonth.setOnClickListener { vm.setTestDate(DateUtils.shiftDay(DAYS_MONTH)) }
        binding.dateQuarter.setOnClickListener { vm.setTestDate(DateUtils.shiftDay(DAYS_QUARTER)) }
        binding.dateNone.setOnClickListener { vm.setTestDate(null) }

        binding.goal10.setOnClickListener { vm.setGoalTarget(10) }
        binding.goal20.setOnClickListener { vm.setGoalTarget(20) }
        binding.goal30.setOnClickListener { vm.setGoalTarget(30) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.progress.collect(::render)
            }
        }
    }

    private fun render(p: ProgressState) {
        val month = DateUtils.shiftDay(DAYS_MONTH)
        val quarter = DateUtils.shiftDay(DAYS_QUARTER)

        setChoice(binding.dateMonth, binding.dateMonthLabel, binding.dateMonthMark, p.testDate == month)
        setChoice(binding.dateQuarter, binding.dateQuarterLabel, binding.dateQuarterMark, p.testDate == quarter)
        setChoice(binding.dateNone, binding.dateNoneLabel, binding.dateNoneMark, p.testDate == null)

        setGoal(binding.goal10, binding.goal10Label, p.goalTarget == 10)
        setGoal(binding.goal20, binding.goal20Label, p.goalTarget == 20)
        setGoal(binding.goal30, binding.goal30Label, p.goalTarget == 30)
    }

    private fun setChoice(
        card: MaterialCardView,
        label: android.widget.TextView,
        mark: android.widget.ImageView,
        selected: Boolean,
    ) {
        val ctx = requireContext()
        card.isChecked = selected
        card.setCardBackgroundColor(
            ContextCompat.getColor(
                ctx,
                if (selected) R.color.md_primary_container else R.color.md_surface_container_lowest,
            ),
        )
        card.strokeWidth = if (selected) 0 else resources.getDimensionPixelSize(R.dimen.stroke_card)
        label.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (selected) R.color.md_on_primary_container else R.color.md_on_surface,
            ),
        )
        label.setTypefaceWeight(if (selected) 600 else 400)
        mark.setImageResource(if (selected) R.drawable.ic_choice_selected else R.drawable.bg_choice_ring)
    }

    private fun setGoal(card: MaterialCardView, label: android.widget.TextView, selected: Boolean) {
        val ctx = requireContext()
        card.isChecked = selected
        card.setCardBackgroundColor(
            ContextCompat.getColor(
                ctx,
                if (selected) R.color.md_secondary else R.color.md_surface_container_lowest,
            ),
        )
        card.strokeWidth = if (selected) 0 else resources.getDimensionPixelSize(R.dimen.stroke_card)
        label.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (selected) R.color.md_on_secondary else R.color.md_on_surface_variant,
            ),
        )
        label.setTypefaceWeight(if (selected) 600 else 400)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DAYS_MONTH = 30
        const val DAYS_QUARTER = 90
    }
}

/** Drives the variable font's wght axis, which is how the design varies weight. */
internal fun android.widget.TextView.setTypefaceWeight(weight: Int) {
    fontVariationSettings = "'wght' $weight"
}
