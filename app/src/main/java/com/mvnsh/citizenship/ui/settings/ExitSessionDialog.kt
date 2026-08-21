package com.mvnsh.citizenship.ui.settings

import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.ui.common.BaseDialogFragment

/**
 * Offered when leaving an unfinished session.
 *
 * It reports the choice through the fragment result API rather than holding a callback:
 * a DialogFragment is recreated on rotation and a captured lambda would not survive it,
 * leaving the buttons silently dead.
 */
class ExitSessionDialog : BaseDialogFragment() {

    override val titleRes = R.string.dlg_exit_title
    override val bodyRes = R.string.dlg_exit_body

    override fun actions() = listOf(
        DialogAction(R.string.dlg_exit_save, R.style.Button_Cta_Primary) { report(RESULT_SAVE) },
        DialogAction(R.string.dlg_exit_keep, R.style.Button_Cta_Neutral) { report(RESULT_KEEP) },
        DialogAction(R.string.dlg_exit_discard, R.style.Button_Text_Danger) {
            report(RESULT_DISCARD)
        },
    )

    private fun report(choice: String) =
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_CHOICE to choice))

    companion object {
        const val REQUEST_KEY = "exit_session"
        const val KEY_CHOICE = "choice"
        const val RESULT_SAVE = "save"
        const val RESULT_KEEP = "keep"
        const val RESULT_DISCARD = "discard"
    }
}
