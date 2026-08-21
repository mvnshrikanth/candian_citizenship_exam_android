package com.mvnsh.citizenship.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mvnsh.citizenship.BuildConfig
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.AccountState
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.SyncState
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.databinding.FragmentSettingsBinding
import com.mvnsh.citizenship.databinding.ItemSettingRowBinding
import com.mvnsh.citizenship.databinding.ViewSwitchRowBinding
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant

/** Every preference the app has, plus the two destructive actions. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private lateinit var switches: List<ViewSwitchRowBinding>

    /** The one Data row whose subtitle counts the bank. */
    private var bankRow: ItemSettingRowBinding? = null

    private val app get() = requireActivity().application as com.mvnsh.citizenship.CitizenshipApp

    /**
     * Asked for at the moment a reminder is switched on, never at launch: that is where
     * the user has expressed the intent the permission is for.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The preference is the user's either way; only the delivery is blocked.
            if (!granted) vm.toast(getString(R.string.set_notif_denied))
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSettingsBinding.bind(view)
        binding.appBar.applyTopInset()
        // The inset goes on the screen, not on the scroll view's padding. With
        // clipToPadding="false" the scroll view's bounds run under the navigation bar, and
        // requestRectangleOnScreen - which is what scrollTo and accessibility focus both
        // use - scrolls only far enough to put a row inside those bounds. That parks a
        // tappable row under the bar, where the system takes the touch and the row simply
        // never responds. Ending the scroll container above the bar makes that impossible.
        binding.screen.applyBottomInset()
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }

        binding.themeSystem.setOnClickListener { vm.setTheme(THEME_SYSTEM) }
        binding.themeLight.setOnClickListener { vm.setTheme(THEME_LIGHT) }
        binding.themeDark.setOnClickListener { vm.setTheme(THEME_DARK) }

        binding.goal10.setOnClickListener { vm.setGoalTarget(10) }
        binding.goal20.setOnClickListener { vm.setGoalTarget(20) }
        binding.goal30.setOnClickListener { vm.setGoalTarget(30) }

        // The design toggles rather than opening a picker. Kept as-is: a real date picker
        // is a different interaction the design does not specify.
        binding.testDateRow.setOnClickListener {
            val current = vm.progress.value.testDate
            vm.setTestDate(if (current == null) DateUtils.shiftDay(DEFAULT_TEST_DAYS) else null)
        }

        switches = buildSwitches()
        buildAccountGroup()
        buildDataGroup()
        buildAboutGroup()

        setFragmentResultListener(ResetProgressDialog.REQUEST_KEY) { _, bundle ->
            if (bundle.getBoolean(ResetProgressDialog.KEY_CONFIRMED)) vm.resetProgress()
        }
        setFragmentResultListener(ClearBookmarksDialog.REQUEST_KEY) { _, bundle ->
            if (bundle.getBoolean(ClearBookmarksDialog.KEY_CONFIRMED)) vm.clearBookmarks()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.bank, ::Pair).collect { (p, bank) -> render(p, bank) }
            }
        }
    }

    // ---- construction ---------------------------------------------------

    private fun buildSwitches(): List<ViewSwitchRowBinding> {
        val rows = listOf(
            Triple(R.string.set_notif_daily, R.string.set_notif_daily_sub, NotifKind.DAILY),
            Triple(R.string.set_notif_streak, R.string.set_notif_streak_sub, NotifKind.STREAK),
            Triple(R.string.set_notif_test, R.string.set_notif_test_sub, NotifKind.TEST),
        )
        val inflater = LayoutInflater.from(requireContext())
        val gap = resources.getDimensionPixelSize(R.dimen.gap_list)

        return rows.mapIndexed { index, (label, sub, kind) ->
            val row = ViewSwitchRowBinding.inflate(inflater, binding.reminders, false)
            row.switchLabel.setText(label)
            row.switchSub.setText(sub)
            row.switchRow.setOnClickListener { toggle(kind) }
            binding.reminders.addView(row.root)
            if (index > 0) {
                row.root.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = gap }
            }
            row
        }
    }

    /**
     * Rebuilt on every account change rather than mutated, because the signed-in and
     * signed-out shapes differ by row count, not just by text.
     */
    private fun buildAccountGroup() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.authRepository.account, app.syncRepository.state, ::Pair)
                    .collect { (account, sync) -> renderAccount(account, sync) }
            }
        }
    }

    private fun renderAccount(account: AccountState, sync: SyncState) {
        binding.accountGroup.removeAllViews()

        when (account) {
            AccountState.SignedOut -> addGroupedRow(
                binding.accountGroup,
                titleRes = R.string.account_signed_out,
                subtitleRes = R.string.account_signed_out_sub,
                trailing = R.drawable.ic_chevron_right,
                onClick = { findNavController().navigate(R.id.signInFragment) },
            )

            is AccountState.SignedIn -> {
                val row = addGroupedRow(
                    binding.accountGroup,
                    titleRes = R.string.account,
                    keepSubtitle = true,
                )
                row.title.text = account.email
                row.subtitle.text = syncLine(sync)

                addGroupedRow(
                    binding.accountGroup,
                    titleRes = R.string.account_sign_out,
                    // Sign-out is not a reset, and the copy has to say so or it reads
                    // like one next to "Reset all progress" further down the screen.
                    subtitleRes = R.string.account_sign_out_sub,
                    onClick = {
                        app.syncRepository.stop()
                        app.authRepository.signOut()
                    },
                )
            }
        }
    }

    private fun syncLine(sync: SyncState): String = when (sync) {
        SyncState.Off, is SyncState.Failed -> getString(R.string.account_never_synced)
        SyncState.Syncing -> getString(R.string.account_syncing)
        is SyncState.Synced -> {
            val at = sync.atEpochMs
                ?.let { DateUtils.fmtDate(Instant.ofEpochMilli(it).toString()) }
                ?.takeIf { it.isNotBlank() }
                ?: return getString(R.string.account_never_synced)
            sync.fromDevice
                ?.let { getString(R.string.account_synced_from, at, it) }
                ?: getString(R.string.account_synced, at)
        }
    }

    private fun buildDataGroup() {
        bankRow = addGroupedRow(
            binding.dataGroup,
            titleRes = R.string.set_bank_downloaded,
            leading = R.drawable.ic_download_done,
            keepSubtitle = true,
        )
        addGroupedRow(
            binding.dataGroup,
            titleRes = R.string.set_clear_bookmarks,
            onClick = { ClearBookmarksDialog().show(parentFragmentManager, null) },
        )
        addGroupedRow(
            binding.dataGroup,
            titleRes = R.string.set_reset,
            titleColour = R.color.md_error,
            onClick = { ResetProgressDialog().show(parentFragmentManager, null) },
        )
        // Genuinely useful for QA, and meaningless to a real user, so debug builds only.
        if (BuildConfig.DEBUG) {
            addGroupedRow(binding.dataGroup, R.string.set_demo, onClick = { vm.seedDemo() })
            addGroupedRow(binding.dataGroup, R.string.set_fresh, onClick = { vm.freshInstall() })
        }
    }

    private fun buildAboutGroup() {
        addGroupedRow(
            binding.aboutGroup,
            titleRes = R.string.set_language,
            subtitleRes = R.string.set_language_sub,
        )
        addGroupedRow(
            binding.aboutGroup,
            titleRes = R.string.set_privacy,
            subtitleRes = R.string.set_privacy_sub,
        )
        addGroupedRow(
            binding.aboutGroup,
            titleRes = R.string.set_feedback,
            trailing = R.drawable.ic_open_in_new,
            onClick = ::sendFeedback,
        )
        addGroupedRow(
            binding.aboutGroup,
            titleRes = R.string.set_version,
            subtitleRes = R.string.set_version_sub,
        )
    }

    /** Adds one opaque row; the 1dp gap between rows is the group's divider. */
    private fun addGroupedRow(
        container: ViewGroup,
        titleRes: Int,
        subtitleRes: Int? = null,
        keepSubtitle: Boolean = false,
        leading: Int? = null,
        trailing: Int? = null,
        titleColour: Int? = null,
        onClick: (() -> Unit)? = null,
    ): ItemSettingRowBinding {
        val row = ItemSettingRowBinding.inflate(
            LayoutInflater.from(requireContext()), container, false,
        )
        row.title.setText(titleRes)
        titleColour?.let {
            row.title.setTextColor(ContextCompat.getColor(requireContext(), it))
        }

        when {
            subtitleRes != null -> row.subtitle.setText(subtitleRes)
            keepSubtitle -> Unit // filled per render, from the loaded bank
            else -> row.subtitle.isVisible = false
        }

        leading?.let {
            row.rowLeading.setImageResource(it)
            row.rowLeading.isVisible = true
        }
        trailing?.let {
            row.rowIcon.setImageResource(it)
            row.rowIcon.isVisible = true
        }
        onClick?.let { action ->
            row.settingRow.isClickable = true
            row.settingRow.setBackgroundResource(R.drawable.bg_setting_row_clickable)
            row.settingRow.setOnClickListener { action() }
        }

        container.addView(row.root)
        if (container.childCount > 1) {
            row.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = resources.getDimensionPixelSize(R.dimen.stroke_card)
            }
        }
        return row
    }

    // ---- rendering ------------------------------------------------------

    private fun render(p: ProgressState, bank: BankRepository.Bank?) {
        renderTheme(p.theme)
        renderGoal(p.goalTarget)

        val testDate = p.testDate
        binding.subtitle.text = if (testDate == null) {
            getString(R.string.set_test_date_none)
        } else {
            getString(
                R.string.set_test_date_value,
                DateUtils.fmtDate(testDate),
                DateUtils.daysTo(testDate),
            )
        }

        renderSwitch(switches[0], p.notif.daily)
        renderSwitch(switches[1], p.notif.streak)
        renderSwitch(switches[2], p.notif.test)

        // The design hardcodes 501 here; the count comes from the bank so it cannot lie.
        bankRow?.subtitle?.text =
            getString(R.string.bank_downloaded_sub, bank?.questions?.size ?: 0)
    }

    private fun renderTheme(theme: String) {
        listOf(
            binding.themeSystem to THEME_SYSTEM,
            binding.themeLight to THEME_LIGHT,
            binding.themeDark to THEME_DARK,
        ).forEach { (segment, name) ->
            val selected = theme == name
            segment.setBackgroundResource(
                if (selected) R.drawable.bg_segment_selected else android.R.color.transparent,
            )
            segment.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.md_on_surface else R.color.md_on_surface_variant,
                ),
            )
            segment.fontVariationSettings = if (selected) "'wght' 600" else "'wght' 400"
            segment.isSelected = selected
        }
    }

    private fun renderGoal(target: Int) {
        listOf(binding.goal10 to 10, binding.goal20 to 20, binding.goal30 to 30)
            .forEach { (chip, value) ->
                val selected = target == value
                chip.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.md_primary else R.color.md_surface_container,
                    ),
                )
                chip.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.md_on_primary else R.color.md_on_surface_variant,
                    ),
                )
                chip.isSelected = selected
            }
    }

    private fun renderSwitch(row: ViewSwitchRowBinding, on: Boolean) {
        val ctx = requireContext()
        val density = resources.displayMetrics.density

        row.switchTrack.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (on) R.color.md_primary else R.color.md_surface_container_high,
            ),
        )
        row.switchThumb.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (on) R.color.md_on_primary else R.color.md_outline,
            ),
        )
        val size = ((if (on) THUMB_ON_DP else THUMB_OFF_DP) * density).toInt()
        row.switchThumb.updateLayoutParams<FrameLayout.LayoutParams> {
            width = size
            height = size
            gravity = Gravity.CENTER_VERTICAL or if (on) Gravity.END else Gravity.START
            marginStart = (THUMB_INSET_DP * density).toInt()
            marginEnd = (THUMB_INSET_DP * density).toInt()
        }

        // A drawn switch is invisible to TalkBack unless it says what it is and where it
        // stands, so both are attached here rather than left to the label alone.
        row.switchRow.contentDescription = row.switchLabel.text
        ViewCompat.setAccessibilityDelegate(
            row.switchRow,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Switch"
                    info.isCheckable = true
                    info.isChecked = on
                }
            },
        )
    }

    // ---- actions --------------------------------------------------------

    private enum class NotifKind { DAILY, STREAK, TEST }

    private fun toggle(kind: NotifKind) {
        val current = vm.progress.value.notif
        val turningOn = when (kind) {
            NotifKind.DAILY -> !current.daily
            NotifKind.STREAK -> !current.streak
            NotifKind.TEST -> !current.test
        }
        when (kind) {
            NotifKind.DAILY -> vm.setNotif(daily = turningOn)
            NotifKind.STREAK -> vm.setNotif(streak = turningOn)
            NotifKind.TEST -> vm.setNotif(test = turningOn)
        }
        if (turningOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * The design shows the affordance but names no destination, so a guarded mail intent
     * is the honest minimum: if nothing handles it, say so rather than crash.
     */
    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            vm.toast(getString(R.string.set_feedback_unavailable))
        }
    }

    override fun onDestroyView() {
        bankRow = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val THEME_SYSTEM = "System"
        const val THEME_LIGHT = "Light"
        const val THEME_DARK = "Dark"

        const val DEFAULT_TEST_DAYS = 38
        const val THUMB_ON_DP = 26f
        const val THUMB_OFF_DP = 22f
        const val THUMB_INSET_DP = 3f
    }
}
