package com.tovar.barbermanager.ui.profile

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.databinding.DialogChangePasswordBinding
import com.tovar.barbermanager.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

/** Perfil: datos personales, URL del servidor, contraseña e historial (RF07). */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by lazy {
        ViewModelProvider(this)[ProfileViewModel::class.java]
    }
    private val historyAdapter = HistorialAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupInputs()
        binding.rvHistory.adapter = historyAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.events.collect { showSnackbar(it) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.profileHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.profileRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.profileHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right)
            insets
        }
    }

    private fun setupInputs() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.nameInput.doAfterTextChanged { viewModel.onNombreChanged(it?.toString().orEmpty()) }
        binding.phoneInput.doAfterTextChanged { viewModel.onTelefonoChanged(it?.toString().orEmpty()) }
        binding.serverInput.doAfterTextChanged { viewModel.onServidorChanged(it?.toString().orEmpty()) }
        binding.saveButton.setOnClickListener { viewModel.guardar() }
        binding.changePasswordButton.setOnClickListener { showChangePasswordDialog() }
    }

    private fun render(state: ProfileUiState) {
        binding.profileProgress.isVisible = state.loading

        if (binding.nameInput.text?.toString() != state.nombre) {
            binding.nameInput.setText(state.nombre)
        }
        if (binding.phoneInput.text?.toString() != state.telefono) {
            binding.phoneInput.setText(state.telefono)
        }
        if (binding.emailInput.text?.toString() != state.correo) {
            binding.emailInput.setText(state.correo)
        }
        if (binding.serverInput.text?.toString() != state.servidorUrl) {
            binding.serverInput.setText(state.servidorUrl)
        }

        binding.profileSessionLabel.setText(
            if (state.isServerSession) R.string.profile_session_server else R.string.profile_session_local
        )

        binding.saveButton.isEnabled = !state.saving
        binding.saveButton.setText(if (state.saving) R.string.booking_saving else R.string.profile_save)

        binding.profileError.isVisible = state.error != null
        binding.profileError.text = state.error
        state.error?.let { viewModel.consumeError() }

        binding.historyProgress.isVisible = state.historialLoading
        binding.historyEmpty.isVisible = !state.historialLoading && state.historialEmpty
        historyAdapter.submitList(state.historial)
    }

    private fun showChangePasswordDialog() {
        val view = DialogChangePasswordBinding.inflate(layoutInflater, null, false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.password_dialog_title)
            .setView(view.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.password_update, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val actual = view.passwordCurrentInput.text?.toString().orEmpty()
                val nueva = view.passwordNewInput.text?.toString().orEmpty()
                val confirmacion = view.passwordConfirmInput.text?.toString().orEmpty()
                if (viewModel.cambiarPassword(actual, nueva, confirmacion)) {
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showSnackbar(message: String) {
        val root = _binding?.root ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
    }
}
