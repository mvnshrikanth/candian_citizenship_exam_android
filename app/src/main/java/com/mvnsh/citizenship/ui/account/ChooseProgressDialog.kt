package com.mvnsh.citizenship.ui.account

import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.ui.common.BaseDialogFragment

/**
 * Asked exactly once, when signing in on a device that already has progress into an
 * account that also has progress.
 *
 * Conflict resolution is last-write-wins everywhere else, which is fine while one person
 * studies on one device at a time. This is the one case where it is not a rare race but
 * the very first thing that happens, and silently picking a side would delete real work.
 */
class ChooseProgressDialog : BaseDialogFragment() {

    override val titleRes = R.string.dlg_merge_title
    override val bodyRes = R.string.dlg_merge_body

    override fun actions() = listOf(
        DialogAction(R.string.dlg_merge_keep_local, R.style.Button_Cta_Primary) { report(true) },
        DialogAction(R.string.dlg_merge_use_cloud, R.style.Button_Cta_Neutral) { report(false) },
    )

    private fun report(keepLocal: Boolean) =
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_KEEP_LOCAL to keepLocal))

    companion object {
        const val REQUEST_KEY = "choose_progress"
        const val KEY_KEEP_LOCAL = "keep_local"
    }
}
