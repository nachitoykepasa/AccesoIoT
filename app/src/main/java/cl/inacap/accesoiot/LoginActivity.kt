package cl.inacap.accesoiot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.accesoiot.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var dbHelper: DatabaseHelper

    companion object {
        private const val ADMIN_USER = "aylen tejas"
        private const val ADMIN_PASS = "12345"

        const val PREFS_NAME = "acceso_iot_prefs"
        const val KEY_LOGGED_IN = "is_logged_in"
        const val KEY_USUARIO = "usuario_actual"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            irAMain()
            return
        }

        binding.btnIngresar.setOnClickListener {
            validarEIngresar()
        }
    }

    private fun validarEIngresar() {
        val usuarioInput = binding.etUsuario.text.toString().trim()
        val passwordInput = binding.etPassword.text.toString().trim()

        binding.tvError.visibility = View.GONE

        if (usuarioInput.isEmpty() || passwordInput.isEmpty()) {
            binding.tvError.text = "Por favor ingresa usuario y contraseña"
            binding.tvError.visibility = View.VISIBLE
            return
        }

        if (usuarioInput.equals(ADMIN_USER, ignoreCase = true) && passwordInput == ADMIN_PASS) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_USUARIO, ADMIN_USER)
                .apply()

            dbHelper.registrarAccion(ADMIN_USER, "Inicio de Sesión", "Inicio de sesión exitoso en la app")

            irAMain()
        } else {
            binding.tvError.text = "Usuario o contraseña incorrectos"
            binding.tvError.visibility = View.VISIBLE
        }
    }

    private fun irAMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
