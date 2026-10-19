package com.tovar.barbermanager.ui.login

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.AuthRepository
import com.tovar.barbermanager.data.LoginResult
import com.tovar.barbermanager.data.RegisterResult
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.databinding.ActivityLoginBinding
import com.tovar.barbermanager.databinding.DialogForgotPasswordBinding
import com.tovar.barbermanager.databinding.DialogRegisterBinding
import com.tovar.barbermanager.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        // Solo se omite el login si se eligió "Recordarme" en una sesión autenticada.
        if (session.isLoggedIn) {
            openHome(session.userEmail)
            return
        }
        // Evita conservar un rol de una sesión anterior que no se quiso recordar.
        session.clear()

        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authRepository = AuthRepository(AppDatabase.getInstance(applicationContext).userDao())

        applyInsets()
        setupListeners()
        initializeLocalAccounts()
    }

    private fun initializeLocalAccounts() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { authRepository.ensureDefaultAdmin() }
            } catch (_: Exception) {
                showMessage(R.string.error_database)
            } finally {
                if (!isFinishing) setLoading(false)
            }
        }
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
        binding.registerLink.setOnClickListener { showRegisterDialog() }
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
        lifecycleScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) {
                    authRepository.authenticate(email, password)
                }) {
                    is LoginResult.Success -> {
                        session.saveSession(result.user, binding.rememberCheck.isChecked)
                        openHome(result.user.email)
                    }
                    LoginResult.InvalidCredentials -> {
                        setLoading(false)
                        showMessage(R.string.error_login_invalid)
                    }
                    LoginResult.InactiveAccount -> {
                        setLoading(false)
                        showMessage(R.string.error_account_inactive)
                    }
                }
            } catch (_: Exception) {
                setLoading(false)
                showMessage(R.string.error_database)
            }
        }
    }

    private fun showRegisterDialog() {
        val dialogBinding = DialogRegisterBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.register_title)
            .setMessage(R.string.register_message)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.register_button, null)
            .create()

        dialogBinding.registerNameInput.doAfterTextChanged {
            dialogBinding.registerNameLayout.error = null
        }
        dialogBinding.registerEmailInput.doAfterTextChanged {
            dialogBinding.registerEmailLayout.error = null
        }
        dialogBinding.registerPasswordInput.doAfterTextChanged {
            dialogBinding.registerPasswordLayout.error = null
            dialogBinding.registerConfirmPasswordLayout.error = null
        }
        dialogBinding.registerConfirmPasswordInput.doAfterTextChanged {
            dialogBinding.registerConfirmPasswordLayout.error = null
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fullName = dialogBinding.registerNameInput.text?.toString()?.trim().orEmpty()
                val email = dialogBinding.registerEmailInput.text?.toString()?.trim().orEmpty()
                val password = dialogBinding.registerPasswordInput.text?.toString().orEmpty()
                val confirmation = dialogBinding.registerConfirmPasswordInput.text?.toString().orEmpty()

                dialogBinding.registerNameLayout.error = if (fullName.isBlank()) {
                    getString(R.string.error_register_name)
                } else null
                dialogBinding.registerEmailLayout.error = emailError(email)
                dialogBinding.registerPasswordLayout.error = passwordError(password)
                dialogBinding.registerConfirmPasswordLayout.error = when {
                    confirmation.isEmpty() -> getString(R.string.error_password_empty)
                    confirmation != password -> getString(R.string.error_password_mismatch)
                    else -> null
                }

                if (dialogBinding.registerNameLayout.error != null ||
                    dialogBinding.registerEmailLayout.error != null ||
                    dialogBinding.registerPasswordLayout.error != null ||
                    dialogBinding.registerConfirmPasswordLayout.error != null
                ) return@setOnClickListener

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    val result = try {
                        withContext(Dispatchers.IO) {
                            authRepository.registerClient(fullName, email, password)
                        }
                    } catch (_: Exception) {
                        RegisterResult.Failed
                    }

                    if (isFinishing) return@launch
                    when (result) {
                        RegisterResult.Created -> {
                            dialog.dismiss()
                            binding.emailInput.setText(email.lowercase())
                            binding.passwordInput.setText("")
                            showMessage(R.string.register_success)
                        }
                        RegisterResult.EmailAlreadyExists -> {
                            dialogBinding.registerEmailLayout.error = getString(R.string.error_email_exists)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                        RegisterResult.Failed -> {
                            showMessage(R.string.error_database)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                    }
                }
            }
        }
        dialog.show()
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
            .setPositiveButton(R.string.forgot_send, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = dialogBinding.forgotEmailInput.text?.toString()?.trim().orEmpty()
                val error = emailError(email)
                if (error != null) {
                    dialogBinding.forgotEmailLayout.error = error
                } else {
                    // No se simula el envío: no hay servidor ni proveedor de correo configurado.
                    dialog.dismiss()
                    showMessage(R.string.forgot_backend_unavailable)
                }
            }
        }
        dialog.show()
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
    }
}
