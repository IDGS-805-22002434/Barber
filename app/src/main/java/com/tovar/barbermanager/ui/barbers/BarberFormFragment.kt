package com.tovar.barbermanager.ui.barbers

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
import com.google.android.material.chip.Chip
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.Barber
import com.tovar.barbermanager.data.local.Specialty
import com.tovar.barbermanager.databinding.FragmentBarberFormBinding
import kotlinx.coroutines.launch

/** Alta y edición de barberos (RF03.02) con asignación de especialidades (RF03.03). */
class BarberFormFragment : Fragment() {

    private var _binding: FragmentBarberFormBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BarberFormViewModel by lazy {
        ViewModelProvider(this)[BarberFormViewModel::class.java]
    }

    private var barberId: Long = BarberFormViewModel.BARBER_ID_NEW
    private var bound = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBarberFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!SessionManager(requireContext()).isAdmin) {
            findNavController().navigateUp()
            return
        }

        barberId = requireArguments().getLong(ARG_BARBER_ID, BarberFormViewModel.BARBER_ID_NEW)

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.formTitle.setText(
            if (barberId == BarberFormViewModel.BARBER_ID_NEW) R.string.barber_form_new_title
            else R.string.barber_form_edit_title
        )
        binding.saveButton.setOnClickListener { attemptSave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.formState.collect { state ->
                        if (!state.loaded) return@collect
                        if (!bound) {
                            bound = true
                            bind(state.barber)
                        }
                        renderSpecialties(state.specialties, state.selectedIds)
                        binding.specialtiesError.isVisible = false
                    }
                }
                launch {
                    viewModel.saved.collect {
                        findNavController().navigateUp()
                    }
                }
            }
        }

        viewModel.load(barberId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.formHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.formRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.formHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    private fun bind(barber: Barber?) {
        binding.nameInput.setText(barber?.name.orEmpty())
        binding.yearsInput.setText(barber?.yearsExperience?.toString().orEmpty())
        binding.activeSwitch.isChecked = barber?.active ?: true
        binding.activeSwitch.visibility = if (barber == null) View.GONE else View.VISIBLE
    }

    private fun renderSpecialties(specialties: List<Specialty>, selectedIds: Set<Long>) {
        binding.specialtiesGroup.removeAllViews()
        specialties.forEach { specialty ->
            val chip = Chip(requireContext()).apply {
                text = specialty.name
                isCheckable = true
                isChecked = specialty.id in selectedIds
                setOnClickListener { viewModel.toggleSpecialty(specialty.id) }
            }
            binding.specialtiesGroup.addView(chip)
        }
    }

    private fun attemptSave() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val yearsText = binding.yearsInput.text?.toString()?.trim().orEmpty()
        val years = yearsText.toIntOrNull()
        val hasSpecialties = viewModel.formState.value.selectedIds.isNotEmpty()

        binding.nameLayout.error =
            if (name.isEmpty()) getString(R.string.error_barber_name) else null
        binding.yearsLayout.error = when {
            years == null || years < 0 || years > 60 -> getString(R.string.error_years_invalid)
            else -> null
        }
        binding.specialtiesError.isVisible = !hasSpecialties

        when {
            binding.nameLayout.error != null -> {
                binding.nameInput.requestFocus()
                return
            }
            binding.yearsLayout.error != null -> {
                binding.yearsInput.requestFocus()
                return
            }
            !hasSpecialties -> {
                binding.specialtiesGroup.requestFocus()
                return
            }
        }

        viewModel.save(
            Barber(
                id = barberId,
                name = name,
                yearsExperience = years ?: 0,
                active = binding.activeSwitch.isChecked
            )
        )
    }

    companion object {
        const val ARG_BARBER_ID = "barberId"
    }
}
