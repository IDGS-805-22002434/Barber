package com.tovar.barbermanager.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.Service
import com.tovar.barbermanager.data.model.ServiceCategory
import com.tovar.barbermanager.databinding.FragmentServiceFormBinding
import com.tovar.barbermanager.ui.util.Format
import kotlinx.coroutines.launch

/**
 * Alta y edición de servicios (RF02.03).
 * El formulario se llena una sola vez (sobrevive a ir a segundo plano) y
 * el guardado vive en el ViewModel; al terminar avisa a la lista con
 * FragmentResult para que el "Servicio guardado" se vea donde corresponde.
 */
class ServiceFormFragment : Fragment() {

    private var _binding: FragmentServiceFormBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ServiceFormViewModel by lazy {
        ViewModelProvider(this)[ServiceFormViewModel::class.java]
    }

    private var serviceId: Long = ServiceFormViewModel.SERVICE_ID_NEW
    private var bound = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServiceFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!SessionManager(requireContext()).isAdmin) {
            findNavController().navigateUp()
            return
        }

        serviceId = requireArguments().getLong(ARG_SERVICE_ID, ServiceFormViewModel.SERVICE_ID_NEW)

        applyInsets()
        setupToolbar()
        setupCategoryDropdown()
        binding.saveButton.setOnClickListener { attemptSave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.formState.collect { state ->
                        if (state.loaded && !bound) {
                            bound = true
                            bind(state.service)
                        }
                    }
                }
                launch {
                    viewModel.saved.collect {
                        parentFragmentManager.setFragmentResult(
                            ServicesFragment.REQUEST_SERVICE_SAVED,
                            Bundle()
                        )
                        findNavController().navigateUp()
                    }
                }
            }
        }

        viewModel.load(serviceId)
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

    private fun setupToolbar() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.formTitle.setText(
            if (serviceId == ServiceFormViewModel.SERVICE_ID_NEW) R.string.service_form_new_title
            else R.string.service_form_edit_title
        )
    }

    private fun setupCategoryDropdown() {
        val labels = ServiceCategory.entries.map { getString(it.titleRes) }
        binding.categoryInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
    }

    private fun bind(service: Service?) {
        binding.nameInput.setText(service?.name.orEmpty())
        binding.descriptionInput.setText(service?.description.orEmpty())
        binding.priceInput.setText(
            service?.let { (it.priceCents / 100.0).toString() }.orEmpty()
        )
        binding.categoryInput.setText(
            getString((service?.category ?: ServiceCategory.CORTES).titleRes),
            false
        )
        binding.activeSwitch.isChecked = service?.active ?: true
        binding.activeSwitch.visibility = if (service == null) View.GONE else View.VISIBLE
    }

    private fun attemptSave() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val description = binding.descriptionInput.text?.toString()?.trim().orEmpty()
        val priceText = binding.priceInput.text?.toString().orEmpty()
        val priceCents = Format.parsePrice(priceText)

        binding.nameLayout.error =
            if (name.isEmpty()) getString(R.string.error_service_name) else null
        binding.priceLayout.error = when {
            priceText.isEmpty() -> getString(R.string.error_price_empty)
            priceCents == null -> getString(R.string.error_price_invalid)
            else -> null
        }

        when {
            binding.nameLayout.error != null -> {
                binding.nameInput.requestFocus()
                return
            }
            binding.priceLayout.error != null -> {
                binding.priceInput.requestFocus()
                return
            }
        }

        viewModel.save(
            Service(
                id = serviceId,
                name = name,
                description = description,
                priceCents = priceCents ?: 0L,
                category = selectedCategory(),
                active = binding.activeSwitch.isChecked
            )
        )
    }

    private fun selectedCategory(): ServiceCategory {
        val selected = binding.categoryInput.text?.toString().orEmpty()
        return ServiceCategory.entries.firstOrNull { getString(it.titleRes) == selected }
            ?: ServiceCategory.CORTES
    }

    companion object {
        const val ARG_SERVICE_ID = "serviceId"
    }
}
