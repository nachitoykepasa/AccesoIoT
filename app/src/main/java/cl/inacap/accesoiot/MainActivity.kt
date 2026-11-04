package cl.inacap.accesoiot

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.accesoiot.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null
    private var ipPanel: String = ""
    private var conectado: Boolean = false

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var dbHelper: DatabaseHelper
    private var usuarioActual: String = "aylen tejas"

    companion object {
        private const val INTERVALO_SONDEO_MS = 2000L
        private const val TIMEOUT_MS = 3000
        private const val KEY_ULTIMA_IP = "ultima_ip"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE)

        // Verificar si la sesión está activa
        if (!prefs.getBoolean(LoginActivity.KEY_LOGGED_IN, false)) {
            cerrarSesion()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        usuarioActual = prefs.getString(LoginActivity.KEY_USUARIO, "aylen tejas") ?: "aylen tejas"
        binding.tvUsuarioActual.text = getString(R.string.label_usuario_formato, usuarioActual)

        binding.etIp.setText(prefs.getString(KEY_ULTIMA_IP, ""))

        binding.btnConectar.setOnClickListener {
            if (conectado) desconectar() else conectar()
        }
        binding.btnAprobar.setOnClickListener { enviarOrden("aprobar") }
        binding.btnRechazar.setOnClickListener { enviarOrden("rechazar") }

        binding.btnCaraFeliz.setOnClickListener { enviarPruebaLeds("carafeliz") }
        binding.btnLimpiarLeds.setOnClickListener { enviarPruebaLeds("limpiar") }

        binding.btnVerHistorial.setOnClickListener {
            val intent = Intent(this, HistorialActivity::class.java)
            startActivity(intent)
        }

        binding.btnCerrarSesion.setOnClickListener {
            dbHelper.registrarAccion(usuarioActual, "Cierre de Sesión", "El usuario cerró sesión")
            prefs.edit().putBoolean(LoginActivity.KEY_LOGGED_IN, false).apply()
            cerrarSesion()
        }

        mostrarEstado(Estado.DESCONECTADO)
        actualizarChip(ChipEstado.DESCONECTADO)
        actualizarEstadisticasBD()
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadisticasBD()
    }

    private fun conectar() {
        val ip = binding.etIp.text.toString().trim()
        if (ip.isEmpty()) {
            binding.tvDetalle.text = "Ingresa la IP del panel para conectar."
            return
        }
        ipPanel = ip
        prefs.edit().putString(KEY_ULTIMA_IP, ip).apply()

        conectado = true
        binding.btnConectar.text = getString(R.string.btn_desconectar)
        binding.etIp.isEnabled = false
        binding.tvDetalle.text = "Conectando con $ip ..."
        binding.btnCaraFeliz.isEnabled = true
        binding.btnLimpiarLeds.isEnabled = true

        // Registrar la conexión en la BD local SQLite
        dbHelper.registrarConexion(ip)
        dbHelper.registrarAccion(usuarioActual, "Conexión", "Conectado al Arduino en IP: $ip")
        actualizarEstadisticasBD()

        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val estado = consultarEstado(ipPanel)
                aplicarEstado(estado)
                delay(INTERVALO_SONDEO_MS)
            }
        }
    }

    private fun desconectar() {
        if (conectado && ipPanel.isNotEmpty()) {
            dbHelper.registrarAccion(usuarioActual, "Desconexión", "Desconectado del panel IP: $ipPanel")
        }

        conectado = false
        pollingJob?.cancel()
        binding.btnConectar.text = getString(R.string.btn_conectar)
        binding.etIp.isEnabled = true
        binding.btnAprobar.isEnabled = false
        binding.btnRechazar.isEnabled = false
        binding.btnCaraFeliz.isEnabled = false
        binding.btnLimpiarLeds.isEnabled = false
        binding.tvDetalle.text = ""
        mostrarEstado(Estado.DESCONECTADO)
        actualizarChip(ChipEstado.DESCONECTADO)
        actualizarEstadisticasBD()
    }

    private suspend fun consultarEstado(ip: String): Estado = withContext(Dispatchers.IO) {
        try {
            val texto = httpGet("http://$ip/estado").trim().uppercase()
            when (texto) {
                "SOLICITUD" -> Estado.SOLICITUD
                "APROBADO" -> Estado.APROBADO
                "RECHAZADO" -> Estado.RECHAZADO
                "IDLE" -> Estado.IDLE
                else -> Estado.IDLE
            }
        } catch (e: Exception) {
            Estado.ERROR
        }
    }

    private fun enviarOrden(ruta: String) {
        if (ipPanel.isEmpty()) return
        binding.btnAprobar.isEnabled = false
        binding.btnRechazar.isEnabled = false
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    httpGet("http://$ipPanel/$ruta")
                    true
                } catch (e: Exception) {
                    false
                }
            }

            val nombreAccion = if (ruta == "aprobar") "Aprobar Solicitud" else "Rechazar Solicitud"
            if (ok) {
                binding.tvDetalle.text = "Orden '${if (ruta == "aprobar") "a" else "r"}' enviada al panel."
                dbHelper.registrarAccion(usuarioActual, nombreAccion, "Orden '$ruta' enviada con éxito a $ipPanel")
            } else {
                binding.tvDetalle.text = "No se pudo contactar al panel."
                dbHelper.registrarAccion(usuarioActual, nombreAccion, "Fallo al enviar orden '$ruta' a $ipPanel")
            }
            actualizarEstadisticasBD()
        }
    }

    private fun enviarPruebaLeds(ruta: String) {
        if (ipPanel.isEmpty()) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    httpGet("http://$ipPanel/$ruta")
                    true
                } catch (e: Exception) {
                    false
                }
            }

            val descripcion = if (ruta == "carafeliz") "Cara Feliz mostrada en LED Matrix 8x12" else "LED Matrix 8x12 apagada"
            if (ok) {
                binding.tvDetalle.text = "Comando '$ruta' enviado a la matriz LED 8x12."
                dbHelper.registrarAccion(usuarioActual, "Prueba LEDs", descripcion)
            } else {
                binding.tvDetalle.text = "No se pudo enviar el comando '$ruta' al Arduino."
                dbHelper.registrarAccion(usuarioActual, "Prueba LEDs", "Fallo al enviar '$ruta'")
            }
            actualizarEstadisticasBD()
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.inputStream.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream))
            return reader.readText()
        }
    }

    private fun aplicarEstado(estado: Estado) {
        mostrarEstado(estado)
        val esSolicitudPendiente = estado == Estado.SOLICITUD
        binding.btnAprobar.isEnabled = esSolicitudPendiente
        binding.btnRechazar.isEnabled = esSolicitudPendiente
        actualizarChip(
            when (estado) {
                Estado.ERROR -> ChipEstado.SIN_SENAL
                Estado.DESCONECTADO -> ChipEstado.DESCONECTADO
                else -> ChipEstado.CONECTADO
            }
        )
    }

    private fun mostrarEstado(estado: Estado) {
        when (estado) {
            Estado.DESCONECTADO -> {
                binding.tvLcd.text = getString(R.string.estado_desconectado)
                pintarSemaforo(rojo = false, amarillo = false, verde = false)
            }
            Estado.IDLE -> {
                binding.tvLcd.text = getString(R.string.estado_idle)
                pintarSemaforo(rojo = false, amarillo = false, verde = false)
            }
            Estado.SOLICITUD -> {
                binding.tvLcd.text = getString(R.string.estado_solicitud)
                pintarSemaforo(rojo = false, amarillo = true, verde = false)
            }
            Estado.APROBADO -> {
                binding.tvLcd.text = getString(R.string.estado_aprobado)
                pintarSemaforo(rojo = false, amarillo = false, verde = true)
            }
            Estado.RECHAZADO -> {
                binding.tvLcd.text = getString(R.string.estado_rechazado)
                pintarSemaforo(rojo = true, amarillo = false, verde = false)
            }
            Estado.ERROR -> {
                binding.tvLcd.text = "Sin conexión con el panel"
                pintarSemaforo(rojo = false, amarillo = false, verde = false)
            }
        }
    }

    private fun pintarSemaforo(rojo: Boolean, amarillo: Boolean, verde: Boolean) {
        binding.ledRojo.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (rojo) Color.parseColor("#D32F2F") else Color.parseColor("#CCCCCC")
        )
        binding.ledAmarillo.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (amarillo) Color.parseColor("#F57C00") else Color.parseColor("#CCCCCC")
        )
        binding.ledVerde.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (verde) Color.parseColor("#388E3C") else Color.parseColor("#CCCCCC")
        )
    }

    private fun actualizarChip(estado: ChipEstado) {
        val (texto, color) = when (estado) {
            ChipEstado.DESCONECTADO -> getString(R.string.chip_desconectado) to R.color.chip_neutro
            ChipEstado.CONECTADO -> getString(R.string.chip_conectado) to R.color.chip_ok
            ChipEstado.SIN_SENAL -> getString(R.string.chip_sin_senal) to R.color.chip_error
        }
        binding.tvChipEstado.text = texto
        binding.tvChipEstado.background.setTint(getColor(color))
    }

    private fun actualizarEstadisticasBD() {
        val totalConexiones = dbHelper.obtenerCantidadConexiones()
        val palabraVez = if (totalConexiones == 1) "vez" else "veces"
        binding.tvTotalConexiones.text = getString(R.string.label_conexiones_formato, totalConexiones, palabraVez)
    }

    private fun cerrarSesion() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        scope.cancel()
    }

    private enum class Estado { DESCONECTADO, IDLE, SOLICITUD, APROBADO, RECHAZADO, ERROR }
    private enum class ChipEstado { DESCONECTADO, CONECTADO, SIN_SENAL }
}
