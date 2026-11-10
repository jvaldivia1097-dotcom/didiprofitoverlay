param(
    [string]$ToolsRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\.tools'),
    [string]$JdkHome,
    [string]$SdkRoot
)

# Ejecutar con dot-sourcing: . .\scripts\Preparar-Entorno.ps1
# Sólo modifica variables del proceso actual; no instala ni escribe secretos.
$ErrorActionPreference = 'Stop'
$toolsPath = (Resolve-Path -LiteralPath $ToolsRoot).Path
if (-not $JdkHome) {
    $JdkHome = (Get-ChildItem -LiteralPath (Join-Path $toolsPath 'jdk17') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
        Select-Object -First 1).FullName
}
if (-not $SdkRoot) { $SdkRoot = Join-Path $toolsPath 'android-sdk' }
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) {
    throw 'Falta JDK 17. Consulta docs/DESARROLLO_Y_ENTREGA.md.'
}
if (-not (Test-Path -LiteralPath (Join-Path $SdkRoot 'platforms\android-35\android.jar'))) {
    throw 'Falta Android API 35 en SdkRoot.'
}
$tempPath = Join-Path $toolsPath 'tmp'
New-Item -ItemType Directory -Force -Path $tempPath | Out-Null
# JDK 17 en Windows usa sockets Unix para el selector: evitar rutas largas.
if ($tempPath.Length -gt 85) { throw 'ToolsRoot necesita una ruta más corta para los sockets de Java.' }
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkRoot).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $toolsPath 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolsPath 'gradle-home'
if ($env:JAVA_TOOL_OPTIONS -notmatch '(^|\s)-Djdk.net.unixdomain.tmpdir=') {
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' -Djdk.net.unixdomain.tmpdir="' + $tempPath + '"').Trim()
}
Write-Output 'Entorno DiDi configurado sólo para esta sesión. Ejecutar Gradle desde DidiProfitOverlay/.'
