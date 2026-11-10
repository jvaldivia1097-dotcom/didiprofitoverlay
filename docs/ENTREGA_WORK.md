# DIDI-M02 — Integración controlada con GitHub y CI

Revisión: **2026-11-10, America/Mexico_City**.

**Estado: BLOQUEADO en publicación/validación remota por autenticación y permisos de escritura. Preparación local concluida.** El usuario indicó que autenticará Git local; aún falta su confirmación de finalización. **No hay PR ni ejecución remota M02 aprobada** y el criterio de aceptación remoto no se cumple todavía.

## 1. Resumen ejecutivo

Se confirmó la integridad de M01 y se preparó un workflow independiente para Pull Requests hacia main que ejecuta pruebas unitarias, Android lint y compilación debug, sin secretos de firma ni publicación release. Pasaron actionlint y la resolución offline del grafo de Gradle. Se reutilizó la evidencia válida de M01 sin repetir tests/build locales sobre código idéntico.

La publicación autorizada se intentó por Git y por la conexión GitHub existente: Git no tiene autenticación utilizable; GitHub rechazó la primera escritura con **403 — Resource not accessible by integration**. Ningún intento publicó la rama. No se eludieron controles ni se solicitaron valores de credenciales. **Enlace del PR: pendiente; no se creó.**

## 2. Repositorio, rama y commits

| Dato | Estado comprobado |
| --- | --- |
| Repositorio | `https://github.com/jvaldivia1097-dotcom/didiprofitoverlay.git` |
| Raíz Git | `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay` |
| Proyecto operativo | `DidiProfitOverlay/`, módulo `:app` |
| Base funcional/main | `eb0c17e3b004c87bd305a54b5103aac623a78e30` (V3.7.1); fetch inicial sin avances |
| M01 comprobado | `20a6ce41781a626f659d07a581a6dbf48b94d955`, rama `codex/didi-m01-preparacion`, árbol inicial limpio |
| Rama M02 | **`codex/didi-m02-ci`**, creada desde M01 |
| Commit CI M02 | **`45ca934b05ab649fa53ca5908d1581236e5aa1e4`** — DIDI-M02: validar PR con pruebas lint y debug sin firma release |
| Commit documental | HEAD de M02; hash y estado exactos en `..\local-evidence\M02\git-final.log` |
| Rama remota/PR | No creados por falta de acceso de escritura; `git ls-remote` confirmó sólo main para las dos refs consultadas |

La preparación M01 aún no está en main: el futuro PR incluirá ese commit y sus archivos, además de M02. La comparación contra V3.7.1 confirma que M01 no alteró app, dependencias ni componentes funcionales; la comparación M02 contra M01 mantiene todo `DidiProfitOverlay/`, workflow firmado e históricos intactos. No se descartó ni reescribió historial local.

## 3. Workflow configurado y justificación

Nuevo archivo **`.github/workflows/validate-pr.yml`**, nombre **Android PR validation**, job **Tests, lint y debug**.

- Evento `pull_request` hacia main, con apertura/reapertura/sincronización predeterminadas. No usa push, workflow_dispatch ni pull_request_target.
- Ubuntu 24.04, Temurin 17, SDK API 35 y Build Tools 35.0.0; usa el wrapper 8.13 comprobado en M01, con su validación automática. No cambia AGP 8.13.2/Kotlin 2.3.21/ML Kit 16.0.1.
- Checkout v4, setup-java v5, setup-gradle v4 y upload-artifact v4, compatibles con las acciones utilizadas en el proyecto. El runner reutiliza paquetes SDK válidos al ejecutar sdkmanager.
- Tres pasos independientes: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, con no-daemon/console plain/máximo dos workers. Sin continue-on-error ni supresiones.
- Token limitado a contents:read y checkout sin conservar credenciales. No referencia secretos, no prepara firma release ni publica artefactos release.
- Caché sólo lectura, cancelación de ejecuciones obsoletas del mismo PR y timeout de 30 minutos.
- Resumen separa referencia M01 de resultados reales. Conserva XML/HTML unitarios e informes lint txt/xml/html durante 14 días, incluso si falla un paso. No sube APK ni material privado de firma.

El ensamblado debug usaría la firma técnica debug habitual del runner, sin sustituir la firma permanente. No se entregó ni instaló APK. Los tags de acciones y la imagen del runner son referencias de proveedor, no un entorno hermético.

El workflow firmado **`.github/workflows/build-apk.yml`** permanece byte a byte igual: eventos **push a main** y **workflow_dispatch**, validaciones de integridad/alias/contraseñas y compilación firmada existentes. No se descargaron/reprodujeron credenciales. **Un futuro merge a main activaría ese build firmado**; revisar esta consecuencia antes de autorizar integración. M02 no lo ha activado. No se cambiaron protecciones de main: disponer del check no lo convierte automáticamente en regla obligatoria de merge.

## 4. Advertencias y evidencia Android reutilizada

M01 confirmó **15 tests aprobados**, **lint con 0 errores y 70 advertencias**, y **assembleDebug correcto**. Esa evidencia sigue en `docs/evidencias/unit-tests/`, `lint-results-debug.txt` y `resumen-verificaciones.json`. M02 no presenta esos resultados como ejecuciones nuevas.

Referencia de advertencias: SetTextI18n 48, ObsoleteSdkInt 9, UseKtx 6, ClickableViewAccessibility 3, GradleDependency 1, MissingApplicationIcon 1, OldTargetApi 1, StaticFieldLeak 1. No se añadió baseline ni supresión general; no se modificó código para reducirlas. Las 70 advertencias son **referencia histórica, no un resultado impuesto ni un umbral que oculte hallazgos**. El workflow mostrará los resultados reales; errores nuevos de las tareas bloquearán el job y deberán diagnosticarse.

## 5. Comprobaciones locales ejecutadas

Se leyeron AGENTS y los cuatro documentos requeridos antes de editar. Se comprobaron remoto, ramas, commits, árbol limpio, archivos de M01, fuente operativa, eventos de release, rutas/versiones/permisos del nuevo workflow y modo/checksum del wrapper. `git fetch origin` confirmó main sin avances.

Actionlint no estaba disponible; se descargó únicamente **actionlint 1.7.12 Windows amd64**, portable en `..\.tools\actionlint`, de la [release oficial](https://github.com/rhysd/actionlint/releases/tag/v1.7.12). Se verificó el ZIP con el checksum del proveedor: **`6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9`**. No se reinstaló JDK/SDK/Gradle ni se descargaron de nuevo dependencias Android.

Desde raíz Git, con las herramientas locales de M01:

```powershell
git diff --exit-code 20a6ce4 -- DidiProfitOverlay .github/workflows/build-apk.yml DidiProfitOverlay_V3.6.0_patch
..\.tools\actionlint\actionlint.exe -color .github\workflows\validate-pr.yml
. .\scripts\Preparar-Entorno.ps1
Set-Location DidiProfitOverlay
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --dry-run --offline --no-daemon --console=plain --max-workers=2
```

| Comprobación | Resultado real |
| --- | --- |
| Diff Android/wrapper/firma/históricos respecto a M01 | Vacío, exit 0 |
| Actionlint nuevo YAML | Sin diagnósticos, exit 0; no había shellcheck externo para análisis opcional de Bash |
| Grafo Gradle offline | BUILD SUCCESSFUL, exit 0, 35 s; todas las tareas SKIPPED por simulación |
| Nuevos tests/lint/build Android locales | No ejecutados; se reutiliza M01 por fuente/wrapper/dependencias idénticos |
| Diff/check y archivos de entrega | Revisados; sólo entorno/documentación/CI y evidencia no secreta |

El dry-run **no ejecutó pruebas, lint ni ensamblado**; verifica configuración y resolución del grafo. Se mantiene el aviso preexistente de lector SDK XML v3 frente a XML v4. La validación estática **no demuestra aceptación/ejecución por GitHub**.

## 6. Intentos de publicación y bloqueo remoto

1. `git push --set-upstream origin codex/didi-m02-ci`, usando GIT_TERMINAL_PROMPT=0, GCM_INTERACTIVE=Never y credential.interactive=false para no solicitar credenciales por consola: exit **128**, `Cannot prompt because user interactivity has been disabled` / `unable to get password from user`. No se publicó la rama.
2. Se comprobó la alternativa del conector GitHub instalado. La primera escritura de un blob (sólo JAR público del wrapper) fue rechazada con **403**, código **FORBIDDEN**, mensaje **Resource not accessible by integration**. No se creó blob, árbol, commit, rama ni PR por esa vía. No se cambiaron permisos OAuth ni se extrajeron tokens.
3. Se utilizó la habilidad [Plugin Management](C:/Users/100160722/.codex/plugins/cache/openai-curated-remote/plugin-management/0.1.0/skills/plugin-management/SKILL.md) para comprobar la conexión disponible: GitHub está instalado/habilitado; esto no acredita escritura. No se reinstaló ni se cambiaron permisos. Se pidió al usuario autenticar Git legítimamente o corregir el acceso del conector, sin pegar secretos; eligió **autenticar Git local** y aún no confirmó finalización.
4. `git ls-remote origin refs/heads/main refs/heads/codex/didi-m02-ci` devolvió únicamente main en el commit base, exit 0. Confirma que el intento rechazado no creó esa rama ni alteró main.

El impedimento es de acceso, no de falta de autorización M02. No se eludieron controles del servicio. Si una ejecución futura exige aprobación manual de seguridad, debe informarse y respetarse.

**PR y resultados remotos M02 de tests/lint/debug: pendientes, no ejecutados.** No se presenta el análisis local ni el workflow histórico de release de M01 como CI remoto de este encargo.

## 7. Archivos modificados y diferencias frente a M01

- `.github/workflows/validate-pr.yml`: nuevo CI independiente de PR sin firma release.
- `AGENTS.md`: alcance autorizado de M02 y ubicación del CI, conservando restricciones funcionales.
- `docs/DESARROLLO_Y_ENTREGA.md`: operación de CI, reportes, límites y consecuencia de un futuro merge.
- `docs/ESTADO_PROYECTO.md` y `docs/ENTREGA_WORK.md`: estado vigente M02 y bloqueo verificable. El informe íntegro M01 permanece en el historial de `20a6ce4`.
- `docs/evidencias/M02/`: logs de actionlint/dry-run y resumen de verificaciones/acceso. Sólo se normalizaron espacios/saltos finales; originales en `..\local-evidence\M02`.

M01 preparó herramientas, completó el wrapper y ejecutó tests/lint/debug, además de verificar una APK histórica. M02 añade CI y validación estática, y prepara la publicación controlada; **la integración remota aún falta**. No se repitió la auditoría ni la preparación ambiental.

## 8. Conservación funcional, firma y validación física

Todo `DidiProfitOverlay/` sigue idéntico a M01: OCR, MediaProjection, parser, fórmulas, overlay, temporizador, SQLite, navegación, recursos, tests y build de app. Se conservan **applicationId com.jonathan.didiprofit**, **versionName 0.3.7.1**, **versionCode 23**, dependencias y configuración de firma. No Uber, automatización de DiDi, fusión de frames o cambios de WindowManager. No se abrió, borró ni migró una base de usuario; no se instaló/desinstaló APK.

Firma release local sigue pendiente por falta de keystore original/acceso privado. M02 no amplió búsqueda ni reprodujo credenciales. Huella pública M01: **`57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12`**; no acredita firma local actual ni comparación con el teléfono instalado.

La validación física de OCR/DiDi real, MediaProjection, historial, overlay, círculo/expandido, pulsación de 900 ms, reanudación y recuperación sigue pendiente según REGRESIONES_Y_PRUEBAS. CI no la sustituye.

## 9. Evidencias y siguiente paso

Documentos y evidencia persistente: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay\docs\`, subcarpeta `evidencias\M02\`. Captura Git final, diff completo e incidencias de acceso: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\local-evidence\M02\`.

**Para completar M02:** el usuario termina autenticación legítima y avisa, sin compartir secretos. Codex publicará sólo `codex/didi-m02-ci`, abrirá PR hacia main, comprobará el workflow hasta terminar y actualizará este informe con enlace PR, SHA, run/job y resultados reales. Esa publicación y verificación ya están autorizadas por M02.

Después, compartir ENTREGA_WORK íntegro con ChatGPT para revisión y eventual decisión de integración. **No merge, push directo a main, cambio de secretos, release ni instalación sobre el teléfono.**
