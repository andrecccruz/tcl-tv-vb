# Mede CPU e memória do launcher na TV, com ele aberto na tela.
# Uso: powershell -ExecutionPolicy Bypass -File launcher\medir.ps1 -Rotulo "antes" [-Segundos 30]
param(
    [string]$Rotulo = 'medida',
    [int]$Segundos = 30,
    [string]$Pacote = 'br.tvtcl.inicio'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }   # adb no PATH
$pkg = $Pacote
$dir = Join-Path $root 'medidas'   # fora do git (.gitignore)
New-Item -ItemType Directory -Force $dir | Out-Null
$saida = Join-Path $dir "medida-$Rotulo.txt"

& $adb shell am start -n "$pkg/br.tvtcl.inicio.MainActivity" | Out-Null
Start-Sleep -Seconds 5   # deixa a tela assentar antes de medir
& $adb shell dumpsys gfxinfo $pkg reset | Out-Null   # gfxinfo passa a contar só a janela da medida
$appPid = (& $adb shell pidof $pkg).Trim()
$cores = [int](& $adb shell nproc).Trim()

# CPU: soma de utime+stime (campos 14 e 15 de /proc/<pid>/stat, em ticks de 1/100 s) no início e no fim.
$script = "a=`$(cut -d' ' -f14,15 /proc/$appPid/stat); sleep $Segundos; b=`$(cut -d' ' -f14,15 /proc/$appPid/stat); echo `$a `$b"
$t = (& $adb shell $script).Trim() -split '\s+'
$ticks = ([int]$t[2] + [int]$t[3]) - ([int]$t[0] + [int]$t[1])
$cpuCore = [math]::Round($ticks / ($Segundos * 100) * 100, 1)
$cpuTotal = [math]::Round($cpuCore / $cores, 1)

$mem = & $adb shell dumpsys meminfo $pkg
$pss = ($mem | Select-String 'TOTAL PSS:|TOTAL:' | Select-Object -First 1).Line.Trim()
$cpuinfo = (& $adb shell dumpsys cpuinfo | Select-String $pkg | Select-Object -First 1)
$gfx = & $adb shell dumpsys gfxinfo $pkg | Select-String 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile'

$linhas = @(
    "== $Rotulo  ($(Get-Date -Format 'yyyy-MM-dd HH:mm'))  pid=$appPid  núcleos=$cores  janela=${Segundos}s",
    "CPU (/proc/stat): $cpuCore% de 1 núcleo = $cpuTotal% da CPU total ($ticks ticks)",
    "dumpsys cpuinfo:  " + $(if ($cpuinfo) { $cpuinfo.Line.Trim() } else { '(não aparece: abaixo do mínimo listado)' }),
    "dumpsys meminfo:  $pss"
) + ($gfx | ForEach-Object { "gfxinfo:          " + $_.Line.Trim() })
$linhas | Set-Content -Encoding utf8 $saida
$linhas
