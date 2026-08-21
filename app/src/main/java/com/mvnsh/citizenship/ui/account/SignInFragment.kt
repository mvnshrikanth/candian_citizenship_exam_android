package com.mvnsh.citizenship.ui.account

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mvnsh.citizenship.CitizenshipApp
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.AccountState
import com.mvnsh.citizenship.data.ProgressDocument
import com.mvnsh.citizenship.data.SyncRepository
import com.mvnsh.citizenship.databinding.FragmentSignInBinding
import com.mvnsh.citizenship.ui.appViewModel
import com.mvnsh.citizenship.ui.common.Motion
import com.mvnsh.citizenship.ui.common.applyBottomInset
import com.mvnsh.citizenship.ui.common.applyTopInset
import kotlinx.coroutines.launch

/**
 * Email and password, matching the web app's only provider.
 *
 * Sign-in is optional and never blocks studying, so this is a destination the user chooses
 * from Settings rather than a gate in front of the app.
 */
class SignInFragment : Fragment(R.layout.fragment_sign_in) {

    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    private val app get() = requireActivity().application as CitizenshipApp

    /** False while the form is creating an account rather than signing in to one. */
    private var creating = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSignInBinding.bind(view)
        binding.appBar.applyTopInset()
        binding.screen.applyBottomInset()
        Motion.rise(binding.content)

        binding.back.setOnClickListener { findNavController().navigateUp() }
        binding.submit.setOnClickListener { submit() }
        binding.toggleMode.setOnClickListener { setMode(!creating) }
        binding.forgot.setOnClickListener { resetPassword() }

        // Any edit clears a stale error; leaving it up while the user retypes is noise.
        binding.email.doAfterTextChanged { showError(null) }
        binding.password.doAfterTextChanged { showError(null) }

        setFragmentResultListener(ChooseProgressDialog.REQUEST_KEY) { _, bundle ->
            val keepLocal = bundle.getBoolean(ChooseProgressDialog.KEY_KEEP_LOCAL)
            startSync(
                if (keepLocal) {
                    SyncRepository.StartResolution.KEEP_LOCAL
                } else {
                    SyncRepository.StartResolution.USE_CLOUD
                },
            )
        }

        setMode(creating)
    }

    private fun setMode(create: Boolean) {
        creating = create
        binding.signInTitle.setText(if (create) R.string.sign_in_create else R.string.sign_in_title)
        binding.submit.setText(if (create) R.string.sign_in_create else R.string.sign_in_cta)
        binding.toggleMode.setText(
            if (create) R.string.sign_in_have_account else R.string.sign_in_create,
        )
        binding.forgot.isVisible = !create
        showError(null)
    }

    private fun submit() {
        val email = binding.email.text?.toString()?.trim().orEmpty()
        val password = binding.password.text?.toString().orEmpty()
        if (email.isEmpty()) return showError(getString(R.string.sign_in_need_email))
        if (password.isEmpty()) return showError(getString(R.string.sign_in_need_password))

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val auth = app.authRepository
            val result = if (creating) auth.signUp(email, password) else auth.signIn(email, password)

            result
                .onFailure {
                    setBusy(false)
                    showError(it.message)
                }
                .onSuccess { account ->
                    if (account is AccountState.SignedIn) resolveThenSync(account)
                }
        }
    }

    /**
     * The one moment last-write-wins would silently destroy work: both this device and the
     * account already hold progress. Ask, once. If either side is empty there is nothing to
     * choose between, so adopt the other without interrupting.
     */
    private suspend fun resolveThenSync(account: AccountState.SignedIn) {
        val localEmpty = ProgressDocument.isEmpty(vm.progress.value)
        val remote = runCatching { app.syncRepository.peek(account.uid) }.getOrElse { emptyMap() }
        val remoteEmpty = ProgressDocument.isEmpty(remote)

        when {
            localEmpty -> startSync(SyncRepository.StartResolution.USE_CLOUD)
            remoteEmpty -> startSync(SyncRepository.StartResolution.KEEP_LOCAL)
            else -> {
                setBusy(false)
                ChooseProgressDialog().show(parentFragmentManager, null)
            }
        }
    }

    private fun startSync(resolution: SyncRepository.StartResolution) {
        val account = app.authRepository.current
        if (account !is AccountState.SignedIn) return

        app.syncRepository.start(account.uid, account.email, resolution)
        vm.toast(getString(R.string.sign_in_welcome, account.email))
        findNavController().navigateUp()
    }

    private fun resetPassword() {
        val email = binding.email.text?.toString()?.trim().orEmpty()
        if (email.isEmpty()) return showError(getString(R.string.sign_in_need_email))

        viewLifecycleOwner.lifecycleScope.launch {
            app.authRepository.sendPasswordReset(email)
                .onSuccess { vm.toast(getString(R.string.sign_in_reset_sent)) }
                .onFailure { showError(it.message) }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.submit.isEnabled = !busy
        binding.toggleMode.isEnabled = !busy
        binding.forgot.isEnabled = !busy
    }

    private fun showError(message: String?) {
        binding.error.isVisible = message != null
        binding.error.text = message.orEmpty()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
