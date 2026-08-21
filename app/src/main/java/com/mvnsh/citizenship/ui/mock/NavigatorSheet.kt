package com.mvnsh.citizenship.ui.mock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.databinding.SheetNavigatorBinding
import com.mvnsh.citizenship.ui.appViewModel
import kotlinx.coroutines.launch

/**
 * The mock's question grid: where you are, what you have answered, and the way out.
 *
 * Jumping is applied straight to the shared state, but submitting is reported back to the
 * host so that the screen - not the sheet - owns the navigation that follows.
 */
class NavigatorSheet : BottomSheetDialogFragment() {

    private var _binding: SheetNavigatorBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val adapter = NavChipAdapter { index ->
        vm.goTo(index)
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetNavigatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.chips.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.chips.adapter = adapter

        binding.keepGoing.setOnClickListener { dismiss() }
        binding.sheetSubmit.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_SUBMIT to true))
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.session.collect { session -> session?.let(::render) }
            }
        }
    }

    private fun render(session: SessionState) {
        val total = session.ids.size
        val answered = session.ids.count { it in session.marks }

        binding.answeredCount.text =
            getString(R.string.nav_sheet_answered, answered, total)
        binding.sheetSubmit.text = if (answered == total) {
            getString(R.string.mock_submit)
        } else {
            getString(R.string.mock_submit_partial, answered, total)
        }

        adapter.submitList(
            session.ids.mapIndexed { index, id ->
                NavChip(
                    index = index,
                    answered = id in session.marks,
                    current = index == session.index,
                )
            },
        )
    }

    override fun onDestroyView() {
        binding.chips.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "navigator"
        const val KEY_SUBMIT = "submit"

        private const val COLUMNS = 5
    }
}
