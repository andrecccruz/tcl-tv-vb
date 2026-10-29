# Salva o estado da TV antes de qualquer mudança: pacotes do sistema, desativados, de terceiros e memória.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
# Os arquivos vão para backup\<data-hora>\ (fora do git: a lista de apps instalados é pessoal).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }   # adb no PATH

$dir = Join-Path $root ('backup\' + (Get-Date -Format 'yyyy-MM-dd_HHmm'))
New-Item -ItemType Directory -Force $dir | Out-Null

& $adb shell getprop ro.build.fingerprint | Set-Content -Encoding utf8 (Join-Path $dir 'build.txt')
& $adb shell pm list packages -s | Set-Content -Encoding utf8 (Join-Path $dir 'packages-system.txt')
& $adb shell pm list packages -d | Set-Content -Encoding utf8 (Join-Path $dir 'packages-disabled.txt')
& $adb shell pm list packages -3 | Set-Content -Encoding utf8 (Join-Path $dir 'packages-third-party.txt')
& $adb shell dumpsys meminfo | Set-Content -Encoding utf8 (Join-Path $dir 'meminfo.txt')

Get-ChildItem $dir
