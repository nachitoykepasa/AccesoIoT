package cl.inacap.accesoiot

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import cl.inacap.accesoiot.databinding.ActivityHistorialBinding

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        binding.btnVolver.setOnClickListener {
            finish()
        }

        cargarHistorial()
    }

    private fun cargarHistorial() {
        val totalConexiones = dbHelper.obtenerCantidadConexiones()
        val listaAcciones = dbHelper.obtenerHistorialAcciones(100)

        binding.tvStatConexiones.text = totalConexiones.toString()
        binding.tvStatAcciones.text = listaAcciones.size.toString()

        if (listaAcciones.isEmpty()) {
            binding.tvVacio.visibility = View.VISIBLE
            binding.rvHistorial.visibility = View.GONE
        } else {
            binding.tvVacio.visibility = View.GONE
            binding.rvHistorial.visibility = View.VISIBLE
            binding.rvHistorial.layoutManager = LinearLayoutManager(this)
            binding.rvHistorial.adapter = HistorialAdapter(listaAcciones)
        }
    }
}
