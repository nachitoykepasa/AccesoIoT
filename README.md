# AccesoIoT — App móvil de aprobación remota (TI3042)

Aplicación Android (Kotlin) que implementa la **MC B** descrita en el informe
"Sistema de Control de Acceso con Solicitud Táctil y Aprobación Remota":
reemplaza al Monitor Serie leyendo el estado publicado por el servidor HTTP
que corre directamente en el **Arduino UNO R4 WiFi** (módulo WiFi integrado
WiFiS3, sin necesidad de un ESP8266/ESP32 externo) y enviando la orden de
aprobación (`a`) o rechazo (`r`) que el propio Arduino procesa.

## Cómo abrir el proyecto

1. Abre Android Studio → **Open** → selecciona la carpeta `AccesoIoT`.
2. Deja que Gradle sincronice (usa AGP 8.5.2 / Kotlin 1.9.24 / compileSdk 34,
   con Gradle 8.7, que requiere un JDK entre 8 y 21 — ver sección
   "Gradle JVM" más abajo si tu Android Studio trae un JDK más nuevo).
3. Conecta un dispositivo o usa un emulador en la misma red WiFi que el
   Arduino UNO R4 WiFi (o el mismo hotspot, si el Arduino actúa como
   Access Point).
4. Ejecuta `app`.

## Gradle JVM incompatible (Gradle 8.7 vs JDK 25)

Si Android Studio marca el proyecto con el error *"The project's Gradle
version 8.7 is incompatible with the Gradle JVM version 25..."*, es porque
el JDK que Android Studio está usando **para ejecutar Gradle** (no para
compilar tu app) es demasiado nuevo: Gradle 8.7 solo soporta JDK 8 a 21.
Se soluciona cambiando esa JVM, sin tocar el código:

1. **Settings / Preferences** → **Build, Execution, Deployment** →
   **Build Tools** → **Gradle**.
2. En **Gradle JDK**, elige un JDK 17 o 21 (Android Studio suele traer uno
   embebido, listado como `jbr-17` o similar; si no aparece ninguno,
   usa **Download JDK...** y elige la versión 17 o 21).
3. Aplica los cambios y deja que Gradle vuelva a sincronizar.

Esto es una configuración del IDE/máquina, por eso no se guarda en el
proyecto ni se puede "arreglar" editando un archivo del repo.

## Contrato HTTP esperado en el Arduino UNO R4 WiFi

El firmware del Arduino UNO R4 WiFi debe exponer un servidor HTTP ligero
(con la librería `WiFiS3`, incluida en el core del R4) con este contrato
mínimo (ajústalo en `MainActivity.kt` si tu firmware usa otras rutas):

| Endpoint     | Método | Respuesta esperada                                  |
|--------------|--------|------------------------------------------------------|
| `/estado`    | GET    | texto plano: `IDLE`, `SOLICITUD`, `APROBADO` o `RECHAZADO` |
| `/aprobar`   | GET    | aplica la aprobación en el propio Arduino, responde `OK` |
| `/rechazar`  | GET    | aplica el rechazo en el propio Arduino, responde `OK` |

Esto refleja el flujo del informe: el Arduino pasa a `"SOLICITUD"` cuando
el patrón táctil es correcto (Estado 3 – `ESPERANDO_APROBACION`), y espera
de vuelta `'a'`/`'r'` (vía estos endpoints HTTP) para pasar al Estado 4
(`BLOQUEADO`) y mover el servo/semáforo. Como el R4 WiFi trae el chip WiFi
integrado, no hay un segundo microcontrolador ni `SoftwareSerial` de por
medio: el mismo Arduino que lee el teclado/patrón es el que sirve el
HTTP.

## Entorno de pruebas (WiFi compartido desde el teléfono)

Como es un entorno controlado, el flujo pensado es: el celular comparte
internet (hotspot) y el Arduino UNO R4 WiFi se une a esa misma red WiFi en
modo estación (no como Access Point). Cada vez que se arma el montaje, el
router/hotspot le asigna una IP distinta al Arduino, así que la app **no
trae ninguna IP fija**:

- El campo de IP arranca vacío (con el hint `192.168.43.1` solo como
  ejemplo) y se guarda localmente (`SharedPreferences`) la última IP que
  funcionó, para no tener que volver a escribirla si no cambió.
- Para saber qué IP le tocó al Arduino UNO R4 WiFi en el hotspot, revisa
  la lista de dispositivos conectados en Ajustes → Hotspot del teléfono,
  o imprímela por el Monitor Serie del propio Arduino al conectarse
  (`WiFi.localIP()`).

## Qué hace la app

- Tarjeta de conexión con el campo de IP editable y botón
  **Conectar / Desconectar**; un chip en la esquina superior muestra
  "Desconectado", "Conectado" o "Sin señal del panel".
- Sondeo (`polling`) cada 2 segundos a `/estado` mientras está "Conectado".
- Pantalla tipo LCD (simulada) que replica los mensajes del informe:
  "Sin solicitudes pendientes", "Nueva solicitud de acceso", "Acceso
  concedido", "Acceso denegado".
- Semáforo R-A-V (con etiquetas Rechazado/Pendiente/Aprobado) que refleja
  el mismo estado que los LEDs físicos del Arduino.
- Botones **Aprobar (a)** / **Rechazar (r)**, habilitados solo cuando hay
  una solicitud pendiente, que llaman a `/aprobar` o `/rechazar`.

## Limitaciones y brechas de seguridad ya señaladas en el informe

- La comunicación es HTTP en **texto plano**, sin autenticación (por eso
  `usesCleartextTraffic="true"` en el manifest). El informe ya identifica
  esto como vulnerabilidad ("Aprobación no autenticada por WiFi") y
  plantea como mitigación futura un token/clave por mensaje y
  HTTPS/TLS — no implementado aquí porque tampoco lo está en el firmware
  actual del Arduino.
- No hay reintentos ni cola de solicitudes: si el sondeo falla, la app
  solo muestra "Sin conexión con el panel" y lo reintenta en el siguiente
  ciclo.

## Estructura

```
AccesoIoT/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/cl/inacap/accesoiot/MainActivity.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/{strings,colors,themes}.xml
│           ├── drawable/{circulo_led,ic_launcher_foreground}.xml
│           └── mipmap-anydpi-v26/ic_launcher*.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
