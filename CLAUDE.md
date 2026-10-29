# Projeto: limpar e personalizar uma TV TCL via ADB

Instruções para agentes (Claude Code ou outro) que forem mexer numa TV com este repositório.

## A TV
- TCL, plataforma G07, Android TV 11 com interface Google TV (build `TCL G07 4K GB NF RP1A.200622.001`).
  Outro modelo: confira os nomes de pacote antes de seguir as listas.
- Conectada via ADB pela rede local. Confira sempre com `adb devices` antes de começar.
- O `adb` pode estar em `./platform-tools/adb.exe` (ignorado pelo git) ou no PATH.

## Regras (siga à risca)
1. **Antes de qualquer mudança**, rode `scripts/backup.ps1` (pacotes do sistema, desativados, de terceiros e memória).
2. **Só use `pm disable-user --user 0 <pacote>`.** Nunca use `pm uninstall`, `rm`, `reboot bootloader`/`recovery`/`fastboot`,
   nem mexa em partições.
3. **No máximo 10 pacotes por vez.** Depois pare e peça ao dono da TV para testar: HDMI, botão de entradas, controle e os
   apps que ele usa.
4. Registre cada pacote desativado em `desativados.txt` (o `scripts/desativar.ps1` faz isso) com o comando para desfazer
   (`adb shell pm enable <pacote>`).
5. Nunca desative um pacote que você não consegue identificar. Investigue com `dumpsys package <pacote>` e pergunte.
6. Mostre a lista e espere o "ok" antes de cada lote.
7. Nunca desative `com.google.android.apps.tv.launcherx`: o Home entra em loop. Use o serviço do launcher (README).

## Listas
`docs/DEBLOAT.md`: nunca desativar, já testados, depende do uso, investigados.

## Launcher
Código em `launcher/`, compila com `launcher/build.ps1` (ferramentas portáteis em `tools/` se não houver admin).
Detalhes de por que o Home funciona assim: `docs/NOTAS-TECNICAS.md`.
