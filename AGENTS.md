# DiDi Rentabilidad

Aplicación Android existente de análisis informativo de ofertas visibles de DiDi. Base de transición M01: `eb0c17e3b004c87bd305a54b5103aac623a78e30`, versionName `0.3.7.1`, versionCode `23`, applicationId/namespace `com.jonathan.didiprofit`.

## Antes de cada encargo

1. Leer `docs/ESTADO_PROYECTO.md`, `docs/ENTREGA_WORK.md`, `docs/DESARROLLO_Y_ENTREGA.md` y los casos pertinentes de `docs/REGRESIONES_Y_PRUEBAS.md`.
2. Comprobar cwd, `git remote -v`, rama, HEAD, `git status --short --branch` y diff. Conservar cambios del usuario; no resetear, limpiar ni asumir que main coincide con esta base. Comparar remoto sólo por lectura/fetch cuando corresponda.
3. Trabajar en rama de alcance específico y seguir la autorización del encargo vigente. M02 permite publicar su rama y abrir PR hacia main; no permite merge, push directo a main, release, cambios de secretos ni instalación sobre la app del usuario.

## Mapa y comandos

- Fuente única: `DidiProfitOverlay/`, módulo `:app`; Kotlin en `app/src/main/java/com/jonathan/didiprofit/`.
- Entrada: MainActivity → CaptureService → OfferParser/HistoryCandidateTracker/HistoryRepository + OverlayController/CircularTimerView. SettingsActivity, HistoryActivity y TopTenActivity completan navegación. Detalles: `docs/ARQUITECTURA.md`.
- No compilar/editar `DidiProfitOverlay_V3.6.0_patch/` ni reutilizar el workflow anidado `.github/workflows/.github/`. No eliminar históricos.
- Desde raíz Git: `. .\scripts\Preparar-Entorno.ps1`; después `Set-Location DidiProfitOverlay`.
- Wrapper verificado: `.\gradlew.bat --version` (Gradle 8.13/JDK 17). Comprobaciones: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain --max-workers=2`. Consultar resultados y fallos reales en ENTREGA_WORK; no equiparar assemble con tests.
- Linux/macOS: JDK 17 y SDK 35 configurados, `./gradlew ...`. No afirmar verificación en esos sistemas; sólo Windows fue utilizado en M01.
- CI de PR: `.github/workflows/validate-pr.yml`, tests/lint/debug sin secretos. La firma release sigue en `build-apk.yml` y se activa por push a main o workflow_dispatch; no fusionar/activarlo sin el permiso correspondiente. Consultar resultados remotos reales en ENTREGA_WORK.

## Límites de edición y entrega

- Conservar MediaProjection autorizada, ML Kit OCR local de un frame, fallback ROI/pantalla del mismo frame y parser actual. No Uber, fusión de frames, reconstrucciones agresivas ni cambios de WindowManager como optimización.
- Nunca aceptar/rechazar/manipular solicitudes de DiDi. No alterar fórmulas, overlay, estados de timer o controles fuera del encargo.
- Historial: tres lecturas estables antes de guardar; conservar valores canónicos/deduplicación y first_seen. No borrar, reiniciar ni migrar SQLite o datos existentes.
- Timer: setup pausa OCR; comenzar libera captura; círculo/expandido conservan duración; finalizar requiere mantener 900 ms. Reanudar tras inicio exige nueva autorización; cancelar permiso conserva estado.
- No incrementar versión, cambiar applicationId, generar/reemplazar keystore release ni exponer secretos. Debug sólo es verificación técnica y no debe entregarse como actualización de release.
- Mantener dependencias/versiones operativas salvo necesidad demostrada y autorizada. Usar herramientas/cachés válidas; evitar reinstalaciones, clean rutinario y repeticiones de tests sin nueva evidencia.
- Ejecutar validaciones proporcionales y registrar comando, salida/código y evidencia. Distinguir inspección, tests ejecutados y dispositivo físico pendiente. Ante fallos preexistentes diagnosticar y reportar, sin refactorizar por iniciativa propia.
- Revisar diff completo y exclusión de secretos antes de commit local. Actualizar ESTADO_PROYECTO y ENTREGA_WORK tras cada encargo: base/rama/commit, cambios, pruebas/compilación/firma, rutas de evidencia, pendientes y siguiente paso. No declarar capacidad de firma ni continuidad sin certificado y evidencia suficiente.
