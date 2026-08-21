package com.mvnsh.citizenship.ui.settings

import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.ui.common.BaseDialogFragment

/** Drops the saved questions only; nothing measured is touched. */
class ClearBookmarksDialog : BaseDialogFragment() {

    override val titleRes = R.string.dlg_clear_title
    override val bodyRes = R.string.dlg_clear_body

    override fun actions() = listOf(
        DialogAction(R.string.dlg_clear_confirm, R.style.Button_Cta_Primary) {
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_CONFIRMED to true))
        },
        DialogAction(R.string.dlg_cancel, R.style.Button_Cta_Neutral) {
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_CONFIRMED to false))
        },
    )

    companion object {
        const val REQUEST_KEY = "clear_bookmarks"
        const val KEY_CONFIRMED = "confirmed"
    }
}
