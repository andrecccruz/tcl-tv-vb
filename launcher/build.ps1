# Compila o launcher. Usa JAVA_HOME / ANDROID_HOME já definidos; se não houver, procura as ferramentas portáteis
# em ..\tools (jdk, android-sdk, gradle), sem instalar nada no Windows. Ver README, seção "Compilar".
# Uso: powershell -ExecutionPolicy Bypass -File launcher\build.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root 'tools'

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $tools 'jdk' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $tools 'android-sdk' }
if (Test-Path $tools) {
    $env:ANDROID_USER_HOME = Join-Path $tools '.android'     # chave de assinatura debug fica aqui
    $env:GRADLE_USER_HOME = Join-Path $tools 'gradle-home'   # cache do Gradle fica aqui
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Set-Content -Encoding ascii (Join-Path $PSScriptRoot 'local.properties') ("sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/'))

$gradle = Join-Path $tools 'gradle\bin\gradle.bat'
if (-not (Test-Path $gradle)) { $gradle = 'gradle' }   # Gradle 8.7+ no PATH
& $gradle -p $PSScriptRoot --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Instalar: adb install -r launcher\app\build\outputs\apk\debug\app-debug.apk
Get-Item (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk')
