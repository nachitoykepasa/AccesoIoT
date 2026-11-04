package cl.inacap.accesoiot

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cl.inacap.accesoiot.databinding.ItemAccionLogBinding

class HistorialAdapter(private val lista: List<AccionLog>) :
    RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAccionLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccionLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val b = holder.binding

        b.tvBadgeAccion.text = item.accion.uppercase()
        b.tvDetalle.text = item.detalle
        b.tvFecha.text = item.fecha
        b.tvUsuario.text = "Por: ${item.usuario}"

        val colorHex = when {
            item.accion.contains("Aprobar", ignoreCase = true) -> "#388E3C"
            item.accion.contains("Rechazar", ignoreCase = true) -> "#D32F2F"
            item.accion.contains("Conexión", ignoreCase = true) -> "#1976D2"
            item.accion.contains("Desconexión", ignoreCase = true) -> "#F57C00"
            else -> "#666666"
        }
        b.tvBadgeAccion.setTextColor(Color.parseColor(colorHex))
    }
}
