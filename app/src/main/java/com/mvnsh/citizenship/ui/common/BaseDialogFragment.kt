package com.mvnsh.citizenship.ui.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.DialogConfirmBinding

/**
 * The design's dialogs are not Material alert dialogs: they are 28dp surface cards with a
 * serif title and a vertical stack of full-width pill buttons. That shape is shared by
 * all three of them, so it is built once here and subclasses supply only the words.
 */
abstract class BaseDialogFragment : DialogFragment() {

    /** One stacked pill. [styleRes] is any of the Button.Cta / Button.Text styles. */
    protected class DialogAction(
        @StringRes val label: Int,
        @StyleRes val styleRes: Int,
        val onClick: () -> Unit,
    )

    @get:StringRes
    protected abstract val titleRes: Int

    @get:StringRes
    protected abstract val bodyRes: Int

    protected abstract fun actions(): List<DialogAction>

    private var _binding: DialogConfirmBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogConfirmBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = _binding!!
        binding.dialogTitle.setText(titleRes)
        binding.dialogBody.setText(bodyRes)

        actions().forEachIndexed { index, action ->
            binding.dialogButtons.addView(buildButton(action, isFirst = index == 0))
        }
        Motion.rise(binding.dialogCard, Motion.RISE_SHEET_MS)
    }

    private fun buildButton(action: DialogAction, isFirst: Boolean): MaterialButton {
        // A style can only be applied at construction, hence the theme wrapper.
        val themed = ContextThemeWrapper(requireContext(), action.styleRes)
        return MaterialButton(themed, null, 0).apply {
            setText(action.label)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (!isFirst) topMargin = resources.getDimensionPixelSize(R.dimen.gap_list)
            }
            setOnClickListener {
                dismiss()
                action.onClick()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The card carries its own shape, so the window behind it must not draw one.
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            setDimAmount(SCRIM_ALPHA)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        /** The design's 32% scrim. */
        const val SCRIM_ALPHA = 0.32f
    }
}
