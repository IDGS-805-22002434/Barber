package com.tovar.barbermanager.ui.services

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
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.Service
import com.tovar.barbermanager.data.model.ServiceCategory
import com.tovar.barbermanager.databinding.DialogServiceDetailBinding
import com.tovar.barbermanager.databinding.FragmentServicesBinding
import com.tovar.barbermanager.ui.util.Format
import kotlinx.coroutines.launch

/**
 * Catálogo de servicios (RF02.01, RF02.02).
 * El cliente ve solo los servicios activos y su detalle al tocarlos;
 * el administrador además puede ordenar, dar de alta y editar (RF02.03).
 */
class ServicesFragment : Fragment() {

    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ServicesViewModel by lazy {
        ViewModelProvider(this)[ServicesViewModel::class.java]
    }
    private lateinit var adapter: ServiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupToolbar()
        setupSearch()
        setupChips()
        setupList()
        setupFab()

        // La pantalla de formulario avisa aquí cuando guardó, para que la
        // confirmación se vea sobre la lista (donde el usuario está mirando).
        setFragmentResultListener(REQUEST_SERVICE_SAVED) { _, _ ->
            showSnackbar(getString(R.string.service_saved))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.items)
                    binding.emptyText.isVisible = !state.loading && state.items.isEmpty()
                    binding.emptyText.setText(
                        if (state.hasFilters) R.string.services_empty_filtered
                        else R.string.services_empty
                    )
                    binding.summaryText.isVisible = !state.loading
                    binding.summaryText.text = if (viewModel.isAdmin) {
                        getString(R.string.services_summary_admin, state.totalServices, state.inactiveCount)
                    } else {
                        getString(R.string.services_summary_client, state.totalServices)
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
        val headerBaseTop = binding.servicesHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.servicesRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.servicesHeader.updatePadding(top = headerBaseTop + bars.top)
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
        binding.adminBadge.isVisible = viewModel.isAdmin
        binding.sortButton.setOnClickListener { showSortDialog() }
    }

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.onSearchChanged(text?.toString().orEmpty())
        }
    }

    private fun setupChips() {
        binding.filterChips.addView(createChip(getString(R.string.filter_all), null))
        ServiceCategory.entries.forEach { category ->
            binding.filterChips.addView(
                createChip(getString(category.titleRes), category)
            )
        }
    }

    private fun createChip(label: String, category: ServiceCategory?): Chip {
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = category == null
            setOnClickListener {
                binding.filterChips.clearCheck()
                isChecked = true
                viewModel.onCategorySelected(category)
            }
        }
        val params = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
        chip.layoutParams = params
        return chip
    }

    private fun setupList() {
        adapter = ServiceAdapter { service ->
            if (viewModel.isAdmin) {
                openForm(service.id)
            } else {
                showDetailDialog(service)
            }
        }
        binding.servicesList.adapter = adapter
    }

    private fun setupFab() {
        binding.addServiceFab.isVisible = viewModel.isAdmin
        binding.addServiceFab.setOnClickListener { openForm(ServiceFormViewModel.SERVICE_ID_NEW) }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_name),
            getString(R.string.sort_price_asc),
            getString(R.string.sort_price_desc)
        )
        val checked = when (viewModel.sort.value) {
            SortMode.NAME -> 0
            SortMode.PRICE_ASC -> 1
            SortMode.PRICE_DESC -> 2
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                viewModel.onSortChanged(
                    when (which) {
                        1 -> SortMode.PRICE_ASC
                        2 -> SortMode.PRICE_DESC
                        else -> SortMode.NAME
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDetailDialog(service: Service) {
        val dialogBinding = DialogServiceDetailBinding.inflate(layoutInflater)
        dialogBinding.detailName.text = service.name
        dialogBinding.detailCategory.setText(service.category.titleRes)
        dialogBinding.detailPrice.text = Format.price(service.priceCents)
        dialogBinding.detailDescription.text = service.description

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.service_detail_close, null)
            .show()
    }

    private fun openForm(serviceId: Long) {
        findNavController().navigate(
            R.id.action_services_to_form,
            Bundle().apply { putLong(ARG_SERVICE_ID, serviceId) }
        )
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val ARG_SERVICE_ID = "serviceId"
        const val REQUEST_SERVICE_SAVED = "service_saved"
    }
}
