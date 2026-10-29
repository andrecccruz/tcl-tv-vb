# Desativa até 10 pacotes, um por vez, com pm disable-user (reversível). Recusa os da lista "nunca desativar".
# Uso: powershell -ExecutionPolicy Bypass -File scripts\desativar.ps1 <pacote> [<pacote> ...]
# Cada pacote desativado vai para desativados.txt (data, pacote, comando para desfazer).
param([Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)][string[]]$Pacotes)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }   # adb no PATH
$registro = Join-Path $root 'desativados.txt'

# Ver docs/DEBLOAT.md. Termina em * = prefixo.
$nunca = @(
    'android', 'com.android.systemui', 'com.android.shell', 'com.android.bluetooth', 'com.android.vending',
    'com.android.location.fused', 'com.android.providers.*', 'com.android.tv.settings*',
    'com.google.android.gms', 'com.google.android.gsf', 'com.google.android.webview',
    'com.google.android.permissioncontroller', 'com.google.android.packageinstaller',
    'com.google.android.tv.remote.service', 'com.google.android.inputmethod.latin',
    'com.google.android.apps.tv.launcherx', 'com.google.android.tungsten.setupwraith',
    'com.tcl.tv', 'com.tcl.tvinput', 'com.tcl.suspension', 'com.tvos', 'com.tcl.system.server', 'com.tcl.autopair',
    'com.tcl.inputmethod.international', 'com.tcl.settings', 'com.tcl.providers.config', 'com.tcl.android.webview',
    'com.tcl.factory.view', 'com.tcl.guard', 'com.tcl.initsetup', 'com.tcl.partnercustomizer', 'com.tcl.ttvs'
)

if ($Pacotes.Count -gt 10) { throw 'No máximo 10 pacotes por vez. Teste a TV antes do próximo lote.' }
foreach ($p in $Pacotes) {
    foreach ($n in $nunca) {
        if ($p -eq $n -or ($n.EndsWith('*') -and $p.StartsWith($n.TrimEnd('*')))) {
            throw "$p está na lista 'nunca desativar' (docs/DEBLOAT.md). Nada foi feito."
        }
    }
}

$estado = (& $adb get-state).Trim()
if ($estado -ne 'device') { throw "TV não conectada (adb get-state = $estado)." }

foreach ($p in $Pacotes) {
    $saida = (& $adb shell pm disable-user --user 0 $p) -join ' '
    Write-Output "$p -> $saida"
    if ($saida -match 'disabled-user') {
        Add-Content -Encoding utf8 $registro ("{0}`t{1}`tadb shell pm enable {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm'), $p)
    }
}
Write-Output ''
Write-Output 'Agora teste: controle, botão de entradas, HDMI e seus apps. Para desfazer: adb shell pm enable <pacote>'
