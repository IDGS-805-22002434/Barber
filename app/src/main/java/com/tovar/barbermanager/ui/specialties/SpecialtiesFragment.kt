package com.tovar.barbermanager.ui.specialties

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.Specialty
import com.tovar.barbermanager.databinding.DialogSpecialtyBinding
import com.tovar.barbermanager.databinding.FragmentSpecialtiesBinding
import kotlinx.coroutines.launch

/** Catálogo CRUD de especialidades (RF03.03); solo accesible para el administrador. */
class SpecialtiesFragment : Fragment() {

    private var _binding: FragmentSpecialtiesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpecialtiesViewModel by lazy {
        ViewModelProvider(this)[SpecialtiesViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpecialtiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isAdmin) {
            findNavController().navigateUp()
            return
        }

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        val adapter = SpecialtyAdapter(onClick = { specialty -> showSpecialtyDialog(specialty) })
        binding.specialtiesList.adapter = adapter

        binding.addSpecialtyFab.setOnClickListener { showSpecialtyDialog(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        adapter.submitList(state.items)
                        binding.emptyText.isVisible =
                            !state.loading && state.items.isEmpty()
                    }
                }
                launch {
                    viewModel.saved.collect {
                        Snackbar.make(binding.root, R.string.specialty_saved, Snackbar.LENGTH_SHORT)
                            .setAnchorView(binding.addSpecialtyFab)
                            .show()
                    }
                }
                launch {
                    viewModel.error.collect { messageRes ->
                        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.addSpecialtyFab)
                            .show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.specialtiesHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.specialtiesRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.specialtiesHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun showSpecialtyDialog(specialty: Specialty?) {
        val dialogBinding = DialogSpecialtyBinding.inflate(layoutInflater)
        dialogBinding.nameInput.setText(specialty?.name.orEmpty())
        dialogBinding.activeSwitch.isVisible = specialty != null
        dialogBinding.activeSwitch.isChecked = specialty?.active ?: true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (specialty == null) R.string.specialty_dialog_new
                else R.string.specialty_dialog_edit
            )
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.specialty_save) { _, _ ->
                viewModel.save(
                    Specialty(
                        id = specialty?.id ?: 0L,
                        name = dialogBinding.nameInput.text?.toString().orEmpty(),
                        active = dialogBinding.activeSwitch.isChecked
                    )
                )
            }
            .show()
    }
}
