# Estado del proyecto

Revisión: **2026-11-10, America/Mexico_City**. Último encargo: **DIDI-M02 — integración continua**. **LISTO para el alcance CI de M02**; pendientes de proyecto: firma release local y validación física.

## Identidad, Git y entorno

Raíz Git: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay`; fuente única de build `DidiProfitOverlay/`, módulo :app. Origin: `https://github.com/jvaldivia1097-dotcom/didiprofitoverlay.git`. Rama publicada **codex/didi-m02-ci**, [PR #1 abierto hacia main](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/pull/1), sin merge. Base V3.7.1/main: `eb0c17e3b004c87bd305a54b5103aac623a78e30`; M01 `20a6ce4`, CI `45ca934`, documentación inicial `0936d07`, corrección SDK **eae4522**. Commit documental final: consultar git log -1; evidencia final en `..\local-evidence\M02\cierre-ci.json`.

Identidad conservada: **com.jonathan.didiprofit**, **versionName 0.3.7.1**, **versionCode 23**, min 26, compile/target 35. Herramientas M01 reutilizadas en `..\.tools`, mediante scripts/Preparar-Entorno.ps1: Git 2.56.0, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21, ML Kit 16.0.1, SDK 35/Build Tools 35.0.0. Sin reinstalaciones ni configuración global.

## Arquitectura y restricciones

MainActivity, CaptureService/MediaProjection/ML Kit local de un frame, OfferParser, modelos de rentabilidad, confirmación HistoryCandidateTracker, HistoryRepository SQLite v1, OverlayController/CircularTimerView, AnalysisResumeState, configuración/historial/rankings. Mapa: ARQUITECTURA.md.

Todo DidiProfitOverlay/, workflow release e históricos sin cambios respecto a M01. No editar/compilar DidiProfitOverlay_V3.6.0_patch ni workflow debug anidado. Conservar tres lecturas antes del historial, datos SQLite, círculo/expandido, finalización 900 ms, captura detenida al comenzar timer y autorización nueva al reanudar. Sin Uber, fusión de frames, reconstrucciones agresivas, optimizaciones WindowManager, cambio de identidad/versión/firma o manipulación automática de DiDi.

## CI comprobado

Workflow `.github/workflows/validate-pr.yml`: pull_request hacia main, Ubuntu 24.04, Temurin 17, android-actions/setup-android@v3 con paquetes platforms;android-35 y build-tools;35.0.0, wrapper 8.13 validado. Permisos contents:read, sin secretos ni release, caché de sólo lectura y reportes 14 días.

El [run inicial 42769093803](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/actions/runs/42769093803) falló exit 127: sdkmanager no estaba en PATH. Tests/lint/debug quedaron omitidos. La corrección elimina esa suposición; logs reales ubican sdkmanager en `/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/`.

El [run corregido 42769700249](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/actions/runs/42769700249), job 129674354253, pasó SDK/wrapper y **ejecutó tests, lint y debug con success**. XML: **15 pruebas, 0 fallos/errores/omitidas**. Lint: **0 errores y 71 advertencias**. Una advertencia adicional frente a las 70 de M01 recomienda Gradle 8.14.6 (AndroidGradlePluginVersion); no se actualizó ni suprimió. Artefacto 13741491249 y reportes verificados en docs/evidencias/M02. Actionlint 1.7.12 de la corrección: exit 0. No se repitieron pruebas/build Android locales.

El push documental final activa otra comprobación automática del HEAD del PR; su cierre se registra en `..\local-evidence\M02\cierre-ci.json`. Informe vigente: ENTREGA_WORK.md. Evidencias de M01/M02 en docs/evidencias; snapshots anteriores de bloqueo son históricos y se conservan como tales.

## Pendientes y siguiente paso

Git local autenticado ya permite publicar; el usuario creó el PR. La conexión GitHub de la app sigue limitada para escritura, pero sirve para consultar CI. No hay bloqueo actual de publicación Git.

Firma release privada local y comparación con teléfono siguen pendientes; certificado público M01: `57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12`. Validación física de permisos, DiDi/OCR, historial, overlay, temporizador/reanudación/recuperación según REGRESIONES_Y_PRUEBAS, todavía no ejecutada. Backup/restauración y tráfico/telemetría de dependencias no auditados; timer en memoria sin garantía ante muerte de proceso.

Siguiente paso: revisión del informe y PR en ChatGPT para decidir eventual integración. **Un merge hacia main activaría el workflow firmado existente**, que conserva push a main/workflow_dispatch. No fusionar, modificar main/secretos, publicar release ni instalar APK bajo M02. Actualizar este estado tras cada encargo futuro.
