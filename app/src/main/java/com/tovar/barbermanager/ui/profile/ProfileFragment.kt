package com.tovar.barbermanager.ui.profile

import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.tovar.barbermanager.data.AvatarStore
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.databinding.DialogChangePasswordBinding
import com.tovar.barbermanager.databinding.DialogCropPhotoBinding
import com.tovar.barbermanager.databinding.FragmentProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Perfil: datos personales, URL del servidor, contraseña e historial (RF07). */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by lazy {
        ViewModelProvider(this)[ProfileViewModel::class.java]
    }
    private val historyAdapter = HistorialAdapter()
    private var isAdmin = false
    private var accountEmail: String? = null

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) openCropper(uri)
        }

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

        val session = SessionManager(requireContext())
        isAdmin = session.isAdmin
        accountEmail = session.currentEmail
        // La conexión con el servidor no se muestra a ningún usuario.
        binding.serverSection.isVisible = false

        applyInsets()
        setupInputs()
        setupPhoto()
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
            R.string.profile_subtitle
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

    private fun setupPhoto() {
        binding.avatarChangeButton.setOnClickListener {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.avatarRemoveButton.setOnClickListener {
            val email = accountEmail ?: return@setOnClickListener
            AvatarStore.delete(requireContext(), email)
            showAvatar(null)
            showSnackbar(getString(R.string.profile_photo_removed))
        }
        val email = accountEmail ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val photo = withContext(Dispatchers.IO) { AvatarStore.load(requireContext(), email) }
            showAvatar(photo)
        }
    }

    /** Lee la foto elegida y abre el recortador para que la persona centre su cara. */
    private fun openCropper(uri: android.net.Uri) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val source = withContext(Dispatchers.IO) {
                AvatarStore.decodeScaled(appContext, uri, CROP_SOURCE_SIDE)
            }
            if (source == null) {
                showSnackbar(getString(R.string.profile_photo_error))
            } else {
                showCropDialog(source)
            }
        }
    }

    private fun showCropDialog(source: Bitmap) {
        val dialogBinding = DialogCropPhotoBinding.inflate(layoutInflater)
        dialogBinding.cropView.setBitmap(source)
        dialogBinding.cropView.onZoomChange = { zoom ->
            if (kotlin.math.abs(dialogBinding.zoomSlider.value - zoom) > 0.001f) {
                dialogBinding.zoomSlider.value = zoom.coerceIn(0f, 1f)
            }
        }
        dialogBinding.zoomSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) dialogBinding.cropView.setZoomFraction(value)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_photo_adjust_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.profile_photo_save) { _, _ ->
                saveCropped(dialogBinding.cropView.croppedBitmap(AVATAR_SIZE))
            }
            .show()
    }

    private fun saveCropped(cropped: Bitmap?) {
        val email = accountEmail
        if (cropped == null || email == null) {
            showSnackbar(getString(R.string.profile_photo_error))
            return
        }
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val photo = withContext(Dispatchers.IO) {
                if (AvatarStore.saveBitmap(appContext, email, cropped)) {
                    AvatarStore.load(appContext, email)
                } else null
            }
            cropped.recycle()
            if (photo == null) {
                showSnackbar(getString(R.string.profile_photo_error))
            } else {
                showAvatar(photo)
                showSnackbar(getString(R.string.profile_photo_saved))
            }
        }
    }

    private fun showAvatar(photo: Bitmap?) {
        val b = _binding ?: return
        if (photo != null) {
            b.avatarImage.setPadding(0, 0, 0, 0)
            b.avatarImage.imageTintList = null
            b.avatarImage.setImageBitmap(photo)
        } else {
            val pad = (28 * resources.displayMetrics.density).toInt()
            b.avatarImage.setPadding(pad, pad, pad, pad)
            b.avatarImage.setImageResource(R.drawable.ic_person)
            b.avatarImage.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        }
        b.avatarRemoveButton.isVisible = photo != null
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

    private companion object {
        const val CROP_SOURCE_SIDE = 1280
        const val AVATAR_SIZE = 512
    }
}
