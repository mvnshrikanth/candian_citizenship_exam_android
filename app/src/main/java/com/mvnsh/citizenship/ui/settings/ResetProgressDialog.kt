package com.mvnsh.citizenship.ui.settings

import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.ui.common.BaseDialogFragment

/** Deletes everything measured. Bookmarks survive, and the copy says so. */
class ResetProgressDialog : BaseDialogFragment() {

    override val titleRes = R.string.dlg_reset_title
    override val bodyRes = R.string.dlg_reset_body

    override fun actions() = listOf(
        DialogAction(R.string.dlg_reset_confirm, R.style.Button_Cta_Danger) {
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_CONFIRMED to true))
        },
        DialogAction(R.string.dlg_cancel, R.style.Button_Cta_Neutral) {
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_CONFIRMED to false))
        },
    )

    companion object {
        const val REQUEST_KEY = "reset_progress"
        const val KEY_CONFIRMED = "confirmed"
    }
}
