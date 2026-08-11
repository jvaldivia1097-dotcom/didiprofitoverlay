# DiDi Rentabilidad Overlay — V1

Aplicación Android personal que analiza ofertas visibles de DiDi Conductor y muestra un panel flotante con:

- **$/hora total**, incluyendo minutos de recogida + minutos del viaje.
- **$/km total**, incluyendo km de recogida + km del viaje.
- Total de minutos, km y tarifa detectada.
- Semáforo configurable para $/hora y $/km.

## Flujo

1. La app solicita permiso `SYSTEM_ALERT_WINDOW` para mostrar un overlay.
2. Al pulsar **Iniciar análisis**, Android muestra su diálogo oficial de captura de pantalla (`MediaProjection`). Si ofrece elegir una sola app, se puede seleccionar DiDi Conductor; también funciona compartiendo toda la pantalla.
3. Un foreground service recibe imágenes de la pantalla aproximadamente cada 800 ms.
4. ML Kit Text Recognition (modelo Latin bundled) hace OCR local en el dispositivo.
5. `OfferParser` detecta tarifa y los primeros dos pares `min + distancia` de la tarjeta de viaje.
6. El overlay calcula y muestra la rentabilidad.

## Fórmulas

- `minutos totales = minutos recogida + minutos viaje`
- `km totales = km recogida + km viaje`
- `$/hora = tarifa / (minutos totales / 60)`
- `$/km = tarifa / km totales`

Las distancias en metros se convierten automáticamente a kilómetros.

## Valores iniciales del semáforo

- $/hora excelente: 300
- $/hora bueno: 220
- $/km excelente: 8
- $/km bueno: 6

Los cuatro valores se pueden editar desde la pantalla principal.

## Privacidad

La V1 no envía capturas ni datos a un servidor. El OCR usa el modelo bundled de ML Kit y se procesa en el teléfono.

## Importar en Android Studio

1. Abre esta carpeta como proyecto en Android Studio.
2. Deja que Android Studio sincronice Gradle y descargue las dependencias.
3. Conecta un teléfono Android con depuración USB o usa un emulador.
4. Ejecuta la app.

La sesión de MediaProjection debe ser autorizada por el usuario cada vez que se inicia el análisis en Android moderno.

## Alcance V1

La app **no toca ni modifica DiDi Conductor**, y **no acepta/rechaza viajes automáticamente**. Solo analiza lo que está visible en pantalla.

## Próxima iteración sugerida

Tras probarla con capturas reales del teléfono Android donde se usará DiDi, ajustar:

- región de pantalla para OCR (mejora velocidad y precisión),
- parser si DiDi cambia el orden o texto de las tarjetas,
- historial local de propuestas,
- estadísticas por hora/zona,
- puntuación combinada de rentabilidad.

## Compilación automática opcional con GitHub Actions

El proyecto incluye `.github/workflows/build-apk.yml`. Si se sube a un repositorio de GitHub, la acción **Build Android APK** compila `app-debug.apk` y lo deja como artefacto descargable del workflow.
