package com.mvnsh.citizenship.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.FragmentHomeBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import com.mvnsh.citizenship.domain.StudyEngine
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The screen every other screen is shaped like: bind in [onViewCreated], register every
 * listener exactly once outside the render pass, and drive all text and visibility from
 * a single [render] so there is one place to read when a value looks wrong.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    /** The topic the weakest-topic card currently points at, or null while it is hidden. */
    private var weakestKey: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)
        binding.scroll.applyTopInset()
        Motion.rise(binding.content)

        binding.searchButton.setOnClickListener { navigate(R.id.searchFragment) }
        binding.settingsButton.setOnClickListener { navigate(R.id.settingsFragment) }
        binding.noDateCard.setOnClickListener { navigate(R.id.settingsFragment) }
        binding.weakTile.setOnClickListener { navigate(R.id.weakFragment) }
        binding.mockTile.setOnClickListener { navigate(R.id.mockIntroFragment) }

        binding.heroCta.setOnClickListener {
            vm.resumeOrStart()
            // Guard the navigation rather than the tap: if the bank has not parsed yet
            // there is no session to show, and an empty quiz screen is worse than nothing.
            if (vm.progress.value.session != null) navigate(R.id.quizFragment)
        }

        binding.weakestCard.setOnClickListener {
            val key = weakestKey ?: return@setOnClickListener
            navigate(R.id.topicDetailFragment, bundleOf("topicKey" to key))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.session, vm.bank, ::Triple).collect { (p, s, b) ->
                    render(p, s, b)
                }
            }
        }
    }

    private fun render(p: ProgressState, session: SessionState?, bank: BankRepository.Bank?) {
        val today = DateUtils.today()

        binding.greeting.setText(greetingRes())
        binding.dayLine.text =
            if (p.streak > 0) getString(R.string.home_day_line, p.streak)
            else getString(R.string.home_ready)

        renderGoal(p, today)
        renderHero(p, session)

        binding.weakCount.text = StudyEngine.weakIds(p).size.toString()
        binding.mockSub.text = getString(
            R.string.home_mock_sub, StudyEngine.MOCK_SIZE, StudyEngine.MOCK_SECONDS / 60,
        )

        renderTestDate(p)
        renderWeakestTopic(p, bank)
    }

    private fun renderGoal(p: ProgressState, today: String) {
        // goalDone belongs to goalDate. Until the next answer rolls it forward, a
        // yesterday-stamped counter means nothing has been done today.
        val done = if (p.goalDate == today) p.goalDone else 0
        val target = max(1, p.goalTarget)
        val left = (target - done).coerceAtLeast(0)

        binding.goalCount.text = done.toString()
        binding.goalTarget.text = getString(R.string.home_goal_target, p.goalTarget)
        binding.goalBar.setPercent(min(100, (done * 100.0 / target).roundToInt()))
        binding.goalBar.contentDescription =
            getString(R.string.cd_goal_progress, done, p.goalTarget)

        binding.goalFooter.text =
            if (left == 0) getString(R.string.home_goal_met)
            else getString(R.string.home_goal_left, left, minutesFor(left))

        val hasStreak = p.streak > 0
        binding.streakFlame.isVisible = hasStreak
        binding.streakText.isVisible = hasStreak
        if (hasStreak) binding.streakText.text = getString(R.string.home_streak_days, p.streak)
    }

    private fun renderHero(p: ProgressState, session: SessionState?) {
        val resumable = session != null && !session.submitted
        if (resumable) {
            binding.heroTitle.setText(R.string.home_resume)
            binding.heroSub.text = getString(
                R.string.home_resume_sub, session.label, session.index + 1, session.ids.size,
            )
        } else {
            binding.heroTitle.setText(R.string.home_start)
            binding.heroSub.text = getString(
                R.string.home_start_sub, p.goalTarget, minutesFor(p.goalTarget),
            )
        }
    }

    private fun renderTestDate(p: ProgressState) {
        val testDate = p.testDate
        binding.countdownCard.isVisible = testDate != null
        binding.noDateCard.isVisible = testDate == null
        if (testDate == null) return

        binding.daysLeft.text = DateUtils.daysTo(testDate).toString()
        binding.testOn.text = getString(R.string.home_test_on, DateUtils.fmtDate(testDate))

        val accuracy = Stats.accuracy(p)
        binding.paceLine.text = when {
            accuracy >= ON_PACE_ACCURACY -> getString(R.string.home_pace_on, accuracy)
            accuracy > 0 -> getString(R.string.home_pace_keep, accuracy)
            else -> getString(R.string.home_pace_none)
        }
    }

    private fun renderWeakestTopic(p: ProgressState, bank: BankRepository.Bank?) {
        val weakest = bank
            ?.let { Stats.topicStats(it.questions, p) }
            ?.filter { it.done > 0 }
            ?.minByOrNull { it.accuracy }

        weakestKey = weakest?.key
        binding.weakestCard.isVisible = weakest != null
        if (weakest == null) return

        binding.weakestName.text = weakest.name
        binding.weakestAccuracy.text =
            getString(R.string.home_weakest_accuracy, weakest.accuracy)
    }

    /**
     * The design hardcodes "Good morning". Splitting at noon and 6pm costs nothing and
     * stops the app greeting an evening user with the wrong half of the day.
     */
    private fun greetingRes(): Int = when (LocalTime.now().hour) {
        in 0 until NOON -> R.string.home_greeting_morning
        in NOON until EVENING -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }

    /** The design's estimate: half a minute per question, never rounded down to zero. */
    private fun minutesFor(questions: Int): Int = max(1, (questions * 0.5).roundToInt())

    private fun navigate(destinationId: Int, args: Bundle? = null) {
        findNavController().navigate(destinationId, args)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val NOON = 12
        const val EVENING = 18
        const val ON_PACE_ACCURACY = 80
    }
}
