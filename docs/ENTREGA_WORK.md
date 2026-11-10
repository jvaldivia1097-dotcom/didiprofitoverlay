# DIDI-M01 — Entrega a ChatGPT

**Estado final: LISTO CON PENDIENTES.** Revisión: **2026-11-09 (America/Mexico_City)**. Trabajo local; sin publicación remota.

## 1. Resumen ejecutivo

DiDi Rentabilidad quedó preparada para encargos posteriores de Codex: copia Git independiente, rama de preparación, wrapper completo y verificado, JDK/SDK portables aislados, script de entorno, mapa técnico y documentación permanente. Las **15 pruebas unitarias existentes pasaron**, **lint terminó con 0 errores y 70 advertencias**, y **se compiló/verificó una APK debug**. No se alteró código funcional ni versión Android.

Se recuperó además la APK release histórica del commit V3.7.1 desde un artefacto existente de GitHub Actions, se validó su integridad y se extrajo su certificado público como referencia. **No se compiló ni firmó una release nueva localmente.** Faltan el material privado de firma original y las pruebas físicas en Android; no se instaló ninguna APK sobre la aplicación del usuario.

## 2. Repositorio, versión y Git

| Dato | Comprobación |
| --- | --- |
| Raíz Git | `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay` |
| Fuente/build operativo | `DidiProfitOverlay/` dentro de esa raíz |
| Origin | `https://github.com/jvaldivia1097-dotcom/didiprofitoverlay.git` |
| Base/main/origin-main al clonar | `eb0c17e3b004c87bd305a54b5103aac623a78e30` |
| Diferencia referencia…origin/main | `git rev-list --left-right --count`: **0 / 0** |
| Estado inicial | Carpeta del encargo vacía; clon independiente limpio, sin trabajo previo que sobrescribir |
| Rama de preparación | `codex/didi-m01-preparacion` |
| Identidad fuente y release histórica | `com.jonathan.didiprofit`, versionName **0.3.7.1**, versionCode **23**, compile/target **35**, min **26** |
| Main | Conservada en la base, sin cambios directos |
| Commit local de entrega | `DIDI-M01: preparar entorno Android y documentar verificacion`; HEAD de la rama. Hash exacto y estado posterior en `..\local-evidence\git-final.log` |

Sólo se realizaron lecturas de GitHub para consultar el workflow y recuperar un artefacto ya existente. No hubo push, merge, release, despacho/reintento de workflows, cambio de secretos ni alteración de historial Git. No se trabajó dentro de Claritmo o Inventario ESR.

## 3. Entorno preparado

Git **2.56.0.windows.4** reutilizado. JDK **Temurin 17.0.21+9** verificado con java/javac. Gradle **8.13**, AGP **8.13.2**, Kotlin plugin **2.3.21**, ML Kit Latin bundled **16.0.1**. SDK API **35 rev. 2**, Build Tools **35.0.0**, command-line tools **22.0**, platform-tools **37.0.1**; adb ejecutó `version`, sin instalación ni interacción con dispositivos.

Herramientas en `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\.tools`; cachés/estado Android aislados dentro de esa carpeta. No hubo cambios globales de PATH/JAVA_HOME, limpieza de cachés, reinstalación de otras herramientas ni actualización de dependencias del proyecto. El JRE 8 del PATH y la distribución global Gradle 9.3.1 no se usaron para este build. No hizo falta local.properties.

La base contenía únicamente `gradle-wrapper.properties`, sin scripts/JAR. Se generaron los tres archivos faltantes desde la distribución oficial **8.13** y se añadió `distributionSha256Sum` sin modificar versión/URL. ZIP y JAR contrastados con checksums oficiales; hashes y procedimiento reproducible en [DESARROLLO_Y_ENTREGA.md](DESARROLLO_Y_ENTREGA.md). La distribución descargada se reutilizó en caché del wrapper.

`scripts/Preparar-Entorno.ps1` configura únicamente la sesión y corrige el fallo de sockets Java en este aislamiento Windows usando una carpeta temporal corta.

## 4. Estructura y arquitectura

Fuente única `DidiProfitOverlay/`, módulo `:app`. MainActivity solicita permisos → CaptureService recibe MediaProjection autorizada → ImageReader/VirtualDisplay → ML Kit local → OfferParser → overlay inmediato y HistoryCandidateTracker de tres lecturas → HistoryRepository SQLite v1. OverlayController/CircularTimerView gestionan temporizador; AnalysisResumeState gestiona recuperación de autorización; SettingsActivity/HistoryActivity/TopTenActivity cubren configuración e historial/rankings.

Se inspeccionaron parser, modelos/fórmulas, persistencia, controles contra registros incorrectos, estados del timer, vistas circular/expandida, finalización de 900 ms, detención/reanudación de captura y limpieza. Detalle con distinción de evidencia en [ARQUITECTURA.md](ARQUITECTURA.md).

El parche `DidiProfitOverlay_V3.6.0_patch/` es histórico e incompleto. `.github/workflows/.github/workflows/build-apk.yml` es copia debug anidada, fuera del nivel de workflows activos. Se conservaron sin cambios. README/BUILD_NOTES/PARSER_TEST_RESULTS de V2 no se usaron como prueba actual.

## 5. Archivos creados o modificados

Desde la raíz Git:

| Archivo/ruta | Motivo |
| --- | --- |
| `AGENTS.md` | Reglas compactas, lectura de estado, fuente única, restricciones y entrega verificable |
| `.gitignore` | Evitar versionar cachés, salidas, configuración local y material privado de firma |
| `.gitattributes` | Finales de línea explícitos para ambos scripts de wrapper |
| `DidiProfitOverlay/gradlew` | Wrapper POSIX generado, marcado ejecutable en Git; no probado en POSIX |
| `DidiProfitOverlay/gradlew.bat` | Wrapper Windows generado y ejecutado |
| `DidiProfitOverlay/gradle/wrapper/gradle-wrapper.jar` | JAR oficial generado/verificado |
| `DidiProfitOverlay/gradle/wrapper/gradle-wrapper.properties` | Único archivo existente modificado: añade checksum de distribución |
| `scripts/Preparar-Entorno.ps1` | Configuración repetible de la sesión, sin secretos ni ajustes globales |
| `docs/ESTADO_PROYECTO.md` | Estado que debe actualizarse en futuros encargos |
| `docs/DESARROLLO_Y_ENTREGA.md` | Preparación, Git, pruebas, build, identidad, firma y publicación |
| `docs/REGRESIONES_Y_PRUEBAS.md` | Antecedentes, cobertura y guion físico pendiente |
| `docs/ARQUITECTURA.md` | Mapa técnico detallado de la fuente comprobada |
| `docs/ENTREGA_WORK.md` | Este informe principal |
| `docs/evidencias/` | Log de build, wrapper, cinco XML de tests, lint completo, metadatos Actions y verificaciones no secretas de APK |

Fuera de Git: `.tools/` contiene herramientas/cachés y `local-evidence/` conserva logs de preparación, ZIP/release histórica y captura Git final. No se incorporaron contraseñas, keystore original, claves privadas ni APK a Git. Las herramientas generaron exclusivamente su keystore estándar **debug**, en `.tools/android-user/debug.keystore`; no sirve como firma permanente ni reemplaza la release.

## 6. Comandos ejecutados y resultados

Directorio de los comandos Android: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay\DidiProfitOverlay`. Antes se ejecutó, desde raíz Git: `. .\scripts\Preparar-Entorno.ps1` y `Set-Location DidiProfitOverlay`.

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain --max-workers=2
```

Primer comando: Gradle 8.13/JVM 17.0.21, exit **0**. Segundo: **BUILD SUCCESSFUL**, exit **0**, **5m 50s**, **53 tareas ejecutadas**; cada tarea solicitada figura en el log. Se ejecutó una sola vez la suite Android conjunta; no se repitieron tests o builds idénticos.

| Verificación | Resultado y evidencia |
| --- | --- |
| Unit tests | **15/15** aprobados, 0 fallos, 0 errores, 0 omitidos; cinco XML en `docs/evidencias/unit-tests/` |
| Lint debug | **0 errores, 70 advertencias**; informe completo `docs/evidencias/lint-results-debug.txt` |
| Assemble debug | APK generada, 46,554,594 bytes; firma debug v2 válida e identidad comprobada |
| Compilación release local | **No ejecutada** por falta del material original de firma |
| Dispositivo físico / DiDi real | **No ejecutado**; sin instalación ni acceso a SQLite de usuario |

Cobertura: OfferParserTest **5**, OfferParserV33Test **2**, HistoryCandidateTrackerTest **4**, OcrFrameAccumulatorTest **1**, TripTimeEstimatorTest **3**. Son entradas sintéticas de JVM; no ejecutan MediaProjection, ML Kit sobre pantallas reales, SQLite Android ni gestos de overlay. Casos/cobertura faltante en REGRESIONES_Y_PRUEBAS.

Advertencias lint por categoría: SetTextI18n **48**, ObsoleteSdkInt **9**, UseKtx **6**, ClickableViewAccessibility **3**, GradleDependency **1**, MissingApplicationIcon **1**, OldTargetApi **1**, StaticFieldLeak **1**. Todas recaen sobre archivos/dependencias conservados; no se añadieron supresiones ni baseline. Riesgo de referencia estática y accesibilidad queda para evaluación en otro encargo, sin cambiar WindowManager por iniciativa propia.

Otros comandos efectivamente utilizados: `git --version`, `git clone`, `git status --short --branch`, `git remote -v`, `git log`, `git rev-parse`, `git rev-list --left-right --count`, `git switch -c codex/didi-m01-preparacion`; JDK `java -version`/`javac -version`; `sdkmanager.bat --sdk_root=... platforms;android-35 build-tools;35.0.0 platform-tools` (con cada paquete como argumento entre comillas); `adb.exe version`; `aapt.exe dump badging` en ambas APK y `dump permissions` en la histórica; `apksigner.bat verify --verbose --print-certs` en ambas; `Get-FileHash -Algorithm SHA256` en distribuciones/JAR/ZIP/APK; `git diff --check` y comparación de fuente contra la base. Todos los comandos de verificación de APK finalizaron correctamente. Los filtros de badging guardan campos relevantes, no el volcado completo.

### Incidencias de preparación resueltas

1. El primer clone dentro del aislamiento falló `Could not resolve host: github.com`; la ejecución con acceso de red permitido clonó correctamente. No se asumió éxito del intento fallido.
2. Inventario inicial con Get-Command no concluyó con prontitud; se interrumpió y se completó con where.exe y rutas de herramientas conocidas. Java/javac se comprobaron después por ruta explícita al JDK portable.
3. Al inventariar workflows, Get-Content encontró una carpeta anidada y emitió error de directorio; se leyó luego el YAML concreto, identificando ambas copias correctamente.
4. Generación del wrapper en proyecto mínimo: intento inicial falló conexión al daemon; fuera del aislamiento también falló `Unable to establish loopback connection`. El intento con IPv4 produjo traza `PipeImpl → UnixDomainSockets.connect: Invalid argument`. Se diagnosticó ruta TEMP del aislamiento de 96 caracteres y se usó `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<ruta corta>`; la generación terminó **BUILD SUCCESSFUL**, 1 tarea. No era un error de la app.
5. Avisos de SDK XML v4/lector v3, APIs Android obsoletas y biblioteca `libmlkit_google_ocr_pipeline.so` empaquetada sin stripping. No impidieron tests/lint/build; se conservaron versiones y bibliotecas. No se instaló NDK para silenciar el aviso.

Logs completos de estos intentos en `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\local-evidence\`: `wrapper-generation.log`, `wrapper-generation-unrestricted.log`, `wrapper-generation-ipv4.log`, `wrapper-generation-short-temp.log`, `sdk-install.log`; metadatos/hashes de descargas en `.tools/jdk-source.json`, `.tools/sdk-source.json` y `.tools/gradle-8.13-bin.zip.sha256`.

## 7. APK e identidad/firma, por separado

| Comprobación | Release histórica V3.7.1 | Debug compilada M01 |
| --- | --- | --- |
| Origen | Actions run #25, ID **33427071690**, commit base exacto | Wrapper local, código sin cambios |
| Ruta absoluta | `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\local-evidence\original-release\app-release.apk` | `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay\DidiProfitOverlay\app\build\outputs\apk\debug\app-debug.apk` |
| Identidad | com.jonathan.didiprofit / 0.3.7.1 / 23 | Igual; marcada debuggable |
| Tamaño | 45,683,622 bytes | 46,554,594 bytes |
| SHA-256 APK | `c66651311c57b6063ca808eba4b2da70d1a42aab4766cc283775424c16b01d8a` | `4706874075b952a5bf127cfe80f58be7d96316530755d35cac5591b3e6f5e830` |
| Firma | apksigner **Verifies**, v2, un firmante RSA 2048 | apksigner **Verifies**, v2, certificado Android Debug distinto |
| SHA-256 certificado | `57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12` | `e437d92cfa84e4f58b74c785e04c9a08b8d1ea9f95b43e8abd42d5d466d37f59` |

**Identidad:** confirmada en código y ambas APK mediante aapt. **Certificado:** verificada la firma criptográfica de la APK release histórica y fijada su huella como referencia; no se comparó con el teléfono instalado ni con una release nueva. **Keystore:** original ausente localmente; su integridad/acceso actuales no verificados. **APK:** hash identifica los bytes y la firma verifica la integridad firmada; no se confunde con hash de keystore o certificado.

El workflow activo reconstruye `app/release-key.jks` desde `ANDROID_KEYSTORE_BASE64` y usa `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Verifica el contenedor contra SHA-256 `ed2afa62b8d34e8d66b806abd9d3ec878e515cde35cc9993add0093383f4203f`, valida alias/contraseña y firma un JAR antes de compilar. Ese hash se leyó de su configuración, **no se recalculó sobre un keystore local**.

La consulta de [Actions #25](https://github.com/jvaldivia1097-dotcom/didiprofitoverlay/actions/runs/33427071690) confirma éxito histórico el **31 de agosto de 2026** en todos esos pasos. No ejecuta tests ni lint. Artefacto **9771169827**, `DiDi-Rentabilidad-V3-APK`, no expirado al consultar; fecha de expiración informada **2026-11-29T18:48:36Z**. ZIP descargado: SHA-256 `083c8d46e840150c00894f8318f8b569c266ff40a5ea1167b2183d94c41fe0fb`, coincide con digest de GitHub. Metadatos en `docs/evidencias/actions-v371.json`; se guardó la APK de referencia localmente antes de su expiración.

La búsqueda de firma se limitó al espacio de trabajo autorizado de DiDi y sus archivos/configuración. No se rastrearon almacenes privados ajenos ni se solicitaron valores de secretos. **No está acreditada la capacidad actual de Codex para entregar nuevas APK release firmadas**. Alternativa viable: conservar Actions y usarlo en un futuro encargo con autorización remota; la ejecución histórica no garantiza que hoy todos los secretos sigan vigentes. No solicitar contraseñas o claves por chat.

La debug es únicamente comprobación técnica: **no entregarla ni instalarla como actualización sobre la release existente**. La APK release recuperada es una referencia histórica, no una nueva versión ni una nueva compilación de M01.

## 8. Conservación funcional y datos

La comparación Git contra la base arroja diff vacío para `DidiProfitOverlay/app/` completo (incluidos tests, manifiesto, recursos y build de app), build/settings/gradle.properties principales, `.github/` e históricos. No se alteraron OCR, MediaProjection, parser, rentabilidad, confirmación histórica, SQLite, temporizador, controles de finalización o reanudación, navegación ni identidad/firma. No se implementó Uber ni automatización de DiDi.

Esta evidencia acredita **ausencia de cambios de fuente** y éxito de pruebas existentes, no una nueva validación física de los comportamientos. No se abrió ni borró una base de usuario, no se cambió esquema ni se instaló/desinstaló aplicación. La única modificación sobre un archivo preexistente es el checksum del wrapper.

## 9. Pendientes, riesgos y siguiente paso

1. **Firma privada local pendiente:** keystore y acceso legítimo no disponibles. Conservar firma original por Actions; decidir un canal privado de provisión sólo si se requiere build release local. No reemplazar/generar firma permanente.
2. **Validación física pendiente:** DiDi real, permisos, precisión de OCR, falsas propuestas, datos/historial, overlay/círculo/panel, captura detenida, reanudación autorizada y protección de 900 ms. Seguir REGRESIONES_Y_PRUEBAS sin sustituir ni desinstalar release o borrar datos. También falta contrastar certificado con la app instalada.
3. **Advertencias preexistentes:** 70 de lint, APIs deprecadas, compatibilidad de lector SDK y stripping ML Kit. Se documentaron; no justifican ampliar M01 a refactorización/actualización.
4. **Límites de privacidad/persistencia:** modelo OCR local confirmado por dependencia/flujo, pero APK final declara INTERNET/ACCESS_NETWORK_STATE añadidos por DataTransport; no se auditó tráfico/telemetría. Backup habilitado y timer en memoria son comportamientos preexistentes; no se ensayó restauración ni muerte de proceso. Tres lecturas estables reducen falsos registros, no los imposibilitan.
5. **Reproducibilidad:** herramientas/versiones/hashes y procedimiento fijados, build ejecutado; no se acreditó build bit a bit idéntico ni fijación de todos los transitivos. No hay prueba de CI nueva ni ejecución en POSIX.

**Recomendación:** revisar este informe en ChatGPT y encargar una validación física controlada con firma original y datos preservados. La preparación de desarrollo/debug ya es utilizable; la publicación y la nueva compilación release firmada requieren un encargo específico.

## 10. Rutas de revisión y devolución

Documentos bajo `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\didiprofitoverlay\docs\`; AGENTS en la raíz Git. Evidencia persistente en `docs/evidencias/`: `gradle-checks.log`, `wrapper-version.log`, `unit-tests/TEST-*.xml`, `lint-results-debug.txt`, `actions-v371.json`, identidades/permisos y firmas de APK, `resumen-verificaciones.json` con rutas/hashes/tamaños y resultado de ejecución.

Reportes originales regenerables: `DidiProfitOverlay/app/build/reports/tests/testDebugUnitTest/index.html`, `DidiProfitOverlay/app/build/reports/lint-results-debug.html` y `.xml`; merger en `DidiProfitOverlay/app/build/outputs/logs/manifest-merger-debug-report.txt`. Son salidas locales ignoradas; los resultados necesarios se preservaron en docs/evidencias.

Estado Git/diff final y hash del commit local de cierre: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\local-evidence\git-final.log`. Diff completo: `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\local-evidence\DIDI-M01.patch`. Se revisó para excluir código funcional, históricos, secretos y cambios remotos. En las copias de logs bajo docs/evidencias sólo se normalizaron espacios finales/saltos de línea para cumplir git diff --check; originales completos en local-evidence y app/build. El commit de cierre sólo contiene preparación/documentación/evidencias de M01.

**Para ChatGPT:** compartir este archivo íntegro. Para revisar riesgos/cobertura en más detalle, añadir `REGRESIONES_Y_PRUEBAS.md`, `ESTADO_PROYECTO.md` y, si se solicitan, los logs/XML de `docs/evidencias/`. No hace falta compartir herramientas, cachés, contraseñas o claves.
