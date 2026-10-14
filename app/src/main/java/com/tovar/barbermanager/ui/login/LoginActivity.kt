package com.tovar.barbermanager.ui.login

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.databinding.ActivityLoginBinding
import com.tovar.barbermanager.databinding.DialogForgotPasswordBinding
import com.tovar.barbermanager.ui.home.HomeActivity
import kotlin.math.max

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        // Si el usuario eligió "Recordarme", se salta el login.
        if (session.isLoggedIn) {
            openHome(session.userEmail)
            return
        }

        // El encabezado es oscuro, así que los íconos de la barra de estado van claros.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupListeners()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun applyInsets() {
        val headerBaseTop = binding.loginHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.loginHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = max(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }

        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        binding.loginButton.setOnClickListener { attemptLogin() }
        binding.forgotLink.setOnClickListener { showForgotPasswordDialog() }
        binding.googleButton.setOnClickListener { showMessage(R.string.msg_google_soon) }
        binding.registerLink.setOnClickListener { showMessage(R.string.msg_register_soon) }
    }

    private fun attemptLogin() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()

        binding.emailLayout.error = emailError(email)
        binding.passwordLayout.error = passwordError(password)

        when {
            binding.emailLayout.error != null -> {
                binding.emailInput.requestFocus()
                return
            }
            binding.passwordLayout.error != null -> {
                binding.passwordInput.requestFocus()
                return
            }
        }

        hideKeyboard()
        setLoading(true)

        // Simulación: aquí irá la llamada al backend (Sprint 1).
        // Por ahora cualquier correo válido con contraseña de 6+ caracteres entra.
        handler.postDelayed({
            setLoading(false)
            onLoginSuccess(email)
        }, FAKE_NETWORK_DELAY_MS)
    }

    private fun onLoginSuccess(email: String) {
        if (binding.rememberCheck.isChecked) {
            session.saveSession(email)
        }
        openHome(email)
    }

    private fun emailError(email: String): String? = when {
        email.isEmpty() -> getString(R.string.error_email_empty)
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.error_email_invalid)
        else -> null
    }

    private fun passwordError(password: String): String? = when {
        password.isEmpty() -> getString(R.string.error_password_empty)
        password.length < MIN_PASSWORD_LENGTH ->
            getString(R.string.error_password_short, MIN_PASSWORD_LENGTH)
        else -> null
    }

    private fun setLoading(loading: Boolean) {
        binding.loginButton.isEnabled = !loading
        binding.loginButton.text = if (loading) "" else getString(R.string.login_button)
        binding.loginProgress.isVisible = loading
        binding.emailLayout.isEnabled = !loading
        binding.passwordLayout.isEnabled = !loading
        binding.googleButton.isEnabled = !loading
        binding.forgotLink.isEnabled = !loading
        binding.registerLink.isEnabled = !loading
    }

    private fun showForgotPasswordDialog() {
        val dialogBinding = DialogForgotPasswordBinding.inflate(layoutInflater)
        dialogBinding.forgotEmailInput.setText(binding.emailInput.text)
        dialogBinding.forgotEmailInput.doAfterTextChanged {
            dialogBinding.forgotEmailLayout.error = null
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_title)
            .setMessage(R.string.forgot_message)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.forgot_send, null) // se define abajo para no cerrar con error
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = dialogBinding.forgotEmailInput.text?.toString()?.trim().orEmpty()
                val error = emailError(email)
                if (error != null) {
                    dialogBinding.forgotEmailLayout.error = error
                } else {
                    // Aquí irá la solicitud de recuperación al backend.
                    dialog.dismiss()
                    showMessage(R.string.forgot_sent)
                }
            }
        }
        dialog.show()
    }

    private fun showMessage(messageRes: Int) {
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun openHome(email: String?) {
        val intent = Intent(this, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_EMAIL, email)
        startActivity(intent)
        finish()
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val FAKE_NETWORK_DELAY_MS = 1200L
    }
}
