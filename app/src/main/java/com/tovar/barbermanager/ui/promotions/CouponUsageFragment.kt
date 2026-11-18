package com.tovar.barbermanager.ui.promotions

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
import com.tovar.barbermanager.databinding.FragmentCouponUsageBinding
import kotlinx.coroutines.launch

class CouponUsageFragment : Fragment() {

    private var _binding: FragmentCouponUsageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CouponUsageViewModel by lazy {
        ViewModelProvider(this)[CouponUsageViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCouponUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerBaseTop = binding.usageHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.usageRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.usageHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.usageSubtitle.setText(
            if (viewModel.isAdmin) R.string.usage_subtitle else R.string.usage_subtitle_client
        )
        val adapter = CouponUsageAdapter(showClient = viewModel.isAdmin)
        binding.usageList.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.usage.collect { items ->
                    adapter.submitList(items.orEmpty())
                    binding.emptyText.isVisible = items != null && items.isEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
