# Estado del proyecto

Revisión: **2026-11-10, America/Mexico_City**. Último encargo: **DIDI-M02 — integración controlada con GitHub y CI**.

**Estado M02: BLOQUEADO en publicación/validación remota por falta de autenticación de escritura. Preparación local concluida.** El usuario indicó que autenticará Git local; todavía no confirmó que haya terminado. No existe PR ni resultado remoto M02 aprobado.

## Identidad y ubicación

- Raíz Git: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay`.
- Build: `DidiProfitOverlay/`, único módulo `:app`.
- Origin: `https://github.com/jvaldivia1097-dotcom/didiprofitoverlay.git`.
- Rama local: `codex/didi-m02-ci`, derivada de M01 `20a6ce41781a626f659d07a581a6dbf48b94d955`.
- Commit CI: `45ca934b05ab649fa53ca5908d1581236e5aa1e4`. Commit documental de cierre: consultar `git log -1` y `..\local-evidence\M02\git-final.log`.
- Main local, origin/main y main remoto comprobados en `eb0c17e3b004c87bd305a54b5103aac623a78e30`; sin avances. Árbol inicial limpio; M01 íntegro.
- Identidad: `com.jonathan.didiprofit`, versionName **0.3.7.1**, versionCode **23**, min **26**, compile/target **35**, sin cambios.

## Componentes y entorno

MainActivity, CaptureService/MediaProjection/ML Kit Latin local de un frame, OfferParser, modelos/cálculos, HistoryCandidateTracker, HistoryRepository SQLite v1, OverlayController/CircularTimerView, AnalysisResumeState y actividades de configuración/historial/rankings. Detalle: [ARQUITECTURA.md](ARQUITECTURA.md).

Todo `DidiProfitOverlay/`, el workflow firmado e históricos permanecen idénticos a M01. No editar/compilar `DidiProfitOverlay_V3.6.0_patch/` ni workflow debug anidado; fuente única `DidiProfitOverlay/`.

Se reutilizan Git 2.56.0.windows.4, Temurin 17.0.21+9, Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21, ML Kit 16.0.1 y SDK API 35/Build Tools 35.0.0 de M01. Herramientas/cachés en `..\.tools`, sesión mediante `scripts/Preparar-Entorno.ps1`; sin reinstalación ni ajustes globales. M02 añadió sólo actionlint portable 1.7.12 con checksum oficial verificado.

## CI y comprobaciones reales

Workflow nuevo `.github/workflows/validate-pr.yml`: PR hacia main, Ubuntu 24.04/Java 17/wrapper 8.13/SDK 35; tests, lint y debug separados, sin secretos ni release, permisos contents:read, validación de wrapper y reportes 14 días. No se cambiaron reglas de protección de main.

| Comprobación | Resultado |
| --- | --- |
| M01 reutilizado | 15 tests aprobados; lint 0 errores/70 advertencias; debug compilada/verificada |
| Actionlint M02 | YAML válido, exit 0; sin shellcheck externo disponible |
| Gradle M02 local | Dry-run offline resuelve las tres tareas; exit 0, 35 s; tareas SKIPPED por simulación |
| Tests/build locales nuevos | No repetidos: fuente/wrapper/dependencias idénticos a M01 |
| Publicación | Git sin autenticación; conexión GitHub rechaza escritura con 403 |
| PR/CI remoto M02 | No creados/no ejecutado; esperando autenticación legítima |
| Release local / físico | No ejecutados; pendientes de M01 conservados |

Las 70 advertencias siguen registradas en `docs/evidencias/lint-results-debug.txt` y `resumen-verificaciones.json`. Son referencia histórica, no baseline ni supresión; CI reportará resultados reales y errores de tareas harán fallar el job.

El workflow firmado `build-apk.yml` no cambió: push a main o workflow_dispatch. **Un futuro merge activaría ese build firmado existente**; considerar esta consecuencia antes de autorizar integración. M02 no activó ese workflow ni modificó secretos.

## Restricciones, pendientes y siguiente paso

Conservar OCR de un frame, confirmación de tres lecturas, SQLite/datos, cálculos, navegación, overlay, timer circular/expandido y finalización de 900 ms; iniciar timer libera captura y reanudar exige nueva autorización. Sin Uber, fusión de frames, reconstrucciones agresivas, cambios de WindowManager o identidad/firma. Matriz física pendiente: [REGRESIONES_Y_PRUEBAS.md](REGRESIONES_Y_PRUEBAS.md).

Firma release local aún no disponible. Huella pública de referencia M01: `57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12`; no acredita firma privada local ni coincidencia con teléfono. No se reprodujeron credenciales. Backup/restauración, muerte de proceso del timer y tráfico/telemetría de dependencias siguen sin probarse; modelo OCR local no acredita ausencia de tráfico.

Siguiente paso: el usuario termina autenticación legítima de Git y avisa, sin enviar secretos al chat. Codex publicará sólo la rama M02, abrirá PR hacia main, observará CI y actualizará estado/informe con enlaces y resultados. La autorización M02 ya cubre esas operaciones. **No merge, push a main, despacho de release, cambio de secretos ni instalación de APK.**

M02 añadió CI, ajustó AGENTS al alcance y documentó comprobaciones. Evidencia local en `docs/evidencias/M02/`; reporte vigente [ENTREGA_WORK.md](ENTREGA_WORK.md). M01 permanece íntegro en el historial del commit `20a6ce4`. Actualizar este archivo después de cada encargo.
