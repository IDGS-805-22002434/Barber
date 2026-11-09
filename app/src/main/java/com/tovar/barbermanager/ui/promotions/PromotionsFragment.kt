package com.tovar.barbermanager.ui.promotions

import android.content.ClipData
import android.content.ClipboardManager
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
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.databinding.FragmentPromotionsBinding
import kotlinx.coroutines.launch

class PromotionsFragment : Fragment() {

    private var _binding: FragmentPromotionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PromotionsViewModel by lazy {
        ViewModelProvider(this)[PromotionsViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPromotionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        val adapter = PromotionAdapter(onCopy = { copyCode(it) })
        binding.promotionsList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.items)
                    binding.emptyText.isVisible = !state.loading && state.items.isEmpty()
                }
            }
        }
    }

    private fun copyCode(code: String) {
        val cm = requireContext().getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("cupon", code))
        Snackbar.make(binding.root, getString(R.string.promo_code_copied, code), Snackbar.LENGTH_SHORT).show()
    }

    private fun applyInsets() {
        val headerBaseTop = binding.promotionsHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.promotionsRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.promotionsHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}