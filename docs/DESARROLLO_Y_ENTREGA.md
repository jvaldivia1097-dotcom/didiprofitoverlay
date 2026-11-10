# Desarrollo y entrega

## Flujo y límites Git

ChatGPT prepara/revisa encargos; Codex ejecuta y documenta; el usuario acepta y valida en Android; GitHub controla versiones y automatiza sólo con autorización. Leer AGENTS y ESTADO_PROYECTO al iniciar. Registrar remoto, rama, HEAD, estado y diff antes de tocar archivos. No descartar trabajo ajeno. Crear rama local por encargo desde la base acordada; si main avanzó, comparar commits y conservarlos.

M01: `codex/didi-m01-preparacion`, sin push, merge, releases, workflows disparados ni cambios en secretos. Los commits locales están autorizados. No trabajar en Claritmo, Inventario ESR ni copias históricas del proyecto. Para revisar entregas: `git diff --stat`, `git diff --check`, diff de todos los archivos y lista de no rastreados. Los binarios de herramientas, cachés, salidas y claves quedan fuera del control de versiones.

## Entorno y preparación reproducible

Fuente de compilación: **`DidiProfitOverlay/`** en la raíz del repositorio. No la carpeta del parche V3.6.0.

Versiones conservadas: Gradle **8.13**, AGP **8.13.2**, Kotlin plugin **2.3.21**, Java/toolchain **17**, compile/target SDK **35**, min SDK **26**, ML Kit Latin bundled **16.0.1**, tests `kotlin("test")` resuelto con el plugin. El Kotlin 2.0.21 mostrado por `gradlew --version` es el embebido de Gradle, no el plugin Android del proyecto.

Compatibilidad oficial consultada: [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes?hl=en) requiere Gradle 8.13, JDK 17 y Build Tools 35.0.0. [Matriz Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html) admite esta combinación para 2.3.20–2.3.21. No se actualizó ninguna dependencia del proyecto.

En esta máquina, herramientas portables en `C:\Users\100160722\Documents\ChatGPT\App_Didi_Rentabilidad\.tools`, fuera de la raíz Git. No se cambió PATH/JAVA_HOME global, registro ni instalación de otras apps. Git 2.56.0.windows.4 ya disponible. La caché global existente sólo contenía distribución 9.3.1; se dejó intacta. Se usa caché aislada `gradle-home` y `android-user` dentro de .tools. El Java encontrado por PATH era un JRE 8, sin javac; no se usó para construir.

Para reconstruir el entorno en otra carpeta, reutilizar JDK 17/SDK/Gradle válidos si ya existen. En caso contrario descargar de los proveedores oficiales, comprobar SHA-256 y extraer sin instaladores globales:

| Paquete probado | Fuente oficial y SHA-256 |
| --- | --- |
| Temurin JDK 17.0.21+9 Windows x64 | [ZIP Adoptium](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip), `98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b` |
| Gradle 8.13 bin | [ZIP Gradle](https://services.gradle.org/distributions/gradle-8.13-bin.zip), `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` |
| Android Command-line Tools 22.0 Windows, build 15859902 | [ZIP Google](https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip), `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`, publicado en [Android Studio](https://developer.android.com/studio) |

Disponer JDK en `.tools/jdk17/jdk-17.0.21+9/` y command-line tools en `.tools/android-sdk/cmdline-tools/latest/` (bin/lib directamente dentro de latest). Desde una sesión con JAVA_HOME apuntando al JDK 17, instalar sólo:

```powershell
& "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$SdkRoot" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
```

Definir `$SdkRoot` como ruta absoluta del SDK; aceptar las licencias presentadas conforme a la autorización del entorno. M01 obtuvo plataforma API 35 revisión 2, Build Tools 35.0.0 y platform-tools 37.0.1. No necesita emulador, NDK ni Android Studio. La herramienta 22.0 anuncia deprecación de sdkmanager a favor de Android CLI; el comando utilizado terminó correctamente. AGP también emite aviso de XML SDK v4 frente a lector v3; no se cambió AGP ni se ocultó la advertencia.

Desde la raíz Git:

```powershell
. .\scripts\Preparar-Entorno.ps1
Set-Location DidiProfitOverlay
.\gradlew.bat --version
```

El script admite `-ToolsRoot`, `-JdkHome` y `-SdkRoot` para rutas alternativas; modifica sólo la sesión. Usa ANDROID_HOME/ANDROID_SDK_ROOT; no hizo falta `local.properties`. No versionar rutas locales ni credenciales. En el aislamiento Windows de Codex fue necesario configurar `-Djdk.net.unixdomain.tmpdir` a una carpeta corta: la ruta TEMP original tenía 96 caracteres y fallaba `PipeImpl/UnixDomainSockets.connect` con `Invalid argument`. IPv4 por sí solo no lo resolvió. El script conserva la corrección. Si las rutas tienen espacios, JAVA_TOOL_OPTIONS los protege con comillas. Si se deniega red/localhost en sandbox, solicitar la ejecución permitida del comando concreto sin alterar firewall global ni seguridad.

### Wrapper

La base sólo traía `gradle-wrapper.properties`. Se generaron **gradlew, gradlew.bat y gradle-wrapper.jar** mediante `wrapper` de la distribución oficial 8.13, en un proyecto temporal mínimo, sin evaluar/modificar lógica Android. Se añadió `distributionSha256Sum` sin cambiar la URL/versión. JAR verificado contra [checksum oficial](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256): `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

La distribución ya descargada y validada se reutilizó en la caché del wrapper, evitando descargarla dos veces. En una máquina nueva, el wrapper la obtiene y verifica automáticamente. Conservar permisos ejecutables de gradlew para POSIX; su ejecución sólo fue comprobada en Windows mediante gradlew.bat.

## Análisis, pruebas y debug

### Integración continua propuesta en M02

`.github/workflows/validate-pr.yml` se activa únicamente por `pull_request` hacia `main` (apertura, reapertura y sincronización predeterminadas). Usa Ubuntu 24.04, Temurin 17 y el wrapper 8.13 del repositorio; prepara API 35 y Build Tools 35.0.0 sólo si el SDK del runner lo necesita. `setup-gradle@v4` valida el wrapper y utiliza caché de sólo lectura. Ejecuta tests, lint y assembleDebug como pasos separados, sin `continue-on-error`. Se conserva el resultado real de lint; **70 advertencias de M01 son referencia, no baseline ni umbral que oculte hallazgos**.

El token tiene `contents: read`, checkout no conserva credenciales y el workflow no referencia secretos, firma release, publicaciones ni `pull_request_target`. Cancela ejecuciones obsoletas del mismo PR y limita el job a 30 minutos. Guarda XML/HTML de pruebas e informes lint durante 14 días, incluso al fallar; no carga APK ni material de firma. La disponibilidad del check no implica protección de rama obligatoria: M02 no modifica reglas de main.

El workflow release `build-apk.yml` permanece intacto: se activa por push a main o workflow_dispatch. Por tanto, **un futuro merge hacia main activaría el build firmado existente**. Revisar esa consecuencia antes de autorizar la integración; M02 sólo permite rama de trabajo y PR, sin merge ni despacho de release.

Para comprobar CI localmente sin repetir el build de M01: actionlint sobre el nuevo YAML y `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --dry-run --offline --no-daemon --console=plain --max-workers=2`. Una simulación no ejecuta tests ni acredita CI remoto. Consultar ENTREGA_WORK para el estado real de publicación/validación y sus posibles impedimentos de acceso.

Ejecutar desde DidiProfitOverlay, después de preparar la sesión:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain --max-workers=2
```

Se pueden separar tareas para diagnosticar fallos; no repetir las ya verificadas sin motivo. Si lint falla después de tests, assemble puede no haberse ejecutado; comprobar cada tarea y reportar por separado. No crear baseline de lint, suprimir errores o cambiar funcionalidad para presentar un éxito artificial. Resultados M01 en ENTREGA_WORK.

Revisar XML/HTML de tests en `app/build/test-results/testDebugUnitTest/` y `app/build/reports/tests/testDebugUnitTest/`; lint en `app/build/reports/lint-results-debug.*`; APK debug en `app/build/outputs/apk/debug/app-debug.apk`. Compilar no ejecuta pruebas físicas. Matriz manual en REGRESIONES_Y_PRUEBAS.

## Release y firma permanente

Configuración actual: `app/build.gradle.kts` usa `app/release-key.jks` y variables de entorno `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Actions además recibe `ANDROID_KEYSTORE_BASE64` para reconstruir el archivo. No enviar valores al chat, comandos visibles, informes o Git. No generar ni sustituir keystore release. La clave debug generada por las herramientas sólo sirve para debug.

**No quedó verificada capacidad de firmar release localmente**: falta keystore original y acceso privado a sus credenciales. La búsqueda de M01 se limitó al espacio autorizado de DiDi y sus archivos de configuración; no se exploraron otras carpetas privadas de firma, almacenes ni proyectos. No se intentó recuperar secretos de GitHub.

Alternativa: conservar el workflow activo `.github/workflows/build-apk.yml`, que ya valida keystore y credenciales antes de `gradle :app:assembleRelease`. Una ejecución nueva requiere autorización remota separada. La ejecución histórica #25 para la base tuvo éxito en verificación de secretos, integridad, alias, clave privada y compilación firmada. Esto acredita ese evento, no la vigencia presente de los secretos ni capacidad de firma en Codex. No se modificó Actions en M01.

Con provisión legítima y segura de la firma, el comando local sería `.\gradlew.bat :app:assembleRelease`; no se ejecutó en M01. No pasar contraseñas como argumentos de consola ni guardar transcripciones sensibles. Para verificar el contenedor keystore usar SHA-256 y un acceso privado mediante herramienta de contraseñas/variables, nunca volcar certificados privados o claves. Hash de contenedor esperado configurado por Actions: `ed2afa62b8d34e8d66b806abd9d3ec878e515cde35cc9993add0093383f4203f`. **No es la huella del certificado ni de la APK.** El contenedor no estuvo disponible para recalcularlo localmente.

### Verificación de artefactos

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs $Apk
& "$env:ANDROID_HOME\build-tools\35.0.0\aapt.exe" dump badging $Apk
Get-FileHash -LiteralPath $Apk -Algorithm SHA256
```

Definir `$Apk` a una APK concreta. Revisar independientemente:

1. **Identidad**: `com.jonathan.didiprofit`, versión aprobada, min/target SDK.
2. **Certificado**: firma criptográficamente válida y huella SHA-256 igual a referencia original `57b0ca9f22d66c8ef491925b305023e9a92f3f33a94ae1c80bfbf94baaf9bc12` (APK V3.7.1 recuperada de Actions, RSA 2048, v2 verificada).
3. **Keystore**: integridad del archivo y acceso a la clave original, comprobación privada separada; no inferirlo de la existencia del certificado público.
4. **APK**: SHA-256 identifica bytes concretos y verifica integridad de entrega. Cambiarlo al compilar no significa necesariamente cambio de certificado.

La APK histórica recuperada tiene SHA-256 `c66651311c57b6063ca808eba4b2da70d1a42aab4766cc283775424c16b01d8a`. Su ZIP de Actions tiene SHA-256 distinto: `083c8d46e840150c00894f8318f8b569c266ff40a5ea1167b2183d94c41fe0fb`. Evidencia de origen, hashes y comandos en ENTREGA_WORK. No se comparó con la APK instalada en el teléfono; continuidad hacia ese dispositivo sigue pendiente.

## Entrega, versiones y publicación

No incrementar versión en M01. En encargos futuros sólo cambiar versionCode/versionName cuando se haya autorizado una entrega de versión; conservar applicationId y firma. Una debug no se ofrece como actualización de la release ni se instala sobre ella. No desinstalar, limpiar datos ni migrar la base por facilitar una prueba.

Para entregar una APK: indicar commit/base/rama, tipo de build, versión/applicationId, ruta, tamaño, SHA-256, certificado, resultado apksigner, tests/lint y limitaciones físicas. Preservar evidencia original y no confundir una APK histórica recuperada con una compilación nueva.

Publicar exige autorización explícita, pruebas pertinentes, aceptación funcional del usuario y continuidad de firma documentada. Sin esos requisitos la entrega permanece local. Mantener resultados reproducibles, sin prometer APK bit a bit idéntica: no se introdujeron lockfiles/verificación completa de todas las dependencias transitivas ni un SDK hermético.

## Devolución al chat de planificación

Actualizar ESTADO_PROYECTO y entregar ENTREGA_WORK con: estado LISTO/LISTO CON PENDIENTES/BLOQUEADO, resumen, base y commits locales, diff, archivos, herramientas, comandos realmente ejecutados con códigos/resultados, identidad/firma/integridad por separado, riesgos, pruebas físicas pendientes y próximo encargo recomendado. El usuario puede compartir ENTREGA_WORK íntegro y, para más detalle, REGRESIONES_Y_PRUEBAS y evidencias de texto. Nunca compartir claves o contraseñas.
