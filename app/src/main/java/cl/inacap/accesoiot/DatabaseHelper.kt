package cl.inacap.accesoiot

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AccionLog(
    val id: Long,
    val usuario: String,
    val accion: String,
    val detalle: String,
    val fecha: String
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "acceso_iot.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_CONEXIONES = "conexiones"
        private const val TABLE_ACCIONES = "acciones"

        private const val KEY_ID = "id"
        private const val KEY_IP = "ip"
        private const val KEY_FECHA = "fecha"

        private const val KEY_USUARIO = "usuario"
        private const val KEY_ACCION = "accion"
        private const val KEY_DETALLE = "detalle"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createConexionesTable = """
            CREATE TABLE $TABLE_CONEXIONES (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_IP TEXT NOT NULL,
                $KEY_FECHA TEXT NOT NULL
            )
        """.trimIndent()

        val createAccionesTable = """
            CREATE TABLE $TABLE_ACCIONES (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_USUARIO TEXT NOT NULL,
                $KEY_ACCION TEXT NOT NULL,
                $KEY_DETALLE TEXT NOT NULL,
                $KEY_FECHA TEXT NOT NULL
            )
        """.trimIndent()

        db.execSQL(createConexionesTable)
        db.execSQL(createAccionesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONEXIONES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACCIONES")
        onCreate(db)
    }

    private fun obtenerFechaActual(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun registrarConexion(ip: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_IP, ip)
            put(KEY_FECHA, obtenerFechaActual())
        }
        return db.insert(TABLE_CONEXIONES, null, values)
    }

    fun obtenerCantidadConexiones(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CONEXIONES", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    fun registrarAccion(usuario: String, accion: String, detalle: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_USUARIO, usuario)
            put(KEY_ACCION, accion)
            put(KEY_DETALLE, detalle)
            put(KEY_FECHA, obtenerFechaActual())
        }
        return db.insert(TABLE_ACCIONES, null, values)
    }

    fun obtenerHistorialAcciones(limit: Int = 30): List<AccionLog> {
        val lista = mutableListOf<AccionLog>()
        val db = readableDatabase
        val query = "SELECT $KEY_ID, $KEY_USUARIO, $KEY_ACCION, $KEY_DETALLE, $KEY_FECHA FROM $TABLE_ACCIONES ORDER BY $KEY_ID DESC LIMIT $limit"
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID))
                val usuario = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USUARIO))
                val accion = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ACCION))
                val detalle = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DETALLE))
                val fecha = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FECHA))

                lista.add(AccionLog(id, usuario, accion, detalle, fecha))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }
}
