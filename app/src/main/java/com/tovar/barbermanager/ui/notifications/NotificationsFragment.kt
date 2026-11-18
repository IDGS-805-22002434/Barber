package com.tovar.barbermanager.ui.notifications

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
import com.tovar.barbermanager.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.launch

/** Avisos del cliente y registro de notificaciones del administrador (RF07, RF07.03). */
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by lazy {
        ViewModelProvider(this)[NotificationsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        val isAdmin = viewModel.isAdmin
        binding.notificationsSubtitle.setText(
            if (isAdmin) R.string.notifications_subtitle_admin else R.string.notifications_subtitle
        )
        binding.emptyText.setText(
            if (isAdmin) R.string.notifications_empty_admin else R.string.notifications_empty
        )
        binding.markReadButton.isVisible = !isAdmin
        binding.markReadButton.setOnClickListener { viewModel.markAllRead() }

        val adapter = NotificationAdapter(
            showRecipient = isAdmin,
            onClick = { viewModel.markRead(it) }
        )
        binding.notificationsList.adapter = adapter

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
        val headerBaseTop = binding.notificationsHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.notificationsRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.notificationsHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right)
            insets
        }
    }
}
