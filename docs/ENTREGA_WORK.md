# DIDI-M02 — Entrega final de integración continua

Revisión: **2026-11-10, America/Mexico_City**. **Estado: LISTO para el alcance de CI de M02.** Persisten pendientes del proyecto de firma release local y validación física; no impiden esta comprobación de CI.

## Resultado y publicación

La rama **codex/didi-m02-ci** está publicada mediante Git local autenticado y el usuario creó manualmente el [PR #1 hacia main](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/pull/1). Se corrigió la preparación del SDK y el workflow del PR ejecutó realmente pruebas unitarias, lint y assembleDebug con éxito. No se fusionó el PR, no se creó otro y no se publicó APK release.

Raíz local: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay`. Proyecto operativo: `DidiProfitOverlay/`. Origin: `https://github.com/jvaldivia1097-dotcom/didiprofitoverlay.git`. Base funcional/main: `eb0c17e3b004c87bd305a54b5103aac623a78e30` (V3.7.1).

Commits de la rama: M01 `20a6ce4`; introducción CI `45ca934`; documentación inicial de bloqueo `0936d07`; corrección SDK **eae45221ff06b9d14a5421ba93116d282f909e8b**. La documentación de cierre se publica en un commit posterior de la misma rama (identificable por git log -1); se comprueba también el check del HEAD final antes de cerrar, con evidencia externa en `..\local-evidence\M02\cierre-ci.json`, evitando autorreferenciar un hash dentro del propio commit.

## Incidencia y corrección mínima

La [ejecución 42769093803](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/actions/runs/42769093803), job 129672416808, falló con `sdkmanager: command not found`, exit 127. Checkout y Java 17 pasaron; wrapper, tests, lint y debug quedaron **skipped**. Su resumen de referencia M01 no acredita ejecución de tests.

El workflow suponía erróneamente que sdkmanager estaba en PATH. Se sustituyó únicamente ese paso por **android-actions/setup-android@v3**, con `packages: 'platforms;android-35 build-tools;35.0.0'` y registro de licencias aceptadas desactivado. La acción prepara command-line tools, PATH y licencias e instala los paquetes concretos sin requerir una ubicación preexistente en PATH. Referencia del proveedor: [action.yml v3](https://github.com/android-actions/setup-android/blob/v3/action.yml).

Los logs de la [ejecución corregida 42769700249](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/actions/runs/42769700249) confirman invocaciones reales a **/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager** para ambos paquetes. Se conservan Java 17, Ubuntu 24.04, SDK 35, Build Tools 35.0.0, wrapper 8.13, AGP 8.13.2, Kotlin 2.3.21 y ML Kit 16.0.1.

## Comprobaciones y evidencia remota

Run corregido: **42769700249**, job **129674354253**, commit **eae45221ff06b9d14a5421ba93116d282f909e8b**, resultado **success**. SDK, validación de wrapper y versión Gradle también pasaron. Los pasos Android no fueron omitidos:

| Paso | Resultado real |
| --- | --- |
| Pruebas unitarias | **15 aprobadas**, 0 fallos, 0 errores, 0 omitidas; cinco XML de JUnit verificados |
| Android lint | **0 errores, 71 advertencias**, sin supresiones ni baseline |
| Assemble debug | **BUILD SUCCESSFUL**, paso de compilación aprobado |

Los logs registran BUILD SUCCESSFUL en 1m25s para unit tests, 39s para lint y 33s para debug. El artefacto **13741491249**, `android-pr-reports-42769700249-1`, contiene reportes, no APK. Se descargó/verificó su ZIP contra SHA-256 de GitHub **f28e64d85219f232274560fe26d3d26dd87b5ed3ff2c9262529245356763e731**. Se preservaron los XML unitarios y el informe lint en `docs/evidencias/M02/`; metadatos de runs/jobs/artefacto en `ci-remoto.json`. Originales HTML/XML y ZIP en `..\local-evidence\M02\ci-reports/` y `ci-reports.zip`.

Las **70 advertencias de M01** siguen como referencia histórica en `docs/evidencias/lint-results-debug.txt`. La diferencia remota es una advertencia **AndroidGradlePluginVersion**: recomienda Gradle 8.14.6 frente a 8.13 en gradle-wrapper.properties. El nombre del detector no cambia el mensaje concreto observado. Las otras ocho categorías conservan sus conteos de M01. Es una recomendación de actualización, no un error de compilación ni autorización para cambiar Gradle; se documentó sin ocultarla. No se presentaron errores nuevos de código Android.

## Validación local y eficiencia

Se reutilizaron herramientas, cachés y pruebas de M01. Sobre esta corrección se ejecutó **actionlint 1.7.12** sobre `.github/workflows/validate-pr.yml`, sin diagnósticos, exit 0; sin shellcheck externo disponible. Se comprobó git diff --check y ausencia de diferencias de Android/firma/históricos contra M01. No se repitieron tests/build Android locales ni preparación de entorno.

La simulación offline M02 anterior (35 s, exit 0) sólo había probado resolución del grafo: no ejecutó tests ni demostró disponibilidad del SDK en GitHub. La aceptación actual se basa en el job remoto y sus reportes. El push documental de cierre provoca automáticamente otra validación del HEAD del PR; se observa hasta concluir para entregar el PR final comprobado, sin despachar workflows manualmente.

## Configuración y archivos

`.github/workflows/validate-pr.yml`: PR hacia main; checkout sin conservar credenciales, contents:read, Java 17, SDK explícito y wrapper validado; tests/lint/debug separados, sin continue-on-error. Caché de sólo lectura, timeout 30 min, cancelación de ejecuciones obsoletas, reportes 14 días, sin secretos ni publicaciones. No se modificaron protecciones obligatorias de main.

Archivos de M02: workflow nuevo, AGENTS, DESARROLLO_Y_ENTREGA, ESTADO_PROYECTO, ENTREGA_WORK y evidencias no secretas en docs/evidencias/M02. En esta continuación sólo se modifica el paso SDK, se actualiza la documentación y se agregan evidencias reales. El registro `verificaciones-locales.json` conserva el bloqueo inicial como snapshot histórico; no representa el estado vigente.

## Conservación y pendientes

Todo `DidiProfitOverlay/`, el workflow release **build-apk.yml** y `DidiProfitOverlay_V3.6.0_patch/` permanecen idénticos a M01. Identidad: **com.jonathan.didiprofit**, **0.3.7.1 / 23**. Sin cambios en OCR, MediaProjection, parser, cálculos, overlay, timer, historial SQLite, navegación, dependencias, firma o datos. No Uber, keystore release nuevo, secretos, instalación de APK ni cambios funcionales.

El workflow firmado conserva push a main y workflow_dispatch. **Un futuro merge a main activaría su build firmado existente**. M02 no lo activó ni modificó. La firma técnica debug del runner no sustituye la clave permanente y no se entrega como actualización de release.

Firma release local sigue pendiente por ausencia del keystore original; referencia pública comprobada en M01: certificado SHA-256 `57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12`. No acredita acceso privado ni comparación con teléfono. Validación física de DiDi/OCR, MediaProjection, datos reales, overlay/timer, 900 ms, reanudación y recuperación sigue pendiente según REGRESIONES_Y_PRUEBAS. CI no la sustituye.

Los bloqueos anteriores de publicación se resolvieron con Git Credential Manager; la conexión de la app mantiene limitación 403 de escritura. No se obtuvieron ni compartieron secretos. El PR lo abrió el usuario; no se volvió a intentar creación por navegador ni se eludieron controles.

## Siguiente paso

Compartir este informe con ChatGPT y revisar [PR #1](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/pull/1), diff y check final antes de decidir integración. **No hay autorización M02 para merge, push directo a main, modificar secretos, publicar release ni instalar APK.**
