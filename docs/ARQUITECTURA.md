# Arquitectura comprobada — DIDI-M01

Inspección de código de `eb0c17e3b004c87bd305a54b5103aac623a78e30`, 2026-11-09 (America/Mexico_City). Este mapa describe código existente; no certifica ejecución física. Resultados ejecutados: [ENTREGA_WORK.md](ENTREGA_WORK.md).

## Estructura y fuente operativa

| Ruta desde la raíz Git | Uso |
| --- | --- |
| `DidiProfitOverlay/` | Único proyecto operativo; ejecutar aquí el wrapper |
| `DidiProfitOverlay/app/` | Único módulo Android, incluido por `settings.gradle.kts` |
| `app/src/main/java/com/jonathan/didiprofit/` dentro del proyecto | Lógica y UI Kotlin, sin Compose ni arquitectura por módulos |
| `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/styles.xml` | Componentes, permisos y tema |
| `app/src/test/java/com/jonathan/didiprofit/` | Cinco clases de pruebas unitarias |
| `.github/workflows/build-apk.yml` | Workflow activo de release firmada |
| `.github/workflows/.github/workflows/build-apk.yml` | Copia anidada antigua de debug; no está en el nivel de descubrimiento de workflows |
| `DidiProfitOverlay_V3.6.0_patch/` | Parche histórico incompleto; no compilar ni editar |
| `scripts/`, `docs/`, `AGENTS.md` | Preparación M01 y continuidad del trabajo |

`DidiProfitOverlay/README.md`, `BUILD_NOTES.txt` y `PARSER_TEST_RESULTS.txt` aún describen V2. Se conservaron como antecedentes: ni identifican la versión actual ni prueban la ejecución de M01.

## Componentes y flujo

1. **MainActivity** es la actividad launcher exportada. Construye vistas Android programáticamente, muestra resumen diario, abre configuración/historial y solicita permisos de notificación, overlay y captura mediante `MediaProjectionManager.createScreenCaptureIntent()`. Inicia/detiene `CaptureService`.
2. **CaptureService**, no exportado y foreground de tipo `mediaProjection`, recibe el resultado autorizado. Crea MediaProjection, VirtualDisplay e ImageReader RGBA de tres buffers; registra callback de detención antes del VirtualDisplay. No hay AccessibilityService ni automatización de aceptación/rechazo en los componentes declarados. El manifiesto fuente no declara INTERNET, pero el manifiesto final sí incorpora INTERNET y ACCESS_NETWORK_STATE desde `com.google.android.datatransport:transport-backend-cct:2.3.3` (reporte de merger y permisos de la release histórica comprobados). No se probó tráfico de red; el carácter local del modelo OCR no acredita ausencia de telemetría de dependencias.
3. **Captura/OCR**: hilo `didi-ocr`, exclusión `ocrBusy` y una única imagen pendiente más reciente (las anteriores se cierran). Intervalo mínimo 320 ms, reintento ocupado 60 ms. Convierte imagen a bitmap, recorta 3.5% superior y reduce anchos mayores a 1080. Primera pasada: 75% inferior; sólo si no resuelve, OCR de la captura completa de ese mismo frame. ML Kit Latin bundled `16.0.1`, procesamiento local. Ordena líneas por coordenada Y; no fusiona lecturas de frames distintos.
4. **OfferParser** normaliza Unicode, comas decimales y acentos para marcadores. Excluye resúmenes del overlay, importes adicionales/base dinámica y rating/viajes. Busca tarifa principal por símbolo/tamaño/posición y dos filas completas de minutos-distancia debajo; fallback conservado a texto simple. Convierte metros a km y rechaza mensajes inactivos. No reconstruye filas transitorias partidas. El servicio aplica límites de plausibilidad: tarifa 10–3000, minutos totales 2–360, km 0.2–500, $/h 20–5000 y $/km 0.5–500.
5. **HistoryCandidateTracker** exige tres lecturas parseadas consecutivas estables; tarifa ±0.02, minutos de cada tramo iguales y km de cada tramo ±0.12. Confirma una vez por secuencia y se reinicia ante cambio, fallo definitivo, inactividad o pausa. La confirmación es algorítmica; no hay diálogo de aprobación manual de cada propuesta. El overlay puede mostrar una lectura antes de su confirmación histórica.
6. **OverlayController** posee los estados `ANALYZING`, `TIMER_SETUP`, `TIMER_RUNNING`, `TIMER_FINISHED`, crea `TYPE_APPLICATION_OVERLAY` con flags existentes y controla arrastre/diana de cierre. `CircularTimerView` dibuja el temporizador. No se modificaron parámetros de WindowManager.
7. **Temporizador**: doble toque en análisis abre configuración, pausa OCR/historial y precarga minutos de la última oferta o el valor predeterminado. Al comenzar se libera MediaProjection/VirtualDisplay/ImageReader, pero permanece el servicio/overlay. Usa `SystemClock.elapsedRealtime()` para duración; hora de reloj para final estimado. El círculo y el panel expandido alternan por toque sin reiniciar. Finalizar requiere mantener 900 ms en el botón del panel expandido; soltar/salir cancela. Al superar el tiempo sigue contando excedente. El resumen se congela al finalizar.
8. **Reanudación**: cancelar `TIMER_SETUP` conserva la autorización vigente, porque todavía no se detuvo la proyección. Desde running/finished se abre MainActivity para nueva autorización oficial; sólo `onProjectionResumed()` retorna a análisis. Si se cancela el permiso, conserva temporizador/resumen. La detención externa de proyección marca `AnalysisResumeState` (SharedPreferences) y notifica; no se reutiliza el token para reiniciar silenciosamente. `onDestroy` libera callbacks, imágenes, recognizer, hilo, display y overlay. Recuperación tras muerte de proceso requiere prueba física: el temporizador es estado en memoria.

## Modelos y cálculos

`RouteMetric(minutes, kilometers)` y `RideOffer(fare, pickup, trip)` en `OfferModels.kt`:

- Minutos/km totales = recogida + viaje.
- Pesos/hora = tarifa × 60 / minutos totales; pesos/km = tarifa / km totales; cero si denominador no positivo.
- Mínimo Pon Tu Precio = objetivo/h × minutos / 60, redondeado hacia arriba a centavos con epsilon 1e-9.
- Puntaje: componentes por umbral bueno/excelente, cada uno 0–100; ponderación 65% $/h y 35% $/km; redondeo y límite 0–100.
- Etiquetas: Excelente 85–100, Muy buena 70–84, Buena 55–69, Regular 40–54, Baja <40.
- `TripTimeEstimator`: suma minutos × 60,000 al instante de referencia; acepta 1–360 minutos.

`ProfitPreferences` usa SharedPreferences `profit_settings`, valores predeterminados: excelente/bueno 300/220 $/h, 8/6 $/km y objetivos 150/180/210 $/h. `SettingsActivity` valida entradas positivas y persiste configuración. `AppUi` contiene estilos y helpers de vistas.

## Persistencia y navegación

`HistoryRepository` usa SQLiteOpenHelper, archivo `ride_history.db`, esquema **1**, tabla `offers`, índice `last_seen`. Guarda tarifa, tramos, rentabilidad, puntaje, first/last seen y contador. `onUpgrade` no realiza operaciones. No se ejecutó ninguna operación sobre una base de usuario.

`recordOrMerge` busca entre los últimos 40 registros vistos en diez minutos: tolerancias tarifa 0.02, minutos de cada tramo 1, recogida 0.60 km y viaje 0.25 km. Si coincide conserva los valores numéricos canónicos, actualiza `last_seen` y aumenta contador sólo si reaparece tras al menos 15 segundos. Estadísticas/fechas usan `first_seen`. No equivale a identificar de manera infalible un viaje único.

`HistoryActivity` muestra historial/estadísticas y `TopTenActivity` rankings por puntaje, $/h y $/km con selección temporal. Existen acciones de borrar fila/vaciar con diálogo de confirmación; M01 no las utilizó. El manifiesto permite backup (`allowBackup=true`); no se cambió ni se verificó copia/restauración real.

## Automatización y límites

El workflow activo usa checkout v4, setup-java v5 Temurin 17 y setup-gradle v4 Gradle 8.13; ejecuta `gradle :app:assembleRelease`, valida secretos, hash del keystore, alias y capacidad de firmar un JAR; carga `DiDi-Rentabilidad-V3-APK`. No ejecuta unit tests ni lint ni extrae el certificado de la APK. Disparadores: push a main y workflow_dispatch. M01 sólo consultó evidencia histórica; no disparó ejecución ni cambió el workflow.

La compilación no demuestra permisos reales, precisión de ML Kit con capturas actuales, ausencia absoluta de falsos positivos, comportamiento bajo bloqueo de pantalla o continuidad del temporizador en Android. Véase [REGRESIONES_Y_PRUEBAS.md](REGRESIONES_Y_PRUEBAS.md).
