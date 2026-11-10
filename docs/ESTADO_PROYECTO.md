# Estado del proyecto

Revisión: **2026-11-09, America/Mexico_City**. Último encargo: **DIDI-M01 — preparación y transición a Codex**. Estado: **LISTO CON PENDIENTES**.

## Identidad y ubicación

- Raíz Git: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay`.
- Directorio de build: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay\DidiProfitOverlay`.
- Remoto: `https://github.com/jvaldivia1097-dotcom/didiprofitoverlay.git`.
- Rama de trabajo: `codex/didi-m01-preparacion`. Base comprobada: `eb0c17e3b004c87bd305a54b5103aac623a78e30`.
- Al clonar, `main`, `origin/main` y referencia V3.7.1 coincidían (ahead/behind 0/0), sin modificaciones. Main permanece en esa base; preparación en rama local. Commit de preparación identificado por `git log -1` y evidencia externa `..\local-evidence\git-final.log`.
- `applicationId`/namespace: `com.jonathan.didiprofit`; `versionName=0.3.7.1`, `versionCode=23`; min 26, target/compile 35. Coinciden código y APK histórica. Versiones/identidad no alteradas.

## Componentes presentes

MainActivity, CaptureService (MediaProjection + ImageReader/VirtualDisplay + ML Kit Latin local), OfferParser, modelos de rentabilidad, HistoryCandidateTracker, HistoryRepository SQLite v1, OverlayController, CircularTimerView, AnalysisResumeState, SettingsActivity, HistoryActivity y TopTenActivity. Mapa y fórmulas: [ARQUITECTURA.md](ARQUITECTURA.md).

Fuente única: `DidiProfitOverlay/`. Mantener fuera de edición/compilación el parche `DidiProfitOverlay_V3.6.0_patch/`. Hay un workflow debug anidado obsoleto; el workflow activo en nivel correcto es el de release firmada. README/BUILD_NOTES/PARSER_TEST_RESULTS de V2 son antecedentes, no estado actual.

## Entorno y comprobaciones

| Elemento | Estado confirmado |
| --- | --- |
| Git | 2.56.0.windows.4, reutilizado |
| JDK | Temurin 17.0.21+9 portable; java/javac operativos |
| Gradle | Wrapper 8.13 completado y ejecutado; distribución y JAR con hashes oficiales verificados |
| AGP / Kotlin / ML Kit | 8.13.2 / 2.3.21 / 16.0.1; sin cambios |
| SDK | API 35 rev. 2, Build Tools 35.0.0, command-line tools 22.0, platform-tools 37.0.1 |
| Sesión local | `scripts/Preparar-Entorno.ps1`; herramientas/caché en `..\.tools`, sin configuración global ni local.properties |
| Tests | 15 aprobados, 0 fallos/errores/omitidos, cinco clases existentes |
| Lint | 0 errores, 70 advertencias preexistentes; no se suprimieron |
| Debug | assembleDebug correcto; APK verificada con apksigner y aapt |
| Release | No compilada/firmada localmente; APK histórica de Actions recuperada y verificada |
| Físico | No ejecutado; no se instaló, desinstaló ni reemplazó la app del teléfono |

Única ejecución Android conjunta: tests + lint + assembleDebug, exit 0, 5m 50s, 53 tareas ejecutadas. Se resolvió fallo de sockets Java por ruta TEMP larga con propiedad de sesión `jdk.net.unixdomain.tmpdir`; se conservaron logs de diagnóstico. Procedimiento: [DESARROLLO_Y_ENTREGA.md](DESARROLLO_Y_ENTREGA.md).

## Firma, restricciones y pendientes

- Certificado SHA-256 de referencia obtenido de APK V3.7.1 de Actions: `57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12`; firma v2 válida. La debug tiene certificado distinto y no es actualización de release.
- Keystore original no disponible en el espacio autorizado; no se buscaron credenciales en otros proyectos. Integridad del contenedor sólo acreditada por el workflow histórico, no recalculada localmente. Capacidad actual de firmar release desde Codex **pendiente**. Conservar Actions y sus secretos; cualquier ejecución remota nueva requiere otro permiso.
- No se comparó certificado con la aplicación instalada ni se ensayó actualización conservando datos. Validación física pendiente de permisos, OCR, historial, overlay, temporizador, reanudación y recuperación. Matriz: [REGRESIONES_Y_PRUEBAS.md](REGRESIONES_Y_PRUEBAS.md).
- Conservar OCR de un frame, confirmación histórica por tres lecturas, SQLite/datos, círculo/expandido, pulsación 900 ms, captura detenida al comenzar timer y permiso nuevo al reanudar. No Uber, automatización de DiDi, reconstrucciones agresivas ni optimizaciones de WindowManager. No cambiar versiones, dependencias, applicationId o firma por rutina.
- Lint advierte principalmente textos incrustados, APIs obsoletas, accesibilidad de gestos y una referencia estática de contexto/overlay; no se corrigió por quedar fuera de M01. También recomienda SDK más reciente; no es autorización para actualizarlo.
- ML Kit incluye modelo local; APK final incorpora permisos INTERNET/ACCESS_NETWORK_STATE desde dependencias de transporte. No se examinó tráfico ni se certificó ausencia de telemetría. `allowBackup=true` es preexistente; respaldo/restauración no probados. Timer en memoria: no prometer persistencia ante muerte de proceso.

## Último trabajo y siguiente paso

M01 añadió wrapper, exclusiones/atributos Git, script de sesión y documentación/evidencias. Código funcional, manifiesto fuente, build de app, dependencias, tests, Actions y archivos históricos permanecen idénticos a la base. Sin publicación remota.

Siguiente paso recomendado: revisión de [ENTREGA_WORK.md](ENTREGA_WORK.md) en ChatGPT y encargo separado de validación física con la firma original, preservando la instalación y datos. Si se requieren nuevas release desde Codex, acordar previamente el canal seguro de firma o una ejecución autorizada de Actions. Actualizar este archivo después de cada encargo futuro.
