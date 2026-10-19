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
import com.tovar.barbermanager.R
import com.tovar.barbermanager.databinding.FragmentBarbersBinding
import kotlinx.coroutines.launch

/** Catálogo de barberos (RF03); el admin puede dar de alta y editar. */
class BarbersFragment : Fragment() {

    private var _binding: FragmentBarbersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BarbersViewModel by lazy {
        ViewModelProvider(this)[BarbersViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBarbersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.adminBadge.isVisible = viewModel.isAdmin
        binding.manageSpecialtiesButton.isVisible = viewModel.isAdmin
        binding.manageSpecialtiesButton.setOnClickListener {
            findNavController().navigate(R.id.action_barbers_to_specialties)
        }

        val adapter = BarberAdapter(
            isAdmin = viewModel.isAdmin,
            onClick = { item -> openForm(item.barber.id) }
        )
        binding.barbersList.adapter = adapter

        binding.addBarberFab.isVisible = viewModel.isAdmin
        binding.addBarberFab.setOnClickListener { openForm(BarberFormViewModel.BARBER_ID_NEW) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.items)
                    binding.emptyText.isVisible = !state.loading && state.items.isEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.barbersHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.barbersRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.barbersHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun openForm(barberId: Long) {
        if (!viewModel.isAdmin) return
        findNavController().navigate(
            R.id.action_barbers_to_form,
            Bundle().apply { putLong(BarberFormFragment.ARG_BARBER_ID, barberId) }
        )
    }
}
